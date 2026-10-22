package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.Credentials
import com.example.proxyscanner.core.model.ParseErrorCode
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.TransportOptions
import com.example.proxyscanner.core.model.TransportSecurity
import com.example.proxyscanner.core.security.Secret

/** `trojan://password@host:port?sni=..&type=..#remark` (syntax only; TLS is the only security mode). */
class TrojanLinkParser : BaseLinkParser(Protocol.TROJAN) {

    override fun build(link: String): ProxyConfig {
        val uri = RawUriParser.parse(link)
        val rawPassword = uri.userInfo
            ?: throw LinkFormatException(ParseErrorCode.MISSING_FIELD, "Trojan password is missing")
        val password = PercentDecoder.decodeOrRaw(rawPassword)
        if (password.isEmpty()) {
            throw LinkFormatException(ParseErrorCode.MISSING_FIELD, "Trojan password is missing")
        }
        val port = uri.port ?: throw LinkFormatException(ParseErrorCode.INVALID_PORT, "Port is missing")
        val query = uri.query

        when (query["security"]?.trim()?.lowercase()) {
            null, "", "tls" -> Unit
            else -> throw UnsupportedLinkOption()
        }
        val transportType = LinkFields.transportOf(query["type"])

        return ProxyConfig(
            fingerprint = LinkFields.PENDING_FINGERPRINT,
            protocol = Protocol.TROJAN,
            address = uri.host,
            port = port,
            credentials = Credentials(password = Secret(password)),
            tls = LinkFields.tlsFromQuery(TransportSecurity.TLS, query),
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
