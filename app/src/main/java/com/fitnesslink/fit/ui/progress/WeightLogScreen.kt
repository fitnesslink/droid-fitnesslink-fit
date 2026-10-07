package com.fitnesslink.fit.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.WeightEntry
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.profile.charts.ReportLineChart
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.viewmodel.WeightLogViewModel
import java.util.Locale

/**
 * iOS `WeightLogView`: Current / Last Change / Total stats, trend chart (now
 * `ReportLineChart` with axes), history, add sheet (unit, weight, date
 * picker, notes). Delete per row with confirmation is an Android addition.
 */
@Composable
fun WeightLogScreen(onBack: () -> Unit) {
    val viewModel: WeightLogViewModel = viewModel()
    var pendingDelete by remember { mutableStateOf<WeightEntry?>(null) }

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Weight", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            // Summary card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        WeightStatColumn(
                            value = viewModel.latestWeight?.let { fmt(viewModel.displayWeight(it)) } ?: "—",
                            unit = viewModel.preferredUnit.label,
                            label = "Current"
                        )
                        StatDivider()
                        WeightStatColumn(
                            value = viewModel.weightChange?.let { signed(it) } ?: "—",
                            unit = viewModel.preferredUnit.label,
                            label = "Last Change",
                            color = viewModel.weightChange?.let { if (it <= 0) FLPrimary else OrangeTheme } ?: TextPrimaryColor
                        )
                        StatDivider()
                        WeightStatColumn(
                            value = viewModel.allTimeChange?.let { signed(it) } ?: "—",
                            unit = viewModel.preferredUnit.label,
                            label = "Total",
                            color = viewModel.allTimeChange?.let { if (it <= 0) FLPrimary else OrangeTheme } ?: TextPrimaryColor
                        )
                    }
                }
            }

            // Chart
            val points = viewModel.reportPoints
            if (points.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Trend", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                            Spacer(modifier = Modifier.height(12.dp))
                            val values = points.map { it.value }
                            val pad = ((values.max() - values.min()) * 0.15).coerceAtLeast(2.0)
                            ReportLineChart(
                                points = points,
                                color = FLPrimary,
                                height = 200.dp,
                                fill = false,
                                yRange = (values.min() - pad)..(values.max() + pad),
                                yLabel = { fmt(it) }
                            )
                        }
                    }
                }
            }

            // Add button
            item {
                Button(
                    onClick = { viewModel.showAddEntry = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FLPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log Weight", fontWeight = FontWeight.SemiBold)
                }
            }

            // History
            item {
                Text("History", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.padding(horizontal = 20.dp))
            }
            if (viewModel.entries.isEmpty()) {
                item {
                    Text(
                        "No weigh-ins yet. Log your first weight to start the trend.",
                        fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
            items(viewModel.entries, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(longDate.format(entry.date), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                            if (entry.notes.isNotEmpty()) Text(entry.notes, fontSize = 12.sp, color = TextSecondaryColor)
                        }
                        Text(
                            "${fmt(viewModel.displayWeight(entry))} ${viewModel.preferredUnit.label}",
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor
                        )
                        IconButton(onClick = { pendingDelete = entry }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete weigh-in", tint = MediumGray, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (viewModel.showAddEntry) {
        AddWeightDialog(viewModel = viewModel, onDismiss = { viewModel.showAddEntry = false })
    }

    pendingDelete?.let { entry ->
        ConfirmDeleteDialog(
            title = "Delete weigh-in?",
            message = "Remove ${fmt(viewModel.displayWeight(entry))} ${viewModel.preferredUnit.label} logged ${longDate.format(entry.date)}?",
            onConfirm = { viewModel.deleteEntry(entry.id) },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(50.dp)
            .background(Color(0xFFE0E0E0))
    )
}

@Composable
fun WeightStatColumn(value: String, unit: String, label: String, color: Color = TextPrimaryColor) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text(unit, fontSize = 13.sp, color = TextSecondaryColor)
        Text(label, fontSize = 11.sp, color = TextSecondaryColor)
    }
}

/** iOS `AddWeightEntrySheet`: unit picker, weight, date, notes. */
@Composable
fun AddWeightDialog(viewModel: WeightLogViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeightUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = viewModel.preferredUnit == unit,
                            onClick = { viewModel.preferredUnit = unit },
                            label = { Text(unit.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = viewModel.newWeight,
                    onValueChange = { v -> viewModel.newWeight = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("Weight (${viewModel.preferredUnit.label})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(date = viewModel.newDate, onDateChange = { viewModel.newDate = it })
                OutlinedTextField(
                    value = viewModel.newNotes,
                    onValueChange = { viewModel.newNotes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("How are you feeling?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.addEntry() },
                enabled = (viewModel.newWeight.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = FLPrimary)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun fmt(v: Double): String = String.format(Locale.US, "%.1f", v)
private fun signed(v: Double): String = "${if (v > 0) "+" else ""}${fmt(v)}"
