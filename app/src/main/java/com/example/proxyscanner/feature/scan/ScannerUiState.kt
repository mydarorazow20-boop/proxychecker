package com.example.proxyscanner.feature.scan

import com.example.proxyscanner.core.model.ImportStats
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.parser.InputFormat
import com.example.proxyscanner.core.testing.ScanAvailability
import com.example.proxyscanner.core.testing.ScanStatus
import com.example.proxyscanner.core.testing.evaluateScanAvailability

/** One problem found while importing, already redacted and safe to display. */
data class ImportIssue(val line: Int?, val message: String)

/** What the import card shows about the last import attempt. */
data class ImportReport(
    val stats: ImportStats,
    val issues: List<ImportIssue>,
    /** Issues that were found but not listed (the list is capped). */
    val omittedIssues: Int,
)

sealed interface ImportUiState {
    /** Nothing has been imported yet. */
    data object Empty : ImportUiState

    data class InProgress(val fileName: String?) : ImportUiState

    /** At least one configuration was loaded. */
    data class Success(
        val fileName: String?,
        val format: InputFormat,
        val report: ImportReport,
    ) : ImportUiState

    /** Nothing was loaded; previously loaded configurations (if any) are unchanged. */
    data class Failure(
        val fileName: String?,
        val message: String,
        val report: ImportReport?,
    ) : ImportUiState

    /** The picker was dismissed; nothing changed. */
    data object Cancelled : ImportUiState
}

sealed interface UrlValidation {
    data object Blank : UrlValidation

    data object Valid : UrlValidation

    data class Invalid(val message: String) : UrlValidation
}

data class UrlInputState(
    val text: String = "",
    val validation: UrlValidation = UrlValidation.Blank,
)

/** What the engine placeholder reports about itself. */
data class EngineInfo(val name: String, val integrated: Boolean)

/**
 * Everything the three screens render. Imported configurations live only here
 * (in memory); they are not written to disk or logs. Do not log this object.
 */
data class ScannerUiState(
    val engine: EngineInfo,
    val probeEndpointConfigured: Boolean,
    val importState: ImportUiState = ImportUiState.Empty,
    val url: UrlInputState = UrlInputState(),
    val configs: List<ProxyConfig> = emptyList(),
    val protocolCounts: Map<Protocol, Int> = emptyMap(),
    val scan: ScanStatus = ScanStatus.Idle,
) {
    val isImporting: Boolean get() = importState is ImportUiState.InProgress
    val isScanning: Boolean get() = scan is ScanStatus.InProgress

    val scanAvailability: ScanAvailability
        get() = evaluateScanAvailability(
            configCount = configs.size,
            engineIntegrated = engine.integrated,
            probeEndpointConfigured = probeEndpointConfigured,
            busy = isImporting || isScanning,
        )

    val canImport: Boolean get() = !isImporting && !isScanning
}
