package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.IntervalMetrics
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.ProgramContributor
import com.fitnesslink.fit.model.api.ShareableContentKind
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.toCreateRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.session.SessionMode
import com.fitnesslink.fit.ui.components.BackCircleButton
import com.fitnesslink.fit.ui.components.ExercisesInfoView
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.LocationInfoView
import com.fitnesslink.fit.ui.components.PendingShareBanner
import com.fitnesslink.fit.ui.components.PrimaryButtonView
import com.fitnesslink.fit.ui.components.SecondaryButtonView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.components.ShareSheet
import com.fitnesslink.fit.ui.components.SharedBadge
import com.fitnesslink.fit.ui.components.TimeInfoView
import com.fitnesslink.fit.ui.components.TrainingLevelInfoView
import com.fitnesslink.fit.ui.navigation.AppRoute
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.session.SessionResumeCard
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
import com.fitnesslink.fit.viewmodel.WorkoutDetailViewModel
import com.fitnesslink.fit.workouts.WorkoutStore
import com.fitnesslink.fit.workouts.WorkoutStructure
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Workout detail (iOS `WorkoutDetailView` + `WorkoutActionView`): owner
 * actions (share / edit / delete) for user-authored workouts, Customize
 * (fork-to-edit) for templates and shared-in content, pending-share banner,
 * tags, structure summary, tappable task rows → `MovementInfoSheet`,
 * interval metric grids, coach card, resume card, Start / Add to Calendar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: String,
    onBack: () -> Unit,
    onStartPlaylist: (String) -> Unit = {},
    onStartInteractive: (String) -> Unit = {},
    onNavigateToEditor: (String) -> Unit = { AppRouter.push(AppRoute.WorkoutEditor.createRoute(it)) }
) {
    val viewModel: WorkoutDetailViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val workout = viewModel.workout

    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var showRemoveAlert by remember { mutableStateOf(false) }
    var removeFailed by remember { mutableStateOf(false) }
    var shareResponseFailed by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<WorkoutTask?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showCustomizeConfirm by remember { mutableStateOf(false) }
    var isForking by remember { mutableStateOf(false) }
    var forkErrorMessage by remember { mutableStateOf<String?>(null) }
    var showRestartConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val phaseOffsets = remember { mutableMapOf<String, Int>() }

    LaunchedEffect(workoutId) { viewModel.loadData(workoutId) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, workoutId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHasSession(workoutId)
                viewModel.reloadFromCache(workoutId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startSession(mode: SessionMode?) {
        when (mode) {
            SessionMode.INTERACTIVE -> onStartInteractive(workoutId)
            else -> onStartPlaylist(workoutId)
        }
    }

    // MARK: Dialogs

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val entry = FitnessContent(
                            id = java.util.UUID.randomUUID().toString(),
                            title = workout.name, programId = "", workoutId = workoutId, mealPlanId = "",
                            status = "scheduled", scheduledDate = millis,
                            workoutImageId = workout.imageId?.toString(), workoutThumbnailId = workout.thumbnailId?.toString()
                        )
                        DatabaseManager.insertCalendarContent(entry)
                        scope.launch {
                            if (NetworkMonitor.isConnected.value) {
                                try { ApiClient.calendarApi.create(entry.toCreateRequest()) } catch (_: Exception) {}
                            }
                        }
                    }
                    showDatePicker = false
                }) { Text("Schedule") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text("Delete Workout?") },
            text = { Text("This will permanently delete this workout.") },
            confirmButton = {
                TextButton(onClick = { showDeleteAlert = false; viewModel.deleteWorkout(workoutId); onBack() }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAlert = false }) { Text("Cancel") } }
        )
    }
    if (showRemoveAlert) {
        AlertDialog(
            onDismissRequest = { showRemoveAlert = false },
            title = { Text("Remove from your library?") },
            text = { Text("This removes the shared workout from your library. It won't delete the original or affect who shared it.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveAlert = false
                    scope.launch { if (viewModel.removeFromLibrary(workoutId)) onBack() else removeFailed = true }
                }) { Text("Remove", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showRemoveAlert = false }) { Text("Cancel") } }
        )
    }
    if (removeFailed) {
        InfoAlert("Couldn't remove", "Something went wrong removing this workout. Please try again.") { removeFailed = false }
    }
    if (shareResponseFailed) {
        InfoAlert("Couldn't update", "Something went wrong responding to this share. Please try again.") { shareResponseFailed = false }
    }
    if (showCustomizeConfirm) {
        AlertDialog(
            onDismissRequest = { showCustomizeConfirm = false },
            title = { Text("Customize This Workout?") },
            text = { Text("We'll save your own editable copy — the original stays unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    showCustomizeConfirm = false
                    if (!isForking) scope.launch {
                        isForking = true
                        try {
                            val forked = viewModel.customize(workoutId)
                            onNavigateToEditor(forked.id)
                        } catch (e: Exception) {
                            forkErrorMessage = WorkoutStore.forkFailureMessage(e)
                        } finally {
                            isForking = false
                        }
                    }
                }) { Text("Create My Copy") }
            },
            dismissButton = { TextButton(onClick = { showCustomizeConfirm = false }) { Text("Cancel") } }
        )
    }
    forkErrorMessage?.let { msg -> InfoAlert("Couldn't Customize", msg) { forkErrorMessage = null } }
    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("Restart from beginning?") },
            text = { Text("Your progress in the current session will be discarded and the workout starts over from the first set.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirm = false
                    viewModel.restartFromBeginning(workoutId) { onStartPlaylist(workoutId) }
                }) { Text("Restart", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showRestartConfirm = false }) { Text("Cancel") } }
        )
    }
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard this session?") },
            text = { Text("Your unfinished session of \"${workout.name}\" will be removed. The workout itself is kept.") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; viewModel.discardSession(workoutId) }) { Text("Discard Session", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep Session") } }
        )
    }
    selectedTask?.let { task -> MovementInfoSheet(task = task, onDismiss = { selectedTask = null }) }
    if (showShareSheet) {
        ShareSheet(contentId = workoutId, kind = ShareableContentKind.WORKOUT, onDismiss = { showShareSheet = false })
    }

    // MARK: Body

    Box(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box {
                FLImageView(ref = (workout.thumbnailId ?: workout.imageId)?.let { MediaRef.WorkoutThumbnail(it.toString()) }, height = 250.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
                )
                BackCircleButton(onClick = onBack, modifier = Modifier.padding(top = 60.dp, start = 20.dp))
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (viewModel.isEditable && workout.name.isNotEmpty()) {
                        HeaderCircle(Icons.Filled.People, "Share") { showShareSheet = true }
                        HeaderCircle(Icons.Filled.Edit, "Edit") { onNavigateToEditor(workoutId) }
                        HeaderCircle(Icons.Filled.Delete, "Delete") { showDeleteAlert = true }
                    } else if (workout.name.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable(enabled = !isForking && viewModel.pendingShare == null) { showCustomizeConfirm = true }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isForking) CircularProgressIndicator(color = White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            else Icon(Icons.Filled.Edit, contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
                            Text("Customize", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (workout.sharedByUserId != null && viewModel.pendingShare == null) {
                            HeaderCircle(Icons.Filled.PersonRemove, "Remove from library") { showRemoveAlert = true }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = workout.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (workout.isSharedWithMe) SharedBadge(sharedByName = viewModel.pendingShare?.sharedByUserName)

                viewModel.pendingShare?.let { share ->
                    PendingShareBanner(
                        share = share,
                        kindLabel = "workout",
                        onAccept = { scope.launch { if (!viewModel.respondToShare(workoutId, true)) shareResponseFailed = true } },
                        onDecline = { scope.launch { if (viewModel.respondToShare(workoutId, false)) onBack() else shareResponseFailed = true } }
                    )
                }

                val levels = workout.trainingLevels.ifEmpty { listOf(workout.trainingLevel) }.filter { it.isNotEmpty() }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    levels.forEach { TrainingLevelInfoView(level = it) }
                    if (workout.displayTime.isNotEmpty()) TimeInfoView(time = workout.displayTime)
                    ExercisesInfoView(count = viewModel.exerciseCount)
                    if (workout.location.isNotEmpty()) LocationInfoView(location = workout.location)
                }

                if (workout.description.isNotEmpty()) {
                    Text(text = workout.description, fontSize = 14.sp, color = TextSecondaryColor)
                }

                ContentTagChipRow(title = "Equipment Needed", names = viewModel.equipmentNames)

                WorkoutStructureView(phases = viewModel.phaseSummaries) { summary ->
                    phaseOffsets[summary.anchorId]?.let { y -> scope.launch { scrollState.animateScrollTo(y) } }
                }

                WorkoutTasksView(
                    phases = workout.phases,
                    onTaskTap = { selectedTask = it },
                    onPhasePositioned = { anchorId, y -> phaseOffsets[anchorId] = y }
                )

                val contributor = workout.contributor
                if (contributor != null && !ContributorStore.isHouse(workout.contributorId?.toString())) {
                    CoachCard(contributor)
                }
            }

            Column(modifier = Modifier.fillMaxWidth().background(White)) {
                SeparatorView()
                viewModel.inProgressSession?.let { entry ->
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        SessionResumeCard(
                            entry = entry,
                            onResume = { startSession(entry.lastMode) },
                            onRestart = { showRestartConfirm = true },
                            onDiscard = { showDiscardConfirm = true }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Box(modifier = Modifier.weight(1f).clickable { showDatePicker = true }) {
                        SecondaryButtonView(text = "Add to Calendar")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f).clickable(enabled = viewModel.pendingShare == null) {
                        startSession(viewModel.inProgressSession?.lastMode)
                    }) {
                        PrimaryButtonView(text = if (viewModel.hasSession) "Resume Workout" else "Start Now")
                    }
                }
            }
        }

        if (viewModel.isLoading && workout.name.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().background(BackgroundColor), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = FLPrimary)
                    Text("Loading workout…", color = TextSecondaryColor)
                }
            }
        }
    }
}

@Composable
private fun InfoAlert(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
private fun HeaderCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = White, modifier = Modifier.size(16.dp))
    }
}

/** "About the Coach" contributor card (iOS `ProgramContributorView`). Live-only payload. */
@Composable
private fun CoachCard(contributor: ProgramContributor) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("About the Coach", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FLImageView(
                ref = contributor.avatarMediaAssetId?.let { MediaRef.UserPhoto(it.toString()) },
                modifier = Modifier.size(52.dp).clip(CircleShape)
            )
            Column {
                Text(contributor.displayName ?: "Coach", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                contributor.headline?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = TextSecondaryColor) }
            }
        }
        contributor.bio?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp, color = TextPrimaryColor) }
        contributor.credentials.sortedBy { it.sortOrder }.takeIf { it.isNotEmpty() }?.let { creds ->
            Text(creds.joinToString(" · ") { it.displayText }, fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

// MARK: - Task rows (iOS WorkoutTasksView / WorkoutMovementView / WorkoutRestView / AdvancedMovementsView)

@Composable
fun WorkoutTasksView(
    phases: List<WorkoutPhase>,
    onTaskTap: ((WorkoutTask) -> Unit)? = null,
    onPhasePositioned: ((anchorId: String, y: Int) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        phases.forEachIndexed { index, phase ->
            val anchorId = WorkoutStructure.anchorId(phase.id.ifEmpty { "index-$index" })
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.onGloballyPositioned { coords ->
                    onPhasePositioned?.invoke(anchorId, coords.positionInParent().y.toInt())
                }
            ) {
                Text(
                    text = WorkoutStructure.displayName(phase, index),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FLPrimary
                )
                phase.taskRows.forEach { row ->
                    val task = row.task ?: return@forEach
                    when {
                        task.isMovement && (row.isInterval || row.advancedTasks.isNotEmpty()) -> AdvancedMovementsView(row = row, onTaskTap = onTaskTap)
                        task.isMovement -> WorkoutMovementView(task = task, onClick = onTaskTap?.let { tap -> { tap(task) } })
                        task.isRest -> WorkoutRestView(task = task)
                    }
                }
            }
        }
    }
}

@Composable
fun WorkoutMovementView(task: WorkoutTask, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(White)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FLImageView(
            ref = (task.movementThumbnailId ?: task.movementImageId)?.let { MediaRef.MovementThumbnail(it) },
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp))
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = task.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (!task.isInterval && task.displayMetric.isNotEmpty()) {
                Text(text = task.displayMetric, fontSize = 12.sp, color = TextSecondaryColor)
            }
            SetBreakdownView(task = task)
            val cue = task.instructions
            if (!cue.isNullOrEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(12.dp))
                    Text(text = cue, fontSize = 12.sp, color = TextSecondaryColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (task.rest.isNotEmpty()) {
            Text(text = "Rest ${task.rest}", fontSize = 11.sp, color = TextSecondaryColor)
        }
    }
}

/** Collapsible per-set breakdown for individually-edited sets; renders nothing for uniform sets. */
@Composable
fun SetBreakdownView(task: WorkoutTask, collapseThreshold: Int = 4) {
    if (!task.hasPerSetBreakdown) return
    val configs = task.setConfigs.sortedBy { it.setNumber }
    val unit = if (task.groupId.isEmpty()) "set" else "round"
    var expanded by remember(task.id) { mutableStateOf<Boolean?>(null) }
    val isExpanded = expanded ?: (configs.size <= collapseThreshold)

    val weights = configs.mapNotNull { it.weightKg }.filter { it > 0 }
    val range = if (weights.isEmpty()) null else {
        val lo = weights.min(); val hi = weights.max()
        if (lo == hi) WeightUnit.displayString(fromLbs = lo) else "${WeightUnit.displayNumber(fromLbs = lo)}–${WeightUnit.displayString(fromLbs = hi)}"
    }
    val summary = "${configs.size} $unit${if (configs.size == 1) "" else "s"}" + (range?.let { " · $it" } ?: "")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.clickable { expanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                if (isExpanded) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowRight,
                contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(12.dp)
            )
            Text(text = summary, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
        }
        if (isExpanded) {
            Column(modifier = Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                configs.forEach { config ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${unit.replaceFirstChar { it.uppercase() }} ${config.setNumber}",
                            fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor,
                            modifier = Modifier.width(58.dp)
                        )
                        Text(text = metricText(task, config), fontSize = 11.sp, color = TextSecondaryColor)
                        config.weightKg?.takeIf { it > 0 }?.let {
                            Text(text = WeightUnit.displayString(fromLbs = it), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun metricText(task: WorkoutTask, config: TaskSetConfig): String {
    if (task.usesDuration) {
        val dur = WorkoutTask.humanizeDuration(config.durationSeconds ?: 0)
        if (config.reps <= 0) return dur
        return if (dur.isEmpty()) "${config.reps} reps" else "$dur · ${config.reps} reps"
    }
    return "${config.reps} reps"
}

@Composable
fun WorkoutRestView(task: WorkoutTask, highlighted: Boolean = false, tint: Color = OrangeTheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) tint.copy(alpha = 0.18f) else White)
            .then(if (highlighted) Modifier.border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(8.dp)) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val restThumb = task.restMovementThumbnailId
        if (task.restType == RestType.ACTIVE_RECOVERY && restThumb != null) {
            FLImageView(ref = MediaRef.MovementThumbnail(restThumb), modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)))
        } else {
            Box(
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(ImagePlaceholder),
                contentAlignment = Alignment.Center
            ) {
                Image(painter = painterResource(R.drawable.rest), contentDescription = "Rest", modifier = Modifier.size(30.dp))
            }
        }
        Column {
            val title = when (task.restType) {
                RestType.PASSIVE -> "Passive Rest"
                RestType.ACTIVE_RECOVERY -> task.restMovementName?.takeIf { it.isNotEmpty() }?.let { "Active Recovery: $it" } ?: "Active Recovery"
            }
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = task.rest, fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

/** Grouped row (superset / circuit / interval) with its color-coded container. */
@Composable
fun AdvancedMovementsView(row: TaskRow, onTaskTap: ((WorkoutTask) -> Unit)? = null) {
    val isInterval = (row.task?.isInterval ?: false) || row.isInterval
    val leadIsMovement = row.task?.isMovement ?: false
    val movementCount = (if (leadIsMovement) 1 else 0) + row.advancedTasks.count { it.isMovement }
    val flaggedSuperset = (row.task?.isSuperset ?: false) || row.isSuperset
    val flaggedCircuit = (row.task?.isCircuit ?: false) || row.isCircuit
    val isSuperset = !isInterval && (flaggedSuperset || (!flaggedCircuit && movementCount == 2))
    val isCircuit = !isInterval && (flaggedCircuit || (!flaggedSuperset && movementCount >= 3))
    val accent = when { isSuperset -> PurpleTheme; isCircuit -> OrangeTheme; isInterval -> BlueTheme; else -> FLPrimary }

    fun display(task: WorkoutTask) = task.copy(isInterval = task.isInterval || isInterval)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.07f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(modifier = Modifier.weight(1f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val metrics = if (isInterval) IntervalMetrics.from(row) else null
                if (metrics != null) {
                    IntervalMetricsGrid(metrics = metrics, accent = accent)
                } else {
                    val rounds = row.totalRounds.coerceAtLeast(row.task?.totalRounds ?: 0)
                    val summary = when {
                        isInterval -> ""
                        rounds > 0 -> "$rounds rounds"
                        else -> ""
                    }
                    val restChip = row.task?.roundRestSeconds?.takeIf { (isSuperset || isCircuit) && it > 0 }
                        ?.let { String.format(Locale.US, "%d:%02d rest", it / 60, it % 60) }
                    val badge = when {
                        isInterval -> "INTERVAL"
                        isSuperset -> "SUPERSET"
                        isCircuit -> "CIRCUIT"
                        row.advanced.isNotEmpty() -> row.advanced.uppercase(Locale.US)
                        else -> ""
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (badge.isNotEmpty()) {
                            Text(
                                text = badge, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = White,
                                modifier = Modifier.background(accent, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (summary.isNotEmpty()) Text(summary, fontSize = 12.sp, color = TextSecondaryColor)
                        if (restChip != null) Text("· $restChip", fontSize = 12.sp, color = TextSecondaryColor)
                    }
                }
                row.task?.let { lead -> WorkoutMovementView(task = display(lead), onClick = onTaskTap?.let { tap -> { tap(display(lead)) } }) }
                row.advancedTasks.forEach { t -> WorkoutMovementView(task = display(t), onClick = onTaskTap?.let { tap -> { tap(display(t)) } }) }
                val lead = row.task
                if (isInterval && lead != null && lead.restType == RestType.ACTIVE_RECOVERY && lead.restMovementId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(White).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val thumb = lead.restMovementThumbnailId
                        if (thumb != null) {
                            FLImageView(ref = MediaRef.MovementThumbnail(thumb), modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)))
                        } else {
                            Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(BackgroundColor), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = MediumGray)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "RECOVERY", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = White,
                                modifier = Modifier.background(OrangeTheme, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Text(lead.restMovementName ?: "Recovery", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
