package com.example.proxyscanner.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.proxyscanner.R
import com.example.proxyscanner.core.testing.ScanAvailability
import com.example.proxyscanner.core.testing.ScanStatus
import com.example.proxyscanner.feature.common.SectionCard
import com.example.proxyscanner.feature.common.protocolLabel
import com.example.proxyscanner.feature.imports.ImportSection
import com.example.proxyscanner.feature.imports.ImportStatusSection
import com.example.proxyscanner.feature.imports.UrlSection
import com.example.proxyscanner.feature.scan.ScannerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: ScannerUiState,
    onDocumentPicked: (String?) -> Unit,
    onPickerUnavailable: () -> Unit,
    onUrlChanged: (String) -> Unit,
    onImportUrl: () -> Unit,
    onCancelImport: () -> Unit,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AboutSection()
            ImportSection(state, onDocumentPicked, onPickerUnavailable)
            UrlSection(state.url, state.canImport, onUrlChanged, onImportUrl)
            ImportStatusSection(state, onCancelImport)
            ScanSection(state, onStartScan, onCancelScan)
            ResultsSummarySection(state, onOpenResults)
        }
    }
}

@Composable
private fun AboutSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.home_phase_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScanSection(
    state: ScannerUiState,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.scan_title)) {
        val availability = state.scanAvailability
        Button(
            onClick = onStartScan,
            enabled = availability == ScanAvailability.READY,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.scan_button))
        }
        // The engine notice is always shown while no engine is integrated, even with no
        // configurations loaded, so the reason scanning is unavailable is never hidden.
        val message = when (availability) {
            ScanAvailability.NO_CONFIGS -> stringResource(R.string.scan_msg_no_configs)
            ScanAvailability.ENGINE_UNAVAILABLE -> stringResource(R.string.scan_msg_engine)
            ScanAvailability.PROBE_ENDPOINT_MISSING -> stringResource(R.string.scan_msg_probe)
            ScanAvailability.READY, ScanAvailability.BUSY -> null
        }
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!state.engine.integrated && availability != ScanAvailability.ENGINE_UNAVAILABLE) {
            Text(
                text = stringResource(R.string.scan_msg_engine),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ScanStatusView(state.scan, onCancelScan)
    }
}

@Composable
private fun ScanStatusView(scan: ScanStatus, onCancelScan: () -> Unit) {
    when (scan) {
        ScanStatus.Idle -> Text(
            text = stringResource(R.string.scan_status_idle),
            style = MaterialTheme.typography.bodyMedium,
        )
        is ScanStatus.InProgress -> {
            if (scan.total > 0) {
                LinearProgressIndicator(
                    progress = { scan.completed.toFloat() / scan.total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(stringResource(R.string.scan_status_progress, scan.completed, scan.total))
            OutlinedButton(onClick = onCancelScan, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scan_cancel))
            }
        }
        is ScanStatus.Complete -> Text(
            stringResource(R.string.scan_status_complete, scan.result.results.size),
        )
        is ScanStatus.Cancelled -> Text(
            stringResource(R.string.scan_status_cancelled, scan.partialResults.size),
        )
        is ScanStatus.Failed -> Text(
            text = stringResource(R.string.scan_status_failed, scan.safeMessage),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ResultsSummarySection(state: ScannerUiState, onOpenResults: () -> Unit) {
    SectionCard(title = stringResource(R.string.summary_title)) {
        val scan = state.scan
        if (scan is ScanStatus.Complete) {
            val summary = scan.result.summary
            Text(stringResource(R.string.summary_loaded_scanned, state.configs.size))
            Text(
                text = stringResource(
                    R.string.summary_counts,
                    summary.working,
                    summary.limited,
                    summary.failed,
                    summary.unsupported,
                    summary.cancelled,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (state.configs.isNotEmpty()) {
            Text(stringResource(R.string.summary_loaded, state.configs.size))
            for ((protocol, count) in state.protocolCounts.entries.sortedBy { it.key.name }) {
                Text(
                    text = stringResource(R.string.protocol_count, protocolLabel(protocol), count),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.summary_none),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onOpenResults, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.summary_open))
        }
    }
}
