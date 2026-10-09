package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.SessionExerciseRow
import com.fitnesslink.fit.model.SessionRow
import com.fitnesslink.fit.model.SessionRpeRule
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.fullSessionRow
import com.fitnesslink.fit.persistence.sessionExerciseNotes
import com.fitnesslink.fit.persistence.sessionQuickStartMeta
import com.fitnesslink.fit.session.SessionExerciseNote
import com.fitnesslink.fit.persistence.sessionSetDistances
import com.fitnesslink.fit.persistence.sessionSetDurations
import com.fitnesslink.fit.persistence.sessionSetKey
import com.fitnesslink.fit.session.SessionRecorder
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.profile.charts.ReportMiniStat
import com.fitnesslink.fit.ui.profile.charts.ReportStatCard
import com.fitnesslink.fit.ui.profile.charts.formatReportDate
import com.fitnesslink.fit.ui.profile.charts.formatReportDuration
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Port of iOS `SessionDetailView`: one saved session. Stats grid, exercise
 * breakdown grouped by exercise with per-set reps / weight / volume in the
 * display unit, timed-set windows (FTNSSLNK-326) and logged distances
 * (FTNSSLNK-409), a toolbar delete with confirmation, and the RPE edit
 * sheet (FTNSSLNK-411: one change within 24 h of completion).
 */
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit
) {
    var session by remember { mutableStateOf<SessionRow?>(null) }
    var exercises by remember { mutableStateOf<List<SessionExerciseRow>>(emptyList()) }
    var setDurations by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var setDistances by remember { mutableStateOf<Map<String, DistanceValue>>(emptyMap()) }
    var showRpeEditor by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // Quick Start PRD §42: the training focus the session was started with, and the per-exercise RPE / Training Log keyed by exercise name.
    var trainingFocus by remember { mutableStateOf<String?>(null) }
    var notesByTaskName by remember { mutableStateOf<Map<String, SessionExerciseNote>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    fun reload() {
        session = DatabaseManager.fullSessionRow(sessionId)
        exercises = DatabaseManager.sessionExercises(sessionId)
        setDurations = DatabaseManager.sessionSetDurations(sessionId)
        setDistances = DatabaseManager.sessionSetDistances(sessionId)
        trainingFocus = DatabaseManager.sessionQuickStartMeta(sessionId)?.trainingFocus
        notesByTaskName = DatabaseManager.sessionExerciseNotes(sessionId).associateBy { it.taskName }
    }

    LaunchedEffect(sessionId) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            HeaderBackView(onBack = onBack)
            if (session != null) {
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !busy,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete session", tint = TextSecondaryColor)
                }
            }
        }

        val s = session
        if (s == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FLPrimary)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.workoutName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(13.dp))
                    Text(formatReportDate(s.displayDate), fontSize = 14.sp, color = TextSecondaryColor)
                    if (s.isCompleted) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(12.dp))
                        Text(s.statusLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                    } else {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = OrangeTheme, modifier = Modifier.size(12.dp))
                        Text("Incomplete", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = OrangeTheme)
                    }
                }
                trainingFocus?.let { focus ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(PurpleTheme.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = PurpleTheme, modifier = Modifier.size(12.dp))
                        Text("Quick Start · $focus", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PurpleTheme)
                    }
                }
            }

            StatsGrid(
                session = s,
                exercises = exercises,
                onEditRpe = { showRpeEditor = true }
            )

            ExerciseBreakdown(exercises, setDurations, setDistances, notesByTaskName)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this session?") },
            text = { Text("This removes the workout and its logged sets from your history and reports. This can't be undone.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showDeleteConfirm = false
                        busy = true
                        scope.launch {
                            try {
                                SessionRecorder.delete(sessionId)
                            } catch (_: Exception) {
                                // Local row is gone either way; the queue retries the server call.
                            } finally {
                                busy = false
                            }
                            onBack()
                        }
                    }
                ) { Text("Delete Session", color = Color(0xFFE53935), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextSecondaryColor) }
            }
        )
    }

    val editing = session
    if (showRpeEditor && editing != null) {
        SessionRpeEditSheet(
            session = editing,
            onDismiss = { showRpeEditor = false },
            onSave = { value ->
                showRpeEditor = false
                busy = true
                scope.launch {
                    try {
                        SessionRecorder.updateRpe(sessionId, value)
                    } catch (_: Exception) {
                        // Engine owns the error path; the row reload below shows whatever landed.
                    } finally {
                        busy = false
                    }
                    reload()
                }
            }
        )
    }
}

// MARK: - Stats grid

@Composable
private fun StatsGrid(session: SessionRow, exercises: List<SessionExerciseRow>, onEditRpe: () -> Unit) {
    val totalVolume = exercises.sumOf { it.reps.toDouble() * it.weightLifted }
    // FTNSSLNK-319 — rows written before the totals writer hold 0s; derive from the sets.
    val totalSets = if (session.totalSets > 0) session.totalSets else exercises.size
    val totalReps = if (session.totalReps > 0) session.totalReps else exercises.sumOf { it.reps }
    val canEdit = session.canEditRpe()

    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportStatCard(Icons.Default.AccessTime, formatReportDuration(session.durationSeconds), "Duration", BlueTheme, Modifier.weight(1f))
            ReportStatCard(
                Icons.Default.Speed, session.rpeText ?: "--", "RPE", PurpleTheme, Modifier.weight(1f),
                annotation = if (canEdit) "Tap to edit" else null,
                onClick = if (canEdit) onEditRpe else null
            )
            ReportStatCard(Icons.Default.DirectionsRun, "${session.exerciseCount}", "Exercises", FLPrimary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportStatCard(Icons.Default.Layers, "$totalSets", "Sets Completed", OrangeTheme, Modifier.weight(1f))
            ReportStatCard(Icons.Default.Repeat, "$totalReps", "Reps Completed", BlueTheme, Modifier.weight(1f))
            ReportStatCard(Icons.Default.FitnessCenter, compactWeight(session.totalWeightLifted), "Weight Lifted", BlueTheme, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportStatCard(Icons.Default.BarChart, compactWeight(totalVolume), "Volume", PurpleTheme, Modifier.weight(1f))
            ReportStatCard(Icons.Default.LocalFireDepartment, "${session.totalCaloriesBurned.toInt()}", "Calories", OrangeTheme, Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** Stored lbs → display unit, k-formatted. */
private fun compactWeight(lbs: Double): String {
    val value = WeightUnit.displayValue(lbs)
    return if (value >= 1000) String.format(Locale.US, "%.1fk", value / 1000) else "${value.roundToInt()}"
}

// MARK: - Exercise breakdown

private data class GroupedExercise(val name: String, val sets: List<SessionExerciseRow>) {
    val totalReps: Int get() = sets.sumOf { it.reps }
    val maxWeight: Double get() = sets.maxOfOrNull { it.weightLifted } ?: 0.0
    val totalVolume: Double get() = sets.sumOf { it.reps.toDouble() * it.weightLifted }
}

@Composable
private fun ExerciseBreakdown(
    exercises: List<SessionExerciseRow>,
    setDurations: Map<String, Int>,
    setDistances: Map<String, DistanceValue>,
    notesByTaskName: Map<String, SessionExerciseNote> = emptyMap()
) {
    val grouped = exercises.groupBy { it.taskName }
        .map { (name, sets) -> GroupedExercise(name, sets.sortedBy { it.setNumber }) }
        .sortedBy { it.sets.firstOrNull()?.logDate ?: Date(0) }

    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Exercises", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (grouped.isEmpty()) {
            Text("No exercise data recorded", fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.padding(top = 8.dp))
        } else {
            grouped.forEach { exercise -> ExerciseCard(exercise, setDurations, setDistances, notesByTaskName[exercise.name]) }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: GroupedExercise,
    setDurations: Map<String, Int>,
    setDistances: Map<String, DistanceValue>,
    note: SessionExerciseNote? = null
) {
    val unit = WeightUnit.current.label
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(exercise.name.ifBlank { "Exercise" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (exercise.totalVolume > 0) {
                Text(
                    "${WeightUnit.displayWholeNumber(exercise.totalVolume)} $unit vol",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PurpleTheme
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReportMiniStat("Sets", "${exercise.sets.size}")
            if (exercise.maxWeight > 0) ReportMiniStat("Max Weight", WeightUnit.displayString(exercise.maxWeight))
            ReportMiniStat("Total Reps", "${exercise.totalReps}")
            note?.rpe?.let { ReportMiniStat("RPE", "$it") }
        }
        // Training Log (Quick Start PRD §34): the user's own words.
        note?.trainingLog?.takeIf { it.isNotBlank() }?.let { log ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Description, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(12.dp).padding(top = 1.dp))
                Text(log, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = TextSecondaryColor)
            }
        }
        HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
        exercise.sets.forEach { set ->
            val key = sessionSetKey(set.taskName, set.setNumber)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text("Set ${set.setNumber}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor, modifier = Modifier.width(50.dp))
                Text(setMetricText(set, setDurations[key], setDistances[key]), fontSize = 13.sp)
                Spacer(modifier = Modifier.weight(1f))
                if (set.weightLifted > 0) {
                    Text(WeightUnit.displayString(set.weightLifted), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(" · ", fontSize = 13.sp, color = TextSecondaryColor)
                    Text(
                        "${WeightUnit.displayWholeNumber(set.reps * set.weightLifted)} $unit vol",
                        fontSize = 12.sp, color = TextSecondaryColor
                    )
                }
            }
        }
    }
}

/**
 * Per-set metric line. Timed sets show the window alongside the reps
 * completed inside it ("30s · 12 reps"); a timed set with no reps shows
 * just the window; a logged distance is appended in the user's unit;
 * rep sets are unchanged.
 */
private fun setMetricText(set: SessionExerciseRow, durationSeconds: Int?, distance: DistanceValue?): String {
    val parts = mutableListOf<String>()
    if (durationSeconds != null && durationSeconds > 0) {
        parts += WorkoutTask.humanizeDuration(durationSeconds)
        if (set.reps > 0) parts += "${set.reps} reps"
    } else if (distance == null || set.reps > 0) {
        parts += "${set.reps} reps"
    }
    if (distance != null) parts += DistanceUnit.displayInCurrent(distance.value, distance.unit)
    return parts.joinToString(" · ")
}

// MARK: - RPE edit (FTNSSLNK-411)

/**
 * One-time RPE change for a saved session, offered within 24 h of
 * completion. Dark surface, 0–10 slider, Cancel / Save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionRpeEditSheet(
    session: SessionRow,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by remember { mutableIntStateOf(session.rpeValue?.roundToInt()?.let { SessionRpeRule.clamp(it) } ?: 5) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(session.workoutName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
            Text(SessionRpeRule.PROMPT, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
            Text("$value", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
            Text(SessionRpeRule.label(value), fontSize = 15.sp, color = Color.White)
            Slider(
                value = value.toFloat(),
                onValueChange = { value = it.roundToInt() },
                valueRange = SessionRpeRule.range.first.toFloat()..SessionRpeRule.range.last.toFloat(),
                steps = SessionRpeRule.range.last - SessionRpeRule.range.first - 1,
                colors = SliderDefaults.colors(
                    thumbColor = FLPrimary,
                    activeTrackColor = FLPrimary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(SessionRpeRule.MIN_ANCHOR, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.weight(1f))
                Text(SessionRpeRule.MAX_ANCHOR, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.End)
            }
            Text(
                "You can change this rating once, within 24 hours of finishing.",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White)
                ) { Text("Cancel", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = { onSave(value) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FLPrimary, contentColor = Color.White)
                ) { Text("Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
