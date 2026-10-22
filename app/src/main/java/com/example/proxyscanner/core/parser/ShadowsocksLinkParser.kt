package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.Credentials
import com.example.proxyscanner.core.model.ParseErrorCode
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.security.Secret

/**
 * Shadowsocks links:
 * - SIP002: `ss://BASE64URL(method:password)@host:port#tag` (also the plain,
 *   percent-encoded `method:password` user info used with 2022 ciphers);
 * - legacy: `ss://BASE64(method:password@host:port)#tag`.
 * Links with a `plugin` parameter are reported as unsupported. The cipher name is
 * checked for shape only; whether an engine supports it is decided later.
 */
class ShadowsocksLinkParser : BaseLinkParser(Protocol.SHADOWSOCKS) {

    private class Parts(val method: String, val password: String, val hostPort: HostPort)

    private val methodRegex = Regex("^[A-Za-z0-9._-]{1,64}$")

    override fun build(link: String): ProxyConfig {
        val body = link.substring(link.indexOf("://") + 3)
        val hashIndex = body.indexOf('#')
        val beforeFragment = if (hashIndex >= 0) body.substring(0, hashIndex) else body
        val fragment = if (hashIndex >= 0) PercentDecoder.decodeOrRaw(body.substring(hashIndex + 1)) else null

        val questionIndex = beforeFragment.indexOf('?')
        val beforeQuery = if (questionIndex >= 0) beforeFragment.substring(0, questionIndex) else beforeFragment
        val query = RawUriParser.parseQuery(
            if (questionIndex >= 0) beforeFragment.substring(questionIndex + 1) else null,
        )
        if (query.containsKey("plugin")) throw UnsupportedLinkOption()

        val core = beforeQuery
        if (core.isEmpty()) throw LinkFormatException(ParseErrorCode.MISSING_FIELD, "Shadowsocks payload is missing")

        val parts = if (core.contains('@')) parseSip002(core) else parseLegacy(core)
        val port = parts.hostPort.port ?: throw LinkFormatException(ParseErrorCode.INVALID_PORT, "Port is missing")

        return ProxyConfig(
            fingerprint = LinkFields.PENDING_FINGERPRINT,
            protocol = Protocol.SHADOWSOCKS,
            address = parts.hostPort.host,
            port = port,
            credentials = Credentials(password = Secret(parts.password), method = parts.method),
            remark = LinkFields.cleanRemark(fragment),
        )
    }

    private fun parseSip002(core: String): Parts {
        val at = core.lastIndexOf('@')
        val userInfo = PercentDecoder.decodeOrRaw(core.substring(0, at))
        // SIP002 allows a trailing slash after the port; legacy Base64 payloads must stay untouched.
        val hostPort = RawUriParser.parseHostPort(core.substring(at + 1).trimEnd('/'))
        val credentials = if (userInfo.contains(':')) userInfo else decodeBase64Text(userInfo)
        val (method, password) = splitMethodAndPassword(credentials)
        return Parts(method, password, hostPort)
    }

    private fun parseLegacy(core: String): Parts {
        val decoded = decodeBase64Text(PercentDecoder.decodeOrRaw(core))
        val at = decoded.lastIndexOf('@')
        if (at < 0) throw LinkFormatException(ParseErrorCode.MALFORMED_URI, "Shadowsocks payload has no host")
        val (method, password) = splitMethodAndPassword(decoded.substring(0, at))
        val hostPort = RawUriParser.parseHostPort(decoded.substring(at + 1))
        return Parts(method, password, hostPort)
    }

    private fun decodeBase64Text(text: String): String {
        val bytes = LenientBase64.decode(text)
            ?: throw LinkFormatException(ParseErrorCode.INVALID_BASE64, "Shadowsocks payload is not valid Base64")
        return StrictUtf8.decode(bytes)
            ?: throw LinkFormatException(ParseErrorCode.INVALID_BASE64, "Shadowsocks payload is not valid UTF-8 text")
    }

    private fun splitMethodAndPassword(text: String): Pair<String, String> {
        val colon = text.indexOf(':')
        if (colon <= 0) {
            throw LinkFormatException(ParseErrorCode.MISSING_FIELD, "Shadowsocks method or password is missing")
        }
        val method = text.substring(0, colon)
        val password = text.substring(colon + 1)
        if (password.isEmpty()) {
            throw LinkFormatException(ParseErrorCode.MISSING_FIELD, "Shadowsocks method or password is missing")
        }
        if (!methodRegex.matches(method)) {
            throw LinkFormatException(ParseErrorCode.INVALID_FIELD, "Shadowsocks method is invalid")
        }
        return Pair(method, password)
    }
}
