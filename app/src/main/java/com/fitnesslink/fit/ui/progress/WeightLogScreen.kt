package com.fitnesslink.fit.ui.progress

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.WeightChartPoint
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.viewmodel.WeightLogViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun WeightLogScreen(onBack: () -> Unit) {
    val viewModel: WeightLogViewModel = viewModel()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

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
                            value = viewModel.latestWeight?.let { "%.1f".format(it.weight) } ?: "\u2014",
                            unit = viewModel.preferredUnit.label,
                            label = "Current"
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(50.dp)
                                .background(Color(0xFFE0E0E0))
                        )
                        WeightStatColumn(
                            value = viewModel.weightChange?.let { "${if (it > 0) "+" else ""}${"%.1f".format(it)}" } ?: "\u2014",
                            unit = viewModel.preferredUnit.label,
                            label = "Last Change",
                            color = viewModel.weightChange?.let { if (it <= 0) FLPrimary else OrangeTheme } ?: Color.Black
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(50.dp)
                                .background(Color(0xFFE0E0E0))
                        )
                        WeightStatColumn(
                            value = viewModel.allTimeChange?.let { "${if (it > 0) "+" else ""}${"%.1f".format(it)}" } ?: "\u2014",
                            unit = viewModel.preferredUnit.label,
                            label = "Total",
                            color = viewModel.allTimeChange?.let { if (it <= 0) FLPrimary else OrangeTheme } ?: Color.Black
                        )
                    }
                }
            }

            // Chart
            if (viewModel.chartPoints.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Trend", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(12.dp))
                            WeightChart(
                                points = viewModel.chartPoints,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
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
                Text(
                    "History",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            items(viewModel.entries) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(dateFormat.format(entry.date), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (entry.notes.isNotEmpty()) {
                                Text(entry.notes, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "${"%.1f".format(entry.weight)} ${entry.unit.label}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Add entry dialog
    if (viewModel.showAddEntry) {
        AddWeightDialog(viewModel = viewModel, onDismiss = { viewModel.showAddEntry = false })
    }
}

@Composable
fun WeightStatColumn(value: String, unit: String, label: String, color: Color = Color.Black) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text(unit, fontSize = 13.sp, color = Color.Gray)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
fun WeightChart(points: List<WeightChartPoint>, modifier: Modifier = Modifier) {
    val primaryColor = FLPrimary
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val weights = points.map { it.weight }
        val minW = weights.min() - 2
        val maxW = weights.max() + 2
        val range = maxW - minW

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = (index.toFloat() / (points.size - 1)) * size.width
            val y = size.height - ((point.weight - minW) / range).toFloat() * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)

            // Draw point
            drawCircle(primaryColor, radius = 4.dp.toPx(), center = Offset(x, y))
        }
        drawPath(path, primaryColor, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
fun AddWeightDialog(viewModel: WeightLogViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Unit picker
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
                    onValueChange = { viewModel.newWeight = it },
                    label = { Text("Weight (${viewModel.preferredUnit.label})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = viewModel.newNotes,
                    onValueChange = { viewModel.newNotes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.addEntry(); onDismiss() },
                enabled = viewModel.newWeight.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = FLPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
