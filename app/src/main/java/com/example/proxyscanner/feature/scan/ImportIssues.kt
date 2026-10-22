package com.example.proxyscanner.feature.scan

import com.example.proxyscanner.core.model.ImportResult
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.UnsupportedEntry
import com.example.proxyscanner.core.model.UnsupportedReason

/** Turns a parse report into short, safe, line-numbered issues for the UI. */
object ImportIssues {
    const val MAX_LISTED = 10

    /** Scheme names that are harmless to display; anything else is shown generically. */
    private val displayableSchemes: Set<String> =
        Protocol.entries.flatMap { it.uriSchemes }.toSet() +
            setOf("wireguard", "wg", "ssr", "hysteria", "http", "https")

    fun report(result: ImportResult): ImportReport {
        val all = buildList<ImportIssue> {
            result.errors.forEach { add(ImportIssue(it.sourceLine, it.safeMessage)) }
            result.unsupported.forEach { add(ImportIssue(it.sourceLine, describe(it))) }
        }.sortedWith(compareBy(nullsLast<Int>()) { it.line })
        return ImportReport(
            stats = result.stats,
            issues = all.take(MAX_LISTED),
            omittedIssues = (all.size - MAX_LISTED).coerceAtLeast(0),
        )
    }

    fun describe(entry: UnsupportedEntry): String {
        val scheme = entry.schemeHint?.takeIf { it in displayableSchemes }
        return when (entry.reason) {
            UnsupportedReason.UNKNOWN_SCHEME ->
                if (scheme != null) "Unsupported link type: $scheme://" else "Unsupported link type"
            UnsupportedReason.PROTOCOL_NOT_IMPLEMENTED ->
                if (scheme != null) "$scheme:// links are not supported yet" else "This protocol is not supported yet"
            UnsupportedReason.UNSUPPORTED_OPTION ->
                if (scheme != null) "$scheme:// link uses an option that is not supported" else "Link uses an option that is not supported"
        }
    }
}
