package com.blepulse.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkSlateBg = Color(0xFF090D16)
val DarkSurface = Color(0xFF111827)
val DarkSurfaceElevated = Color(0xFF1E293B)
val DarkBorder = Color(0xFF334155)

val BleCyan = Color(0xFF00E5FF)
val BleCyanDark = Color(0xFF00B0FF)
val ElectricViolet = Color(0xFF7C4DFF)
val SignalGreen = Color(0xFF00E676)
val WarningAmber = Color(0xFFFFB300)
val CriticalRed = Color(0xFFFF1744)

// Semantic aliases
val BleGreen = SignalGreen
val BleOrange = WarningAmber
val BleRed = CriticalRed
val BleViolet = ElectricViolet
val DarkCard = DarkSurfaceElevated

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

private val DarkColors = darkColorScheme(
    primary = BleCyan,
    onPrimary = Color.Black,
    secondary = ElectricViolet,
    onSecondary = Color.White,
    tertiary = SignalGreen,
    background = DarkSlateBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = CriticalRed,
    onError = Color.White
)

@Composable
fun BlePulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
