package com.example.proxyscanner.core.testing

import com.example.proxyscanner.core.model.FailureCategory
import com.example.proxyscanner.core.model.ProxyTestResult

/** Outcome of a finished scan: one real [ProxyTestResult] per tested configuration plus counters. */
data class ScanResult(
    val results: List<ProxyTestResult>,
    val summary: ScanSummary,
)

/**
 * Lifecycle of the scan as shown to the user. Values are produced only from
 * events of a real [ScanOrchestrator]; nothing in this app invents progress,
 * timings or success rates.
 */
sealed interface ScanStatus {
    data object Idle : ScanStatus

    data class InProgress(val completed: Int, val total: Int) : ScanStatus

    data class Complete(val result: ScanResult) : ScanStatus

    /** The user cancelled; [partialResults] are the results received before cancellation. */
    data class Cancelled(val partialResults: List<ProxyTestResult>) : ScanStatus

    /** [safeMessage] is fixed text without credentials, hosts or URLs. */
    data class Failed(val category: FailureCategory, val safeMessage: String) : ScanStatus
}
