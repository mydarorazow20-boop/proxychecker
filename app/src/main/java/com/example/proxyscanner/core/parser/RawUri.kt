package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.ParseErrorCode
import com.example.proxyscanner.core.model.UnsupportedReason

/** Thrown inside link parsers; [safeMessage] never contains input text. */
internal class LinkFormatException(
    val code: ParseErrorCode,
    val safeMessage: String,
) : Exception(safeMessage)

/** The link is well-formed but uses an option this app does not model. */
internal class UnsupportedLinkOption(
    val reason: UnsupportedReason = UnsupportedReason.UNSUPPORTED_OPTION,
) : Exception("Unsupported option")

internal data class HostPort(val host: String, val port: Int?)

internal data class RawUri(
    val scheme: String,
    val userInfo: String?,
    val host: String,
    val port: Int?,
    val path: String,
    val query: Map<String, String>,
    val fragment: String?,
)

/**
 * Splits `scheme://[userinfo@]host[:port][/path][?query][#fragment]` without
 * java.net.URI (which rejects many real-world share links). Query keys are
 * lower-cased; the first occurrence of a key wins. All failures throw
 * [LinkFormatException] with a fixed message.
 */
internal object RawUriParser {

    private const val MAX_HOST_LENGTH = 253

    fun parse(link: String): RawUri {
        val schemeEnd = link.indexOf("://")
        if (schemeEnd <= 0) throw LinkFormatException(ParseErrorCode.MALFORMED_URI, "Link has no scheme")
        val scheme = link.substring(0, schemeEnd).lowercase()
        var rest = link.substring(schemeEnd + 3)

        var fragment: String? = null
        val hash = rest.indexOf('#')
        if (hash >= 0) {
            fragment = PercentDecoder.decodeOrRaw(rest.substring(hash + 1))
            rest = rest.substring(0, hash)
        }
        var queryString: String? = null
        val question = rest.indexOf('?')
        if (question >= 0) {
            queryString = rest.substring(question + 1)
            rest = rest.substring(0, question)
        }
        var path = ""
        val slash = rest.indexOf('/')
        if (slash >= 0) {
            path = rest.substring(slash)
            rest = rest.substring(0, slash)
        }
        val at = rest.lastIndexOf('@')
        val userInfo = if (at >= 0) rest.substring(0, at) else null
        val hostPortText = if (at >= 0) rest.substring(at + 1) else rest
        val hostPort = parseHostPort(hostPortText)
        return RawUri(scheme, userInfo, hostPort.host, hostPort.port, path, parseQuery(queryString), fragment)
    }

    fun parseHostPort(text: String): HostPort {
        if (text.isEmpty()) throw LinkFormatException(ParseErrorCode.MISSING_FIELD, "Host is missing")
        val host: String
        val portText: String?
        if (text.startsWith("[")) {
            val close = text.indexOf(']')
            if (close < 0) throw LinkFormatException(ParseErrorCode.MALFORMED_URI, "Host is malformed")
            host = text.substring(1, close)
            val after = text.substring(close + 1)
            portText = when {
                after.isEmpty() -> null
                after.startsWith(":") -> after.substring(1)
                else -> throw LinkFormatException(ParseErrorCode.MALFORMED_URI, "Host is malformed")
            }
            if (!isValidIpv6Literal(host)) {
                throw LinkFormatException(ParseErrorCode.MALFORMED_URI, "Host is malformed")
            }
        } else {
            val colon = text.lastIndexOf(':')
            if (colon >= 0) {
                host = text.substring(0, colon)
                portText = text.substring(colon + 1)
            } else {
                host = text
                portText = null
            }
            if (!isValidHostname(host)) {
                throw LinkFormatException(ParseErrorCode.MALFORMED_URI, "Host is malformed")
            }
        }
        val port = if (portText == null) null else parsePort(portText)
        return HostPort(host, port)
    }

    fun parsePort(text: String): Int {
        if (text.isEmpty() || text.length > 5 || !text.all { it in '0'..'9' }) {
            throw LinkFormatException(ParseErrorCode.INVALID_PORT, "Port must be a number from 1 to 65535")
        }
        val value = text.toInt()
        if (value !in 1..65535) {
            throw LinkFormatException(ParseErrorCode.INVALID_PORT, "Port must be a number from 1 to 65535")
        }
        return value
    }

    /** A host name or an unbracketed IPv6 literal (as found in VMess JSON). */
    fun isValidHost(host: String): Boolean = isValidHostname(host) || isValidIpv6Literal(host)

    fun isValidHostname(host: String): Boolean {
        if (host.isEmpty() || host.length > MAX_HOST_LENGTH) return false
        if (host.startsWith(".") || host.startsWith("-")) return false
        return host.all { it.isLetterOrDigit() || it == '-' || it == '.' || it == '_' }
    }

    fun isValidIpv6Literal(host: String): Boolean {
        if (host.length < 2 || host.length > 45) return false
        if (host.count { it == ':' } < 2) return false
        return host.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
    }

    fun parseQuery(queryString: String?): Map<String, String> {
        if (queryString.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in queryString.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val key = (if (eq >= 0) pair.substring(0, eq) else pair).lowercase()
            val value = if (eq >= 0) pair.substring(eq + 1) else ""
            if (key.isEmpty() || out.containsKey(key)) continue
            out[key] = PercentDecoder.decodeOrRaw(value)
        }
        return out
    }
}
