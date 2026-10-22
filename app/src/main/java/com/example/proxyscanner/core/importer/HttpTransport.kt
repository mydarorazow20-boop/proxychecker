package com.example.proxyscanner.core.importer

import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress

/** One HTTP response. [close] must be idempotent and must abort a read that is blocked in another thread. */
interface HttpResponse : Closeable {
    val status: Int

    /** Raw `Location` header, if any. */
    val location: String?

    /** Declared body length, or null when unknown. */
    val contentLength: Long?

    /** The body stream, or null when there is none. Obtained once by the caller. */
    val body: InputStream?
}

/**
 * Minimal blocking HTTP GET used by [HttpsFetcher]. Implementations must NOT follow
 * redirects themselves, must apply the given timeouts, and throw
 * `java.net.SocketTimeoutException` on a timeout or another `IOException` on failure.
 * The Android implementation lives in the app package; tests use fakes.
 */
interface HttpTransport {
    fun get(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): HttpResponse
}

/** Resolves a host name to all of its addresses (blocking). */
fun interface AddressResolver {
    fun resolve(host: String): List<InetAddress>
}

object SystemAddressResolver : AddressResolver {
    override fun resolve(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()
}

/** Decides whether a resolved address is a public internet address (RemoteUrlPolicy, docs section 9). */
object PublicAddressPolicy {
    fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) {
            return false
        }
        val bytes = address.address
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if (first == 0) return false
            if (first == 100 && second in 64..127) return false // carrier-grade NAT
            if (first >= 240) return false // reserved / broadcast
        } else if (bytes.size == 16) {
            if ((bytes[0].toInt() and 0xFE) == 0xFC) return false // unique local fc00::/7
        }
        return true
    }
}
