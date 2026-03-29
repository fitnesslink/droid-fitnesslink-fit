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
import androidx.compose.material.icons.filled.Person
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
import com.fitnesslink.fit.model.PhotoAngle
import com.fitnesslink.fit.model.ProgressPhotoEntry
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.viewmodel.PhotoComparisonViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun PhotoComparisonScreen(onBack: () -> Unit) {
    val viewModel: PhotoComparisonViewModel = viewModel()
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    var showBeforePicker by remember { mutableStateOf(false) }
    var showAfterPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        // Angle picker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
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
                        angle.displayName,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) FLPrimary else Color.Gray
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(top = 16.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Side by side
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Before
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TextButton(onClick = { showBeforePicker = true }) {
                            Text(
                                if (viewModel.beforeEntry != null) "Change" else "Select",
                                fontSize = 12.sp,
                                color = FLPrimary
                            )
                        }
                        viewModel.beforeEntry?.let { entry ->
                            Text(dateFormat.format(entry.date), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            ComparisonPhotoBox(entry, viewModel.selectedAngle)
                            viewModel.closestWeight(entry.date)?.let { w ->
                                Text(
                                    "${"%.1f".format(w.weight)} ${w.unit.label}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        } ?: run {
                            ComparisonPlaceholder()
                        }
                    }

                    // After
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TextButton(onClick = { showAfterPicker = true }) {
                            Text(
                                if (viewModel.afterEntry != null) "Change" else "Select",
                                fontSize = 12.sp,
                                color = FLPrimary
                            )
                        }
                        viewModel.afterEntry?.let { entry ->
                            Text(dateFormat.format(entry.date), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            ComparisonPhotoBox(entry, viewModel.selectedAngle)
                            viewModel.closestWeight(entry.date)?.let { w ->
                                Text(
                                    "${"%.1f".format(w.weight)} ${w.unit.label}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        } ?: run {
                            ComparisonPlaceholder()
                        }
                    }
                }
            }

            // Weight change
            val before = viewModel.beforeEntry
            val after = viewModel.afterEntry
            if (before != null && after != null) {
                val wB = viewModel.closestWeight(before.date)
                val wA = viewModel.closestWeight(after.date)
                if (wB != null && wA != null) {
                    val diff = wA.weight - wB.weight
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(modifier = Modifier.padding(14.dp)) {
                                Text("Weight Change", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${if (diff > 0) "+" else ""}${"%.1f".format(diff)} ${wA.unit.label}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diff <= 0) FLPrimary else OrangeTheme
                                )
                            }
                        }
                    }
                }

                // Measurement changes
                val mB = viewModel.closestMeasurements(before.date)
                val mA = viewModel.closestMeasurements(after.date)
                if (mB != null && mA != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Measurement Changes", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(8.dp))
                                mA.measurements.forEach { ma ->
                                    mB.measurement(ma.bodyPart)?.let { mb ->
                                        val mDiff = ma.value - mb.value
                                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                            Text(ma.bodyPart.displayName, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                "${"%.1f".format(mb.value)} \u2192 ${"%.1f".format(ma.value)}",
                                                fontSize = 13.sp,
                                                color = Color.Gray
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "${if (mDiff > 0) "+" else ""}${"%.1f".format(mDiff)}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (mDiff < 0) FLPrimary else if (mDiff > 0) OrangeTheme else Color.Gray
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

    // Date pickers
    if (showBeforePicker) {
        DateEntryPickerDialog(
            entries = viewModel.allEntries,
            selected = viewModel.beforeEntry,
            onSelect = { viewModel.selectBefore(it); showBeforePicker = false },
            onDismiss = { showBeforePicker = false }
        )
    }
    if (showAfterPicker) {
        DateEntryPickerDialog(
            entries = viewModel.allEntries,
            selected = viewModel.afterEntry,
            onSelect = { viewModel.selectAfter(it); showAfterPicker = false },
            onDismiss = { showAfterPicker = false }
        )
    }
}

@Composable
fun ComparisonPhotoBox(entry: ProgressPhotoEntry, angle: PhotoAngle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF0F0F0)),
        contentAlignment = Alignment.Center
    ) {
        val hasPhoto = entry.photo(angle) != null
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = if (hasPhoto) MediumGray else MediumGray.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun ComparisonPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF5F5F5)),
        contentAlignment = Alignment.Center
    ) {
        Text("Select a date", fontSize = 14.sp, color = Color.Gray)
    }
}

@Composable
fun DateEntryPickerDialog(
    entries: List<ProgressPhotoEntry>,
    selected: ProgressPhotoEntry?,
    onSelect: (ProgressPhotoEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Date") },
        text = {
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
                        Text(dateFormat.format(entry.date), fontSize = 15.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${entry.photos.size} photos", fontSize = 13.sp, color = Color.Gray)
                        if (selected?.id == entry.id) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = FLPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
