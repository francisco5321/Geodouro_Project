package com.example.geodouro_project.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GeodouroGreen,
    onPrimary = GeodouroWhite,
    primaryContainer = GeodouroBrandGreen,
    onPrimaryContainer = GeodouroWhite,
    secondary = GeodouroLightGreen,
    onSecondary = GeodouroTextPrimary,
    secondaryContainer = GeodouroDarkGreen,
    onSecondaryContainer = GeodouroWhite,
    tertiary = GeodouroWhite,
    onTertiary = GeodouroTextPrimary,
    background = GeodouroTextPrimary,
    onBackground = GeodouroWhite,
    surface = Color(0xFF1E1E1E),
    onSurface = GeodouroWhite,
    surfaceVariant = Color(0xFF2B2B2B),
    onSurfaceVariant = Color(0xFFD0D0D0),
    outline = GeodouroGrey,
    outlineVariant = Color(0xFF4A4A4A),
    error = Color(0xFFCF6679),
    onError = GeodouroWhite
)

private val LightColorScheme = lightColorScheme(
    primary = GeodouroBrandGreen,
    onPrimary = GeodouroWhite,
    primaryContainer = GeodouroGreen,
    onPrimaryContainer = GeodouroTextPrimary,
    secondary = GeodouroGreen,
    onSecondary = GeodouroTextPrimary,
    secondaryContainer = GeodouroLightGreen,
    onSecondaryContainer = GeodouroTextPrimary,
    tertiary = GeodouroWhite,
    onTertiary = GeodouroTextPrimary,
    background = GeodouroBg,
    onBackground = GeodouroTextPrimary,
    surface = GeodouroWhite,
    onSurface = GeodouroTextPrimary,
    surfaceVariant = GeodouroLightBg,
    onSurfaceVariant = GeodouroTextSecondary,
    outline = GeodouroGreen.copy(alpha = 0.7f),
    outlineVariant = GeodouroLightBg,
    error = Color(0xFFB3261E),
    onError = GeodouroWhite
)

@Composable
fun Geodouro_ProjectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> LightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
