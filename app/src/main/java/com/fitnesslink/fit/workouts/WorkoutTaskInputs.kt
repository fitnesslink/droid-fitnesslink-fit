package com.fitnesslink.fit.workouts

import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.WorkoutTaskTypeId
import com.fitnesslink.fit.network.dto.CreateWorkoutRequest
import com.fitnesslink.fit.network.dto.UpdateWorkoutRequest
import com.fitnesslink.fit.network.dto.WorkoutTaskInput
import com.fitnesslink.fit.network.dto.WorkoutTaskSetConfigInput
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID

/**
 * Resolves a `WorkoutTaskType` lookup name ("Movement" / "Rest" /
 * "Interval") to its GUID. The editor passes `ClassificationStore::workoutTaskTypeId`;
 * the default answers from the seeded fallback constants so the pure
 * flattening below stays testable without the classification cache.
 */
typealias TaskTypeResolver = (name: String) -> String

/**
 * Pure `Workout` → wire-format mapping for the save pipeline — a 1:1 port
 * of iOS `WorkoutEditorViewModel.taskInputs(from:)`, `taskInputs(fromPhase:)`,
 * `makeInput`, `createRequest` and `updateRequest`. No Android or network
 * dependencies, so the unit tests exercise exactly what ships on the wire.
 */
object WorkoutTaskInputs {
    /** Canonical server ordering of the `TrainingLevel` lookup; the chips render and store in this order. */
    val trainingLevelOptions = listOf("Beginner", "Intermediate", "Specialized", "Advanced")

    /** `WorkoutLocation` lookup names, in the order the editor shows them. */
    val locationOptions = listOf("Gym", "Home", "Outdoors", "Track", "Studio", "Pool")

    val fallbackTaskTypeResolver: TaskTypeResolver = { name -> WorkoutTaskTypeId.fallbackByName(name) }

    /**
     * Build an ordered `[WorkoutTaskInput]` from the workout's phases. Each
     * row becomes one input for its lead task plus one for every advanced
     * task; grouped rows share a `groupId` so the server can rebuild them.
     */
    fun taskInputs(workout: Workout, resolve: TaskTypeResolver = fallbackTaskTypeResolver): List<WorkoutTaskInput> {
        val inputs = mutableListOf<WorkoutTaskInput>()
        for (phase in workout.phases) {
            inputs.addAll(taskInputs(phase, startingOrder = inputs.size, resolve = resolve))
        }
        return inputs
    }

    /**
     * One phase's ordered inputs — split out so save-as-template can
     * serialize a single phase with the same groupId semantics as a save.
     *
     * Interval rows keep a groupId even with one exercise (the server uses it
     * to mark the task as part of an interval block); supersets / circuits only
     * need one when there are 2+ tasks to reassemble.
     */
    fun taskInputs(phase: WorkoutPhase, startingOrder: Int = 0, resolve: TaskTypeResolver = fallbackTaskTypeResolver): List<WorkoutTaskInput> {
        val inputs = mutableListOf<WorkoutTaskInput>()
        var order = startingOrder
        for (row in phase.taskRows) {
            val needsGroupId = row.advancedTasks.isNotEmpty() || row.isInterval
            val groupId: String? = if (needsGroupId) {
                row.task?.groupId?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString().lowercase(Locale.US)
            } else null
            row.task?.let { task ->
                inputs.add(makeInput(task, phase.id, order, groupId, resolve))
                order += 1
            }
            for (adv in row.advancedTasks) {
                inputs.add(makeInput(adv, phase.id, order, groupId, resolve))
                order += 1
            }
        }
        return inputs
    }

    /** `WorkoutTaskTypeId.from(task)` on iOS: stored id first, else by role. */
    fun taskTypeId(task: WorkoutTask, resolve: TaskTypeResolver = fallbackTaskTypeResolver): String {
        task.workoutTaskTypeId?.takeIf { it.isNotBlank() }?.let { return it.lowercase(Locale.US) }
        return when {
            task.isRest -> resolve("Rest")
            task.isInterval -> resolve("Interval")
            else -> resolve("Movement")
        }.lowercase(Locale.US)
    }

    fun makeInput(
        task: WorkoutTask,
        phaseId: String,
        order: Int,
        groupId: String?,
        resolve: TaskTypeResolver = fallbackTaskTypeResolver
    ): WorkoutTaskInput = WorkoutTaskInput(
        id = task.id.lowercase(Locale.US),
        workoutPhaseId = phaseId.lowercase(Locale.US),
        workoutTaskTypeId = taskTypeId(task, resolve),
        order = order,
        groupId = groupId,
        movementId = task.movementId,
        advancedMovementId = task.advancedMovementId,
        reps = if (task.usesDuration) null else task.reps.takeIf { it > 0 },
        sets = task.sets.takeIf { it > 0 },
        restSeconds = task.restSeconds.takeIf { it > 0 },
        // Server DTOs echo `totalRounds` but historically not `rounds`; fall
        // back so a re-save never wipes the column.
        rounds = task.rounds ?: task.totalRounds.takeIf { it > 0 },
        // `intervalSeconds` is the wire-level signal of duration mode.
        intervalSeconds = if (task.usesDuration) task.durationSeconds else null,
        instructions = task.instructions,
        weightKg = task.weightKg,
        incline = task.incline?.let { BigDecimal.valueOf(it) },
        paceSeconds = task.paceSeconds,
        speed = task.speed?.let { BigDecimal.valueOf(it) },
        caloriesBurned = task.caloriesBurned,
        heartRate = task.heartRate?.let { BigDecimal.valueOf(it) },
        intervalMode = task.intervalMode?.rawValue,
        intervalWorkSeconds = task.intervalWorkSeconds,
        intervalRestSeconds = task.intervalRestSeconds,
        intervalWindowSeconds = task.intervalWindowSeconds,
        intervalSets = task.intervalSets,
        // Interval leads carry their between-sets rest in the dedicated
        // column and stop writing `roundRestSeconds`; supersets / circuits unchanged.
        intervalSetRestSeconds = if (task.isInterval) task.effectiveIntervalSetRestSeconds else null,
        roundRestSeconds = if (task.isInterval) null else task.roundRestSeconds,
        restTypeId = task.restTypeId,
        restMovementId = task.restMovementId,
        sameAcrossSets = task.sameAcrossSets,
        // Only ship per-set rows in individual-edit mode.
        setConfigs = if (task.sameAcrossSets) null else task.setConfigs.map {
            WorkoutTaskSetConfigInput(
                setNumber = it.setNumber,
                reps = it.reps,
                weightKg = it.weightKg?.toInt(),
                durationSeconds = it.durationSeconds,
                isDropSet = it.isDropSet,
                isWarmup = it.isWarmup
            )
        }
    )

    /**
     * `POST /workouts` body. Create semantics: null and "" both mean "no
     * location"; null and [] both mean "no levels"; explicit tags null =
     * movement-derived, list = explicit.
     */
    fun createRequest(
        workout: Workout,
        tasks: List<WorkoutTaskInput>,
        statusId: String,
        equipmentIds: List<String>? = null,
        anatomyIds: List<String>? = null
    ): CreateWorkoutRequest = CreateWorkoutRequest(
        name = workout.name,
        statusId = workout.statusId?.toString()?.lowercase(Locale.US) ?: statusId,
        description = workout.description.ifEmpty { null },
        contributorId = workout.contributorId?.toString()?.lowercase(Locale.US),
        imageId = workout.imageId?.toString()?.lowercase(Locale.US),
        thumbnailId = workout.thumbnailId?.toString()?.lowercase(Locale.US),
        estimatedTime = workout.estimatedTime ?: 0,
        showInLibrary = workout.showInLibrary,
        preBuiltTemplateId = workout.preBuiltTemplateId?.toString()?.lowercase(Locale.US),
        tasks = tasks,
        location = workout.location.ifEmpty { null },
        trainingLevels = workout.trainingLevels.ifEmpty { null },
        equipmentIds = equipmentIds,
        anatomyIds = anatomyIds
    )

    /**
     * Shell-only `PUT /workouts/{id}` body. Update semantics: the editor owns
     * `location` / `trainingLevels` wholesale (so "" / [] clear); explicit
     * tags send null when untouched, [] to reset, the full list to replace.
     */
    fun updateRequest(
        workout: Workout,
        equipmentIds: List<String>? = null,
        anatomyIds: List<String>? = null
    ): UpdateWorkoutRequest = UpdateWorkoutRequest(
        name = workout.name,
        description = workout.description.ifEmpty { null },
        imageId = workout.imageId?.toString()?.lowercase(Locale.US),
        thumbnailId = workout.thumbnailId?.toString()?.lowercase(Locale.US),
        estimatedTime = workout.estimatedTime,
        showInLibrary = workout.showInLibrary,
        statusId = workout.statusId?.toString()?.lowercase(Locale.US),
        location = workout.location,
        trainingLevels = workout.trainingLevels,
        equipmentIds = equipmentIds,
        anatomyIds = anatomyIds
    )
}
