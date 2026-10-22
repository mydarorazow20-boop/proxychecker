package com.example.proxyscanner.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.proxyscanner.app.theme.ProxyScannerTheme
import com.example.proxyscanner.feature.scan.ScannerViewModel

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(applicationContext.contentResolver) }

    private val scannerViewModel: ScannerViewModel by viewModels {
        viewModelFactory { initializer { container.createScannerViewModel() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so system bar icons must be light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            ProxyScannerTheme {
                ProxyScannerApp(viewModel = scannerViewModel)
            }
        }
    }
}
