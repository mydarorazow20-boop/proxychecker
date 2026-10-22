package com.example.proxyscanner.core.parser

/** Pure heuristic: JSON, plain link list, Base64-wrapped link list. */
class DefaultFormatDetector : FormatDetector {

    override fun detect(content: String): InputFormat {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return InputFormat.UNKNOWN
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return InputFormat.JSON
        if (trimmed.contains("://")) return InputFormat.PLAIN_LINK_LIST
        val decoded = LenientBase64.decode(trimmed)
        if (decoded != null) {
            val text = StrictUtf8.decode(decoded)
            if (text != null && text.contains("://")) return InputFormat.BASE64_LINK_LIST
        }
        // Not recognizable: let the line parser report each line instead of guessing.
        return InputFormat.PLAIN_LINK_LIST
    }
}
