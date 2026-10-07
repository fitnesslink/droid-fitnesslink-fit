package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMetrics
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.WorkoutEditorViewModel
import java.util.Locale

/**
 * Focused edit sheet for an interval group (iOS `IntervalEditSheet`):
 * header with the big Total Time → Timing (mode-specific steppers) → Sets
 * (+ Rest Between Sets) → Active Recovery → Preview → Coach Notes →
 * Change style. Every number comes from `IntervalMath`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalEditSheet(row: TaskRow, viewModel: WorkoutEditorViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lead = row.task ?: WorkoutTask()
    val taskId = lead.id
    val mode = row.intervalMode ?: lead.intervalMode ?: IntervalMode.HIIT
    val plan = IntervalMath.plan(lead, rowTotalRounds = row.totalRounds, mode = mode)
    val metrics = IntervalMetrics.from(row)
    val windowSeconds = lead.intervalWindowSeconds ?: 600
    val sets = maxOf(1, lead.intervalSets ?: 1)
    var showModeChangeConfirm by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    var showSetRestPicker by remember { mutableStateOf(false) }

    if (showModeChangeConfirm) {
        AlertDialog(
            onDismissRequest = { showModeChangeConfirm = false },
            title = { Text("Change interval style?") },
            text = { Text("Switching styles will reset Work, Rest, Rounds, and Window to the new style's defaults.") },
            confirmButton = { TextButton(onClick = { showModeChangeConfirm = false; showModePicker = true }) { Text("Continue", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { showModeChangeConfirm = false }) { Text("Cancel") } }
        )
    }
    if (showModePicker) {
        IntervalModePickerSheet(onPick = { viewModel.updateIntervalMode(taskId, it); showModePicker = false }, onCancel = { showModePicker = false })
    }
    if (showSetRestPicker) {
        RestDurationPickerSheet(
            initialSeconds = lead.effectiveIntervalSetRestSeconds ?: IntervalMath.defaultSetRestSeconds, title = "Rest Between Sets",
            onConfirm = { viewModel.updateIntervalSetRest(taskId, it); showSetRestPicker = false }, onCancel = { showSetRestPicker = false }
        )
    }
    if (viewModel.restMovementPickerTaskId == taskId) {
        // HIIT-007: the recovery picker is presented from inside this sheet.
        ExerciseBrowserSheet(
            onSelect = { movement -> viewModel.updateRestMovement(taskId, movement); viewModel.restMovementPickerTaskId = null },
            onCreateNew = {},
            onDismiss = { viewModel.restMovementPickerTaskId = null },
            pendingGroupType = null, pendingCount = 0,
            mode = BrowserMode.Single("Recovery Exercise")
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BackgroundColor) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Edit Interval", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done", fontWeight = FontWeight.SemiBold, color = FLPrimary) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(mode.displayName.uppercase(Locale.US), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = BlueTheme)
                if (metrics != null) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(metrics.totalText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text("Total Time", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    metrics.summaryLines.dropLast(1).forEach { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor) }
                }
            }

            SectionCard("Timing") {
                when (mode) {
                    IntervalMode.TABATA, IntervalMode.HIIT -> {
                        LabeledStepper("Work", plan.workSeconds, 5, 300, 5, "s") { viewModel.updateIntervalWork(taskId, it) }
                        LabeledStepper("Rest", plan.restSeconds, 0, 300, 5, "s") { viewModel.updateIntervalRest(taskId, it) }
                        LabeledStepper("Rounds per Set", plan.roundsPerSet, 1, 30, 1, "") { viewModel.updateGroupRounds(taskId, it) }
                        Text("Per set: ${IntervalMath.formatTime(IntervalMath.setSeconds(plan))}", fontSize = 11.sp, color = TextSecondaryColor)
                    }
                    IntervalMode.EMOM -> {
                        LabeledStepper("Minutes", maxOf(1, windowSeconds / 60), 1, 30, 1, " min") { viewModel.updateIntervalWindow(taskId, it * 60) }
                        LabeledStepper("Work / min", minOf(plan.workSeconds, 55), 5, 55, 5, "s") { viewModel.updateIntervalWork(taskId, it) }
                        Text("Rest: ${60 - minOf(plan.workSeconds, 55)}s implicit · Per set: ${IntervalMath.formatTime(IntervalMath.setSeconds(plan))}", fontSize = 11.sp, color = TextSecondaryColor)
                    }
                    IntervalMode.AMRAP -> {
                        LabeledStepper("Window", maxOf(1, windowSeconds / 60), 1, 30, 1, " min") { viewModel.updateIntervalWindow(taskId, it * 60) }
                        Text("As many rounds as possible in ${IntervalMath.formatTime(windowSeconds)}. Tap +1 mid-workout.", fontSize = 11.sp, color = TextSecondaryColor)
                    }
                }
            }

            if (mode != IntervalMode.AMRAP) {
                SectionCard("Sets") {
                    LabeledStepper("Number of Sets", sets, 1, 20, 1, "") { viewModel.updateIntervalSets(taskId, it) }
                    if (sets > 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Rest Between Sets", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showSetRestPicker = true }) {
                                Text(
                                    IntervalMath.formatTime(lead.effectiveIntervalSetRestSeconds ?: IntervalMath.defaultSetRestSeconds),
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary
                                )
                                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            SectionCard("Active Recovery") { ActiveRecoverySection(row = row, viewModel = viewModel) }
            SectionCard("Preview") { IntervalTimelinePreview(row = row) }
            SectionCard("Coach Notes") {
                InstructionsField(value = lead.instructions ?: "", onValueChange = { viewModel.updateInstructions(taskId, it) }, label = "", placeholder = "Coaching cue (optional)")
            }

            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).clickable { showModeChangeConfirm = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                Text("Change interval style…", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title.uppercase(Locale.US), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryColor)
        content()
    }
}

@Composable
private fun LabeledStepper(label: String, value: Int, min: Int, max: Int, step: Int, suffix: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        EditorStepper(value = value, onChange = onChange, min = min, max = max, step = step, suffix = suffix, valueWidth = 64)
    }
}

/** Passive vs active rest for the interval's per-round rest, plus the recovery-exercise picker and in-group quick-picks. */
@Composable
private fun ActiveRecoverySection(row: TaskRow, viewModel: WorkoutEditorViewModel) {
    val lead = row.task ?: return
    val taskId = lead.id
    val restType = lead.restType
    val inGroup = (listOf(lead) + row.advancedTasks).filter { !it.movementId.isNullOrEmpty() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BackgroundColor)) {
            SegmentPill("Passive", restType == RestType.PASSIVE, Modifier.weight(1f)) { viewModel.updateRestType(taskId, RestType.PASSIVE) }
            SegmentPill("Active", restType == RestType.ACTIVE_RECOVERY, Modifier.weight(1f)) { viewModel.updateRestType(taskId, RestType.ACTIVE_RECOVERY) }
        }
        Text(
            if (restType == RestType.PASSIVE) "Stop and recover between rounds." else "Keep moving lightly between rounds — walk, jog, or mobility work.",
            fontSize = 11.sp, color = TextSecondaryColor
        )
        if (restType == RestType.ACTIVE_RECOVERY) {
            if (lead.restMovementId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BackgroundColor).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RecoveryThumbnail(lead.restMovementThumbnailId, 40)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Recovery Exercise", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                        Text(lead.restMovementName ?: "Recovery", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, maxLines = 1)
                    }
                    Text("Edit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary, modifier = Modifier.clickable { viewModel.restMovementPickerTaskId = taskId })
                    Text("Remove", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Red, modifier = Modifier.clickable { viewModel.clearRestMovement(taskId) })
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BackgroundColor)
                        .clickable { viewModel.restMovementPickerTaskId = taskId }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recovery Exercise", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor, modifier = Modifier.weight(1f))
                    Text("+ Add Exercise", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                }
            }
            if (inGroup.isNotEmpty()) {
                Text("Or use one of these", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    inGroup.forEach { mvt ->
                        val selected = lead.restMovementId == mvt.movementId
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) FLPrimary else BackgroundColor)
                                .clickable { viewModel.updateRestMovementFromInGroupTask(taskId, mvt) }.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = White, modifier = Modifier.size(12.dp))
                            Text(mvt.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (selected) White else TextPrimaryColor, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
