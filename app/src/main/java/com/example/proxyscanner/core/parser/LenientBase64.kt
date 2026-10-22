package com.example.proxyscanner.core.parser

import java.io.ByteArrayOutputStream

/**
 * Pure-Kotlin Base64 decoder for share links.
 *
 * `java.util.Base64` needs API 26 and `android.util.Base64` is not available in
 * JVM unit tests, so this small decoder is used instead. It accepts the standard
 * and URL-safe alphabets (also mixed), optional padding and embedded whitespace.
 * It returns null for any other character or an impossible length.
 */
object LenientBase64 {

    private fun valueOf(c: Char): Int = when (c) {
        in 'A'..'Z' -> c - 'A'
        in 'a'..'z' -> c - 'a' + 26
        in '0'..'9' -> c - '0' + 52
        '+', '-' -> 62
        '/', '_' -> 63
        else -> -1
    }

    fun decode(input: String): ByteArray? {
        val out = ByteArrayOutputStream(input.length * 3 / 4 + 3)
        var buffer = 0
        var bits = 0
        var dataChars = 0
        var paddingSeen = false
        for (ch in input) {
            if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') continue
            if (ch == '=') {
                paddingSeen = true
                continue
            }
            if (paddingSeen) return null
            val v = valueOf(ch)
            if (v < 0) return null
            buffer = (buffer shl 6) or v
            bits += 6
            dataChars++
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        // A single leftover character cannot encode a byte.
        if (dataChars % 4 == 1) return null
        return out.toByteArray()
    }
}
