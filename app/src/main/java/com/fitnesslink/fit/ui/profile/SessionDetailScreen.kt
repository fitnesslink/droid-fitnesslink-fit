package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.SessionExerciseRow
import com.fitnesslink.fit.model.SessionRow
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit
) {
    var session by remember { mutableStateOf<SessionRow?>(null) }
    var exercises by remember { mutableStateOf<List<SessionExerciseRow>>(emptyList()) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    LaunchedEffect(sessionId) {
        session = DatabaseManager.sessionById(sessionId)
        exercises = DatabaseManager.sessionExercises(sessionId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        val s = session
        if (s != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 10.dp, bottom = 30.dp)
            ) {
                // Header
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(s.workoutName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(14.dp))
                        Text(dateFormat.format(s.date), fontSize = 14.sp, color = TextSecondaryColor)
                        if (s.isCompleted) {
                            Text("Completed", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                        } else {
                            Text("Incomplete", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = OrangeTheme)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats grid
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Duration", formatDur(s.durationSeconds), Icons.Default.AccessTime, BlueTheme, Modifier.weight(1f))
                        StatCard("RPE", s.rpeValue?.let { String.format("%.1f", it) } ?: "--", Icons.Default.Speed, PurpleTheme, Modifier.weight(1f))
                        StatCard("Exercises", "${s.exerciseCount}", Icons.Default.DirectionsRun, FLPrimary, Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Weight", formatW(s.totalWeightLifted), Icons.Default.FitnessCenter, BlueTheme, Modifier.weight(1f))
                        val totalVol = exercises.sumOf { it.reps.toDouble() * it.weightLifted }
                        StatCard("Volume", formatW(totalVol), Icons.Default.BarChart, PurpleTheme, Modifier.weight(1f))
                        StatCard("Calories", "${s.totalCaloriesBurned.toInt()}", Icons.Default.LocalFireDepartment, OrangeTheme, Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Exercise breakdown
                Text("Exercises", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(modifier = Modifier.height(12.dp))

                val grouped = exercises.groupBy { it.taskName }
                val sortedExercises = grouped.entries.sortedBy { entry ->
                    entry.value.minOfOrNull { it.logDate } ?: java.util.Date()
                }

                sortedExercises.forEach { (name, sets) ->
                    val sortedSets = sets.sortedBy { it.setNumber }
                    val totalVol = sortedSets.sumOf { it.reps.toDouble() * it.weightLifted }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(White)
                            .padding(14.dp)
                    ) {
                        Row {
                            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (totalVol > 0) {
                                Text("${totalVol.toInt()} lbs vol", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PurpleTheme)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column { Text("${sortedSets.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold); Text("Sets", fontSize = 10.sp, color = TextSecondaryColor) }
                            val maxW = sortedSets.maxOfOrNull { it.weightLifted } ?: 0.0
                            if (maxW > 0) { Column { Text("${maxW.toInt()} lbs", fontSize = 14.sp, fontWeight = FontWeight.Bold); Text("Max Weight", fontSize = 10.sp, color = TextSecondaryColor) } }
                            Column { Text("${sortedSets.sumOf { it.reps }}", fontSize = 14.sp, fontWeight = FontWeight.Bold); Text("Total Reps", fontSize = 10.sp, color = TextSecondaryColor) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        sortedSets.forEach { set ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("Set ${set.setNumber}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor, modifier = Modifier.width(50.dp))
                                Text("${set.reps} reps", fontSize = 13.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                if (set.weightLifted > 0) {
                                    Text("${set.weightLifted.toInt()} lbs", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FLPrimary)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = TextSecondaryColor)
    }
}

private fun formatDur(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatW(value: Double): String =
    if (value >= 1000) String.format("%.1fk", value / 1000) else "${value.toInt()}"
