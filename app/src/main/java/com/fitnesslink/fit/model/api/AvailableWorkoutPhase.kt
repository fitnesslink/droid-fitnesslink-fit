package com.fitnesslink.fit.model.api

import com.fitnesslink.fit.model.TolerantAdapterFactory
import com.google.gson.annotations.JsonAdapter

/** One row of the phase catalog (`GET /workout-phases`). System rows are platform-managed. */
@JsonAdapter(AvailableWorkoutPhase.Adapter::class)
data class AvailableWorkoutPhase(
    val id: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
    val isSystem: Boolean = false
) {
    class Adapter : TolerantAdapterFactory<AvailableWorkoutPhase>(AvailableWorkoutPhase::class.java)
}

data class CreateWorkoutPhaseRequest(
    val name: String,
    val sortOrder: Int
)

data class UpdateWorkoutPhaseRequest(
    val name: String? = null,
    val sortOrder: Int? = null
)

// MARK: - Phase templates (FTNSSLNK-247)
//
// A phase saved WITH its ordered task list so the workout builder can drop a
// fully-built phase into another workout (`/workout-phase-templates`).

/** Summary row for the template picker. `isSystem` marks house templates visible to everyone. */
data class WorkoutPhaseTemplateSummary(
    val id: String = "",
    val name: String = "",
    val isSystem: Boolean = false,
    val taskCount: Int = 0
)

/** Full template with the ordered task list. */
data class WorkoutPhaseTemplate(
    val id: String = "",
    val name: String = "",
    val isSystem: Boolean = false,
    val tasks: List<WorkoutPhaseTemplateTask> = emptyList()
)

/** One template task. `movementName` / `movementThumbnailUrl` are display-only extras. */
@JsonAdapter(WorkoutPhaseTemplateTask.Adapter::class)
data class WorkoutPhaseTemplateTask(
    val id: String = "",
    val workoutTaskTypeId: String = "",
    val order: Int = 0,
    val groupId: String? = null,
    val movementId: String? = null,
    val advancedMovementId: String? = null,
    val reps: Int? = null,
    val sets: Int? = null,
    val restSeconds: Int? = null,
    val rounds: Int? = null,
    val intervalSeconds: Int? = null,
    val instructions: String? = null,
    val weightKg: Int? = null,
    val incline: Double? = null,
    val paceSeconds: Int? = null,
    val speed: Double? = null,
    val caloriesBurned: Int? = null,
    val heartRate: Double? = null,
    val roundRestSeconds: Int? = null,
    val intervalMode: String? = null,
    val intervalWorkSeconds: Int? = null,
    val intervalRestSeconds: Int? = null,
    val intervalWindowSeconds: Int? = null,
    val intervalSets: Int? = null,
    val intervalSetRestSeconds: Int? = null,
    val restTypeId: String? = null,
    val restMovementId: String? = null,
    val sameAcrossSets: Boolean = true,
    val setConfigs: List<WorkoutPhaseTemplateSetConfig> = emptyList(),
    val movementName: String = "",
    val movementThumbnailUrl: String? = null
) {
    class Adapter : TolerantAdapterFactory<WorkoutPhaseTemplateTask>(WorkoutPhaseTemplateTask::class.java)
}

/** Per-set values on a template task (server `WorkoutTaskSetConfigDto`). */
data class WorkoutPhaseTemplateSetConfig(
    val setNumber: Int = 0,
    val reps: Int = 0,
    val weightKg: Int? = null,
    val durationSeconds: Int? = null,
    val isDropSet: Boolean = false,
    val isWarmup: Boolean = false
)

/** `POST /workout-phase-templates` body. */
data class CreateWorkoutPhaseTemplateRequest(
    val name: String,
    val tasks: List<WorkoutPhaseTemplateTaskInput>
)

data class WorkoutPhaseTemplateTaskInput(
    val workoutTaskTypeId: String,
    val order: Int,
    val groupId: String? = null,
    val movementId: String? = null,
    val advancedMovementId: String? = null,
    val reps: Int? = null,
    val sets: Int? = null,
    val restSeconds: Int? = null,
    val rounds: Int? = null,
    val intervalSeconds: Int? = null,
    val instructions: String? = null,
    val weightKg: Int? = null,
    val incline: Double? = null,
    val paceSeconds: Int? = null,
    val speed: Double? = null,
    val caloriesBurned: Int? = null,
    val heartRate: Double? = null,
    val intervalMode: String? = null,
    val intervalWorkSeconds: Int? = null,
    val intervalRestSeconds: Int? = null,
    val intervalWindowSeconds: Int? = null,
    val intervalSets: Int? = null,
    val intervalSetRestSeconds: Int? = null,
    val roundRestSeconds: Int? = null,
    val restTypeId: String? = null,
    val restMovementId: String? = null,
    val sameAcrossSets: Boolean = true,
    val setConfigs: List<WorkoutPhaseTemplateSetConfig>? = null
)
