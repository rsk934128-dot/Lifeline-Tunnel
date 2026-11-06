package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyberPrimary,
    onPrimary = CyberBackground,
    secondary = CyberSecondary,
    onSecondary = CyberBackground,
    tertiary = CyberTertiary,
    onTertiary = CyberBackground,
    background = CyberBackground,
    onBackground = CyberTextPrimary,
    surface = CyberSurface,
    onSurface = CyberTextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberTextSecondary,
    error = CyberError,
    onError = CyberTextPrimary
)

private val LightColorScheme = darkColorScheme(
    // Force dark theme even in light mode for a premium immersive tactical vibe!
    primary = CyberPrimary,
    onPrimary = CyberBackground,
    secondary = CyberSecondary,
    onSecondary = CyberBackground,
    tertiary = CyberTertiary,
    onTertiary = CyberBackground,
    background = CyberBackground,
    onBackground = CyberTextPrimary,
    surface = CyberSurface,
    onSurface = CyberTextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberTextSecondary,
    error = CyberError,
    onError = CyberTextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to true for immersive tactical emergency feel
    dynamicColor: Boolean = false, // Disable dynamic colors to preserve our beautiful hand-crafted cyber theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
