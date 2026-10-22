package com.example.proxyscanner.feature.scan

import com.example.proxyscanner.core.engine.EngineDescriptor
import com.example.proxyscanner.core.engine.EngineInitResult
import com.example.proxyscanner.core.engine.EngineOpenResult
import com.example.proxyscanner.core.engine.ProxyEngine
import com.example.proxyscanner.core.engine.SessionOptions
import com.example.proxyscanner.core.engine.UnavailableProxyEngine
import com.example.proxyscanner.core.importer.ConfigImporter
import com.example.proxyscanner.core.importer.DocumentReadResult
import com.example.proxyscanner.core.importer.DocumentReader
import com.example.proxyscanner.core.importer.ImportFailure
import com.example.proxyscanner.core.importer.ImportFailureCode
import com.example.proxyscanner.core.importer.ImportOutcome
import com.example.proxyscanner.core.importer.ImportSource
import com.example.proxyscanner.core.importer.UrlSyntaxValidator
import com.example.proxyscanner.core.model.FailureCategory
import com.example.proxyscanner.core.model.ImportLimits
import com.example.proxyscanner.core.model.ImportResult
import com.example.proxyscanner.core.model.ImportStats
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProbeEndpoint
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.ProxyTestResult
import com.example.proxyscanner.core.model.ScanSettings
import com.example.proxyscanner.core.model.TestStatus
import com.example.proxyscanner.core.parser.InputFormat
import com.example.proxyscanner.core.testing.ScanAvailability
import com.example.proxyscanner.core.testing.ScanEvent
import com.example.proxyscanner.core.testing.ScanOrchestrator
import com.example.proxyscanner.core.testing.ScanStatus
import com.example.proxyscanner.core.testing.ScanSummary
import com.example.proxyscanner.core.testing.UnavailableScanOrchestrator
import com.example.proxyscanner.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeDocuments : DocumentReader {
    override suspend fun displayName(uri: String): String? = "servers.txt"
    override suspend fun read(uri: String, maxBytes: Long): DocumentReadResult = DocumentReadResult.Unreadable
}

private class GatedImporter : ConfigImporter {
    val gate = CompletableDeferred<ImportOutcome>()
    var calls = 0
    var lastSource: ImportSource? = null
    override suspend fun import(source: ImportSource, limits: ImportLimits): ImportOutcome {
        calls++
        lastSource = source
        return gate.await()
    }
}

private class ThrowingImporter : ConfigImporter {
    override suspend fun import(source: ImportSource, limits: ImportLimits): ImportOutcome =
        throw IllegalStateException("boom secret-host.example.invalid")
}

/** Test double that claims to be integrated so the scan code path can be exercised. */
private class IntegratedFakeEngine : ProxyEngine {
    override val descriptor = EngineDescriptor("fake", "Fake engine", null, emptySet(), integrated = true)
    override fun supports(config: ProxyConfig): Boolean = true
    override suspend fun initialize(): EngineInitResult = EngineInitResult.Ready
    override suspend fun open(config: ProxyConfig, options: SessionOptions): EngineOpenResult =
        EngineOpenResult.Failed(FailureCategory.ENGINE_UNAVAILABLE)
    override suspend fun close() = Unit
}

private class FakeOrchestrator(private val events: () -> Flow<ScanEvent>) : ScanOrchestrator {
    override fun scan(configs: List<ProxyConfig>, settings: ScanSettings): Flow<ScanEvent> = events()
}

class ScannerViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val probe = ProbeEndpoint(latencyUrl = "https://probe.example.invalid/204")

    private fun config(n: Int, protocol: Protocol = Protocol.VLESS) = ProxyConfig(
        fingerprint = "fp-$n",
        protocol = protocol,
        address = "host$n.example.invalid",
        port = 443,
    )

    private fun success(configs: List<ProxyConfig>) = ImportOutcome.Success(
        ImportResult(
            configs = configs,
            duplicates = emptyList(),
            unsupported = emptyList(),
            errors = emptyList(),
            stats = ImportStats(configs.size, configs.size, 0, 0, 0),
        ),
        InputFormat.PLAIN_LINK_LIST,
    )

    private fun viewModel(
        importer: ConfigImporter = GatedImporter().also { it.gate.complete(success(emptyList())) },
        engine: ProxyEngine = UnavailableProxyEngine(),
        orchestrator: ScanOrchestrator = UnavailableScanOrchestrator(),
        settings: ScanSettings = ScanSettings(),
    ) = ScannerViewModel(importer, FakeDocuments(), UrlSyntaxValidator(), engine, orchestrator, settings)

    private fun summary(total: Int) = ScanSummary(total, 0, 0, total, 0, 0, 1L, 2L)

    private fun failedResult(n: Int) = ProxyTestResult(
        configFingerprint = "fp-$n",
        status = TestStatus.FAILED,
        failure = FailureCategory.CONNECT_TIMEOUT,
    )

    /** A view model that already holds [count] configurations. */
    private fun loaded(
        count: Int,
        engine: ProxyEngine = UnavailableProxyEngine(),
        orchestrator: ScanOrchestrator = UnavailableScanOrchestrator(),
        settings: ScanSettings = ScanSettings(),
    ): ScannerViewModel {
        val importer = GatedImporter()
        importer.gate.complete(success((1..count).map { config(it) }))
        val vm = viewModel(importer, engine, orchestrator, settings)
        vm.onDocumentPicked("content://x/1")
        return vm
    }

    // ---- initial state ----

    @Test
    fun initialStateIsEmptyAndIdle() {
        val s = viewModel().state.value
        assertEquals(ImportUiState.Empty, s.importState)
        assertEquals(ScanStatus.Idle, s.scan)
        assertTrue(s.configs.isEmpty())
        assertEquals(ScanAvailability.NO_CONFIGS, s.scanAvailability)
        assertFalse(s.engine.integrated)
        assertEquals(UrlValidation.Blank, s.url.validation)
    }

    // ---- import ----

    @Test
    fun cancelledPickerChangesNothingElse() = runTest {
        val vm = loaded(2)
        vm.onDocumentPicked(null)
        advanceUntilIdle()
        assertEquals(ImportUiState.Cancelled, vm.state.value.importState)
        assertEquals(2, vm.state.value.configs.size)
    }

    @Test
    fun importShowsProgressThenSuccess() = runTest {
        val importer = GatedImporter()
        val vm = viewModel(importer)
        vm.onDocumentPicked("content://x/1")
        advanceUntilIdle()
        val during = vm.state.value
        assertEquals(ImportUiState.InProgress("servers.txt"), during.importState)
        assertTrue(during.isImporting)
        assertFalse(during.canImport)
        assertEquals(ScanAvailability.BUSY, during.scanAvailability)

        importer.gate.complete(success(listOf(config(1), config(2, Protocol.TROJAN), config(3))))
        advanceUntilIdle()
        val after = vm.state.value
        val state = after.importState as ImportUiState.Success
        assertEquals("servers.txt", state.fileName)
        assertEquals(3, after.configs.size)
        assertEquals(mapOf(Protocol.VLESS to 2, Protocol.TROJAN to 1), after.protocolCounts)
        assertEquals(ScanStatus.Idle, after.scan)
    }

    @Test
    fun importFailureKeepsPreviouslyLoadedConfigs() = runTest {
        val importer = GatedImporter()
        importer.gate.complete(success(listOf(config(1))))
        val vm = viewModel(importer)
        vm.onDocumentPicked("content://x/1")
        advanceUntilIdle()
        assertEquals(1, vm.state.value.configs.size)

        val failing = GatedImporter()
        failing.gate.complete(ImportOutcome.Failure(ImportFailure(ImportFailureCode.EMPTY_CONTENT, "The file is empty.")))
        val vm2 = viewModel(failing)
        vm2.onDocumentPicked("content://x/2")
        advanceUntilIdle()
        val failure = vm2.state.value.importState as ImportUiState.Failure
        assertEquals("The file is empty.", failure.message)
        assertEquals("servers.txt", failure.fileName)
        assertTrue(vm2.state.value.configs.isEmpty())
        assertEquals(ScanAvailability.NO_CONFIGS, vm2.state.value.scanAvailability)
    }

    @Test
    fun unexpectedImporterExceptionBecomesGenericFailure() = runTest {
        val vm = viewModel(ThrowingImporter())
        vm.onDocumentPicked("content://x/1")
        advanceUntilIdle()
        val failure = vm.state.value.importState as ImportUiState.Failure
        assertFalse(failure.message.contains("secret-host"))
        assertFalse(failure.message.contains("boom"))
    }

    @Test
    fun missingPickerIsReportedAsFailure() {
        val vm = viewModel()
        vm.onPickerUnavailable()
        assertTrue(vm.state.value.importState is ImportUiState.Failure)
    }

    // ---- URL input ----

    @Test
    fun urlInputIsValidatedWithoutAnyNetworkAccess() {
        val vm = viewModel()
        vm.onUrlChanged("   ")
        assertEquals(UrlValidation.Blank, vm.state.value.url.validation)
        vm.onUrlChanged("https://example.com/list.txt")
        assertEquals(UrlValidation.Valid, vm.state.value.url.validation)
        vm.onUrlChanged("http://example.com/list.txt")
        assertTrue(vm.state.value.url.validation is UrlValidation.Invalid)
        vm.onUrlChanged("https://127.0.0.1/x")
        val invalid = vm.state.value.url.validation as UrlValidation.Invalid
        assertFalse(invalid.message.contains("127.0.0.1"))
        vm.onUrlChanged("h".repeat(5000))
        assertEquals(ScannerViewModel.MAX_URL_INPUT, vm.state.value.url.text.length)
    }

    // ---- scan placeholder (no engine) ----

    @Test
    fun withoutEngineStartScanIsDisabledAndShowsTheHonestMessage() = runTest {
        val vm = loaded(2)
        advanceUntilIdle()
        assertEquals(ScanAvailability.ENGINE_UNAVAILABLE, vm.state.value.scanAvailability)
        vm.startScan()
        advanceUntilIdle()
        val scan = vm.state.value.scan as ScanStatus.Failed
        assertEquals(FailureCategory.ENGINE_UNAVAILABLE, scan.category)
        assertEquals("Scanning engine will be integrated in a later phase.", scan.safeMessage)
    }

    @Test
    fun startScanWithoutConfigsDoesNothing() {
        val vm = viewModel()
        vm.startScan()
        assertEquals(ScanStatus.Idle, vm.state.value.scan)
    }

    @Test
    fun integratedEngineWithoutProbeEndpointCannotScan() = runTest {
        val vm = loaded(1, engine = IntegratedFakeEngine())
        advanceUntilIdle()
        assertEquals(ScanAvailability.PROBE_ENDPOINT_MISSING, vm.state.value.scanAvailability)
        vm.startScan()
        val scan = vm.state.value.scan as ScanStatus.Failed
        assertEquals(FailureCategory.INVALID_CONFIG, scan.category)
    }

    // ---- scan lifecycle with fakes (the app itself has no engine yet) ----

    @Test
    fun scanProgressesAndCompletesFromEvents() = runTest {
        val gate = CompletableDeferred<Unit>()
        val orchestrator = FakeOrchestrator {
            flow {
                emit(ScanEvent.Started(2))
                emit(ScanEvent.ConfigStarted("fp-1"))
                emit(ScanEvent.ConfigFinished(failedResult(1)))
                gate.await()
                emit(ScanEvent.ConfigFinished(failedResult(2)))
                emit(ScanEvent.Finished(summary(2)))
            }
        }
        val vm = loaded(2, IntegratedFakeEngine(), orchestrator, ScanSettings(probeEndpoint = probe))
        advanceUntilIdle()
        assertEquals(ScanAvailability.READY, vm.state.value.scanAvailability)

        vm.startScan()
        advanceUntilIdle()
        assertEquals(ScanStatus.InProgress(1, 2), vm.state.value.scan)
        assertEquals(ScanAvailability.BUSY, vm.state.value.scanAvailability)
        assertFalse(vm.state.value.canImport)

        gate.complete(Unit)
        advanceUntilIdle()
        val done = vm.state.value.scan as ScanStatus.Complete
        assertEquals(2, done.result.results.size)
        assertEquals(2, done.result.summary.total)
        assertEquals(ScanAvailability.READY, vm.state.value.scanAvailability)
    }

    @Test
    fun cancellingKeepsPartialResults() = runTest {
        val orchestrator = FakeOrchestrator {
            flow {
                emit(ScanEvent.Started(3))
                emit(ScanEvent.ConfigFinished(failedResult(1)))
                awaitCancellation()
            }
        }
        val vm = loaded(3, IntegratedFakeEngine(), orchestrator, ScanSettings(probeEndpoint = probe))
        advanceUntilIdle()
        vm.startScan()
        advanceUntilIdle()
        assertTrue(vm.state.value.isScanning)

        vm.cancelScan()
        advanceUntilIdle()
        val cancelled = vm.state.value.scan as ScanStatus.Cancelled
        assertEquals(1, cancelled.partialResults.size)
        assertFalse(vm.state.value.isScanning)
    }

    @Test
    fun orchestratorFailureBecomesGenericScanFailure() = runTest {
        val orchestrator = FakeOrchestrator {
            flow { throw IllegalStateException("leak secret-host.example.invalid") }
        }
        val vm = loaded(1, IntegratedFakeEngine(), orchestrator, ScanSettings(probeEndpoint = probe))
        advanceUntilIdle()
        vm.startScan()
        advanceUntilIdle()
        val failed = vm.state.value.scan as ScanStatus.Failed
        assertEquals(FailureCategory.UNKNOWN, failed.category)
        assertFalse(failed.safeMessage.contains("secret-host"))
    }

    @Test
    fun flowEndingWithoutFinishedEventIsAFailure() = runTest {
        val orchestrator = FakeOrchestrator { flow { emit(ScanEvent.Started(1)) } }
        val vm = loaded(1, IntegratedFakeEngine(), orchestrator, ScanSettings(probeEndpoint = probe))
        advanceUntilIdle()
        vm.startScan()
        advanceUntilIdle()
        assertTrue(vm.state.value.scan is ScanStatus.Failed)
    }

    @Test
    fun importIsIgnoredWhileScanning() = runTest {
        val orchestrator = FakeOrchestrator { flow { emit(ScanEvent.Started(1)); awaitCancellation() } }
        val importer = GatedImporter()
        importer.gate.complete(success(listOf(config(1))))
        val vm = viewModel(importer, IntegratedFakeEngine(), orchestrator, ScanSettings(probeEndpoint = probe))
        vm.onDocumentPicked("content://x/1")
        advanceUntilIdle()
        vm.startScan()
        advanceUntilIdle()
        val callsBefore = importer.calls
        vm.onDocumentPicked("content://x/2")
        advanceUntilIdle()
        assertEquals(callsBefore, importer.calls)
        vm.cancelScan()
        advanceUntilIdle()
    }

    @Test
    fun newSuccessfulImportResetsAFinishedScan() = runTest {
        val orchestrator = FakeOrchestrator {
            flow {
                emit(ScanEvent.Started(1))
                emit(ScanEvent.ConfigFinished(failedResult(1)))
                emit(ScanEvent.Finished(summary(1)))
            }
        }
        val importer = GatedImporter()
        importer.gate.complete(success(listOf(config(1))))
        val vm = viewModel(importer, IntegratedFakeEngine(), orchestrator, ScanSettings(probeEndpoint = probe))
        vm.onDocumentPicked("content://x/1")
        advanceUntilIdle()
        vm.startScan()
        advanceUntilIdle()
        assertTrue(vm.state.value.scan is ScanStatus.Complete)
        vm.onDocumentPicked("content://x/2")
        advanceUntilIdle()
        assertEquals(ScanStatus.Idle, vm.state.value.scan)
    }

    // ---- URL import ----

    @Test
    fun urlImportLoadsConfigsAndShowsOnlyTheHost() = runTest {
        val importer = GatedImporter()
        importer.gate.complete(success(listOf(config(1), config(2))))
        val vm = viewModel(importer)
        vm.onUrlChanged("https://lists.example.com/sub.txt?token=abc123")
        vm.onImportUrl()
        advanceUntilIdle()
        val s = vm.state.value
        val imported = s.importState as ImportUiState.Success
        assertEquals("lists.example.com", imported.fileName)
        assertEquals(2, s.configs.size)
        assertEquals(
            ImportSource.RemoteUrl("https://lists.example.com/sub.txt?token=abc123"),
            importer.lastSource,
        )
        assertFalse(s.importState.toString().contains("abc123"))
    }

    @Test
    fun urlImportFailureShowsTheSafeMessageAndHost() = runTest {
        val failing = GatedImporter()
        failing.gate.complete(
            ImportOutcome.Failure(ImportFailure(ImportFailureCode.HTTP_ERROR, "The server answered with HTTP 404.")),
        )
        val vm = viewModel(failing)
        vm.onUrlChanged("https://lists.example.com/sub.txt")
        vm.onImportUrl()
        advanceUntilIdle()
        val failure = vm.state.value.importState as ImportUiState.Failure
        assertEquals("The server answered with HTTP 404.", failure.message)
        assertEquals("lists.example.com", failure.fileName)
        assertTrue(vm.state.value.configs.isEmpty())
    }

    @Test
    fun blankOrInvalidUrlsNeverStartAnImport() = runTest {
        val importer = GatedImporter()
        val vm = viewModel(importer)
        vm.onImportUrl() // blank
        vm.onUrlChanged("http://lists.example.com/sub.txt")
        vm.onImportUrl() // not https
        vm.onUrlChanged("https://127.0.0.1/sub.txt")
        vm.onImportUrl() // local address
        advanceUntilIdle()
        assertEquals(0, importer.calls)
        assertEquals(ImportUiState.Empty, vm.state.value.importState)
    }

    @Test
    fun cancellingAUrlImportReturnsToCancelled() = runTest {
        val slow = GatedImporter() // never completes
        val vm = viewModel(slow)
        vm.onUrlChanged("https://lists.example.com/sub.txt")
        vm.onImportUrl()
        assertTrue(vm.state.value.importState is ImportUiState.InProgress)
        assertFalse(vm.state.value.canImport)
        vm.cancelImport()
        advanceUntilIdle()
        assertEquals(ImportUiState.Cancelled, vm.state.value.importState)
        assertTrue(vm.state.value.canImport)
    }

    @Test
    fun secondUrlImportIsIgnoredWhileOneIsRunning() = runTest {
        val slow = GatedImporter()
        val vm = viewModel(slow)
        vm.onUrlChanged("https://lists.example.com/sub.txt")
        vm.onImportUrl()
        vm.onImportUrl()
        assertEquals(1, slow.calls)
    }
}
