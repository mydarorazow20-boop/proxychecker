package com.example.proxyscanner.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark-first palette. The app is dark in this version regardless of the system
 * setting (a theme choice would need persistence, which is not decided yet).
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFA0F2EA),
    secondary = Color(0xFFB0CCC8),
    onSecondary = Color(0xFF1B3532),
    background = Color(0xFF0F1415),
    onBackground = Color(0xFFDEE4E3),
    surface = Color(0xFF0F1415),
    onSurface = Color(0xFFDEE4E3),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C7),
    surfaceContainerLowest = Color(0xFF0A0F10),
    surfaceContainerLow = Color(0xFF171D1E),
    surfaceContainer = Color(0xFF1B2122),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303637),
    outline = Color(0xFF899392),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun ProxyScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
