package com.example.proxyscanner.core.testing

import com.example.proxyscanner.core.engine.LocalProxyEndpoint
import com.example.proxyscanner.core.model.ConnectivityResult
import com.example.proxyscanner.core.model.DownloadTestSettings
import com.example.proxyscanner.core.model.FailureCategory
import com.example.proxyscanner.core.model.ProbeEndpoint
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.ProxyTestResult
import com.example.proxyscanner.core.model.ScanSettings
import com.example.proxyscanner.core.model.ThroughputResult
import kotlinx.coroutines.flow.Flow

data class LatencyProbeResult(
    val connectivity: ConnectivityResult,
    /** Median over successful attempts; null if none succeeded. */
    val latencyMs: Long?,
    val firstByteMs: Long?,
    val failure: FailureCategory?,
)

data class DownloadProbeResult(
    val throughput: ThroughputResult?,
    val failure: FailureCategory?,
)

/**
 * Sends probe traffic through a session's loopback endpoint.
 * Implementations must enforce timeouts and the byte/time limits, and must stop
 * reading as soon as a limit is reached.
 */
interface ProbeClient {
    suspend fun measureLatency(
        endpoint: LocalProxyEndpoint,
        probe: ProbeEndpoint,
        attempts: Int,
        timeoutMs: Long,
    ): LatencyProbeResult

    suspend fun measureDownload(
        endpoint: LocalProxyEndpoint,
        downloadUrl: String,
        settings: DownloadTestSettings,
    ): DownloadProbeResult
}

/**
 * Tests ONE configuration: engine session -> latency probes -> optional bounded
 * download -> classification. Always returns a result; cancellation is the only
 * exception that may propagate (CancellationException).
 */
interface ProxyTester {
    suspend fun test(config: ProxyConfig, settings: ScanSettings): ProxyTestResult
}

data class ScanSummary(
    val total: Int,
    val working: Int,
    val limited: Int,
    val failed: Int,
    val unsupported: Int,
    val cancelled: Int,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
)

sealed interface ScanEvent {
    data class Started(val total: Int) : ScanEvent

    data class ConfigStarted(val fingerprint: String) : ScanEvent

    data class ConfigFinished(val result: ProxyTestResult) : ScanEvent

    data class Finished(val summary: ScanSummary) : ScanEvent
}

/**
 * Runs a bounded-concurrency scan (at most ScanSettings.maxConcurrency tests at
 * once). Cancelling collection of the returned flow cancels all in-flight
 * tests and closes their sessions.
 */
interface ScanOrchestrator {
    fun scan(configs: List<ProxyConfig>, settings: ScanSettings): Flow<ScanEvent>
}
