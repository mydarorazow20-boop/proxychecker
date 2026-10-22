package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.ParseErrorCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LowLevelParsingTest {

    private fun assertFormatError(code: ParseErrorCode, block: () -> Unit) {
        try {
            block()
            fail("expected LinkFormatException with $code")
        } catch (e: LinkFormatException) {
            assertEquals(code, e.code)
        }
    }

    // ---- LenientBase64 ----

    @Test
    fun base64DecodesPaddedAndUnpadded() {
        assertEquals("hello", String(LenientBase64.decode("aGVsbG8=")!!, Charsets.UTF_8))
        assertEquals("hello", String(LenientBase64.decode("aGVsbG8")!!, Charsets.UTF_8))
        assertEquals("", String(LenientBase64.decode("")!!, Charsets.UTF_8))
    }

    @Test
    fun base64AcceptsUrlSafeAlphabetAndWhitespace() {
        val expected = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xFE.toByte())
        assertArrayEquals(expected, LenientBase64.decode("+//+"))
        assertArrayEquals(expected, LenientBase64.decode("-__-"))
        assertEquals("hello", String(LenientBase64.decode("aGVs\nbG8=\r\n")!!, Charsets.UTF_8))
    }

    @Test
    fun base64RejectsInvalidInput() {
        assertNull(LenientBase64.decode("aGV\$bG8="))
        assertNull(LenientBase64.decode("a"))
        assertNull(LenientBase64.decode("aG=V"))
    }

    // ---- PercentDecoder ----

    @Test
    fun percentDecodesUtf8AndKeepsPlus() {
        assertEquals("a b", PercentDecoder.decode("a%20b"))
        assertEquals("\u4E2D", PercentDecoder.decode("%E4%B8%AD"))
        assertEquals("a+b", PercentDecoder.decode("a+b"))
    }

    @Test
    fun percentRejectsBrokenEscapes() {
        assertNull(PercentDecoder.decode("100%"))
        assertNull(PercentDecoder.decode("%zz"))
        assertNull(PercentDecoder.decode("%4"))
        assertEquals("%zz", PercentDecoder.decodeOrRaw("%zz"))
    }

    // ---- StrictUtf8 ----

    @Test
    fun strictUtf8RejectsMalformedAndStripsBom() {
        assertNull(StrictUtf8.decode(byteArrayOf(0xC3.toByte(), 0x28)))
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 'a'.code.toByte())
        assertEquals("a", StrictUtf8.decode(withBom))
    }

    // ---- FlatJson ----

    @Test
    fun flatJsonReadsScalarValues() {
        val map = FlatJson.parseObject(" {\"a\":\"x\",\"b\":12,\"c\":true,\"d\":null,\"e\":\"\\u0041\\n\\\"\"} ")
        assertNotNull(map)
        assertEquals("x", map!!["a"])
        assertEquals("12", map["b"])
        assertEquals("true", map["c"])
        assertTrue(map.containsKey("d"))
        assertNull(map["d"])
        assertEquals("A\n\"", map["e"])
    }

    @Test
    fun flatJsonRejectsNestedTrailingAndBrokenInput() {
        assertNull(FlatJson.parseObject("{\"a\":{\"b\":1}}"))
        assertNull(FlatJson.parseObject("{\"a\":[1]}"))
        assertNull(FlatJson.parseObject("{\"a\":1} x"))
        assertNull(FlatJson.parseObject("{\"a\":\"unterminated}"))
        assertNull(FlatJson.parseObject("{\"a\" 1}"))
        assertNull(FlatJson.parseObject("[]"))
        assertNull(FlatJson.parseObject(""))
        assertEquals(emptyMap<String, String?>(), FlatJson.parseObject("{}"))
    }

    // ---- RawUriParser ----

    @Test
    fun uriIsSplitIntoParts() {
        val uri = RawUriParser.parse("VLESS://user@Example.invalid:8443/p/a?Type=ws&sni=a%2Eb&type=ignored#My%20Node")
        assertEquals("vless", uri.scheme)
        assertEquals("user", uri.userInfo)
        assertEquals("Example.invalid", uri.host)
        assertEquals(8443, uri.port)
        assertEquals("/p/a", uri.path)
        assertEquals("ws", uri.query["type"])
        assertEquals("a.b", uri.query["sni"])
        assertEquals("My Node", uri.fragment)
    }

    @Test
    fun ipv6HostAndMissingPortAreHandled() {
        val uri = RawUriParser.parse("trojan://pw@[2001:db8::1]:443")
        assertEquals("2001:db8::1", uri.host)
        assertEquals(443, uri.port)
        assertNull(RawUriParser.parse("trojan://pw@host.example.invalid").port)
    }

    @Test
    fun invalidPortsAndHostsAreRejected() {
        assertFormatError(ParseErrorCode.INVALID_PORT) { RawUriParser.parse("a://x@h.example.invalid:70000") }
        assertFormatError(ParseErrorCode.INVALID_PORT) { RawUriParser.parse("a://x@h.example.invalid:0") }
        assertFormatError(ParseErrorCode.INVALID_PORT) { RawUriParser.parse("a://x@h.example.invalid:") }
        assertFormatError(ParseErrorCode.INVALID_PORT) { RawUriParser.parse("a://x@h.example.invalid:44a") }
        assertFormatError(ParseErrorCode.MALFORMED_URI) { RawUriParser.parse("a://x@bad host.invalid:443") }
        assertFormatError(ParseErrorCode.MALFORMED_URI) { RawUriParser.parse("a://x@a:b:c") }
        assertFormatError(ParseErrorCode.MISSING_FIELD) { RawUriParser.parse("a://") }
        assertFormatError(ParseErrorCode.MALFORMED_URI) { RawUriParser.parse("no-scheme-here") }
    }
}
