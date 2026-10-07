package com.fitnesslink.fit.session

import android.util.Log
import com.fitnesslink.fit.calendar.CalendarSyncService
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.ModelGson
import com.fitnesslink.fit.model.SessionOverrides
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WorkoutSession
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.WorkoutSessionStatusId
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.SessionCompleteRequest
import com.fitnesslink.fit.network.dto.SessionHistoryEntry
import com.fitnesslink.fit.network.dto.SessionHistoryLogRequest
import com.fitnesslink.fit.network.dto.SessionProgressRequest
import com.fitnesslink.fit.network.dto.SessionStartRequest
import com.fitnesslink.fit.network.dto.toCreateRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.pendingSessionStartIds
import com.fitnesslink.fit.persistence.renameWorkoutSession
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import com.fitnesslink.fit.sync.TransientSyncException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Navigation-level outcomes other screens react to (CONTRACTS). */
sealed class SessionEvent {
    data class Completed(val sessionId: String, val workoutId: String) : SessionEvent()
    data class Discarded(val sessionId: String) : SessionEvent()
}

/** What the UI observes for the whole-workout timer and the per-mutation version. */
data class ActiveSessionState(
    val sessionId: String = "",
    val workoutId: String? = null,
    val secondsElapsed: Int = 0,
    val timerText: String = "00:00:00",
    val isCounting: Boolean = false,
    /** Bumped on every per-set / override / breadcrumb mutation. */
    val version: Int = 0
)

/**
 * Live state of the user's in-progress workout session. Shared between the Playlist and
 * Interactive players so flipping between modes preserves the timer, logs and completion marks.
 * Port of iOS `ActiveWorkoutSession` (PlaylistViewModel.swift). Every mutation persists
 * immediately to SQLite; server writes are queue-first with an inline attempt.
 */
object ActiveWorkoutSession {
    private const val TAG = "ActiveWorkoutSession"

    /** Sync-queue tags (producer ↔ drainer agreement). */
    val sessionsQueueEntityType: String = SyncDomain.SESSIONS.wireName
    val sessionProgressQueueEntityType: String = SyncDomain.SESSION_PROGRESS.wireName

    /** Nobody trains for a day straight: an unfinished session older than this is abandoned instead of resumed. */
    const val resumeWindowMs: Long = 24L * 60 * 60 * 1000
    val resumeWindowDays: Int get() = maxOf(1, (resumeWindowMs / 86_400_000L).toInt())

    fun isStale(startDateMs: Long, nowMs: Long = clock()): Boolean = nowMs - startDateMs > resumeWindowMs

    /** Injectable wall clock (epoch millis). */
    @Volatile var clock: () -> Long = { System.currentTimeMillis() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(ActiveSessionState())
    val state: StateFlow<ActiveSessionState> = _state.asStateFlow()

    data class ActiveSetKey(val taskId: String, val iteration: Int)

    // MARK: - State

    var workoutId: String? = null
        private set
    var sessionId: String = UUID.randomUUID().toString().lowercase(Locale.US)
        private set
    var startDateMs: Long = clock()
        private set
    var secondsElapsed: Int = 0
        private set
    val timerText: String get() = formatTimer(secondsElapsed)

    /** Resume breadcrumbs mirrored from the server / the local row. */
    var programId: String? = null
        private set
    var workoutPhaseId: String? = null
        private set
    var currentIndex: Int? = null
        private set
    var nextMovementId: String? = null
        private set
    /** Round inside a superset / circuit at [currentIndex]. Local-only breadcrumb. */
    var currentRound: Int? = null
        private set
    var statusId: String? = null
        private set

    /** Session-only edits (Do → Build) for a workout the user can't edit at the source. */
    var overrides: SessionOverrides = SessionOverrides()
        private set

    /** Logged sets keyed `[taskId][setNumber]`. */
    var actualSetLogs: Map<String, Map<Int, TaskSetConfig>> = emptyMap()
        private set

    /** Per-set outcomes, persisted as JSON on the session row on every change. */
    var progress: SessionProgress = SessionProgress()
        private set

    /** Sets (loose) or rounds (group members) each movement task must complete. */
    var requiredIterations: Map<String, Int> = emptyMap()
        private set

    private var registeredTasks: Map<String, WorkoutTask> = emptyMap()

    /** The set the interactive cursor is on — reported as ACTIVE until it has an outcome. */
    var activeSet: ActiveSetKey? = null
        set(value) { field = value; bump() }

    private var legacyCompletedTaskIds: Set<String> = emptySet()

    /** The player the session was last open in (FTNSSLNK-404). */
    var lastMode: SessionMode? = null
        private set

    /** Workout ids refreshed from the server this launch. Per launch, not per session. */
    var refreshedWorkoutIds: Set<String> = emptySet()
        private set

    /** Where the interactive session left an interval block when its view was torn down. In-memory only. */
    var intervalSnapshot: IntervalRunner.Snapshot? = null

    private var startContext: Triple<String, String?, String?>? = null

    /** Wall-clock instant active counting last (re)started. Null while paused. */
    private var activeAnchorMs: Long? = null
    private var accumulatedSeconds: Int = 0
    private var tickerJob: Job? = null

    val isCounting: Boolean get() = activeAnchorMs != null

    private val userId: String get() = DatabaseManager.user()?.id ?: "user1"

    private fun isGuid(s: String): Boolean = runCatching { UUID.fromString(s) }.isSuccess

    init {
        ensureRegistered()
    }

    // MARK: - Derived

    val completedTaskIds: Set<String> get() = progress.completedTaskIds(requiredIterations)
    val completionPercent: Int get() = progress.completionPercent(requiredIterations)
    val completedSetCount: Int get() = progress.completedSetCount(requiredIterations)
    val requiredSetCount: Int get() = requiredIterations.values.sum()
    val unresolvedTaskIds: Set<String> get() = progress.unresolvedTaskIds(requiredIterations)

    fun markWorkoutRefreshed(workoutId: String) { refreshedWorkoutIds = refreshedWorkoutIds + workoutId.lowercase(Locale.US) }
    fun clearWorkoutRefreshMarks() { refreshedWorkoutIds = emptySet() }

    /** Note which player is showing the session, in memory and on the row. */
    fun recordMode(mode: SessionMode) {
        lastMode = mode
        if (sessionId.isEmpty() || workoutId == null) return
        DatabaseManager.updateSessionLastMode(sessionId, mode.rawValue)
        bump()
    }

    fun restoreLastMode(mode: SessionMode?) { lastMode = mode }

    /** Replace [overrides] and persist them with the session row. */
    fun setOverrides(new: SessionOverrides) {
        overrides = new
        if (sessionId.isEmpty()) return
        DatabaseManager.updateSessionOverrides(sessionId, new.takeIf { !it.isEmpty })
        bump()
    }

    /** The workout this session runs on was renamed from a client-minted id to the server's. */
    fun adoptWorkoutId(localId: String, serverId: String) {
        val current = workoutId ?: return
        if (!current.equals(localId, ignoreCase = true) || serverId.equals(current, ignoreCase = true)) return
        workoutId = serverId.lowercase(Locale.US)
        DatabaseManager.updateWorkoutSessionWorkoutId(sessionId, serverId)
        bump()
    }

    // MARK: - Begin / resume

    /**
     * Start (or resume) a session. If the same workout is already active the timer keeps running
     * and existing logs are preserved. A different workout discards the prior in-memory session.
     */
    fun begin(workoutId: String, programId: String? = null, workoutPhaseId: String? = null) {
        val wid = workoutId.lowercase(Locale.US)
        if (this.workoutId == wid && tickerJob != null) return
        reset()
        this.workoutId = wid
        val cachedWorkout = DatabaseManager.workout(wid)
        val initialPhaseId = workoutPhaseId ?: cachedWorkout?.phases?.firstOrNull()?.id?.takeIf { it.isNotEmpty() }

        var existing = DatabaseManager.inProgressSession(wid)
        if (existing != null && isStale(existing.startDate)) {
            Log.i(TAG, "local session ${existing.id} is past the resume window — abandoning, starting fresh")
            commitAbandon(existing.id)
            DatabaseManager.discardInProgressSession(existing.id)
            existing = null
        }

        val uid = userId
        if (existing != null) {
            sessionId = existing.id.lowercase(Locale.US)
            startDateMs = existing.startDate
            secondsElapsed = maxOf(0, ((clock() - existing.startDate) / 1000).toInt())
            val restored = DatabaseManager.loadSessionLogs(existing.id)
            actualSetLogs = restored.logs
            val saved = SessionProgress.decode(DatabaseManager.sessionProgressJson(existing.id))
            if (saved != null) {
                progress = saved
            } else {
                progress = SessionProgress()
                legacyCompletedTaskIds = restored.completed
                for ((taskId, sets) in restored.logs) for (setNumber in sets.keys) progress.log(taskId, setNumber)
            }
            DatabaseManager.sessionResumeBreadcrumbs(existing.id)?.let { crumbs ->
                this.workoutPhaseId = crumbs.workoutPhaseId
                this.currentIndex = crumbs.currentIndex
                this.nextMovementId = crumbs.nextMovementId
                this.currentRound = crumbs.currentRound
            }
            this.programId = existing.programId ?: programId
            DatabaseManager.sessionOverrides(existing.id)?.let { overrides = it }
            restoreLastMode(SessionMode.fromRaw(DatabaseManager.sessionLastMode(existing.id)))
            startContext = Triple(uid, programId, initialPhaseId)
            Log.i(TAG, "resuming session $sessionId — re-issuing commitStart")
            commitStart(sessionId, wid, uid, programId, initialPhaseId) { applyResume(it) }
        } else {
            sessionId = UUID.randomUUID().toString().lowercase(Locale.US)
            startDateMs = clock()
            secondsElapsed = 0
            this.programId = programId
            startContext = Triple(uid, programId, initialPhaseId)
            DatabaseManager.insertWorkoutSession(
                id = sessionId, workoutId = wid, userId = uid, programId = programId,
                workoutName = cachedWorkout?.name ?: "", startDate = startDateMs, completionDate = null,
                durationSeconds = 0, isCompleted = false, exerciseCount = 0, totalSets = 0, totalReps = 0,
                totalWeightLifted = 0.0, totalCaloriesBurned = 0.0, rpeValue = null,
                statusId = WorkoutSessionStatusId.inProgress
            )
            Log.i(TAG, "starting new session $sessionId — commitStart")
            commitStart(sessionId, wid, uid, programId, initialPhaseId) { applyResume(it) }
            ensureCalendarEntryForToday(wid)
        }
        startCounting()
    }

    /** Anchor active counting to the wall clock from the current [secondsElapsed] and start the 1 Hz redraw. */
    fun startCounting() {
        accumulatedSeconds = secondsElapsed
        activeAnchorMs = clock()
        publishTimer()
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000)
                tick()
            }
        }
    }

    /** Re-derive [secondsElapsed] from the wall clock right now (foreground return). */
    fun resync() = tick()

    private fun tick() {
        activeAnchorMs?.let { anchor -> secondsElapsed = accumulatedSeconds + maxOf(0, ((clock() - anchor) / 1000).toInt()) }
        publishTimer()
    }

    /** Stop the timer and clear in-memory state. Called on explicit finish or when starting a different workout. */
    fun reset() {
        tickerJob?.cancel()
        tickerJob = null
        activeAnchorMs = null
        accumulatedSeconds = 0
        workoutId = null
        secondsElapsed = 0
        actualSetLogs = emptyMap()
        progress = SessionProgress()
        requiredIterations = emptyMap()
        registeredTasks = emptyMap()
        activeSet = null
        legacyCompletedTaskIds = emptySet()
        intervalSnapshot = null
        programId = null
        workoutPhaseId = null
        currentIndex = null
        nextMovementId = null
        currentRound = null
        overrides = SessionOverrides()
        statusId = null
        lastMode = null
        startContext = null
        publishTimer()
    }

    /**
     * Apply the server's `SessionDto` from a start call. Reconciles the local row's id with the
     * server-canonical id (cross-device dedup), adopts the server start date, then seeds resume
     * breadcrumbs. A server row past the resume window is abandoned and the start re-issued.
     */
    fun applyResume(resolved: WorkoutSession) {
        val serverId = resolved.id.lowercase(Locale.US)
        val serverStart = resolved.startDate?.time
        if (serverStart != null && !resolved.isCompletedOnServer && isStale(serverStart)) {
            Log.i(TAG, "server session $serverId is past the resume window — abandoning instead of resuming")
            val freshId = sessionId
            val context = startContext
            val wid = workoutId
            ioScope.launch {
                try {
                    ApiClient.sessionApi.abandon(serverId)
                } catch (e: ApiError.NotFound) {
                } catch (e: ApiError.Forbidden) {
                } catch (e: Exception) {
                    commitAbandon(serverId)
                    return@launch
                }
                if (serverId == freshId || context == null || wid == null || sessionId != freshId) return@launch
                commitStart(freshId, wid, context.first, context.second, context.third) { r -> if (sessionId == freshId) applyResume(r) }
            }
            return
        }
        if (serverId != sessionId) {
            Log.i(TAG, "reconciling id $sessionId → $serverId")
            DatabaseManager.renameWorkoutSession(sessionId, serverId)
            sessionId = serverId
        }
        if (serverStart != null) {
            startDateMs = serverStart
            secondsElapsed = maxOf(0, ((clock() - serverStart) / 1000).toInt())
            accumulatedSeconds = secondsElapsed
            if (activeAnchorMs != null) activeAnchorMs = clock()
            publishTimer()
        }
        programId = resolved.programId?.toString()?.lowercase(Locale.US) ?: programId
        workoutPhaseId = resolved.workoutPhaseId?.toString()?.lowercase(Locale.US)
        currentIndex = resolved.currentIndex
        nextMovementId = resolved.nextMovementId?.toString()?.lowercase(Locale.US)
        statusId = resolved.statusId?.toString()?.lowercase(Locale.US)
        DatabaseManager.updateSessionResumeBreadcrumbs(sessionId, workoutPhaseId, currentIndex, nextMovementId, currentRound)
        if (progress.isEmpty && actualSetLogs.isEmpty() && !resolved.isCompletedOnServer) hydrateProgressFromServer(sessionId)
        bump()
    }

    /** `GET /sessions/{id}/history` → per-set state for a session this install never saw. Silent. */
    fun hydrateProgressFromServer(targetId: String) {
        if (!NetworkMonitor.isConnected.value) return
        ioScope.launch {
            val entries = try { ApiClient.sessionApi.getHistory(targetId) } catch (_: Exception) { return@launch }
            if (entries.isEmpty()) return@launch
            withContext(Dispatchers.Main.immediate) {
                if (sessionId != targetId || !progress.isEmpty || actualSetLogs.isNotEmpty()) return@withContext
                applyRebuiltHistory(entries)
            }
        }
    }

    /** Adopt server history as the session's per-set state; logged sets are written to the local history table too. */
    fun applyRebuiltHistory(entries: List<SessionHistoryEntry>) {
        val wid = workoutId
        val tasks = wid?.let { DatabaseManager.workout(it)?.hydrateGroupRounds()?.allTasks } ?: emptyList()
        val rebuilt = rebuiltProgress(entries, tasks.map { it.id })
        progress = rebuilt.progress
        actualSetLogs = rebuilt.logs
        legacyCompletedTaskIds = emptySet()
        persistProgress()
        if (wid == null) return
        val uid = userId
        for ((taskId, sets) in rebuilt.logs) {
            val task = tasks.firstOrNull { it.id == taskId }
            for ((setNumber, config) in sets) {
                DatabaseManager.upsertSessionHistoryEntry(
                    sessionId = sessionId, taskId = taskId, workoutId = wid, userId = uid, setNumber = setNumber,
                    reps = config.reps, weightLbs = config.weightKg, taskName = task?.name ?: "",
                    durationSeconds = config.durationSeconds, movementId = task?.movementId,
                    distance = config.distance, distanceUnit = config.distanceUnit
                )
            }
        }
        Log.i(TAG, "rebuilt per-set state from server history: ${entries.size} rows, $completionPercent%")
    }

    /** Freeze the ticking but keep [secondsElapsed] (Playlist completion overlay). Interactive pause never calls it. */
    fun pause() {
        activeAnchorMs?.let { anchor ->
            accumulatedSeconds += maxOf(0, ((clock() - anchor) / 1000).toInt())
            secondsElapsed = accumulatedSeconds
        }
        activeAnchorMs = null
        tickerJob?.cancel()
        tickerJob = null
        publishTimer()
    }

    fun resume() {
        if (tickerJob != null || workoutId == null) return
        startCounting()
    }

    // MARK: - Per-set logging

    fun logSet(taskId: String, setNumber: Int, weightLbs: Double?, reps: Int, durationSeconds: Int? = null,
               distance: Double? = null, distanceUnit: String? = null) {
        val unit = DistanceUnit.fromWire(distanceUnit)
        val loggedDistance = if (unit != null && distance != null && distance > 0) DistanceValue(distance, unit) else null
        val byTask = (actualSetLogs[taskId] ?: emptyMap()).toMutableMap()
        byTask[setNumber] = TaskSetConfig(
            setNumber = setNumber, reps = reps, weightKg = weightLbs, durationSeconds = durationSeconds,
            distance = loggedDistance?.value, distanceUnit = loggedDistance?.unit?.rawValue
        )
        actualSetLogs = actualSetLogs + (taskId to byTask)
        progress.log(taskId, setNumber, durationSeconds, loggedDistance?.value ?: 0.0, loggedDistance?.unit?.rawValue ?: DistanceUnit.KM.rawValue)
        persistProgress()
        val wid = workoutId ?: return
        val task = registeredTasks[taskId] ?: DatabaseManager.workout(wid)?.allTasks?.firstOrNull { it.id == taskId }
        DatabaseManager.upsertSessionHistoryEntry(
            sessionId = sessionId, taskId = taskId, workoutId = wid, userId = userId, setNumber = setNumber,
            reps = reps, weightLbs = weightLbs, taskName = task?.name ?: "", durationSeconds = durationSeconds,
            movementId = task?.movementId, distance = loggedDistance?.value, distanceUnit = loggedDistance?.unit?.rawValue
        )
    }

    /** Sum of every distance logged this session in [unit]; null when none was. */
    fun totalDistance(unit: DistanceUnit = DistanceUnit.current): Double? {
        val values = actualSetLogs.values.flatMap { it.values }.mapNotNull { it.distanceValue }
        if (values.isEmpty()) return null
        return values.sumOf { it.converted(unit).value }
    }

    fun clearLoggedSet(taskId: String, setNumber: Int) {
        val byTask = actualSetLogs[taskId]?.toMutableMap()
        if (byTask != null) {
            byTask.remove(setNumber)
            actualSetLogs = if (byTask.isEmpty()) actualSetLogs - taskId else actualSetLogs + (taskId to byTask)
        }
        progress.clearLog(taskId, setNumber)
        persistProgress()
        DatabaseManager.deleteSessionHistoryEntry(sessionId, taskId, setNumber)
    }

    // MARK: - Per-set transitions

    /** Tell the session which tasks it runs on — every movement's set / round count. */
    fun registerTasks(tasks: List<WorkoutTask>) {
        requiredIterations = SessionProgress.requiredIterations(tasks)
        registeredTasks = tasks.associateBy { it.id }
        MovementHistoryStore.seedIfNeeded(tasks.mapNotNull { if (it.isMovement) it.movementId else null })
        if (legacyCompletedTaskIds.isEmpty()) { bump(); return }
        for (id in legacyCompletedTaskIds) requiredIterations[id]?.let { progress.completeExercise(id, it) }
        legacyCompletedTaskIds = emptySet()
        persistProgress()
    }

    fun required(taskId: String): Int = requiredIterations[taskId] ?: 1

    /** Stored outcome of a set, with ACTIVE for the set under the interactive cursor while it has no outcome yet. */
    fun setState(taskId: String, iteration: Int): SetState {
        val stored = progress.state(taskId, iteration)
        val active = activeSet
        if (stored == SetState.UPCOMING && active != null && active.taskId == taskId && active.iteration == iteration) return SetState.ACTIVE
        return stored
    }

    fun setStates(taskId: String): List<SetState> {
        val n = required(taskId)
        if (n <= 0) return emptyList()
        return (1..n).map { setState(taskId, it) }
    }

    fun isSetLogged(taskId: String, iteration: Int): Boolean = progress.isLogged(taskId, iteration)
    fun exerciseState(taskId: String): ExerciseState = progress.exerciseState(taskId, required(taskId))

    fun completeSet(taskId: String, iteration: Int, durationSeconds: Int? = null) { progress.complete(taskId, iteration, durationSeconds); persistProgress() }
    fun skipSet(taskId: String, iteration: Int) { progress.skipSet(taskId, iteration); persistProgress() }
    fun skipRemainingSets(taskId: String, from: Int) { progress.skipRemaining(taskId, from, required(taskId)); persistProgress() }
    fun markExerciseCompleted(taskId: String) { progress.completeExercise(taskId, required(taskId)); persistProgress() }
    fun revertExercise(taskId: String) { progress.revertExercise(taskId, required(taskId)); persistProgress() }
    fun toggleCompleted(taskId: String) { if (isCompleted(taskId)) revertExercise(taskId) else markExerciseCompleted(taskId) }
    fun isCompleted(taskId: String): Boolean = exerciseState(taskId) == ExerciseState.COMPLETED

    private fun persistProgress() {
        if (sessionId.isNotEmpty() && workoutId != null) {
            DatabaseManager.updateSessionProgress(sessionId, progress.encodedJSON(), completionPercent, completedTaskIds)
        }
        bump()
    }

    // MARK: - Breadcrumbs

    /** Persist a same-phase position advance to local SQLite only — no server write. */
    fun recordPositionLocally(currentIndex: Int?, nextMovementId: String?, currentRound: Int? = null) {
        if (currentRound != null) this.currentRound = currentRound
        if (sessionId.isEmpty()) return
        if (currentIndex != null) this.currentIndex = currentIndex
        if (nextMovementId != null) this.nextMovementId = nextMovementId
        DatabaseManager.updateSessionResumeBreadcrumbs(sessionId, null, currentIndex, nextMovementId, currentRound)
    }

    /** Persist a phase / position advance to the sync queue and apply it locally. */
    fun enqueueProgress(workoutPhaseId: String? = null, currentIndex: Int? = null, nextMovementId: String? = null) {
        if (workoutPhaseId == null && currentIndex == null && nextMovementId == null) return
        if (sessionId.isEmpty()) return
        if (workoutPhaseId != null) this.workoutPhaseId = workoutPhaseId
        if (currentIndex != null) this.currentIndex = currentIndex
        if (nextMovementId != null) this.nextMovementId = nextMovementId
        DatabaseManager.updateSessionResumeBreadcrumbs(sessionId, workoutPhaseId, currentIndex, nextMovementId, null)
        val payload = QueuedProgressPayload(sessionId, workoutPhaseId, currentIndex, nextMovementId)
        DatabaseManager.enqueueSyncEntry(sessionProgressQueueEntityType, sessionId, "progress", payload.encoded())
    }

    // MARK: - Resume surfaces (CONTRACTS)

    fun inProgressEntries(): List<InProgressSessionEntry> {
        val now = clock()
        return DatabaseManager.allInProgressSessions().map { row ->
            InProgressSessionEntry(
                sessionId = row.id, workoutId = row.workoutId,
                workoutName = row.workoutName.ifEmpty { "Workout" },
                completionPercent = row.completionPercent ?: 0,
                elapsedSeconds = maxOf(0L, (now - row.startDate) / 1000),
                lastMode = SessionMode.fromRaw(row.lastMode) ?: SessionMode.PLAYLIST,
                startedAt = row.startDate,
                programId = row.programId,
                hasCompletionPercent = row.completionPercent != null
            )
        }
    }

    fun inProgressEntry(workoutId: String): InProgressSessionEntry? =
        inProgressEntries().firstOrNull { it.workoutId.equals(workoutId, ignoreCase = true) }

    /** Abandon + delete local + notify. The workout itself is untouched. */
    suspend fun discard(sessionId: String) {
        commitAbandon(sessionId)
        withContext(Dispatchers.Main.immediate) {
            DatabaseManager.discardInProgressSession(sessionId)
            if (workoutId != null && this@ActiveWorkoutSession.sessionId.equals(sessionId, ignoreCase = true)) reset()
            _events.tryEmit(SessionEvent.Discarded(sessionId.lowercase(Locale.US)))
        }
    }

    /** Fire the completion event (SessionRecorder.save calls this). */
    internal fun emitCompleted(sessionId: String, workoutId: String) {
        _events.tryEmit(SessionEvent.Completed(sessionId.lowercase(Locale.US), workoutId.lowercase(Locale.US)))
    }

    // MARK: - Server writes (queue-first)

    /**
     * Enqueue the start request and try the inline POST. 4xx / 404 / 403 drop the queue row (logged);
     * transient errors stay queued for the drain. Refuses a userId that isn't a GUID.
     */
    fun commitStart(sessionId: String, workoutId: String, userId: String, programId: String? = null,
                    workoutPhaseId: String? = null, onResolved: ((WorkoutSession) -> Unit)? = null) {
        if (!isGuid(userId)) {
            Log.w(TAG, "commitStart skipped — userId '$userId' is not a Guid (auth not ready?)")
            return
        }
        val payload = QueuedStartPayload(sessionId, workoutId, programId, userId, workoutPhaseId, ApiClient.PLATFORM)
        val queueId = DatabaseManager.enqueueSyncEntry(sessionsQueueEntityType, sessionId, "start", payload.encoded())
        if (!NetworkMonitor.isConnected.value) { Log.i(TAG, "commitStart deferred — offline (queued)"); return }
        ioScope.launch {
            try {
                val resolved = ApiClient.sessionApi.start(payload.toRequest())
                Log.i(TAG, "commitStart success server-id=${resolved.id} minted-id=$sessionId")
                DatabaseManager.markSyncEntrySynced(queueId)
                withContext(Dispatchers.Main.immediate) { onResolved?.invoke(resolved) }
            } catch (e: Exception) {
                if (isFinal(e)) { Log.w(TAG, "commitStart final failure for $sessionId: ${e.message}"); DatabaseManager.markSyncEntrySynced(queueId) }
                else Log.i(TAG, "commitStart transient error for $sessionId: ${e.message}")
            }
        }
    }

    /** Enqueue a session-complete and try inline. A 404 stays queued until the start lands. */
    fun commitFinish(sessionId: String, rpeValue: Int? = null, completionPercent: Int? = null) {
        if (!isGuid(sessionId)) return
        val payload = QueuedCompletePayload(sessionId, rpeValue, completionPercent)
        val queueId = DatabaseManager.enqueueSyncEntry(sessionsQueueEntityType, sessionId, "complete", payload.encoded())
        if (!NetworkMonitor.isConnected.value) return
        ioScope.launch {
            try {
                ApiClient.sessionApi.complete(sessionId, SessionCompleteRequest.of(rpeId = null, rpeValue = rpeValue, completionPercent = completionPercent))
                DatabaseManager.markSyncEntrySynced(queueId)
            } catch (e: ApiError.NotFound) {
                Log.i(TAG, "commitFinish 404 for $sessionId — will retry after start lands")
            } catch (e: ApiError.Forbidden) {
                Log.i(TAG, "commitFinish 403 for $sessionId — will retry after start lands")
            } catch (e: Exception) {
                if (isFinal(e)) DatabaseManager.markSyncEntrySynced(queueId)
                else Log.i(TAG, "commitFinish transient error for $sessionId: ${e.message}")
            }
        }
    }

    /** Queue every per-set history row of a finished session and try them inline. */
    fun commitHistory(sessionId: String, entries: List<SessionHistoryLogRequest>) {
        if (!isGuid(sessionId) || entries.isEmpty()) return
        val queued = entries.mapNotNull { entry ->
            val payload = QueuedHistoryPayload(sessionId, entry).encoded() ?: return@mapNotNull null
            DatabaseManager.enqueueSyncEntry(sessionProgressQueueEntityType, sessionId, "history", payload) to entry
        }
        if (!NetworkMonitor.isConnected.value) return
        ioScope.launch {
            for ((queueId, entry) in queued) {
                try {
                    ApiClient.sessionApi.logHistory(sessionId, entry)
                    DatabaseManager.markSyncEntrySynced(queueId)
                } catch (e: ApiError.NotFound) {
                    return@launch
                } catch (e: ApiError.Forbidden) {
                    return@launch
                } catch (e: Exception) {
                    if (isFinal(e)) DatabaseManager.markSyncEntrySynced(queueId) else return@launch
                }
            }
        }
    }

    /** Enqueue a session-abandon and try inline. 404 = nothing to abandon (dropped). */
    fun commitAbandon(sessionId: String) {
        if (!isGuid(sessionId)) return
        val queueId = DatabaseManager.enqueueSyncEntry(sessionsQueueEntityType, sessionId, "abandon", sessionId)
        if (!NetworkMonitor.isConnected.value) return
        ioScope.launch {
            try {
                ApiClient.sessionApi.abandon(sessionId)
                DatabaseManager.markSyncEntrySynced(queueId)
            } catch (e: Exception) {
                if (isFinal(e) || e is ApiError.NotFound || e is ApiError.Forbidden) DatabaseManager.markSyncEntrySynced(queueId)
            }
        }
    }

    /** Mirrors [commitAbandon] for `DELETE /sessions/{id}`. */
    fun commitDelete(sessionId: String) {
        if (!isGuid(sessionId)) return
        val queueId = DatabaseManager.enqueueSyncEntry(sessionsQueueEntityType, sessionId, "delete", sessionId)
        if (!NetworkMonitor.isConnected.value) return
        ioScope.launch {
            try {
                ApiClient.sessionApi.delete(sessionId)
                DatabaseManager.markSyncEntrySynced(queueId)
            } catch (e: Exception) {
                if (isFinal(e) || e is ApiError.NotFound || e is ApiError.Forbidden) DatabaseManager.markSyncEntrySynced(queueId)
            }
        }
    }

    /** iOS `APIError.serverError(400..<500)`: replaying won't help. */
    private fun isFinal(e: Exception): Boolean = when (e) {
        is ApiError.ServerError -> e.statusCode in 400..499 && e.statusCode != 401
        is ApiError.DecodingError -> true
        is retrofit2.HttpException -> e.code() in 400..499 && e.code() != 401
        else -> false
    }

    // MARK: - Calendar

    /** If there's no calendar entry today for this workout, drop one in with `scheduledDate = now`. */
    fun ensureCalendarEntryForToday(workoutId: String) {
        val today = clock()
        val existing = DatabaseManager.calendarContent(forDate = today).firstOrNull { it.workoutId.equals(workoutId, ignoreCase = true) }
        if (existing != null) return
        val workout = DatabaseManager.workout(workoutId)
        val entry = FitnessContent(
            id = UUID.randomUUID().toString().lowercase(Locale.US),
            title = workout?.name?.ifEmpty { null } ?: "Workout",
            programId = "", workoutId = workoutId, mealPlanId = "", status = "scheduled", scheduledDate = today,
            workoutImageId = workout?.imageId?.toString(), workoutThumbnailId = workout?.thumbnailId?.toString()
        )
        DatabaseManager.insertCalendarContent(entry)
        mirrorDeviceCalendar()
        if (NetworkMonitor.isConnected.value) {
            ioScope.launch { try { ApiClient.calendarApi.create(entry.toCreateRequest()) } catch (_: Exception) {} }
        }
    }

    internal fun mirrorDeviceCalendar() {
        val context = SessionSettings.appContext ?: return
        try { CalendarSyncService.syncAll(context) } catch (_: Exception) {}
    }

    // MARK: - Sync drains

    private var registered = false

    /** Register the session drains + the stale-session purge with `SyncManager` (idempotent). */
    fun ensureRegistered() {
        if (registered) return
        registered = true
        SyncManager.registerPreDrainStep("purgeStaleSessions") {
            val ids = DatabaseManager.purgeStaleInProgressSessions(resumeWindowDays)
            for (id in ids) {
                if (isGuid(id)) DatabaseManager.enqueueSyncEntry(sessionsQueueEntityType, id, "abandon", id)
                if (workoutId != null && sessionId.equals(id, ignoreCase = true)) withContext(Dispatchers.Main.immediate) { reset() }
            }
        }
        SyncManager.registerDrain(SyncDomain.SESSIONS) { drainSessions() }
        SyncManager.registerDrain(SyncDomain.SESSION_PROGRESS) { drainSessionProgress() }
    }

    /** iOS `drainSessions`: start / abandon / delete / complete in createdAt order. */
    suspend fun drainSessions() {
        SyncManager.drainQueue(SyncDomain.SESSIONS) { entry ->
            when (entry.action) {
                "start" -> {
                    val payload = QueuedStartPayload.decode(entry.payload) ?: return@drainQueue
                    val resolved = ApiClient.sessionApi.start(payload.toRequest())
                    if (workoutId != null && sessionId.equals(payload.id, ignoreCase = true)) {
                        withContext(Dispatchers.Main.immediate) { applyResume(resolved) }
                    }
                }
                "abandon" -> try { ApiClient.sessionApi.abandon(entry.entityId) } catch (_: ApiError.NotFound) {} catch (_: ApiError.Forbidden) {}
                "delete" -> try { ApiClient.sessionApi.delete(entry.entityId) } catch (_: ApiError.NotFound) {} catch (_: ApiError.Forbidden) {}
                "complete" -> {
                    val payload = QueuedCompletePayload.decode(entry.payload, entry.entityId)
                    try {
                        ApiClient.sessionApi.complete(
                            payload.sessionId,
                            SessionCompleteRequest.of(rpeId = null, rpeValue = payload.rpeValue, completionPercent = payload.completionPercent)
                        )
                    } catch (e: ApiError.NotFound) {
                        throw TransientSyncException("start for ${payload.sessionId} has not landed", e)
                    }
                }
                else -> Unit
            }
        }
    }

    /** iOS `drainSessionProgress`: progress / history, skipping entries whose start hasn't landed. */
    suspend fun drainSessionProgress() {
        val pendingStarts = DatabaseManager.pendingSessionStartIds()
        SyncManager.drainQueue(SyncDomain.SESSION_PROGRESS) { entry ->
            if (entry.entityId.lowercase(Locale.US) in pendingStarts) throw TransientSyncException("start for ${entry.entityId} still queued")
            when (entry.action) {
                "progress" -> {
                    val payload = QueuedProgressPayload.decode(entry.payload) ?: return@drainQueue
                    try {
                        ApiClient.sessionApi.updateProgress(payload.sessionId, SessionProgressRequest(payload.workoutPhaseId, payload.currentIndex, payload.nextMovementId))
                    } catch (e: ApiError.NotFound) {
                        throw TransientSyncException("session ${payload.sessionId} not on server yet", e)
                    }
                }
                "history" -> {
                    val payload = QueuedHistoryPayload.decode(entry.payload) ?: return@drainQueue
                    try {
                        ApiClient.sessionApi.logHistory(payload.sessionId, payload.entry)
                    } catch (e: ApiError.NotFound) {
                        throw TransientSyncException("session ${payload.sessionId} not on server yet", e)
                    }
                }
                else -> Unit
            }
        }
    }

    // MARK: - Helpers

    private fun QueuedStartPayload.toRequest() = SessionStartRequest(
        id = id, workoutId = workoutId, programId = programId, userId = userId, workoutPhaseId = workoutPhaseId,
        platform = platform ?: ApiClient.PLATFORM
    )

    private fun publishTimer() {
        _state.value = _state.value.copy(
            sessionId = sessionId, workoutId = workoutId, secondsElapsed = secondsElapsed,
            timerText = timerText, isCounting = isCounting
        )
    }

    private fun bump() {
        _state.value = _state.value.copy(sessionId = sessionId, workoutId = workoutId, version = _state.value.version + 1)
    }

    fun formatTimer(seconds: Int): String =
        String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)

    /** JSON for a log map (debug / tests). */
    fun encodeLogs(logs: Map<String, Map<Int, TaskSetConfig>>): String = ModelGson.instance.toJson(logs)

    /** Today, as a Date, for callers that still speak `Date`. */
    fun now(): Date = Date(clock())
}
