package com.fitnesslink.fit.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import com.fitnesslink.fit.model.BodyPart
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.MeasurementUnit
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.viewmodel.MeasurementsViewModel

/**
 * iOS `MeasurementsLogView`: latest entry as the shared
 * `MeasurementBreakdownView` (icons + deltas vs. previous), "Log
 * Measurements" sheet (unit, date, part toggles with values, notes), and
 * history rows that open the entry detail. Delete per entry is an Android
 * addition.
 *
 * [onOpenEntry] navigates to `measurementEntryDetail/{entryId}`; when the
 * host hasn't wired it (the inline `measurements` route only passes
 * `onBack`), the detail renders in place so the feature still works.
 */
@Composable
fun MeasurementsLogScreen(
    onBack: () -> Unit,
    onOpenEntry: ((String) -> Unit)? = null
) {
    val viewModel: MeasurementsViewModel = viewModel()
    var inlineEntryId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<MeasurementEntry?>(null) }

    LaunchedEffect(Unit) { viewModel.loadData() }

    inlineEntryId?.let { id ->
        MeasurementEntryDetailScreen(entryId = id, onBack = { inlineEntryId = null }, viewModel = viewModel)
        return
    }

    val openEntry: (String) -> Unit = onOpenEntry ?: { inlineEntryId = it }

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
                    "Measurements", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            viewModel.latestEntry?.let { latest ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Latest — ", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                                Text(longDate.format(latest.date), fontSize = 14.sp, color = TextSecondaryColor)
                            }
                            MeasurementBreakdownView(
                                entry = latest,
                                previous = viewModel.entries.getOrNull(1),
                                unit = viewModel.preferredUnit
                            )
                        }
                    }
                }
            }

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

            if (viewModel.entries.isEmpty()) {
                item {
                    Text(
                        "No measurements yet. Log your first set to start tracking changes.",
                        fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            if (viewModel.entries.size > 1) {
                item {
                    Text("History", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.padding(horizontal = 20.dp))
                }
                items(viewModel.entries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clickable { openEntry(entry.id) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(longDate.format(entry.date), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                                    Text("${entry.measurements.size} parts", fontSize = 12.sp, color = TextSecondaryColor)
                                }
                                if (entry.notes.isNotEmpty()) Text(entry.notes, fontSize = 12.sp, color = TextSecondaryColor)
                            }
                            IconButton(onClick = { pendingDelete = entry }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete entry", tint = MediumGray, modifier = Modifier.size(18.dp))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (viewModel.showAddEntry) {
        AddMeasurementDialog(viewModel = viewModel, onDismiss = { viewModel.showAddEntry = false })
    }

    pendingDelete?.let { entry ->
        ConfirmDeleteDialog(
            title = "Delete measurements?",
            message = "Remove the ${entry.measurements.size} measurements logged ${longDate.format(entry.date)}?",
            onConfirm = { viewModel.deleteEntry(entry.id) },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** iOS `AddMeasurementSheet`: unit, date, body parts by category with icons and values, notes. */
@Composable
fun AddMeasurementDialog(viewModel: MeasurementsViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Measurements") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 440.dp)
            ) {
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
                item { DatePickerField(date = viewModel.newDate, onDateChange = { viewModel.newDate = it }) }
                item { Text("Select body parts to measure", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor) }

                val grouped = BodyPart.entries.groupBy { it.category }
                BodyPart.categoryOrder.forEach { category ->
                    grouped[category]?.let { parts ->
                        item {
                            Text(category, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor, modifier = Modifier.padding(top = 4.dp))
                        }
                        items(parts) { part ->
                            val selected = viewModel.selectedParts.contains(part)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { viewModel.togglePart(part) },
                                    colors = CheckboxDefaults.colors(checkedColor = FLPrimary)
                                )
                                Icon(bodyPartIcon(part), contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(part.displayName, fontSize = 14.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                                if (selected) {
                                    OutlinedTextField(
                                        value = viewModel.values[part] ?: "",
                                        onValueChange = { viewModel.updateValue(part, it) },
                                        modifier = Modifier.width(96.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        placeholder = { Text("0.0", fontSize = 13.sp) },
                                        suffix = { Text(viewModel.preferredUnit.label, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = viewModel.newNotes,
                        onValueChange = { viewModel.newNotes = it },
                        label = { Text("Notes (optional)") },
                        placeholder = { Text("Any observations?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.addEntry() },
                enabled = viewModel.canSave,
                colors = ButtonDefaults.buttonColors(containerColor = FLPrimary)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
