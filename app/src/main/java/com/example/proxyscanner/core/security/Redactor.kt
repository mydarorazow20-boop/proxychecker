package com.example.proxyscanner.core.security

/**
 * Best-effort redaction for free text that may end up in errors or diagnostics.
 *
 * This is a safety net, not a substitute for [Secret]: code must not put raw
 * credentials into messages in the first place.
 */
object Redactor {
    const val PLACEHOLDER = "[REDACTED]"

    // Any scheme://... token. Whole remainder is dropped: proxy links and
    // subscription URLs routinely carry credentials or tokens.
    private val urlRegex = Regex("""(?i)\b([a-z][a-z0-9+.\-]*)://\S+""")

    // key=value / key: value pairs for credential-like keys.
    private val keyValueRegex = Regex(
        """(?i)\b(password|passwd|pwd|token|secret|auth|psk|uuid|id|key|private[-_]?key|obfs[-_]?password)(\s*["']?\s*[=:]\s*["']?)([^\s,"'&}]+)""",
    )

    // Standalone UUIDs.
    private val uuidRegex = Regex(
        """(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""",
    )

    fun redact(text: String): String {
        var out = urlRegex.replace(text) { match -> match.groupValues[1] + "://" + PLACEHOLDER }
        out = keyValueRegex.replace(out) { match ->
            match.groupValues[1] + match.groupValues[2] + PLACEHOLDER
        }
        out = uuidRegex.replace(out) { PLACEHOLDER }
        return out
    }
}
