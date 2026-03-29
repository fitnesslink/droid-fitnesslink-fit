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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.ProgressPhotosViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ProgressPhotosScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val viewModel: ProgressPhotosViewModel = viewModel()
    var showCapture by remember { mutableStateOf(false) }

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
            // Action buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showCapture = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = FLPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Take Photos", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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

            // Section title
            item {
                Text(
                    text = "Progress Photos",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            // Entries
            if (viewModel.filteredEntries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MediumGray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No photos yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Take progress photos to track your transformation",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                items(viewModel.filteredEntries) { entry ->
                    PhotoEntryCard(
                        entry = entry,
                        closestWeight = viewModel.closestWeight(entry.date),
                        closestMeasurements = viewModel.closestMeasurements(entry.date),
                        onClick = { onNavigate("photoEntryDetail/${entry.id}") },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoEntryCard(
    entry: ProgressPhotoEntry,
    closestWeight: WeightEntry?,
    closestMeasurements: MeasurementEntry?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Date header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dateFormat.format(entry.date),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${entry.photos.size} angle${if (entry.photos.size == 1) "" else "s"}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MediumGray
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Photo thumbnails
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoAngle.entries.forEach { angle ->
                    val hasPhoto = entry.photo(angle) != null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (hasPhoto) Color(0xFFE8E8E8) else Color(0xFFF5F5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = angle.displayName,
                            modifier = Modifier.size(24.dp),
                            tint = if (hasPhoto) MediumGray else MediumGray.copy(alpha = 0.3f)
                        )
                        // Angle label
                        Text(
                            angle.displayName,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (hasPhoto) Color.DarkGray else Color.LightGray,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Linked data
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                closestWeight?.let { weight ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MonitorWeight, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${"%.1f".format(weight.weight)} ${weight.unit.label}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                closestMeasurements?.let { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${m.measurements.size} measurements", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            if (entry.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(entry.notes, fontSize = 13.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
