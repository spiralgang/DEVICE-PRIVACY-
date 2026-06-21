package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Bold neon / graffiti palette: hot magenta + electric cyan + acid green on near-black.
val NeonMagenta = Color(0xFFFF2BD6)
val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF39FF14)
val NeonPurple = Color(0xFF7A1FA2)
val InkBlack = Color(0xFF07060A)
val InkPanel = Color(0xFF15111F)
val InkPanelHi = Color(0xFF211A33)

private val GraffitiColorScheme = darkColorScheme(
    primary = NeonMagenta,
    onPrimary = InkBlack,
    primaryContainer = NeonPurple,
    onPrimaryContainer = NeonCyan,
    secondary = NeonCyan,
    onSecondary = InkBlack,
    tertiary = NeonGreen,
    background = InkBlack,
    onBackground = Color(0xFFF2E9FF),
    surface = InkPanel,
    onSurface = Color(0xFFF2E9FF),
    surfaceVariant = InkPanelHi,
    onSurfaceVariant = Color(0xFFD7C8FF),
    error = Color(0xFFFF4D6D),
    onError = InkBlack
)

@Composable
fun PrivacyTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = GraffitiColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = InkBlack.toArgb()
        window.navigationBarColor = InkBlack.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
