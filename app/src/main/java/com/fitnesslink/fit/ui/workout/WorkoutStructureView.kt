package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.workouts.WorkoutPhaseSummary

/**
 * "Workout Structure" (FTNSSLNK-407): every phase in order with its one-line
 * summary. Rows are tappable so the parent can scroll to the phase.
 */
@Composable
fun WorkoutStructureView(phases: List<WorkoutPhaseSummary>, onSelect: ((WorkoutPhaseSummary) -> Unit)? = null) {
    if (phases.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Workout Structure", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(White)) {
            phases.forEachIndexed { index, phase ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = onSelect != null) { onSelect?.invoke(phase) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = phase.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                    Text(text = "·", fontSize = 14.sp, color = TextSecondaryColor)
                    Text(
                        text = phase.summary, fontSize = 14.sp, color = TextSecondaryColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Shows this phase's exercises", tint = FLPrimary, modifier = Modifier.size(16.dp))
                }
                if (index < phases.size - 1) HorizontalDivider(modifier = Modifier.padding(start = 14.dp))
            }
        }
    }
}
