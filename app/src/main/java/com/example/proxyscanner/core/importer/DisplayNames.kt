package com.example.proxyscanner.core.importer

import com.example.proxyscanner.core.parser.RawUriParser

/** Makes provider-supplied file names safe to display (no control or bidi characters, bounded length). */
object DisplayNames {
    private const val MAX_LENGTH = 80

    fun sanitize(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.filter { !it.isISOControl() && !isBidiControl(it) }.trim()
        if (cleaned.isEmpty()) return null
        return if (cleaned.length > MAX_LENGTH) cleaned.take(MAX_LENGTH - 1) + "\u2026" else cleaned
    }

    /** Host name of [url] for display. Never includes user info, path or query (they may hold secrets). */
    fun hostOf(url: String): String? = try {
        sanitize(RawUriParser.parse(url.trim()).host)
    } catch (e: Exception) {
        null
    }

    private fun isBidiControl(c: Char): Boolean =
        c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069' || c == '\u200E' || c == '\u200F'
}
