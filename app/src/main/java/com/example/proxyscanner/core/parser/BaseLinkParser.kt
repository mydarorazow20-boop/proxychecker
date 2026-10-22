package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.ParseError
import com.example.proxyscanner.core.model.ParseErrorCode
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.UnsupportedEntry

/** Scheme sniffing shared by the facade and the link parsers. */
object LinkSyntax {
    private val schemeRegex = Regex("^([A-Za-z][A-Za-z0-9+.-]{0,31})://")

    /** Lower-case URI scheme of [line], or null if it does not start with `scheme://`. */
    fun schemeOf(line: String): String? =
        schemeRegex.find(line.trimStart())?.groupValues?.get(1)?.lowercase()
}

/**
 * Template for one-protocol parsers: subclasses build a [ProxyConfig] from the
 * trimmed link and throw [LinkFormatException] / [UnsupportedLinkOption]; this
 * class converts those into [ParseOutcome]. Nothing here logs or echoes input.
 */
abstract class BaseLinkParser(final override val protocol: Protocol) : LinkParser {

    override fun accepts(line: String): Boolean {
        val scheme = LinkSyntax.schemeOf(line) ?: return false
        return scheme in protocol.uriSchemes
    }

    override fun parse(line: String, lineNumber: Int?): ParseOutcome {
        return try {
            ParseOutcome.Parsed(build(line.trim()))
        } catch (e: LinkFormatException) {
            ParseOutcome.Failed(ParseError(lineNumber, e.code, e.safeMessage))
        } catch (e: UnsupportedLinkOption) {
            ParseOutcome.Unsupported(
                UnsupportedEntry(lineNumber, e.reason, protocol.uriSchemes.first()),
            )
        } catch (e: IllegalArgumentException) {
            ParseOutcome.Failed(
                ParseError(lineNumber, ParseErrorCode.INVALID_FIELD, "A field has an invalid value"),
            )
        }
    }

    /** Builds a configuration with fingerprint [LinkFields.PENDING_FINGERPRINT]. */
    internal abstract fun build(link: String): ProxyConfig
}
