package com.example.proxyscanner.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.proxyscanner.R
import com.example.proxyscanner.app.theme.ProxyScannerTheme
import com.example.proxyscanner.core.engine.UnavailableProxyEngine
import com.example.proxyscanner.core.importer.DefaultConfigImporter
import com.example.proxyscanner.core.importer.DocumentReadResult
import com.example.proxyscanner.core.importer.DocumentReader
import com.example.proxyscanner.core.importer.UrlSyntaxValidator
import com.example.proxyscanner.core.model.ScanSettings
import com.example.proxyscanner.core.parser.DefaultConfigParser
import com.example.proxyscanner.core.testing.UnavailableScanOrchestrator
import com.example.proxyscanner.feature.scan.ScannerViewModel
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI tests for the Phase 02 foundation. They use the real placeholder
 * engine/orchestrator (no engine integrated) and a document reader that is never
 * called, so no file picker, network or proxy connection is involved.
 */
class ProxyScannerAppUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private object NoDocuments : DocumentReader {
        override suspend fun displayName(uri: String): String? = null
        override suspend fun read(uri: String, maxBytes: Long): DocumentReadResult = DocumentReadResult.Unreadable
    }

    private fun setApp() {
        val viewModel = ScannerViewModel(
            importer = DefaultConfigImporter(NoDocuments, DefaultConfigParser()),
            documents = NoDocuments,
            urlValidator = UrlSyntaxValidator(),
            engine = UnavailableProxyEngine(),
            orchestrator = UnavailableScanOrchestrator(),
            scanSettings = ScanSettings(),
        )
        rule.setContent { ProxyScannerTheme { ProxyScannerApp(viewModel) } }
    }

    private fun str(id: Int, vararg args: Any): String = rule.activity.getString(id, *args)

    @Test
    fun home_showsMainControls_andScanIsDisabledWithEngineMessage() {
        setApp()
        rule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.import_button)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.action_settings)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.scan_button)).assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.scan_msg_no_configs)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.scan_msg_engine)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.summary_none)).assertIsDisplayed()
    }

    @Test
    fun urlInput_showsValidationMessages() {
        setApp()
        rule.onNodeWithText(str(R.string.url_label)).performTextInput("http://example.com/list")
        rule.onNodeWithText("Plain http:// is not accepted. Use an https:// URL.").assertIsDisplayed()
    }

    @Test
    fun navigation_homeToResultsAndBack_showsEmptyState() {
        setApp()
        rule.onNodeWithText(str(R.string.summary_open)).performClick()
        rule.onNodeWithText(str(R.string.title_results)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.results_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.action_back)).performClick()
        rule.onNodeWithText(str(R.string.import_button)).assertIsDisplayed()
    }

    @Test
    fun navigation_homeToSettingsAndBack() {
        setApp()
        rule.onNodeWithText(str(R.string.action_settings)).performClick()
        rule.onNodeWithText(str(R.string.settings_readonly)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.action_back)).performClick()
        rule.onNodeWithText(str(R.string.scan_button)).assertIsDisplayed()
    }
}
