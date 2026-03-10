package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitnesslink.fit.model.ProgressTracker
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray

@Composable
fun StepProgressView(
    progress: ProgressTracker,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        progress.pages.forEach { page ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .background(if (page.selected) FLPrimary else MediumGray)
            )
        }
    }
}
