package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.WorkoutEditorViewModel
import com.fitnesslink.fit.workouts.WorkoutRowOps

private val WorkGreen = Color(0xFF22A35A)

/**
 * Grouped row in the editor (iOS `GroupEditorView`): superset / circuit /
 * interval header with rounds stepper and actions menu, the interval summary
 * card or round-rest row, each member with its own menu and inline set
 * editor, and the active-recovery row.
 */
@Composable
fun GroupEditorView(row: TaskRow, phaseIndex: Int, viewModel: WorkoutEditorViewModel) {
    val lead = row.task ?: return
    val leadId = lead.id
    val groupColor = when { row.isSuperset -> PurpleTheme; row.isCircuit -> OrangeTheme; else -> BlueTheme }
    var showMenu by remember(leadId) { mutableStateOf(false) }
    var showMoveMenu by remember(leadId) { mutableStateOf(false) }
    var memberPendingDelete by remember(leadId) { mutableStateOf<WorkoutTask?>(null) }
    val memberCount = 1 + row.advancedTasks.size
    val otherPhases = viewModel.workout.phases.withIndex().filter { it.index != phaseIndex }

    memberPendingDelete?.let { member ->
        val label = if (row.isInterval) "${row.intervalMode?.displayName ?: "HIIT"} interval" else if (row.isSuperset) "superset" else "circuit"
        AlertDialog(
            onDismissRequest = { memberPendingDelete = null },
            title = { Text("Delete Exercise?") },
            text = { Text("Remove ${member.name} from this $label? The rest of the block keeps its order and settings.") },
            confirmButton = { TextButton(onClick = { viewModel.removeGroupMember(member.id, phaseIndex); memberPendingDelete = null }) { Text("Delete", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { memberPendingDelete = null }) { Text("Cancel") } }
        )
    }

    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(White).height(IntrinsicSize.Min)) {
        Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(groupColor))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val header = if (row.isInterval && row.intervalMode != null) "Interval · ${row.intervalMode.displayName}" else row.advanced
                    Text(header.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = groupColor)
                    if (!row.isInterval && row.totalRounds > 0) Text("${row.totalRounds} rounds", fontSize = 11.sp, color = TextSecondaryColor)
                }
                if (row.isSuperset || row.isCircuit) {
                    EditorStepper(row.totalRounds, { viewModel.updateGroupRounds(leadId, it) }, min = 1, max = 20, valueWidth = 28)
                }
                Box {
                    Icon(
                        Icons.Filled.MoreVert, contentDescription = "Group actions", tint = MediumGray,
                        modifier = Modifier.size(28.dp).clickable { showMenu = true }.padding(4.dp)
                    )
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (row.isCircuit && viewModel.canAddNextMovement(leadId, phaseIndex)) {
                            DropdownMenuItem(text = { Text("Add next movement") }, onClick = { showMenu = false; viewModel.addNextMovementToGroup(leadId, phaseIndex) })
                        }
                        DropdownMenuItem(text = { Text("Ungroup") }, onClick = { showMenu = false; viewModel.ungroupExercise(leadId, phaseIndex) })
                        if (otherPhases.isNotEmpty()) {
                            DropdownMenuItem(text = { Text("Move to phase…") }, onClick = { showMenu = false; showMoveMenu = true })
                        }
                        DropdownMenuItem(text = { Text("Remove", color = Color.Red) }, onClick = { showMenu = false; viewModel.removeGroup(leadId, phaseIndex) })
                    }
                    DropdownMenu(expanded = showMoveMenu, onDismissRequest = { showMoveMenu = false }) {
                        otherPhases.forEach { (index, phase) ->
                            DropdownMenuItem(text = { Text(phase.name) }, onClick = { showMoveMenu = false; viewModel.moveRow(leadId, index) })
                        }
                    }
                }
            }

            if (row.isInterval) {
                IntervalSummaryCard(row = row, viewModel = viewModel, modifier = Modifier.padding(horizontal = 12.dp))
            } else if (row.isSuperset || row.isCircuit) {
                RoundRestEditorRow(row = row, viewModel = viewModel, modifier = Modifier.padding(horizontal = 12.dp))
            }

            (listOf(lead) + row.advancedTasks).forEachIndexed { memberIdx, task ->
                GroupMemberRow(
                    row = row, task = task, memberIdx = memberIdx, memberCount = memberCount, phaseIndex = phaseIndex,
                    viewModel = viewModel, onDelete = { memberPendingDelete = task }
                )
            }

            if (row.isInterval && lead.restType == RestType.ACTIVE_RECOVERY && lead.restMovementId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RolePill("RECOVERY", OrangeTheme)
                    RecoveryThumbnail(lead.restMovementThumbnailId, 32)
                    Text(lead.restMovementName ?: "Recovery", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Sync, contentDescription = "Change recovery movement", tint = MediumGray,
                        modifier = Modifier.size(20.dp).clickable { viewModel.restMovementPickerTaskId = leadId })
                    Icon(Icons.Filled.Cancel, contentDescription = "Passive rest", tint = MediumGray,
                        modifier = Modifier.size(20.dp).clickable { viewModel.updateRestType(leadId, RestType.PASSIVE) })
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun GroupMemberRow(
    row: TaskRow, task: WorkoutTask, memberIdx: Int, memberCount: Int, phaseIndex: Int,
    viewModel: WorkoutEditorViewModel, onDelete: () -> Unit
) {
    val isExpanded = viewModel.expandedTaskId == task.id
    var showMenu by remember(task.id) { mutableStateOf(false) }
    val editorTask = task.copy(isInterval = task.isInterval || row.isInterval, intervalMode = task.intervalMode ?: row.intervalMode)
    val metric = when {
        editorTask.isInterval -> if (IntervalMath.usesRepTargets(row.intervalMode ?: editorTask.intervalMode) && editorTask.reps > 0) "${editorTask.reps} reps" else ""
        editorTask.usesDuration -> WorkoutRowOps.formatDuration(editorTask.durationSeconds ?: 0)
        editorTask.reps > 0 -> "${editorTask.reps} reps"
        else -> ""
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f).clickable { viewModel.expandedTaskId = if (isExpanded) null else task.id },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (row.isInterval) RolePill("WORK", WorkGreen)
                Text(task.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                if (metric.isNotEmpty()) Text(metric, fontSize = 12.sp, color = TextSecondaryColor)
                Icon(if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MediumGray, modifier = Modifier.size(18.dp))
            }
            Box {
                Icon(Icons.Filled.MoreHoriz, contentDescription = "Member actions", tint = MediumGray, modifier = Modifier.size(24.dp).clickable { showMenu = true })
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Edit exercise") }, onClick = { showMenu = false; viewModel.expandedTaskId = task.id })
                    DropdownMenuItem(text = { Text("Replace exercise") }, onClick = { showMenu = false; viewModel.replaceExerciseTaskId = task.id })
                    if (memberIdx > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { showMenu = false; viewModel.moveGroupMember(task.id, true, phaseIndex) })
                    if (memberIdx < memberCount - 1) DropdownMenuItem(text = { Text("Move down") }, onClick = { showMenu = false; viewModel.moveGroupMember(task.id, false, phaseIndex) })
                    DropdownMenuItem(text = { Text("Delete exercise", color = Color.Red) }, onClick = { showMenu = false; onDelete() })
                }
            }
        }
        if ((row.isSuperset || row.isCircuit) && memberIdx < memberCount - 1) {
            MemberRestEditorRow(task = task, viewModel = viewModel, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp))
        }
        if (isExpanded) {
            Box(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
                SetEditorView(task = editorTask, viewModel = viewModel, isGrouped = true)
            }
        }
    }
}

@Composable
internal fun RolePill(label: String, color: Color) {
    Text(
        text = label, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = White,
        modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

/** Read-only interval summary; tapping opens [IntervalEditSheet]. */
@Composable
private fun IntervalSummaryCard(row: TaskRow, viewModel: WorkoutEditorViewModel, modifier: Modifier = Modifier) {
    var showEdit by remember(row.task?.id) { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(FLPrimary.copy(alpha = 0.06f))
            .clickable { showEdit = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val metrics = IntervalMetrics.from(row)
        if (metrics != null) IntervalMetricsLine(metrics, modifier = Modifier.weight(1f))
        else Text("Tap to finish setup", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary, modifier = Modifier.weight(1f))
        Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(16.dp))
    }
    if (showEdit) {
        // Re-read the live row so edits inside the sheet reflect immediately.
        val liveRow = viewModel.workout.phases.flatMap { it.taskRows }.firstOrNull { it.task?.id == row.task?.id } ?: row
        IntervalEditSheet(row = liveRow, viewModel = viewModel, onDismiss = { showEdit = false })
    }
}

/** Tap-to-edit rest between rounds of a superset / circuit; X clears (stores null). */
@Composable
private fun RoundRestEditorRow(row: TaskRow, viewModel: WorkoutEditorViewModel, modifier: Modifier = Modifier) {
    val taskId = row.task?.id ?: return
    val seconds = row.task.roundRestSeconds
    var showPicker by remember(taskId) { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = MediumGray, modifier = Modifier.size(14.dp))
        Text("Rest between rounds", fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.weight(1f))
        Text(
            text = seconds?.let(WorkoutRowOps::formatRest) ?: "Add", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary,
            modifier = Modifier.clickable { showPicker = true }
        )
        if (seconds != null && seconds > 0) {
            Icon(Icons.Filled.Cancel, contentDescription = "Clear", tint = MediumGray, modifier = Modifier.size(16.dp).clickable { viewModel.updateRoundRest(taskId, null) })
        }
    }
    if (showPicker) {
        RestDurationPickerSheet(
            initialSeconds = seconds?.takeIf { it > 0 } ?: 60, title = "Rest Between Rounds",
            onConfirm = { viewModel.updateRoundRest(taskId, it); showPicker = false }, onCancel = { showPicker = false }
        )
    }
}

/** Rest taken after a superset / circuit member, before the next member in the same round. */
@Composable
private fun MemberRestEditorRow(task: WorkoutTask, viewModel: WorkoutEditorViewModel, modifier: Modifier = Modifier) {
    val seconds = task.restSeconds.takeIf { it > 0 }
    var showPicker by remember(task.id) { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = MediumGray, modifier = Modifier.size(12.dp))
        Text(
            text = seconds?.let { "Rest after: ${WorkoutRowOps.formatRest(it)}" } ?: "Add rest after",
            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (seconds == null) TextSecondaryColor else FLPrimary,
            modifier = Modifier.clickable { showPicker = true }
        )
        if (seconds != null) {
            Icon(Icons.Filled.Cancel, contentDescription = "Clear", tint = MediumGray, modifier = Modifier.size(14.dp).clickable { viewModel.updateGroupMemberRest(task.id, null) })
        }
    }
    if (showPicker) {
        RestDurationPickerSheet(
            initialSeconds = seconds ?: 30, title = "Rest After ${task.name}",
            onConfirm = { viewModel.updateGroupMemberRest(task.id, it); showPicker = false }, onCancel = { showPicker = false }
        )
    }
}

/** First step of the guided Interval flow (and the Edit sheet's "Change interval style…"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalModePickerSheet(onPick: (IntervalMode) -> Unit, onCancel: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState, containerColor = White) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel", color = TextSecondaryColor) }
                Spacer(modifier = Modifier.weight(1f))
                Text("New Interval", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(64.dp))
            }
            Text("Pick an interval style", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            ModeOptionCard("Tabata", "8 rounds · 20s work / 10s rest. Fixed 4:00 protocol.", Icons.Filled.Timer, PurpleTheme) { onPick(IntervalMode.TABATA) }
            ModeOptionCard("HIIT", "Custom work / rest over your chosen rounds.", Icons.Filled.LocalFireDepartment, OrangeTheme) { onPick(IntervalMode.HIIT) }
            ModeOptionCard("EMOM", "Every minute on the minute. Work N seconds, rest the remainder.", Icons.Filled.AccessTime, BlueTheme) { onPick(IntervalMode.EMOM) }
            ModeOptionCard("AMRAP", "As many rounds as possible inside a fixed time window.", Icons.Filled.AllInclusive, FLPrimary) { onPick(IntervalMode.AMRAP) }
        }
    }
}

@Composable
private fun ModeOptionCard(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundColor).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text(subtitle, fontSize = 13.sp, color = TextSecondaryColor)
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray)
    }
}
