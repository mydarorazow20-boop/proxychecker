package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.Credentials
import com.example.proxyscanner.core.model.ParseErrorCode
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.TransportOptions
import com.example.proxyscanner.core.model.TransportSecurity
import com.example.proxyscanner.core.security.Secret

/** `vless://UUID@host:port?type=..&security=..&...#remark` (syntax only; no connectivity). */
class VlessLinkParser : BaseLinkParser(Protocol.VLESS) {

    override fun build(link: String): ProxyConfig {
        val uri = RawUriParser.parse(link)
        val userInfo = uri.userInfo
            ?: throw LinkFormatException(ParseErrorCode.MISSING_FIELD, "VLESS user id is missing")
        val uuid = LinkFields.requireUuid(PercentDecoder.decodeOrRaw(userInfo), "VLESS")
        val port = uri.port ?: throw LinkFormatException(ParseErrorCode.INVALID_PORT, "Port is missing")
        val query = uri.query

        // Only "none" is modelled; newer post-quantum encryption strings are not.
        val encryption = LinkFields.nonBlank(query["encryption"])
        if (encryption != null && !encryption.equals("none", ignoreCase = true)) {
            throw UnsupportedLinkOption()
        }

        val transportType = LinkFields.transportOf(query["type"])
        val security = when (query["security"]?.trim()?.lowercase()) {
            null, "", "none" -> TransportSecurity.NONE
            "tls" -> TransportSecurity.TLS
            "reality" -> TransportSecurity.REALITY
            else -> throw UnsupportedLinkOption()
        }

        return ProxyConfig(
            fingerprint = LinkFields.PENDING_FINGERPRINT,
            protocol = Protocol.VLESS,
            address = uri.host,
            port = port,
            credentials = Credentials(
                userId = Secret(uuid),
                flow = LinkFields.nonBlank(query["flow"]),
            ),
            tls = LinkFields.tlsFromQuery(security, query),
            transport = TransportOptions(
                type = transportType,
                host = LinkFields.nonBlank(query["host"]),
                path = LinkFields.nonBlank(query["path"]),
                serviceName = LinkFields.nonBlank(query["servicename"]),
            ),
            remark = LinkFields.cleanRemark(uri.fragment),
        )
    }
}
