package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.ImportLimits
import com.example.proxyscanner.core.model.ImportResult
import com.example.proxyscanner.core.model.ParseErrorCode
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.UnsupportedReason
import com.example.proxyscanner.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultConfigParserTest {

    private val parser = DefaultConfigParser()
    private val limits = ImportLimits()

    private fun parse(vararg lines: String, limits: ImportLimits = this.limits): ImportResult =
        parser.parse(lines.joinToString("\n"), limits)

    // ---- valid schemes ----

    @Test
    fun allFourSupportedSchemesAreParsed() {
        val result = parse(Fixtures.vless(), Fixtures.vmess(), Fixtures.ss(), Fixtures.trojan())
        assertEquals(4, result.configs.size)
        assertEquals(
            setOf(Protocol.VLESS, Protocol.VMESS, Protocol.SHADOWSOCKS, Protocol.TROJAN),
            result.configs.map { it.protocol }.toSet(),
        )
        assertTrue(result.errors.isEmpty())
        assertTrue(result.unsupported.isEmpty())
        assertEquals(4, result.stats.totalEntries)
        assertEquals(4, result.stats.parsed)
        assertTrue(result.configs.all { it.fingerprint.length == 64 })
    }

    @Test
    fun originalLinkIsKeptOnlyAsSecret() {
        val link = Fixtures.trojan()
        val config = parse(link).configs.single()
        assertEquals(link, config.original!!.reveal())
        assertFalse(config.toString().contains(Fixtures.PASSWORD))
    }

    @Test
    fun blankLinesCommentsAndCrLfAreHandled() {
        val text = "# my list\r\n\r\n" + Fixtures.vless() + "\r\n// another comment\r\n   \r\n" + Fixtures.trojan() + "\r\n"
        val result = parser.parse(text, limits)
        assertEquals(2, result.configs.size)
        assertEquals(2, result.stats.totalEntries)
        assertTrue(result.errors.isEmpty())
    }

    // ---- malformed / empty ----

    @Test
    fun emptyAndBlankContentReportAnErrorWithoutThrowing() {
        for (content in listOf("", "   ", "\n\n\t\n")) {
            val result = parser.parse(content, limits)
            assertTrue(result.configs.isEmpty())
            assertEquals(ParseErrorCode.EMPTY_ENTRY, result.errors.single().code)
            assertEquals(0, result.stats.totalEntries)
        }
    }

    @Test
    fun malformedLinesAreReportedWithLineNumbers() {
        val result = parse(
            Fixtures.vless(),
            "this is not a link",
            "vless://${Fixtures.UUID}@${Fixtures.HOST}:notaport",
            "trojan://",
            Fixtures.trojan(),
        )
        assertEquals(2, result.configs.size)
        assertEquals(3, result.errors.size)
        assertEquals(listOf(2, 3, 4), result.errors.map { it.sourceLine })
        assertEquals(ParseErrorCode.MALFORMED_URI, result.errors[0].code)
        assertEquals(ParseErrorCode.INVALID_PORT, result.errors[1].code)
        assertEquals(5, result.stats.totalEntries)
    }

    @Test
    fun messagesNeverContainInputSecrets() {
        val result = parse(
            "garbage ${Fixtures.PASSWORD}",
            "vless://${Fixtures.UUID}@${Fixtures.HOST}:badport",
            "vless://not-a-uuid@${Fixtures.HOST}:443",
            "wireguard://${Fixtures.PASSWORD}@${Fixtures.HOST}:51820",
            "vmess://" + Fixtures.b64("{\"add\":\"${Fixtures.HOST}\",\"id\":\"${Fixtures.UUID}\"}"),
            "ss://${Fixtures.PASSWORD}@${Fixtures.HOST}",
        )
        val messages = result.errors.map { it.safeMessage }
        assertTrue(messages.isNotEmpty())
        for (message in messages) {
            for (secret in listOf(Fixtures.PASSWORD, Fixtures.UUID, Fixtures.HOST)) {
                assertFalse("leak of $secret in: $message", message.contains(secret))
            }
        }
    }

    // ---- unsupported ----

    @Test
    fun unsupportedSchemesAreReportedSeparately() {
        val result = parse(
            "wireguard://abc@${Fixtures.HOST}:51820",
            "hysteria2://${Fixtures.PASSWORD}@${Fixtures.HOST}:443",
            "socks5://${Fixtures.HOST}:1080",
            "http://example.invalid/page",
            Fixtures.vless(),
        )
        assertEquals(1, result.configs.size)
        assertEquals(4, result.unsupported.size)
        assertTrue(result.errors.isEmpty())
        assertEquals(UnsupportedReason.UNKNOWN_SCHEME, result.unsupported[0].reason)
        assertEquals(UnsupportedReason.PROTOCOL_NOT_IMPLEMENTED, result.unsupported[1].reason)
        assertEquals("hysteria2", result.unsupported[1].schemeHint)
        assertEquals(UnsupportedReason.PROTOCOL_NOT_IMPLEMENTED, result.unsupported[2].reason)
        assertEquals(1, result.unsupported[0].sourceLine)
    }

    @Test
    fun unsupportedOptionsOfKnownSchemesAreCountedAsUnsupported() {
        val result = parse(Fixtures.vless(query = "type=kcp"), Fixtures.ss().replace("#", "?plugin=x#"))
        assertTrue(result.configs.isEmpty())
        assertEquals(2, result.unsupported.size)
        assertTrue(result.unsupported.all { it.reason == UnsupportedReason.UNSUPPORTED_OPTION })
    }

    @Test
    fun jsonContentIsRejectedAsUnsupportedFormat() {
        val result = parser.parse("{\"outbounds\":[{\"protocol\":\"vless\"}]}", limits)
        assertTrue(result.configs.isEmpty())
        assertEquals(ParseErrorCode.UNSUPPORTED_FORMAT, result.errors.single().code)
    }

    // ---- duplicates ----

    @Test
    fun duplicatesAreDetectedByFingerprintNotByText() {
        val first = Fixtures.vless(host = "Node1.Example.Invalid", remark = "first")
        val second = Fixtures.vless(host = "node1.example.invalid", remark = "second name")
        val different = Fixtures.vless(port = 8443)
        val result = parse(first, "# note", second, different, first)
        assertEquals(2, result.configs.size)
        assertEquals(2, result.duplicates.size)
        assertEquals(listOf(3, 5), result.duplicates.map { it.sourceLine })
        assertTrue(result.duplicates.all { it.keptIndex == 0 })
        assertEquals("first", result.configs[0].remark)
        assertEquals(4, result.stats.parsed)
        assertEquals(2, result.stats.duplicates)
    }

    @Test
    fun differentCredentialsAreNotDuplicates() {
        val result = parse(Fixtures.trojan(password = "one"), Fixtures.trojan(password = "two"))
        assertEquals(2, result.configs.size)
        assertTrue(result.duplicates.isEmpty())
        assertNotEquals(result.configs[0].fingerprint, result.configs[1].fingerprint)
    }

    // ---- Base64 lists ----

    @Test
    fun base64LinkListIsDecodedAndParsed() {
        val content = Fixtures.b64(listOf(Fixtures.vless(), Fixtures.ss(), "wireguard://x@y.invalid:1").joinToString("\n"))
        val result = parser.parse(content, limits)
        assertEquals(2, result.configs.size)
        assertEquals(1, result.unsupported.size)
    }

    // ---- limits ----

    @Test
    fun entryLimitStopsParsingWithAnError() {
        val limited = ImportLimits(maxEntries = 2)
        val result = parse(Fixtures.vless(port = 1), Fixtures.vless(port = 2), Fixtures.vless(port = 3), limits = limited)
        assertEquals(2, result.configs.size)
        assertEquals(ParseErrorCode.TOO_MANY_ENTRIES, result.errors.single().code)
    }

    @Test
    fun lineLengthLimitIsEnforcedPerLine() {
        val limited = ImportLimits(maxLineLength = 60)
        val result = parse(Fixtures.vless(), "trojan://p@h.invalid:1", limits = limited)
        assertEquals(1, result.configs.size)
        assertEquals(ParseErrorCode.ENTRY_TOO_LONG, result.errors.single().code)
        assertEquals(1, result.errors.single().sourceLine)
    }

    @Test
    fun oversizedContentIsRejectedBeforeParsing() {
        val limited = ImportLimits(maxBytes = 10)
        val result = parser.parse(Fixtures.vless(), limited)
        assertTrue(result.configs.isEmpty())
        assertEquals(ParseErrorCode.CONTENT_TOO_LARGE, result.errors.single().code)
    }

    // ---- format detector ----

    @Test
    fun formatDetectorRecognizesFormats() {
        val detector = DefaultFormatDetector()
        assertEquals(InputFormat.UNKNOWN, detector.detect("  \n"))
        assertEquals(InputFormat.JSON, detector.detect(" {\"a\":1}"))
        assertEquals(InputFormat.JSON, detector.detect("[1,2]"))
        assertEquals(InputFormat.PLAIN_LINK_LIST, detector.detect(Fixtures.vless()))
        assertEquals(InputFormat.BASE64_LINK_LIST, detector.detect(Fixtures.b64(Fixtures.vless())))
        assertEquals(InputFormat.PLAIN_LINK_LIST, detector.detect("just some words"))
    }

    // ---- normalizer / fingerprint ----

    @Test
    fun fingerprintIsStableAndIgnoresRemarkAndHostCase() {
        val normalizer = DefaultConfigNormalizer()
        val a = parse(Fixtures.vless(host = "NODE1.example.invalid", remark = "a")).configs.single()
        val b = parse(Fixtures.vless(host = "node1.example.invalid", remark = "b")).configs.single()
        assertEquals(a.fingerprint, b.fingerprint)
        assertEquals(a.fingerprint, normalizer.fingerprint(a))
        assertFalse(a.fingerprint.contains(Fixtures.UUID))
        assertTrue(a.fingerprint.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
