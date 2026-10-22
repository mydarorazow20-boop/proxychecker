package com.example.proxyscanner.core.importer

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// These tests use runBlocking (real time) on purpose: the fetcher applies withTimeout and
// real worker threads, which would not mix with the virtual time of runTest.

private class FakeResponse(
    override val status: Int,
    override val location: String? = null,
    override val contentLength: Long? = null,
    private val stream: InputStream? = null,
) : HttpResponse {
    @Volatile
    var closed = false

    override val body: InputStream? get() = stream

    override fun close() {
        closed = true
        stream?.close()
    }
}

private class FakeTransport(private val handler: (String) -> HttpResponse) : HttpTransport {
    val requested = mutableListOf<String>()
    val responses = mutableListOf<FakeResponse>()

    override fun get(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): HttpResponse {
        requested.add(url)
        val response = handler(url)
        if (response is FakeResponse) responses.add(response)
        return response
    }
}

/** Blocks every read until it is closed, like a stalled socket. */
private class BlockingStream(private val started: CountDownLatch) : InputStream() {
    private val closedLatch = CountDownLatch(1)

    @Volatile
    var closed = false

    override fun read(): Int {
        started.countDown()
        closedLatch.await()
        throw IOException("stream closed")
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        started.countDown()
        closedLatch.await()
        throw IOException("stream closed")
    }

    override fun close() {
        closed = true
        closedLatch.countDown()
    }
}

/** Throws a timeout on the first read. */
private class TimeoutStream : InputStream() {
    override fun read(): Int = throw SocketTimeoutException("read timed out")
    override fun read(b: ByteArray, off: Int, len: Int): Int = throw SocketTimeoutException("read timed out")
}

class HttpsFetcherTest {

    private val url = "https://lists.example.com/sub.txt"
    private val publicIp: InetAddress = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))
    private val privateIp: InetAddress = InetAddress.getByAddress(byteArrayOf(10, 0, 0, 5))
    private val publicResolver = AddressResolver { listOf(publicIp) }

    private fun fetcher(
        transport: HttpTransport,
        resolver: AddressResolver = publicResolver,
        settings: FetchSettings = FetchSettings(),
    ) = HttpsFetcher(transport, UrlSyntaxValidator(), resolver, settings, Dispatchers.IO)

    private fun ok(text: String, contentLength: Long? = text.length.toLong()) =
        FakeResponse(200, contentLength = contentLength, stream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

    private fun fetch(transport: HttpTransport, target: String = url, max: Long = 1000, resolver: AddressResolver = publicResolver) =
        runBlocking { fetcher(transport, resolver).fetch(target, max) }

    @Test
    fun successReturnsTheBodyAndClosesTheConnection() {
        val transport = FakeTransport { ok("hello list") }
        val result = fetch(transport)
        assertTrue(result is FetchResult.Body)
        assertEquals("hello list", (result as FetchResult.Body).bytes.toString(Charsets.UTF_8))
        assertEquals(listOf(url), transport.requested)
        assertTrue(transport.responses.all { it.closed })
    }

    @Test
    fun emptyBodyIsReturnedAsEmptyBytes() {
        val result = fetch(FakeTransport { FakeResponse(204) })
        assertTrue(result is FetchResult.Body)
        assertEquals(0, (result as FetchResult.Body).bytes.size)
    }

    // ---- URL rules ----

    @Test
    fun nonHttpsAndMalformedUrlsAreRejectedWithoutAnyRequest() {
        val bad = listOf(
            "http://lists.example.com/a",
            "ftp://lists.example.com/a",
            "https://user:pw@lists.example.com/a",
            "https://localhost/a",
            "https://127.0.0.1/a",
            "not a url",
            "",
        )
        for (target in bad) {
            val transport = FakeTransport { ok("x") }
            val result = fetch(transport, target)
            assertTrue("expected Rejected for '$target' but was $result", result is FetchResult.Rejected)
            assertTrue(transport.requested.isEmpty())
        }
    }

    @Test
    fun hostThatResolvesToAPrivateAddressIsBlockedBeforeConnecting() {
        val transport = FakeTransport { ok("x") }
        val result = fetch(transport, resolver = AddressResolver { listOf(publicIp, privateIp) })
        assertEquals(
            ImportFailureCode.PRIVATE_ADDRESS_BLOCKED,
            (result as FetchResult.Rejected).code,
        )
        assertTrue(transport.requested.isEmpty())
    }

    @Test
    fun dnsFailureIsANetworkError() {
        val transport = FakeTransport { ok("x") }
        val result = fetch(transport, resolver = AddressResolver { throw java.net.UnknownHostException("nope") })
        assertEquals(FetchResult.NetworkError, result)
        assertTrue(transport.requested.isEmpty())
    }

    // ---- HTTP status ----

    @Test
    fun httpErrorsAreReportedWithTheirStatus() {
        for (status in listOf(401, 403, 404, 429, 500, 503)) {
            val result = fetch(FakeTransport { FakeResponse(status) })
            assertEquals(FetchResult.HttpError(status), result)
        }
    }

    // ---- redirects ----

    @Test
    fun httpsRedirectIsFollowedIncludingRelativeLocations() {
        val transport = FakeTransport { requested ->
            when (requested) {
                url -> FakeResponse(302, location = "/moved/list.txt")
                "https://lists.example.com/moved/list.txt" -> FakeResponse(301, location = "https://cdn.example.org/final.txt")
                else -> ok("final content")
            }
        }
        val result = fetch(transport)
        assertEquals("final content", (result as FetchResult.Body).bytes.toString(Charsets.UTF_8))
        assertEquals(
            listOf(url, "https://lists.example.com/moved/list.txt", "https://cdn.example.org/final.txt"),
            transport.requested,
        )
        assertTrue(transport.responses.all { it.closed })
    }

    @Test
    fun redirectToPlainHttpIsRejected() {
        val transport = FakeTransport { FakeResponse(302, location = "http://lists.example.com/x") }
        val result = fetch(transport)
        assertEquals(ImportFailureCode.UNSUPPORTED_SCHEME, (result as FetchResult.Rejected).code)
        assertEquals(1, transport.requested.size)
    }

    @Test
    fun redirectToALocalAddressIsRejected() {
        val transport = FakeTransport { FakeResponse(307, location = "https://192.168.0.1/x") }
        val result = fetch(transport)
        assertEquals(ImportFailureCode.PRIVATE_ADDRESS_BLOCKED, (result as FetchResult.Rejected).code)
        assertEquals(1, transport.requested.size)
    }

    @Test
    fun redirectTargetResolvingToAPrivateAddressIsBlocked() {
        val resolver = AddressResolver { host -> if (host == "internal.example.com") listOf(privateIp) else listOf(publicIp) }
        val transport = FakeTransport { FakeResponse(302, location = "https://internal.example.com/x") }
        val result = fetch(transport, resolver = resolver)
        assertEquals(ImportFailureCode.PRIVATE_ADDRESS_BLOCKED, (result as FetchResult.Rejected).code)
        assertEquals(1, transport.requested.size)
    }

    @Test
    fun tooManyRedirectsStopsTheDownload() {
        val transport = FakeTransport { FakeResponse(302, location = "https://lists.example.com/loop") }
        val result = fetch(transport)
        assertEquals(FetchResult.TooManyRedirects, result)
        // the original request plus the allowed redirects, nothing more
        assertEquals(FetchSettings().maxRedirects + 1, transport.requested.size)
    }

    @Test
    fun redirectWithoutLocationIsAnHttpError() {
        assertEquals(FetchResult.HttpError(302), fetch(FakeTransport { FakeResponse(302) }))
    }

    // ---- size limit ----

    @Test
    fun declaredContentLengthAboveTheLimitIsRejectedWithoutReading() {
        val stream = ByteArrayInputStream(ByteArray(10))
        val transport = FakeTransport { FakeResponse(200, contentLength = 5_000, stream = stream) }
        assertEquals(FetchResult.TooLarge, fetch(transport, max = 1000))
        assertEquals(10, stream.available()) // nothing was consumed
    }

    @Test
    fun undeclaredBodyAboveTheLimitStopsAtTheLimit() {
        val body = "a".repeat(5_000)
        val transport = FakeTransport { ok(body, contentLength = null) }
        assertEquals(FetchResult.TooLarge, fetch(transport, max = 1000))
        assertTrue(transport.responses.all { it.closed })
    }

    @Test
    fun bodyExactlyAtTheLimitIsAccepted() {
        val transport = FakeTransport { ok("a".repeat(1000), contentLength = null) }
        val result = fetch(transport, max = 1000)
        assertEquals(1000, (result as FetchResult.Body).bytes.size)
    }

    @Test
    fun bodyOneByteOverTheLimitIsRejected() {
        val transport = FakeTransport { ok("a".repeat(1001), contentLength = null) }
        assertEquals(FetchResult.TooLarge, fetch(transport, max = 1000))
    }

    // ---- failures and timeouts ----

    @Test
    fun connectTimeoutIsATimeout() {
        val transport = FakeTransport { throw SocketTimeoutException("connect timed out") }
        assertEquals(FetchResult.Timeout, fetch(transport))
    }

    @Test
    fun readTimeoutIsATimeoutAndClosesTheConnection() {
        val transport = FakeTransport { FakeResponse(200, stream = TimeoutStream()) }
        assertEquals(FetchResult.Timeout, fetch(transport))
        assertTrue(transport.responses.all { it.closed })
    }

    @Test
    fun otherIoErrorsAreANetworkError() {
        val transport = FakeTransport { throw IOException("connection reset") }
        assertEquals(FetchResult.NetworkError, fetch(transport))
    }

    @Test
    fun stalledServerHitsTheTotalDeadline() {
        val started = CountDownLatch(1)
        val stream = BlockingStream(started)
        val transport = FakeTransport { FakeResponse(200, stream = stream) }
        val settings = FetchSettings(totalTimeoutMs = 300)
        val result = runBlocking { fetcher(transport, settings = settings).fetch(url, 1000) }
        assertEquals(FetchResult.Timeout, result)
        assertTrue(stream.closed)
    }

    // ---- cancellation ----

    @Test
    fun cancellationClosesTheConnectionAndStopsTheDownload() {
        val started = CountDownLatch(1)
        val stream = BlockingStream(started)
        val transport = FakeTransport { FakeResponse(200, stream = stream) }
        runBlocking {
            val job = launch(Dispatchers.Default) { fetcher(transport).fetch(url, 1000) }
            assertTrue("download did not start", started.await(5, TimeUnit.SECONDS))
            job.cancel()
            withTimeout(5_000) { job.join() }
            assertTrue(job.isCancelled)
            assertTrue(stream.closed)
            assertTrue(transport.responses.all { it.closed })
        }
    }

    // ---- no leaks ----

    @Test
    fun resultsNeverContainTheUrl() {
        val secretUrl = "https://secret-host.example.com/path?token=abc123"
        val results = listOf(
            fetch(FakeTransport { FakeResponse(500) }, secretUrl),
            fetch(FakeTransport { throw IOException("boom") }, secretUrl),
            fetch(FakeTransport { FakeResponse(302, location = "http://secret-host.example.com/?token=abc123") }, secretUrl),
        )
        for (result in results) {
            assertFalse(result.toString().contains("secret-host"))
            assertFalse(result.toString().contains("abc123"))
        }
    }
}
