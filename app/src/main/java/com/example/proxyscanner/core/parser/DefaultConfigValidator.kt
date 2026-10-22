package com.example.proxyscanner.core.parser

import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.TransportSecurity

/**
 * Schema-level checks of a parsed configuration. This says nothing about whether
 * the server exists or accepts the credentials. Messages contain field names only.
 */
class DefaultConfigValidator : ConfigValidator {

    override fun validate(config: ProxyConfig): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        if (config.address.length > 253) issues += ValidationIssue("address", "Address is too long")
        val c = config.credentials
        when (config.protocol) {
            Protocol.VLESS, Protocol.VMESS -> {
                if (c.userId == null) issues += ValidationIssue("userId", "User id is missing")
            }
            Protocol.SHADOWSOCKS -> {
                if (c.method.isNullOrBlank()) issues += ValidationIssue("method", "Cipher method is missing")
                if (c.password == null) issues += ValidationIssue("password", "Password is missing")
            }
            Protocol.TROJAN -> {
                if (c.password == null) issues += ValidationIssue("password", "Password is missing")
            }
            else -> issues += ValidationIssue("protocol", "Protocol has no parser in this version")
        }
        if (config.tls.security == TransportSecurity.REALITY && config.tls.realityPublicKey == null) {
            issues += ValidationIssue("realityPublicKey", "REALITY public key is missing")
        }
        return issues
    }
}
