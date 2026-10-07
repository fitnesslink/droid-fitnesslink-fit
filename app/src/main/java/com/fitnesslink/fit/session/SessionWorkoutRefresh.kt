package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Refreshes a cached workout from the server when a session starts (port of iOS
 * `SessionWorkoutRefresh`). A shallow cache (no phases) always fetches; a hydrated cache fetches
 * once per launch and only while the session is fresh (nothing recorded). The result is
 * re-applied only when the phase / task structure actually changed, and never once progress exists.
 */
object SessionWorkoutRefresh {

    // Injection seams (tests swap these; production defaults).
    var isConnected: () -> Boolean = { NetworkMonitor.isConnected.value }
    var fetch: suspend (String) -> Workout = { ApiClient.workoutApi.get(it) }
    /** Write the merged workout to the cache and hand back the cache-reconstructed row (what the players run on). */
    var persist: (Workout) -> Workout = { remote ->
        DatabaseManager.insertWorkout(remote)
        DatabaseManager.workout(remote.id) ?: remote
    }

    fun resetSeams() {
        isConnected = { NetworkMonitor.isConnected.value }
        fetch = { ApiClient.workoutApi.get(it) }
        persist = { remote -> DatabaseManager.insertWorkout(remote); DatabaseManager.workout(remote.id) ?: remote }
    }

    /** Nothing recorded on the session yet: no per-set outcome, no logged set, no session-only edit. */
    fun isFresh(session: ActiveWorkoutSession): Boolean =
        session.progress.isEmpty && session.actualSetLogs.isEmpty() && session.overrides.isEmpty

    fun shouldRefresh(workoutId: String, cached: Workout, session: ActiveWorkoutSession): Boolean {
        if (cached.phases.isEmpty()) return true
        return !session.refreshedWorkoutIds.contains(workoutId.lowercase()) && isFresh(session)
    }

    /** Phase id order plus each phase's ordered task ids (grouped members included). */
    fun structure(workout: Workout): List<List<String>> = workout.phases.map { listOf(it.id) + it.allTasks.map { t -> t.id } }

    /**
     * Fetch, persist and return the workout to re-apply — or null when nothing should change on
     * screen: offline / request failed, the session recorded progress while the request was in
     * flight, or the structure is identical to what's already applied.
     */
    suspend fun refresh(workoutId: String, cached: Workout, session: ActiveWorkoutSession): Workout? {
        if (!isConnected()) return null
        val remote = try { withContext(Dispatchers.IO) { fetch(workoutId) } } catch (_: Exception) { return null }
        val refreshed = withContext(Dispatchers.IO) { persist(remote) }
        session.markWorkoutRefreshed(workoutId)
        if (cached.phases.isNotEmpty() && !isFresh(session)) return null
        if (structure(refreshed) == structure(cached)) return null
        return refreshed
    }
}
