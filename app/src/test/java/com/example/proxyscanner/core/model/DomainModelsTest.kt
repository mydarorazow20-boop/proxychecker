package com.example.proxyscanner.core.model

import com.example.proxyscanner.core.security.Secret
import com.example.proxyscanner.core.testing.ScanAvailability
import com.example.proxyscanner.core.testing.evaluateScanAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DomainModelsTest {

    private fun config(port: Int = 443, address: String = "host.example.invalid", fingerprint: String = "abcdef0123456789") =
        ProxyConfig(
            fingerprint = fingerprint,
            protocol = Protocol.VLESS,
            address = address,
            port = port,
            credentials = Credentials(userId = Secret("11111111-2222-3333-4444-555555555555")),
        )

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun proxyConfigValidatesItsInvariants() {
        assertRejected { config(port = 0) }
        assertRejected { config(port = 65536) }
        assertRejected { config(address = " ") }
        assertRejected { config(fingerprint = "") }
        assertEquals(65535, config(port = 65535).port)
    }

    @Test
    fun safeLabelHasNoHostOrCredentials() {
        val label = config().safeLabel()
        assertEquals("VLESS:443#abcdef01", label)
        assertFalse(label.contains("example"))
        assertFalse(config().toString().contains("11111111"))
    }

    @Test
    fun plannedProtocolsAreRepresentable() {
        val schemes = Protocol.entries.flatMap { it.uriSchemes }
        for (s in listOf("vless", "vmess", "ss", "trojan")) assertTrue(schemes.contains(s))
    }

    @Test
    fun importLimitsRejectNonPositiveValues() {
        assertRejected { ImportLimits(maxBytes = 0) }
        assertRejected { ImportLimits(maxEntries = 0) }
        assertRejected { ImportLimits(maxLineLength = -1) }
    }

    @Test
    fun workingResultRequiresVerifiedProbe() {
        assertRejected { ProxyTestResult(configFingerprint = "fp", status = TestStatus.WORKING) }
        assertRejected { ProxyTestResult(configFingerprint = "fp", status = TestStatus.FAILED) }
        assertEquals(TestStatus.PENDING, ProxyTestResult.pending("fp").status)
    }

    @Test
    fun scanAvailabilityFollowsTheRules() {
        assertEquals(ScanAvailability.NO_CONFIGS, evaluateScanAvailability(0, true, true, false))
        assertEquals(ScanAvailability.ENGINE_UNAVAILABLE, evaluateScanAvailability(3, false, true, false))
        assertEquals(ScanAvailability.PROBE_ENDPOINT_MISSING, evaluateScanAvailability(3, true, false, false))
        assertEquals(ScanAvailability.BUSY, evaluateScanAvailability(3, true, true, true))
        assertEquals(ScanAvailability.READY, evaluateScanAvailability(3, true, true, false))
    }
}
