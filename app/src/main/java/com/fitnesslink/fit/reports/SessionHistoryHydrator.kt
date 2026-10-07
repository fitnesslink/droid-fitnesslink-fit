package com.fitnesslink.fit.reports

import android.util.Log
import com.fitnesslink.fit.model.WorkoutSession
import com.fitnesslink.fit.model.api.Rpe
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.SessionHistoryEntry
import com.fitnesslink.fit.network.dto.SessionSetStatus
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Port of iOS `WorkoutReportViewModel.hydrateFromServer`.
 *
 * The report reads only local SQLite (the session engine writes in-app
 * completions), so workouts finished on the web / another device / a
 * previous install never appear until this pass runs: pull `/sessions/me`,
 * and for each completed session we don't already have, fetch its per-set
 * history and rebuild the local `workout_sessions` +
 * `workout_session_history` rows the report aggregates over.
 *
 * Best-effort: any network failure leaves the cached local data untouched.
 * Safe to call repeatedly — sessions already present are skipped.
 */
object SessionHistoryHydrator {
    private const val TAG = "SessionHydrator"

    /** At most this many `/sessions/{id}/history` calls in flight at once. */
    const val MAX_CONCURRENT_HISTORY_FETCHES = 6

    private val runLock = Mutex()

    /**
     * Run the backfill. Returns the number of sessions inserted (0 when
     * nothing was missing or the server could not be reached). Concurrent
     * callers (report load + pull-to-refresh) serialize on a lock so the same
     * session is never inserted twice.
     */
    suspend fun hydrate(): Int = runLock.withLock {
        withContext(Dispatchers.IO) { hydrateLocked() }
    }

    private suspend fun hydrateLocked(): Int {
        val userId = DatabaseManager.user()?.id ?: "user1"

        val serverSessions = try {
            ApiClient.sessionApi.getMySessions()
        } catch (e: Exception) {
            Log.w(TAG, "getMySessions failed: ${e.message}")
            return 0
        }

        val newSessions = sessionsToBackfill(serverSessions) { DatabaseManager.sessionExists(it) }
        if (newSessions.isEmpty()) return 0

        // RpeId -> numeric midpoint so the RPE metric works for hydrated rows.
        // Best-effort: skip RPE if the scales don't load.
        val rpeMidpoints = try {
            rpeMidpoints(ApiClient.classificationApi.getRpeScales())
        } catch (e: Exception) {
            Log.w(TAG, "getRpeScales failed: ${e.message}")
            emptyMap()
        }

        // One round-trip per new session, capped so a first-run backfill of a
        // long history doesn't fire hundreds of simultaneous requests.
        val semaphore = Semaphore(MAX_CONCURRENT_HISTORY_FETCHES)
        val historyBySession: Map<String, List<SessionHistoryEntry>> = coroutineScope {
            newSessions.map { session ->
                async {
                    semaphore.withPermit {
                        val entries = try {
                            ApiClient.sessionApi.getHistory(session.id)
                        } catch (e: Exception) {
                            Log.w(TAG, "getHistory(${session.id}) failed: ${e.message}")
                            emptyList()
                        }
                        session.id to entries
                    }
                }
            }.awaitAll().toMap()
        }

        var inserted = 0
        for (session in newSessions) {
            val history = (historyBySession[session.id] ?: emptyList()).filter(::isPerformedSet)
            val stats = deriveStats(session, history)
            val nameByTask = session.workoutTasks.associate { it.id.lowercase(Locale.US) to it.name }
            val movementByTask = session.workoutTasks
                .filter { it.movementId != null }
                .associate { it.id.lowercase(Locale.US) to it.movementId!! }

            // Calories aren't on the session payload (body-data is a separate
            // endpoint) — leave 0 for hydrated rows rather than fan out again.
            DatabaseManager.insertWorkoutSession(
                id = session.id,
                workoutId = session.workoutId,
                userId = userId,
                programId = session.programId?.toString(),
                workoutName = session.name,
                startDate = session.startDate?.time ?: System.currentTimeMillis(),
                completionDate = session.completionDate?.time,
                durationSeconds = stats.durationSeconds,
                // Every session that reaches here passed `isCompletedOnServer`.
                isCompleted = true,
                exerciseCount = stats.exerciseCount,
                totalSets = stats.totalSets,
                totalReps = stats.totalReps,
                totalWeightLifted = stats.totalWeightLifted,
                totalCaloriesBurned = 0.0,
                rpeValue = session.rpeId?.let { rpeMidpoints[it.toString().lowercase(Locale.US)] },
                statusId = session.statusId?.toString()
            )

            for (entry in history) {
                val taskKey = entry.workoutTaskId.lowercase(Locale.US)
                DatabaseManager.insertSessionHistory(
                    id = DatabaseManager.sessionHistoryRowId(session.id, entry.workoutTaskId, entry.set ?: 1),
                    workoutSessionId = session.id,
                    workoutTaskId = entry.workoutTaskId,
                    workoutId = entry.workoutId,
                    programId = entry.programId,
                    userId = userId,
                    logDate = entry.logDate.time,
                    reps = entry.reps,
                    setNumber = entry.set,
                    intervalSeconds = entry.intervalSeconds,
                    weightLifted = entry.weightLifted,
                    taskName = nameByTask[taskKey] ?: "",
                    movementId = movementByTask[taskKey],
                    distance = entry.distance,
                    distanceUnit = entry.distanceUnit
                )
            }
            inserted++
        }
        Log.i(TAG, "backfilled $inserted session(s) from server")
        return inserted
    }

    // MARK: - Pure rules (unit-tested)

    /**
     * FTNSSLNK-368: sessions that are completed on the server AND have no
     * local row yet. `/sessions/me` has no status filter, so it also returns
     * rows still InProgress server-side; a read path must never create a
     * local in-progress row (Home would surface it as resumable), and the
     * originating device holds the authoritative stats for rows we already
     * have — re-inserting history would double-count volume.
     */
    fun sessionsToBackfill(
        serverSessions: List<WorkoutSession>,
        existsLocally: (String) -> Boolean
    ): List<WorkoutSession> = serverSessions.filter { it.isCompletedOnServer && !existsLocally(it.id) }

    /** `fromValue…toValue` → midpoint; a single-value scale maps to itself. */
    fun rpeMidpoint(scale: Rpe): Double = (scale.fromValue + (scale.toValue ?: scale.fromValue)) / 2.0

    /** Lower-cased RPE id → midpoint. */
    fun rpeMidpoints(scales: List<Rpe>): Map<String, Double> =
        scales.associate { it.id.toString().lowercase(Locale.US) to rpeMidpoint(it) }

    /**
     * Only performed sets become local rows: skipped / incomplete entries
     * (FTNSSLNK-405) carry no metrics and must not count as sets or
     * exercises. A null status comes from a server that predates the field
     * and means Completed.
     */
    fun isPerformedSet(entry: SessionHistoryEntry): Boolean =
        entry.status == null || entry.status.equals(SessionSetStatus.COMPLETED, ignoreCase = true)

    data class DerivedStats(
        val exerciseCount: Int,
        val totalSets: Int,
        val totalReps: Int,
        /** Σ weight × reps over rep sets (duration-only sets carry reps 0 → contribute 0). Stored lbs. */
        val totalWeightLifted: Double,
        val durationSeconds: Int
    )

    /** The aggregate columns the report reads, mirroring the session engine's writer. */
    fun deriveStats(session: WorkoutSession, performedHistory: List<SessionHistoryEntry>): DerivedStats {
        val distinctTasks = performedHistory.map { it.workoutTaskId.lowercase(Locale.US) }.toSet()
        val totalReps = performedHistory.sumOf { it.reps ?: 0 }
        val totalWeight = performedHistory.sumOf { (it.weightLifted ?: 0.0) * (it.reps ?: 0) }
        val start = session.startDate
        val end = session.completionDate
        val duration = if (start != null && end != null) ((end.time - start.time) / 1000L).toInt().coerceAtLeast(0) else 0
        return DerivedStats(distinctTasks.size, performedHistory.size, totalReps, totalWeight, duration)
    }
}
