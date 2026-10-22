package com.example.proxyscanner.feature.common

import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig

fun protocolLabel(protocol: Protocol): String = when (protocol) {
    Protocol.VLESS -> "VLESS"
    Protocol.VMESS -> "VMess"
    Protocol.SHADOWSOCKS -> "Shadowsocks"
    Protocol.TROJAN -> "Trojan"
    Protocol.SOCKS5 -> "SOCKS5"
    Protocol.HYSTERIA2 -> "Hysteria2"
    Protocol.TUIC -> "TUIC"
}

/** `host:port`, with brackets around IPv6 literals. Never includes credentials. */
fun endpointLabel(config: ProxyConfig): String {
    val host = if (config.address.contains(':')) "[${config.address}]" else config.address
    return "$host:${config.port}"
}
