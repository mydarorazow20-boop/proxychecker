package com.example.proxyscanner.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.proxyscanner.R
import com.example.proxyscanner.core.model.ImportLimits
import com.example.proxyscanner.feature.common.SectionCard
import com.example.proxyscanner.feature.scan.ScannerUiState

@Suppress("DEPRECATION")
private fun appVersionName(context: Context): String? = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (e: Exception) {
    null
}

/** Read-only information in this version: nothing here changes behaviour yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: ScannerUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember { appVersionName(context) }
    val limits = remember { ImportLimits() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_readonly),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionCard(title = stringResource(R.string.settings_about_title)) {
                Text(
                    if (version != null) {
                        stringResource(R.string.settings_version, version)
                    } else {
                        stringResource(R.string.settings_version_unknown)
                    },
                )
            }
            SectionCard(title = stringResource(R.string.settings_engine_title)) {
                Text(
                    stringResource(
                        R.string.settings_engine,
                        if (state.engine.integrated) state.engine.name else stringResource(R.string.settings_engine_none),
                    ),
                )
                Text(stringResource(R.string.settings_probe))
            }
            SectionCard(title = stringResource(R.string.settings_import_title)) {
                Text(stringResource(R.string.settings_limit_size, (limits.maxBytes / (1024 * 1024)).toInt()))
                Text(stringResource(R.string.settings_limit_entries, limits.maxEntries))
                Text(stringResource(R.string.settings_limit_line, limits.maxLineLength))
                Text(
                    text = stringResource(R.string.settings_schemes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionCard(title = stringResource(R.string.settings_privacy_title)) {
                Text(stringResource(R.string.settings_privacy_body))
            }
        }
    }
}
