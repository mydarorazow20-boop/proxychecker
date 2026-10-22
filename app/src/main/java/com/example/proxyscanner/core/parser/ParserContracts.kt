package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.ImportLimits
import com.example.proxyscanner.core.model.ImportResult
import com.example.proxyscanner.core.model.ParseError
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.UnsupportedEntry

enum class InputFormat {
    PLAIN_LINK_LIST,
    BASE64_LINK_LIST,
    JSON,
    UNKNOWN,
}

/** Decides how a blob of imported text should be parsed. Pure function, no I/O. */
interface FormatDetector {
    fun detect(content: String): InputFormat
}

sealed interface ParseOutcome {
    data class Parsed(val config: ProxyConfig) : ParseOutcome

    data class Unsupported(val entry: UnsupportedEntry) : ParseOutcome

    data class Failed(val error: ParseError) : ParseOutcome
}

/**
 * Parses one share link of one protocol. Implementations must be pure, must
 * not log input, and must put only redacted text into [ParseError.safeMessage].
 */
interface LinkParser {
    val protocol: Protocol

    fun accepts(line: String): Boolean

    fun parse(line: String, lineNumber: Int?): ParseOutcome
}

data class ValidationIssue(val field: String, val message: String)

/** Schema-level validation of an already parsed configuration. */
interface ConfigValidator {
    fun validate(config: ProxyConfig): List<ValidationIssue>
}

/** Canonicalizes fields (case, default ports, ordering) and computes the fingerprint. */
interface ConfigNormalizer {
    fun normalize(config: ProxyConfig): ProxyConfig

    /** Hash over normalized fields including credentials; not reversible. */
    fun fingerprint(config: ProxyConfig): String
}

/**
 * Facade: detect format -> split -> parse -> normalize -> validate -> de-duplicate.
 * Must enforce [ImportLimits] and never throw for malformed input; problems are
 * reported inside [ImportResult].
 */
interface ConfigParser {
    fun parse(content: String, limits: ImportLimits): ImportResult
}
