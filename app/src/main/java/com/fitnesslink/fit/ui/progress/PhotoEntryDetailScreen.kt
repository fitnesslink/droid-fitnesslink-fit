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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.PhotoAngle
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun PhotoEntryDetailScreen(
    entryId: String,
    onBack: () -> Unit
) {
    val entry = remember { MockDataProvider.photoEntry(entryId) }
    val closestWeight = remember { entry?.let { MockDataProvider.closestWeight(it.date) } }
    val closestMeasurements = remember { entry?.let { MockDataProvider.closestMeasurements(it.date) } }
    var selectedAngle by remember { mutableStateOf(PhotoAngle.FRONT) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        if (entry != null) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date
                item {
                    Text(
                        dateFormat.format(entry.date),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // Angle selector
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(White)
                    ) {
                        PhotoAngle.entries.forEach { angle ->
                            val isSelected = selectedAngle == angle
                            val hasPhoto = entry.photo(angle) != null
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedAngle = angle }
                                    .background(if (isSelected) FLPrimary.copy(alpha = 0.1f) else Color.Transparent)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    angle.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) FLPrimary else if (hasPhoto) Color.Black else Color.LightGray
                                )
                            }
                        }
                    }
                }

                // Photo display
                item {
                    val photo = entry.photo(selectedAngle)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF0F0F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photo != null) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = selectedAngle.displayName,
                                modifier = Modifier.size(64.dp),
                                tint = MediumGray
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(32.dp), tint = MediumGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No ${selectedAngle.displayName.lowercase()} photo", fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                // Angle notes
                item {
                    val photo = entry.photo(selectedAngle)
                    photo?.notes?.takeIf { it.isNotEmpty() }?.let { notes ->
                        Text(
                            notes,
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                }

                // Entry notes
                if (entry.notes.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = White)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Notes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(entry.notes, fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                // Linked weight
                closestWeight?.let { weight ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = White)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row {
                                    Text("Body Weight", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(dateFormat.format(weight.date), fontSize = 11.sp, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MonitorWeight, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${"%.1f".format(weight.weight)} ${weight.unit.label}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Linked measurements
                closestMeasurements?.let { measurements ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = White)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row {
                                    Text("Measurements", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(dateFormat.format(measurements.date), fontSize = 11.sp, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                measurements.measurements.forEach { m ->
                                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(m.bodyPart.displayName, fontSize = 13.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text("${"%.1f".format(m.value)} ${m.unit.label}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
