package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.EmomEditorState
import com.fitnesslink.fit.model.EmomMath
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.ui.theme.WorkGreen
import com.fitnesslink.fit.viewmodel.WorkoutEditorViewModel
import java.util.Locale

/**
 * Edit EMOM (EMOM PRD v2.0 §3.4): Summary → Timing (Total Duration ⇄ Interval) → Rounds & Cycles →
 * Preview → Coach Notes → Change interval style. Edits stay in an [EmomEditorState] draft: Done
 * (disabled while any validation message shows) writes it, Cancel / swipe-away discards it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmomEditSheet(row: TaskRow, viewModel: WorkoutEditorViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val taskId = row.task?.id ?: return
    val state = remember(taskId) { EmomEditorState.from(row) } ?: return
    // EmomEditorState is a plain class; bump to recompose after every edit.
    var version by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = version
    var durationText by remember(taskId) { mutableStateOf(EmomEditorState.formatMinutes(state.totalSeconds)) }
    var cyclesText by remember(taskId) { mutableStateOf("${state.cycles}") }
    var notesText by remember(taskId) { mutableStateOf(state.coachNotes ?: "") }
    var showIntervalMenu by remember { mutableStateOf(false) }
    var showModeChangeConfirm by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    val single = state.exerciseCount == 1
    val plan = state.plan

    fun syncAfterPrimaryEdit() {
        if (state.lastPrimary == EmomEditorState.Primary.CYCLES) durationText = EmomEditorState.formatMinutes(state.totalSeconds)
        else if (state.durationMessage == null) cyclesText = "${state.cycles}"
        version += 1
    }

    if (showModeChangeConfirm) {
        AlertDialog(
            onDismissRequest = { showModeChangeConfirm = false },
            title = { Text("Change interval style?") },
            text = { Text("Switching styles will reset Work, Rest, Rounds, and Window to the new style's defaults.") },
            confirmButton = { TextButton(onClick = { showModeChangeConfirm = false; showModePicker = true }) { Text("Continue", color = FLTheme.colors.danger) } },
            dismissButton = { TextButton(onClick = { showModeChangeConfirm = false }) { Text("Cancel") } }
        )
    }
    if (showModePicker) {
        IntervalModePickerSheet(onPick = { viewModel.updateIntervalMode(taskId, it); showModePicker = false }, onCancel = { showModePicker = false })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = FLTheme.colors.background, contentColor = FLTheme.colors.textPrimary) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = FLTheme.colors.textSecondary) }
                Text("Edit EMOM", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(
                    enabled = state.isValid,
                    onClick = { viewModel.applyEmomEdit(taskId, state.interval, state.cycles, notesText); onDismiss() }
                ) { Text("Done", fontWeight = FontWeight.SemiBold, color = if (state.isValid) FLPrimary else FLTheme.colors.textTertiary) }
            }

            // Summary
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("EMOM", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = BlueTheme)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(IntervalMath.formatTime(plan.totalSeconds), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("Total Time", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(EmomMath.roundsCyclesLine(plan), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textSecondary)
            }

            EmomSection("Timing") {
                FieldLabel("Total Duration", "Enter the total time you want to program.")
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { raw ->
                        durationText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                        state.editDurationMinutes(durationText)
                        syncAfterPrimaryEdit()
                    },
                    singleLine = true,
                    suffix = { Text("min", fontSize = 13.sp, color = FLTheme.colors.textSecondary) },
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                FieldLabel("Interval Duration", "The length of each round.")
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(FLTheme.colors.background)
                            .clickable { showIntervalMenu = true }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(EmomMath.intervalLabel(state.interval), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose interval", tint = FLTheme.colors.textSecondary)
                    }
                    DropdownMenu(expanded = showIntervalMenu, onDismissRequest = { showIntervalMenu = false }) {
                        val options = (EmomMath.intervalOptions + state.interval).distinct().sorted()
                        options.forEach { seconds ->
                            DropdownMenuItem(
                                text = { Text(EmomMath.intervalLabel(seconds), fontWeight = if (seconds == state.interval) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    showIntervalMenu = false
                                    state.editInterval(seconds)
                                    syncAfterPrimaryEdit()
                                }
                            )
                        }
                    }
                }
            }

            EmomSection("Rounds & Cycles") {
                FieldLabel(if (single) "Rounds" else "Cycles", EmomMath.cyclesCaption(state.exerciseCount))
                OutlinedTextField(
                    value = cyclesText,
                    onValueChange = { raw ->
                        cyclesText = raw.filter(Char::isDigit).take(3)
                        cyclesText.toIntOrNull()?.takeIf { it >= 1 }?.let { state.editCycles(it) }
                        syncAfterPrimaryEdit()
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                EmomMath.totalRoundsCaption(state.exerciseCount)?.let { caption ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Total Rounds", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${plan.totalRounds}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
                    }
                    Text(caption, fontSize = 11.sp, color = FLTheme.colors.textSecondary)
                }
            }

            if (state.messages.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLTheme.colors.danger.copy(alpha = 0.1f)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.messages.forEach { message ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = FLTheme.colors.danger, modifier = Modifier.size(16.dp))
                            Text(message, fontSize = 13.sp, color = FLTheme.colors.danger)
                        }
                    }
                }
            }

            EmomSection("Preview") {
                EmomPreviewRows(state.members, plan.intervalSeconds)
                Text(EmomMath.previewFooter(plan), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLTheme.colors.textSecondary)
            }

            EmomSection("Coach Notes") {
                InstructionsField(value = notesText, onValueChange = { notesText = it; state.coachNotes = it }, label = "", placeholder = "Coaching cue (optional)")
            }

            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLTheme.colors.surface).clickable { showModeChangeConfirm = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                Text("Change interval style…", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = FLTheme.colors.textTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** One preview row per member: `R1` chip, name, target (or "Reps – Time"), trailing "60s". */
@Composable
fun EmomPreviewRows(members: List<com.fitnesslink.fit.model.WorkoutTask>, intervalSeconds: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        members.forEachIndexed { i, task ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "R${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = FLTheme.colors.onBrand,
                    modifier = Modifier.clip(CircleShape).background(WorkGreen).padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(task.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(EmomMath.targetText(task), fontSize = 12.sp, color = FLTheme.colors.textSecondary)
                }
                Text("${intervalSeconds}s", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun EmomSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLTheme.colors.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title.uppercase(Locale.US), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textSecondary)
        content()
    }
}

@Composable
private fun FieldLabel(title: String, caption: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(caption, fontSize = 11.sp, color = FLTheme.colors.textSecondary)
    }
}
