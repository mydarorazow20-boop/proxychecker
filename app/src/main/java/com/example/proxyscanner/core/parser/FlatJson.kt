package com.example.proxyscanner.core.parser

/**
 * Minimal reader for ONE flat JSON object (the VMess share-link payload).
 *
 * Values: strings are unescaped; numbers and booleans are returned as their
 * literal text; null stays null. Nested objects or arrays, trailing data and any
 * syntax error make the whole parse return null. It never throws.
 */
internal object FlatJson {

    private const val MAX_KEYS = 64

    fun parseObject(text: String): Map<String, String?>? = Reader(text).readObject()

    private class Value(val text: String?)

    private class Reader(private val s: String) {
        private var i = 0

        fun readObject(): Map<String, String?>? {
            skipWhitespace()
            if (!consume('{')) return null
            val out = LinkedHashMap<String, String?>()
            skipWhitespace()
            if (!consume('}')) {
                while (true) {
                    skipWhitespace()
                    val key = readString() ?: return null
                    skipWhitespace()
                    if (!consume(':')) return null
                    skipWhitespace()
                    val value = readValue() ?: return null
                    if (out.size >= MAX_KEYS) return null
                    out[key] = value.text
                    skipWhitespace()
                    if (consume(',')) continue
                    if (consume('}')) break
                    return null
                }
            }
            skipWhitespace()
            return if (i == s.length) out else null
        }

        private fun skipWhitespace() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        private fun consume(c: Char): Boolean {
            if (i < s.length && s[i] == c) {
                i++
                return true
            }
            return false
        }

        private fun consumeWord(word: String): Boolean {
            if (s.startsWith(word, i)) {
                i += word.length
                return true
            }
            return false
        }

        private fun readValue(): Value? {
            if (i >= s.length) return null
            return when (s[i]) {
                '"' -> readString()?.let { Value(it) }
                't' -> if (consumeWord("true")) Value("true") else null
                'f' -> if (consumeWord("false")) Value("false") else null
                'n' -> if (consumeWord("null")) Value(null) else null
                '{', '[' -> null
                else -> readNumber()?.let { Value(it) }
            }
        }

        private fun readNumber(): String? {
            val start = i
            while (i < s.length && (s[i] in '0'..'9' || s[i] == '-' || s[i] == '+' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++
            return if (i == start) null else s.substring(start, i)
        }

        private fun readString(): String? {
            if (!consume('"')) return null
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i]
                i++
                if (c == '"') return sb.toString()
                if (c < ' ') return null
                if (c != '\\') {
                    sb.append(c)
                    continue
                }
                if (i >= s.length) return null
                val e = s[i]
                i++
                when (e) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (i + 4 > s.length) return null
                        val code = s.substring(i, i + 4).toIntOrNull(16) ?: return null
                        sb.append(code.toChar())
                        i += 4
                    }
                    else -> return null
                }
            }
            return null
        }
    }
}
