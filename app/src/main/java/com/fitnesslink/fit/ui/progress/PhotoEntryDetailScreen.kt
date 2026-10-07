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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.PhotoAngle
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.ProgressPhotosViewModel
import java.util.Locale

/**
 * iOS `PhotoEntryDetailView`: the date, each captured angle stacked
 * vertically as a large `FLImageView` with its per-angle notes, entry
 * notes, and linked "Body Weight" / "Measurements" cards. Reads the entry
 * from the store-backed ViewModel (not `MockDataProvider`). Delete with
 * confirmation is an Android addition.
 */
@Composable
fun PhotoEntryDetailScreen(
    entryId: String,
    onBack: () -> Unit
) {
    val viewModel: ProgressPhotosViewModel = viewModel()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(entryId) { viewModel.loadData() }

    val entry = viewModel.entry(entryId)
    val closestWeight = entry?.let { viewModel.closestWeight(it.date) }
    val closestMeasurements = entry?.let { viewModel.closestMeasurements(it.date) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            HeaderBackView(onBack = onBack)
            if (entry != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(White)
                        .clickable { confirmDelete = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete photos", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                }
            }
        }

        if (entry == null) {
            Text(
                if (viewModel.entries.isEmpty()) "Loading…" else "These photos are no longer available.",
                fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.padding(20.dp)
            )
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    longDate.format(entry.date), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }

            PhotoAngle.entries.forEach { angle ->
                val photo = entry.photo(angle) ?: return@forEach
                item(key = "angle-${angle.name}") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                            Text(angle.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                        }
                        ProgressPhotoThumb(
                            photo = photo, angle = angle, height = 350.dp, showLabel = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                        )
                        if (photo.notes.isNotBlank()) Text(photo.notes, fontSize = 14.sp, color = TextSecondaryColor)
                    }
                }
            }

            if (entry.notes.isNotEmpty()) {
                item {
                    DetailCard {
                        Text("Notes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(entry.notes, fontSize = 14.sp, color = TextSecondaryColor)
                    }
                }
            }

            closestWeight?.let { weight ->
                item {
                    DetailCard {
                        Row {
                            Text("Body Weight", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(longDate.format(weight.date), fontSize = 11.sp, color = TextSecondaryColor)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MonitorWeight, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${String.format(Locale.US, "%.1f", weight.weight)} ${weight.unit.label}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                        }
                    }
                }
            }

            closestMeasurements?.let { measurements ->
                item {
                    DetailCard {
                        Row {
                            Text("Measurements", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(longDate.format(measurements.date), fontSize = 11.sp, color = TextSecondaryColor)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        measurements.measurements.forEach { m ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(m.bodyPart.displayName, fontSize = 13.sp, color = TextSecondaryColor)
                                Spacer(modifier = Modifier.weight(1f))
                                Text("${formatMeasurement(m.value)} ${m.unit.label}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                            }
                        }
                    }
                }
            }
        }
    }

    val deleting = entry
    if (confirmDelete && deleting != null) {
        ConfirmDeleteDialog(
            title = "Delete photos?",
            message = "Remove the ${deleting.photos.size} photo${if (deleting.photos.size == 1) "" else "s"} from ${longDate.format(deleting.date)}? This can't be undone.",
            onConfirm = {
                viewModel.deleteEntry(deleting.id)
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}
