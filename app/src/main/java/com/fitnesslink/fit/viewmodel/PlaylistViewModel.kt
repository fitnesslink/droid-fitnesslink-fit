package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.SessionOverrides
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.session.ActiveWorkoutSession
import com.fitnesslink.fit.session.ExerciseState
import com.fitnesslink.fit.session.SessionAchievements
import com.fitnesslink.fit.session.SessionAchievementsBuilder
import com.fitnesslink.fit.session.SessionFinishRule
import com.fitnesslink.fit.session.SessionHistoryRows
import com.fitnesslink.fit.session.SessionMode
import com.fitnesslink.fit.session.SessionRecorder
import com.fitnesslink.fit.session.SessionWorkoutRefresh
import com.fitnesslink.fit.session.SetState
import com.fitnesslink.fit.session.WorkoutSummaryStats
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Playlist mode — port of iOS `PlaylistViewModel`. Non-interactive: the user follows along at
 * their own pace, logs what they did per movement and marks each as complete. Timer + logs +
 * completion marks live on [ActiveWorkoutSession] so switching to Interactive keeps them.
 */
class PlaylistViewModel : ViewModel() {
    var workout by mutableStateOf(Workout())
        private set
    var isLoadingWorkout by mutableStateOf(false)
        private set
    var isWorkoutComplete by mutableStateOf(false)
        private set
    var showFinishConfirmation by mutableStateOf(false)
        private set
    var logTargetTaskId by mutableStateOf<String?>(null)
        private set
    var logTargetSetNumber by mutableStateOf<Int?>(null)
        private set
    /** Set by `loadData`: the resumed session was last in Interactive Mode (FTNSSLNK-404). One-shot. */
    var shouldReopenInteractive by mutableStateOf(false)
    var summaryAchievements by mutableStateOf(SessionAchievements())
        private set
    var completedElapsedSeconds: Int? = null
        private set
    /** Mirrors the shared session's version so rows re-read per-set state. */
    var tick by mutableIntStateOf(0)
        private set

    /** Set by the playlist screen; rows and the list footer send their requests here. */
    var onEditRequest: ((PlaylistEditRequest) -> Unit)? = null

    private val session: ActiveWorkoutSession get() = ActiveWorkoutSession

    val sessionId: String get() = session.sessionId
    val startDateMs: Long get() = session.startDateMs
    val secondsElapsed: Int get() = session.secondsElapsed
    val timerText: String get() = session.timerText
    val actualSetLogs: Map<String, Map<Int, TaskSetConfig>> get() = session.actualSetLogs
    val completedTaskIds: Set<String> get() = session.completedTaskIds
    val completionPercent: Int get() = session.completionPercent
    val incompleteExerciseCount: Int get() = session.unresolvedTaskIds.size
    val canFinishWithoutConfirmation: Boolean get() = incompleteExerciseCount == 0
    val finishConfirmationMessage: String get() = SessionFinishRule.message(incompleteExerciseCount)
    val movementTasks: List<WorkoutTask> get() = workout.allTasks.filter { it.isMovement }
    val exerciseCount: Int get() = movementTasks.size
    val isWorkoutEditable: Boolean get() = SessionWorkoutEditor.isEditable(workout)
    val hasSessionOnlyEdits: Boolean get() = !session.overrides.isEmpty

    init {
        viewModelScope.launch { session.state.collect { tick += 1 } }
    }

    val summaryStats: WorkoutSummaryStats
        get() = WorkoutSummaryStats.build(
            workout.allTasks, session.actualSetLogs, session.progress, session.requiredIterations,
            completedElapsedSeconds ?: session.secondsElapsed,
            session.totalDistance(DistanceUnit.current)?.let { DistanceValue(it, DistanceUnit.current) }
        )

    fun setStates(taskId: String): List<SetState> = session.setStates(taskId)
    fun exerciseState(taskId: String): ExerciseState = session.exerciseState(taskId)
    fun isSetLogged(taskId: String, setNumber: Int): Boolean = session.isSetLogged(taskId, setNumber)
    fun isCompleted(taskId: String): Boolean = session.isCompleted(taskId)
    fun toggleCompleted(taskId: String) { session.toggleCompleted(taskId) }

    // MARK: - Load

    fun loadData(workoutId: String, programId: String? = null) {
        val cached = DatabaseManager.workout(workoutId) ?: Workout()
        session.begin(workoutId, programId)
        shouldReopenInteractive = session.lastMode == SessionMode.INTERACTIVE
        applyWorkout(cached)
        if (!shouldReopenInteractive) session.recordMode(SessionMode.PLAYLIST)
        refreshFromServerIfNeeded(workoutId, cached)
    }

    fun refreshFromServerIfNeeded(workoutId: String, cached: Workout) {
        if (!SessionWorkoutRefresh.shouldRefresh(workoutId, cached, session)) return
        isLoadingWorkout = true
        viewModelScope.launch {
            try {
                SessionWorkoutRefresh.refresh(workoutId, cached, session)?.let { applyWorkout(it) }
            } finally {
                isLoadingWorkout = false
            }
        }
    }

    /** Re-read the cached workout (e.g. after the interactive session added / swapped exercises). */
    fun reload(workoutId: String) {
        DatabaseManager.workout(workoutId)?.let { applyWorkout(it) }
    }

    fun recordPlaylistMode() = session.recordMode(SessionMode.PLAYLIST)

    fun applyWorkout(workout: Workout) {
        var loaded = workout.hydrateGroupRounds()
        val overrides = session.overrides
        if (!overrides.isEmpty) {
            val perPhase = overrides.apply(loaded.phases.map { it.allTasks }, loaded.phases.map { it.id })
            loaded = loaded.copy(phases = loaded.phases.mapIndexed { i, phase ->
                if (i < perPhase.size) phase.copy(taskRows = SessionOverrides.rows(perPhase[i])) else phase
            })
        }
        this.workout = loaded
        session.registerTasks(loaded.allTasks)
        tick += 1
    }

    // MARK: - Do → Build

    fun requestEdit(request: PlaylistEditRequest) { onEditRequest?.invoke(request) }

    private val editScope: SessionWorkoutEditor.Scope
        get() = if (isWorkoutEditable) SessionWorkoutEditor.Scope.PERMANENT else SessionWorkoutEditor.Scope.SESSION_ONLY

    private val targetWorkoutId: String get() = session.workoutId ?: workout.id

    fun addExercise(movement: MovementLibraryItem, afterTaskId: String?, scope: SessionWorkoutEditor.Scope? = null) {
        val id = targetWorkoutId.ifEmpty { return }
        val all = workout.allTasks
        val template = afterTaskId?.let { a -> all.firstOrNull { it.id == a } } ?: all.lastOrNull()
        SessionWorkoutEditor.add(movement, id, afterTaskId, null, template, scope ?: editScope)
        reload(id)
    }

    fun addExerciseToPhase(movement: MovementLibraryItem, toPhaseId: String, scope: SessionWorkoutEditor.Scope? = null) {
        val id = targetWorkoutId.ifEmpty { return }
        SessionWorkoutEditor.add(movement, id, null, toPhaseId, null, scope ?: editScope)
        reload(id)
    }

    fun swapMovement(taskId: String, movement: MovementLibraryItem, scope: SessionWorkoutEditor.Scope? = null) {
        val id = targetWorkoutId.ifEmpty { return }
        SessionWorkoutEditor.swap(taskId, movement, id, scope ?: editScope)
        reload(id)
    }

    fun removeExercise(taskId: String, scope: SessionWorkoutEditor.Scope? = null) {
        val id = targetWorkoutId.ifEmpty { return }
        SessionWorkoutEditor.remove(taskId, id, scope ?: editScope)
        reload(id)
    }

    fun updateSets(taskId: String, sets: Int, reps: Int, scope: SessionWorkoutEditor.Scope? = null) {
        val id = targetWorkoutId.ifEmpty { return }
        val weight = workout.allTasks.firstOrNull { it.id == taskId }?.weightKg
        SessionWorkoutEditor.updateSets(taskId, sets, reps, weight, id, scope ?: editScope)
        reload(id)
    }

    fun saveMyVersion(): String? = targetWorkoutId.ifEmpty { null }?.let { SessionWorkoutEditor.saveMyVersion(it) }

    // MARK: - Logging

    fun openLogSheet(taskId: String, setNumber: Int? = null) { logTargetSetNumber = setNumber; logTargetTaskId = taskId }
    fun closeLogSheet() { logTargetTaskId = null; logTargetSetNumber = null }

    /** One hint per screen: under the first exercise, while nothing in this session has been logged yet (FTNSSLNK-444). */
    fun showsLoggingHint(taskId: String): Boolean {
        if (!actualSetLogs.values.all { it.isEmpty() }) return false
        val first = movementTasks.firstOrNull { setStates(it.id).isNotEmpty() } ?: return false
        return first.id == taskId
    }

    /** `Set 2 · 135 lb × 10` — the most recent set logged for a task. */
    fun latestLoggedSetLine(taskId: String, unit: String): String? {
        val logs = actualSetLogs[taskId] ?: return null
        val (number, log) = logs.maxByOrNull { it.key } ?: return null
        val parts = mutableListOf<String>()
        val lbs = log.weightKg
        if (lbs != null && lbs > 0) parts.add("${WeightUnit.displayNumber(fromLbs = lbs)} ${WeightUnit.current.label}")
        if (log.reps > 0) parts.add(if (parts.isEmpty()) "${log.reps} reps" else "× ${log.reps}")
        else log.durationSeconds?.takeIf { it > 0 }?.let { parts.add(WorkoutTask.humanizeDuration(it)) }
        val detail = parts.joinToString(" ")
        return if (detail.isEmpty()) "$unit $number logged" else "$unit $number · $detail"
    }

    fun logSet(taskId: String, setNumber: Int, weightLbs: Double?, reps: Int, durationSeconds: Int? = null) {
        session.logSet(taskId, setNumber, weightLbs, reps, durationSeconds)
    }

    fun clearLoggedSet(taskId: String, setNumber: Int) = session.clearLoggedSet(taskId, setNumber)

    // MARK: - Finish

    fun requestFinish() { if (canFinishWithoutConfirmation) complete() else showFinishConfirmation = true }
    fun finishAnyway() { showFinishConfirmation = false; complete() }
    fun continueWorkout() { showFinishConfirmation = false }

    private fun complete() {
        completedElapsedSeconds = session.secondsElapsed
        summaryAchievements = buildAchievements()
        isWorkoutComplete = true
    }

    private fun buildAchievements(): SessionAchievements {
        val rows = SessionHistoryRows.build(workout.allTasks, session.actualSetLogs, session.progress, session.requiredIterations)
        return SessionAchievementsBuilder.build(rows, session.sessionId, workout.id, session.programId)
    }

    /** Discard Session: abandoned and removed, nothing saved, the workout untouched. */
    fun discardSession(onDone: () -> Unit = {}) {
        showFinishConfirmation = false
        val id = session.sessionId
        viewModelScope.launch { ActiveWorkoutSession.discard(id); onDone() }
    }

    /** Both the RPE submit and the dismiss path save, so an explicit Finish never leaves a ghost session. */
    fun finish(rpe: Int?) {
        SessionRecorder.save(
            sessionId = session.sessionId, workoutId = workout.id, workoutName = workout.name, workoutTasks = workout.allTasks,
            startDateMs = session.startDateMs, durationSeconds = completedElapsedSeconds ?: session.secondsElapsed,
            actualSetLogs = session.actualSetLogs, progress = session.progress, requiredIterations = session.requiredIterations, rpe = rpe
        )
        session.reset()
    }
}
