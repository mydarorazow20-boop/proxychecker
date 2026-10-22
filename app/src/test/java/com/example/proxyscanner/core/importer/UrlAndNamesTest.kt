package com.example.proxyscanner.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlAndNamesTest {

    private val validator = UrlSyntaxValidator()

    private fun assertRejected(input: String, code: ImportFailureCode) {
        val check = validator.check(input)
        assertTrue("expected rejection of input but got $check", check is UrlCheck.Rejected)
        check as UrlCheck.Rejected
        assertEquals(code, check.code)
        if (input.isNotBlank()) assertFalse(check.reason.contains(input.trim()))
    }

    @Test
    fun validHttpsUrlsAreAllowed() {
        assertEquals(UrlCheck.Allowed, validator.check("https://example.com/list.txt"))
        assertEquals(UrlCheck.Allowed, validator.check("  HTTPS://Sub.Example.com:8443/a/b?token=1#x  "))
        assertEquals(UrlCheck.Allowed, validator.check("https://8.8.8.8/list"))
    }

    @Test
    fun otherSchemesAreRejected() {
        assertRejected("http://example.com/a", ImportFailureCode.UNSUPPORTED_SCHEME)
        assertRejected("ftp://example.com/a", ImportFailureCode.UNSUPPORTED_SCHEME)
        assertRejected("file:///sdcard/a.txt", ImportFailureCode.UNSUPPORTED_SCHEME)
        assertRejected("example.com/list.txt", ImportFailureCode.UNSUPPORTED_SCHEME)
        assertRejected("vless://abc@example.com:443", ImportFailureCode.UNSUPPORTED_SCHEME)
    }

    @Test
    fun malformedUrlsAreRejected() {
        assertRejected("", ImportFailureCode.URL_NOT_ALLOWED)
        assertRejected("https://exa mple.com", ImportFailureCode.URL_NOT_ALLOWED)
        assertRejected("https://example.com:99999/", ImportFailureCode.URL_NOT_ALLOWED)
        assertRejected("https://", ImportFailureCode.URL_NOT_ALLOWED)
        assertRejected("https://user:pass@example.com/", ImportFailureCode.URL_NOT_ALLOWED)
        assertRejected("https://intranet/list", ImportFailureCode.URL_NOT_ALLOWED)
        assertRejected("https://[2001:db8::1]/x", ImportFailureCode.URL_NOT_ALLOWED)
        assertRejected("https://example.com/" + "a".repeat(3000), ImportFailureCode.URL_NOT_ALLOWED)
    }

    @Test
    fun localAndPrivateAddressesAreRejected() {
        assertRejected("https://localhost/x", ImportFailureCode.PRIVATE_ADDRESS_BLOCKED)
        assertRejected("https://app.localhost/x", ImportFailureCode.PRIVATE_ADDRESS_BLOCKED)
        for (ip in listOf("127.0.0.1", "10.1.2.3", "192.168.1.1", "172.16.0.1", "172.31.255.1", "169.254.169.254", "0.0.0.0", "100.64.0.1", "224.0.0.1")) {
            assertRejected("https://$ip/x", ImportFailureCode.PRIVATE_ADDRESS_BLOCKED)
        }
        assertEquals(UrlCheck.Allowed, validator.check("https://172.32.0.1/x"))
        assertRejected("https://999.1.1.1/x", ImportFailureCode.URL_NOT_ALLOWED)
        assertRejected("https://2130706433/x", ImportFailureCode.URL_NOT_ALLOWED)
    }

    @Test
    fun displayNamesAreSanitized() {
        assertNull(DisplayNames.sanitize(null))
        assertNull(DisplayNames.sanitize("  \n "))
        assertEquals("list.txt", DisplayNames.sanitize(" list.txt\n"))
        assertEquals("abc.txt", DisplayNames.sanitize("a\u0000b\u202Ec.txt"))
        val long = DisplayNames.sanitize("x".repeat(200))!!
        assertEquals(80, long.length)
        assertTrue(long.endsWith("\u2026"))
    }
}
