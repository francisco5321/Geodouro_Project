package com.example.geodouro_project.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.geodouro_project.R

@Composable
fun GeoFloraHeaderLogo(
    modifier: Modifier = Modifier,
    height: Dp = 34.dp
) {
    Image(
        painter = painterResource(id = R.drawable.logo_geoflora),
        contentDescription = "GeoFlora",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(height)
            .width(height * LOGO_ASPECT_RATIO)
    )
}

private const val LOGO_ASPECT_RATIO = 471f / 125f
