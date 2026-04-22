package com.example.geodouro_project.ui.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

@Composable
fun geodouroOutlinedTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = GeodouroTextPrimary,
        unfocusedTextColor = GeodouroTextPrimary,
        disabledTextColor = GeodouroTextSecondary,
        focusedContainerColor = GeodouroWhite,
        unfocusedContainerColor = GeodouroWhite,
        disabledContainerColor = GeodouroLightBg,
        focusedBorderColor = GeodouroBrandGreen,
        unfocusedBorderColor = GeodouroOutline,
        disabledBorderColor = GeodouroGrey.copy(alpha = 0.3f),
        focusedLabelColor = GeodouroBrandGreen,
        unfocusedLabelColor = GeodouroTextSecondary,
        focusedPlaceholderColor = GeodouroTextSecondary,
        unfocusedPlaceholderColor = GeodouroTextSecondary,
        cursorColor = GeodouroBrandGreen,
        focusedLeadingIconColor = GeodouroBrandGreen,
        unfocusedLeadingIconColor = GeodouroGrey,
        focusedTrailingIconColor = GeodouroBrandGreen,
        unfocusedTrailingIconColor = GeodouroGrey
    )
}
