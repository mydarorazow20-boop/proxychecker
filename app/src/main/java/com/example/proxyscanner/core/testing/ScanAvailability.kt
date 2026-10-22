package com.example.proxyscanner.core.testing

/** Why a scan can or cannot be started right now. */
enum class ScanAvailability {
    READY,
    NO_CONFIGS,
    ENGINE_UNAVAILABLE,
    PROBE_ENDPOINT_MISSING,
    BUSY,
}

/**
 * Single source of truth for the Start Scan button. A scan needs at least one
 * configuration, an integrated protocol engine, a configured probe endpoint
 * (decision D-05) and no import or scan already running.
 */
fun evaluateScanAvailability(
    configCount: Int,
    engineIntegrated: Boolean,
    probeEndpointConfigured: Boolean,
    busy: Boolean,
): ScanAvailability = when {
    busy -> ScanAvailability.BUSY
    configCount <= 0 -> ScanAvailability.NO_CONFIGS
    !engineIntegrated -> ScanAvailability.ENGINE_UNAVAILABLE
    !probeEndpointConfigured -> ScanAvailability.PROBE_ENDPOINT_MISSING
    else -> ScanAvailability.READY
}
