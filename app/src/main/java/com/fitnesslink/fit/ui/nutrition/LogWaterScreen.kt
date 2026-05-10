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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.WaterIntakeEntry
import com.fitnesslink.fit.model.WaterUnit
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.HydrationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogWaterScreen(
    onBack: () -> Unit,
    viewModel: HydrationViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadData() }

    var amountText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(viewModel.goal.unit) }
    var notes by remember { mutableStateOf("") }

    // Sync the unit picker default once the goal arrives from local DB.
    LaunchedEffect(viewModel.goal.unit) { unit = viewModel.goal.unit }

    val isValid = amountText.toDoubleOrNull()?.let { it > 0 } == true
    val quickAmounts: List<Double> =
        if (unit == WaterUnit.ML) listOf(250.0, 500.0, 750.0) else listOf(8.0, 16.0, 24.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Log Water",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            ProgressCard(
                total = viewModel.totalIntake,
                goal = viewModel.goal.dailyGoal,
                unitLabel = viewModel.goal.unit.rawValue,
                progress = viewModel.hydrationProgress.toFloat(),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickAmounts.forEach { amount ->
                    QuickAddButton(
                        amount = amount,
                        unit = unit,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.addEntry(
                                WaterIntakeEntry(
                                    amount = amount,
                                    unit = unit,
                                    loggedAt = Date(),
                                    notes = ""
                                )
                            )
                        }
                    )
                }
            }

            // Amount + unit + notes form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
            ) {
                LabeledField(label = "Amount") {
                    BasicTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = TextPrimaryColor,
                            textAlign = TextAlign.End
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(120.dp)
                    )
                }
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                UnitPicker(selected = unit, onSelected = { unit = it })
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                LabeledField(label = "Notes") {
                    BasicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = TextPrimaryColor,
                            textAlign = TextAlign.End
                        ),
                        modifier = Modifier.width(180.dp)
                    )
                }
            }

            // Daily goal editor
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
            ) {
                Text(
                    text = "Daily Goal",
                    fontSize = 12.sp,
                    color = TextSecondaryColor,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                )
                GoalEditor(
                    goal = viewModel.goal,
                    onChange = viewModel::updateGoal
                )
            }

            // Today's entries
            if (viewModel.todayEntries.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Today",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryColor
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(White, RoundedCornerShape(12.dp))
                    ) {
                        viewModel.todayEntries.forEachIndexed { index, entry ->
                            EntryRow(entry = entry, onDelete = { viewModel.deleteEntry(entry.id) })
                            if (index < viewModel.todayEntries.size - 1) {
                                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }

            // Save button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(
                        if (isValid) BlueTheme else MediumGray,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = isValid) {
                        val amount = amountText.toDoubleOrNull() ?: return@clickable
                        viewModel.addEntry(
                            WaterIntakeEntry(
                                amount = amount,
                                unit = unit,
                                loggedAt = Date(),
                                notes = notes
                            )
                        )
                        amountText = ""
                        notes = ""
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Log Water",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = White
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(
    total: Double,
    goal: Double,
    unitLabel: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatAmount(total),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueTheme
                )
                Text(
                    text = " / ${formatAmount(goal)} $unitLabel",
                    fontSize = 14.sp,
                    color = TextSecondaryColor,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondaryColor
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = BlueTheme,
            trackColor = BlueTheme.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun QuickAddButton(
    amount: Double,
    unit: WaterUnit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(White, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+${formatAmount(amount)} ${unit.rawValue}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = BlueTheme
        )
    }
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        content()
    }
}

@Composable
private fun UnitPicker(selected: WaterUnit, onSelected: (WaterUnit) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Unit", fontSize = 12.sp, color = TextSecondaryColor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundColor, RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WaterUnit.entries.forEach { u ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected == u) White else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelected(u) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = u.rawValue,
                        fontSize = 13.sp,
                        fontWeight = if (selected == u) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected == u) BlueTheme else TextSecondaryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalEditor(goal: HydrationGoal, onChange: (HydrationGoal) -> Unit) {
    var goalText by remember(goal.id) { mutableStateOf(formatAmount(goal.dailyGoal)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Target", fontSize = 14.sp, color = TextSecondaryColor)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicTextField(
                value = goalText,
                onValueChange = { v ->
                    val cleaned = v.filter { it.isDigit() || it == '.' }
                    goalText = cleaned
                    cleaned.toDoubleOrNull()?.let { parsed ->
                        if (parsed > 0) onChange(goal.copy(dailyGoal = parsed))
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryColor,
                    textAlign = TextAlign.End
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(70.dp)
            )
            Text(goal.unit.rawValue, fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun EntryRow(entry: WaterIntakeEntry, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.WaterDrop,
            contentDescription = null,
            tint = BlueTheme
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatAmount(entry.amount)} ${entry.unit.rawValue}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimaryColor
            )
            if (entry.notes.isNotEmpty()) {
                Text(text = entry.notes, fontSize = 12.sp, color = TextSecondaryColor)
            }
            Text(
                text = timeFormatter.format(entry.loggedAt),
                fontSize = 11.sp,
                color = TextSecondaryColor
            )
        }
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = "Delete",
            tint = TextSecondaryColor,
            modifier = Modifier.clickable(onClick = onDelete)
        )
    }
}

private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
