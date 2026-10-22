package com.example.proxyscanner.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    @Test
    fun proxyLinkIsFullyRedacted() {
        val input = "failed to parse vless://00000000-0000-0000-0000-000000000000@host.invalid:443?type=ws#name"
        val out = Redactor.redact(input)
        assertTrue(out.contains("vless://[REDACTED]"))
        assertFalse(out.contains("00000000"))
        assertFalse(out.contains("host.invalid"))
    }

    @Test
    fun subscriptionUrlTokenIsRedacted() {
        val out = Redactor.redact("fetch https://sub.invalid/path?token=abc123 failed")
        assertEquals("fetch https://[REDACTED] failed", out)
    }

    @Test
    fun keyValuePairsAreRedacted() {
        assertEquals("password=[REDACTED]", Redactor.redact("password=hunter2"))
        assertEquals("token: [REDACTED]", Redactor.redact("token: abc123"))
    }

    @Test
    fun standaloneUuidIsRedacted() {
        val out = Redactor.redact("uuid 123e4567-e89b-12d3-a456-426614174000 rejected")
        assertFalse(out.contains("123e4567"))
    }

    @Test
    fun plainTextIsUnchanged() {
        assertEquals("connection timed out after 10000 ms", Redactor.redact("connection timed out after 10000 ms"))
    }

    @Test
    fun secretNeverRevealsValueInToString() {
        val secret = Secret("super-secret-value")
        assertEquals("[REDACTED]", secret.toString())
        assertFalse("value: $secret".contains("super-secret-value"))
        assertEquals("super-secret-value", secret.reveal())
    }
}
