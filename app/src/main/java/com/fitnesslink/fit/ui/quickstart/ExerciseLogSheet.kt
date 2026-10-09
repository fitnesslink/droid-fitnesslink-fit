package com.fitnesslink.fit.ui.quickstart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.session.ExerciseRPE
import com.fitnesslink.fit.session.MovementHistoryStore
import com.fitnesslink.fit.session.QuickStartPlan
import com.fitnesslink.fit.session.SessionExerciseNote
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.ImagePlaceholder
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * The universal Exercise Logging Page (Quick Start PRD §27–36): one
 * exercise's header + history, a Log New Set form, every set already logged
 * this session (editable), the Training Log and the Exercise RPE. Save Set
 * appends live; Done saves the log + RPE and closes; Cancel closes without
 * touching them (sets saved meanwhile stay). Port of iOS `ExerciseLogSheet`.
 *
 * Values arrive in storage units (weight in lbs) and are shown / entered in
 * the user's display units. Presentation only — the owner persists through
 * the callbacks — so pre-built workouts and programs can adopt it by passing
 * their [programmed] line.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseLogSheet(
    task: WorkoutTask,
    sessionId: String,
    loggedSets: List<TaskSetConfig>,
    programmed: String? = null,
    initialSetNumber: Int = 1,
    note: SessionExerciseNote? = null,
    unit: String = "Set",
    onSaveSet: (setNumber: Int, weightLbs: Double?, reps: Int, durationSeconds: Int?, distance: DistanceValue?) -> Unit,
    onDeleteSet: (setNumber: Int) -> Unit,
    onSaveNote: (rpe: Int?, trainingLog: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sets by remember { mutableStateOf(loggedSets.sortedBy { it.setNumber }) }
    var editingSetNumber by remember { mutableStateOf<Int?>(null) }
    var weightText by remember { mutableStateOf("") }
    var repsText by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }
    var distanceText by remember { mutableStateOf("") }
    var showTime by remember { mutableStateOf(task.usesDuration || loggedSets.any { (it.durationSeconds ?: 0) > 0 }) }
    var showDistance by remember { mutableStateOf(loggedSets.any { (it.distance ?: 0.0) > 0 }) }
    var distanceUnit by remember { mutableStateOf(loggedSets.lastOrNull { it.distanceUnit != null }?.let { DistanceUnit.fromWire(it.distanceUnit) } ?: DistanceUnit.current) }
    var rpe by remember { mutableStateOf(note?.rpe) }
    var trainingLog by remember { mutableStateOf(note?.trainingLog ?: "") }
    val previous = remember(task.id) { MovementHistoryStore.previousWeight(task.movementId, task.name, sessionId) }
    val topSet = remember(task.id) { MovementHistoryStore.topSet(task.movementId, task.name, sessionId) }
    val anatomyNames = remember(task.movementId) {
        task.movementId?.let { DatabaseManager.movement(it) }?.anatomyIds?.mapNotNull { ClassificationStore.anatomyName(it) } ?: emptyList()
    }

    fun beginNewSet() {
        editingSetNumber = null
        val last = sets.lastOrNull()
        if (last != null) {
            weightText = last.weightKg?.let { WeightUnit.displayNumber(fromLbs = it) } ?: ""
            repsText = if (last.reps > 0) "${last.reps}" else ""
            timeText = last.durationSeconds?.takeIf { it > 0 }?.let { QuickStartPlan.formatSeconds(it) } ?: ""
        } else {
            weightText = previous?.let { WeightUnit.displayNumber(fromLbs = it.weightLbs) } ?: ""
            repsText = ""
            timeText = ""
        }
        distanceText = ""
    }

    fun beginEditing(set: TaskSetConfig) {
        editingSetNumber = set.setNumber
        weightText = set.weightKg?.let { WeightUnit.displayNumber(fromLbs = it) } ?: ""
        repsText = if (set.reps > 0) "${set.reps}" else ""
        val seconds = set.durationSeconds
        if (seconds != null && seconds > 0) { showTime = true; timeText = QuickStartPlan.formatSeconds(seconds) } else timeText = ""
        val distance = set.distanceValue
        if (distance != null) { showDistance = true; distanceUnit = distance.unit; distanceText = QuickStartPlan.trimmedNumber(distance.value) } else distanceText = ""
    }

    var configured by remember { mutableStateOf(false) }
    if (!configured) {
        configured = true
        val existing = sets.firstOrNull { it.setNumber == initialSetNumber }
        if (existing != null) beginEditing(existing) else beginNewSet()
    }

    val parsedWeightLbs = weightText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { WeightUnit.storageLbs(fromDisplay = it) }
    val parsedReps = repsText.trim().toIntOrNull() ?: 0
    val parsedSeconds = if (showTime) QuickStartPlan.parseSeconds(timeText).takeIf { it > 0 } else null
    val parsedDistance = if (showDistance) distanceText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { DistanceValue(it, distanceUnit) } else null
    // A set needs at least one performance value (PRD §31: never force a metric that doesn't apply).
    val canSave = parsedReps > 0 || parsedSeconds != null || parsedDistance != null

    fun saveSet() {
        if (!canSave) return
        val number = editingSetNumber ?: (sets.size + 1)
        onSaveSet(number, parsedWeightLbs, parsedReps, parsedSeconds, parsedDistance)
        val base = sets.firstOrNull { it.setNumber == number } ?: TaskSetConfig(setNumber = number, reps = parsedReps)
        val config = base.copy(reps = parsedReps, weightKg = parsedWeightLbs, durationSeconds = parsedSeconds, distance = parsedDistance?.value, distanceUnit = parsedDistance?.unit?.rawValue)
        sets = (sets.filter { it.setNumber != number } + config).sortedBy { it.setNumber }
        beginNewSet()
    }

    fun delete(set: TaskSetConfig) {
        onDeleteSet(set.setNumber)
        sets = sets.filter { it.setNumber != set.setNumber }.mapIndexed { i, s -> s.copy(setNumber = i + 1) }
        if (editingSetNumber == set.setNumber) beginNewSet()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BackgroundColor) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Log $unit", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            // Header (PRD §28)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                val thumb = (task.movementThumbnailId ?: task.movementImageId)?.let { MediaRef.MovementThumbnail(it) }
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(ImagePlaceholder)) { if (thumb != null) FLImageView(ref = thumb, modifier = Modifier.size(64.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(task.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, maxLines = 2)
                    programmed?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PurpleTheme) }
                    if (anatomyNames.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            anatomyNames.take(3).forEach { name ->
                                Text(name, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(White).padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
            }

            // History (PRD §29)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HistoryTile("Last Time", Icons.Filled.History, FLPrimary, previous?.let { QuickStartPlan.setLine(TaskSetConfig(setNumber = 0, reps = it.reps, weightKg = it.weightLbs)) } ?: "—", null, Modifier.weight(1f))
                HistoryTile("Top Set", Icons.Filled.EmojiEvents, PurpleTheme, topSet?.let { QuickStartPlan.setLine(TaskSetConfig(setNumber = 0, reps = it.reps, weightKg = it.maxWeight)) } ?: "—", null, Modifier.weight(1f))
                HistoryTile("Suggested", Icons.Filled.WorkspacePremium, OrangeTheme, "Soon", "Coming soon", Modifier.weight(1f))
            }

            // Log New Set (PRD §22, §30–31)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(editingSetNumber?.let { "Edit $unit $it" } ?: "Log New $unit", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                    if (editingSetNumber != null) Text("Cancel edit", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor, modifier = Modifier.clickable { beginNewSet() })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LogField("Weight (${WeightUnit.current.label})", weightText, { weightText = it }, KeyboardType.Decimal, Modifier.weight(1f))
                    LogField("Reps", repsText, { repsText = it }, KeyboardType.Number, Modifier.weight(1f))
                    if (showTime) LogField("Time (m:ss)", timeText, { timeText = it }, KeyboardType.Text, Modifier.weight(1f))
                    if (showDistance) LogField("Dist (${distanceUnit.label})", distanceText, { distanceText = it }, KeyboardType.Decimal, Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!showTime) MetricToggle("Add time", Icons.Filled.Timer) { showTime = true }
                    if (!showDistance) MetricToggle("Add distance", Icons.Filled.Route) { showDistance = true }
                    if (showDistance) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DistanceUnit.entries.forEach { u ->
                            Text(u.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (distanceUnit == u) White else TextPrimaryColor,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (distanceUnit == u) FLPrimary else BackgroundColor).clickable { distanceUnit = u }.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
                Text(
                    editingSetNumber?.let { "Update $unit $it" } ?: "Save $unit", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (canSave) PurpleTheme else DisabledButton).clickable(enabled = canSave) { saveSet() }.padding(vertical = 14.dp)
                )
            }

            // Logged sets (PRD §32–33)
            if (sets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Logged ${unit}s", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White)) {
                        sets.forEachIndexed { index, set ->
                            var menu by remember(set.setNumber) { mutableStateOf(false) }
                            val editing = editingSetNumber == set.setNumber
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().clickable { beginEditing(set) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(if (editing) PurpleTheme else BackgroundColor), contentAlignment = Alignment.Center) {
                                    Text("${set.setNumber}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (editing) White else TextPrimaryColor)
                                }
                                Text(QuickStartPlan.setLine(set), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                                Box {
                                    Icon(Icons.Filled.MoreHoriz, contentDescription = "$unit ${set.setNumber} options", tint = TextSecondaryColor, modifier = Modifier.size(24.dp).clickable { menu = true })
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; beginEditing(set) })
                                        DropdownMenuItem(text = { Text("Delete", color = Color(0xFFE53935)) }, onClick = { menu = false; delete(set) })
                                    }
                                }
                            }
                            if (index < sets.size - 1) HorizontalDivider(Modifier.padding(start = 52.dp), color = BackgroundColor)
                        }
                    }
                }
            }

            // Training Log (PRD §34)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                    Text("Training Log", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    Text("(optional)", fontSize = 13.sp, color = TextSecondaryColor)
                }
                OutlinedTextField(value = trainingLog, onValueChange = { trainingLog = it }, minLines = 2, maxLines = 5,
                    placeholder = { Text("How did this exercise feel? What did you notice?") }, modifier = Modifier.fillMaxWidth())
            }

            // Exercise RPE (PRD §36)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.BarChart, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                    Text("Exercise RPE", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    Text("(Rate of Perceived Exertion)", fontSize = 11.sp, color = TextSecondaryColor, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExerciseRPE.range.forEach { value ->
                        val on = rpe == value
                        Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(if (on) PurpleTheme else BackgroundColor).clickable { rpe = if (on) null else value }, contentAlignment = Alignment.Center) {
                            Text("$value", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) White else TextPrimaryColor)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("• ${ExerciseRPE.LOW_LABEL}", fontSize = 11.sp, color = TextSecondaryColor, modifier = Modifier.weight(1f))
                    Text("${ExerciseRPE.HIGH_LABEL} •", fontSize = 11.sp, color = TextSecondaryColor)
                }
            }

            Text("Done", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FLPrimary).clickable { onSaveNote(rpe, trainingLog); onDismiss() }.padding(vertical = 15.dp))
            Text("Cancel", color = TextSecondaryColor, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 8.dp))
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HistoryTile(title: String, icon: ImageVector, color: Color, value: String, caption: String?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(White).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor, maxLines = 1)
        }
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, maxLines = 1)
        caption?.let { Text(it, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PurpleTheme, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(PurpleTheme.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)) }
    }
}

@Composable
private fun LogField(label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor, maxLines = 1)
        OutlinedTextField(value = value, onValueChange = onChange, singleLine = true, placeholder = { Text("0", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MetricToggle(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(FLPrimary.copy(alpha = 0.1f)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
    }
}
