package com.example.proxyscanner.core.model

import com.example.proxyscanner.core.security.Secret

enum class CancellationBehavior {
    /** Keep results of configs that already finished; in-flight ones become CANCELLED. */
    KEEP_PARTIAL_RESULTS,

    /** Discard the whole scan's results on cancel. */
    DISCARD_ALL,
}

/**
 * The controlled HTTP test endpoint. No default is shipped (open decision D-05):
 * until the user configures one, scanning stays disabled.
 */
data class ProbeEndpoint(
    /** Small response used for connectivity and latency probes. */
    val latencyUrl: String,
    val expectedStatusCode: Int = 200,
    /** Optional text the latency response body must contain (content verification). */
    val expectedBodyMarker: Secret? = null,
    /** Bounded payload for throughput; null disables the download test. */
    val downloadUrl: String? = null,
)

data class DownloadTestSettings(
    val enabled: Boolean = true,
    val maxBytes: Long = 1_000_000L,
    val maxDurationMs: Long = 10_000L,
    /** 0 means "any throughput is acceptable". */
    val minBitsPerSecond: Long = 0L,
) {
    init {
        require(maxBytes in 1..ScanSettings.ABSOLUTE_MAX_DOWNLOAD_BYTES) { "maxBytes out of bounds" }
        require(maxDurationMs in 1..ScanSettings.ABSOLUTE_MAX_DURATION_MS) { "maxDurationMs out of bounds" }
        require(minBitsPerSecond >= 0) { "minBitsPerSecond must not be negative" }
    }
}

/**
 * All limits are enforced; there is no "unlimited" value.
 * Default numbers are initial proposals to be tuned with real measurements.
 */
data class ScanSettings(
    val probeEndpoint: ProbeEndpoint? = null,
    val connectTimeoutMs: Long = 10_000L,
    val probeTimeoutMs: Long = 10_000L,
    val perConfigTimeoutMs: Long = 45_000L,
    val maxConcurrency: Int = 4,
    val probeAttempts: Int = 3,
    val download: DownloadTestSettings = DownloadTestSettings(),
    val cancellation: CancellationBehavior = CancellationBehavior.KEEP_PARTIAL_RESULTS,
) {
    init {
        require(connectTimeoutMs in 1..ABSOLUTE_MAX_DURATION_MS) { "connectTimeoutMs out of bounds" }
        require(probeTimeoutMs in 1..ABSOLUTE_MAX_DURATION_MS) { "probeTimeoutMs out of bounds" }
        require(perConfigTimeoutMs in 1..ABSOLUTE_MAX_PER_CONFIG_MS) { "perConfigTimeoutMs out of bounds" }
        require(maxConcurrency in 1..ABSOLUTE_MAX_CONCURRENCY) { "maxConcurrency out of bounds" }
        require(probeAttempts in 1..ABSOLUTE_MAX_PROBE_ATTEMPTS) { "probeAttempts out of bounds" }
    }

    companion object {
        const val ABSOLUTE_MAX_CONCURRENCY = 16
        const val ABSOLUTE_MAX_PROBE_ATTEMPTS = 10
        const val ABSOLUTE_MAX_DOWNLOAD_BYTES = 25L * 1024 * 1024
        const val ABSOLUTE_MAX_DURATION_MS = 60_000L
        const val ABSOLUTE_MAX_PER_CONFIG_MS = 180_000L
    }
}
