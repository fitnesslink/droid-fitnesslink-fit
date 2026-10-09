package com.fitnesslink.fit.session

import android.util.Log
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.WorkoutSessionStatusId
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.UpdateCalendarEntryRequest
import com.fitnesslink.fit.network.dto.toCreateRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.deleteSessionExerciseNotes
import com.fitnesslink.fit.persistence.openProgramEntry
import com.fitnesslink.fit.persistence.sessionDetailRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * Persists a completed workout session and syncs it to the server. Called by both players so they
 * share a single source of truth for how completions are stored. Port of iOS `SessionRecorder` +
 * `SessionDeletion` (plus the RPE edit per CONTRACTS).
 *
 * Only sets the user actually performed produce local history rows; skipped / incomplete sets are
 * posted to the server with their status. The session row keeps the completion % and the per-set
 * state JSON alongside its Completed status.
 */
object SessionRecorder {
    private const val TAG = "SessionRecorder"
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun save(
        sessionId: String,
        workoutId: String,
        workoutName: String,
        workoutTasks: List<WorkoutTask>,
        startDateMs: Long,
        durationSeconds: Int,
        actualSetLogs: Map<String, Map<Int, TaskSetConfig>>,
        progress: SessionProgress,
        requiredIterations: Map<String, Int>,
        rpe: Int?
    ) {
        val userId = DatabaseManager.user()?.id ?: "user1"
        val completionPercent = progress.completionPercent(requiredIterations)
        val rows = SessionHistoryRows.build(workoutTasks, actualSetLogs, progress, requiredIterations)
        val performed = rows.filter { it.isPerformed }
        val performedTaskIds = performed.map { it.task.id }.toSet()
        val performedTasks = workoutTasks.filter { performedTaskIds.contains(it.id) }

        val stats = WorkoutSummaryStats.build(workoutTasks, rows, requiredIterations, durationSeconds, completionPercent)
        val totalCaloriesBurned = performedTasks.sumOf { (it.caloriesBurned ?: 0).toDouble() }
        val rpeStored = SummaryRPE.storedValue(rpe)
        val now = ActiveWorkoutSession.clock()
        Log.i(TAG, "save sessionId=$sessionId workoutId=$workoutId rpe=$rpe completion=$completionPercent%")

        val programId = ActiveWorkoutSession.programId?.takeIf { ActiveWorkoutSession.sessionId.equals(sessionId, ignoreCase = true) }
        DatabaseManager.insertWorkoutSession(
            id = sessionId, workoutId = workoutId, userId = userId, programId = programId, workoutName = workoutName,
            startDate = startDateMs, completionDate = now, durationSeconds = durationSeconds, isCompleted = true,
            exerciseCount = stats.exercisesCompleted, totalSets = stats.setsCompleted, totalReps = stats.totalReps,
            totalWeightLifted = stats.totalVolumeLbs ?: 0.0, totalCaloriesBurned = totalCaloriesBurned,
            rpeValue = rpeStored, statusId = WorkoutSessionStatusId.completed,
            completionPercent = completionPercent, progressJson = progress.encodedJSON(),
            lastMode = ActiveWorkoutSession.lastMode?.rawValue
        )

        for (s in performed) {
            DatabaseManager.insertSessionHistory(
                id = DatabaseManager.sessionHistoryRowId(sessionId, s.task.id, s.setNumber),
                workoutSessionId = sessionId, workoutTaskId = s.task.id, workoutId = workoutId, programId = null,
                userId = userId, logDate = now, reps = s.reps, setNumber = s.setNumber,
                intervalSeconds = s.intervalSeconds, weightLifted = s.weightLbs, taskName = s.task.name,
                movementId = s.task.movementId, distance = s.distance?.value, distanceUnit = s.distance?.unit?.rawValue
            )
        }

        val logDate = java.util.Date(now)
        ActiveWorkoutSession.commitHistory(sessionId, rows.map { it.request(workoutId, userId, logDate) })
        ActiveWorkoutSession.commitFinish(sessionId, SummaryRPE.wireValue(rpe), completionPercent)

        markCalendarCompleted(workoutId, workoutName, now)
        ActiveWorkoutSession.emitCompleted(sessionId, workoutId)
    }

    private fun markCalendarCompleted(workoutId: String, workoutName: String, nowMs: Long) {
        val existing = DatabaseManager.calendarContent(forDate = nowMs).firstOrNull { it.workoutId.equals(workoutId, ignoreCase = true) }
        val entry: FitnessContent
        val wasExisting: Boolean
        val programEntry = if (existing == null) DatabaseManager.openProgramEntry(workoutId, nowMs) else null
        when {
            existing != null -> { entry = existing.copy(status = "completed"); wasExisting = true }
            programEntry != null -> { entry = programEntry.copy(status = "completed", scheduledDate = nowMs); wasExisting = true }
            else -> {
                entry = FitnessContent(
                    id = UUID.randomUUID().toString().lowercase(Locale.US), title = workoutName.ifEmpty { "Workout" },
                    programId = "", workoutId = workoutId, mealPlanId = "", status = "completed", scheduledDate = nowMs
                )
                wasExisting = false
            }
        }
        DatabaseManager.insertCalendarContent(entry)
        ActiveWorkoutSession.mirrorDeviceCalendar()
        if (!NetworkMonitor.isConnected.value) return
        ioScope.launch {
            try {
                if (wasExisting) ApiClient.calendarApi.update(entry.id, UpdateCalendarEntryRequest(scheduledDate = java.util.Date(nowMs), status = "completed"))
                else ApiClient.calendarApi.create(entry.toCreateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "calendar completion failed: ${e.message}")
            }
        }
    }

    // MARK: - Delete (Workout Report → Delete Session)

    /** Soft delete local, revert the calendar entry the session completed, queue `DELETE /sessions/{id}`. */
    suspend fun delete(sessionId: String) {
        withContext(Dispatchers.Main.immediate) {
            val row = DatabaseManager.sessionDetailRow(sessionId)
            DatabaseManager.deleteSession(sessionId)
            DatabaseManager.deleteSessionExerciseNotes(sessionId)
            if (row != null) revertCalendarCompletion(row)
            ActiveWorkoutSession.commitDelete(sessionId)
        }
    }

    private fun revertCalendarCompletion(row: SavedSessionRow) {
        if (row.workoutId.isEmpty()) return
        val day = row.completionDateMs ?: row.startDateMs
        if (DatabaseManager.completedSession(row.workoutId, day) != null) return
        val entries = DatabaseManager.calendarContent(forDate = day).filter { it.isCompleted && it.workoutId.equals(row.workoutId, ignoreCase = true) }
        for (e in entries) {
            val reverted = e.copy(status = "scheduled")
            DatabaseManager.insertCalendarContent(reverted)
            if (NetworkMonitor.isConnected.value) {
                ioScope.launch { try { ApiClient.calendarApi.update(reverted.id, UpdateCalendarEntryRequest(status = "scheduled")) } catch (_: Exception) {} }
            }
        }
        ActiveWorkoutSession.mirrorDeviceCalendar()
    }

    // MARK: - RPE edit (24 h, once)

    /** The one-time RPE change within 24 h of completion: stamps `rpeEditedAt` and re-POSTs the completion. */
    suspend fun updateRpe(sessionId: String, rpeValue: Int) {
        withContext(Dispatchers.Main.immediate) {
            val row = DatabaseManager.sessionDetailRow(sessionId) ?: return@withContext
            if (!row.canEditRpe(ActiveWorkoutSession.clock())) return@withContext
            DatabaseManager.updateSessionRpe(sessionId, SummaryRPE.storedValue(rpeValue), ActiveWorkoutSession.clock())
            ActiveWorkoutSession.commitFinish(sessionId, SummaryRPE.wireValue(rpeValue), row.completionPercent)
        }
    }
}
