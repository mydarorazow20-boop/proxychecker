package com.example.proxyscanner.core.importer

import com.example.proxyscanner.core.parser.RawUriParser
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class FetchSettings(
    val connectTimeoutMs: Int = 10_000,
    val readTimeoutMs: Int = 15_000,
    /** Hard deadline for the whole download, including redirects (stops slow-drip responses). */
    val totalTimeoutMs: Long = 30_000,
    val maxRedirects: Int = 3,
) {
    init {
        require(connectTimeoutMs > 0) { "connectTimeoutMs must be positive" }
        require(readTimeoutMs > 0) { "readTimeoutMs must be positive" }
        require(totalTimeoutMs > 0) { "totalTimeoutMs must be positive" }
        require(maxRedirects >= 0) { "maxRedirects must not be negative" }
    }
}

/**
 * HTTPS-only, bounded download built on an [HttpTransport].
 *
 * - Every hop (the first URL and each redirect target) must pass the existing
 *   [UrlSyntaxValidator] (https only, no user info, no local/private literals) and its host must
 *   resolve only to public addresses ([PublicAddressPolicy]). A redirect to http:// is rejected.
 * - At most [FetchSettings.maxRedirects] redirects are followed.
 * - Connect/read timeouts come from the transport; [FetchSettings.totalTimeoutMs] bounds the whole call.
 * - The body is read in chunks and abandoned as soon as it exceeds `maxBytes`
 *   (a larger declared Content-Length is rejected before reading).
 * - Cancellation closes the connection, which aborts a blocked read.
 * - Nothing is logged and the URL is never put into a result.
 *
 * Known limit: the address that was checked is not pinned for the connection (the platform
 * resolves the host again), so DNS rebinding is not fully excluded.
 */
class HttpsFetcher(
    private val transport: HttpTransport,
    private val validator: UrlSyntaxValidator = UrlSyntaxValidator(),
    private val resolver: AddressResolver = SystemAddressResolver,
    private val settings: FetchSettings = FetchSettings(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RemoteFetcher {

    override suspend fun fetch(url: String, maxBytes: Long): FetchResult {
        require(maxBytes > 0) { "maxBytes must be positive" }
        return try {
            withTimeout(settings.totalTimeoutMs) {
                withContext(ioDispatcher) { download(url.trim(), maxBytes) }
            }
        } catch (e: TimeoutCancellationException) {
            FetchResult.Timeout
        }
    }

    private suspend fun download(startUrl: String, maxBytes: Long): FetchResult = coroutineScope {
        val open = AtomicReference<HttpResponse?>(null)
        // Closing the connection is the only way to interrupt a blocking socket read.
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                open.get()?.closeQuietly()
            }
        }
        try {
            follow(startUrl, maxBytes, open)
        } finally {
            watcher.cancel()
            open.getAndSet(null)?.closeQuietly()
        }
    }

    private suspend fun follow(startUrl: String, maxBytes: Long, open: AtomicReference<HttpResponse?>): FetchResult {
        var url = startUrl
        var redirects = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val blocked = checkHop(url)
            if (blocked != null) return blocked

            val response = try {
                transport.get(url, settings.connectTimeoutMs, settings.readTimeoutMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                return FetchResult.Timeout
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                return FetchResult.NetworkError
            }
            open.set(response)

            val status = response.status
            if (status in 200..299) return readBody(response, maxBytes)

            val location = response.location
            response.closeQuietly()
            open.set(null)
            if (status !in REDIRECT_STATUSES) return FetchResult.HttpError(status)

            redirects++
            if (redirects > settings.maxRedirects) return FetchResult.TooManyRedirects
            if (location.isNullOrBlank()) return FetchResult.HttpError(status)
            url = resolveLocation(url, location)
                ?: return FetchResult.Rejected(ImportFailureCode.URL_NOT_ALLOWED, "The redirect target is not a valid URL.")
        }
    }

    /** Returns null when [url] may be requested, otherwise the reason it may not. */
    private fun checkHop(url: String): FetchResult? {
        when (val check = validator.check(url)) {
            UrlCheck.Allowed -> Unit
            is UrlCheck.Rejected -> return FetchResult.Rejected(check.code, check.reason)
        }
        val host = try {
            RawUriParser.parse(url).host
        } catch (e: Exception) {
            return FetchResult.Rejected(ImportFailureCode.URL_NOT_ALLOWED, "The URL is malformed.")
        }
        val addresses = try {
            resolver.resolve(host)
        } catch (e: Exception) {
            return FetchResult.NetworkError
        }
        if (addresses.isEmpty()) return FetchResult.NetworkError
        if (addresses.any { !PublicAddressPolicy.isPublic(it) }) {
            return FetchResult.Rejected(
                ImportFailureCode.PRIVATE_ADDRESS_BLOCKED,
                "The address resolves to a private or local network address.",
            )
        }
        return null
    }

    private fun resolveLocation(base: String, location: String): String? {
        val trimmed = location.trim()
        if (trimmed.contains("://")) return trimmed
        return try {
            URI(base).resolve(trimmed).toString()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun readBody(response: HttpResponse, maxBytes: Long): FetchResult {
        val declared = response.contentLength
        if (declared != null && declared > maxBytes) return FetchResult.TooLarge
        val out = ByteArrayOutputStream()
        try {
            val input = response.body ?: return FetchResult.Body(ByteArray(0))
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                if (out.size().toLong() + n > maxBytes) return FetchResult.TooLarge
                out.write(buffer, 0, n)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            return FetchResult.Timeout
        } catch (e: Exception) {
            // A close() caused by cancellation surfaces here as an IOException: report the cancellation instead.
            currentCoroutineContext().ensureActive()
            return FetchResult.NetworkError
        }
        return FetchResult.Body(out.toByteArray())
    }

    private fun HttpResponse.closeQuietly() {
        try {
            close()
        } catch (e: Exception) {
            // Nothing useful to do; the connection is being abandoned anyway.
        }
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
        val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}
