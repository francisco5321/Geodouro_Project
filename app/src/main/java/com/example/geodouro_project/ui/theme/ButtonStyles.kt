package com.example.geodouro_project.ui.theme

import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun geodouroPrimaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = GeodouroBrandGreen,
    contentColor = GeodouroWhite,
    disabledContainerColor = GeodouroLightBg,
    disabledContentColor = GeodouroTextSecondary
)

@Composable
fun geodouroSecondaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = GeodouroGreen,
    contentColor = GeodouroTextPrimary,
    disabledContainerColor = GeodouroLightBg,
    disabledContentColor = GeodouroTextSecondary
)

@Composable
fun geodouroOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = GeodouroBrandGreen,
    disabledContentColor = GeodouroTextSecondary
)

fun geodouroOutlinedBorderColor(enabled: Boolean): Color {
    return if (enabled) GeodouroGreen.copy(alpha = 0.75f) else GeodouroLightBg
}

fun geodouroLoadingIndicatorColor(enabled: Boolean = true): Color {
    return if (enabled) GeodouroWhite else GeodouroTextSecondary
}
