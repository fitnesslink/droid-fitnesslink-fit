package com.fitnesslink.fit.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.ExerciseGroupType
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.TrainingFocus
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.AvailableWorkoutPhase
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.deleteSessionExerciseNote
import com.fitnesslink.fit.persistence.sessionExerciseNotes
import com.fitnesslink.fit.persistence.sessionQuickStartMeta
import com.fitnesslink.fit.persistence.updateSessionQuickStartMeta
import com.fitnesslink.fit.persistence.upsertSessionExerciseNote
import com.fitnesslink.fit.session.ActiveWorkoutSession
import com.fitnesslink.fit.session.ExerciseRPE
import com.fitnesslink.fit.session.QuickStartPlan
import com.fitnesslink.fit.session.SessionAchievements
import com.fitnesslink.fit.session.SessionAchievementsBuilder
import com.fitnesslink.fit.session.SessionExerciseNote
import com.fitnesslink.fit.session.SessionHistoryRows
import com.fitnesslink.fit.session.SessionMode
import com.fitnesslink.fit.session.SessionProgress
import com.fitnesslink.fit.session.SessionRecorder
import com.fitnesslink.fit.session.SetState
import com.fitnesslink.fit.session.WorkoutSummaryStats
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import com.fitnesslink.fit.workouts.WorkoutPhaseStore
import com.fitnesslink.fit.workouts.WorkoutRowOps
import com.fitnesslink.fit.workouts.WorkoutSaveEvent
import com.fitnesslink.fit.workouts.WorkoutStore
import com.fitnesslink.fit.workouts.WorkoutTaskInputs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * Quick Start (Quick Start PRD): start training with nothing planned,
 * build the workout as you go and log what you actually do. Port of iOS
 * `QuickStartViewModel`.
 *
 * Composition, not a new engine: the workout's *structure* (phases,
 * exercises, rests, supersets / circuits / intervals) is a real
 * user-owned [Workout] edited through [WorkoutRowOps] and saved through
 * [WorkoutStore], so it lands in My Content (PRD §43); the *performance*
 * (timer, logged sets, per-set state) is the shared [ActiveWorkoutSession],
 * so persistence, resume, history rows and the summary are the ones every
 * other session uses (PRD §38, §41–42).
 *
 * The rule that ties them together: nothing is programmed ahead, so after
 * every log the workout's set counts and per-set values are rewritten to
 * mirror the logs ([syncPlanToLogs]); Finish prunes what was never logged.
 */
class QuickStartViewModel : ViewModel() {
    private val session: ActiveWorkoutSession get() = ActiveWorkoutSession

    var workout by mutableStateOf(Workout())
        private set
    var focus by mutableStateOf(TrainingFocus.presets[0])
        private set
    var isLoaded by mutableStateOf(false)
        private set
    var isStarting by mutableStateOf(false)
        private set
    /** Mirrors the shared clock on (re)entry; [togglePause] flips it. */
    var isPaused by mutableStateOf(false)
        private set
    /** Exercise RPE + Training Log by task id (PRD §34–36). */
    var exerciseNotes by mutableStateOf<Map<String, SessionExerciseNote>>(emptyMap())
        private set
    var collapsedPhaseIds by mutableStateOf<Set<String>>(emptySet())
    var showFinishConfirmation by mutableStateOf(false)
    var isWorkoutComplete by mutableStateOf(false)
        private set
    var completedElapsedSeconds: Int? = null
        private set
    var summaryAchievements by mutableStateOf(SessionAchievements())
        private set
    var phaseRenameError by mutableStateOf<String?>(null)
    /** Mirrors the shared session's version so rows re-read per-set state. */
    var tick by mutableIntStateOf(0)
        private set

    private var isNewWorkout = true

    init {
        WorkoutStore.ensureRegistered()
        viewModelScope.launch { session.state.collect { tick += 1 } }
        // A queued create landing through the drain renames the workout: the
        // session row and our model follow it (parity item 13).
        viewModelScope.launch {
            WorkoutStore.events.collect { event ->
                if (event is WorkoutSaveEvent.CreateDidApply && workout.id.equals(event.localId, true)) {
                    workout = workout.copy(id = event.serverId)
                    isNewWorkout = false
                    session.adoptWorkoutId(event.localId, event.serverId)
                }
            }
        }
    }

    // MARK: - Read-through

    val workoutId: String get() = workout.id
    val sessionId: String get() = session.sessionId
    val startDateMs: Long get() = session.startDateMs
    val secondsElapsed: Int get() = session.secondsElapsed
    val timerText: String get() = session.timerText
    val movementTasks: List<WorkoutTask> get() = workout.allTasks.filter { it.isMovement }
    val exerciseCount: Int get() = movementTasks.size
    val loggedSetCount: Int get() = movementTasks.sumOf { loggedSets(it.id).size }
    /** Exercises added but never logged — dropped from the saved workout at Finish (PRD §30). */
    val unloggedExerciseCount: Int get() = movementTasks.count { loggedSets(it.id).isEmpty() }
    private val loggedTaskIds: Set<String> get() = movementTasks.filter { loggedSets(it.id).isNotEmpty() }.map { it.id }.toSet()

    // MARK: - Start / resume

    /**
     * Mint the workout (server create awaited when online so the session
     * starts against the server id; offline the local id stands and the
     * queued create lands later) and start the session clock. Returns the
     * workout id the canvas should open.
     */
    suspend fun start(focus: TrainingFocus, name: String?): String {
        isStarting = true
        try {
            this.focus = focus
            viewModelScope.launch { WorkoutPhaseStore.refresh() }
            val trimmed = name?.trim().orEmpty()
            workout = Workout(
                id = UUID.randomUUID().toString().lowercase(Locale.US),
                name = trimmed.ifEmpty { TrainingFocus.defaultWorkoutName(focus) },
                phases = emptyList()
            )
            isNewWorkout = true
            save().join()
            val id = workout.id
            beginSession(id)
            DatabaseManager.updateSessionQuickStartMeta(session.sessionId, focus.name, true)
            isLoaded = true
            return id
        } finally {
            isStarting = false
        }
    }

    /** Open the canvas on a workout whose Quick Start session is running (push from the focus screen, or Resume). */
    fun load(workoutId: String) {
        val loaded = DatabaseManager.workout(workoutId)?.let { WorkoutRowOps.normalizeMovementFlags(it.hydrateGroupRounds()) } ?: Workout(id = workoutId.lowercase(Locale.US))
        workout = loaded
        isNewWorkout = false
        beginSession(workoutId)
        DatabaseManager.sessionQuickStartMeta(session.sessionId)?.trainingFocus?.let { focus = TrainingFocus.resolve(it) }
        exerciseNotes = DatabaseManager.sessionExerciseNotes(session.sessionId).associateBy { it.taskId }
        viewModelScope.launch { WorkoutPhaseStore.refresh() }
        isLoaded = true
    }

    private fun beginSession(workoutId: String) {
        // `begin` restarts a same-workout session whose clock is paused, so only call it when this workout isn't the active one.
        if (!session.workoutId.equals(workoutId, ignoreCase = true)) session.begin(workoutId)
        session.recordMode(SessionMode.QUICK_START)
        isPaused = !session.isCounting
        syncPlanToLogs(persistLocal = false)
    }

    // MARK: - Focus / name

    fun changeFocus(newFocus: TrainingFocus) {
        focus = newFocus
        DatabaseManager.updateSessionQuickStartMeta(session.sessionId, newFocus.name, true)
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == workout.name) return
        workout = workout.copy(name = trimmed)
        persistStructure()
    }

    // MARK: - Phases (PRD §12–13)

    fun addPhase(phase: AvailableWorkoutPhase) {
        if (workout.phases.any { it.id.equals(phase.id, true) }) return
        val nextOrder = (workout.phases.maxOfOrNull { it.order } ?: 0) + 1
        workout = workout.copy(phases = workout.phases + WorkoutPhase(id = phase.id.lowercase(Locale.US), name = phase.name, order = nextOrder, isSystem = phase.isSystem))
        persistStructure()
    }

    /** Custom phases rename through the phase API (catalog rows shared across the user's workouts); system phases are locked. */
    suspend fun renamePhase(index: Int, name: String) {
        val phase = workout.phases.getOrNull(index) ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == phase.name) return
        if (phase.isSystem) { phaseRenameError = "System phases can't be renamed. Add a custom phase instead."; return }
        try {
            WorkoutPhaseStore.rename(phase.id, trimmed)
        } catch (e: Exception) {
            phaseRenameError = e.message ?: "Couldn't rename the phase."
            return
        }
        updatePhase(index) { p ->
            p.copy(name = trimmed, taskRows = p.taskRows.map { row ->
                row.copy(task = row.task?.copy(phaseName = trimmed), advancedTasks = row.advancedTasks.map { it.copy(phaseName = trimmed) })
            })
        }
        persistStructure()
    }

    /** Delete a phase and everything in it — including logged sets of its exercises. */
    fun deletePhase(index: Int) {
        val phase = workout.phases.getOrNull(index) ?: return
        phase.allTasks.filter { it.isMovement }.forEach { clearLogs(it.id) }
        workout = workout.copy(phases = WorkoutRowOps.restampPhaseOrders(workout.phases.filterIndexed { i, _ -> i != index }))
        persistStructure()
    }

    fun movePhase(index: Int, up: Boolean) {
        val target = if (up) index - 1 else index + 1
        if (index !in workout.phases.indices || target !in workout.phases.indices) return
        val phases = workout.phases.toMutableList()
        val p = phases.removeAt(index)
        phases.add(target, p)
        workout = workout.copy(phases = WorkoutRowOps.restampPhaseOrders(phases))
        persistStructure()
    }

    // MARK: - Exercises (PRD §14, §19–20, §24–26)

    /** Append loose exercises to a phase, each with one open set. */
    fun addExercises(movements: List<MovementLibraryItem>, phaseIndex: Int) {
        val phase = workout.phases.getOrNull(phaseIndex) ?: return
        if (movements.isEmpty()) return
        val rows = phase.taskRows.toMutableList()
        for (movement in movements) {
            val fresh = DatabaseManager.movement(movement.id) ?: movement
            val task = QuickStartPlan.makeOpenTask(fresh).copy(order = rows.size, phaseId = phase.id, phaseName = phase.name, workoutPhaseId = phase.id)
            rows.add(TaskRow(task = task))
            DatabaseManager.logRecentMovement(fresh.id)
        }
        updatePhase(phaseIndex) { it.copy(taskRows = rows) }
        persistStructure()
    }

    /**
     * Build a superset / circuit / interval from picked movements, using the
     * builder's own grouping so the block is shaped exactly like one authored
     * in the editor (PRD §26). Members start with one open round.
     */
    fun addGroup(type: ExerciseGroupType, movements: List<MovementLibraryItem>, intervalMode: IntervalMode? = null, phaseIndex: Int) {
        val phase = workout.phases.getOrNull(phaseIndex) ?: return
        if (movements.size < WorkoutRowOps.minimumGroupCount(type)) return
        val picks = if (type == ExerciseGroupType.Superset) movements.take(2) else movements
        var rows = phase.taskRows
        val ids = mutableSetOf<String>()
        for (movement in picks) {
            val fresh = DatabaseManager.movement(movement.id) ?: movement
            val task = QuickStartPlan.makeOpenTask(fresh).copy(order = rows.size, phaseId = phase.id, phaseName = phase.name, workoutPhaseId = phase.id)
            ids.add(task.id)
            rows = rows + TaskRow(task = task)
            DatabaseManager.logRecentMovement(fresh.id)
        }
        val grouped = WorkoutRowOps.groupRows(
            rows = rows, selectedIds = ids, type = type,
            mode = if (type == ExerciseGroupType.Interval) (intervalMode ?: IntervalMode.HIIT) else null,
            passiveRestTypeId = ClassificationStore.restTypeId("Passive")
        )
        // The builder seeds 3 rounds; Quick Start plans nothing.
        val reset = grouped.map { row ->
            if (row.task != null && ids.contains(row.task.id)) {
                val opened = row.copy(task = QuickStartPlan.opened(row.task), advancedTasks = row.advancedTasks.map { QuickStartPlan.opened(it) })
                WorkoutRowOps.withRounds(opened, 1)
            } else row
        }
        updatePhase(phaseIndex) { it.copy(taskRows = reset) }
        persistStructure()
    }

    fun addRest(phaseIndex: Int, seconds: Int) {
        if (phaseIndex !in workout.phases.indices) return
        updatePhase(phaseIndex) { it.copy(taskRows = it.taskRows + TaskRow(task = WorkoutRowOps.makeRestTask(seconds, ClassificationStore.workoutTaskTypeId("Rest")))) }
        persistStructure()
    }

    /** Remove a loose exercise, a rest, or one member of a block. Logged sets of a removed exercise go with it. */
    fun removeTask(task: WorkoutTask) {
        val pi = phaseIndex(containing = task.id) ?: return
        if (task.isMovement) clearLogs(task.id)
        if (task.groupId.isNotEmpty()) {
            updatePhase(pi) { it.copy(taskRows = WorkoutRowOps.removedGroupMember(it.taskRows, task.id)) }
        } else {
            updatePhase(pi) { it.copy(taskRows = it.taskRows.filter { row -> row.task?.id != task.id }) }
        }
        persistStructure()
    }

    /** Remove a whole superset / circuit / interval row. */
    fun removeGroup(leadTaskId: String) {
        val pi = phaseIndex(containing = leadTaskId) ?: return
        val row = workout.phases[pi].taskRows.firstOrNull { it.task?.id == leadTaskId } ?: return
        (listOfNotNull(row.task) + row.advancedTasks).filter { it.isMovement }.forEach { clearLogs(it.id) }
        updatePhase(pi) { it.copy(taskRows = it.taskRows.filter { r -> r.task?.id != leadTaskId }) }
        persistStructure()
    }

    fun moveTask(taskId: String, toPhaseIndex: Int) {
        val destination = workout.phases.getOrNull(toPhaseIndex) ?: return
        val from = locateRow(taskId) ?: return
        if (from.first == toPhaseIndex) return
        val phases = workout.phases.toMutableList()
        val sourceRows = phases[from.first].taskRows.toMutableList()
        val row = WorkoutRowOps.rehomed(sourceRows.removeAt(from.second), destination)
        phases[from.first] = phases[from.first].copy(taskRows = sourceRows)
        phases[toPhaseIndex] = phases[toPhaseIndex].copy(taskRows = phases[toPhaseIndex].taskRows + row)
        workout = workout.copy(phases = phases)
        persistStructure()
    }

    fun phaseIndex(containing: String): Int? =
        workout.phases.indexOfFirst { phase -> phase.allTasks.any { it.id == containing } }.takeIf { it >= 0 }

    // MARK: - Set logging (PRD §21–23, §32–33)

    fun loggedSets(taskId: String): List<TaskSetConfig> = (session.actualSetLogs[taskId] ?: emptyMap()).values.sortedBy { it.setNumber }

    /** The Set Logging Circles: every logged set a ✓ and one open ⊕ after them — it never disappears (PRD §21–23). */
    fun setStates(taskId: String): List<SetState> = List(loggedSets(taskId).size) { SetState.COMPLETED } + SetState.UPCOMING

    fun nextSetNumber(taskId: String): Int = loggedSets(taskId).size + 1

    /** Log — or correct (PRD §33) — one set. Weight is stored in lbs. */
    fun logSet(taskId: String, setNumber: Int, weightLbs: Double?, reps: Int, durationSeconds: Int? = null, distance: DistanceValue? = null) {
        session.logSet(taskId, setNumber, weightLbs, reps, durationSeconds, distance?.value, distance?.unit?.rawValue)
        syncPlanToLogs()
    }

    /** Remove one logged set and close the gap so set numbers stay contiguous. */
    fun deleteSet(taskId: String, setNumber: Int) {
        val current = loggedSets(taskId)
        if (current.none { it.setNumber == setNumber }) return
        val remaining = current.filter { it.setNumber != setNumber }
        current.forEach { session.clearLoggedSet(taskId, it.setNumber) }
        remaining.forEachIndexed { offset, set ->
            session.logSet(taskId, offset + 1, set.weightKg, set.reps, set.durationSeconds, set.distance, set.distanceUnit)
        }
        syncPlanToLogs()
    }

    private fun clearLogs(taskId: String) {
        loggedSets(taskId).forEach { session.clearLoggedSet(taskId, it.setNumber) }
        exerciseNotes = exerciseNotes - taskId
        DatabaseManager.deleteSessionExerciseNote(session.sessionId, taskId)
    }

    // MARK: - Exercise RPE + Training Log (PRD §34–36)

    fun exerciseNote(taskId: String): SessionExerciseNote? = exerciseNotes[taskId]

    fun saveExerciseNote(task: WorkoutTask, rpe: Int?, trainingLog: String?) {
        val trimmed = trainingLog?.trim().orEmpty()
        val note = SessionExerciseNote(
            sessionId = session.sessionId, taskId = task.id, movementId = task.movementId, taskName = task.name,
            rpe = rpe?.let(ExerciseRPE::clamp), trainingLog = trimmed.ifEmpty { null }, updatedAtMs = ActiveWorkoutSession.clock()
        )
        exerciseNotes = if (note.isEmpty) exerciseNotes - task.id else exerciseNotes + (task.id to note)
        DatabaseManager.upsertSessionExerciseNote(note)
    }

    // MARK: - Timer (PRD §10)

    fun togglePause() {
        if (isPaused) session.resume() else session.pause()
        isPaused = !session.isCounting
    }

    // MARK: - Finish (PRD §40–43)

    val finishConfirmationMessage: String get() = QuickStartPlan.finishMessage(unloggedExerciseCount, loggedSetCount)

    fun requestFinish() { showFinishConfirmation = true }

    fun confirmFinish() {
        showFinishConfirmation = false
        completedElapsedSeconds = session.secondsElapsed
        summaryAchievements = buildAchievements()
        isWorkoutComplete = true
    }

    fun continueWorkout() { showFinishConfirmation = false; isWorkoutComplete = false }

    /** What the user actually did (PRD §41): the same rows the saved session records. */
    val summaryStats: WorkoutSummaryStats
        get() {
            val tasks = QuickStartPlan.pruneUnlogged(workout, loggedTaskIds).allTasks
            return WorkoutSummaryStats.build(
                tasks, session.actualSetLogs, session.progress, SessionProgress.requiredIterations(tasks),
                completedElapsedSeconds ?: session.secondsElapsed,
                session.totalDistance(DistanceUnit.current)?.let { DistanceValue(it, DistanceUnit.current) }
            )
        }

    private fun buildAchievements(): SessionAchievements {
        val tasks = QuickStartPlan.pruneUnlogged(workout, loggedTaskIds).allTasks
        val rows = SessionHistoryRows.build(tasks, session.actualSetLogs, session.progress, SessionProgress.requiredIterations(tasks))
        return SessionAchievementsBuilder.build(rows, session.sessionId, workout.id, null)
    }

    /**
     * Save: the workout keeps only what was logged, the session row and
     * history rows are written + queued for the server, the Quick Start meta
     * is re-stamped, and the shared session is cleared. The screen then
     * lands the user on Home; the saved workout is in My Content (PRD §42–43).
     */
    fun finish(rpe: Int?) {
        val sid = session.sessionId
        val duration = completedElapsedSeconds ?: session.secondsElapsed
        workout = QuickStartPlan.pruneUnlogged(workout, loggedTaskIds)
        syncPlanToLogs(persistLocal = false)
        save()
        val tasks = workout.allTasks
        SessionRecorder.save(
            sessionId = sid, workoutId = workout.id, workoutName = workout.name, workoutTasks = tasks,
            startDateMs = session.startDateMs, durationSeconds = duration,
            actualSetLogs = session.actualSetLogs, progress = session.progress,
            requiredIterations = SessionProgress.requiredIterations(tasks), rpe = rpe
        )
        DatabaseManager.updateSessionQuickStartMeta(sid, focus.name, true)
        session.reset()
    }

    /** Discard Session: the session and its logs go, and so does the workout minted for it. */
    fun discard(onDone: () -> Unit = {}) {
        showFinishConfirmation = false
        val id = workout.id
        val sid = session.sessionId
        viewModelScope.launch {
            ActiveWorkoutSession.discard(sid)
            runCatching { WorkoutStore.deleteWorkout(id) }
            onDone()
        }
    }

    // MARK: - Plan ⇄ logs

    /**
     * Rewrite the workout so its set counts and per-set values mirror the
     * logs (one open set where nothing is logged yet), then register the
     * result with the session so required-set counts, completion % and
     * history rows all describe what happened.
     */
    fun syncPlanToLogs(persistLocal: Boolean = true) {
        var changed = false
        val phases = workout.phases.map { phase ->
            val rows = phase.taskRows.map { row ->
                val updated = QuickStartPlan.mirrorLogs(row) { loggedSets(it) }
                if (updated != row) changed = true
                updated
            }
            phase.copy(taskRows = rows)
        }
        if (changed) {
            workout = workout.copy(phases = phases)
            if (persistLocal) DatabaseManager.insertWorkout(workout)
        }
        session.registerTasks(workout.allTasks)
        tick += 1
    }

    // MARK: - Save pipeline (the editor's, without the header / image / tag concerns)

    /** Local write + queued server save after any structural change. */
    private fun persistStructure() {
        syncPlanToLogs(persistLocal = false)
        save()
    }

    private fun save(): Job {
        if (workout.name.isBlank()) workout = workout.copy(name = "Quick Start")
        val wasNew = isNewWorkout
        DatabaseManager.insertWorkout(workout)
        return viewModelScope.launch {
            try {
                val snapshot = workout
                if (WorkoutPhaseStore.hasUnsyncedPhase(snapshot.phases.map { it.id })) SyncManager.drain(SyncDomain.WORKOUT_PHASES)
                val forPush = DatabaseManager.workout(snapshot.id) ?: snapshot
                val inputs = WorkoutTaskInputs.taskInputs(forPush) { ClassificationStore.workoutTaskTypeId(it) }
                if (wasNew) {
                    val request = WorkoutTaskInputs.createRequest(forPush, inputs, statusId = ClassificationStore.contentStatusId("Active"))
                    val saved = withContext(Dispatchers.IO) { WorkoutStore.commitCreate(forPush.id, request) }
                    if (workout.id.equals(snapshot.id, true)) workout = workout.copy(id = saved.id.lowercase(Locale.US))
                    isNewWorkout = false
                } else {
                    runCatching { withContext(Dispatchers.IO) { WorkoutStore.commitUpdateShell(forPush.id, WorkoutTaskInputs.updateRequest(forPush)) } }
                        .onFailure { if (WorkoutStore.isFatalCommitError(it)) throw it }
                    withContext(Dispatchers.IO) { WorkoutStore.commitReplaceTasks(forPush.id, inputs) }
                    WorkoutStore.notifySaveDidCommit(forPush.id)
                }
            } catch (e: ApiError.Offline) {
                // Queued; the drain lands it.
            } catch (e: Exception) {
                Log.i(TAG, "quick start save deferred: ${e.message}")
            }
        }
    }

    // MARK: - Helpers

    private fun locateRow(taskId: String): Pair<Int, Int>? {
        workout.phases.forEachIndexed { pi, phase ->
            val ri = phase.taskRows.indexOfFirst { it.task?.id == taskId }
            if (ri >= 0) return pi to ri
        }
        return null
    }

    private fun updatePhase(index: Int, transform: (WorkoutPhase) -> WorkoutPhase) {
        val phases = workout.phases.toMutableList()
        phases[index] = transform(phases[index])
        workout = workout.copy(phases = phases)
    }

    companion object {
        private const val TAG = "QuickStartViewModel"
    }
}
