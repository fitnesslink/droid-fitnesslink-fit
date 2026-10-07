package com.fitnesslink.fit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.components.ActionButtonView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * Stepper sheet for the "workouts this week" display goal (iOS
 * `WeeklyGoalSheet`, FTNSSLNK-365). Device-local: it only drives the
 * dashboard tile's "Goal:" line. Range 1–14.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyGoalSheet(current: Int, onSave: (Int) -> Unit, onCancel: () -> Unit) {
    var goal by remember { mutableIntStateOf(current.coerceIn(1, 14)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState, containerColor = BackgroundColor) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Cancel",
                    fontSize = 15.sp,
                    color = FLPrimary,
                    modifier = Modifier.clickable(onClick = onCancel)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "Weekly Workout Goal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(44.dp, 1.dp))
            }
            Text(
                text = "How many workouts do you want to complete each week?",
                fontSize = 15.sp,
                color = TextSecondaryColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StepButton(icon = Icons.Filled.Remove, description = "Decrease") { goal = maxOf(1, goal - 1) }
                Text(
                    text = goal.toString(),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = FLPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.defaultMinSize(minWidth = 70.dp)
                )
                StepButton(icon = Icons.Filled.Add, description = "Increase") { goal = minOf(14, goal + 1) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ActionButtonView(text = "Save Goal", isEnabled = true, isLoading = false, onClick = { onSave(goal) })
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(White)
            .clickable(onClick = onClick)
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = TextPrimaryColor, modifier = Modifier.size(20.dp))
    }
}
