package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.ParseErrorCode
import com.example.proxyscanner.core.model.TlsOptions
import com.example.proxyscanner.core.model.TransportSecurity
import com.example.proxyscanner.core.model.TransportType
import com.example.proxyscanner.core.security.Secret

/** Helpers shared by the share-link parsers. Messages never contain input text. */
internal object LinkFields {

    /** Placeholder until [ConfigNormalizer] computes the real fingerprint. */
    const val PENDING_FINGERPRINT = "pending-normalization"

    private const val MAX_REMARK_LENGTH = 100
    private const val MAX_ALPN_ENTRIES = 8
    private const val MAX_ALPN_LENGTH = 32

    private val uuidRegex =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun requireUuid(value: String?, protocolName: String): String {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) throw LinkFormatException(ParseErrorCode.MISSING_FIELD, "$protocolName user id is missing")
        if (!uuidRegex.matches(text)) {
            throw LinkFormatException(ParseErrorCode.INVALID_FIELD, "$protocolName user id is not a valid UUID")
        }
        return text
    }

    /** Free-text label from the link: control and bidi characters removed, length capped. */
    fun cleanRemark(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.filter { !it.isISOControl() && !isBidiControl(it) }.trim()
        if (cleaned.isEmpty()) return null
        return if (cleaned.length > MAX_REMARK_LENGTH) cleaned.take(MAX_REMARK_LENGTH) else cleaned
    }

    private fun isBidiControl(c: Char): Boolean =
        c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069' || c == '\u200E' || c == '\u200F'

    fun isTruthy(value: String?): Boolean {
        val v = value?.trim()?.lowercase() ?: return false
        return v == "1" || v == "true" || v == "yes"
    }

    fun nonBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    /** Transport names understood by the model; anything else is reported as unsupported. */
    fun transportOf(name: String?): TransportType = when (name?.trim()?.lowercase()) {
        null, "", "tcp", "raw" -> TransportType.TCP
        "ws", "websocket" -> TransportType.WEBSOCKET
        "grpc" -> TransportType.GRPC
        "h2", "http" -> TransportType.HTTP2
        "httpupgrade" -> TransportType.HTTP_UPGRADE
        else -> throw UnsupportedLinkOption()
    }

    fun alpnOf(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_ALPN_LENGTH }
            .take(MAX_ALPN_ENTRIES)
    }

    /** TLS or REALITY options from share-link query parameters (keys already lower-cased). */
    fun tlsFromQuery(security: TransportSecurity, query: Map<String, String>): TlsOptions {
        val serverName = nonBlank(query["sni"]) ?: nonBlank(query["peer"])
        val allowInsecure = isTruthy(query["allowinsecure"]) || isTruthy(query["insecure"])
        var publicKey: Secret? = null
        var shortId: Secret? = null
        if (security == TransportSecurity.REALITY) {
            val pbk = nonBlank(query["pbk"])
                ?: throw LinkFormatException(ParseErrorCode.MISSING_FIELD, "REALITY public key is missing")
            publicKey = Secret(pbk)
            shortId = nonBlank(query["sid"])?.let { Secret(it) }
        }
        return TlsOptions(
            security = security,
            serverName = serverName,
            alpn = alpnOf(query["alpn"]),
            allowInsecure = allowInsecure,
            clientFingerprint = nonBlank(query["fp"]),
            realityPublicKey = publicKey,
            realityShortId = shortId,
        )
    }
}
