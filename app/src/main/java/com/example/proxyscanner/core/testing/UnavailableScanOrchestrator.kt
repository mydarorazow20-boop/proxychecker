package com.example.proxyscanner.core.testing

import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.ScanSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ScanEngineUnavailableException : IllegalStateException("No protocol engine is integrated.")

/**
 * Honest placeholder for [ScanOrchestrator] until the engine phase: it never emits
 * a result, it only fails. The UI does not offer Start Scan while this is wired.
 */
class UnavailableScanOrchestrator : ScanOrchestrator {
    override fun scan(configs: List<ProxyConfig>, settings: ScanSettings): Flow<ScanEvent> = flow {
        throw ScanEngineUnavailableException()
    }
}
