package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.HydrationPresets
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.HydrationViewModel
import java.util.Date

/**
 * Water tile for one day (port of iOS `HydrationCardView`, FTNSSLNK-195/221):
 * total vs goal in the preferred display unit, +8 / +16 / +24 oz presets
 * converted into the goal unit, undo of the last preset tap (this session,
 * this day), and a "Log" link to the full screen. Presets are hidden on a
 * future day (plan-only view).
 */
@Composable
fun HydrationCardView(date: String, modifier: Modifier = Modifier, onLog: (() -> Unit)? = null) {
    val viewModel: HydrationViewModel = viewModel(key = "hydration-card-$date")
    LaunchedEffect(date) { viewModel.loadData(date) }

    Column(
        modifier = modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WaterDrop, contentDescription = "Water", tint = BlueTheme)
            Text("Water", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp).weight(1f))
            if (viewModel.canUndo) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { viewModel.undoLastTap() }.padding(end = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = BlueTheme, modifier = Modifier.size(14.dp))
                    Text("Undo", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = BlueTheme)
                }
            }
            if (onLog != null && viewModel.canLog) {
                Text("Log ›", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = BlueTheme, modifier = Modifier.clickable(onClick = onLog))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(formatAmount(viewModel.totalIntake), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BlueTheme)
            if (viewModel.dailyGoalDisplay > 0) {
                Text(" / ${formatAmount(viewModel.dailyGoalDisplay)} ${viewModel.displayUnit.rawValue}", fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.padding(bottom = 2.dp).weight(1f))
                Text("${(viewModel.hydrationProgress * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
            } else {
                Text(" ${viewModel.displayUnit.rawValue}", fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.padding(bottom = 2.dp))
            }
        }

        LinearProgressIndicator(progress = { viewModel.hydrationProgress.toFloat() }, modifier = Modifier.fillMaxWidth(), color = BlueTheme, trackColor = BlueTheme.copy(alpha = 0.15f))

        if (viewModel.canLog) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HydrationPresets.presetOunces.forEach { ounces ->
                    Text(
                        "+${ounces.toInt()} oz", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BlueTheme, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).background(BlueTheme.copy(alpha = 0.10f), RoundedCornerShape(8.dp)).clickable { viewModel.logPreset(ounces) }.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/** Pre-Wave-3 entry point (`NutritionHomeScreen`): today's card with a Log link. */
@Composable
fun HydrationCard(
    onLogClick: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") viewModel: HydrationViewModel? = null
) {
    HydrationCardView(date = MealPlanDay.string(Date()), modifier = modifier, onLog = onLogClick)
}

internal fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
