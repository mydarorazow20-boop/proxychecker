package com.example.proxyscanner.core.model

import com.example.proxyscanner.core.security.Secret

/** Credential material. Every sensitive field is a [Secret]. */
data class Credentials(
    /** VLESS/VMess UUID or SOCKS5 username. */
    val userId: Secret? = null,
    /** Shadowsocks/Trojan/SOCKS5 password. */
    val password: Secret? = null,
    /** Cipher or security mode, e.g. a Shadowsocks method. Not secret. */
    val method: String? = null,
    /** Flow control value (for example for VLESS). Not secret. */
    val flow: String? = null,
)

enum class TransportSecurity { NONE, TLS, REALITY }

data class TlsOptions(
    val security: TransportSecurity = TransportSecurity.NONE,
    val serverName: String? = null,
    val alpn: List<String> = emptyList(),
    /** Certificate verification disabled. Surfaced to the user as a risk flag. */
    val allowInsecure: Boolean = false,
    val clientFingerprint: String? = null,
    val realityPublicKey: Secret? = null,
    val realityShortId: Secret? = null,
)

enum class TransportType { TCP, WEBSOCKET, GRPC, HTTP2, HTTP_UPGRADE, OTHER }

/** The set of transport types will be refined per engine in the engine-integration phase. */
data class TransportOptions(
    val type: TransportType = TransportType.TCP,
    val host: String? = null,
    val path: String? = null,
    val serviceName: String? = null,
)

/**
 * A normalized proxy configuration.
 *
 * [fingerprint] is a hash over normalized fields used for de-duplication and
 * for correlating results; it is not reversible to credentials.
 * [original] keeps the source text only when needed for export; it is a
 * [Secret] because share links embed credentials.
 */
data class ProxyConfig(
    val fingerprint: String,
    val protocol: Protocol,
    val address: String,
    val port: Int,
    val credentials: Credentials = Credentials(),
    val tls: TlsOptions = TlsOptions(),
    val transport: TransportOptions = TransportOptions(),
    val remark: String? = null,
    val original: Secret? = null,
) {
    init {
        require(fingerprint.isNotBlank()) { "fingerprint must not be blank" }
        require(address.isNotBlank()) { "address must not be blank" }
        require(port in 1..65535) { "port out of range" }
    }

    /** Label safe for logs and diagnostics: protocol + port, no host, no credentials. */
    fun safeLabel(): String = "${protocol.name}:$port#${fingerprint.take(8)}"
}
