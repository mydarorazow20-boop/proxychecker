package com.example.proxyscanner.core.importer

import com.example.proxyscanner.core.parser.LinkFormatException
import com.example.proxyscanner.core.parser.RawUriParser

/**
 * Network-free syntax check for a future remote configuration URL.
 *
 * It decides whether the text is acceptable input; it does NOT fetch anything,
 * resolve DNS or prove that the URL is safe. The DNS/redirect checks required by
 * [RemoteUrlPolicy] belong to the phase that adds the network fetcher.
 *
 * Accepted: `https://` URLs with a fully-qualified host name or a public IPv4
 * literal, no user info, no whitespace. Rejected: other schemes (including plain
 * `http://`, decision D-06 is still open), user info, single-label hosts,
 * `localhost`, IPv6 literals, and IPv4 literals in private, loopback, link-local,
 * carrier-grade NAT, multicast or unspecified ranges. Reasons are fixed text and
 * never echo the URL.
 */
class UrlSyntaxValidator(private val maxLength: Int = 2048) {

    fun check(input: String): UrlCheck {
        val url = input.trim()
        if (url.isEmpty()) return reject(ImportFailureCode.URL_NOT_ALLOWED, "Enter a URL.")
        if (url.length > maxLength) return reject(ImportFailureCode.URL_NOT_ALLOWED, "The URL is too long.")
        if (url.any { it.isWhitespace() || it.isISOControl() }) {
            return reject(ImportFailureCode.URL_NOT_ALLOWED, "The URL must not contain spaces or control characters.")
        }
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) {
            return reject(ImportFailureCode.UNSUPPORTED_SCHEME, "The URL must start with https://")
        }
        when (url.substring(0, schemeEnd).lowercase()) {
            "https" -> Unit
            "http" -> return reject(
                ImportFailureCode.UNSUPPORTED_SCHEME,
                "Plain http:// is not accepted. Use an https:// URL.",
            )
            else -> return reject(ImportFailureCode.UNSUPPORTED_SCHEME, "Only https:// URLs are accepted.")
        }
        val uri = try {
            RawUriParser.parse(url)
        } catch (e: LinkFormatException) {
            return reject(ImportFailureCode.URL_NOT_ALLOWED, "The URL is malformed.")
        }
        if (uri.userInfo != null) {
            return reject(ImportFailureCode.URL_NOT_ALLOWED, "The URL must not contain a username or password.")
        }
        return checkHost(uri.host.lowercase())
    }

    private fun checkHost(host: String): UrlCheck {
        if (host == "localhost" || host.endsWith(".localhost")) {
            return reject(ImportFailureCode.PRIVATE_ADDRESS_BLOCKED, "Local addresses are not allowed.")
        }
        if (host.contains(':')) {
            return reject(ImportFailureCode.URL_NOT_ALLOWED, "IPv6 address URLs are not accepted. Use a host name.")
        }
        if (!host.contains('.')) {
            return reject(ImportFailureCode.URL_NOT_ALLOWED, "The URL needs a full host name such as example.com.")
        }
        if (host.all { it in '0'..'9' || it == '.' }) return checkIpv4Literal(host)
        return UrlCheck.Allowed
    }

    private fun checkIpv4Literal(host: String): UrlCheck {
        val parts = host.split('.')
        val octets = parts.map { if (it.isNotEmpty() && it.length <= 3) it.toInt() else -1 }
        if (octets.size != 4 || octets.any { it < 0 || it > 255 }) {
            return reject(ImportFailureCode.URL_NOT_ALLOWED, "The IP address in the URL is malformed.")
        }
        val a = octets[0]
        val b = octets[1]
        val blocked = a == 0 || a == 10 || a == 127 || a >= 224 ||
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 100 && b in 64..127)
        if (blocked) {
            return reject(ImportFailureCode.PRIVATE_ADDRESS_BLOCKED, "Private or local network addresses are not allowed.")
        }
        return UrlCheck.Allowed
    }

    private fun reject(code: ImportFailureCode, reason: String): UrlCheck =
        UrlCheck.Rejected(code, reason)
}
