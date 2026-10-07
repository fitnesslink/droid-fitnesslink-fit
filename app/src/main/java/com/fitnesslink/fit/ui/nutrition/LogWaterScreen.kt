package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.WaterIntakeEntry
import com.fitnesslink.fit.model.WaterUnit
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.HydrationViewModel
import com.fitnesslink.fit.viewmodel.MealPlanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log water for one day (port of iOS `LogWaterView`, FTNSSLNK-221): a past
 * [date] backfills (entries stamped local noon, banner makes it obvious),
 * quick-add presets in the chosen unit, server "Recent" custom amounts,
 * amount / unit / notes form, the day's entries with delete. Android-only
 * extras kept: the daily goal editor and a preferred display-unit picker.
 */
@Composable
fun LogWaterScreen(
    onBack: () -> Unit,
    date: String? = null,
    viewModel: HydrationViewModel = viewModel()
) {
    val day = date?.let { MealPlanDay.normalize(it) } ?: MealPlanDay.string(Date())
    LaunchedEffect(day) { viewModel.loadData(day); viewModel.loadRecentAmounts() }

    var amountText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(viewModel.displayUnit) }
    var notes by remember { mutableStateOf("") }
    var unitTouched by remember { mutableStateOf(false) }
    // Follow the preferred display unit until the user picks one by hand.
    LaunchedEffect(viewModel.displayUnit) { if (!unitTouched) unit = viewModel.displayUnit }

    val isValid = amountText.toDoubleOrNull()?.let { it > 0 } == true
    val isBackfilling = !viewModel.isViewingToday
    val dayLabel = "${MealPlanViewModel.weekdayFull(day)}, ${MealPlanViewModel.mediumDay(day)}"

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        PlanTopBar(title = "Log Water", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isBackfilling) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(BlueTheme.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = BlueTheme, modifier = Modifier.size(16.dp))
                    Text("Logging for $dayLabel", fontSize = 13.sp, color = BlueTheme)
                }
            }

            // Progress
            PlanCard(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatAmount(viewModel.totalIntake), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = BlueTheme)
                    Text(" / ${formatAmount(viewModel.dailyGoalDisplay)} ${viewModel.displayUnit.rawValue}", fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.padding(bottom = 3.dp).weight(1f))
                    Text("${(viewModel.hydrationProgress * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                }
                LinearProgressIndicator(progress = { viewModel.hydrationProgress.toFloat() }, modifier = Modifier.fillMaxWidth(), color = BlueTheme, trackColor = BlueTheme.copy(alpha = 0.15f))
            }

            // Quick add
            Row(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.presetAmounts(unit).forEach { amount ->
                    Text(
                        "+${formatAmount(amount)} ${unit.rawValue}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BlueTheme, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).background(White, RoundedCornerShape(12.dp)).clickable(enabled = viewModel.canLog) { viewModel.log(amount, unit) }.padding(vertical = 12.dp)
                    )
                }
            }

            // Recent custom amounts (FTNSSLNK-221)
            val recents = viewModel.recentAmounts.filter { it.waterUnit != null }
            if (recents.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recent", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recents, key = { it.id }) { recent ->
                            val recentUnit = recent.waterUnit ?: return@items
                            Text(
                                "${formatAmount(recent.amount)} ${recentUnit.rawValue}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BlueTheme,
                                modifier = Modifier.background(BlueTheme.copy(alpha = 0.1f), RoundedCornerShape(10.dp)).clickable(enabled = viewModel.canLog) { viewModel.log(recent.amount, recentUnit) }.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // Form
            Column(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                FieldBlock("Amount") {
                    PlanTextField(amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' } }, "e.g. 12", keyboardType = KeyboardType.Decimal)
                }
                PlanDivider()
                FieldBlock("Unit") {
                    SegmentedUnits(unit) { unit = it; unitTouched = true }
                }
                PlanDivider()
                FieldBlock("Notes (optional)") { PlanTextField(notes, { notes = it }, "e.g. post-workout") }
                PlanDivider()
                GoalEditor(viewModel.goal) { viewModel.updateGoal(it) }
                PlanDivider()
                FieldBlock("Display unit") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanChipLabel("Automatic", viewModel.goal.preferredUnit == null) { viewModel.setPreferredUnit(null) }
                        WaterUnit.entries.forEach { u -> PlanChipLabel(u.rawValue, viewModel.goal.preferredUnit == u) { viewModel.setPreferredUnit(u) } }
                    }
                }
            }

            // Entries
            if (viewModel.dayEntries.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isBackfilling) dayLabel else "Today", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                    Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                        viewModel.dayEntries.forEachIndexed { index, entry ->
                            EntryRow(entry, viewModel.displayUnit) { viewModel.deleteEntry(entry.id) }
                            if (index < viewModel.dayEntries.lastIndex) PlanDivider()
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(if (isValid && viewModel.canLog) BlueTheme else DisabledButton, RoundedCornerShape(12.dp))
                    .clickable(enabled = isValid && viewModel.canLog) {
                        val amount = amountText.toDoubleOrNull() ?: return@clickable
                        viewModel.log(amount, unit, notes)
                        amountText = ""; notes = ""
                    }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("Log Water", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White) }
        }
    }
}

@Composable
private fun FieldBlock(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondaryColor)
        content()
    }
}

@Composable
private fun SegmentedUnits(selected: WaterUnit, onSelect: (WaterUnit) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(BackgroundColor, RoundedCornerShape(8.dp)).padding(3.dp)) {
        WaterUnit.entries.forEach { u ->
            val isSelected = u == selected
            Box(
                modifier = Modifier.weight(1f).background(if (isSelected) White else Color.Transparent, RoundedCornerShape(6.dp)).clickable { onSelect(u) }.padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) { Text(u.rawValue, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = TextPrimaryColor) }
        }
    }
}

/** Android-only daily goal editor (iOS has no UI for the amount). */
@Composable
private fun GoalEditor(goal: HydrationGoal, onChange: (HydrationGoal) -> Unit) {
    var goalText by remember(goal.id, goal.dailyGoal) { mutableStateOf(formatAmount(goal.dailyGoal)) }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Daily goal", fontSize = 14.sp, color = TextSecondaryColor)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicTextField(
                value = goalText,
                onValueChange = { v ->
                    val cleaned = v.filter { it.isDigit() || it == '.' }
                    goalText = cleaned
                    cleaned.toDoubleOrNull()?.let { parsed -> if (parsed > 0) onChange(goal.copy(dailyGoal = parsed)) }
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, textAlign = TextAlign.End),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(70.dp)
            )
            Text(goal.unit.rawValue, fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun EntryRow(entry: WaterIntakeEntry, displayUnit: WaterUnit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = BlueTheme)
        Column(modifier = Modifier.weight(1f)) {
            Text("${formatAmount(entry.unit.convert(entry.amount, displayUnit))} ${displayUnit.rawValue}", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            if (entry.notes.isNotEmpty()) Text(entry.notes, fontSize = 12.sp, color = TextSecondaryColor)
            Text(timeFormatter.format(entry.loggedAt), fontSize = 11.sp, color = TextSecondaryColor)
        }
        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = TextSecondaryColor, modifier = Modifier.clickable(onClick = onDelete))
    }
}

private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
