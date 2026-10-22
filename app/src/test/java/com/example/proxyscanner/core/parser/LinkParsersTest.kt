package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.ParseErrorCode
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.TransportSecurity
import com.example.proxyscanner.core.model.TransportType
import com.example.proxyscanner.core.model.UnsupportedReason
import com.example.proxyscanner.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LinkParsersTest {

    private val vless = VlessLinkParser()
    private val vmess = VmessLinkParser()
    private val ss = ShadowsocksLinkParser()
    private val trojan = TrojanLinkParser()

    private fun parsed(parser: LinkParser, link: String): ProxyConfig {
        val outcome = parser.parse(link, 1)
        if (outcome !is ParseOutcome.Parsed) fail("expected Parsed but was $outcome")
        return (outcome as ParseOutcome.Parsed).config
    }

    private fun failedCode(parser: LinkParser, link: String): ParseErrorCode {
        val outcome = parser.parse(link, 7)
        if (outcome !is ParseOutcome.Failed) fail("expected Failed but was $outcome")
        val error = (outcome as ParseOutcome.Failed).error
        assertEquals(7, error.sourceLine)
        assertNoSecrets(error.safeMessage)
        return error.code
    }

    private fun assertUnsupported(parser: LinkParser, link: String) {
        val outcome = parser.parse(link, 3)
        if (outcome !is ParseOutcome.Unsupported) fail("expected Unsupported but was $outcome")
        assertEquals(UnsupportedReason.UNSUPPORTED_OPTION, (outcome as ParseOutcome.Unsupported).entry.reason)
    }

    private fun assertNoSecrets(text: String) {
        for (secret in listOf(Fixtures.UUID, Fixtures.PASSWORD, Fixtures.HOST, "sni.example.invalid")) {
            assertFalse("message leaks $secret: $text", text.contains(secret))
        }
    }

    // ---- scheme matching ----

    @Test
    fun parsersAcceptOnlyTheirOwnSchemes() {
        assertTrue(vless.accepts("vless://x"))
        assertTrue(vless.accepts("  VLESS://x"))
        assertFalse(vless.accepts("vmess://x"))
        assertTrue(ss.accepts("ss://x"))
        assertFalse(ss.accepts("ssr://x"))
        assertFalse(trojan.accepts("not a link"))
    }

    // ---- VLESS ----

    @Test
    fun vlessTlsWebSocketLinkIsParsed() {
        val link = "vless://${Fixtures.UUID}@${Fixtures.HOST}:443?type=ws&security=tls&sni=sni.example.invalid" +
            "&path=%2Fws&host=cdn.example.invalid&alpn=h2%2Chttp%2F1.1&fp=chrome&flow=xtls-rprx-vision#My%20Node"
        val c = parsed(vless, link)
        assertEquals(Protocol.VLESS, c.protocol)
        assertEquals(Fixtures.HOST, c.address)
        assertEquals(443, c.port)
        assertEquals(Fixtures.UUID, c.credentials.userId!!.reveal())
        assertEquals("xtls-rprx-vision", c.credentials.flow)
        assertEquals(TransportSecurity.TLS, c.tls.security)
        assertEquals("sni.example.invalid", c.tls.serverName)
        assertEquals(listOf("h2", "http/1.1"), c.tls.alpn)
        assertEquals("chrome", c.tls.clientFingerprint)
        assertFalse(c.tls.allowInsecure)
        assertEquals(TransportType.WEBSOCKET, c.transport.type)
        assertEquals("/ws", c.transport.path)
        assertEquals("cdn.example.invalid", c.transport.host)
        assertEquals("My Node", c.remark)
    }

    @Test
    fun vlessRealityNeedsPublicKey() {
        val ok = parsed(vless, Fixtures.vless(query = "security=reality&pbk=PUBKEY&sid=ab12&sni=sni.example.invalid"))
        assertEquals(TransportSecurity.REALITY, ok.tls.security)
        assertEquals("PUBKEY", ok.tls.realityPublicKey!!.reveal())
        assertEquals("ab12", ok.tls.realityShortId!!.reveal())
        assertEquals(
            ParseErrorCode.MISSING_FIELD,
            failedCode(vless, Fixtures.vless(query = "security=reality&sni=sni.example.invalid")),
        )
    }

    @Test
    fun vlessRejectsBadUuidPortAndMissingParts() {
        assertEquals(ParseErrorCode.INVALID_FIELD, failedCode(vless, Fixtures.vless(uuid = "not-a-uuid")))
        assertEquals(ParseErrorCode.INVALID_PORT, failedCode(vless, "vless://${Fixtures.UUID}@${Fixtures.HOST}?type=tcp"))
        assertEquals(ParseErrorCode.INVALID_PORT, failedCode(vless, Fixtures.vless(port = 99999)))
        assertEquals(ParseErrorCode.MISSING_FIELD, failedCode(vless, "vless://${Fixtures.HOST}:443"))
    }

    @Test
    fun vlessUnsupportedOptionsAreReportedNotParsed() {
        assertUnsupported(vless, Fixtures.vless(query = "type=kcp"))
        assertUnsupported(vless, Fixtures.vless(query = "type=tcp&security=xtls"))
        assertUnsupported(vless, Fixtures.vless(query = "encryption=mlkem768x25519plus.native.0rtt.abc"))
    }

    @Test
    fun vlessInsecureFlagIsCarriedAsRiskFlag() {
        val c = parsed(vless, Fixtures.vless(query = "security=tls&allowInsecure=1"))
        assertTrue(c.tls.allowInsecure)
    }

    @Test
    fun configToStringNeverRevealsSecrets() {
        val c = parsed(vless, Fixtures.vless())
        assertFalse(c.toString().contains(Fixtures.UUID))
        assertFalse(c.safeLabel().contains(Fixtures.UUID))
        assertFalse(c.safeLabel().contains(Fixtures.HOST))
    }

    // ---- VMess ----

    @Test
    fun vmessJsonLinkIsParsed() {
        val c = parsed(vmess, Fixtures.vmess())
        assertEquals(Protocol.VMESS, c.protocol)
        assertEquals(Fixtures.HOST, c.address)
        assertEquals(443, c.port)
        assertEquals(Fixtures.UUID, c.credentials.userId!!.reveal())
        assertEquals("auto", c.credentials.method)
        assertEquals(TransportType.WEBSOCKET, c.transport.type)
        assertEquals("/ws", c.transport.path)
        assertEquals("cdn.example.invalid", c.transport.host)
        assertEquals(TransportSecurity.TLS, c.tls.security)
        assertEquals("sni.example.invalid", c.tls.serverName)
        assertEquals("vmess node", c.remark)
    }

    @Test
    fun vmessAcceptsNumericPortAndGrpcServiceName() {
        val json = Fixtures.vmessJson(port = "8443").replace("\"net\":\"ws\"", "\"net\":\"grpc\"")
        val c = parsed(vmess, Fixtures.vmess(json))
        assertEquals(8443, c.port)
        assertEquals(TransportType.GRPC, c.transport.type)
        assertEquals("/ws", c.transport.serviceName)
        assertNull(c.transport.path)
    }

    @Test
    fun vmessWithoutTlsHasNoTlsOptions() {
        val json = Fixtures.vmessJson().replace("\"tls\":\"tls\"", "\"tls\":\"\"")
        val c = parsed(vmess, Fixtures.vmess(json))
        assertEquals(TransportSecurity.NONE, c.tls.security)
        assertNull(c.tls.serverName)
    }

    @Test
    fun vmessErrorsAreClassified() {
        assertEquals(ParseErrorCode.INVALID_BASE64, failedCode(vmess, "vmess://%%%not-base64%%%"))
        assertEquals(ParseErrorCode.INVALID_JSON, failedCode(vmess, "vmess://" + Fixtures.b64("not json")))
        assertEquals(ParseErrorCode.INVALID_JSON, failedCode(vmess, "vmess://" + Fixtures.b64("{\"a\":{\"b\":1}}")))
        assertEquals(ParseErrorCode.MISSING_FIELD, failedCode(vmess, "vmess://" + Fixtures.b64("{\"add\":\"${Fixtures.HOST}\",\"port\":\"443\"}")))
        assertEquals(ParseErrorCode.INVALID_PORT, failedCode(vmess, Fixtures.vmess(Fixtures.vmessJson(port = "\"abc\""))))
        assertEquals(ParseErrorCode.INVALID_FIELD, failedCode(vmess, Fixtures.vmess(Fixtures.vmessJson(id = "bad-id"))))
        assertEquals(ParseErrorCode.MISSING_FIELD, failedCode(vmess, "vmess://"))
    }

    @Test
    fun vmessUnsupportedVariantsAreReported() {
        assertUnsupported(vmess, Fixtures.vmess(Fixtures.vmessJson().replace("\"net\":\"ws\"", "\"net\":\"kcp\"")))
        assertUnsupported(vmess, "vmess://${Fixtures.UUID}@${Fixtures.HOST}:443?type=ws")
        val tcpHttpHeader = Fixtures.vmessJson().replace("\"net\":\"ws\"", "\"net\":\"tcp\"").replace("\"type\":\"none\"", "\"type\":\"http\"")
        assertUnsupported(vmess, Fixtures.vmess(tcpHttpHeader))
    }

    @Test
    fun vmessRemarkControlCharactersAreRemoved() {
        val json = Fixtures.vmessJson().replace("vmess node", "a\\u0007b\\u202Ec")
        assertEquals("abc", parsed(vmess, Fixtures.vmess(json)).remark)
    }

    // ---- Shadowsocks ----

    @Test
    fun shadowsocksSip002Base64UserInfoIsParsed() {
        val c = parsed(ss, Fixtures.ss(tag = "My%20SS"))
        assertEquals(Protocol.SHADOWSOCKS, c.protocol)
        assertEquals(Fixtures.HOST, c.address)
        assertEquals(8388, c.port)
        assertEquals("aes-256-gcm", c.credentials.method)
        assertEquals(Fixtures.PASSWORD, c.credentials.password!!.reveal())
        assertEquals("My SS", c.remark)
        assertEquals(TransportSecurity.NONE, c.tls.security)
    }

    @Test
    fun shadowsocksPlainPercentEncodedUserInfoAndTrailingSlash() {
        val c = parsed(ss, "ss://2022-blake3-aes-128-gcm:abc%2Bdef@${Fixtures.HOST}:8388/#t")
        assertEquals("2022-blake3-aes-128-gcm", c.credentials.method)
        assertEquals("abc+def", c.credentials.password!!.reveal())
    }

    @Test
    fun shadowsocksLegacyWholeLinkBase64IsParsed() {
        val link = "ss://" + Fixtures.b64("chacha20-ietf-poly1305:pa:ss@${Fixtures.HOST}:443") + "#legacy"
        val c = parsed(ss, link)
        assertEquals("chacha20-ietf-poly1305", c.credentials.method)
        assertEquals("pa:ss", c.credentials.password!!.reveal())
        assertEquals(Fixtures.HOST, c.address)
        assertEquals(443, c.port)
        assertEquals("legacy", c.remark)
    }

    @Test
    fun shadowsocksErrorsAndPlugins() {
        assertEquals(ParseErrorCode.INVALID_BASE64, failedCode(ss, "ss://!!!@${Fixtures.HOST}:8388"))
        assertEquals(ParseErrorCode.INVALID_PORT, failedCode(ss, "ss://" + Fixtures.b64UrlNoPad("aes-256-gcm:x") + "@${Fixtures.HOST}"))
        assertEquals(ParseErrorCode.MISSING_FIELD, failedCode(ss, "ss://" + Fixtures.b64UrlNoPad("nocolon") + "@${Fixtures.HOST}:1"))
        assertEquals(ParseErrorCode.MALFORMED_URI, failedCode(ss, "ss://" + Fixtures.b64("aes-256-gcm:x")))
        assertEquals(ParseErrorCode.MISSING_FIELD, failedCode(ss, "ss://"))
        assertUnsupported(ss, Fixtures.ss().replace("#", "?plugin=obfs-local%3Bobfs%3Dhttp#"))
    }

    // ---- Trojan ----

    @Test
    fun trojanLinkIsParsed() {
        val c = parsed(trojan, "trojan://p%40ss@${Fixtures.HOST}:443?sni=sni.example.invalid&type=ws&path=%2Ft&host=cdn.example.invalid&allowInsecure=true#tj%201")
        assertEquals(Protocol.TROJAN, c.protocol)
        assertEquals("p@ss", c.credentials.password!!.reveal())
        assertEquals(TransportSecurity.TLS, c.tls.security)
        assertEquals("sni.example.invalid", c.tls.serverName)
        assertTrue(c.tls.allowInsecure)
        assertEquals(TransportType.WEBSOCKET, c.transport.type)
        assertEquals("tj 1", c.remark)
    }

    @Test
    fun trojanErrorsAndUnsupportedOptions() {
        assertEquals(ParseErrorCode.MISSING_FIELD, failedCode(trojan, "trojan://${Fixtures.HOST}:443"))
        assertEquals(ParseErrorCode.MISSING_FIELD, failedCode(trojan, "trojan://@${Fixtures.HOST}:443"))
        assertEquals(ParseErrorCode.INVALID_PORT, failedCode(trojan, "trojan://${Fixtures.PASSWORD}@${Fixtures.HOST}"))
        assertUnsupported(trojan, "trojan://${Fixtures.PASSWORD}@${Fixtures.HOST}:443?security=none")
        assertUnsupported(trojan, "trojan://${Fixtures.PASSWORD}@${Fixtures.HOST}:443?type=quic")
    }
}
