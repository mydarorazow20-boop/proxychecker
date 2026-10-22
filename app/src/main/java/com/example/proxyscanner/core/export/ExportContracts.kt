package com.example.proxyscanner.core.export

import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.model.ProxyTestResult
import com.example.proxyscanner.core.model.TestStatus

enum class ExportFormat(val mimeType: String, val fileExtension: String) {
    TXT("text/plain", "txt"),
    JSON("application/json", "json"),
}

data class ExportEntry(val config: ProxyConfig, val result: ProxyTestResult)

data class ExportRequest(
    val format: ExportFormat,
    val entries: List<ExportEntry>,
    /** Entries whose status is not in this set are skipped. Default: working only. */
    val includeStatuses: Set<TestStatus> = setOf(TestStatus.WORKING),
)

sealed interface ExportOutcome {
    data class Success(
        val content: String,
        val suggestedFileName: String,
        val mimeType: String,
        val exportedCount: Int,
    ) : ExportOutcome

    data class Failure(val reason: String) : ExportOutcome
}

/**
 * Exports selected configurations.
 * - TXT: one original configuration link per line (successful ones by default).
 * - JSON: configurations and test results as separate top-level arrays,
 *   linked by fingerprint.
 * Exported files contain credentials by design (they are the user's data);
 * the UI must say so, and exporters must never write the content to logs.
 */
interface ConfigExporter {
    fun export(request: ExportRequest): ExportOutcome
}
