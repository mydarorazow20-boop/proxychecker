package com.example.proxyscanner.core.model

enum class ParseErrorCode {
    EMPTY_ENTRY,
    MALFORMED_URI,
    MISSING_FIELD,
    INVALID_PORT,
    INVALID_BASE64,
    INVALID_JSON,
    ENTRY_TOO_LONG,
    CONTENT_TOO_LARGE,
    TOO_MANY_ENTRIES,
    UNSUPPORTED_FORMAT,
    INVALID_FIELD,
    UNKNOWN,
}

enum class UnsupportedReason {
    UNKNOWN_SCHEME,
    PROTOCOL_NOT_IMPLEMENTED,
    UNSUPPORTED_OPTION,
}

/** [safeMessage] must already be redacted; never put raw input in it. */
data class ParseError(
    val sourceLine: Int?,
    val code: ParseErrorCode,
    val safeMessage: String,
)

data class UnsupportedEntry(
    val sourceLine: Int?,
    val reason: UnsupportedReason,
    /** URI scheme only (for example "wireguard"); never the full entry. */
    val schemeHint: String?,
)

data class DuplicateEntry(
    val fingerprint: String,
    val sourceLine: Int?,
    /** Index in [ImportResult.configs] of the entry that was kept. */
    val keptIndex: Int,
)

/**
 * Counters for one import. [parsed] counts entries that were syntactically
 * valid links, including those later dropped as duplicates; it says nothing
 * about whether a configuration is online or working.
 */
data class ImportStats(
    val totalEntries: Int,
    val parsed: Int,
    val duplicates: Int,
    val unsupported: Int,
    val errors: Int,
)

data class ImportResult(
    val configs: List<ProxyConfig>,
    val duplicates: List<DuplicateEntry>,
    val unsupported: List<UnsupportedEntry>,
    val errors: List<ParseError>,
    val stats: ImportStats,
)

/** Hard bounds applied to any imported content. */
data class ImportLimits(
    val maxBytes: Long = 5L * 1024 * 1024,
    val maxEntries: Int = 10_000,
    val maxLineLength: Int = 8_192,
) {
    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(maxLineLength > 0) { "maxLineLength must be positive" }
    }
}
