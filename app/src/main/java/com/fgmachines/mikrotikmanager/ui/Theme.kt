package com.fgmachines.mikrotikmanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy900 = Color(0xFF081017)
val Navy800 = Color(0xFF0C1319)
val Gunmetal = Color(0xFF2E3945)
val Silver = Color(0xFF9E9B98)
val Platinum = Color(0xFFD9D7D4)
val ElectricBlue = Color(0xFF65A9E8)
val CyanAccent = Color(0xFF59D7E8)
val GoldAccent = Color(0xFFD4A84D)

private val FgDarkColors = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Navy900,
    primaryContainer = Color(0xFF173349),
    onPrimaryContainer = Color(0xFFD7EBFF),
    secondary = Platinum,
    onSecondary = Color(0xFF111820),
    secondaryContainer = Gunmetal,
    onSecondaryContainer = Color(0xFFF0F3F5),
    tertiary = GoldAccent,
    onTertiary = Color(0xFF241A00),
    tertiaryContainer = Color(0xFF4C3B10),
    onTertiaryContainer = Color(0xFFFFE7A3),
    background = Navy800,
    onBackground = Color(0xFFE9EEF2),
    surface = Color(0xFF121B23),
    onSurface = Color(0xFFE9EEF2),
    surfaceVariant = Gunmetal,
    onSurfaceVariant = Platinum,
    outline = Silver,
    outlineVariant = Color(0xFF46515D),
    error = Color(0xFFFFB4AB)
)

@Composable
fun FgMikroTikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FgDarkColors,
        content = content
    )
}
