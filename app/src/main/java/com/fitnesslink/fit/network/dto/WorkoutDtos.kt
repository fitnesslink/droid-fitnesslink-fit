package com.fitnesslink.fit.network.dto

import java.math.BigDecimal

// Wire shapes for /workouts, /workout-phases, /workout-phase-templates and
// /workouts/generate-from-prompt. Field names match the iOS Codable types
// (`WorkoutTaskInput.swift`, `AvailableWorkoutPhase.swift`,
// `GenerateWorkout.swift`) exactly — camelCase as on the wire.

// MARK: - Tasks

/**
 * One task on `POST /workouts` and `POST /workouts/{id}/tasks/replace`. The
 * client owns `id` (lowercase UUID). `weightKg` is whole lbs despite the name.
 */
data class WorkoutTaskInput(
    val id: String,
    val workoutPhaseId: String,
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
    val incline: BigDecimal? = null,
    val paceSeconds: Int? = null,
    val speed: BigDecimal? = null,
    val caloriesBurned: Int? = null,
    val heartRate: BigDecimal? = null,
    /** "tabata" | "hiit" | "emom" | "amrap" — interval leads only. */
    val intervalMode: String? = null,
    val intervalWorkSeconds: Int? = null,
    val intervalRestSeconds: Int? = null,
    val intervalWindowSeconds: Int? = null,
    /** Outer-loop set count for interval blocks. */
    val intervalSets: Int? = null,
    /** Rest between sets of an interval block (`intervalSets > 1`); interval leads only. */
    val intervalSetRestSeconds: Int? = null,
    /** Rest between rounds for superset / circuit groups; lives on the lead task. */
    val roundRestSeconds: Int? = null,
    /** `RestType` lookup GUID ("Passive" / "Active Recovery") for rest tasks. */
    val restTypeId: String? = null,
    /** Movement targeted by an active-recovery rest. */
    val restMovementId: String? = null,
    /** False = each set carries its own values in [setConfigs]. */
    val sameAcrossSets: Boolean = true,
    val setConfigs: List<WorkoutTaskSetConfigInput>? = null
)

/** Per-set values on a task input. `weightKg` is whole lbs. */
data class WorkoutTaskSetConfigInput(
    val setNumber: Int,
    val reps: Int? = null,
    val weightKg: Int? = null,
    val durationSeconds: Int? = null,
    val isDropSet: Boolean = false,
    val isWarmup: Boolean = false
)

// MARK: - Workout create / update

/**
 * `POST /workouts`. The server returns the projected `Workout` including
 * phases derived from the task list.
 *
 * `location` is a `WorkoutLocation` lookup NAME ("Gym" / "Home" / "Outdoors"
 * / "Track" / "Studio" / "Pool"); `trainingLevels` are `TrainingLevel`
 * NAMES. `equipmentIds` / `anatomyIds`: null = key omitted (stay
 * movement-derived), a list = store as the explicit set.
 */
data class CreateWorkoutRequest(
    val name: String,
    val statusId: String,
    val description: String? = null,
    val contributorId: String? = null,
    val imageId: String? = null,
    val thumbnailId: String? = null,
    /** SECONDS. */
    val estimatedTime: Int,
    val showInLibrary: Boolean,
    val preBuiltTemplateId: String? = null,
    /** Zero GUID lets the server stamp the authenticated user. */
    val createdBy: String = ZERO_GUID,
    val tasks: List<WorkoutTaskInput>? = null,
    val location: String? = null,
    val trainingLevels: List<String>? = null,
    val equipmentIds: List<String>? = null,
    val anatomyIds: List<String>? = null
)

/**
 * Shell-only `PUT /workouts/{id}` (204). Tasks go through
 * `POST /workouts/{id}/tasks/replace`.
 *
 * PUT semantics: `location` null = unchanged, "" = clear; `trainingLevels`
 * null = unchanged, [] = clear, list = replace; `equipmentIds` /
 * `anatomyIds` null = unchanged, [] = back to movement-derived, list =
 * replace the explicit set.
 */
data class UpdateWorkoutRequest(
    val name: String,
    val description: String? = null,
    val imageId: String? = null,
    val thumbnailId: String? = null,
    /** SECONDS. */
    val estimatedTime: Int? = null,
    val showInLibrary: Boolean? = null,
    val statusId: String? = null,
    val location: String? = null,
    val trainingLevels: List<String>? = null,
    val equipmentIds: List<String>? = null,
    val anatomyIds: List<String>? = null
)

/** `POST /workouts/{id}/tasks/replace` — atomic full task-list replace. */
data class ReplaceWorkoutTasksRequest(
    val tasks: List<WorkoutTaskInput>
)

const val ZERO_GUID = "00000000-0000-0000-0000-000000000000"

// MARK: - Workout phases (catalog)

/** `GET /workout-phases` row. System phases (Warmup / Exercises / Cool Down) are locked. */
data class AvailableWorkoutPhaseDto(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false
)

/** `POST /workout-phases` (201 = bare new id; 409 = duplicate name). */
data class CreateWorkoutPhaseRequest(
    val name: String,
    val sortOrder: Int
)

/** `PUT /workout-phases/{id}` (204). 403 on system phases, 409 duplicate name. */
data class UpdateWorkoutPhaseRequest(
    val name: String? = null,
    val sortOrder: Int? = null
)

// MARK: - Workout phase templates (FTNSSLNK-247)

/** Summary row for `GET /workout-phase-templates`. */
data class WorkoutPhaseTemplateSummary(
    val id: String,
    val name: String,
    val isSystem: Boolean = false,
    val taskCount: Int = 0
)

/** Full template with ordered tasks — `GET /workout-phase-templates/{id}`. */
data class WorkoutPhaseTemplate(
    val id: String,
    val name: String,
    val isSystem: Boolean = false,
    val tasks: List<WorkoutPhaseTemplateTask> = emptyList()
)

data class WorkoutPhaseTemplateTask(
    val id: String,
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
)

data class WorkoutPhaseTemplateSetConfig(
    val setNumber: Int,
    val reps: Int = 0,
    val weightKg: Int? = null,
    val durationSeconds: Int? = null,
    val isDropSet: Boolean = false,
    val isWarmup: Boolean = false
)

/** `POST /workout-phase-templates` (201 = full template; 409 = duplicate name). */
data class CreateWorkoutPhaseTemplateRequest(
    val name: String,
    val tasks: List<WorkoutPhaseTemplateTaskInput>
)

/** [WorkoutTaskInput] minus `id` / `workoutPhaseId` (template task ids are server-minted). */
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
    val incline: BigDecimal? = null,
    val paceSeconds: Int? = null,
    val speed: BigDecimal? = null,
    val caloriesBurned: Int? = null,
    val heartRate: BigDecimal? = null,
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
    val setConfigs: List<WorkoutTaskSetConfigInput>? = null
) {
    companion object {
        /** Lift a workout-save task payload into the template shape. */
        fun from(input: WorkoutTaskInput) = WorkoutPhaseTemplateTaskInput(
            workoutTaskTypeId = input.workoutTaskTypeId,
            order = input.order,
            groupId = input.groupId,
            movementId = input.movementId,
            advancedMovementId = input.advancedMovementId,
            reps = input.reps,
            sets = input.sets,
            restSeconds = input.restSeconds,
            rounds = input.rounds,
            intervalSeconds = input.intervalSeconds,
            instructions = input.instructions,
            weightKg = input.weightKg,
            incline = input.incline,
            paceSeconds = input.paceSeconds,
            speed = input.speed,
            caloriesBurned = input.caloriesBurned,
            heartRate = input.heartRate,
            intervalMode = input.intervalMode,
            intervalWorkSeconds = input.intervalWorkSeconds,
            intervalRestSeconds = input.intervalRestSeconds,
            intervalWindowSeconds = input.intervalWindowSeconds,
            intervalSets = input.intervalSets,
            intervalSetRestSeconds = input.intervalSetRestSeconds,
            roundRestSeconds = input.roundRestSeconds,
            restTypeId = input.restTypeId,
            restMovementId = input.restMovementId,
            sameAcrossSets = input.sameAcrossSets,
            setConfigs = input.setConfigs
        )
    }
}

// MARK: - AI generation (POST /workouts/generate-from-prompt)

data class GenerateWorkoutRequest(
    val prompt: String,
    val context: GenerateWorkoutContext? = null,
    val temperature: Double? = null
)

data class GenerateWorkoutContext(
    val equipment: List<String>? = null,
    val durationMinutes: Int? = null,
    val level: String? = null,
    val audience: String? = null
)

/**
 * Either-shape envelope. `success` discriminates: on true `draftWorkout` +
 * `movementResolutions` are set; on false `errorReasonCode` / `errorDetail`.
 * The server also returns this body on 400 / 502 / 504 — recover it from
 * `ApiError.ServerError.decodeBody`.
 */
data class GenerateWorkoutResponse(
    val success: Boolean = false,
    val httpStatus: Int = 0,
    val generationId: String = "",
    val draftWorkout: DraftWorkout? = null,
    val movementResolutions: List<MovementResolution>? = null,
    val usage: GenerationUsage? = null,
    val errorReasonCode: String? = null,
    val errorDetail: String? = null
)

data class DraftWorkout(
    val name: String = "",
    val description: String? = null,
    val durationMinutes: Int = 0,
    val level: String? = null,
    val tasks: List<DraftWorkoutTask> = emptyList()
)

data class DraftWorkoutTask(
    val taskIndex: Int = 0,
    val movementName: String = "",
    val movementDescription: String? = null,
    /** A `WorkoutPhase.name` from the platform vocabulary. */
    val phase: String = "",
    /** A `WorkoutTaskType.name` (e.g. "Movement"). */
    val taskType: String = "",
    val sets: Int? = null,
    val reps: Int? = null,
    val restSeconds: Int? = null,
    val weightKg: Int? = null,
    val rpeLabel: String? = null,
    val notes: String? = null,
    val equipmentNames: List<String>? = null,
    val anatomyNames: List<String>? = null,
    val durationSeconds: Int? = null,
    /** Tasks sharing a key (within a phase) form one block. */
    val groupKey: String? = null,
    /** "Superset" | "Circuit" | "Interval". */
    val groupType: String? = null,
    val rounds: Int? = null,
    val roundRestSeconds: Int? = null,
    val intervalMode: String? = null,
    val intervalWorkSeconds: Int? = null,
    val intervalRestSeconds: Int? = null,
    val intervalWindowSeconds: Int? = null,
    val intervalSets: Int? = null,
    val intervalSetRestSeconds: Int? = null
)

data class MovementResolution(
    val taskIndex: Int = 0,
    /** "matched" | "ambiguous" | "unmatched". */
    val status: String = "",
    val existingMovementId: String? = null,
    val suggestedMovementName: String = "",
    val candidates: List<MovementResolutionCandidate> = emptyList()
)

data class MovementResolutionCandidate(
    val movementId: String = "",
    val name: String = "",
    val score: Double = 0.0,
    val reasonCodes: List<String> = emptyList()
)

/** Shared by the workout and meal generators. */
data class GenerationUsage(
    val promptTokens: Int? = null,
    val responseTokens: Int? = null
)
