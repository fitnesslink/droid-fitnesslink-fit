package com.fitnesslink.fit.ui.session

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.IntervalPhase
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.AudioCueService
import com.fitnesslink.fit.session.IntervalRunner
import com.fitnesslink.fit.session.SessionSettings
import com.fitnesslink.fit.session.SummaryRPEMode
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.ui.workout.CreateExerciseSheet
import com.fitnesslink.fit.ui.workout.ExerciseBrowserSheet
import com.fitnesslink.fit.viewmodel.InteractiveSessionViewModel
import com.fitnesslink.fit.viewmodel.SessionEditAction
import com.fitnesslink.fit.viewmodel.SessionWorkoutEditor

/**
 * Interactive Mode (port of iOS `InteractiveSessionView`): full-bleed demo video with the prep /
 * rest overlay, the info strip or interval strip, the transport bar and the dock. The completion
 * screen renders exclusively once the workout ends so the player unmounts.
 */
@Composable
fun InteractiveSessionScreen(
    workoutId: String,
    onBack: () -> Unit,
    programId: String? = null,
    viewModel: InteractiveSessionViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    @Suppress("UNUSED_VARIABLE") val tick = viewModel.tick

    var showMovementInfo by remember { mutableStateOf(false) }
    var editAction by remember { mutableStateOf<SessionEditAction?>(null) }
    var createAction by remember { mutableStateOf<SessionEditAction?>(null) }
    var pendingMovement by remember { mutableStateOf<MovementLibraryItem?>(null) }
    var pendingScopeAction by remember { mutableStateOf<SessionEditAction?>(null) }
    var showSetsSheet by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showSaveVersionConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var savedVersionMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(workoutId) {
        AudioCueService.attach(context)
        SessionSettings.attach(context)
        viewModel.loadData(workoutId, programId)
    }
    LaunchedEffect(viewModel.workoutNotFound) { if (viewModel.workoutNotFound) onBack() }

    // Keep the screen awake while the workout runs; paused sessions may auto-lock.
    DisposableEffect(viewModel.isVideoPaused, viewModel.isWorkoutComplete) {
        view.keepScreenOn = !viewModel.isVideoPaused && !viewModel.isWorkoutComplete
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME && !viewModel.isWorkoutComplete) viewModel.resyncClocks() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); viewModel.stop() }
    }

    fun goHome() { if (AppRouter.resetStackHandler != null) AppRouter.resetStack(AppRouter.HOME_TAB) else onBack() }

    fun applyEdit(action: SessionEditAction, movement: MovementLibraryItem?, scope: SessionWorkoutEditor.Scope) {
        when (action) {
            SessionEditAction.AddAfterCurrent -> movement?.let { viewModel.addExercise(it, InteractiveSessionViewModel.AddPlacement.AfterCurrent, scope) }
            SessionEditAction.AddAtEnd -> movement?.let { viewModel.addExercise(it, InteractiveSessionViewModel.AddPlacement.End, scope) }
            is SessionEditAction.AddToPhase -> movement?.let { viewModel.addExercise(it, InteractiveSessionViewModel.AddPlacement.Phase(action.phaseId), scope) }
            SessionEditAction.Swap -> movement?.let { viewModel.swapCurrentMovement(it, scope) }
            SessionEditAction.Remove -> viewModel.removeCurrentExercise(scope)
            SessionEditAction.EditSets, SessionEditAction.SaveMyVersion -> Unit
        }
    }

    fun movementPicked(movement: MovementLibraryItem, action: SessionEditAction) {
        if (viewModel.isWorkoutEditable) applyEdit(action, movement, SessionWorkoutEditor.Scope.PERMANENT)
        else { pendingMovement = movement; pendingScopeAction = action }
    }

    if (viewModel.isWorkoutComplete) {
        WorkoutCompletedScreen(
            sessionId = viewModel.sessionId,
            workoutName = viewModel.workoutName,
            stats = viewModel.summaryStats,
            achievements = viewModel.summaryAchievements,
            rpeMode = SummaryRPEMode.Editable,
            onComplete = { rpe -> viewModel.saveSession(rpe); goHome() },
            onDismiss = { viewModel.saveSession(null); goHome() }
        )
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (viewModel.isLoadingWorkout && viewModel.workoutTask.id.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = White)
                Text("Loading workout…", color = White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }
        } else {
            SessionContent(viewModel, onInfo = { showMovementInfo = true }, onEdit = { action ->
                when (action) {
                    SessionEditAction.EditSets -> showSetsSheet = true
                    SessionEditAction.Remove -> showRemoveConfirm = true
                    SessionEditAction.SaveMyVersion -> showSaveVersionConfirm = true
                    else -> editAction = action
                }
            })
        }

        SessionTopBar(
            timerText = viewModel.timerText,
            onPlaylist = onBack,
            isMuted = viewModel.isMuted,
            onToggleMute = { viewModel.setMasterMuted(!viewModel.isMuted) },
            audioChannels = listOf(
                SessionAudioChannel("video", "Exercise Video Audio", viewModel.isVideoAudioEnabled) { viewModel.setVideoAudioEnabled(!viewModel.isVideoAudioEnabled) },
                SessionAudioChannel("voice", "Voice Cues", SessionSettings.intervalVoiceCuesEnabled) { SessionSettings.intervalVoiceCuesEnabled = !SessionSettings.intervalVoiceCuesEnabled },
                SessionAudioChannel("beeps", "Countdown Beeps", SessionSettings.intervalCuesEnabled) { SessionSettings.intervalCuesEnabled = !SessionSettings.intervalCuesEnabled }
            ),
            onDiscard = { showDiscardConfirm = true },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }

    if (viewModel.showLogSheet) SetLogSheet(viewModel) { viewModel.showLogSheet = false }
    if (showMovementInfo) MovementInfoSheet(viewModel.workoutTask) { showMovementInfo = false }
    editAction?.let { action ->
        ExerciseBrowserSheet(
            onSelect = { movement -> editAction = null; movementPicked(movement, action) },
            onCreateNew = { editAction = null; createAction = action },
            onDismiss = { editAction = null },
            pendingGroupType = null, pendingCount = 0
        )
    }
    createAction?.let { action ->
        CreateExerciseSheet(onCreated = { movement -> createAction = null; movementPicked(movement, action) }, onDismiss = { createAction = null })
    }
    if (showSetsSheet) {
        SessionSetsSheet(maxOf(viewModel.workoutTask.sets, 1), viewModel.workoutTask.reps, viewModel.workoutTask.name,
            onSave = { s, r -> viewModel.updateCurrentSets(s, r, if (viewModel.isWorkoutEditable) SessionWorkoutEditor.Scope.PERMANENT else SessionWorkoutEditor.Scope.SESSION_ONLY) },
            onDismiss = { showSetsSheet = false })
    }
    val scopeAction = pendingScopeAction
    if (scopeAction != null) {
        EditScopeDialog(
            onSessionOnly = { applyEdit(scopeAction, pendingMovement, SessionWorkoutEditor.Scope.SESSION_ONLY); pendingMovement = null; pendingScopeAction = null },
            onSessionAndSave = {
                applyEdit(scopeAction, pendingMovement, SessionWorkoutEditor.Scope.SESSION_ONLY); pendingMovement = null; pendingScopeAction = null
                if (viewModel.saveMyVersion() != null) savedVersionMessage = "Saved to My Workouts. This session keeps running on the original."
            },
            onDismiss = { pendingMovement = null; pendingScopeAction = null }
        )
    }
    if (showRemoveConfirm) {
        ConfirmDialog("Remove this exercise?",
            if (viewModel.isWorkoutEditable) "${viewModel.workoutTask.name} will be removed from this workout." else "${viewModel.workoutTask.name} will be skipped for this session.",
            "Remove", destructive = true,
            onConfirm = {
                showRemoveConfirm = false
                if (viewModel.isWorkoutEditable) viewModel.removeCurrentExercise(SessionWorkoutEditor.Scope.PERMANENT) else pendingScopeAction = SessionEditAction.Remove
            },
            onDismiss = { showRemoveConfirm = false })
    }
    if (showSaveVersionConfirm) {
        ConfirmDialog("Save as my workout?", "Creates a personal copy of ${viewModel.workoutName} with your session changes.", "Save",
            onConfirm = { showSaveVersionConfirm = false; if (viewModel.saveMyVersion() != null) savedVersionMessage = "Saved to My Workouts. This session keeps running on the original." },
            onDismiss = { showSaveVersionConfirm = false })
    }
    savedVersionMessage?.let { msg -> ConfirmDialog("Saved", msg, "OK", onConfirm = { savedVersionMessage = null }, onDismiss = { savedVersionMessage = null }) }
    if (viewModel.showFinishConfirmation) FinishConfirmationDialog(viewModel.finishConfirmationMessage, onFinishAnyway = { viewModel.finishAnyway() }, onContinue = { viewModel.continueWorkout() })
    if (showDiscardConfirm) SessionDiscardDialog(viewModel.workoutName, onConfirm = { showDiscardConfirm = false; viewModel.discardSession { goHome() } }, onDismiss = { showDiscardConfirm = false })
}

@Composable
private fun SessionContent(viewModel: InteractiveSessionViewModel, onInfo: () -> Unit, onEdit: (SessionEditAction) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        VideoLayer(viewModel)
        if (viewModel.shouldHoldVideo && !viewModel.isRest) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(420.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
        }
        if (viewModel.isVideoPaused) PausedBadge(Modifier.align(Alignment.TopCenter).padding(top = 118.dp))

        if (viewModel.isRest) {
            Box(Modifier.fillMaxSize().padding(top = 96.dp, bottom = 300.dp).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                RestTextView(
                    task = viewModel.workoutTask,
                    isTransientRest = viewModel.activeRestKind != null,
                    isGetReady = viewModel.isPreparing,
                    upNext = viewModel.upNext,
                    prescription = if (viewModel.isPreparing) viewModel.preparationPrescription else null,
                    coachingCue = if (viewModel.isPreparing) viewModel.workoutTask.instructions else null,
                    phaseChange = if (viewModel.isPreparing) viewModel.pendingPhaseChange else viewModel.restPhaseChange,
                    block = viewModel.preparationBlock,
                    secondsRemaining = viewModel.countdownSecondsRemaining,
                    totalSeconds = viewModel.countdownTotalSeconds,
                    phaseName = if (viewModel.isPreparing) viewModel.currentPhaseName else null,
                    structureLabel = viewModel.preparationStructureLabel,
                    upNextStructureLabel = if (viewModel.isPreparing) null else viewModel.restUpNextStructureLabel,
                    upNextPrescription = if (viewModel.isPreparing) null else viewModel.restUpNextPrescription,
                    upNextNote = if (viewModel.isPreparing) null else viewModel.restUpNextNote
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).padding(bottom = 34.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            viewModel.phaseLabelText?.let { phase ->
                Text(phase, color = White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 5.dp))
            }
            val runner = viewModel.intervalRunner
            if (!viewModel.isPreparing) {
                if (runner != null) IntervalOverlayView(runner, viewModel.tick) { viewModel.resyncClocks() }
                else if (!viewModel.isRest) SessionInfoStrip(viewModel, onInfo, onEdit)
            }
            val upNext = viewModel.upNext
            if (viewModel.showsUpNextCard && upNext != null) UpNextCard(upNext) { viewModel.getNextExercise() }
            SessionTransportBar(
                isPaused = viewModel.isVideoPaused,
                canSkipSet = viewModel.canSkipSet,
                canSkipExercise = viewModel.canSkipExercise,
                skipExerciseLabel = viewModel.skipExerciseLabel,
                showSkipBlock = runner != null,
                onPrevious = { viewModel.getPrevious() },
                onTogglePause = { viewModel.toggleVideoState() },
                onSkipSet = { viewModel.skipCurrentSet() },
                onSkipExercise = { viewModel.skipCurrentExercise() },
                onSkipBlock = { viewModel.exitIntervalGroup() },
                onNext = { viewModel.getNext() }
            )
            if (runner == null) {
                SessionDockView(
                    isPreparing = viewModel.isPreparing, isRest = viewModel.isRest, isMovement = viewModel.workoutTask.isMovement,
                    logLabel = viewModel.logSetCTALabel, isLogged = viewModel.currentSetIsLogged, hasUpNext = upNext != null,
                    onStartNow = { viewModel.startWork() }, onSkipRest = { viewModel.getNext() }, onLogSet = { viewModel.showLogSheet = true },
                    onContinue = { viewModel.getNext() }, onFinish = { viewModel.getNext() }
                )
            }
        }
    }
}

/** Full-bleed background: the demo video (held per `shouldHoldVideo`), the rest thumbnail, or the recovery movement. */
@Composable
private fun VideoLayer(viewModel: InteractiveSessionViewModel) {
    val runner = viewModel.intervalRunner
    val task = viewModel.workoutTask
    if (runner != null) {
        val lead = runner.leadTask
        val recovery = runner.phase == IntervalPhase.REST && lead?.restType == RestType.ACTIVE_RECOVERY
        val recoveryVideo = lead?.effectiveRestMovementVideoId
        val exercise = runner.currentExercise
        when {
            recovery && lead != null && recoveryVideo != null && lead.isEffectiveRestMovementVideoReady ->
                MovementPlayer(recoveryVideo, lead.effectiveRestMovementVideoStatus, lead.restMovementThumbnailId, paused = viewModel.isVideoPaused, muted = viewModel.isVideoMuted)
            recovery && lead?.restMovementThumbnailId != null -> FLImageView(ref = MediaRef.MovementThumbnail(lead.restMovementThumbnailId), modifier = Modifier.fillMaxSize())
            exercise?.effectiveMovementVideoId != null ->
                MovementPlayer(exercise.effectiveMovementVideoId!!, exercise.effectiveMovementVideoStatus, exercise.movementThumbnailId, paused = viewModel.shouldHoldVideo, muted = viewModel.isVideoMuted)
            else -> Box(Modifier.fillMaxSize().background(Color(0xFF1F2937)))
        }
        return
    }
    if (viewModel.isRest && !viewModel.isPreparing) {
        FLImageView(ref = viewModel.restBackgroundMediaId?.let { MediaRef.MovementThumbnail(it) }, modifier = Modifier.fillMaxSize())
        return
    }
    val videoId = task.effectiveMovementVideoId
    if (videoId != null) MovementPlayer(videoId, task.effectiveMovementVideoStatus, task.movementThumbnailId, paused = viewModel.shouldHoldVideo, muted = viewModel.isVideoMuted)
    else Box(Modifier.fillMaxSize().background(Color(0xFF1F2937)))
}

@Composable
private fun MovementPlayer(videoId: String, status: com.fitnesslink.fit.model.api.MuxStatus?, thumbnailId: String?, paused: Boolean, muted: Boolean) {
    MuxVideoPlayer(
        ref = MediaRef.MovementVideo(videoId),
        modifier = Modifier.fillMaxSize(),
        loop = true, fill = true, muted = muted, paused = paused,
        videoStatus = status?.let { com.fitnesslink.fit.media.MuxStatus.valueOf(it.name) },
        posterRef = thumbnailId?.let { MediaRef.MovementThumbnail(it) }
    )
}

/** Slim translucent info card above the dock: meter row, identity row, target, previous weight, position, note. */
@Composable
private fun SessionInfoStrip(viewModel: InteractiveSessionViewModel, onInfo: () -> Unit, onEdit: (SessionEditAction) -> Unit) {
    val task = viewModel.workoutTask
    val isGrouped = task.isSuperset || task.isCircuit || task.isInterval
    val previous = remember(task.id, viewModel.actualSetLogs[task.id]?.size) { viewModel.previousWeight }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isGrouped) WorkoutControlTopView(task, viewModel.currentRound)
            else viewModel.setHeadlineText?.let { Text(it, color = White, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1) }
            Spacer(Modifier.weight(1f))
            if (task.isMovement) SetTimerLabel(viewModel.setTimerText, viewModel.setTimerCountsDown)
            SessionEditMenu(viewModel.phaseOptions, task.isMovement, viewModel.canSwapCurrentMovement, viewModel.canEditCurrentSets, viewModel.canRemoveCurrentExercise,
                viewModel.isWorkoutEditable, viewModel.hasSessionOnlyEdits, onEdit)
        }
        if (task.isMovement) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(task.name, color = White, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (task.movementId != null) Icon(Icons.Filled.Info, contentDescription = "Movement info", tint = White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp).clickable { onInfo() })
            }
            viewModel.currentTargetText?.let { Text(it, color = OrangeTheme, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1) }
            previous?.let { Text(it.previousLine, color = White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
        val progress = viewModel.workoutProgressText
        if (progress.isNotEmpty()) Text(progress, color = White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (task.isMovement && !task.instructions.isNullOrBlank()) {
            HorizontalDivider(color = White.copy(alpha = 0.2f))
            SessionNoteCard(task.instructions, label = "NOTES", maxLines = 2, isBordered = false)
        }
    }
}
