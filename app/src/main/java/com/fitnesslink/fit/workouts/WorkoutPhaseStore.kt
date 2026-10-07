package com.fitnesslink.fit.workouts

import android.util.Log
import com.fitnesslink.fit.model.api.AvailableWorkoutPhase
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.AvailableWorkoutPhaseDto
import com.fitnesslink.fit.network.dto.ClassificationFallbackIds
import com.fitnesslink.fit.network.dto.CreateWorkoutPhaseRequest
import com.fitnesslink.fit.network.dto.UpdateWorkoutPhaseRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.pendingWorkoutPhaseIds
import com.fitnesslink.fit.persistence.renameWorkoutPhaseId
import com.fitnesslink.fit.persistence.rewritePhaseIdInSyncQueue
import com.fitnesslink.fit.persistence.rewritePhaseIdInWorkouts
import com.fitnesslink.fit.persistence.workoutPhaseSyncStates
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID

/**
 * Local catalog of available workout phases (iOS `WorkoutPhaseStore`).
 * Read-through cache over `DatabaseManager`'s `workout_phases` table:
 * composables collect [available]; [refresh] pulls `GET /workout-phases`
 * and reconciles; custom-phase create / rename / delete route through here
 * so offline creates queue in `sync_queue` and replay via the
 * `SyncDomain.WORKOUT_PHASES` drain with an id swap.
 */
object WorkoutPhaseStore {
    private const val TAG = "WorkoutPhaseStore"

    /** Canonical production rows for the 3 system phases — first-launch-offline seed only. */
    val systemDefaults: List<AvailableWorkoutPhase> = listOf(
        AvailableWorkoutPhase(ClassificationFallbackIds.PHASE_WARMUP.lowercase(Locale.US), "Warmup", 1, true),
        AvailableWorkoutPhase(ClassificationFallbackIds.PHASE_EXERCISES.lowercase(Locale.US), "Exercises", 2, true),
        AvailableWorkoutPhase(ClassificationFallbackIds.PHASE_COOL_DOWN.lowercase(Locale.US), "Cool Down", 3, true)
    )

    private val _available = MutableStateFlow<List<AvailableWorkoutPhase>>(emptyList())
    val available: StateFlow<List<AvailableWorkoutPhase>> get() { ensureHydrated(); return _available.asStateFlow() }

    @Volatile private var hydrated = false
    private var isRefreshing = false
    private val refreshMutex = Mutex()
    @Volatile private var registered = false

    val systemPhases: List<AvailableWorkoutPhase> get() = available.value.filter { it.isSystem }.sortedBy { it.sortOrder }
    val customPhases: List<AvailableWorkoutPhase> get() = available.value.filter { !it.isSystem }

    sealed class PhaseError(message: String) : Exception(message) {
        object EmptyName : PhaseError("Phase name can't be empty.")
        object DuplicateName : PhaseError("You already have a phase with that name.")
        object InUse : PhaseError("Remove this phase from all workouts before deleting.")
        object Forbidden : PhaseError("System phases can't be modified.")
        object NotFound : PhaseError("This phase no longer exists.")
        object Offline : PhaseError("You're offline. Reconnect to make changes.")
        class Unknown(message: String) : PhaseError(message)
    }

    /** Sync-queue payload for offline phase creates. */
    data class QueuedPhaseCreate(val localId: String, val name: String, val sortOrder: Int)

    /** Register the queue drain once per process. Safe to call repeatedly. */
    fun ensureRegistered() {
        if (registered) return
        registered = true
        SyncManager.registerDrain(SyncDomain.WORKOUT_PHASES) { drain() }
    }

    private fun ensureHydrated() {
        if (hydrated) return
        synchronized(this) {
            if (hydrated) return
            hydrated = true
            val rows = runCatching { DatabaseManager.availableWorkoutPhases() }.getOrDefault(emptyList())
            if (rows.isEmpty()) {
                runCatching { systemDefaults.forEach { DatabaseManager.upsertWorkoutPhase(it, "synced") } }
                _available.value = systemDefaults
            } else {
                _available.value = rows
            }
        }
    }

    private fun reload() {
        _available.value = runCatching { DatabaseManager.availableWorkoutPhases() }.getOrDefault(_available.value)
    }

    /** Pull the server list and reconcile the local cache. Best-effort. */
    suspend fun refresh() {
        ensureHydrated()
        if (!NetworkMonitor.isConnected.value || isRefreshing) return
        refreshMutex.withLock {
            isRefreshing = true
            try {
                val remote = runCatching { ApiClient.workoutPhaseApi.listAvailable() }
                    .onFailure { Log.w(TAG, "phase refresh failed: ${it.message}") }
                    .getOrNull() ?: return
                val states = DatabaseManager.workoutPhaseSyncStates()
                remote.forEach { DatabaseManager.upsertWorkoutPhase(it.toModel(), "synced") }
                val remoteIds = remote.map { it.id.lowercase(Locale.US) }.toSet()
                states.filter { (id, state) -> state == "synced" && !remoteIds.contains(id) }
                    .keys.forEach { DatabaseManager.deleteWorkoutPhase(it) }
                reload()
            } finally {
                isRefreshing = false
            }
        }
    }

    /**
     * Create a user-authored phase. Online: optimistic pending row, POST,
     * swap to the server id. Offline / transient failure: pending row +
     * queued create, local id returned so the workout can reference it now.
     */
    suspend fun create(name: String): String {
        ensureHydrated()
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw PhaseError.EmptyName
        val nextSort = (_available.value.maxOfOrNull { it.sortOrder } ?: 99) + 1
        val localId = UUID.randomUUID().toString().lowercase(Locale.US)
        DatabaseManager.upsertWorkoutPhase(AvailableWorkoutPhase(localId, trimmed, nextSort, false), "pending")
        reload()

        if (!NetworkMonitor.isConnected.value) {
            enqueueCreate(localId, trimmed, nextSort)
            return localId
        }
        return try {
            val serverId = ApiClient.workoutPhaseApi.create(CreateWorkoutPhaseRequest(trimmed, nextSort))
            applyServerId(localId, serverId)
            serverId.lowercase(Locale.US)
        } catch (e: ApiError.ServerError) {
            if (e.statusCode == 409) {
                DatabaseManager.deleteWorkoutPhase(localId)
                reload()
                throw PhaseError.DuplicateName
            }
            enqueueCreate(localId, trimmed, nextSort)
            localId
        } catch (e: Exception) {
            enqueueCreate(localId, trimmed, nextSort)
            localId
        }
    }

    private fun enqueueCreate(localId: String, name: String, sortOrder: Int) {
        val payload = ApiClient.gson.toJson(QueuedPhaseCreate(localId, name, sortOrder))
        DatabaseManager.enqueueSyncEntry(SyncManager.WORKOUT_PHASES_TYPE, localId, "create", payload)
    }

    /** Promote a local phase id to the server id and rewrite every reference (workouts + queued payloads). */
    fun applyServerId(localId: String, serverId: String) {
        val lower = serverId.lowercase(Locale.US)
        DatabaseManager.renameWorkoutPhaseId(localId, lower)
        DatabaseManager.rewritePhaseIdInWorkouts(localId, lower)
        DatabaseManager.rewritePhaseIdInSyncQueue(localId, lower)
        reload()
    }

    suspend fun rename(id: String, newName: String) {
        ensureHydrated()
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) throw PhaseError.EmptyName
        val phase = _available.value.firstOrNull { it.id.equals(id, true) } ?: throw PhaseError.NotFound
        if (phase.isSystem) throw PhaseError.Forbidden
        if (!NetworkMonitor.isConnected.value) throw PhaseError.Offline
        if (DatabaseManager.workoutPhaseSyncStates()[id.lowercase(Locale.US)] == "pending") {
            throw PhaseError.Unknown("This phase hasn't finished syncing yet. Try again in a moment.")
        }
        try {
            ApiClient.workoutPhaseApi.update(id, UpdateWorkoutPhaseRequest(name = trimmed))
            DatabaseManager.upsertWorkoutPhase(phase.copy(name = trimmed), "synced")
            reload()
        } catch (e: ApiError.ServerError) {
            if (e.statusCode == 409) throw PhaseError.DuplicateName
            throw PhaseError.Unknown(e.message ?: "Couldn't rename the phase.")
        } catch (e: ApiError.Forbidden) {
            throw PhaseError.Forbidden
        } catch (e: ApiError.NotFound) {
            DatabaseManager.deleteWorkoutPhase(id)
            reload()
            throw PhaseError.NotFound
        } catch (e: PhaseError) {
            throw e
        } catch (e: Exception) {
            throw PhaseError.Unknown(e.message ?: "Couldn't rename the phase.")
        }
    }

    /** Delete a user phase from the catalog; 409 = still attached to a workout. */
    suspend fun deletePhase(id: String) {
        ensureHydrated()
        val phase = _available.value.firstOrNull { it.id.equals(id, true) } ?: throw PhaseError.NotFound
        if (phase.isSystem) throw PhaseError.Forbidden
        if (!NetworkMonitor.isConnected.value) throw PhaseError.Offline
        try {
            ApiClient.workoutPhaseApi.delete(id)
            DatabaseManager.deleteWorkoutPhase(id)
            reload()
        } catch (e: ApiError.ServerError) {
            if (e.statusCode == 409) throw PhaseError.InUse
            throw PhaseError.Unknown(e.message ?: "Couldn't delete the phase.")
        } catch (e: ApiError.Forbidden) {
            throw PhaseError.Forbidden
        } catch (e: ApiError.NotFound) {
            DatabaseManager.deleteWorkoutPhase(id)
            reload()
            throw PhaseError.NotFound
        } catch (e: PhaseError) {
            throw e
        } catch (e: Exception) {
            throw PhaseError.Unknown(e.message ?: "Couldn't delete the phase.")
        }
    }

    /** True when any phase id in [phaseIds] still carries a local (pending) id. */
    fun hasUnsyncedPhase(phaseIds: Collection<String>): Boolean {
        val pending = runCatching { DatabaseManager.pendingWorkoutPhaseIds() }.getOrDefault(emptySet())
        if (pending.isEmpty()) return false
        return phaseIds.any { pending.contains(it.lowercase(Locale.US)) }
    }

    /** Replay queued offline creates, swapping ids on success (`SyncManager.drainWorkoutPhases`). */
    suspend fun drain() {
        SyncManager.drainQueue(SyncDomain.WORKOUT_PHASES) { entry ->
            if (entry.action != "create") return@drainQueue
            val req = SyncManager.decodePayload<QueuedPhaseCreate>(entry.payload) ?: return@drainQueue
            try {
                val serverId = ApiClient.workoutPhaseApi.create(CreateWorkoutPhaseRequest(req.name, req.sortOrder))
                applyServerId(req.localId, serverId)
            } catch (e: ApiError.ServerError) {
                if (e.statusCode == 409) {
                    // Duplicate on the server (created elsewhere) — drop the local row; the next refresh pulls the server's.
                    DatabaseManager.deleteWorkoutPhase(req.localId)
                    reload()
                    return@drainQueue
                }
                throw e
            }
        }
    }

    private fun AvailableWorkoutPhaseDto.toModel() = AvailableWorkoutPhase(id.lowercase(Locale.US), name, sortOrder, isSystem)
}
