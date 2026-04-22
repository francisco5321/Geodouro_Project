package com.example.geodouro_project.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GeodouroGreen,
    onPrimary = GeodouroWhite,
    primaryContainer = GeodouroDarkGreen,
    onPrimaryContainer = GeodouroWhite,
    secondary = GeodouroMint,
    onSecondary = GeodouroDarkGreen,
    secondaryContainer = GeodouroBrandGreen,
    onSecondaryContainer = GeodouroWhite,
    tertiary = GeodouroLightGreen,
    onTertiary = GeodouroDarkGreen,
    background = GeodouroDarkSurface,
    onBackground = GeodouroWhite,
    surface = GeodouroDarkSurface,
    onSurface = GeodouroWhite,
    surfaceVariant = GeodouroDarkSurfaceVariant,
    onSurfaceVariant = GeodouroLightGreen,
    outline = GeodouroDarkOutline,
    outlineVariant = GeodouroDarkOutline,
    error = GeodouroError,
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
    tertiary = GeodouroMint,
    onTertiary = GeodouroBrandGreen,
    background = GeodouroBg,
    onBackground = GeodouroTextPrimary,
    surface = GeodouroWhite,
    onSurface = GeodouroTextPrimary,
    surfaceVariant = GeodouroLightBg,
    onSurfaceVariant = GeodouroTextSecondary,
    outline = GeodouroOutline,
    outlineVariant = GeodouroLightGreen,
    error = GeodouroError,
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
