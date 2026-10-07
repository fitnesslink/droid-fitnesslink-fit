package com.fitnesslink.fit.viewmodel

import android.util.Log
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.SessionAddedTask
import com.fitnesslink.fit.model.SessionMovementOverride
import com.fitnesslink.fit.model.SessionOverrides
import com.fitnesslink.fit.model.SessionSetOverride
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.WorkoutTaskTypeId
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.session.ActiveWorkoutSession
import com.fitnesslink.fit.workouts.WorkoutStore
import com.fitnesslink.fit.workouts.WorkoutTaskInputs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Do → Build edits shared by both session modes (port of iOS `SessionWorkoutEditor`).
 * Permanent edits (the user's own workout) rewrite the cached workout and go through the
 * workouts save pipeline (`WorkoutStore.commitReplaceTasks`, queue-first). Session-only edits
 * (a coach / program / shared-in workout) update `ActiveWorkoutSession.overrides`. Callers reload
 * their own task list afterwards.
 */
object SessionWorkoutEditor {
    private const val TAG = "SessionWorkoutEditor"
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    enum class Scope { PERMANENT, SESSION_ONLY }

    /** User-authored workouts are editable at the source; contributor templates and shared-in workouts are not. */
    fun isEditable(workout: Workout): Boolean = workout.contributorId == null && workout.sharedByUserId == null

    fun canSwap(task: WorkoutTask): Boolean = task.isMovement && !task.isInterval
    fun canRemove(task: WorkoutTask): Boolean = task.isMovement && task.groupId.isEmpty()
    fun canEditSets(task: WorkoutTask): Boolean = task.isMovement && task.groupId.isEmpty() && !task.usesDuration

    /** A fresh 3 × 10 movement task for [movement] (the editor's default prescription). */
    fun makeMovementTask(movement: MovementLibraryItem): WorkoutTask = WorkoutTask(
        id = UUID.randomUUID().toString().lowercase(Locale.US),
        name = movement.name,
        metric = "3 x 10",
        isMovement = true,
        sets = 3,
        reps = 10,
        movementId = movement.id,
        movementImageId = movement.imageId,
        movementThumbnailId = movement.thumbnailId,
        movementVideoId = movement.videoId,
        movementPhoneVideoId = movement.phoneVideoId,
        movementImageUrl = movement.imageUrl,
        movementThumbnailUrl = movement.thumbnailUrl,
        movementVideoUrl = movement.videoUrl,
        movementPhoneVideoUrl = movement.phoneVideoUrl,
        workoutTaskTypeId = WorkoutTaskTypeId.movement
    )

    /**
     * Insert [movement] after [afterTaskId], or — with no anchor — at the end of [phaseId]'s phase,
     * or at the end of the workout when neither is given. Returns the new task's id.
     */
    fun add(movement: MovementLibraryItem, workoutId: String, afterTaskId: String?, phaseId: String? = null,
            template: WorkoutTask?, scope: Scope): String {
        var task = makeMovementTask(movement)
        val workout = DatabaseManager.workout(workoutId)
        val targetPhase = phaseId?.let { pid -> workout?.phases?.firstOrNull { it.id.equals(pid, ignoreCase = true) } }
        task = if (targetPhase != null && afterTaskId == null) {
            task.copy(phaseName = targetPhase.name, phaseId = targetPhase.id, workoutPhaseId = targetPhase.id)
        } else {
            task.copy(phaseName = template?.phaseName ?: "", phaseId = template?.phaseId ?: "", workoutPhaseId = template?.workoutPhaseId)
        }
        when (scope) {
            Scope.SESSION_ONLY -> {
                val o = ActiveWorkoutSession.overrides
                ActiveWorkoutSession.setOverrides(o.copy(added = o.added + SessionAddedTask(task, afterTaskId, targetPhase?.id)))
            }
            Scope.PERMANENT -> {
                val w = workout ?: return task.id
                val phases = w.phases.toMutableList()
                val loc = afterTaskId?.let { locateRow(w, it) }
                if (loc != null) {
                    val rows = phases[loc.first].taskRows.toMutableList()
                    rows.add(loc.second + 1, TaskRow(task = task))
                    phases[loc.first] = phases[loc.first].copy(taskRows = rows)
                } else if (targetPhase != null) {
                    val pi = phases.indexOfFirst { it.id.equals(targetPhase.id, ignoreCase = true) }
                    if (pi < 0) return task.id
                    phases[pi] = phases[pi].copy(taskRows = phases[pi].taskRows + TaskRow(task = task))
                } else {
                    val last = phases.indices.lastOrNull { phases[it].taskRows.isNotEmpty() } ?: phases.indices.lastOrNull() ?: return task.id
                    phases[last] = phases[last].copy(taskRows = phases[last].taskRows + TaskRow(task = task))
                }
                persistPermanent(w.copy(phases = phases))
            }
        }
        DatabaseManager.logRecentMovement(movement.id)
        return task.id
    }

    fun swap(taskId: String, movement: MovementLibraryItem, workoutId: String, scope: Scope) {
        when (scope) {
            Scope.SESSION_ONLY -> {
                val o = ActiveWorkoutSession.overrides
                ActiveWorkoutSession.setOverrides(o.copy(swaps = o.swaps + (taskId to SessionMovementOverride(movement))))
            }
            Scope.PERMANENT -> {
                val w = DatabaseManager.workout(workoutId) ?: return
                val override = SessionMovementOverride(movement)
                persistPermanent(mapTask(w, taskId) { override.apply(it) })
            }
        }
    }

    fun remove(taskId: String, workoutId: String, scope: Scope) {
        when (scope) {
            Scope.SESSION_ONLY -> {
                val o = ActiveWorkoutSession.overrides
                ActiveWorkoutSession.setOverrides(o.copy(removed = o.removed + taskId, added = o.added.filter { it.task.id != taskId }))
            }
            Scope.PERMANENT -> {
                val w = DatabaseManager.workout(workoutId) ?: return
                val loc = locateRow(w, taskId) ?: return
                val phases = w.phases.toMutableList()
                val rows = phases[loc.first].taskRows.toMutableList()
                val row = rows[loc.second]
                if (row.task?.id == taskId && row.advancedTasks.isEmpty()) {
                    rows.removeAt(loc.second)
                    // Drop the rest row that preceded a loose movement, like the editor does.
                    if (loc.second > 0 && rows.getOrNull(loc.second - 1)?.task?.isRest == true) rows.removeAt(loc.second - 1)
                } else if (row.task?.id == taskId) {
                    val promoted = row.advancedTasks.first()
                    rows[loc.second] = row.copy(task = promoted, advancedTasks = row.advancedTasks.drop(1))
                } else {
                    rows[loc.second] = row.copy(advancedTasks = row.advancedTasks.filter { it.id != taskId })
                }
                phases[loc.first] = phases[loc.first].copy(taskRows = rows)
                persistPermanent(w.copy(phases = phases))
            }
        }
    }

    fun updateSets(taskId: String, sets: Int, reps: Int, weightLbs: Int?, workoutId: String, scope: Scope) {
        when (scope) {
            Scope.SESSION_ONLY -> {
                val o = ActiveWorkoutSession.overrides
                ActiveWorkoutSession.setOverrides(o.copy(setChanges = o.setChanges + (taskId to SessionSetOverride(sets, reps))))
            }
            Scope.PERMANENT -> {
                val w = DatabaseManager.workout(workoutId) ?: return
                persistPermanent(mapTask(w, taskId) { SessionOverrides.applySets(it.copy(weightKg = weightLbs ?: it.weightKg), sets, reps) })
            }
        }
    }

    /**
     * Bake every session-only edit into a personal copy of the workout (created through the normal
     * create path, so it works offline and doesn't trip the server's fork-while-in-progress guard).
     * The running session stays on the original. Returns the new workout's local id.
     */
    fun saveMyVersion(workoutId: String, name: String? = null): String? {
        val source = DatabaseManager.workout(workoutId)?.hydrateGroupRounds() ?: return null
        val perPhase = ActiveWorkoutSession.overrides.apply(source.phases.map { it.allTasks }, source.phases.map { it.id })
        val copyId = UUID.randomUUID().toString().lowercase(Locale.US)
        val phases = source.phases.zip(perPhase).map { (phase, tasks) ->
            phase.copy(taskRows = copiedTaskRows(SessionOverrides.rows(tasks), phase.id, phase.name))
        }
        val copy = Workout(
            id = copyId,
            name = name ?: "${source.name} (My Version)",
            description = source.description,
            location = source.location,
            trainingLevels = source.trainingLevels,
            trainingLevel = source.trainingLevel,
            imageId = source.imageId,
            thumbnailId = source.thumbnailId,
            estimatedTime = source.estimatedTime,
            preBuiltTemplateId = runCatching { UUID.fromString(source.id) }.getOrNull(),
            showInLibrary = false,
            contributorId = null,
            sharedByUserId = null,
            isSharedWithMe = false,
            phases = phases
        )
        DatabaseManager.insertWorkout(copy)
        ioScope.launch {
            try {
                val inputs = WorkoutTaskInputs.taskInputs(copy, ClassificationStore::workoutTaskTypeId)
                WorkoutStore.commitCreate(copyId, WorkoutTaskInputs.createRequest(copy, inputs, statusId = ClassificationStore.contentStatusId("Active")))
            } catch (e: Exception) {
                Log.w(TAG, "saveMyVersion create deferred: ${e.message}")
            }
        }
        return copyId
    }

    fun locateRow(workout: Workout, taskId: String): Pair<Int, Int>? {
        for ((pi, phase) in workout.phases.withIndex()) {
            for ((ri, row) in phase.taskRows.withIndex()) {
                if (row.task?.id == taskId || row.advancedTasks.any { it.id == taskId }) return pi to ri
            }
        }
        return null
    }

    // MARK: - Private

    /** Fresh task ids for a copied phase, stamped with the copy's phase identity. */
    private fun copiedTaskRows(rows: List<TaskRow>, phaseId: String, phaseName: String): List<TaskRow> {
        fun copyTask(t: WorkoutTask, groupId: String) = t.copy(
            id = UUID.randomUUID().toString().lowercase(Locale.US), phaseId = phaseId, workoutPhaseId = phaseId, phaseName = phaseName, groupId = groupId
        )
        return rows.map { row ->
            val groupId = if (row.task?.groupId.isNullOrEmpty()) "" else UUID.randomUUID().toString().lowercase(Locale.US)
            row.copy(task = row.task?.let { copyTask(it, groupId) }, advancedTasks = row.advancedTasks.map { copyTask(it, groupId) })
        }
    }

    private fun mapTask(workout: Workout, taskId: String, transform: (WorkoutTask) -> WorkoutTask): Workout =
        workout.copy(phases = workout.phases.map { phase ->
            phase.copy(taskRows = phase.taskRows.map { row ->
                row.copy(
                    task = row.task?.let { if (it.id == taskId) transform(it) else it },
                    advancedTasks = row.advancedTasks.map { if (it.id == taskId) transform(it) else it }
                )
            })
        })

    /** Cache first (the players re-read it), then the queue-first task replace through the workouts pipeline. */
    private fun persistPermanent(updated: Workout) {
        DatabaseManager.insertWorkout(updated)
        ioScope.launch {
            try {
                WorkoutStore.commitReplaceTasks(updated.id, WorkoutTaskInputs.taskInputs(updated, ClassificationStore::workoutTaskTypeId))
            } catch (e: Exception) {
                Log.w(TAG, "replace-tasks deferred for ${updated.id}: ${e.message}")
            }
        }
    }
}

/** What a playlist row (or the list footer) asked to change. The screen turns these into the picker / sheets / dialogs. */
sealed class PlaylistEditRequest {
    data class Add(val afterTaskId: String?) : PlaylistEditRequest()
    /** "Add exercise to <phase>": append to that phase, wherever it sits. */
    data class AddToPhase(val phaseId: String) : PlaylistEditRequest()
    data class Swap(val taskId: String) : PlaylistEditRequest()
    data class EditSets(val taskId: String) : PlaylistEditRequest()
    data class Remove(val taskId: String) : PlaylistEditRequest()
    object SaveMyVersion : PlaylistEditRequest()

    val needsMovementPick: Boolean get() = this is Add || this is AddToPhase || this is Swap
    val pickerTitle: String get() = if (this is Swap) "Replace Exercise" else "Add Exercise"
}

/** Do → Build: what the user can change about the running workout from inside the interactive session. */
sealed class SessionEditAction {
    object AddAfterCurrent : SessionEditAction()
    object AddAtEnd : SessionEditAction()
    data class AddToPhase(val phaseId: String) : SessionEditAction()
    object Swap : SessionEditAction()
    object EditSets : SessionEditAction()
    object Remove : SessionEditAction()
    object SaveMyVersion : SessionEditAction()

    val needsMovementPick: Boolean get() = this is AddAfterCurrent || this is AddAtEnd || this is AddToPhase || this is Swap
    val pickerTitle: String get() = if (this is Swap) "Replace Exercise" else "Add Exercise"
}

/** One row of the "Add exercise to phase" submenu. */
data class PhaseOption(val id: String, val name: String) {
    companion object {
        fun from(phase: WorkoutPhase) = PhaseOption(phase.id, phase.name)
    }
}
