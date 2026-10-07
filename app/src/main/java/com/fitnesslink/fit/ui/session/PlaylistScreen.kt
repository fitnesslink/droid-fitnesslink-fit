package com.fitnesslink.fit.ui.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.AudioCueService
import com.fitnesslink.fit.session.SessionSettings
import com.fitnesslink.fit.session.SummaryRPEMode
import com.fitnesslink.fit.session.VoicePhrase
import com.fitnesslink.fit.ui.components.BackCircleButton
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.TimeInfoView
import com.fitnesslink.fit.ui.components.TrainingLevelInfoView
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.ui.workout.CreateExerciseSheet
import com.fitnesslink.fit.ui.workout.ExerciseBrowserSheet
import com.fitnesslink.fit.viewmodel.PlaylistEditRequest
import com.fitnesslink.fit.viewmodel.PlaylistViewModel
import com.fitnesslink.fit.viewmodel.SessionWorkoutEditor
import kotlinx.coroutines.delay

/**
 * Playlist mode (port of iOS `PlaylistView`): starts / resumes the shared session, whole-workout
 * timer, per-set rings, log sheet, group containers, row menu (Do → Build), footer add, Interactive
 * hand-off, Finish with the finish rule, ⋯ Discard, completion overlay with RPE.
 */
@Composable
fun PlaylistScreen(
    workoutId: String,
    onBack: () -> Unit,
    onNavigateToInteractive: (String) -> Unit = {},
    programId: String? = null,
    viewModel: PlaylistViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    @Suppress("UNUSED_VARIABLE") val tick = viewModel.tick
    var pickerRequest by remember { mutableStateOf<PlaylistEditRequest?>(null) }
    var createRequest by remember { mutableStateOf<PlaylistEditRequest?>(null) }
    var setsTarget by remember { mutableStateOf<WorkoutTask?>(null) }
    var removeTarget by remember { mutableStateOf<WorkoutTask?>(null) }
    var showSaveVersionConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var pendingScope by remember { mutableStateOf<Pair<PlaylistEditRequest, MovementLibraryItem?>?>(null) }
    var savedVersionMessage by remember { mutableStateOf<String?>(null) }
    var videoTask by remember { mutableStateOf<WorkoutTask?>(null) }
    var infoTask by remember { mutableStateOf<WorkoutTask?>(null) }
    var highlightInteractive by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) {
        AudioCueService.attach(context)
        SessionSettings.attach(context)
        viewModel.loadData(workoutId, programId)
        if (viewModel.shouldReopenInteractive) {
            viewModel.shouldReopenInteractive = false
            delay(50)
            onNavigateToInteractive(workoutId)
        } else if (!SessionSettings.didAnnounceInteractiveModeHint) {
            SessionSettings.didAnnounceInteractiveModeHint = true
            delay(800)
            highlightInteractive = true
            AudioCueService.speak(VoicePhrase.InteractiveModeHint)
            delay(9_000)
            highlightInteractive = false
        }
    }
    // Coming back from Interactive: re-read the cache (edits made there) and record the playlist mode.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { viewModel.reload(workoutId); viewModel.recordPlaylistMode() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { viewModel.onEditRequest = { request -> handlePlaylistEdit(request, viewModel, onPicker = { pickerRequest = it }, onSets = { setsTarget = it }, onRemove = { removeTarget = it }, onSaveVersion = { showSaveVersionConfirm = true }) } }

    fun goHome() { if (AppRouter.resetStackHandler != null) AppRouter.resetStack(AppRouter.HOME_TAB) else onBack() }

    fun apply(request: PlaylistEditRequest, movement: MovementLibraryItem?, scope: SessionWorkoutEditor.Scope) {
        when (request) {
            is PlaylistEditRequest.Add -> movement?.let { viewModel.addExercise(it, request.afterTaskId, scope) }
            is PlaylistEditRequest.AddToPhase -> movement?.let { viewModel.addExerciseToPhase(it, toPhaseId = request.phaseId, scope = scope) }
            is PlaylistEditRequest.Swap -> movement?.let { viewModel.swapMovement(request.taskId, it, scope) }
            is PlaylistEditRequest.Remove -> viewModel.removeExercise(request.taskId, scope)
            is PlaylistEditRequest.EditSets, PlaylistEditRequest.SaveMyVersion -> Unit
        }
    }

    fun movementPicked(movement: MovementLibraryItem, request: PlaylistEditRequest) {
        if (viewModel.isWorkoutEditable) apply(request, movement, SessionWorkoutEditor.Scope.PERMANENT) else pendingScope = request to movement
    }

    if (viewModel.isWorkoutComplete) {
        WorkoutCompletedScreen(
            sessionId = viewModel.sessionId, workoutName = viewModel.workout.name, stats = viewModel.summaryStats,
            achievements = viewModel.summaryAchievements, rpeMode = SummaryRPEMode.Editable,
            onComplete = { rpe -> viewModel.finish(rpe); goHome() },
            onDismiss = { viewModel.finish(null); goHome() }
        )
        return
    }

    Box(Modifier.fillMaxSize().background(BackgroundColor)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Hero(viewModel, highlightInteractive, onBack) { onNavigateToInteractive(workoutId) }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (viewModel.isLoadingWorkout && viewModel.workout.phases.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = FLPrimary); Text("Loading workout…", color = TextSecondaryColor, fontSize = 14.sp)
                    }
                } else {
                    PlaylistTasks(viewModel, onPlayVideo = { videoTask = it }, onInfo = { infoTask = it })
                }
                Spacer(Modifier.height(100.dp))
            }
        }
        FinishBar(viewModel, Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 12.dp)) { showDiscardConfirm = true }
    }

    videoTask?.let { task -> MovementVideoCover(task) { videoTask = null } }
    infoTask?.let { task -> MovementInfoSheet(task) { infoTask = null } }
    viewModel.logTargetTaskId?.let { id ->
        val task = viewModel.workout.allTasks.firstOrNull { it.id == id }
        if (task != null) PlaylistMovementLogSheet(viewModel, task, viewModel.logTargetSetNumber) { viewModel.closeLogSheet() } else viewModel.closeLogSheet()
    }
    pickerRequest?.let { request ->
        ExerciseBrowserSheet(
            onSelect = { movement -> pickerRequest = null; movementPicked(movement, request) },
            onCreateNew = { pickerRequest = null; createRequest = request },
            onDismiss = { pickerRequest = null }, pendingGroupType = null, pendingCount = 0
        )
    }
    createRequest?.let { request -> CreateExerciseSheet(onCreated = { movement -> createRequest = null; movementPicked(movement, request) }, onDismiss = { createRequest = null }) }
    setsTarget?.let { task ->
        SessionSetsSheet(maxOf(task.sets, 1), task.reps, task.name, onSave = { s, r -> viewModel.updateSets(task.id, s, r) }, onDismiss = { setsTarget = null })
    }
    removeTarget?.let { task ->
        ConfirmDialog("Remove this exercise?", if (viewModel.isWorkoutEditable) "${task.name} will be removed from this workout." else "${task.name} will be skipped for this session.", "Remove", destructive = true,
            onConfirm = { removeTarget = null; if (viewModel.isWorkoutEditable) viewModel.removeExercise(task.id) else pendingScope = PlaylistEditRequest.Remove(task.id) to null },
            onDismiss = { removeTarget = null })
    }
    pendingScope?.let { (request, movement) ->
        EditScopeDialog(
            onSessionOnly = { apply(request, movement, SessionWorkoutEditor.Scope.SESSION_ONLY); pendingScope = null },
            onSessionAndSave = { apply(request, movement, SessionWorkoutEditor.Scope.SESSION_ONLY); pendingScope = null; if (viewModel.saveMyVersion() != null) savedVersionMessage = "Saved to My Workouts. This session keeps running on the original." },
            onDismiss = { pendingScope = null }
        )
    }
    if (showSaveVersionConfirm) ConfirmDialog("Save as my workout?", "Creates a personal copy of ${viewModel.workout.name} with your session changes.", "Save",
        onConfirm = { showSaveVersionConfirm = false; if (viewModel.saveMyVersion() != null) savedVersionMessage = "Saved to My Workouts. This session keeps running on the original." }, onDismiss = { showSaveVersionConfirm = false })
    savedVersionMessage?.let { msg -> ConfirmDialog("Saved", msg, "OK", onConfirm = { savedVersionMessage = null }, onDismiss = { savedVersionMessage = null }) }
    if (viewModel.showFinishConfirmation) FinishConfirmationDialog(viewModel.finishConfirmationMessage, onFinishAnyway = { viewModel.finishAnyway() }, onContinue = { viewModel.continueWorkout() })
    if (showDiscardConfirm) SessionDiscardDialog(viewModel.workout.name, onConfirm = { showDiscardConfirm = false; viewModel.discardSession { goHome() } }, onDismiss = { showDiscardConfirm = false })
}

private fun handlePlaylistEdit(
    request: PlaylistEditRequest, viewModel: PlaylistViewModel,
    onPicker: (PlaylistEditRequest) -> Unit, onSets: (WorkoutTask) -> Unit, onRemove: (WorkoutTask) -> Unit, onSaveVersion: () -> Unit
) {
    fun task(id: String) = viewModel.workout.allTasks.firstOrNull { it.id == id }
    when (request) {
        is PlaylistEditRequest.Add, is PlaylistEditRequest.AddToPhase, is PlaylistEditRequest.Swap -> onPicker(request)
        is PlaylistEditRequest.EditSets -> task(request.taskId)?.let(onSets)
        is PlaylistEditRequest.Remove -> task(request.taskId)?.let(onRemove)
        PlaylistEditRequest.SaveMyVersion -> onSaveVersion()
    }
}

@Composable
private fun Hero(viewModel: PlaylistViewModel, highlightInteractive: Boolean, onBack: () -> Unit, onInteractive: () -> Unit) {
    val workout = viewModel.workout
    Box(Modifier.fillMaxWidth().height(370.dp)) {
        FLImageView(ref = (workout.thumbnailId ?: workout.imageId)?.let { MediaRef.WorkoutThumbnail(it.toString()) }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BackCircleButton(onClick = onBack)
                Spacer(Modifier.weight(1f))
                Text(viewModel.timerText, color = White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 16.dp, vertical = 8.dp))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(White).clickable { onInteractive() }
                        .then(if (highlightInteractive) Modifier.border(3.dp, FLPrimary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) { Image(painterResource(R.drawable.interactive), contentDescription = "Interactive mode", modifier = Modifier.size(14.dp)) }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 25.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(workout.name, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TrainingLevelInfoView(level = workout.trainingLevel)
                    if (workout.displayTime.isNotEmpty()) TimeInfoView(time = workout.displayTime)
                }
            }
        }
    }
}

@Composable
private fun PlaylistTasks(viewModel: PlaylistViewModel, onPlayVideo: (WorkoutTask) -> Unit, onInfo: (WorkoutTask) -> Unit) {
    val workout = viewModel.workout
    var addMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (workout.description.isNotEmpty()) {
            Text("Description", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Text(workout.description, fontSize = 14.sp, color = TextPrimaryColor)
        }
        workout.phases.forEach { phase ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(phase.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.padding(top = 10.dp))
                phase.taskRows.forEach { row ->
                    val task = row.task
                    when {
                        row.isInterval || row.advancedTasks.isNotEmpty() -> PlaylistGroupContainer(row, viewModel, onPlayVideo, onInfo)
                        task?.isMovement == true -> PlaylistMovementRow(task, viewModel, onPlayVideo = onPlayVideo, onInfo = onInfo)
                        task?.isRest == true -> PlaylistRestRow(task)
                    }
                }
            }
        }
        if (viewModel.onEditRequest != null) {
            Box(Modifier.padding(top = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(FLPrimary.copy(alpha = 0.08f))
                        .clickable { if (workout.phases.size > 1) addMenu = true else viewModel.requestEdit(PlaylistEditRequest.Add(null)) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.AddCircle, contentDescription = null, tint = FLPrimary)
                    Text("Add exercise", color = FLPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    workout.phases.forEach { phase -> DropdownMenuItem(text = { Text("Add to ${phase.name}") }, onClick = { addMenu = false; viewModel.requestEdit(PlaylistEditRequest.AddToPhase(phase.id)) }) }
                }
            }
            if (!viewModel.isWorkoutEditable) {
                Row(Modifier.fillMaxWidth().clickable { viewModel.requestEdit(PlaylistEditRequest.SaveMyVersion) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Save, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(18.dp))
                    Text(if (viewModel.hasSessionOnlyEdits) "Save my changes as my workout" else "Save as my workout", color = TextSecondaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("Changes to a coach's workout apply to this session only.", color = TextSecondaryColor, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp))
            }
        }
    }
}

@Composable
private fun FinishBar(viewModel: PlaylistViewModel, modifier: Modifier, onDiscard: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(FLPrimary).clickable { viewModel.requestFinish() }.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Flag, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Finish Workout", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Box {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(White).clickable { menu = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Session options", tint = TextPrimaryColor)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Discard Session", color = Color(0xFFE53935)) }, onClick = { menu = false; onDiscard() })
            }
        }
    }
}

/** Fullscreen movement video (loop, no controls, audible) with the task name + metric. */
@Composable
private fun MovementVideoCover(task: WorkoutTask, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val videoId = task.effectiveMovementVideoId
            if (videoId != null) {
                MuxVideoPlayer(
                    ref = MediaRef.MovementVideo(videoId), modifier = Modifier.fillMaxSize(), loop = true, fill = true, muted = false,
                    videoStatus = task.effectiveMovementVideoStatus?.let { com.fitnesslink.fit.media.MuxStatus.valueOf(it.name) },
                    posterRef = task.movementThumbnailId?.let { MediaRef.MovementThumbnail(it) }
                )
            }
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))).statusBarsPadding().padding(20.dp)) {
                Text(task.name, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (!task.isInterval) Text(task.displayMetric, color = White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(32.dp).clickable { onDismiss() })
        }
    }
}
