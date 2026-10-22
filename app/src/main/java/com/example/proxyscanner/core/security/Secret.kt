package com.example.proxyscanner.core.security

/**
 * Wraps a sensitive string (UUID, password, token, original share link).
 *
 * toString() never reveals the value, so accidentally interpolating a model
 * object into a log line or error message cannot leak credentials.
 * Call [reveal] only at the exact point where the raw value is required
 * (engine configuration, explicit user-requested export).
 */
@JvmInline
value class Secret(private val raw: String) {
    fun reveal(): String = raw

    override fun toString(): String = Redactor.PLACEHOLDER
}
