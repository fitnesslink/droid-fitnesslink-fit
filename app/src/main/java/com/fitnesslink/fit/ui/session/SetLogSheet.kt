package com.fitnesslink.fit.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.prescribedDistance
import com.fitnesslink.fit.session.MovementPerformanceLines
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.InteractiveSessionViewModel

private val durationPresets = listOf(30, 60, 120, 300, 600)

/**
 * Interactive Log Set / Save Set sheet (port of iOS `SetLogSheet`): weight in the display unit,
 * reps, a duration picker for timed tasks, distance for distance-capable movements (else "Add
 * distance"), Previous / Top set / This session lines. Prefill follows `logDefaults()`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetLogSheet(viewModel: InteractiveSessionViewModel, onDismiss: () -> Unit) {
    val task = viewModel.workoutTask
    var weightText by remember { mutableStateOf("") }
    var repsText by remember { mutableStateOf("") }
    var durationSeconds by remember { mutableIntStateOf(60) }
    var showCustomDuration by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf("") }
    var customSeconds by remember { mutableStateOf("") }
    var distanceText by remember { mutableStateOf("") }
    var showDistance by remember { mutableStateOf(false) }
    var distanceUnit by remember { mutableStateOf(DistanceUnit.current) }
    val previous = remember(task.id) { viewModel.previousWeight }
    val topSet = remember(task.id) { MovementPerformanceLines.topSet(task) }

    LaunchedEffect(task.id, viewModel.currentSetNumber, viewModel.currentRound) {
        val defaults = viewModel.logDefaults()
        weightText = defaults.weightLbs?.let { WeightUnit.displayNumber(fromLbs = it) } ?: ""
        repsText = if (task.usesDuration && defaults.reps <= 0) "" else defaults.reps.toString()
        val timed = viewModel.setTimerElapsedSeconds
        durationSeconds = if (task.usesDuration && timed > 0) timed else (defaults.durationSeconds ?: 60)
        val logged = viewModel.currentSetLoggedDistance
        val prescribed = task.prescribedDistance
        showDistance = logged != null || viewModel.currentSetIsDistanceCapable
        distanceUnit = logged?.unit ?: DistanceUnit.current
        distanceText = when {
            logged != null -> DistanceUnit.format(logged.value)
            prescribed != null -> DistanceUnit.format(prescribed.converted(distanceUnit).value)
            else -> ""
        }
    }

    val parsedWeight = weightText.trim().toDoubleOrNull()
    val parsedReps = repsText.trim().toIntOrNull()
    val parsedDistance = distanceText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val canSave = when {
        task.usesDuration -> durationSeconds > 0
        parsedReps != null && parsedReps > 0 -> true
        else -> showDistance && parsedDistance != null
    }
    val previouslyLogged = (viewModel.actualSetLogs[task.id] ?: emptyMap()).values.sortedBy { it.setNumber }

    fun save() {
        val weightLbs = parsedWeight?.let { WeightUnit.storageLbs(fromDisplay = it) }
        val distance = if (showDistance) parsedDistance else null
        if (task.usesDuration) {
            if (durationSeconds <= 0) return
            viewModel.logCurrentSet(weightLbs, parsedReps ?: 0, durationSeconds, distance, distanceUnit)
        } else {
            val reps = parsedReps ?: (if (distance != null) 0 else return)
            viewModel.logCurrentSet(weightLbs, reps, null, distance, distanceUnit)
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = White) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (task.groupId.isEmpty()) "Set ${viewModel.currentSetNumber} of ${maxOf(task.sets, 1)}"
                    else "Round ${viewModel.currentRound} of ${maxOf(task.rounds ?: task.totalRounds, 1)}",
                    fontSize = 14.sp, color = TextSecondaryColor
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FieldColumn("Weight (${WeightUnit.current.label})", Modifier.weight(1f)) {
                    NumberField(weightText, { weightText = it }, decimal = true)
                }
                FieldColumn("Reps", Modifier.weight(1f)) { NumberField(repsText, { repsText = it }) }
                if (task.usesDuration) {
                    FieldColumn("Duration", Modifier.weight(1f)) {
                        Text(InteractiveSessionViewModel.formatDuration(durationSeconds), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BackgroundColor).padding(vertical = 14.dp))
                    }
                }
            }
            if (task.usesDuration) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    durationPresets.forEach { seconds ->
                        val active = durationSeconds == seconds && !showCustomDuration
                        Chip(InteractiveSessionViewModel.formatDuration(seconds), active) { showCustomDuration = false; durationSeconds = seconds }
                    }
                    Chip("Custom", showCustomDuration) {
                        showCustomDuration = !showCustomDuration
                        if (showCustomDuration) { customMinutes = "${durationSeconds / 60}"; customSeconds = String.format("%02d", durationSeconds % 60) }
                    }
                }
                if (showCustomDuration) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(customMinutes, { customMinutes = it; durationSeconds = commitCustom(customMinutes, customSeconds) }, modifier = Modifier.width(72.dp), singleLine = true, label = { Text("min") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text(":", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(customSeconds, { customSeconds = it; durationSeconds = commitCustom(customMinutes, customSeconds) }, modifier = Modifier.width(72.dp), singleLine = true, label = { Text("ss") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            }
            if (showDistance) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FieldColumn("Distance (${distanceUnit.label})", Modifier.weight(1f)) { NumberField(distanceText, { distanceText = it }, decimal = true) }
                    FieldColumn("Unit", Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                            DistanceUnit.entries.forEach { unit -> Chip(unit.label, distanceUnit == unit) { distanceUnit = unit } }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showDistance = true }) {
                    Icon(Icons.Filled.AddCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.width(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add distance", color = FLPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            previous?.let { Text(it.lastTimeLine, color = FLPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            topSet?.let {
                Row { Text("Top set", color = TextSecondaryColor, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text(MovementPerformanceLines.topSetLine(it), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
            if (previouslyLogged.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BackgroundColor).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This session", color = TextSecondaryColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    previouslyLogged.forEach { config ->
                        Row { Text("Set ${config.setNumber}", color = TextSecondaryColor, fontSize = 13.sp, modifier = Modifier.width(48.dp)); Text(historyLine(config, task.usesDuration), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
            Text(
                "Save Set", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (canSave) FLPrimary else DisabledButton).clickable(enabled = canSave) { save() }.padding(vertical = 14.dp)
            )
            Text("Cancel", color = TextSecondaryColor, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 10.dp))
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

private fun commitCustom(minutes: String, seconds: String): Int {
    val m = maxOf(0, minutes.toIntOrNull() ?: 0)
    val s = (seconds.toIntOrNull() ?: 0).coerceIn(0, 59)
    return m * 60 + s
}

internal fun historyLine(config: TaskSetConfig, usesDuration: Boolean): String {
    val weightLbs = config.weightKg ?: 0.0
    val weightStr = WeightUnit.displayString(fromLbs = weightLbs)
    val weightSuffix = if (weightLbs > 0) " @ $weightStr" else ""
    val distanceSuffix = config.distanceValue?.let { " · ${it.text}" } ?: ""
    if (usesDuration) {
        val dur = InteractiveSessionViewModel.formatDuration(config.durationSeconds ?: 0)
        val repsSuffix = if (config.reps > 0) " · ${config.reps} reps" else ""
        return "$dur$repsSuffix$weightSuffix$distanceSuffix"
    }
    val distance = config.distanceValue
    if (config.reps <= 0 && distance != null) return "${distance.text}$weightSuffix"
    return "$weightStr × ${config.reps} reps$distanceSuffix"
}

@Composable
internal fun FieldColumn(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = TextSecondaryColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
internal fun NumberField(value: String, onChange: (String) -> Unit, decimal: Boolean = false, placeholder: String = "0") {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(placeholder, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text, color = if (active) White else com.fitnesslink.fit.ui.theme.TextPrimaryColor, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (active) FLPrimary else BackgroundColor).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/** "Mark as completed" toggle row shared by the playlist log sheet. */
@Composable
internal fun CompletedToggle(checked: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text("Mark as completed", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() }, colors = SwitchDefaults.colors(checkedTrackColor = FLPrimary))
    }
}
