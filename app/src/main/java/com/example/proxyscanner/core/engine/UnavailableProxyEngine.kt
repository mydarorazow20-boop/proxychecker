package com.example.proxyscanner.core.engine

import com.example.proxyscanner.core.model.FailureCategory
import com.example.proxyscanner.core.model.ProxyConfig

/**
 * Honest placeholder used until a real engine is integrated (selected in the
 * engine-integration phase, see ADR-0006). It never opens a connection and
 * never reports success.
 */
class UnavailableProxyEngine : ProxyEngine {
    override val descriptor: EngineDescriptor = EngineDescriptor(
        id = "none",
        displayName = "No engine",
        version = null,
        verifiedProtocols = emptySet(),
        integrated = false,
    )

    override fun supports(config: ProxyConfig): Boolean = false

    override suspend fun initialize(): EngineInitResult =
        EngineInitResult.Unavailable("No protocol engine is integrated.")

    override suspend fun open(config: ProxyConfig, options: SessionOptions): EngineOpenResult =
        EngineOpenResult.Failed(
            category = FailureCategory.ENGINE_UNAVAILABLE,
            safeDetail = "No protocol engine is integrated.",
        )

    override suspend fun close() = Unit
}
