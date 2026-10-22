package com.example.proxyscanner.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proxyscanner.core.engine.ProxyEngine
import com.example.proxyscanner.core.importer.ConfigImporter
import com.example.proxyscanner.core.importer.DisplayNames
import com.example.proxyscanner.core.importer.DocumentReader
import com.example.proxyscanner.core.importer.ImportFailure
import com.example.proxyscanner.core.importer.ImportFailureCode
import com.example.proxyscanner.core.importer.ImportOutcome
import com.example.proxyscanner.core.importer.ImportSource
import com.example.proxyscanner.core.importer.UrlCheck
import com.example.proxyscanner.core.importer.UrlSyntaxValidator
import com.example.proxyscanner.core.model.FailureCategory
import com.example.proxyscanner.core.model.ImportLimits
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.ProxyTestResult
import com.example.proxyscanner.core.model.ScanSettings
import com.example.proxyscanner.core.testing.ScanAvailability
import com.example.proxyscanner.core.testing.ScanEngineUnavailableException
import com.example.proxyscanner.core.testing.ScanEvent
import com.example.proxyscanner.core.testing.ScanOrchestrator
import com.example.proxyscanner.core.testing.ScanResult
import com.example.proxyscanner.core.testing.ScanStatus
import com.example.proxyscanner.core.testing.ScanSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the UI state of all screens and runs import and scan work off the UI
 * thread. It contains no Android UI types, so it is unit-tested on the JVM.
 *
 * Scan behaviour: the Start Scan button is only enabled for
 * [ScanAvailability.READY]. In this version no engine is integrated, so the scan
 * code path is exercised by unit tests with fakes only; the app itself never
 * produces a scan result.
 */
class ScannerViewModel(
    private val importer: ConfigImporter,
    private val documents: DocumentReader,
    private val urlValidator: UrlSyntaxValidator,
    engine: ProxyEngine,
    private val orchestrator: ScanOrchestrator,
    private val scanSettings: ScanSettings,
    private val importLimits: ImportLimits = ImportLimits(),
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        ScannerUiState(
            engine = EngineInfo(engine.descriptor.displayName, engine.descriptor.integrated),
            probeEndpointConfigured = scanSettings.probeEndpoint != null,
        ),
    )
    val state: StateFlow<ScannerUiState> = mutableState.asStateFlow()

    private var importJob: Job? = null
    private var scanJob: Job? = null

    // ---- import ---------------------------------------------------------

    /** Called with the picked document, or null when the user dismissed the picker. */
    fun onDocumentPicked(uri: String?) {
        if (!mutableState.value.canImport) return
        if (uri == null) {
            mutableState.update { it.copy(importState = ImportUiState.Cancelled) }
            return
        }
        importJob?.cancel()
        importJob = viewModelScope.launch { runImport(uri) }
    }

    fun onPickerUnavailable() {
        if (!mutableState.value.canImport) return
        mutableState.update {
            it.copy(
                importState = ImportUiState.Failure(
                    fileName = null,
                    message = "No file picker is available on this device.",
                    report = null,
                ),
            )
        }
    }

    private suspend fun runImport(uri: String) {
        mutableState.update { it.copy(importState = ImportUiState.InProgress(null)) }
        val name = try {
            DisplayNames.sanitize(documents.displayName(uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        mutableState.update { it.copy(importState = ImportUiState.InProgress(name)) }
        finishImport(ImportSource.LocalDocument(uri, name), name)
    }

    // ---- URL import ------------------------------------------------------

    /** Imports from the URL in the input field. Only the host is ever shown or kept in UI state. */
    fun onImportUrl() {
        val current = mutableState.value
        if (!current.canImport) return
        if (current.url.validation != UrlValidation.Valid) return
        val url = current.url.text.trim()
        importJob?.cancel()
        importJob = viewModelScope.launch {
            val label = DisplayNames.hostOf(url)
            mutableState.update { it.copy(importState = ImportUiState.InProgress(label)) }
            finishImport(ImportSource.RemoteUrl(url), label)
        }
    }

    /** Cancels a running import (file or URL). Previously loaded configurations stay untouched. */
    fun cancelImport() {
        importJob?.cancel()
        mutableState.update {
            if (it.importState is ImportUiState.InProgress) it.copy(importState = ImportUiState.Cancelled) else it
        }
    }

    private suspend fun finishImport(source: ImportSource, name: String?) {
        val outcome = try {
            importer.import(source, importLimits)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImportOutcome.Failure(
                ImportFailure(ImportFailureCode.READ_ERROR, "An unexpected error occurred during the import."),
            )
        }

        when (outcome) {
            is ImportOutcome.Success -> {
                val configs = outcome.result.configs
                mutableState.update {
                    it.copy(
                        importState = ImportUiState.Success(name, outcome.format, ImportIssues.report(outcome.result)),
                        configs = configs,
                        protocolCounts = configs.groupingBy { c -> c.protocol }.eachCount(),
                        scan = ScanStatus.Idle,
                    )
                }
            }
            is ImportOutcome.Failure -> {
                val details = outcome.failure.details
                mutableState.update {
                    it.copy(
                        importState = ImportUiState.Failure(
                            fileName = name,
                            message = outcome.failure.safeMessage,
                            report = details?.let { d -> ImportIssues.report(d) },
                        ),
                    )
                }
            }
        }
    }

    // ---- URL input field (validation only; the download starts in onImportUrl) ----

    fun onUrlChanged(text: String) {
        val limited = text.take(MAX_URL_INPUT)
        val validation = if (limited.isBlank()) {
            UrlValidation.Blank
        } else {
            when (val check = urlValidator.check(limited)) {
                UrlCheck.Allowed -> UrlValidation.Valid
                is UrlCheck.Rejected -> UrlValidation.Invalid(check.reason)
            }
        }
        mutableState.update { it.copy(url = UrlInputState(limited, validation)) }
    }

    // ---- scan -----------------------------------------------------------

    fun startScan() {
        val current = mutableState.value
        when (current.scanAvailability) {
            ScanAvailability.READY -> launchScan(current.configs)
            ScanAvailability.ENGINE_UNAVAILABLE -> setScan(
                ScanStatus.Failed(FailureCategory.ENGINE_UNAVAILABLE, ENGINE_UNAVAILABLE_MESSAGE),
            )
            ScanAvailability.PROBE_ENDPOINT_MISSING -> setScan(
                ScanStatus.Failed(FailureCategory.INVALID_CONFIG, PROBE_ENDPOINT_MISSING_MESSAGE),
            )
            ScanAvailability.NO_CONFIGS, ScanAvailability.BUSY -> Unit
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    private fun setScan(status: ScanStatus) {
        mutableState.update { it.copy(scan = status) }
    }

    private fun launchScan(configs: List<ProxyConfig>) {
        val total = configs.size
        setScan(ScanStatus.InProgress(0, total))
        scanJob = viewModelScope.launch {
            val results = ArrayList<ProxyTestResult>()
            try {
                var summary: ScanSummary? = null
                orchestrator.scan(configs, scanSettings).collect { event ->
                    when (event) {
                        is ScanEvent.Started -> {
                            setScan(ScanStatus.InProgress(0, event.total))
                        }
                        is ScanEvent.ConfigStarted -> {
                            // Per-config start carries no result; nothing to show yet.
                        }
                        is ScanEvent.ConfigFinished -> {
                            results.add(event.result)
                            setScan(ScanStatus.InProgress(results.size, total))
                        }
                        is ScanEvent.Finished -> {
                            summary = event.summary
                        }
                    }
                }
                val finished = summary
                if (finished != null) {
                    setScan(ScanStatus.Complete(ScanResult(results.toList(), finished)))
                } else {
                    setScan(ScanStatus.Failed(FailureCategory.UNKNOWN, "The scan ended unexpectedly."))
                }
            } catch (e: CancellationException) {
                setScan(ScanStatus.Cancelled(results.toList()))
                throw e
            } catch (e: ScanEngineUnavailableException) {
                setScan(ScanStatus.Failed(FailureCategory.ENGINE_UNAVAILABLE, ENGINE_UNAVAILABLE_MESSAGE))
            } catch (e: Exception) {
                // The exception text is deliberately not shown: it could contain hosts or credentials.
                setScan(ScanStatus.Failed(FailureCategory.UNKNOWN, "The scan failed unexpectedly."))
            }
        }
    }

    companion object {
        const val MAX_URL_INPUT = 2100
        const val ENGINE_UNAVAILABLE_MESSAGE = "Scanning engine will be integrated in a later phase."
        const val PROBE_ENDPOINT_MISSING_MESSAGE = "A test endpoint must be configured before scanning."
    }
}
