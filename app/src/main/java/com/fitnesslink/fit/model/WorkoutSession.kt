package com.fitnesslink.fit.model

import com.fitnesslink.fit.model.api.EntityID
import com.fitnesslink.fit.model.api.WorkoutSessionStatusId
import com.google.gson.annotations.JsonAdapter
import java.util.Date
import java.util.Locale

@JsonAdapter(WorkoutSession.Adapter::class)
data class WorkoutSession(
    val id: String = "",
    val workoutId: String = "",
    val name: String = "",
    val description: String = "",
    val estimatedTime: String = "",
    val trainingLevel: String = "",
    val workoutTasks: List<WorkoutTask> = emptyList(),

    // API-aligned fields
    val userId: EntityID? = null,
    val programId: EntityID? = null,
    val rpeId: EntityID? = null,
    val currentIndex: Int? = null,
    val startDate: Date? = null,
    val completionDate: Date? = null,
    val nextMovementId: EntityID? = null,
    val workoutPhaseId: EntityID? = null,
    /**
     * Explicit session lifecycle state (InProgress / Completed / Abandoned).
     * Null on legacy rows — fall back to date inference.
     */
    val statusId: EntityID? = null
) {
    /**
     * True when the SERVER considers this session finished. Read paths that
     * backfill local rows from the server must only create rows for
     * sessions that pass this check, so a purged in-progress session can't
     * resurrect under "Continue Where You Left Off".
     */
    val isCompletedOnServer: Boolean
        get() {
            if (completionDate != null) return true
            val status = statusId ?: return false
            return status.toString().lowercase(Locale.US) == WorkoutSessionStatusId.completed.lowercase(Locale.US)
        }

    class Adapter : TolerantAdapterFactory<WorkoutSession>(WorkoutSession::class.java)
}
