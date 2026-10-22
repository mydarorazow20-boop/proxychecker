package com.example.proxyscanner.feature.imports

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.proxyscanner.R
import com.example.proxyscanner.core.parser.InputFormat
import com.example.proxyscanner.feature.common.SectionCard
import com.example.proxyscanner.feature.scan.ImportReport
import com.example.proxyscanner.feature.scan.ImportUiState
import com.example.proxyscanner.feature.scan.ScannerUiState
import com.example.proxyscanner.feature.scan.UrlInputState
import com.example.proxyscanner.feature.scan.UrlValidation

/** MIME types offered by the system picker: plain text and generic files (content is validated after reading). */
private val PICKER_MIME_TYPES = arrayOf("text/plain", "text/*", "application/octet-stream")

@Composable
fun ImportSection(
    state: ScannerUiState,
    onDocumentPicked: (String?) -> Unit,
    onPickerUnavailable: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        onDocumentPicked(uri?.toString())
    }
    SectionCard(title = stringResource(R.string.import_title)) {
        Text(
            text = stringResource(R.string.import_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                try {
                    launcher.launch(PICKER_MIME_TYPES)
                } catch (e: ActivityNotFoundException) {
                    onPickerUnavailable()
                }
            },
            enabled = state.canImport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_button))
        }
    }
}

/** Result of the last file or URL import, with a cancel button while one is running. */
@Composable
fun ImportStatusSection(state: ScannerUiState, onCancelImport: () -> Unit) {
    SectionCard(title = stringResource(R.string.import_status_title)) {
        ImportStatus(state)
        if (state.isImporting) {
            OutlinedButton(onClick = onCancelImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_cancel))
            }
        }
    }
}

@Composable
private fun ImportStatus(state: ScannerUiState) {
    when (val s = state.importState) {
        ImportUiState.Empty -> Text(
            text = stringResource(R.string.import_empty),
            style = MaterialTheme.typography.bodyMedium,
        )
        is ImportUiState.InProgress -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = if (s.fileName != null) {
                    stringResource(R.string.import_in_progress_file, s.fileName)
                } else {
                    stringResource(R.string.import_in_progress)
                },
            )
        }
        is ImportUiState.Success -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (s.fileName != null) {
                    stringResource(R.string.import_success_title, s.fileName)
                } else {
                    stringResource(R.string.import_success_title_unnamed)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            ReportBody(s.report)
            Text(
                text = when (s.format) {
                    InputFormat.BASE64_LINK_LIST -> stringResource(R.string.import_format_base64)
                    else -> stringResource(R.string.import_format_plain)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.import_syntax_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is ImportUiState.Failure -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.import_failure_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (s.fileName != null) {
                    Text(
                        text = stringResource(R.string.import_failure_file, s.fileName),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Text(text = s.message, color = MaterialTheme.colorScheme.onErrorContainer)
                if (s.report != null) ReportBody(s.report)
                if (state.configs.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.import_failure_kept),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        ImportUiState.Cancelled -> Text(
            text = stringResource(R.string.import_cancelled),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReportBody(report: ImportReport) {
    val stats = report.stats
    Text(
        text = stringResource(
            R.string.import_stats,
            stats.parsed - stats.duplicates,
            stats.duplicates,
            stats.unsupported,
            stats.errors,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    for (issue in report.issues) {
        val line = issue.line
        Text(
            text = if (line != null) {
                stringResource(R.string.import_issue_line, line, issue.message)
            } else {
                stringResource(R.string.import_issue_no_line, issue.message)
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (report.omittedIssues > 0) {
        Text(
            text = stringResource(R.string.import_issues_more, report.omittedIssues),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun UrlSection(
    url: UrlInputState,
    canImport: Boolean,
    onUrlChanged: (String) -> Unit,
    onImportUrl: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.url_title)) {
        val validation = url.validation
        OutlinedTextField(
            value = url.text,
            onValueChange = onUrlChanged,
            label = { Text(stringResource(R.string.url_label)) },
            singleLine = true,
            isError = validation is UrlValidation.Invalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            supportingText = {
                Text(
                    when (validation) {
                        UrlValidation.Blank -> stringResource(R.string.url_hint)
                        UrlValidation.Valid -> stringResource(R.string.url_valid)
                        is UrlValidation.Invalid -> validation.message
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onImportUrl,
            enabled = canImport && validation is UrlValidation.Valid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.url_import_button))
        }
    }
}
