package com.fitnesslink.fit.ui.quickstart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.ExerciseGroupType
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.ExerciseState
import com.fitnesslink.fit.session.QuickStartPlan
import com.fitnesslink.fit.session.SessionExerciseNote
import com.fitnesslink.fit.session.SetState
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.session.PlaylistSetStatusRow
import com.fitnesslink.fit.session.SessionDiscardRule
import com.fitnesslink.fit.ui.session.WorkoutCompletedScreen
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.ImagePlaceholder
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.ui.workout.BrowserMode
import com.fitnesslink.fit.ui.workout.CreateExerciseSheet
import com.fitnesslink.fit.ui.workout.ExerciseBrowserSheet
import com.fitnesslink.fit.ui.workout.IntervalModePickerSheet
import com.fitnesslink.fit.ui.workout.RestDurationPickerSheet
import com.fitnesslink.fit.viewmodel.QuickStartViewModel
import com.fitnesslink.fit.workouts.WorkoutRowOps
import kotlinx.coroutines.launch

private data class BrowserRequest(val phaseIndex: Int, val groupType: ExerciseGroupType? = null, val intervalMode: IntervalMode? = null)
private data class LogTarget(val task: WorkoutTask, val setNumber: Int)

/**
 * Quick Start PRD §10–11, §20–26, §40 — the Live Workout canvas: the
 * running timer, the focus / name card, and the phases the user builds as
 * they train. Same structure as the Workout Builder, but every exercise row
 * carries Set Logging Circles instead of a plan. The X leaves the session
 * running (Save & Exit — Home shows it under "Continue Where You Left
 * Off"); Finish Workout ends it. Port of iOS `QuickStartCanvasView`.
 */
@Composable
fun QuickStartCanvasScreen(
    workoutId: String,
    onBack: () -> Unit,
    onOpenMovement: (String) -> Unit = {},
    viewModel: QuickStartViewModel = viewModel()
) {
    @Suppress("UNUSED_VARIABLE") val tick = viewModel.tick
    val scope = rememberCoroutineScope()
    var showPhasePicker by remember { mutableStateOf(false) }
    var browserRequest by remember { mutableStateOf<BrowserRequest?>(null) }
    var createRequest by remember { mutableStateOf<BrowserRequest?>(null) }
    var intervalPhase by remember { mutableStateOf<Int?>(null) }
    var restPhase by remember { mutableStateOf<Int?>(null) }
    var logTarget by remember { mutableStateOf<LogTarget?>(null) }
    var renamePhase by remember { mutableStateOf<Int?>(null) }
    var renamePhaseDraft by remember { mutableStateOf("") }
    var deletePhase by remember { mutableStateOf<Int?>(null) }
    var removeTarget by remember { mutableStateOf<WorkoutTask?>(null) }
    var removeGroupLeadId by remember { mutableStateOf<String?>(null) }
    var showRenameWorkout by remember { mutableStateOf(false) }
    var workoutNameDraft by remember { mutableStateOf("") }
    var showFocusPicker by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) { if (!viewModel.isLoaded) viewModel.load(workoutId) }

    fun goHome() { if (AppRouter.resetStackHandler != null) AppRouter.resetStack(AppRouter.HOME_TAB) else onBack() }

    /** Picked movements → loose exercises, or a block when the request asked for one and enough were picked. */
    fun apply(request: BrowserRequest, picks: List<MovementLibraryItem>) {
        if (picks.isEmpty()) return
        val type = request.groupType
        if (type != null && picks.size >= WorkoutRowOps.minimumGroupCount(type)) viewModel.addGroup(type, picks, request.intervalMode, request.phaseIndex)
        else viewModel.addExercises(picks, request.phaseIndex)
    }

    if (viewModel.isWorkoutComplete) {
        BackHandler { }
        WorkoutCompletedScreen(
            sessionId = viewModel.sessionId, workoutName = viewModel.workout.name, stats = viewModel.summaryStats,
            achievements = viewModel.summaryAchievements,
            // Both paths save; the X on the summary means "skip the rating", not "back to the workout".
            onComplete = { rpe -> viewModel.finish(rpe); goHome() },
            onDismiss = { viewModel.finish(null); goHome() }
        )
        return
    }

    if (showFocusPicker) {
        QuickStartFocusScreen(onBack = { showFocusPicker = false }, onPick = { viewModel.changeFocus(it); showFocusPicker = false }, initial = viewModel.focus)
        BackHandler { showFocusPicker = false }
        return
    }

    Column(Modifier.fillMaxSize().background(BackgroundColor)) {
        // Top bar: X = save & exit; ⋯ = pause / rename / focus / discard
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Close, contentDescription = "Save and exit — your workout keeps running", tint = TextPrimaryColor, modifier = Modifier.size(28.dp).clickable { onBack() }.padding(2.dp))
            Text("Quick Start", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            var menu by remember { mutableStateOf(false) }
            Box {
                Icon(Icons.Filled.MoreHoriz, contentDescription = "Workout options", tint = FLPrimary, modifier = Modifier.size(28.dp).clickable { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (viewModel.isPaused) "Resume Timer" else "Pause Timer") }, onClick = { menu = false; viewModel.togglePause() })
                    DropdownMenuItem(text = { Text("Rename Workout") }, onClick = { menu = false; workoutNameDraft = viewModel.workout.name; showRenameWorkout = true })
                    DropdownMenuItem(text = { Text("Change Training Focus") }, onClick = { menu = false; showFocusPicker = true })
                    DropdownMenuItem(text = { Text("Discard Session", color = Color(0xFFE53935)) }, onClick = { menu = false; showDiscardConfirm = true })
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TimerCard(viewModel)
            InfoCard(viewModel, onRename = { workoutNameDraft = viewModel.workout.name; showRenameWorkout = true }, onFocus = { showFocusPicker = true })
            if (viewModel.workout.phases.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(White).padding(vertical = 36.dp, horizontal = 20.dp)) {
                    Icon(Icons.Filled.Layers, contentDescription = null, tint = MediumGray, modifier = Modifier.size(36.dp))
                    Text("Add your first phase to start building your workout.", fontSize = 15.sp, color = TextSecondaryColor, textAlign = TextAlign.Center)
                }
            } else {
                viewModel.workout.phases.forEachIndexed { index, phase ->
                    QuickStartPhaseCard(
                        viewModel = viewModel, index = index, phase = phase,
                        isCollapsed = viewModel.collapsedPhaseIds.contains(phase.id),
                        onToggleCollapsed = { viewModel.collapsedPhaseIds = if (viewModel.collapsedPhaseIds.contains(phase.id)) viewModel.collapsedPhaseIds - phase.id else viewModel.collapsedPhaseIds + phase.id },
                        onRename = { renamePhaseDraft = phase.name; renamePhase = index },
                        onDelete = { deletePhase = index },
                        onAddExercise = { browserRequest = BrowserRequest(index) },
                        onAddRest = { restPhase = index },
                        onAddGroup = { type -> if (type == ExerciseGroupType.Interval) intervalPhase = index else browserRequest = BrowserRequest(index, type) },
                        onLogSet = { task, setNumber -> logTarget = LogTarget(task, setNumber) },
                        onRemoveTask = { task -> if (task.isRest) viewModel.removeTask(task) else removeTarget = task },
                        onRemoveGroup = { removeGroupLeadId = it },
                        onOpenMovement = onOpenMovement
                    )
                }
            }
            val empty = viewModel.workout.phases.isEmpty()
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(if (empty) FLPrimary else FLPrimary.copy(alpha = 0.12f))
                    .border(1.dp, FLPrimary.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).clickable { showPhasePicker = true }
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = null, tint = if (empty) White else FLPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Phase", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (empty) White else FLPrimary)
            }
        }

        // Finish (PRD §40) — a real inset so an empty canvas never slides Add Phase under it.
        Box(Modifier.fillMaxWidth().background(BackgroundColor).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(PurpleTheme).clickable { viewModel.requestFinish() }
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Finish Workout", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
            }
        }
    }

    // MARK: Sheets

    if (showPhasePicker) {
        QuickStartPhasePickerSheet(alreadyAttachedIds = viewModel.workout.phases.map { it.id }.toSet(), onPick = { viewModel.addPhase(it) }, onDismiss = { showPhasePicker = false })
    }
    browserRequest?.let { request ->
        ExerciseBrowserSheet(
            onSelect = { },
            onCreateNew = { browserRequest = null; createRequest = request },
            onDismiss = { browserRequest = null },
            pendingGroupType = request.groupType, pendingCount = 0, mode = BrowserMode.Multi,
            multiSelect = true,
            maxSelection = if (request.groupType == ExerciseGroupType.Superset) 2 else null,
            suggestionPhaseName = viewModel.workout.phases.getOrNull(request.phaseIndex)?.name,
            onAddSelected = { picks -> browserRequest = null; apply(request, picks) }
        )
    }
    createRequest?.let { request ->
        CreateExerciseSheet(onCreated = { movement -> createRequest = null; apply(request, listOf(movement)) }, onDismiss = { createRequest = null })
    }
    intervalPhase?.let { index ->
        IntervalModePickerSheet(onPick = { mode -> intervalPhase = null; browserRequest = BrowserRequest(index, ExerciseGroupType.Interval, mode) }, onCancel = { intervalPhase = null })
    }
    restPhase?.let { index ->
        RestDurationPickerSheet(initialSeconds = 60, title = "Add Rest", confirmLabel = "Add", onConfirm = { seconds -> viewModel.addRest(index, seconds); restPhase = null }, onCancel = { restPhase = null })
    }
    logTarget?.let { target ->
        ExerciseLogSheet(
            task = target.task, sessionId = viewModel.sessionId, loggedSets = viewModel.loggedSets(target.task.id),
            initialSetNumber = target.setNumber, note = viewModel.exerciseNote(target.task.id),
            unit = if (target.task.groupId.isEmpty()) "Set" else "Round",
            onSaveSet = { number, weight, reps, seconds, distance -> viewModel.logSet(target.task.id, number, weight, reps, seconds, distance) },
            onDeleteSet = { number -> viewModel.deleteSet(target.task.id, number) },
            onSaveNote = { rpe, log -> viewModel.saveExerciseNote(target.task, rpe, log) },
            onDismiss = { logTarget = null }
        )
    }

    // MARK: Dialogs

    if (showRenameWorkout) {
        TextDialog("Rename Workout", workoutNameDraft, { workoutNameDraft = it }, "Workout name", "Save",
            onConfirm = { viewModel.rename(workoutNameDraft); showRenameWorkout = false }, onDismiss = { showRenameWorkout = false })
    }
    renamePhase?.let { index ->
        TextDialog("Rename Phase", renamePhaseDraft, { renamePhaseDraft = it }, "Phase name", "Rename",
            onConfirm = { val name = renamePhaseDraft; renamePhase = null; scope.launch { viewModel.renamePhase(index, name) } }, onDismiss = { renamePhase = null },
            message = "The new name applies everywhere you use this phase.")
    }
    viewModel.phaseRenameError?.let { error ->
        AlertDialog(onDismissRequest = { viewModel.phaseRenameError = null }, title = { Text("Couldn't Rename Phase") }, text = { Text(error) },
            confirmButton = { TextButton(onClick = { viewModel.phaseRenameError = null }) { Text("OK") } })
    }
    deletePhase?.let { index ->
        val phase = viewModel.workout.phases.getOrNull(index)
        val count = phase?.allTasks?.count { it.isMovement } ?: 0
        ConfirmDialog(
            title = "Delete this phase?",
            message = when {
                phase == null -> ""
                count == 0 -> "\"${phase.name}\" is empty."
                count == 1 -> "Its exercise and any logged sets are removed from this workout."
                else -> "Its $count exercises and their logged sets are removed from this workout."
            },
            confirmLabel = "Delete Phase", onConfirm = { viewModel.deletePhase(index); deletePhase = null }, onDismiss = { deletePhase = null }
        )
    }
    removeTarget?.let { task ->
        val logged = viewModel.loggedSets(task.id).size
        ConfirmDialog(
            title = "Remove this exercise?",
            message = if (logged == 0) "\"${task.name}\" is removed from this workout."
            else "\"${task.name}\" and its $logged logged ${if (logged == 1) "set are" else "sets are"} removed from this workout.",
            confirmLabel = "Remove Exercise", onConfirm = { viewModel.removeTask(task); removeTarget = null }, onDismiss = { removeTarget = null }
        )
    }
    removeGroupLeadId?.let { id ->
        ConfirmDialog("Remove this block?", "Every exercise in it and their logged sets are removed from this workout.", "Remove Block",
            onConfirm = { viewModel.removeGroup(id); removeGroupLeadId = null }, onDismiss = { removeGroupLeadId = null })
    }
    if (viewModel.showFinishConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.continueWorkout() },
            title = { Text("Finish Workout?") },
            text = { Text(viewModel.finishConfirmationMessage) },
            confirmButton = { TextButton(onClick = { viewModel.confirmFinish() }) { Text("Finish Workout", color = FLPrimary, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { viewModel.continueWorkout() }) { Text("Continue Workout", color = TextSecondaryColor) } }
        )
    }
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(SessionDiscardRule.title) },
            text = { Text("Your logged sets and this Quick Start workout are removed. Nothing is saved to your history.") },
            confirmButton = { TextButton(onClick = { showDiscardConfirm = false; viewModel.discard { goHome() } }) { Text(SessionDiscardRule.confirmLabel, color = Color(0xFFE53935), fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text(SessionDiscardRule.cancelLabel) } }
        )
    }
}

// MARK: - Cards

@Composable
private fun TimerCard(viewModel: QuickStartViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(White).padding(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (viewModel.isPaused) OrangeTheme else FLPrimary))
                Text(if (viewModel.isPaused) "Paused" else "Workout in progress", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (viewModel.isPaused) OrangeTheme else FLPrimary)
            }
            Text(viewModel.timerText, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, maxLines = 1)
        }
        RoundControl(if (viewModel.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (viewModel.isPaused) "Resume timer" else "Pause timer") { viewModel.togglePause() }
        RoundControl(Icons.Filled.Stop, "Finish workout") { viewModel.requestFinish() }
    }
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).border(1.5.dp, MediumGray.copy(alpha = 0.6f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = TextPrimaryColor, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InfoCard(viewModel: QuickStartViewModel, onRename: () -> Unit, onFocus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(White).padding(14.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(PurpleTheme.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(focusIcon(viewModel.focus.symbol), contentDescription = null, tint = PurpleTheme, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(viewModel.workout.name.ifEmpty { "Quick Start" }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Filled.Edit, contentDescription = "Rename workout", tint = MediumGray, modifier = Modifier.size(14.dp).clickable(onClick = onRename))
            }
            Text("${viewModel.focus.name} ›", fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.clickable(onClick = onFocus))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${viewModel.exerciseCount}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Text(if (viewModel.exerciseCount == 1) "exercise" else "exercises", fontSize = 11.sp, color = TextSecondaryColor)
        }
    }
}

// MARK: - Phase card

@Composable
private fun QuickStartPhaseCard(
    viewModel: QuickStartViewModel, index: Int, phase: WorkoutPhase, isCollapsed: Boolean,
    onToggleCollapsed: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
    onAddExercise: () -> Unit, onAddRest: () -> Unit, onAddGroup: (ExerciseGroupType) -> Unit,
    onLogSet: (WorkoutTask, Int) -> Unit, onRemoveTask: (WorkoutTask) -> Unit, onRemoveGroup: (String) -> Unit,
    onOpenMovement: (String) -> Unit
) {
    val (icon, color) = QuickStartPhaseOption.style(phase.name)
    val exerciseCount = phase.allTasks.count { it.isMovement }
    val phaseCount = viewModel.workout.phases.size
    val otherPhases = viewModel.workout.phases.mapIndexedNotNull { i, p -> if (i == index) null else i to p.name }
    var menu by remember(phase.id) { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(White)) {
        Box(Modifier.width(4.dp).fillMaxHeight().padding(vertical = 10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(phase.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (exerciseCount == 1) "1 exercise" else "$exerciseCount exercises", fontSize = 12.sp, color = TextSecondaryColor)
                }
                Icon(if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, contentDescription = if (isCollapsed) "Expand ${phase.name}" else "Collapse ${phase.name}", tint = TextSecondaryColor, modifier = Modifier.size(28.dp).clickable(onClick = onToggleCollapsed))
                Box {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = "${phase.name} options", tint = TextSecondaryColor, modifier = Modifier.size(28.dp).clickable { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!phase.isSystem) DropdownMenuItem(text = { Text("Rename Phase") }, onClick = { menu = false; onRename() })
                        if (index > 0) DropdownMenuItem(text = { Text("Move Up") }, onClick = { menu = false; viewModel.movePhase(index, up = true) })
                        if (index < phaseCount - 1) DropdownMenuItem(text = { Text("Move Down") }, onClick = { menu = false; viewModel.movePhase(index, up = false) })
                        DropdownMenuItem(text = { Text("Delete Phase", color = Color(0xFFE53935)) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (!isCollapsed) {
                if (phase.taskRows.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(BackgroundColor), contentAlignment = Alignment.Center) { Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = MediumGray, modifier = Modifier.size(22.dp)) }
                        Text("No exercises added yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                        Text("Add exercises to start building this phase.", fontSize = 13.sp, color = TextSecondaryColor, textAlign = TextAlign.Center)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        phase.taskRows.forEach { row ->
                            val lead = row.task ?: return@forEach
                            val isGroup = row.advancedTasks.isNotEmpty() || row.isSuperset || row.isCircuit || row.isInterval
                            when {
                                lead.isRest -> RestRow(lead) { onRemoveTask(lead) }
                                isGroup -> GroupRow(row, lead, viewModel, otherPhases, onLogSet, onRemoveTask, onRemoveGroup, onOpenMovement)
                                else -> QuickStartExerciseRow(
                                    task = lead, states = viewModel.setStates(lead.id), lastSet = viewModel.loggedSets(lead.id).lastOrNull(),
                                    note = viewModel.exerciseNote(lead.id), unit = "Set", otherPhases = otherPhases,
                                    onTapSet = { onLogSet(lead, it) }, onMove = { viewModel.moveTask(lead.id, it) }, onRemove = { onRemoveTask(lead) }, onOpenMovement = onOpenMovement
                                )
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FooterButton("Add Exercise", Icons.Filled.AddCircle, White, FLPrimary, Modifier.weight(1f), onAddExercise)
                        FooterButton("Add Rest", Icons.Filled.Timer, TextPrimaryColor, BackgroundColor, Modifier.weight(1f), onAddRest)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupButton("Superset", PurpleTheme, Modifier.weight(1f)) { onAddGroup(ExerciseGroupType.Superset) }
                        GroupButton("Circuit", OrangeTheme, Modifier.weight(1f)) { onAddGroup(ExerciseGroupType.Circuit) }
                        GroupButton("Interval", BlueTheme, Modifier.weight(1f)) { onAddGroup(ExerciseGroupType.Interval) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestRow(task: WorkoutTask, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BackgroundColor.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(16.dp))
        Text("Rest · ${task.rest.ifEmpty { WorkoutRowOps.formatRest(task.restSeconds) }}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.Close, contentDescription = "Remove rest", tint = TextSecondaryColor, modifier = Modifier.size(24.dp).clickable(onClick = onRemove).padding(4.dp))
    }
}

@Composable
private fun GroupRow(
    row: TaskRow, lead: WorkoutTask, viewModel: QuickStartViewModel, otherPhases: List<Pair<Int, String>>,
    onLogSet: (WorkoutTask, Int) -> Unit, onRemoveTask: (WorkoutTask) -> Unit, onRemoveGroup: (String) -> Unit, onOpenMovement: (String) -> Unit
) {
    val type = when { row.isSuperset -> ExerciseGroupType.Superset; row.isCircuit -> ExerciseGroupType.Circuit; else -> ExerciseGroupType.Interval }
    val color = when (type) { ExerciseGroupType.Superset -> PurpleTheme; ExerciseGroupType.Circuit -> OrangeTheme; ExerciseGroupType.Interval -> BlueTheme }
    val members = listOf(lead) + row.advancedTasks
    // Rounds actually logged (the most any member has), not the plan's floor of one open round.
    val rounds = members.maxOfOrNull { viewModel.loggedSets(it.id).size } ?: 0
    var menu by remember(lead.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.06f)).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(type.label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = White, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp))
            if (type == ExerciseGroupType.Interval) (row.intervalMode ?: lead.intervalMode)?.let { Text(it.displayName.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color) }
            Text(when (rounds) { 0 -> "No rounds logged yet"; 1 -> "1 round logged"; else -> "$rounds rounds logged" }, fontSize = 12.sp, color = TextSecondaryColor, modifier = Modifier.weight(1f))
            Box {
                Icon(Icons.Filled.MoreHoriz, contentDescription = "${type.label} options", tint = TextSecondaryColor, modifier = Modifier.size(24.dp).clickable { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    otherPhases.forEach { (i, name) -> DropdownMenuItem(text = { Text("Move to $name") }, onClick = { menu = false; viewModel.moveTask(lead.id, i) }) }
                    DropdownMenuItem(text = { Text("Remove ${type.label}", color = Color(0xFFE53935)) }, onClick = { menu = false; onRemoveGroup(lead.id) })
                }
            }
        }
        members.forEach { member ->
            QuickStartExerciseRow(
                task = member, states = viewModel.setStates(member.id), lastSet = viewModel.loggedSets(member.id).lastOrNull(),
                note = viewModel.exerciseNote(member.id), unit = "Round", otherPhases = emptyList(),
                onTapSet = { onLogSet(member, it) }, onMove = { }, onRemove = { onRemoveTask(member) }, onOpenMovement = onOpenMovement
            )
        }
    }
}

/**
 * Thumbnail, name, what was logged last, and the Set Logging Circles
 * ([PlaylistSetStatusRow], the app-wide ⊕ → ✓ control): every logged set a
 * ✓, the next one a ⊕ that never goes away (PRD §20–23).
 */
@Composable
private fun QuickStartExerciseRow(
    task: WorkoutTask, states: List<SetState>, lastSet: TaskSetConfig?, note: SessionExerciseNote?, unit: String,
    otherPhases: List<Pair<Int, String>>, onTapSet: (Int) -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit, onOpenMovement: (String) -> Unit
) {
    val loggedCount = states.count { it == SetState.COMPLETED }
    val subtitle = when {
        lastSet == null || loggedCount == 0 -> "Tap + to log your first ${unit.lowercase()}"
        loggedCount == 1 -> "1 ${unit.lowercase()} · ${QuickStartPlan.setLine(lastSet)}"
        else -> "$loggedCount ${unit.lowercase()}s · last ${QuickStartPlan.setLine(lastSet)}"
    }
    var menu by remember(task.id) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundColor.copy(alpha = 0.6f)).padding(10.dp)) {
        val thumb = (task.movementThumbnailId ?: task.movementImageId)?.let { MediaRef.MovementThumbnail(it) }
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(ImagePlaceholder)) { if (thumb != null) FLImageView(ref = thumb, modifier = Modifier.size(56.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(task.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(subtitle, fontSize = 12.sp, color = TextSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                note?.rpe?.let { Text("RPE $it", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PurpleTheme, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PurpleTheme.copy(alpha = 0.12f)).padding(horizontal = 5.dp, vertical = 1.dp)) }
            }
            PlaylistSetStatusRow(states = states, exerciseState = if (loggedCount > 0) ExerciseState.IN_PROGRESS else ExerciseState.UPCOMING, unit = unit, onTapSet = onTapSet)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            task.movementId?.let { id -> Icon(Icons.Filled.Info, contentDescription = "About ${task.name}", tint = FLPrimary, modifier = Modifier.size(28.dp).clickable { onOpenMovement(id) }.padding(4.dp)) }
            Box {
                Icon(Icons.Filled.MoreVert, contentDescription = "${task.name} options", tint = TextSecondaryColor, modifier = Modifier.size(28.dp).clickable { menu = true }.padding(4.dp))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Log $unit ${loggedCount + 1}") }, onClick = { menu = false; onTapSet(loggedCount + 1) })
                    otherPhases.forEach { (i, name) -> DropdownMenuItem(text = { Text("Move to $name") }, onClick = { menu = false; onMove(i) }) }
                    DropdownMenuItem(text = { Text("Remove Exercise", color = Color(0xFFE53935)) }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
private fun FooterButton(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, background: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = modifier.height(42.dp).clip(RoundedCornerShape(10.dp)).background(background).clickable(onClick = onClick)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun GroupButton(title: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun TextDialog(title: String, value: String, onChange: (String) -> Unit, placeholder: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, message: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message?.let { Text(it, fontSize = 13.sp, color = TextSecondaryColor) }
                OutlinedTextField(value = value, onValueChange = onChange, singleLine = true, placeholder = { Text(placeholder) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = value.isNotBlank()) { Text(confirmLabel, color = FLPrimary, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondaryColor) } }
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = Color(0xFFE53935), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondaryColor) } }
    )
}
