package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.security.Secret
import java.security.MessageDigest

/**
 * Canonicalizes case and ordering, then computes a SHA-256 fingerprint over the
 * normalized fields (credentials included, remark excluded). The fingerprint is a
 * hash, not an encryption: it is for de-duplication and correlation only and is
 * never shown as a credential.
 */
class DefaultConfigNormalizer : ConfigNormalizer {

    override fun normalize(config: ProxyConfig): ProxyConfig {
        val credentials = config.credentials
        val isUuidProtocol = config.protocol == Protocol.VLESS || config.protocol == Protocol.VMESS
        val normalizedUserId = credentials.userId?.let {
            if (isUuidProtocol) Secret(it.reveal().trim().lowercase()) else it
        }
        val base = config.copy(
            address = config.address.trim().trimEnd('.').lowercase(),
            credentials = credentials.copy(
                userId = normalizedUserId,
                method = credentials.method?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                flow = credentials.flow?.trim()?.takeIf { it.isNotEmpty() },
            ),
            tls = config.tls.copy(
                serverName = config.tls.serverName?.trim()?.trimEnd('.')?.lowercase()?.takeIf { it.isNotEmpty() },
                alpn = config.tls.alpn.map { it.lowercase() }.distinct().sorted(),
            ),
            transport = config.transport.copy(
                host = config.transport.host?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            ),
        )
        return base.copy(fingerprint = fingerprint(base))
    }

    override fun fingerprint(config: ProxyConfig): String {
        val c = config.credentials
        val t = config.tls
        val tr = config.transport
        val canonical = listOf(
            config.protocol.name,
            config.address,
            config.port.toString(),
            c.userId?.reveal().orEmpty(),
            c.password?.reveal().orEmpty(),
            c.method.orEmpty(),
            c.flow.orEmpty(),
            t.security.name,
            t.serverName.orEmpty(),
            t.alpn.joinToString(","),
            t.allowInsecure.toString(),
            t.clientFingerprint.orEmpty(),
            t.realityPublicKey?.reveal().orEmpty(),
            t.realityShortId?.reveal().orEmpty(),
            tr.type.name,
            tr.host.orEmpty(),
            tr.path.orEmpty(),
            tr.serviceName.orEmpty(),
        ).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            hex.append("0123456789abcdef"[v shr 4])
            hex.append("0123456789abcdef"[v and 0x0F])
        }
        return hex.toString()
    }
}
