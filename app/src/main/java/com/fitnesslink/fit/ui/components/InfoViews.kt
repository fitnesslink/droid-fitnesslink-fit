package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.TextSecondaryColor

@Composable
fun TrainingLevelInfoView(level: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Speed,
            contentDescription = "Training Level",
            tint = TextSecondaryColor,
            modifier = Modifier.width(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = level, fontSize = 13.sp, color = TextSecondaryColor)
    }
}

@Composable
fun TimeInfoView(time: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.HourglassBottom,
            contentDescription = "Time",
            tint = TextSecondaryColor,
            modifier = Modifier.width(12.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = time, fontSize = 13.sp, color = TextSecondaryColor)
    }
}

@Composable
fun ExercisesInfoView(count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = "Exercises",
            tint = TextSecondaryColor,
            modifier = Modifier.width(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "$count exercises", fontSize = 13.sp, color = TextSecondaryColor)
    }
}
