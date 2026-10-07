package com.fitnesslink.fit.ui.workout

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.SetPreset
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.WorkoutEditorViewModel
import com.fitnesslink.fit.workouts.WorkoutRowOps

// MARK: - Shared editor controls

/** Selectable pill chip used across the editor (time / level / location / presets). */
@Composable
fun EditorChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (selected) White else TextPrimaryColor,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) FLPrimary else BackgroundColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/** Minus / value / plus stepper. */
@Composable
fun EditorStepper(value: Int, onChange: (Int) -> Unit, min: Int, max: Int, step: Int = 1, suffix: String = "", valueWidth: Int = 36) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Filled.RemoveCircle, contentDescription = "Decrease", tint = MediumGray,
            modifier = Modifier.size(24.dp).clickable { onChange(maxOf(min, value - step)) }
        )
        Text(text = "$value$suffix", fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(valueWidth.dp))
        Icon(
            Icons.Filled.AddCircle, contentDescription = "Increase", tint = FLPrimary,
            modifier = Modifier.size(24.dp).clickable { onChange(minOf(max, value + step)) }
        )
    }
}

@Composable
private fun StepperWithLabel(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, color = TextSecondaryColor)
        content()
    }
}

/** Compact chip label: "15s" under a minute, "1m" on whole minutes, "m:ss" otherwise. */
private fun restLabel(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0 -> "${seconds / 60}m"
    else -> WorkoutRowOps.formatDuration(seconds)
}

// MARK: - Set editor (iOS SetEditorView)

/**
 * Inline set editor under an expanded movement row: Reps / Time toggle,
 * steppers, presets, per-set (or per-round) editing, rest between sets and
 * the coaching cue. [isGrouped] hides the sets-driven controls for superset /
 * circuit members; interval members only get a rep target (EMOM / AMRAP).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetEditorView(task: WorkoutTask, viewModel: WorkoutEditorViewModel, isGrouped: Boolean = false) {
    val taskId = task.id
    val durationPresets = listOf(30, 60, 120, 300, 600)
    val restPresets = listOf(15, 30, 45, 60, 120, 180, 300)
    var showCustomDuration by remember(taskId) { mutableStateOf(false) }
    var customMinutes by remember(taskId) { mutableStateOf("") }
    var customSeconds by remember(taskId) { mutableStateOf("") }
    var showCustomRest by remember(taskId) { mutableStateOf(task.restSeconds > 0 && !restPresets.contains(task.restSeconds)) }
    var customRest by remember(taskId) { mutableStateOf(if (task.restSeconds > 0) "${task.restSeconds}" else "") }
    val weightLbs = task.weightKg?.toDouble()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        if (task.isInterval) {
            if (IntervalMath.usesRepTargets(task.intervalMode)) {
                StepperWithLabel("Reps") {
                    EditorStepper(task.reps, { viewModel.updateTaskSets(taskId, task.sets, it, weightLbs) }, min = 1, max = 100)
                }
            } else {
                Text("Timed work — Work / Rest are set on the interval.", fontSize = 12.sp, color = TextSecondaryColor)
            }
        } else {
            // Reps / Time toggle
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BackgroundColor)) {
                ModePill("Reps", !task.usesDuration, Modifier.weight(1f)) { viewModel.setUsesDuration(taskId, false); showCustomDuration = false }
                ModePill("Time", task.usesDuration, Modifier.weight(1f)) { viewModel.setUsesDuration(taskId, true) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                if (!isGrouped) {
                    StepperWithLabel("Sets") {
                        EditorStepper(task.sets, { newSets ->
                            if (task.usesDuration) viewModel.updateTaskDuration(taskId, newSets, task.durationSeconds ?: 60, weightLbs)
                            else viewModel.updateTaskSets(taskId, newSets, task.reps, weightLbs)
                        }, min = 1, max = 10)
                    }
                }
                if (!task.usesDuration) {
                    StepperWithLabel("Reps") {
                        EditorStepper(task.reps, { viewModel.updateTaskSets(taskId, task.sets, it, weightLbs) }, min = 1, max = 100)
                    }
                    WeightField(
                        lbs = weightLbs,
                        onChangeLbs = { viewModel.updateTaskSets(taskId, task.sets, task.reps, it) }
                    )
                }
            }

            if (task.usesDuration) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Duration", fontSize = 11.sp, color = TextSecondaryColor)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        durationPresets.forEach { secs ->
                            EditorChip(WorkoutRowOps.formatDuration(secs), task.durationSeconds == secs && !showCustomDuration, onClick = {
                                showCustomDuration = false
                                viewModel.updateTaskDuration(taskId, maxOf(task.sets, 1), secs, weightLbs)
                            })
                        }
                        EditorChip("Custom", showCustomDuration, onClick = {
                            showCustomDuration = !showCustomDuration
                            if (showCustomDuration) {
                                val s = task.durationSeconds ?: 60
                                customMinutes = "${s / 60}"; customSeconds = String.format(java.util.Locale.US, "%02d", s % 60)
                            }
                        })
                    }
                    if (showCustomDuration) {
                        fun commit() {
                            val m = customMinutes.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            val s = customSeconds.toIntOrNull()?.coerceIn(0, 59) ?: 0
                            val total = m * 60 + s
                            if (total > 0) viewModel.updateTaskDuration(taskId, maxOf(task.sets, 1), total, weightLbs)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SmallNumberField(customMinutes, "min", 56) { customMinutes = it.filter(Char::isDigit); commit() }
                            Text(":", fontWeight = FontWeight.SemiBold)
                            SmallNumberField(customSeconds, "ss", 56) { customSeconds = it.filter(Char::isDigit); commit() }
                        }
                    }
                }
            }

            if (!task.usesDuration && !isGrouped) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SetPreset.presets.forEach { preset ->
                        EditorChip(preset.label, task.sets == preset.sets && task.reps == preset.reps, onClick = { viewModel.applyPreset(taskId, preset) })
                    }
                }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isGrouped) "Different weight each round" else "Edit each set individually", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = !task.sameAcrossSets,
                    onCheckedChange = { if (isGrouped) viewModel.togglePerRound(taskId) else viewModel.toggleSameAcrossSets(taskId) },
                    colors = SwitchDefaults.colors(checkedTrackColor = FLPrimary)
                )
            }
            if (!task.sameAcrossSets) PerSetRows(task, viewModel, perRowLabel = if (isGrouped) "Round" else "Set")

            if (!isGrouped) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Rest Between Sets", fontSize = 11.sp, color = TextSecondaryColor)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EditorChip("None", task.restSeconds == 0 && !showCustomRest, onClick = { showCustomRest = false; viewModel.updateExerciseRest(taskId, 0) })
                        restPresets.forEach { secs ->
                            EditorChip(restLabel(secs), task.restSeconds == secs && !showCustomRest, onClick = { showCustomRest = false; viewModel.updateExerciseRest(taskId, secs) })
                        }
                        EditorChip("Custom", showCustomRest, onClick = {
                            showCustomRest = !showCustomRest
                            if (showCustomRest) customRest = if (task.restSeconds > 0) "${task.restSeconds}" else ""
                        })
                    }
                    if (showCustomRest) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SmallNumberField(customRest, "60", 64) { raw ->
                                customRest = raw.filter(Char::isDigit)
                                customRest.toIntOrNull()?.takeIf { it > 0 }?.let { viewModel.updateExerciseRest(taskId, it) }
                            }
                            Text("seconds", fontSize = 11.sp, color = TextSecondaryColor)
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        InstructionsField(value = task.instructions ?: "", onValueChange = { viewModel.updateInstructions(taskId, it) })
    }
}

@Composable
private fun ModePill(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        color = if (active) White else TextPrimaryColor,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) FLPrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

/** Coaching cue field shared by the set editor and the interval sheet. */
@Composable
fun InstructionsField(value: String, onValueChange: (String) -> Unit, label: String = "Instructions", placeholder: String = "Movement cues") {
    var text by remember(value) { mutableStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, color = TextSecondaryColor)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onValueChange(it) },
            placeholder = { Text(placeholder, fontSize = 14.sp) },
            textStyle = TextStyle(fontSize = 14.sp),
            minLines = 1, maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SmallNumberField(value: String, placeholder: String, width: Int, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 13.sp, textAlign = TextAlign.Center) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(width.dp)
    )
}

/**
 * Task-level load. Displays / parses in the preferred unit; storage stays
 * whole lbs (`weightKg` is lbs despite the name). Empty / 0 clears to bodyweight.
 */
@Composable
private fun WeightField(lbs: Double?, onChangeLbs: (Double?) -> Unit) {
    var text by remember(lbs) { mutableStateOf(lbs?.takeIf { it > 0 }?.let { WeightUnit.displayNumber(fromLbs = it) } ?: "") }
    StepperWithLabel("Weight") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    text = raw
                    val display = raw.toDoubleOrNull()
                    val stored = display?.let { WeightUnit.storageLbs(fromDisplay = it) }?.let { Math.round(it).toDouble() }
                    onChangeLbs(stored?.takeIf { it > 0 })
                },
                placeholder = { Text("0", textAlign = TextAlign.Center) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(72.dp)
            )
            Text(WeightUnit.current.label, fontSize = 11.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun PerSetRows(task: WorkoutTask, viewModel: WorkoutEditorViewModel, perRowLabel: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        task.setConfigs.sortedBy { it.setNumber }.forEach { config ->
            key(config.id) { PerSetRow(task, config, viewModel, perRowLabel) }
        }
    }
}

@Composable
private fun PerSetRow(task: WorkoutTask, config: TaskSetConfig, viewModel: WorkoutEditorViewModel, perRowLabel: String) {
    var reps by remember(config.id) { mutableStateOf(if (config.reps > 0) "${config.reps}" else "") }
    var duration by remember(config.id) { mutableStateOf(WorkoutRowOps.formatDuration(config.durationSeconds ?: 0)) }
    var weight by remember(config.id) { mutableStateOf(config.weightKg?.takeIf { it > 0 }?.let { WeightUnit.displayNumber(fromLbs = it) } ?: "") }
    LaunchedEffect(config.reps) { if ((reps.toIntOrNull() ?: 0) != config.reps) reps = if (config.reps > 0) "${config.reps}" else "" }

    fun lbs(): Double? = weight.toDoubleOrNull()?.let { WeightUnit.storageLbs(fromDisplay = it) }?.takeIf { it > 0 }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$perRowLabel ${config.setNumber}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor, modifier = Modifier.width(64.dp))
        if (task.usesDuration) {
            OutlinedTextField(
                value = duration,
                onValueChange = { raw ->
                    duration = raw
                    val parts = raw.split(":")
                    val m = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val s = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    viewModel.updateSetConfigDuration(task.id, config.setNumber, maxOf(0, m * 60 + s), config.weightKg)
                },
                placeholder = { Text("0:30") }, singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center), modifier = Modifier.width(70.dp)
            )
        }
        OutlinedTextField(
            value = reps,
            onValueChange = { raw ->
                reps = raw.filter(Char::isDigit)
                viewModel.updateSetConfig(task.id, config.setNumber, reps.toIntOrNull() ?: 0, config.weightKg, config.isDropSet)
            },
            placeholder = { Text("Reps") }, singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(60.dp)
        )
        Text("reps", fontSize = 11.sp, color = TextSecondaryColor)
        OutlinedTextField(
            value = weight,
            onValueChange = { raw ->
                weight = raw
                val l = lbs()
                if (task.usesDuration) viewModel.updateSetConfigDuration(task.id, config.setNumber, config.durationSeconds ?: 0, l)
                else viewModel.updateSetConfig(task.id, config.setNumber, config.reps, l, config.isDropSet)
            },
            placeholder = { Text(WeightUnit.current.label) }, singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(72.dp)
        )
        Text(WeightUnit.current.label, fontSize = 11.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.weight(1f))
    }
}
