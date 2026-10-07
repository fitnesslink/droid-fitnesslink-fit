package com.fitnesslink.fit.workouts

import android.util.Log
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.CreateWorkoutRequest
import com.fitnesslink.fit.network.dto.ReplaceWorkoutTasksRequest
import com.fitnesslink.fit.network.dto.UpdateWorkoutRequest
import com.fitnesslink.fit.network.dto.WorkoutTaskInput
import com.fitnesslink.fit.network.dto.toFitnessContent
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.deleteWorkoutCascade
import com.fitnesslink.fit.persistence.renameWorkout
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import com.fitnesslink.fit.sync.TransientSyncException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Calendar
import java.util.Collections
import java.util.Locale

/** Lifecycle notifications an open editor / detail screen keys off (iOS NotificationCenter names). */
sealed class WorkoutSaveEvent {
    /** A create landed server-side; the local row was renamed to [serverId]. */
    data class CreateDidApply(val localId: String, val serverId: String) : WorkoutSaveEvent()
    /** An update save (shell + replace-tasks) was accepted by the server. */
    data class SaveDidCommit(val workoutId: String) : WorkoutSaveEvent()
    /** A workout was deleted locally (and queued for the server). */
    data class Deleted(val workoutId: String) : WorkoutSaveEvent()
}

/**
 * Queue-first workout write pipeline — the Android home of iOS
 * `WorkoutEditorViewModel`'s commit / apply helpers plus
 * `SyncManager.drainWorkouts`. Every write is enqueued in `sync_queue`
 * before the network call so it survives an app kill; on success the row is
 * marked synced; fatal failures (400 / 403 / 404) drop the row; anything
 * else stays queued for the `SyncDomain.WORKOUTS` drain.
 */
object WorkoutStore {
    private const val TAG = "WorkoutStore"

    private val _events = MutableSharedFlow<WorkoutSaveEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<WorkoutSaveEvent> = _events.asSharedFlow()

    private val inFlightQueueIds: MutableSet<String> = Collections.synchronizedSet(HashSet())
    @Volatile private var registered = false

    /** Register the queue drain once per process. Safe to call repeatedly. */
    fun ensureRegistered() {
        if (registered) return
        registered = true
        WorkoutPhaseStore.ensureRegistered()
        SyncManager.registerDrain(SyncDomain.WORKOUTS) { drain() }
    }

    fun isQueueEntryInFlight(id: String): Boolean = inFlightQueueIds.contains(id)

    /** 400 validation, 403, 404 — the queued request will never succeed as-is. */
    fun isFatalCommitError(e: Throwable): Boolean = when (e) {
        is ApiError.ServerError -> e.statusCode == 400
        is ApiError.Forbidden, is ApiError.NotFound -> true
        else -> false
    }

    // MARK: - Commit helpers (queue-first)

    suspend fun commitCreate(localId: String, request: CreateWorkoutRequest): Workout {
        // Supersede any still-pending create for the same local id so a re-save can't mint two server workouts.
        DatabaseManager.pendingSyncEntries(SyncManager.WORKOUTS_TYPE)
            .filter { it.entityId.equals(localId, true) && it.action == "create" }
            .forEach { DatabaseManager.markSyncEntrySynced(it.id) }
        val queueId = DatabaseManager.enqueueSyncEntry(SyncManager.WORKOUTS_TYPE, localId, "create", ApiClient.gson.toJson(request))
        if (!NetworkMonitor.isConnected.value) throw ApiError.Offline
        inFlightQueueIds.add(queueId)
        try {
            val saved = applyCreate(localId, request)
            DatabaseManager.markSyncEntrySynced(queueId)
            return saved
        } catch (e: Exception) {
            if (isFatalCommitError(e)) DatabaseManager.markSyncEntrySynced(queueId)
            throw e
        } finally {
            inFlightQueueIds.remove(queueId)
        }
    }

    suspend fun commitUpdateShell(workoutId: String, request: UpdateWorkoutRequest) {
        val queueId = DatabaseManager.enqueueSyncEntry(SyncManager.WORKOUTS_TYPE, workoutId, "update-shell", ApiClient.gson.toJson(request))
        if (!NetworkMonitor.isConnected.value) throw ApiError.Offline
        inFlightQueueIds.add(queueId)
        try {
            ApiClient.workoutApi.update(workoutId, request)
            DatabaseManager.markSyncEntrySynced(queueId)
        } catch (e: Exception) {
            if (isFatalCommitError(e)) DatabaseManager.markSyncEntrySynced(queueId)
            throw e
        } finally {
            inFlightQueueIds.remove(queueId)
        }
    }

    suspend fun commitReplaceTasks(workoutId: String, tasks: List<WorkoutTaskInput>): Workout {
        val queueId = DatabaseManager.enqueueSyncEntry(
            SyncManager.WORKOUTS_TYPE, workoutId, "replace-tasks", ApiClient.gson.toJson(ReplaceWorkoutTasksRequest(tasks))
        )
        if (!NetworkMonitor.isConnected.value) throw ApiError.Offline
        inFlightQueueIds.add(queueId)
        try {
            val saved = applyReplaceTasks(workoutId, tasks)
            DatabaseManager.markSyncEntrySynced(queueId)
            return saved
        } catch (e: Exception) {
            if (isFatalCommitError(e)) DatabaseManager.markSyncEntrySynced(queueId)
            throw e
        } finally {
            inFlightQueueIds.remove(queueId)
        }
    }

    // MARK: - Apply (wire call + local reconcile; shared with the drain)

    suspend fun applyCreate(localId: String, request: CreateWorkoutRequest): Workout {
        val saved = ApiClient.workoutApi.create(request).hydrateGroupRounds()
        if (!saved.id.equals(localId, true)) DatabaseManager.renameWorkout(localId, saved.id)
        val local = DatabaseManager.workout(saved.id)
        val merged = WorkoutRowOps.mergePreservingPhases(local ?: saved, saved).copy(id = saved.id.lowercase(Locale.US))
        DatabaseManager.insertWorkout(merged)
        _events.tryEmit(WorkoutSaveEvent.CreateDidApply(localId.lowercase(Locale.US), saved.id.lowercase(Locale.US)))
        return saved
    }

    suspend fun applyReplaceTasks(workoutId: String, tasks: List<WorkoutTaskInput>): Workout {
        val saved = ApiClient.workoutApi.replaceTasks(workoutId, ReplaceWorkoutTasksRequest(tasks)).hydrateGroupRounds()
        val local = DatabaseManager.workout(workoutId)
        val merged = WorkoutRowOps.mergePreservingPhases(local ?: saved, saved)
        DatabaseManager.insertWorkout(merged)
        return saved
    }

    fun notifySaveDidCommit(workoutId: String) {
        _events.tryEmit(WorkoutSaveEvent.SaveDidCommit(workoutId.lowercase(Locale.US)))
    }

    // MARK: - Delete

    /** Local delete + cascade, queue the DELETE, try inline. */
    suspend fun deleteWorkout(id: String) {
        DatabaseManager.deleteWorkoutCascade(id)
        val queueId = DatabaseManager.enqueueSyncEntry(SyncManager.WORKOUTS_TYPE, id, "delete", "")
        _events.tryEmit(WorkoutSaveEvent.Deleted(id.lowercase(Locale.US)))
        if (!NetworkMonitor.isConnected.value) return
        try {
            ApiClient.workoutApi.delete(id)
            DatabaseManager.markSyncEntrySynced(queueId)
        } catch (e: ApiError.NotFound) {
            DatabaseManager.markSyncEntrySynced(queueId)
        } catch (e: Exception) {
            Log.i(TAG, "delete $id stays queued: ${e.message}")
        }
    }

    // MARK: - Customize (fork-to-edit, FTNSSLNK-250)

    /**
     * Fork a library / shared-in workout into an owned copy. Strictly
     * server-first: nothing is written locally until the fork resolves. Then
     * the fork is cached and today-forward calendar entries re-pulled (the
     * server re-pointed future entries on the source to the fork).
     */
    suspend fun customizeFork(sourceId: String): Workout {
        if (!NetworkMonitor.isConnected.value) throw ApiError.Offline
        val forked = ApiClient.workoutApi.fork(sourceId).hydrateGroupRounds()
        DatabaseManager.insertWorkout(forked)
        val cal = Calendar.getInstance()
        val startOfToday = String.format(
            Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        runCatching { ApiClient.calendarApi.getEntries(startDate = startOfToday) }
            .getOrNull()?.forEach { DatabaseManager.insertCalendarContent(it.toFitnessContent()) }
        return forked
    }

    fun forkFailureMessage(e: Throwable): String = when (e) {
        is ApiError.ServerError -> e.envelope?.error ?: e.envelope?.message ?: "Couldn't create your copy. Please try again."
        is ApiError.NotFound -> "This workout is no longer available."
        is ApiError.Offline -> "You're offline. Reconnect to customize this workout."
        else -> "Couldn't create your copy. Please try again."
    }

    fun templateSaveFailureMessage(e: Throwable): String = when (e) {
        is ApiError.ServerError -> if (e.statusCode == 409) "You already have a template with this name." else "Couldn't save the template. Please try again."
        is ApiError.Offline -> "You're offline. Reconnect to save templates."
        else -> "Couldn't save the template. Please try again."
    }

    // MARK: - Drain (iOS `SyncManager.drainWorkouts`)

    suspend fun drain() {
        SyncManager.drainQueue(SyncDomain.WORKOUTS) { entry ->
            // The editor is committing this row right now; replaying would race the inline POST.
            if (isQueueEntryInFlight(entry.id)) throw TransientSyncException("in flight")
            try {
                when (entry.action) {
                    "create" -> {
                        val req = SyncManager.decodePayload<CreateWorkoutRequest>(entry.payload) ?: return@drainQueue
                        applyCreate(entry.entityId, req)
                    }
                    "update-shell" -> {
                        val req = SyncManager.decodePayload<UpdateWorkoutRequest>(entry.payload) ?: return@drainQueue
                        ApiClient.workoutApi.update(entry.entityId, req)
                    }
                    "replace-tasks" -> {
                        val req = SyncManager.decodePayload<ReplaceWorkoutTasksRequest>(entry.payload) ?: return@drainQueue
                        applyReplaceTasks(entry.entityId, req.tasks)
                        notifySaveDidCommit(entry.entityId)
                    }
                    "delete" -> {
                        try { ApiClient.workoutApi.delete(entry.entityId) } catch (_: ApiError.NotFound) { /* already gone */ }
                    }
                    else -> return@drainQueue
                }
            } catch (e: ApiError.Forbidden) {
                // No longer ours — drop the local cache so the user sees the server's truth.
                if (entry.action != "delete") DatabaseManager.deleteWorkout(entry.entityId)
                throw e
            } catch (e: ApiError.NotFound) {
                if (entry.action != "delete") DatabaseManager.deleteWorkout(entry.entityId)
                throw e
            }
        }
    }
}
