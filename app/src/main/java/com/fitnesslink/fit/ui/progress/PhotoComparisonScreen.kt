package com.fitnesslink.fit.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.MeasurementUnit
import com.fitnesslink.fit.model.PhotoAngle
import com.fitnesslink.fit.model.ProgressPhotoEntry
import com.fitnesslink.fit.progress.MeasurementMath
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.PhotoComparisonViewModel
import java.util.Locale

/**
 * iOS `PhotoComparisonView`: angle selector, Before / After pickers
 * (`DateEntryPicker`), the real images side by side, weight per side,
 * Weight Change and Measurement Changes cards (from the real caches).
 */
@Composable
fun PhotoComparisonScreen(onBack: () -> Unit) {
    val viewModel: PhotoComparisonViewModel = viewModel()
    var showBeforePicker by remember { mutableStateOf(false) }
    var showAfterPicker by remember { mutableStateOf(false) }
    val unit = MeasurementUnit.defaultUnit

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(White)
        ) {
            PhotoAngle.entries.forEach { angle ->
                val isSelected = viewModel.selectedAngle == angle
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.selectedAngle = angle }
                        .background(if (isSelected) FLPrimary.copy(alpha = 0.1f) else Color.Transparent)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        angle.displayName, fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) FLPrimary else TextSecondaryColor
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(top = 16.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ComparisonColumn(
                        entry = viewModel.beforeEntry, angle = viewModel.selectedAngle,
                        weightLabel = viewModel.beforeEntry?.let { e -> viewModel.closestWeight(e.date)?.let { "${fmt(it.weight)} ${it.unit.label}" } },
                        onPick = { showBeforePicker = true }, modifier = Modifier.weight(1f)
                    )
                    ComparisonColumn(
                        entry = viewModel.afterEntry, angle = viewModel.selectedAngle,
                        weightLabel = viewModel.afterEntry?.let { e -> viewModel.closestWeight(e.date)?.let { "${fmt(it.weight)} ${it.unit.label}" } },
                        onPick = { showAfterPicker = true }, modifier = Modifier.weight(1f)
                    )
                }
            }

            val before = viewModel.beforeEntry
            val after = viewModel.afterEntry
            if (before != null && after != null) {
                val wB = viewModel.closestWeight(before.date)
                val wA = viewModel.closestWeight(after.date)
                if (wB != null && wA != null) {
                    val diff = wA.unit.convert(wA.weight, wA.unit) - wB.unit.convert(wB.weight, wA.unit)
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = White)
                        ) {
                            Row(modifier = Modifier.padding(14.dp)) {
                                Text("Weight Change", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${if (diff > 0) "+" else ""}${fmt(diff)} ${wA.unit.label}",
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    color = if (diff <= 0) FLPrimary else OrangeTheme
                                )
                            }
                        }
                    }
                }

                val mB = viewModel.closestMeasurements(before.date)
                val mA = viewModel.closestMeasurements(after.date)
                if (mB != null && mA != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = White)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Measurement Changes", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                                Spacer(modifier = Modifier.height(8.dp))
                                mA.measurements.forEach { ma ->
                                    val mb = mB.measurement(ma.bodyPart) ?: return@forEach
                                    val a = MeasurementMath.displayValue(ma, unit)
                                    val b = MeasurementMath.displayValue(mb, unit)
                                    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(ma.bodyPart.displayName, fontSize = 13.sp, color = TextPrimaryColor)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text("${fmt(b)} → ${fmt(a)} ${unit.label}", fontSize = 13.sp, color = TextSecondaryColor)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        DeltaChip(a - b)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBeforePicker) {
        DateEntryPickerDialog(
            entries = viewModel.allEntries, selected = viewModel.beforeEntry,
            onSelect = { viewModel.selectBefore(it); showBeforePicker = false },
            onDismiss = { showBeforePicker = false }
        )
    }
    if (showAfterPicker) {
        DateEntryPickerDialog(
            entries = viewModel.allEntries, selected = viewModel.afterEntry,
            onSelect = { viewModel.selectAfter(it); showAfterPicker = false },
            onDismiss = { showAfterPicker = false }
        )
    }
}

@Composable
private fun ComparisonColumn(
    entry: ProgressPhotoEntry?,
    angle: PhotoAngle,
    weightLabel: String?,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onPick) {
            Text(if (entry != null) "Change" else "Select Date", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
        }
        if (entry != null) {
            Text(longDate.format(entry.date), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            ProgressPhotoThumb(photo = entry.photo(angle), angle = angle, height = 200.dp, showLabel = false, modifier = Modifier.fillMaxWidth())
            weightLabel?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Default.MonitorWeight, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(12.dp))
                    Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Gray.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text("Select a date", fontSize = 14.sp, color = TextSecondaryColor)
            }
        }
    }
}

/** iOS `DateEntryPicker`: list of entries with date, photo count and a check on the selected one. */
@Composable
fun DateEntryPickerDialog(
    entries: List<ProgressPhotoEntry>,
    selected: ProgressPhotoEntry?,
    onSelect: (ProgressPhotoEntry) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Date") },
        text = {
            if (entries.isEmpty()) {
                Text("No photo entries yet.", fontSize = 14.sp, color = TextSecondaryColor)
            } else {
                LazyColumn {
                    items(entries.size) { index ->
                        val entry = entries[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(entry) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(longDate.format(entry.date), fontSize = 15.sp, color = TextPrimaryColor)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("${entry.photos.size} photo${if (entry.photos.size == 1) "" else "s"}", fontSize = 13.sp, color = TextSecondaryColor)
                            if (selected?.id == entry.id) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = FLPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun fmt(v: Double): String = String.format(Locale.US, "%.1f", v)
