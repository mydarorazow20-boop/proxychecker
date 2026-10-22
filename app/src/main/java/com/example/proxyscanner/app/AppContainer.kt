package com.example.proxyscanner.app

import android.content.ContentResolver
import com.example.proxyscanner.core.engine.EngineLifecycleManager
import com.example.proxyscanner.core.engine.ProxyEngine
import com.example.proxyscanner.core.engine.UnavailableProxyEngine
import com.example.proxyscanner.core.importer.ConfigImporter
import com.example.proxyscanner.core.importer.DefaultConfigImporter
import com.example.proxyscanner.core.importer.DocumentReader
import com.example.proxyscanner.core.importer.HttpsFetcher
import com.example.proxyscanner.core.importer.UrlSyntaxValidator
import com.example.proxyscanner.core.model.ScanSettings
import com.example.proxyscanner.core.parser.DefaultConfigParser
import com.example.proxyscanner.core.testing.ScanOrchestrator
import com.example.proxyscanner.core.testing.UnavailableScanOrchestrator
import com.example.proxyscanner.feature.scan.ScannerViewModel

/**
 * Manual dependency container (ADR-0002: no DI framework). The only place that
 * wires implementations to interfaces. The engine and orchestrator stay honest
 * placeholders until the engine-integration phase.
 */
class AppContainer(contentResolver: ContentResolver) {
    private val engine: ProxyEngine = UnavailableProxyEngine()

    /** Lifecycle wrapper around [engine]. Not used by the UI yet (Phase 05 foundation). */
    val engineLifecycle: EngineLifecycleManager = EngineLifecycleManager(engine)

    private val orchestrator: ScanOrchestrator = UnavailableScanOrchestrator()
    private val documents: DocumentReader = ContentResolverDocumentReader(contentResolver)
    private val importer: ConfigImporter = DefaultConfigImporter(
        documents,
        DefaultConfigParser(),
        fetcher = HttpsFetcher(UrlConnectionTransport()),
    )

    fun createScannerViewModel(): ScannerViewModel = ScannerViewModel(
        importer = importer,
        documents = documents,
        urlValidator = UrlSyntaxValidator(),
        engine = engine,
        orchestrator = orchestrator,
        scanSettings = ScanSettings(),
    )
}
