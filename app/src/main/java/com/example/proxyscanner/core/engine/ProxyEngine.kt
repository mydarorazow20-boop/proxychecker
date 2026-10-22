package com.example.proxyscanner.core.engine

import com.example.proxyscanner.core.model.FailureCategory
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.security.Secret

/** Static description of an engine implementation. */
data class EngineDescriptor(
    val id: String,
    val displayName: String,
    val version: String?,
    /** Protocols for which this engine has been integrated AND tested. */
    val verifiedProtocols: Set<Protocol>,
    /** False for placeholders that cannot open real connections. */
    val integrated: Boolean,
)

sealed interface EngineInitResult {
    data object Ready : EngineInitResult

    /** [reason] must not contain credentials. */
    data class Unavailable(val reason: String) : EngineInitResult
}

data class SessionOptions(val connectTimeoutMs: Long)

enum class LocalProxyType { SOCKS5, HTTP }

/**
 * Loopback endpoint exposed by an open session. Probes go through it.
 * Contract: [host] must be a loopback address, the port must be chosen at
 * random, and engines should require [username]/[password] so that other apps
 * on the device cannot use the proxy.
 */
data class LocalProxyEndpoint(
    val host: String,
    val port: Int,
    val type: LocalProxyType,
    val username: Secret? = null,
    val password: Secret? = null,
)

/** An established proxied session. [close] must be idempotent and never throw. */
interface ProxySession : AutoCloseable {
    val endpoint: LocalProxyEndpoint

    /** Time the engine needed to establish this session. */
    val establishedInMs: Long
}

sealed interface EngineOpenResult {
    data class Opened(val session: ProxySession) : EngineOpenResult

    /** [safeDetail] must already be redacted. */
    data class Failed(val category: FailureCategory, val safeDetail: String? = null) : EngineOpenResult
}

/**
 * Replaceable protocol-engine abstraction.
 *
 * Lifecycle: [initialize] once -> [open] per configuration (possibly several in
 * parallel, bounded by ScanSettings.maxConcurrency) -> close each session ->
 * [close] when the engine is no longer needed.
 *
 * Rules for implementations:
 * - [open] is a suspending function and must honor coroutine cancellation: on
 *   cancellation it releases native resources and rethrows CancellationException.
 * - [open] must enforce [SessionOptions.connectTimeoutMs] and report
 *   FailureCategory.CONNECT_TIMEOUT instead of hanging.
 * - Never log or return credentials, UUIDs, tokens or full proxy URLs.
 *   Use [Secret.reveal] only when generating the engine's own configuration.
 * - [supports] must be true only for configurations the engine has been
 *   integrated and tested for.
 */
interface ProxyEngine {
    val descriptor: EngineDescriptor

    fun supports(config: ProxyConfig): Boolean

    suspend fun initialize(): EngineInitResult

    suspend fun open(config: ProxyConfig, options: SessionOptions): EngineOpenResult

    suspend fun close()
}
