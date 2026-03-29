package com.fitnesslink.fit.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.BodyPart
import com.fitnesslink.fit.model.MeasurementUnit
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.viewmodel.MeasurementsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MeasurementsLogScreen(onBack: () -> Unit) {
    val viewModel: MeasurementsViewModel = viewModel()
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
            // Latest measurements
            viewModel.latestEntry?.let { latest ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row {
                                Text("Latest", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(dateFormat.format(latest.date), fontSize = 14.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            val grouped = latest.measurements.groupBy { it.bodyPart.category }
                            listOf("Core", "Arms", "Legs", "Other").forEach { category ->
                                grouped[category]?.let { parts ->
                                    Text(
                                        category,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                    parts.forEach { m ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(m.bodyPart.displayName, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                "${"%.1f".format(m.value)} ${m.unit.label}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )

                                            viewModel.changes(m.bodyPart)?.let { change ->
                                                if (change != 0.0) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "${if (change > 0) "+" else ""}${"%.1f".format(change)}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (change < 0) FLPrimary else OrangeTheme,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(
                                                                if (change < 0) FLPrimary.copy(alpha = 0.1f)
                                                                else OrangeTheme.copy(alpha = 0.1f)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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
                    Text("Log Measurements", fontWeight = FontWeight.SemiBold)
                }
            }

            // History
            if (viewModel.entries.size > 1) {
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
                            Text("${entry.measurements.size} parts", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }

    // Add entry dialog
    if (viewModel.showAddEntry) {
        AddMeasurementDialog(viewModel = viewModel, onDismiss = { viewModel.showAddEntry = false })
    }
}

@Composable
fun AddMeasurementDialog(viewModel: MeasurementsViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Measurements") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                // Unit picker
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MeasurementUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = viewModel.preferredUnit == unit,
                                onClick = { viewModel.preferredUnit = unit },
                                label = { Text(unit.label) }
                            )
                        }
                    }
                }

                // Body parts grouped by category
                val grouped = BodyPart.entries.groupBy { it.category }
                listOf("Core", "Arms", "Legs", "Other").forEach { category ->
                    grouped[category]?.let { parts ->
                        item {
                            Text(
                                category,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(parts) { part ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = viewModel.selectedParts.contains(part),
                                    onCheckedChange = { viewModel.togglePart(part) },
                                    colors = CheckboxDefaults.colors(checkedColor = FLPrimary)
                                )
                                Text(part.displayName, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                if (viewModel.selectedParts.contains(part)) {
                                    OutlinedTextField(
                                        value = viewModel.values[part] ?: "",
                                        onValueChange = { viewModel.updateValue(part, it) },
                                        modifier = Modifier.width(80.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        suffix = { Text(viewModel.preferredUnit.label, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Notes
                item {
                    OutlinedTextField(
                        value = viewModel.newNotes,
                        onValueChange = { viewModel.newNotes = it },
                        label = { Text("Notes (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.addEntry(); onDismiss() },
                enabled = viewModel.selectedParts.isNotEmpty(),
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
