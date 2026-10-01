package com.fgmachines.mikrotikmanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// FG Link 2.1.2 visual identity
val FgBlack = Color(0xFF02080E)
val FgDeepNavy = Color(0xFF04131F)
val FgNavy = Color(0xFF071C2B)
val FgPanel = Color(0xFF0A1F30)
val FgPanelRaised = Color(0xFF0E2A3D)
val FgSilver = Color(0xFFC8D5DF)
val FgSilverMuted = Color(0xFF8FA5B5)
val FgWhite = Color(0xFFF4F7FA)

val FgBlue = Color(0xFF159DFF)
val FgCyan = Color(0xFF16D4E8)
val FgMint = Color(0xFF42E6A4)
val FgPurple = Color(0xFFA955F7)
val FgAmber = Color(0xFFFFBE43)
val FgMagenta = Color(0xFFE94BD6)

// Backwards-compatible aliases used by existing screens.
val Navy900 = FgBlack
val Navy800 = FgDeepNavy
val Gunmetal = FgPanelRaised
val Silver = FgSilverMuted
val Platinum = FgSilver
val ElectricBlue = FgBlue
val CyanAccent = FgCyan
val GoldAccent = FgAmber

private val FgDarkColors = darkColorScheme(
    primary = FgBlue,
    onPrimary = FgBlack,
    primaryContainer = Color(0xFF0B3855),
    onPrimaryContainer = Color(0xFFD7EEFF),

    secondary = FgMint,
    onSecondary = FgBlack,
    secondaryContainer = Color(0xFF0C493D),
    onSecondaryContainer = Color(0xFFC9FFE9),

    tertiary = FgPurple,
    onTertiary = FgWhite,
    tertiaryContainer = Color(0xFF3C1B62),
    onTertiaryContainer = Color(0xFFEEDCFF),

    background = FgBlack,
    onBackground = FgWhite,
    surface = FgDeepNavy,
    onSurface = FgWhite,
    surfaceVariant = FgPanel,
    onSurfaceVariant = FgSilver,

    outline = FgSilverMuted,
    outlineVariant = Color(0xFF4C6577),

    error = Color(0xFFFF7B7B),
    errorContainer = Color(0xFF5C1C25),
    onErrorContainer = Color(0xFFFFD9DD)
)

@Composable
fun FgMikroTikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FgDarkColors,
        content = content
    )
}
