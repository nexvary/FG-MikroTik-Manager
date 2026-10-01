package com.fgmachines.mikrotikmanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FgDarkColors = darkColorScheme(
    primary = Color(0xFF6A88A0),
    onPrimary = Color(0xFF071019),
    secondary = Color(0xFFD9D7D4),
    onSecondary = Color(0xFF111820),
    background = Color(0xFF0C1319),
    onBackground = Color(0xFFE9EEF2),
    surface = Color(0xFF151E27),
    onSurface = Color(0xFFE9EEF2),
    surfaceVariant = Color(0xFF2E3945),
    onSurfaceVariant = Color(0xFFD9D7D4),
    outline = Color(0xFF9E9B98),
    error = Color(0xFFFFB4AB)
)

@Composable
fun FgMikroTikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FgDarkColors,
        content = content
    )
}
