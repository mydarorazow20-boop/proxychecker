package com.example.proxyscanner.feature.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.proxyscanner.R
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.ProxyTestResult
import com.example.proxyscanner.core.testing.ScanStatus
import com.example.proxyscanner.feature.common.endpointLabel
import com.example.proxyscanner.feature.common.protocolLabel
import com.example.proxyscanner.feature.scan.ScannerUiState

/**
 * Shows real scan results when a scan has completed; otherwise an empty state and
 * the imported configurations labelled "Not tested". It never shows latency,
 * speed or success rates that were not measured by an engine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(state: ScannerUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_results)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
            )
        },
    ) { innerPadding ->
        val scan = state.scan
        val configsByFingerprint = remember(state.configs) { state.configs.associateBy { it.fingerprint } }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (scan is ScanStatus.Complete) {
                item {
                    Text(
                        text = stringResource(R.string.results_scan_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(scan.result.results, key = { it.configFingerprint }) { result ->
                    ResultRow(result, configsByFingerprint[result.configFingerprint])
                }
            } else {
                item { EmptyResults() }
                if (state.configs.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.results_configs_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(state.configs, key = { it.fingerprint }) { config -> ConfigRow(config) }
                }
            }
        }
    }
}

@Composable
private fun EmptyResults() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.results_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.results_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigRow(config: ProxyConfig) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = config.remark ?: protocolLabel(config.protocol),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = protocolLabel(config.protocol) + " · " + endpointLabel(config),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.results_not_tested),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultRow(result: ProxyTestResult, config: ProxyConfig?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (config != null) {
                    config.remark ?: (protocolLabel(config.protocol) + " · " + endpointLabel(config))
                } else {
                    stringResource(R.string.results_unknown_config)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(stringResource(R.string.results_status, result.status.name))
            val latency = result.timings.latencyMs
            if (latency != null) {
                Text(stringResource(R.string.results_latency, latency))
            }
            val failure = result.failure
            if (failure != null) {
                Text(stringResource(R.string.results_failure, failure.name))
            }
        }
    }
}
