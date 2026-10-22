package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.DuplicateEntry
import com.example.proxyscanner.core.model.ImportLimits
import com.example.proxyscanner.core.model.ImportResult
import com.example.proxyscanner.core.model.ImportStats
import com.example.proxyscanner.core.model.ParseError
import com.example.proxyscanner.core.model.ParseErrorCode
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.UnsupportedEntry
import com.example.proxyscanner.core.model.UnsupportedReason
import com.example.proxyscanner.core.security.Secret

/** The link parsers available in this version. */
fun defaultLinkParsers(): List<LinkParser> = listOf(
    VlessLinkParser(),
    VmessLinkParser(),
    ShadowsocksLinkParser(),
    TrojanLinkParser(),
)

/**
 * detect format -> split lines -> parse -> normalize -> validate -> de-duplicate.
 *
 * Never throws for malformed input: every problem becomes a [ParseError] or an
 * [UnsupportedEntry] inside the returned [ImportResult]. No input text, URI or
 * credential is copied into any message. Supported formats: plain link list
 * (one link per line; blank lines and lines starting with `#` or `//` are
 * skipped) and a Base64-encoded link list. JSON files are reported as unsupported.
 */
class DefaultConfigParser(
    private val detector: FormatDetector = DefaultFormatDetector(),
    private val parsers: List<LinkParser> = defaultLinkParsers(),
    private val normalizer: ConfigNormalizer = DefaultConfigNormalizer(),
    private val validator: ConfigValidator = DefaultConfigValidator(),
) : ConfigParser {

    private class Collector {
        val configs = mutableListOf<ProxyConfig>()
        val duplicates = mutableListOf<DuplicateEntry>()
        val unsupported = mutableListOf<UnsupportedEntry>()
        val errors = mutableListOf<ParseError>()
        val fingerprintIndex = HashMap<String, Int>()
        var totalEntries = 0

        fun toResult(): ImportResult = ImportResult(
            configs = configs.toList(),
            duplicates = duplicates.toList(),
            unsupported = unsupported.toList(),
            errors = errors.toList(),
            stats = ImportStats(
                totalEntries = totalEntries,
                parsed = configs.size + duplicates.size,
                duplicates = duplicates.size,
                unsupported = unsupported.size,
                errors = errors.size,
            ),
        )
    }

    override fun parse(content: String, limits: ImportLimits): ImportResult {
        val collector = Collector()
        val text = prepareText(content, limits, collector)
        if (text != null) processLines(text, limits, collector)
        return collector.toResult()
    }

    private fun prepareText(content: String, limits: ImportLimits, collector: Collector): String? {
        if (content.isBlank()) {
            collector.errors += ParseError(null, ParseErrorCode.EMPTY_ENTRY, "The content is empty")
            return null
        }
        if (content.length.toLong() > limits.maxBytes) {
            collector.errors += ParseError(null, ParseErrorCode.CONTENT_TOO_LARGE, "The content exceeds the size limit")
            return null
        }
        return when (detector.detect(content)) {
            InputFormat.JSON -> {
                collector.errors += ParseError(
                    null,
                    ParseErrorCode.UNSUPPORTED_FORMAT,
                    "JSON files are not supported yet. Use a text file with one link per line.",
                )
                null
            }
            InputFormat.BASE64_LINK_LIST -> {
                val decoded = LenientBase64.decode(content)?.let { StrictUtf8.decode(it) }
                if (decoded == null) {
                    collector.errors += ParseError(null, ParseErrorCode.INVALID_BASE64, "The Base64 content could not be decoded")
                }
                decoded
            }
            InputFormat.PLAIN_LINK_LIST, InputFormat.UNKNOWN -> content
        }
    }

    private fun processLines(text: String, limits: ImportLimits, collector: Collector) {
        val lines = text.split("\r\n", "\n", "\r")
        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
            if (collector.totalEntries >= limits.maxEntries) {
                collector.errors += ParseError(
                    null,
                    ParseErrorCode.TOO_MANY_ENTRIES,
                    "More than ${limits.maxEntries} entries; the rest were ignored",
                )
                return
            }
            collector.totalEntries++
            val lineNumber = index + 1
            if (line.length > limits.maxLineLength) {
                collector.errors += ParseError(lineNumber, ParseErrorCode.ENTRY_TOO_LONG, "Line is too long")
                continue
            }
            processEntry(line, lineNumber, collector)
        }
    }

    private fun processEntry(line: String, lineNumber: Int, collector: Collector) {
        val scheme = LinkSyntax.schemeOf(line)
        if (scheme == null) {
            collector.errors += ParseError(
                lineNumber,
                ParseErrorCode.MALFORMED_URI,
                "Line is not a proxy link (expected scheme://...)",
            )
            return
        }
        val parser = parsers.firstOrNull { it.accepts(line) }
        if (parser == null) {
            val known = Protocol.entries.any { scheme in it.uriSchemes }
            collector.unsupported += UnsupportedEntry(
                sourceLine = lineNumber,
                reason = if (known) UnsupportedReason.PROTOCOL_NOT_IMPLEMENTED else UnsupportedReason.UNKNOWN_SCHEME,
                schemeHint = scheme,
            )
            return
        }
        val outcome = try {
            parser.parse(line, lineNumber)
        } catch (e: Exception) {
            ParseOutcome.Failed(ParseError(lineNumber, ParseErrorCode.UNKNOWN, "Unexpected error while parsing this line"))
        }
        when (outcome) {
            is ParseOutcome.Parsed -> accept(outcome.config, line, lineNumber, collector)
            is ParseOutcome.Unsupported -> {
                collector.unsupported += outcome.entry
            }
            is ParseOutcome.Failed -> {
                collector.errors += outcome.error
            }
        }
    }

    private fun accept(parsed: ProxyConfig, line: String, lineNumber: Int, collector: Collector) {
        val config = try {
            normalizer.normalize(parsed).copy(original = Secret(line))
        } catch (e: IllegalArgumentException) {
            collector.errors += ParseError(lineNumber, ParseErrorCode.INVALID_FIELD, "A field has an invalid value")
            return
        }
        val issues = validator.validate(config)
        if (issues.isNotEmpty()) {
            val fields = issues.joinToString(", ") { it.field }
            collector.errors += ParseError(lineNumber, ParseErrorCode.MISSING_FIELD, "Configuration is incomplete: $fields")
            return
        }
        val keptIndex = collector.fingerprintIndex[config.fingerprint]
        if (keptIndex != null) {
            collector.duplicates += DuplicateEntry(config.fingerprint, lineNumber, keptIndex)
        } else {
            collector.fingerprintIndex[config.fingerprint] = collector.configs.size
            collector.configs += config
        }
    }
}
