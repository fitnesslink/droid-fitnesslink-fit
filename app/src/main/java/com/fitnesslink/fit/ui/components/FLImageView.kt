package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.fitnesslink.fit.ui.theme.ImagePlaceholder

@Composable
fun FLImageView(
    url: String = "",
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(ImagePlaceholder),
        contentAlignment = Alignment.Center
    ) {
        if (url.isEmpty()) {
            // Placeholder
        } else {
            CircularProgressIndicator()
        }
    }
}
