package com.example.proxyscanner.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.proxyscanner.feature.home.HomeScreen
import com.example.proxyscanner.feature.results.ResultsScreen
import com.example.proxyscanner.feature.scan.ScannerViewModel
import com.example.proxyscanner.feature.settings.SettingsScreen

/** Destinations of the app. Navigation is a single state value (ADR-0007). */
enum class Screen { HOME, RESULTS, SETTINGS }

@Composable
fun ProxyScannerApp(viewModel: ScannerViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val state by viewModel.state.collectAsState()

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    when (screen) {
        Screen.HOME -> HomeScreen(
            state = state,
            onDocumentPicked = viewModel::onDocumentPicked,
            onPickerUnavailable = viewModel::onPickerUnavailable,
            onUrlChanged = viewModel::onUrlChanged,
            onImportUrl = viewModel::onImportUrl,
            onCancelImport = viewModel::cancelImport,
            onStartScan = viewModel::startScan,
            onCancelScan = viewModel::cancelScan,
            onOpenResults = { screen = Screen.RESULTS },
            onOpenSettings = { screen = Screen.SETTINGS },
        )
        Screen.RESULTS -> ResultsScreen(
            state = state,
            onBack = { screen = Screen.HOME },
        )
        Screen.SETTINGS -> SettingsScreen(
            state = state,
            onBack = { screen = Screen.HOME },
        )
    }
}
