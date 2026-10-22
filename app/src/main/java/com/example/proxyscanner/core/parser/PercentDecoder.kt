package com.example.proxyscanner.core.parser

import java.io.ByteArrayOutputStream

/**
 * Percent-decoding for URI components. '+' is left unchanged (it is not a space
 * in share-link fragments or user info).
 */
internal object PercentDecoder {

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    /** Returns null when a '%' is not followed by two hex digits. */
    fun decode(text: String): String? {
        if (text.indexOf('%') < 0) return text
        val out = ByteArrayOutputStream(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '%') {
                if (i + 2 >= text.length) return null
                val hi = hexValue(text[i + 1])
                val lo = hexValue(text[i + 2])
                if (hi < 0 || lo < 0) return null
                out.write(hi * 16 + lo)
                i += 3
            } else {
                var j = i
                while (j < text.length && text[j] != '%') j++
                out.write(text.substring(i, j).toByteArray(Charsets.UTF_8))
                i = j
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /** Decodes when valid, otherwise returns the text unchanged. */
    fun decodeOrRaw(text: String): String = decode(text) ?: text
}
