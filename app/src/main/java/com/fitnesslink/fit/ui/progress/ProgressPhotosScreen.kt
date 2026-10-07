package com.fitnesslink.fit.ui.progress

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.PhotoAngle
import com.fitnesslink.fit.model.ProgressPhotoEntry
import com.fitnesslink.fit.model.WeightEntry
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.ProgressPhotosViewModel
import java.util.Date
import java.util.Locale

/**
 * iOS `ProgressPhotosView`: "Take New Photos" (→ `photoCapture`) and
 * "Compare", search by date (picker, clearable), timeline of
 * `PhotoEntryCard`s with real thumbnails plus nearest weight / measurement
 * chips. Long-press a card to delete it (Android addition).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressPhotosScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val viewModel: ProgressPhotosViewModel = viewModel()
    var pendingDelete by remember { mutableStateOf<ProgressPhotoEntry?>(null) }

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Progress Photos", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onNavigate("photoCapture") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = FLPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Take New Photos", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onNavigate("photoComparison") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FLPrimary)
                    ) {
                        Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compare", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Search by date
            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(White)
                        .clickable { viewModel.showDatePicker = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(18.dp))
                    val sd = viewModel.searchDate
                    if (sd != null) {
                        Text(longDate.format(sd), fontSize = 14.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.Cancel, contentDescription = "Clear date", tint = TextSecondaryColor,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { viewModel.searchDate = null }
                        )
                    } else {
                        Text("Search by date", fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.weight(1f))
                    }
                }
            }

            if (viewModel.filteredEntries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, start = 20.dp, end = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp), tint = MediumGray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(if (viewModel.searchDate != null) "No photos on that day" else "No photos yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Take progress photos to track your transformation", fontSize = 14.sp, color = TextSecondaryColor)
                    }
                }
            } else {
                items(viewModel.filteredEntries, key = { it.id }) { entry ->
                    PhotoEntryCard(
                        entry = entry,
                        closestWeight = viewModel.closestWeight(entry.date),
                        closestMeasurements = viewModel.closestMeasurements(entry.date),
                        onClick = { onNavigate("photoEntryDetail/${entry.id}") },
                        onDelete = { pendingDelete = entry },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }

    if (viewModel.showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localDayToUtcMillis(viewModel.searchDate ?: Date()))
        DatePickerDialog(
            onDismissRequest = { viewModel.showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.searchDate = utcDayToLocalDate(it, Date()) }
                    viewModel.showDatePicker = false
                }) { Text("Search") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.searchDate = null; viewModel.showDatePicker = false }) { Text("Clear") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    pendingDelete?.let { entry ->
        ConfirmDeleteDialog(
            title = "Delete photos?",
            message = "Remove the ${entry.photos.size} photo${if (entry.photos.size == 1) "" else "s"} from ${longDate.format(entry.date)}? This can't be undone.",
            onConfirm = { viewModel.deleteEntry(entry.id) },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** iOS `PhotoEntryCard`: date + angle count, three thumbnails, linked weight / measurement chips, notes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoEntryCard(
    entry: ProgressPhotoEntry,
    closestWeight: WeightEntry?,
    closestMeasurements: MeasurementEntry?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { if (onDelete != null) menuOpen = true }),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(longDate.format(entry.date), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${entry.photos.size} angle${if (entry.photos.size == 1) "" else "s"}", fontSize = 12.sp, color = TextSecondaryColor)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MediumGray)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoAngle.entries.forEach { angle ->
                        ProgressPhotoThumb(photo = entry.photo(angle), angle = angle, height = 100.dp, modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    closestWeight?.let { weight ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MonitorWeight, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondaryColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${String.format(Locale.US, "%.1f", weight.weight)} ${weight.unit.label}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
                        }
                    }
                    closestMeasurements?.let { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondaryColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${m.measurements.size} measurements", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
                        }
                    }
                }

                if (entry.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(entry.notes, fontSize = 13.sp, color = TextSecondaryColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Delete photos", color = Color(0xFFE53935)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE53935)) },
                onClick = { menuOpen = false; onDelete?.invoke() }
            )
        }
    }
}
