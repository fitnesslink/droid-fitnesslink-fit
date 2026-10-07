package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.DistanceRule
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.IntervalPhase
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutDurationEstimator
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.session.ActiveWorkoutSession
import com.fitnesslink.fit.session.AudioCueService
import com.fitnesslink.fit.session.IntervalRunner
import com.fitnesslink.fit.session.MovementHistoryStore
import com.fitnesslink.fit.session.MovementPreviousWeight
import com.fitnesslink.fit.session.PhaseChangeCue
import com.fitnesslink.fit.session.PreparationBlockSummary
import com.fitnesslink.fit.session.SessionAchievements
import com.fitnesslink.fit.session.SessionAchievementsBuilder
import com.fitnesslink.fit.session.SessionCueEvent
import com.fitnesslink.fit.session.SessionCuePhrasing
import com.fitnesslink.fit.session.SessionFinishRule
import com.fitnesslink.fit.session.SessionHistoryRows
import com.fitnesslink.fit.session.SessionMode
import com.fitnesslink.fit.session.SessionPrescription
import com.fitnesslink.fit.session.SessionProgress
import com.fitnesslink.fit.session.SessionRecorder
import com.fitnesslink.fit.session.SessionSettings
import com.fitnesslink.fit.session.SessionTransitionEngine
import com.fitnesslink.fit.session.SessionTransitionEngine.Cursor
import com.fitnesslink.fit.session.SessionTransitionEngine.PhaseChange
import com.fitnesslink.fit.session.SessionTransitionEngine.RestKind
import com.fitnesslink.fit.session.SessionTransitionEngine.Transition
import com.fitnesslink.fit.session.SessionWorkoutRefresh
import com.fitnesslink.fit.session.SetState
import com.fitnesslink.fit.session.UpNextDescriptor
import com.fitnesslink.fit.session.WorkoutSummaryStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Interactive Mode state machine — port of iOS `InteractiveSessionViewModel`. Exactly one of
 * these is live at a time: a transient rest ([activeRestKind] + [afterRest]), the preparation
 * screen ([isPreparing]), an interval runner, or work. The pure engine decides every transition;
 * this class applies it and owns the clocks (one 250 ms ticker recomputing from wall-clock anchors).
 */
/** Plays a preparation cue and calls back when its voice is done. */
typealias PrepCuePlayer = (SessionCueEvent, () -> Unit) -> Unit

class InteractiveSessionViewModel : ViewModel() {

    private var workoutTasks: List<WorkoutTask> = emptyList()
    private var position = 0
    private var currentSet = 1
    private var workoutId: String = ""

    /** Injectable wall clock (millis). */
    var clock: () -> Long = { System.currentTimeMillis() }

    private val session: ActiveWorkoutSession get() = ActiveWorkoutSession

    // MARK: - Observable state

    /** Bumped by the ticker and on every mutation so composables re-read the derived getters. */
    var tick by mutableIntStateOf(0)
        private set
    var workoutTask by mutableStateOf(WorkoutTask())
        private set
    var currentRound by mutableIntStateOf(1)
        private set
    var isRest by mutableStateOf(false)
        private set
    var isPreparing by mutableStateOf(false)
        private set
    var pendingPhaseChange by mutableStateOf<PhaseChange?>(null)
        private set
    var isAwaitingPrepCue by mutableStateOf(false)
        private set
    var isVideoPaused by mutableStateOf(false)
        private set
    var isMuted by mutableStateOf(false)
        private set
    var isWorkoutComplete by mutableStateOf(false)
        private set
    var showLogSheet by mutableStateOf(false)
    var showFinishConfirmation by mutableStateOf(false)
        private set
    var workoutName by mutableStateOf("")
        private set
    var workoutNotFound by mutableStateOf(false)
        private set
    var isLoadingWorkout by mutableStateOf(false)
        private set
    var intervalRunner: IntervalRunner? = null
        private set
    var phaseOptions by mutableStateOf<List<PhaseOption>>(emptyList())
        private set
    var isWorkoutEditable by mutableStateOf(false)
        private set
    var summaryAchievements by mutableStateOf(SessionAchievements())
        private set
    var completedElapsedSeconds: Int? = null
        private set

    val timerText: String get() = session.timerText
    val sessionId: String get() = session.sessionId
    val startDateMs: Long get() = session.startDateMs
    val secondsElapsed: Int get() = session.secondsElapsed
    val actualSetLogs: Map<String, Map<Int, TaskSetConfig>> get() = session.actualSetLogs
    val completedTaskIds: Set<String> get() = session.completedTaskIds

    // MARK: - Transient rest / prep

    var activeRestKind: RestKind? = null
        private set
    private var afterRest: Transition? = null
    val isRoundRest: Boolean get() = activeRestKind == RestKind.BETWEEN_ROUNDS
    val isSetRest: Boolean get() = activeRestKind == RestKind.BETWEEN_SETS
    val isMemberRest: Boolean get() = activeRestKind == RestKind.BETWEEN_MEMBERS

    /** Length of the preparation countdown. 0 disables the prep screen (tests). */
    var preparationSeconds = preWorkoutCountdownSeconds

    var prepCuePlayer: PrepCuePlayer = { event, done -> AudioCueService.play(event); done() }
    private var prepCueToken: String? = null
    private var prepCueHoldJob: Job? = null
    private var announceModeEntry = false

    // Rest / prep countdown (wall-clock anchored)
    private var restSecondsRemaining = 0
    private var restTotalSeconds = 0
    private var restEndMs: Long? = null
    private var restPausedRemainingMs: Long? = null
    private var restRunning = false
    var rest by mutableStateOf("00:00")
        private set

    // Set timer
    var setTimerSeconds by mutableIntStateOf(0)
        private set
    var setTimerElapsedSeconds = 0
        private set
    private var setRunning = false
    private var setAnchorMs: Long? = null
    private var setAccumulatedMs: Long = 0
    private var timedSetKey = ""
    private var lastSeenGroupId = ""
    private var blockFrozenBehindPrep = false
    private var tickerJob: Job? = null

    private val engine: SessionTransitionEngine get() = SessionTransitionEngine(workoutTasks)
    private val cursor: Cursor get() = Cursor(position, currentSet, currentRound)
    val currentSetNumber: Int get() = currentSet
    private val currentIteration: Int get() = if (workoutTask.groupId.isEmpty()) currentSet else currentRound
    val setTimerText: String get() = formatRestTime(setTimerSeconds)
    val setTimerTargetSeconds: Int? get() = timedSetSeconds(workoutTask, currentIteration)
    val setTimerCountsDown: Boolean get() = setTimerTargetSeconds != null
    val isVideoAudioEnabled: Boolean get() = SessionSettings.sessionVideoAudioEnabled
    val isVideoMuted: Boolean get() = isMuted || !isVideoAudioEnabled

    fun setVideoAudioEnabled(enabled: Boolean) { SessionSettings.sessionVideoAudioEnabled = enabled; bump() }
    fun setMasterMuted(muted: Boolean) {
        isMuted = muted
        AudioCueService.isMasterMuted = muted
        if (muted) AudioCueService.silenceVoice()
    }

    // MARK: - Derived display

    val preparationPrescription: String? get() = SessionPrescription.line(workoutTask, currentGroupRange()?.let { engine.groupRounds(it) })
    val preparationBlock: PreparationBlockSummary? get() = if (isPreparing && workoutTasks.isNotEmpty()) SessionPrescription.block(workoutTasks, position) else null
    val countdownSecondsRemaining: Int get() = maxOf(restSecondsRemaining, 0)
    val countdownTotalSeconds: Int get() = maxOf(restTotalSeconds, 0)
    val currentPhaseName: String?
        get() = workoutTasks.getOrNull(position)?.let { SessionTransitionEngine.phaseDisplayName(it.phaseName).ifEmpty { null } }
    val preparationStructureLabel: String? get() = if (isPreparing && workoutTask.isMovement && preparationBlock == null) "STANDARD SETS" else null
    private val restUpNextIndex: Int? get() = if (isPreparing) null else upNext?.taskId?.let { id -> workoutTasks.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
    val restUpNextStructureLabel: String?
        get() {
            val idx = restUpNextIndex ?: return null
            if (!workoutTasks[idx].isMovement) return null
            return SessionPrescription.block(workoutTasks, idx)?.title ?: "STANDARD SETS"
        }
    val restUpNextPrescription: String?
        get() {
            val idx = restUpNextIndex ?: return null
            val task = workoutTasks[idx]
            return if (activeRestKind != null) SessionPrescription.unit(task)?.uppercase(Locale.US)
            else SessionPrescription.line(task, engine.groupRange(idx)?.let { engine.groupRounds(it) })
        }
    val restUpNextNote: String? get() = restUpNextIndex?.let { workoutTasks[it].instructions }
    val restPhaseChange: PhaseChange?
        get() {
            if (isPreparing || !workoutTask.isRest || workoutTasks.isEmpty() || !engine.endsOnRestBlock(position)) return null
            val target = engine.afterItem(position).targetCursor ?: return null
            return engine.phaseChange(position, target.position)
        }
    val phaseLabelText: String? get() = if (workoutTasks.isEmpty()) null else engine.phaseLabel(position)
    val exerciseCount: Int get() = workoutTasks.count { it.isMovement }
    val completionPercent: Int get() = session.completionPercent
    val incompleteExerciseCount: Int get() = session.unresolvedTaskIds.size
    val finishConfirmationMessage: String get() = SessionFinishRule.message(incompleteExerciseCount)
    val currentSetState: SetState get() = session.setState(workoutTask.id, currentIteration)
    val currentSetIsLogged: Boolean get() = actualSetLogs[workoutTask.id]?.get(currentIteration) != null
    val hasSessionOnlyEdits: Boolean get() = !session.overrides.isEmpty
    val canSwapCurrentMovement: Boolean get() = SessionWorkoutEditor.canSwap(workoutTask) && intervalRunner == null
    val canRemoveCurrentExercise: Boolean get() = SessionWorkoutEditor.canRemove(workoutTask)
    val canEditCurrentSets: Boolean get() = SessionWorkoutEditor.canEditSets(workoutTask)

    val summaryStats: WorkoutSummaryStats
        get() = WorkoutSummaryStats.build(
            workoutTasks, actualSetLogs, session.progress, session.requiredIterations,
            completedElapsedSeconds ?: secondsElapsed,
            session.totalDistance(DistanceUnit.current)?.let { DistanceValue(it, DistanceUnit.current) }
        )

    // MARK: - Up Next

    val upNext: UpNextDescriptor?
        get() {
            val runner = intervalRunner
            if (runner != null && runner.phase != IntervalPhase.DONE) {
                val next = runner.nextExercise
                val slot = runner.nextSlot
                if (next != null && slot != null) {
                    val detail = if (runner.intervalMode != IntervalMode.AMRAP) {
                        if (runner.setsTotal > 1) "Set ${slot.first} · Round ${slot.second} of ${runner.roundsTotal}" else "Round ${slot.second} of ${runner.roundsTotal}"
                    } else if (runner.setsTotal > 1) "Set ${slot.first} of ${runner.setsTotal}" else null
                    val seconds = if (runner.intervalMode == IntervalMode.AMRAP) runner.plan.windowSeconds else runner.plan.workSeconds
                    return descriptor(next, UpNextDescriptor.Kind.Interval("Work", seconds), detail = detail, startsIn = null)
                }
                val after = currentGroupRange()?.let { it.last + 1 } ?: (position + 1)
                val task = firstMovement(after) ?: return null
                return descriptor(task, UpNextDescriptor.Kind.NextExercise, prescription = compactPrescription(task), startsIn = null)
            }
            if (isPreparing) return descriptor(workoutTask, UpNextDescriptor.Kind.NextExercise, prescription = compactPrescription(workoutTask), startsIn = restSecondsRemaining)
            val deferred = afterRest
            val target = deferred?.targetCursor
            if (deferred != null && target != null) {
                val task = firstMovement(target.position)
                if (task != null) when (activeRestKind) {
                    RestKind.BETWEEN_SETS -> return descriptor(task, UpNextDescriptor.Kind.NextSet(target.set, workoutTask.sets), detail = "Set ${target.set} of ${workoutTask.sets}", startsIn = restSecondsRemaining)
                    RestKind.BETWEEN_MEMBERS -> return descriptor(task, UpNextDescriptor.Kind.NextMember(engine.memberLabel(task.order) ?: ""), prescription = SessionPrescription.unit(task), startsIn = restSecondsRemaining)
                    RestKind.BETWEEN_ROUNDS -> {
                        val range = engine.groupRange(target.position)
                        if (target.round > 1 && range != null) {
                            val rounds = engine.groupRounds(range)
                            return descriptor(task, UpNextDescriptor.Kind.NextRound(target.round, rounds, engine.memberLabel(task.order)), detail = "Round ${target.round} of $rounds", startsIn = restSecondsRemaining)
                        }
                        return descriptor(task, UpNextDescriptor.Kind.NextExercise, prescription = compactPrescription(task), startsIn = restSecondsRemaining)
                    }
                    null -> Unit
                }
            }
            nextRoundLead()?.let { (task, round, rounds) ->
                return descriptor(task, UpNextDescriptor.Kind.NextRound(round, rounds, engine.memberLabel(task.order)), detail = "Round $round of $rounds", startsIn = if (workoutTask.isRest) restSecondsRemaining else null)
            }
            val range = currentGroupRange()
            if (range != null && !workoutTask.isInterval) {
                val member = firstMovement(position + 1)
                if (member != null && member.order in range) {
                    return descriptor(member, UpNextDescriptor.Kind.NextMember(engine.memberLabel(member.order) ?: ""), prescription = SessionPrescription.unit(member), startsIn = if (workoutTask.isRest) restSecondsRemaining else null)
                }
            }
            val task = firstMovement(position + 1) ?: return null
            return descriptor(task, UpNextDescriptor.Kind.NextExercise, prescription = compactPrescription(task), startsIn = if (workoutTask.isRest) restSecondsRemaining else null)
        }

    private fun nextRoundLead(): Triple<WorkoutTask, Int, Int>? {
        val range = currentGroupRange() ?: return null
        if (workoutTask.isInterval || position != range.last) return null
        val lead = workoutTasks[range.first]
        val rounds = maxOf(lead.rounds ?: lead.totalRounds, 1)
        if (currentRound >= rounds) return null
        val first = firstMovement(range.first) ?: return null
        if (first.order !in range) return null
        return Triple(first, currentRound + 1, rounds)
    }

    val showsUpNextCard: Boolean
        get() {
            val runner = intervalRunner ?: return false
            return upNext != null && runner.phase != IntervalPhase.DONE && runner.phase != IntervalPhase.WORK
        }

    private fun descriptor(task: WorkoutTask, kind: UpNextDescriptor.Kind, prescription: String? = null, detail: String? = null, startsIn: Int?): UpNextDescriptor? {
        if (task.name.isEmpty()) return null
        return UpNextDescriptor(task.id, task.name, task.movementThumbnailId ?: task.movementImageId, prescription, kind, detail, startsIn)
    }

    private fun compactPrescription(task: WorkoutTask): String? = SessionPrescription.compact(task, engine.groupRange(task.order)?.let { engine.groupRounds(it) })
    val nextMovementMediaId: String? get() = upNext?.mediaId
    private fun firstMovement(index: Int): WorkoutTask? = if (index < 0) null else workoutTasks.drop(index).firstOrNull { it.isMovement }

    val logSetCTALabel: String
        get() {
            val label = if (workoutTask.groupId.isEmpty()) "Set" else "Round"
            val logged = actualSetLogs[workoutTask.id]?.get(currentIteration) ?: return "Log $label $currentIteration"
            val weight = logged.weightKg ?: 0.0
            val weightStr = "${WeightUnit.displayNumber(fromLbs = weight)} ${WeightUnit.current.label}"
            if (workoutTask.usesDuration) {
                val dur = formatDuration(logged.durationSeconds ?: 0)
                return if (weight > 0) "$label ${logged.setNumber} · $weightStr × $dur" else "$label ${logged.setNumber} · $dur"
            }
            return "$label ${logged.setNumber} · $weightStr × ${logged.reps}"
        }

    val currentTargetText: String? get() = SessionPrescription.target(workoutTask, currentIteration)
    val workoutProgressText: String
        get() {
            val total = exerciseCount
            if (total <= 0 || workoutTasks.isEmpty()) return ""
            val done = (0..minOf(position, workoutTasks.size - 1)).count { workoutTasks[it].isMovement }
            return "Exercise ${maxOf(done, 1)} of $total"
        }
    val setHeadlineText: String?
        get() = if (workoutTask.isMovement && !workoutTask.isInterval && workoutTask.groupId.isEmpty() && intervalRunner == null && workoutTask.sets > 1) "SET $currentSet OF ${workoutTask.sets}" else null
    val exerciseProgressText: String?
        get() {
            intervalRunner?.let { r -> val total = maxOf(1, r.setsTotal); return if (total > 1) "Set ${maxOf(1, r.currentSet)} of $total" else null }
            if (!workoutTask.isMovement) return null
            if (workoutTask.groupId.isNotEmpty()) return "Round $currentRound of ${maxOf(workoutTask.rounds ?: workoutTask.totalRounds, 1)}"
            val total = maxOf(workoutTask.sets, 1)
            return if (total > 1) "Set $currentSet of $total" else null
        }
    val restBackgroundMediaId: String?
        get() = when {
            isSetRest -> workoutTask.movementThumbnailId ?: workoutTask.movementImageId
            workoutTask.isRest && workoutTask.restType == RestType.ACTIVE_RECOVERY -> workoutTask.restMovementThumbnailId
            else -> nextMovementMediaId
        }

    val workoutRemainingSeconds: Int
        get() {
            if (workoutTasks.isEmpty() || position >= workoutTasks.size) return 0
            var current = 0
            var tailFrom = position + 1
            val runner = intervalRunner
            val range = currentGroupRange()
            if (runner != null && runner.phase != IntervalPhase.DONE) {
                current = runner.blockRemainingSeconds + (if (runner.phase == IntervalPhase.PREPARE) runner.secondsRemaining else 0)
                tailFrom = range?.let { it.last + 1 } ?: (position + 1)
            } else if (isPreparing) {
                current = restSecondsRemaining + WorkoutDurationEstimator.plannedSeconds(listOf(workoutTask))
            } else if (range != null) {
                val lead = workoutTasks[range.first]
                val rounds = maxOf(lead.rounds ?: lead.totalRounds, 1)
                val block = WorkoutDurationEstimator.plannedSeconds(range.map { workoutTasks[it] })
                val roundsLeft = maxOf(0, rounds - currentRound + (if (isRoundRest) 0 else 1))
                current = block * roundsLeft / rounds + (if (isRoundRest) restSecondsRemaining else 0)
                tailFrom = range.last + 1
            } else if (workoutTask.isRest) {
                current = restSecondsRemaining
            } else if (workoutTask.isMovement) {
                val sets = maxOf(workoutTask.sets, 1)
                val task = WorkoutDurationEstimator.plannedSeconds(listOf(workoutTask))
                val setsLeft = maxOf(0, sets - currentSet + (if (isSetRest) 0 else 1))
                current = task * setsLeft / sets + (if (isSetRest) restSecondsRemaining else 0)
            }
            return maxOf(0, current + WorkoutDurationEstimator.plannedSeconds(workoutTasks, tailFrom))
        }
    val workoutRemainingText: String get() = IntervalMath.formatTime(workoutRemainingSeconds)

    /** Freeze the demo video: paused, prep, interval prepare / between-sets rest, passive interval rest. */
    val shouldHoldVideo: Boolean
        get() {
            if (isVideoPaused || isPreparing) return true
            val runner = intervalRunner ?: return false
            return when (runner.phase) {
                IntervalPhase.PREPARE, IntervalPhase.BETWEEN_SETS_REST -> true
                IntervalPhase.REST -> runner.leadTask?.restType != RestType.ACTIVE_RECOVERY
                IntervalPhase.WORK, IntervalPhase.DONE -> false
            }
        }

    // MARK: - Load

    fun loadData(workoutId: String, programId: String? = null) {
        this.workoutId = workoutId.lowercase(Locale.US)
        val cached = DatabaseManager.workout(workoutId)
        if (cached == null) { workoutNotFound = true; return }
        session.begin(workoutId, programId)
        session.recordMode(SessionMode.INTERACTIVE)
        announceModeEntry = true
        prepCuePlayer = { event, done -> AudioCueService.play(event, done) }
        applyDetail(cached)
        startTicker()
        refreshFromServerIfNeeded(workoutId, cached)
    }

    fun refreshFromServerIfNeeded(workoutId: String, cached: Workout) {
        if (!SessionWorkoutRefresh.shouldRefresh(workoutId, cached, session)) return
        isLoadingWorkout = true
        viewModelScope.launch {
            try {
                val refreshed = SessionWorkoutRefresh.refresh(workoutId, cached, session) ?: return@launch
                applyDetail(refreshed)
            } finally {
                isLoadingWorkout = false
            }
        }
    }

    fun applyDetail(workout: Workout) {
        val detail = workout.hydrateGroupRounds()
        workoutName = detail.name
        phaseOptions = detail.phases.map { PhaseOption.from(it) }
        isWorkoutEditable = SessionWorkoutEditor.isEditable(detail)
        workoutTasks = session.overrides.apply(detail.allTasks).mapIndexed { i, t -> t.copy(order = i) }
        session.registerTasks(workoutTasks)
        lastSeenGroupId = ""
        if (workoutTasks.isEmpty()) return
        val savedIndex = session.currentIndex?.takeIf { it in workoutTasks.indices }
        val progress = session.progress
        if (progress.isEmpty) {
            position = savedIndex ?: 0
            restoreSavedRoundAndRender()
            return
        }
        val c = progress.firstUpcomingCursor(workoutTasks, session.requiredIterations)
        if (c != null) {
            position = c.position
            restoreSavedRoundAndRender(c.set, c.round)
            return
        }
        val cont = progress.continueCursor(workoutTasks, session.requiredIterations)
        if (cont != null) {
            position = cont.position
            restoreSavedRoundAndRender(cont.set, cont.round)
        } else completeWorkout()
    }

    private fun restoreSavedRoundAndRender(set: Int = 1, round: Int? = null) {
        val task = workoutTasks[position]
        var resolvedRound = round ?: 1
        val saved = session.currentRound
        if (round == null && task.groupId.isNotEmpty() && !task.isInterval && saved != null && saved > 1) {
            resolvedRound = minOf(saved, maxOf(task.rounds ?: task.totalRounds, 1))
        }
        val snap = session.intervalSnapshot?.takeIf { task.groupId.isNotEmpty() && it.groupId == task.groupId && it.phase != IntervalPhase.DONE }
        val row = currentIntervalTaskRow()
        if (snap != null && row != null) {
            val probe = IntervalRunner(row, snap, clock)
            probe.reconcile(clock())
            if (probe.phase == IntervalPhase.DONE) {
                probe.cancel()
                session.intervalSnapshot = null
                currentSet = set; currentRound = resolvedRound; workoutTask = task
                exitIntervalGroup()
                return
            }
            if (probe.isPaused) isVideoPaused = true else { probe.pause(); blockFrozenBehindPrep = true }
            session.intervalSnapshot = probe.snapshot()
            probe.cancel()
        }
        val prepares = (task.isMovement || engine.groupRange(position) != null) && !task.isRest
        land(Cursor(position, set, resolvedRound), preparing = prepares, phaseChange = null)
    }

    // MARK: - Navigation

    fun getNext() {
        if (isPreparing) { startWork(); return }
        removeRestTimer()
        val deferred = afterRest
        if (deferred != null) {
            afterRest = null; activeRestKind = null; isRest = false
            apply(deferred, completing = false)
            return
        }
        val runner = intervalRunner
        if (runner != null && runner.phase != IntervalPhase.DONE) { runner.skip(); bump(); return }
        apply(engine.next(cursor), completing = true)
    }

    fun startWork() {
        if (!isPreparing) return
        cancelPrepCueGate()
        removeRestTimer()
        isPreparing = false
        pendingPhaseChange = null
        isRest = workoutTask.isRest
        val row = currentIntervalTaskRow()
        if (workoutTask.isInterval && workoutTask.groupId.isNotEmpty() && row != null) {
            makeRunner(row, prepareSeconds = 0)
            bump()
            return
        }
        AudioCueService.play(SessionCueEvent.PrepEnded)
        syncSetTimer()
        bump()
    }

    private fun apply(transition: Transition, completing: Boolean) {
        if (completing) markCurrentSetCompleted()
        when (transition) {
            is Transition.NextSet -> {
                currentSet = transition.cursor.set
                syncSetTimer()
                AudioCueService.play(SessionCueEvent.SetAdvanced(transition.cursor.set, maxOf(workoutTask.sets, 1)))
            }
            is Transition.Step -> {
                val leavingRestBlock = engine.endsOnRestBlock(position)
                position = transition.cursor.position
                currentSet = transition.cursor.set
                currentRound = transition.cursor.round
                updateUI(resetRoundOnGroupChange = false)
                if (leavingRestBlock && workoutTask.isMovement && intervalRunner == null) AudioCueService.play(SessionCueEvent.PrepEnded)
            }
            is Transition.Rest -> {
                activeRestKind = transition.kind
                afterRest = transition.then
                isRest = true
                stopSetTimer()
                startRestCountdown(transition.seconds)
                AudioCueService.play(SessionCueEvent.RestStarted(transition.seconds, SessionCuePhrasing.spokenUpNext(upNext?.title)))
            }
            is Transition.RestBlock -> land(transition.cursor, preparing = false, phaseChange = null)
            is Transition.Prepare -> land(transition.cursor, preparing = true, phaseChange = transition.phaseChange)
            Transition.WorkoutComplete -> requestWorkoutComplete()
        }
        bump()
    }

    private fun land(c: Cursor, preparing: Boolean, phaseChange: PhaseChange?) {
        position = c.position; currentSet = c.set; currentRound = c.round
        afterRest = null; activeRestKind = null
        if (preparing && preparationSeconds > 0) {
            isPreparing = true
            pendingPhaseChange = phaseChange
            updateUI(resetRoundOnGroupChange = false)
            stopSetTimer()
            removeRestTimer()
            restEndMs = null
            restSecondsRemaining = preparationSeconds
            restTotalSeconds = preparationSeconds
            rest = formatRestTime(preparationSeconds)
            val event = SessionCueEvent.PrepStarted(
                exercise = workoutTask.name,
                prescription = SessionCuePhrasing.spokenPrescription(preparationPrescription),
                phaseChange = phaseChange?.let { PhaseChangeCue(it.completedPhaseName, it.nextPhaseName) },
                structure = preparationBlock?.spoken,
                modeEntry = announceModeEntry
            )
            announceModeEntry = false
            val token = UUID.randomUUID().toString()
            prepCueToken = token
            isAwaitingPrepCue = true
            prepCuePlayer(event) { prepCueFinished(token) }
            if (isAwaitingPrepCue && prepCueToken == token) armPrepCueHold(token)
        } else {
            isPreparing = false
            pendingPhaseChange = null
            updateUI(resetRoundOnGroupChange = false)
        }
        bump()
    }

    private fun prepCueFinished(token: String) {
        if (token != prepCueToken || !isPreparing) return
        cancelPrepCueGate()
        startRestCountdown(preparationSeconds)
        bump()
    }

    private fun armPrepCueHold(token: String) {
        prepCueHoldJob?.cancel()
        prepCueHoldJob = viewModelScope.launch { delay(prepCueMaxHoldMs); prepCueFinished(token) }
    }

    private fun cancelPrepCueGate() {
        prepCueToken = null
        isAwaitingPrepCue = false
        prepCueHoldJob?.cancel()
        prepCueHoldJob = null
    }

    private fun markCurrentSetCompleted() {
        if (!workoutTask.isMovement || workoutTask.isInterval || intervalRunner != null) return
        session.completeSet(workoutTask.id, currentIteration, if (setTimerElapsedSeconds > 0) setTimerElapsedSeconds else null)
    }

    private fun recordIntervalBlockOutcome(range: IntRange, roundsDone: Int?) {
        for (i in range) {
            if (!workoutTasks[i].isMovement) continue
            val id = workoutTasks[i].id
            if (roundsDone == null) { session.markExerciseCompleted(id); continue }
            val done = minOf(session.required(id), roundsDone)
            for (r in 1..done) session.completeSet(id, r)
            session.skipRemainingSets(id, done + 1)
        }
    }

    private fun clearTransientState() {
        cancelPrepCueGate()
        removeRestTimer()
        afterRest = null; activeRestKind = null
        isPreparing = false; pendingPhaseChange = null
        isRest = workoutTask.isRest
    }

    fun getPrevious() {
        if (isPreparing) {
            if (position <= 0) return
            clearTransientState()
            stepBackToPreviousTask()
            bump(); return
        }
        removeRestTimer()
        val runner = intervalRunner
        if (runner != null && runner.phase != IntervalPhase.DONE) {
            if (runner.back()) { bump(); return }
            val blockStart = currentGroupRange()?.first ?: position
            if (blockStart <= 0) return
            cancelRunner()
            position = blockStart
            stepBackToPreviousTask()
            bump(); return
        }
        if (afterRest != null) {
            val wasRoundRest = isRoundRest
            afterRest = null; activeRestKind = null; isRest = false
            if (wasRoundRest) updateUI(resetRoundOnGroupChange = false) else syncSetTimer()
            bump(); return
        }
        val range = currentGroupRange()
        if (range != null) {
            if (position > range.first) { position -= 1; updateUI(resetRoundOnGroupChange = false); bump(); return }
            if (currentRound > 1) { currentRound -= 1; position = range.last; updateUI(resetRoundOnGroupChange = false); bump(); return }
            currentRound = 1
            stepBackToPreviousTask()
            bump(); return
        }
        if (workoutTask.sets > 0 && currentSet > 1) { currentSet -= 1; syncSetTimer(); bump(); return }
        currentSet = 1
        stepBackToPreviousTask()
        bump()
    }

    private fun stepBackToPreviousTask() {
        if (position <= 0) return
        position -= 1
        landAtEndOfCurrentTask()
    }

    private fun landAtEndOfCurrentTask() {
        val range = currentGroupRange()
        if (range == null) {
            val task = workoutTasks[position]
            currentSet = if (task.isMovement) maxOf(task.sets, 1) else 1
            updateUI()
            return
        }
        val lead = workoutTasks[range.first]
        if (lead.isInterval) {
            position = range.first
            currentRound = 1
            updateUI()
            intervalRunner?.jumpToLastRound()
            if (isVideoPaused) intervalRunner?.pause()
            return
        }
        currentRound = maxOf(lead.rounds ?: lead.totalRounds, 1)
        position = range.last
        lastSeenGroupId = lead.groupId
        updateUI(resetRoundOnGroupChange = false)
    }

    private fun currentGroupRange(): IntRange? = engine.groupRange(position)

    /** The Up Next skip affordance. */
    fun getNextExercise() {
        val runner = intervalRunner
        if (runner != null) {
            if (runner.phase != IntervalPhase.DONE && runner.jumpToNextWork()) { bump(); return }
            exitIntervalGroup(); return
        }
        clearTransientState()
        val insideGroup = currentGroupRange() != null
        val transition = engine.skipForward(cursor)
        if (transition == Transition.WorkoutComplete && !insideGroup) { syncSetTimer(); bump(); return }
        apply(transition, completing = insideGroup)
    }

    // MARK: - Skip

    val canSkipSet: Boolean
        get() = workoutTask.isMovement && !workoutTask.isInterval && !workoutTask.isRest && intervalRunner == null && activeRestKind == null && !isWorkoutComplete
    val canSkipExercise: Boolean get() = canSkipSet || (intervalRunner != null && !isWorkoutComplete)
    val skipExerciseLabel: String get() = if (currentGroupRange() != null) "Skip Block" else "Skip Exercise"

    fun skipCurrentSet() {
        if (!canSkipSet) return
        clearTransientState()
        stopSetTimer()
        session.skipSet(workoutTask.id, currentIteration)
        AudioCueService.skipped()
        apply(droppingRest(engine.next(cursor)), completing = false)
    }

    fun skipCurrentExercise() {
        if (!canSkipExercise) return
        if (intervalRunner != null) { AudioCueService.skipped(); exitIntervalGroup(); return }
        clearTransientState()
        stopSetTimer()
        val range = currentGroupRange()
        val transition = if (range != null) {
            for (i in range) if (workoutTasks[i].isMovement) session.skipRemainingSets(workoutTasks[i].id, if (i >= position) currentRound else currentRound + 1)
            engine.afterItem(range.last)
        } else {
            session.skipRemainingSets(workoutTask.id, currentIteration)
            engine.afterItem(position)
        }
        AudioCueService.skipped()
        apply(transition, completing = false)
    }

    private fun droppingRest(t: Transition): Transition = if (t is Transition.Rest) t.then else t

    // MARK: - Pause / clocks

    fun toggleVideoState() {
        isVideoPaused = !isVideoPaused
        if (isVideoPaused) { stopSetTimer(); pauseRestTimer(); intervalRunner?.pause() }
        else { if (isRest) { if (!isAwaitingPrepCue) setRestTimer() } else syncSetTimer(); intervalRunner?.resume() }
        bump()
    }

    fun resyncClocks() {
        val now = clock()
        session.resync()
        intervalRunner?.reconcile(now)
        if (restRunning) reconcileRestTimer(now)
        if (setRunning) tickSetTimer(now)
        bump()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(250)
                onTick()
            }
        }
    }

    private fun onTick() {
        val now = clock()
        val runner = intervalRunner
        if (runner != null && runner.phase != IntervalPhase.DONE && !runner.isPaused) runner.reconcile(now)
        if (restRunning) reconcileRestTimer(now)
        if (setRunning) tickSetTimer(now)
        bump()
    }

    private fun bump() { tick += 1 }

    /** Tear down local clocks; a live interval block leaves its snapshot on the session. */
    fun stop() {
        val runner = intervalRunner
        val parked = runner?.takeIf { it.phase != IntervalPhase.DONE }?.snapshot() ?: (if (isPreparing) session.intervalSnapshot else null)
        cancelRunner()
        session.intervalSnapshot = parked
        session.activeSet = null
        cancelPrepCueGate()
        removeRestTimer()
        stopSetTimer()
        tickerJob?.cancel(); tickerJob = null
        AudioCueService.silenceVoice()
        AudioCueService.isMasterMuted = false
    }

    private fun removeRestTimer() { restRunning = false; restPausedRemainingMs = null }

    private fun pauseRestTimer() {
        val end = restEndMs
        if (end != null && restRunning) restPausedRemainingMs = maxOf(0L, end - clock())
        restRunning = false
    }

    // MARK: - Logging

    val previousWeight: MovementPreviousWeight?
        get() = if (workoutTask.isMovement) MovementHistoryStore.previousWeight(workoutTask.movementId, workoutTask.name, sessionId) else null

    data class LogDefaults(val weightLbs: Double?, val reps: Int, val durationSeconds: Int?)

    fun logDefaults(): LogDefaults {
        val logs = actualSetLogs[workoutTask.id] ?: emptyMap()
        val n = currentIteration
        val prev = logs.keys.filter { it < n }.maxOrNull()?.let { logs[it] }
        if (prev != null) return LogDefaults(prev.weightKg, prev.reps, prev.durationSeconds)
        val previousLbs = previousWeight?.weightLbs
        val planned = workoutTask.setConfigs.firstOrNull { it.setNumber == n }
        if (planned != null) return LogDefaults(previousLbs ?: planned.weightKg ?: workoutTask.weightKg?.toDouble(), planned.reps, planned.durationSeconds ?: workoutTask.durationSeconds)
        return LogDefaults(previousLbs ?: workoutTask.weightKg?.toDouble(), workoutTask.reps, workoutTask.durationSeconds)
    }

    fun logCurrentSet(weightLbs: Double?, reps: Int, durationSeconds: Int? = null, distance: Double? = null, distanceUnit: DistanceUnit? = null) {
        session.logSet(workoutTask.id, currentIteration, weightLbs, reps, durationSeconds, distance, distanceUnit?.rawValue)
        bump()
    }

    val currentSetIsDistanceCapable: Boolean
        get() {
            val anatomy = workoutTask.movementId?.let { id -> DatabaseManager.anatomyIds(id).mapNotNull { ClassificationStore.anatomyName(it) } } ?: emptyList()
            return DistanceRule.isDistanceCapable(workoutTask, anatomy)
        }
    val currentSetLoggedDistance: DistanceValue? get() = actualSetLogs[workoutTask.id]?.get(currentIteration)?.distanceValue

    // MARK: - Finish / discard

    private fun requestWorkoutComplete() {
        if (incompleteExerciseCount == 0) { currentSet = 1; currentRound = 1; completeWorkout() }
        else { stopSetTimer(); showFinishConfirmation = true }
    }

    fun finishAnyway() { showFinishConfirmation = false; currentSet = 1; currentRound = 1; completeWorkout() }

    fun continueWorkout() {
        showFinishConfirmation = false
        val c = session.progress.continueCursor(workoutTasks, session.requiredIterations) ?: run { syncSetTimer(); bump(); return }
        clearTransientState()
        cancelRunner()
        lastSeenGroupId = workoutTasks[c.position].groupId
        land(c, preparing = true, phaseChange = null)
    }

    private fun completeWorkout() {
        if (isWorkoutComplete) return
        isWorkoutComplete = true
        completedElapsedSeconds = session.secondsElapsed
        stopSetTimer(); removeRestTimer(); cancelRunner()
        session.activeSet = null
        summaryAchievements = buildAchievements()
        AudioCueService.play(SessionCueEvent.WorkoutComplete)
        bump()
    }

    private fun buildAchievements(): SessionAchievements {
        val rows = SessionHistoryRows.build(workoutTasks, actualSetLogs, session.progress, session.requiredIterations)
        return SessionAchievementsBuilder.build(rows, sessionId, workoutId, session.programId)
    }

    fun discardSession(onDone: () -> Unit = {}) {
        showFinishConfirmation = false
        stop()
        val id = sessionId
        viewModelScope.launch { ActiveWorkoutSession.discard(id); onDone() }
    }

    fun saveSession(rpe: Int?) {
        SessionRecorder.save(sessionId, workoutId, workoutName, workoutTasks, startDateMs, completedElapsedSeconds ?: secondsElapsed,
            actualSetLogs, session.progress, session.requiredIterations, rpe)
        session.reset()
    }

    // MARK: - In-session editing (Do → Build)

    sealed class AddPlacement { object AfterCurrent : AddPlacement(); object End : AddPlacement(); data class Phase(val id: String) : AddPlacement() }

    fun addExercise(movement: MovementLibraryItem, placement: AddPlacement, scope: SessionWorkoutEditor.Scope) {
        val anchorId = if (placement == AddPlacement.AfterCurrent && workoutTasks.isNotEmpty()) {
            currentGroupRange()?.let { workoutTasks[it.last].id } ?: workoutTask.id
        } else null
        val phaseId = (placement as? AddPlacement.Phase)?.id
        val template = anchorId?.let { id -> workoutTasks.firstOrNull { it.id == id } } ?: workoutTasks.lastOrNull()
        SessionWorkoutEditor.add(movement, workoutId, anchorId, phaseId, template, scope)
        reloadFromCache(if (workoutTasks.isEmpty()) null else workoutTask.id)
    }

    fun swapCurrentMovement(movement: MovementLibraryItem, scope: SessionWorkoutEditor.Scope) {
        if (!canSwapCurrentMovement) return
        val id = workoutTask.id
        SessionWorkoutEditor.swap(id, movement, workoutId, scope)
        reloadFromCache(id)
    }

    fun removeCurrentExercise(scope: SessionWorkoutEditor.Scope) {
        if (!canRemoveCurrentExercise) return
        val next = workoutTasks.getOrNull(position + 1)?.id
        SessionWorkoutEditor.remove(workoutTask.id, workoutId, scope)
        reloadFromCache(next)
    }

    fun updateCurrentSets(sets: Int, reps: Int, scope: SessionWorkoutEditor.Scope) {
        if (!canEditCurrentSets) return
        val id = workoutTask.id
        SessionWorkoutEditor.updateSets(id, sets, reps, workoutTask.weightKg, workoutId, scope)
        reloadFromCache(id)
    }

    fun saveMyVersion(name: String? = null): String? = SessionWorkoutEditor.saveMyVersion(workoutId, name)

    fun reloadFromCache(anchorTaskId: String?) {
        val detail = DatabaseManager.workout(workoutId)?.hydrateGroupRounds() ?: return
        phaseOptions = detail.phases.map { PhaseOption.from(it) }
        applyTaskList(detail.allTasks, anchorTaskId)
    }

    fun applyTaskList(base: List<WorkoutTask>, anchorTaskId: String?) {
        val tasks = session.overrides.apply(base).mapIndexed { i, t -> t.copy(order = i) }
        if (tasks.isEmpty()) return
        val previousPosition = position
        val previousGroupId = workoutTask.groupId
        workoutTasks = tasks
        session.registerTasks(workoutTasks)
        val idx = anchorTaskId?.let { id -> tasks.indexOfFirst { it.id == id } } ?: -1
        position = if (idx >= 0) idx else minOf(previousPosition, tasks.size - 1)
        cancelPrepCueGate(); removeRestTimer()
        afterRest = null; activeRestKind = null; isPreparing = false; pendingPhaseChange = null; isRest = false
        val task = workoutTasks[position]
        if (task.groupId != previousGroupId || task.groupId.isEmpty()) { cancelRunner(); currentRound = 1 }
        else currentRound = minOf(currentRound, maxOf(task.rounds ?: task.totalRounds, 1))
        currentSet = minOf(currentSet, maxOf(task.sets, 1))
        lastSeenGroupId = task.groupId
        updateUI(resetRoundOnGroupChange = false)
        bump()
    }

    // MARK: - updateUI

    private fun updateUI(resetRoundOnGroupChange: Boolean = true) {
        if (workoutTasks.isEmpty()) return
        workoutTask = workoutTasks[position]
        isRest = workoutTask.isRest || isPreparing
        var gidChanged = false
        if (resetRoundOnGroupChange) {
            val gid = workoutTask.groupId
            gidChanged = gid != lastSeenGroupId
            if (gidChanged) currentRound = 1
            lastSeenGroupId = gid
        } else lastSeenGroupId = workoutTask.groupId

        if (isPreparing) {
            val parked = session.intervalSnapshot?.takeIf { workoutTask.groupId.isNotEmpty() && it.groupId == workoutTask.groupId && it.phase != IntervalPhase.DONE }
            cancelRunner()
            session.intervalSnapshot = parked
        } else if (workoutTask.isInterval && workoutTask.groupId.isNotEmpty()) {
            val row = currentIntervalTaskRow()
            if ((intervalRunner == null || gidChanged) && row != null) makeRunner(row)
        } else {
            cancelRunner()
            if (workoutTask.isRest) {
                startRestCountdown(workoutTask.restSeconds)
                AudioCueService.play(SessionCueEvent.RestStarted(workoutTask.restSeconds, SessionCuePhrasing.spokenUpNext(upNext?.title), restPhaseChange?.nextPhaseName))
            }
        }

        val newPhase = workoutTask.workoutPhaseId?.lowercase(Locale.US)
        val nextMovement = nextMovementIdAfter(position)
        if (!newPhase.isNullOrEmpty() && newPhase != session.workoutPhaseId) session.enqueueProgress(newPhase, position, nextMovement)
        else session.recordPositionLocally(position, nextMovement)
        session.recordPositionLocally(null, null, if (workoutTask.groupId.isEmpty()) 1 else currentRound)
        syncSetTimer()
    }

    // MARK: - Set timer

    private fun syncSetTimer() {
        session.activeSet = if (workoutTask.isMovement && !workoutTask.isInterval && !isWorkoutComplete && intervalRunner == null)
            ActiveWorkoutSession.ActiveSetKey(workoutTask.id, currentIteration) else null
        if (!workoutTask.isMovement || isRest || isWorkoutComplete || intervalRunner != null) { stopSetTimer(); return }
        val key = "${workoutTask.id}#$currentIteration"
        if (key != timedSetKey) {
            timedSetKey = key
            setAccumulatedMs = 0; setAnchorMs = null
            setTimerElapsedSeconds = 0
            setTimerSeconds = setTimerTargetSeconds ?: 0
            AudioCueService.resetCountdownCues()
        }
        if (isVideoPaused) { stopSetTimer(); return }
        if (setAnchorMs == null) setAnchorMs = clock()
        setRunning = true
    }

    private fun tickSetTimer(now: Long) {
        val anchor = setAnchorMs ?: return
        val elapsed = maxOf(0, ((setAccumulatedMs + (now - anchor)) / 1000).toInt())
        val target = setTimerTargetSeconds
        if (target != null) {
            val remaining = maxOf(0, target - elapsed)
            setTimerElapsedSeconds = minOf(elapsed, target)
            if (remaining != setTimerSeconds) {
                setTimerSeconds = remaining
                if (remaining > 0) AudioCueService.play(SessionCueEvent.TimedSetTick(remaining, target))
            }
            if (elapsed >= target) {
                stopSetTimer()
                AudioCueService.play(SessionCueEvent.TimedSetEnded)
                getNext()
            }
            return
        }
        setTimerElapsedSeconds = elapsed
        if (elapsed != setTimerSeconds) setTimerSeconds = elapsed
    }

    private fun stopSetTimer() {
        setAnchorMs?.let { anchor ->
            setAccumulatedMs += maxOf(0L, clock() - anchor)
            setAnchorMs = null
            val elapsed = (setAccumulatedMs / 1000).toInt()
            val target = setTimerTargetSeconds
            if (target != null) { setTimerElapsedSeconds = minOf(elapsed, target); setTimerSeconds = maxOf(0, target - elapsed) }
            else { setTimerElapsedSeconds = elapsed; setTimerSeconds = elapsed }
        }
        setRunning = false
    }

    // MARK: - Interval runner lifecycle

    private fun makeRunner(row: TaskRow, prepareSeconds: Int = IntervalMath.defaultPrepareSeconds) {
        val gid = row.task?.groupId
        val restorable = session.intervalSnapshot?.takeIf { !gid.isNullOrEmpty() && it.groupId == gid && it.phase != IntervalPhase.DONE }
        val resumeFrozen = blockFrozenBehindPrep
        blockFrozenBehindPrep = false
        cancelRunner()
        val runner: IntervalRunner
        if (restorable != null) {
            runner = IntervalRunner(row, restorable, clock)
            runner.onEvent = { handleRunnerEvent(it, runner) }
            intervalRunner = runner
            session.intervalSnapshot = restorable
            if (runner.isPaused && resumeFrozen && !isVideoPaused) runner.resume()
            runner.reconcile(clock())
            if (runner.isPaused) isVideoPaused = true
        } else {
            runner = IntervalRunner(row, prepareSeconds, clock)
            runner.onEvent = { handleRunnerEvent(it, runner) }
            intervalRunner = runner
            runner.start()
        }
        if (isVideoPaused) runner.pause()
        if (runner.phase != IntervalPhase.DONE) session.intervalSnapshot = runner.snapshot()
    }

    private fun cancelRunner() {
        intervalRunner?.cancel()
        intervalRunner = null
        session.intervalSnapshot = null
    }

    private fun handleRunnerEvent(event: IntervalRunner.Event, runner: IntervalRunner) {
        if (runner !== intervalRunner) return
        AudioCueService.handle(event, finalRound = runner.isFinalRound, leadsToWork = runner.leadsIntoWork)
        session.intervalSnapshot = if (runner.phase == IntervalPhase.DONE) null else runner.snapshot()
        if (event == IntervalRunner.Event.BlockComplete) {
            viewModelScope.launch { if (runner === intervalRunner) exitIntervalGroup() }
        }
    }

    private fun nextMovementIdAfter(position: Int): String? =
        workoutTasks.drop(position + 1).firstOrNull { it.isMovement && it.movementId != null }?.movementId

    private fun currentIntervalTaskRow(): TaskRow? {
        val range = currentGroupRange() ?: return null
        val lead = workoutTasks[range.first]
        return TaskRow(task = lead, totalRounds = lead.totalRounds, isInterval = true, intervalMode = lead.intervalMode,
            advancedTasks = range.drop(1).map { workoutTasks[it] })
    }

    /** Advance past the entire interval group when the runner finishes (or Skip Block / Up Next leaves it). */
    fun exitIntervalGroup() {
        val runner = intervalRunner
        val lead = runner?.leadTask
        if (runner != null && runner.intervalMode == IntervalMode.AMRAP && lead != null) {
            val partial = runner.amrapPartialReps
            session.logSet(lead.id, 1, if (partial > 0) partial.toDouble() else null, runner.roundsCompleted, lead.intervalWindowSeconds)
        }
        val roundsDone: Int? = runner?.takeIf { it.phase != IntervalPhase.DONE }?.let { r ->
            maxOf(0, r.currentSet - 1) * maxOf(r.roundsTotal, 1) + maxOf(0, r.currentRound - 1)
        }
        cancelRunner()
        val range = currentGroupRange() ?: return
        val finishedLead = workoutTasks[range.first]
        recordIntervalBlockOutcome(range, roundsDone)
        currentRound = 1
        var transition = engine.afterItem(range.last)
        val recoveryRest = finishedLead.roundRestSeconds
        if (finishedLead.intervalMode == IntervalMode.TABATA && IntervalMath.plan(finishedLead, finishedLead.totalRounds).sets <= 1 &&
            recoveryRest != null && recoveryRest > 0 && transition is Transition.Prepare
        ) {
            val nextLead = workoutTasks[transition.cursor.position]
            if (nextLead.isInterval && nextLead.intervalMode == IntervalMode.TABATA && nextLead.groupId.isNotEmpty()) {
                transition = Transition.Rest(recoveryRest, RestKind.BETWEEN_ROUNDS, transition)
                AudioCueService.handle(IntervalRunner.Event.TransitionToRest)
            }
        }
        apply(transition, completing = false)
    }

    // MARK: - Rest countdown

    private fun startRestCountdown(seconds: Int) {
        removeRestTimer()
        val total = maxOf(0, seconds)
        restSecondsRemaining = total
        if (!isPreparing || restTotalSeconds <= 0) restTotalSeconds = total
        rest = formatRestTime(total)
        restEndMs = clock() + total * 1000L
        if (isVideoPaused) { restPausedRemainingMs = total * 1000L; return }
        setRestTimer()
    }

    private fun setRestTimer() {
        if (isVideoPaused) return
        restPausedRemainingMs?.let { restEndMs = clock() + it; restPausedRemainingMs = null }
        restRunning = true
    }

    private fun reconcileRestTimer(now: Long) {
        val end = restEndMs ?: return
        val remaining = end - now
        if (remaining <= 0) {
            removeRestTimer()
            restEndMs = null
            restSecondsRemaining = 0
            rest = formatRestTime(0)
            getNext()
            return
        }
        val seconds = IntervalRunner.ceilSeconds(remaining)
        if (seconds == restSecondsRemaining) return
        restSecondsRemaining = seconds
        rest = formatRestTime(seconds)
        if (isPreparing) AudioCueService.play(SessionCueEvent.PrepTick(seconds))
        else if (activeRestKind != null || workoutTask.isRest) AudioCueService.play(SessionCueEvent.RestTick(seconds))
    }

    private fun formatRestTime(seconds: Int): String = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
        prepCueHoldJob?.cancel()
        intervalRunner?.cancel()
    }

    companion object {
        /** Default length of the preparation countdown (FTNSSLNK-398). */
        const val preWorkoutCountdownSeconds = 10
        /** Longest the countdown waits for the voice before starting anyway. */
        const val prepCueMaxHoldMs = 20_000L

        /** Programmed duration for [iteration] of [task] when the set is time-based; null for rep-based work, rests, interval members. */
        fun timedSetSeconds(task: WorkoutTask, iteration: Int): Int? {
            if (!task.isMovement || task.isRest || task.isInterval) return null
            if (task.hasPerSetBreakdown) task.setConfigs.firstOrNull { it.setNumber == iteration }?.durationSeconds?.takeIf { it > 0 }?.let { return it }
            return if (task.usesDuration) task.durationSeconds?.takeIf { it > 0 } else null
        }

        /** `0:30` / `1:00` / `12:00` for duration chips. */
        fun formatDuration(seconds: Int): String = String.format(Locale.US, "%d:%02d", maxOf(0, seconds) / 60, maxOf(0, seconds) % 60)
    }
}
