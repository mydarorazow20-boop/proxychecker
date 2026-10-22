package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.Credentials
import com.example.proxyscanner.core.model.ParseErrorCode
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.TlsOptions
import com.example.proxyscanner.core.model.TransportOptions
import com.example.proxyscanner.core.model.TransportSecurity
import com.example.proxyscanner.core.model.TransportType
import com.example.proxyscanner.core.security.Secret

/**
 * `vmess://BASE64(JSON)` with the common v2rayN field names
 * (add, port, id, scy, net, type, host, path, tls, sni, alpn, fp, ps).
 * The URI-style variant `vmess://uuid@host:port?...` is reported as unsupported.
 */
class VmessLinkParser : BaseLinkParser(Protocol.VMESS) {

    private val methodRegex = Regex("^[A-Za-z0-9._-]{1,64}$")

    override fun build(link: String): ProxyConfig {
        val payload = link.substring(link.indexOf("://") + 3).substringBefore('#').trim()
        if (payload.isEmpty()) throw LinkFormatException(ParseErrorCode.MISSING_FIELD, "VMess payload is missing")
        if (payload.contains('@')) throw UnsupportedLinkOption()

        val bytes = LenientBase64.decode(payload)
            ?: throw LinkFormatException(ParseErrorCode.INVALID_BASE64, "VMess payload is not valid Base64")
        val json = StrictUtf8.decode(bytes)
            ?: throw LinkFormatException(ParseErrorCode.INVALID_JSON, "VMess payload is not valid UTF-8 text")
        val parsed = FlatJson.parseObject(json)
            ?: throw LinkFormatException(ParseErrorCode.INVALID_JSON, "VMess payload is not a valid JSON object")
        val fields = parsed.mapKeys { it.key.lowercase() }

        val address = LinkFields.nonBlank(fields["add"])
            ?: throw LinkFormatException(ParseErrorCode.MISSING_FIELD, "VMess address is missing")
        if (!RawUriParser.isValidHost(address)) {
            throw LinkFormatException(ParseErrorCode.MALFORMED_URI, "Host is malformed")
        }
        val portText = LinkFields.nonBlank(fields["port"])
            ?: throw LinkFormatException(ParseErrorCode.INVALID_PORT, "Port is missing")
        val port = RawUriParser.parsePort(portText)
        val uuid = LinkFields.requireUuid(fields["id"], "VMess")

        val method = LinkFields.nonBlank(fields["scy"]) ?: "auto"
        if (!methodRegex.matches(method)) {
            throw LinkFormatException(ParseErrorCode.INVALID_FIELD, "VMess security method is invalid")
        }

        val transportType = LinkFields.transportOf(fields["net"])
        val headerType = LinkFields.nonBlank(fields["type"])?.lowercase()
        if (transportType == TransportType.TCP && headerType != null && headerType != "none") {
            throw UnsupportedLinkOption()
        }

        val security = when (LinkFields.nonBlank(fields["tls"])?.lowercase()) {
            null, "none" -> TransportSecurity.NONE
            "tls" -> TransportSecurity.TLS
            else -> throw UnsupportedLinkOption()
        }
        val tls = if (security == TransportSecurity.TLS) {
            TlsOptions(
                security = TransportSecurity.TLS,
                serverName = LinkFields.nonBlank(fields["sni"]),
                alpn = LinkFields.alpnOf(fields["alpn"]),
                allowInsecure = LinkFields.isTruthy(fields["allowinsecure"]),
                clientFingerprint = LinkFields.nonBlank(fields["fp"]),
            )
        } else {
            TlsOptions()
        }

        val path = LinkFields.nonBlank(fields["path"])
        val transport = TransportOptions(
            type = transportType,
            host = LinkFields.nonBlank(fields["host"]),
            path = if (transportType == TransportType.GRPC) null else path,
            serviceName = if (transportType == TransportType.GRPC) path else null,
        )

        return ProxyConfig(
            fingerprint = LinkFields.PENDING_FINGERPRINT,
            protocol = Protocol.VMESS,
            address = address,
            port = port,
            credentials = Credentials(userId = Secret(uuid), method = method),
            tls = tls,
            transport = transport,
            remark = LinkFields.cleanRemark(fields["ps"]),
        )
    }
}
