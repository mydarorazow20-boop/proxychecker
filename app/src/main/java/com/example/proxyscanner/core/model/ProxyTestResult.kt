package com.example.proxyscanner.core.model

/**
 * Lifecycle / outcome of testing one configuration.
 * Exact classification rules: docs/04-testing-strategy.md, section 3.
 */
enum class TestStatus {
    PENDING,
    TESTING,
    WORKING,
    LIMITED_CONNECTIVITY,
    FAILED,
    UNSUPPORTED,
    CANCELLED,
}

enum class FailureCategory {
    INVALID_CONFIG,
    UNSUPPORTED_PROTOCOL,
    ENGINE_UNAVAILABLE,
    ENGINE_ERROR,
    DNS_FAILURE,
    CONNECT_TIMEOUT,
    CONNECTION_REFUSED,
    TLS_HANDSHAKE_FAILURE,
    AUTHENTICATION_FAILURE,
    PROTOCOL_ERROR,
    PROBE_TIMEOUT,
    PROBE_HTTP_ERROR,
    PROBE_CONTENT_MISMATCH,
    THROUGHPUT_TOO_LOW,
    DOWNLOAD_INTERRUPTED,
    PER_CONFIG_TIMEOUT,
    CANCELLED,
    UNKNOWN,
}

/**
 * Timings are separate measurements, never one number:
 * - [connectMs]: time for the engine to establish the proxied session.
 * - [latencyMs]: median time-to-response of small probe requests through the proxy,
 *   measured after the session is established.
 * - [firstByteMs]: median time to first response byte of those probes.
 */
data class TestTimings(
    val connectMs: Long? = null,
    val latencyMs: Long? = null,
    val firstByteMs: Long? = null,
)

data class ConnectivityResult(
    val probeAttempts: Int,
    val probeSuccesses: Int,
    val lastHttpStatus: Int?,
    /** True only if the response matched the expected status and content check. */
    val contentVerified: Boolean,
)

data class ThroughputResult(
    val bytesTransferred: Long,
    val durationMs: Long,
    /** True if the whole bounded payload was received. */
    val completed: Boolean,
    /** True if the configured byte or time limit stopped the transfer. */
    val limitReached: Boolean,
) {
    init {
        require(bytesTransferred >= 0) { "bytesTransferred must not be negative" }
        require(durationMs >= 0) { "durationMs must not be negative" }
    }

    /** Bits per second, or null when no meaningful duration was measured. */
    val bitsPerSecond: Long?
        get() = if (durationMs > 0) bytesTransferred * 8L * 1000L / durationMs else null
}

data class ProxyTestResult(
    val configFingerprint: String,
    val status: TestStatus,
    val timings: TestTimings = TestTimings(),
    val connectivity: ConnectivityResult? = null,
    val throughput: ThroughputResult? = null,
    val failure: FailureCategory? = null,
    val startedAtEpochMs: Long? = null,
    val finishedAtEpochMs: Long? = null,
    /** Must already be redacted. */
    val safeDetail: String? = null,
) {
    init {
        require(configFingerprint.isNotBlank()) { "configFingerprint must not be blank" }
        // A successful TCP/TLS connection alone must never yield WORKING.
        if (status == TestStatus.WORKING) {
            require(connectivity?.contentVerified == true) {
                "WORKING requires a verified probe response"
            }
            require(timings.latencyMs != null) { "WORKING requires a measured latency" }
            require(failure == null) { "WORKING must not carry a failure category" }
        }
        if (status == TestStatus.FAILED || status == TestStatus.UNSUPPORTED) {
            require(failure != null) { "$status requires a failure category" }
        }
        if (startedAtEpochMs != null && finishedAtEpochMs != null) {
            require(finishedAtEpochMs >= startedAtEpochMs) { "finished before started" }
        }
    }

    companion object {
        fun pending(configFingerprint: String): ProxyTestResult =
            ProxyTestResult(configFingerprint = configFingerprint, status = TestStatus.PENDING)
    }
}
