package com.example.proxyscanner.core.model

/**
 * Protocols the data model can represent.
 *
 * Being listed here does NOT mean the protocol is parsed or testable.
 * A protocol is "supported" only after its parser AND a connection engine
 * have been integrated and tested (see docs/01-technical-design.md, section 2).
 */
enum class Protocol(val uriSchemes: Set<String>) {
    VLESS(setOf("vless")),
    VMESS(setOf("vmess")),
    SHADOWSOCKS(setOf("ss")),
    TROJAN(setOf("trojan")),

    /** Planned for a later phase. */
    SOCKS5(setOf("socks5", "socks")),

    /** Only after engine feasibility is verified. */
    HYSTERIA2(setOf("hysteria2", "hy2")),

    /** Only after engine feasibility is verified. */
    TUIC(setOf("tuic")),
}
