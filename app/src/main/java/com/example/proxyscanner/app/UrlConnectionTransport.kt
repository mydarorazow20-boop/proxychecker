package com.example.proxyscanner.app

import com.example.proxyscanner.core.importer.HttpResponse
import com.example.proxyscanner.core.importer.HttpTransport
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android/JVM [HttpTransport] on `HttpURLConnection` (no extra dependency). It is the only
 * class that opens a network connection. Redirects are disabled here because
 * [com.example.proxyscanner.core.importer.HttpsFetcher] validates every hop itself.
 * TLS certificate and host name verification stay at the platform defaults.
 */
class UrlConnectionTransport : HttpTransport {

    override fun get(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = "GET"
            connection.useCaches = false
            connection.setRequestProperty("Accept", "text/plain, */*")
            connection.setRequestProperty("User-Agent", "ProxyScanner")
            return Response(connection, connection.responseCode)
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
    }

    private class Response(
        private val connection: HttpURLConnection,
        override val status: Int,
    ) : HttpResponse {
        override val location: String? get() = connection.getHeaderField("Location")
        override val contentLength: Long? get() = connection.contentLengthLong.takeIf { it >= 0 }
        override val body: InputStream? get() = if (status in 200..299) connection.inputStream else null
        override fun close() {
            connection.disconnect()
        }
    }
}
