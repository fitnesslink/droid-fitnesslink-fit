package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.persistence.DatabaseManager
import java.util.UUID

class WorkoutEditorViewModel : ViewModel() {
    var workout by mutableStateOf(Workout())
    var isNewWorkout by mutableStateOf(true)
    var isMultiSelectMode by mutableStateOf(false)
    var selectedTaskIds by mutableStateOf(emptySet<String>())
    var isDirty by mutableStateOf(false)
    var showExerciseBrowser by mutableStateOf(false)
    var showCreateExercise by mutableStateOf(false)
    var activePhaseIndex by mutableIntStateOf(0)
    var expandedTaskId by mutableStateOf<String?>(null)
    var pendingGroupType by mutableStateOf<ExerciseGroupType?>(null)
    var pendingGroupTaskIds by mutableStateOf<List<String>>(emptyList())

    fun createNewWorkout() {
        workout = Workout(
            id = UUID.randomUUID().toString(),
            name = "",
            phases = listOf(
                WorkoutPhase(id = UUID.randomUUID().toString(), name = "Warm Up"),
                WorkoutPhase(id = UUID.randomUUID().toString(), name = "Main Workout"),
                WorkoutPhase(id = UUID.randomUUID().toString(), name = "Cool Down")
            )
        )
        isNewWorkout = true
        isDirty = false
    }

    fun loadExistingWorkout(id: String) {
        workout = DatabaseManager.workout(id) ?: Workout()
        isNewWorkout = false
        isDirty = false
    }

    fun save() {
        if (workout.name.isEmpty()) workout = workout.copy(name = "My Workout")
        DatabaseManager.insertWorkout(workout)
        isDirty = false
        isNewWorkout = false
    }

    // Phase Management

    fun addPhase(name: String) {
        workout = workout.copy(phases = workout.phases + WorkoutPhase(id = UUID.randomUUID().toString(), name = name))
        isDirty = true
    }

    fun renamePhase(index: Int, name: String) {
        if (index >= workout.phases.size) return
        val phases = workout.phases.toMutableList()
        phases[index] = phases[index].copy(name = name)
        workout = workout.copy(phases = phases)
        isDirty = true
    }

    fun deletePhase(index: Int) {
        if (index >= workout.phases.size || workout.phases[index].taskRows.isNotEmpty()) return
        workout = workout.copy(phases = workout.phases.toMutableList().also { it.removeAt(index) })
        isDirty = true
    }

    // Exercise Management

    fun addExercise(movement: MovementLibraryItem, phaseIndex: Int) {
        if (phaseIndex >= workout.phases.size) return

        val task = WorkoutTask(
            id = UUID.randomUUID().toString(),
            name = movement.name,
            isMovement = true,
            movementId = movement.id,
            sets = 3,
            reps = 10,
            metric = "3 x 10",
            order = workout.phases[phaseIndex].taskRows.size
        )

        // If building a group, accumulate tasks
        if (pendingGroupType != null) {
            pendingGroupTaskIds = pendingGroupTaskIds + task.id
            val phases = workout.phases.toMutableList()
            phases[phaseIndex] = phases[phaseIndex].copy(
                taskRows = phases[phaseIndex].taskRows + TaskRow(task = task)
            )
            workout = workout.copy(phases = phases)
            DatabaseManager.logRecentMovement(movement.id)
            isDirty = true

            if (pendingGroupType == ExerciseGroupType.Superset && pendingGroupTaskIds.size >= 2) {
                finalizePendingGroup()
                showExerciseBrowser = false
            }
            return
        }

        val phases = workout.phases.toMutableList()
        val currentRows = phases[phaseIndex].taskRows.toMutableList()

        // Auto-insert rest before exercise in Main phase if exercises already exist
        val phaseName = phases[phaseIndex].name.lowercase()
        val existingMovements = currentRows.mapNotNull { it.task }.filter { it.isMovement }
        if (existingMovements.isNotEmpty() && phaseName.contains("main")) {
            val restTask = WorkoutTask(
                id = UUID.randomUUID().toString(),
                name = "Rest",
                isRest = true,
                restSeconds = 60,
                rest = "1:00"
            )
            currentRows.add(TaskRow(task = restTask))
        }

        currentRows.add(TaskRow(task = task))
        phases[phaseIndex] = phases[phaseIndex].copy(taskRows = currentRows)
        workout = workout.copy(phases = phases)
        DatabaseManager.logRecentMovement(movement.id)
        isDirty = true
    }

    fun finalizePendingGroup() {
        val type = pendingGroupType
        if (type == null || pendingGroupTaskIds.size < 2) {
            pendingGroupType = null
            pendingGroupTaskIds = emptyList()
            return
        }
        selectedTaskIds = pendingGroupTaskIds.toSet()
        createGroup(type, activePhaseIndex)
        pendingGroupType = null
        pendingGroupTaskIds = emptyList()
    }

    fun cancelPendingGroup() {
        pendingGroupType = null
        pendingGroupTaskIds = emptyList()
    }

    fun removeExercise(taskId: String, phaseIndex: Int) {
        if (phaseIndex >= workout.phases.size) return
        val rows = workout.phases[phaseIndex].taskRows.toMutableList()
        val taskIndex = rows.indexOfFirst { it.task?.id == taskId }
        if (taskIndex < 0) return

        // Remove associated rest before this exercise
        if (taskIndex > 0 && rows[taskIndex - 1].task?.isRest == true) {
            rows.removeAt(taskIndex - 1)
            rows.removeAt(taskIndex - 1)
        } else {
            rows.removeAt(taskIndex)
        }

        val phases = workout.phases.toMutableList()
        phases[phaseIndex] = phases[phaseIndex].copy(taskRows = rows)
        workout = workout.copy(phases = phases)
        isDirty = true
    }

    fun moveExerciseUp(taskId: String, phaseIndex: Int) {
        if (phaseIndex >= workout.phases.size) return
        val rows = workout.phases[phaseIndex].taskRows.toMutableList()
        val idx = rows.indexOfFirst { it.task?.id == taskId }
        if (idx <= 0) return
        var target = idx - 1
        while (target >= 0) {
            val t = rows[target].task
            if (t != null && (t.isMovement || !t.isRest)) break
            target--
        }
        if (target < 0) return
        val tmp = rows[idx]; rows[idx] = rows[target]; rows[target] = tmp
        val phases = workout.phases.toMutableList()
        phases[phaseIndex] = phases[phaseIndex].copy(taskRows = rows)
        workout = workout.copy(phases = phases)
        isDirty = true
    }

    fun moveExerciseDown(taskId: String, phaseIndex: Int) {
        if (phaseIndex >= workout.phases.size) return
        val rows = workout.phases[phaseIndex].taskRows.toMutableList()
        val idx = rows.indexOfFirst { it.task?.id == taskId }
        if (idx < 0) return
        var target = idx + 1
        while (target < rows.size) {
            val t = rows[target].task
            if (t != null && (t.isMovement || !t.isRest)) break
            target++
        }
        if (target >= rows.size) return
        val tmp = rows[idx]; rows[idx] = rows[target]; rows[target] = tmp
        val phases = workout.phases.toMutableList()
        phases[phaseIndex] = phases[phaseIndex].copy(taskRows = rows)
        workout = workout.copy(phases = phases)
        isDirty = true
    }

    // Set Configuration

    fun updateTaskSets(taskId: String, sets: Int, reps: Int, weightKg: Double?) {
        forTask(taskId) { task ->
            val metric = if (weightKg != null) "$sets x $reps @ ${weightKg.toInt()} lbs" else "$sets x $reps"
            task.copy(sets = sets, reps = reps, weightKg = weightKg?.toInt(), metric = metric)
        }
        isDirty = true
    }

    // Rest Management

    fun addRest(afterTaskId: String, phaseIndex: Int, seconds: Int = 60) {
        if (phaseIndex >= workout.phases.size) return
        val rows = workout.phases[phaseIndex].taskRows.toMutableList()
        val idx = rows.indexOfFirst { it.task?.id == afterTaskId }
        if (idx < 0) return

        val restTask = WorkoutTask(
            id = UUID.randomUUID().toString(),
            name = "Rest",
            isRest = true,
            restSeconds = seconds,
            rest = formatRest(seconds)
        )
        rows.add(idx + 1, TaskRow(task = restTask))
        val phases = workout.phases.toMutableList()
        phases[phaseIndex] = phases[phaseIndex].copy(taskRows = rows)
        workout = workout.copy(phases = phases)
        isDirty = true
    }

    fun updateRest(taskId: String, seconds: Int) {
        forTask(taskId) { task ->
            task.copy(restSeconds = seconds, rest = formatRest(seconds))
        }
        isDirty = true
    }

    fun removeRest(taskId: String, phaseIndex: Int) {
        if (phaseIndex >= workout.phases.size) return
        val phases = workout.phases.toMutableList()
        phases[phaseIndex] = phases[phaseIndex].copy(
            taskRows = phases[phaseIndex].taskRows.filter { it.task?.id != taskId }
        )
        workout = workout.copy(phases = phases)
        isDirty = true
    }

    // Grouping (Superset/Circuit/Interval)

    fun createGroup(type: ExerciseGroupType, phaseIndex: Int) {
        if (phaseIndex >= workout.phases.size || selectedTaskIds.size < 2) return

        val rows = workout.phases[phaseIndex].taskRows
        var selectedRows = rows.filter { row ->
            val id = row.task?.id ?: return@filter false
            selectedTaskIds.contains(id) && row.task.isMovement
        }
        if (selectedRows.size < 2) return

        if (type == ExerciseGroupType.Superset && selectedRows.size > 2) {
            selectedRows = selectedRows.take(2)
        }

        val usedIds = selectedRows.mapNotNull { it.task?.id }.toSet()
        val remainingRows = rows.filter { row ->
            val id = row.task?.id ?: return@filter true
            !usedIds.contains(id)
        }

        val primaryTask = selectedRows[0].task!!
        val secondaryTasks = selectedRows.drop(1).mapNotNull { it.task }

        val groupRow = TaskRow(
            task = primaryTask,
            advanced = type.label,
            rounds = if (type == ExerciseGroupType.Superset) "" else "3 rounds",
            totalRounds = if (type == ExerciseGroupType.Superset) 0 else 3,
            isSuperset = type == ExerciseGroupType.Superset,
            isCircuit = type == ExerciseGroupType.Circuit,
            advancedTasks = secondaryTasks
        )

        val phases = workout.phases.toMutableList()
        phases[phaseIndex] = phases[phaseIndex].copy(taskRows = remainingRows + groupRow)
        workout = workout.copy(phases = phases)
        selectedTaskIds = emptySet()
        isMultiSelectMode = false
        isDirty = true
    }

    fun ungroupExercise(taskId: String, phaseIndex: Int) {
        if (phaseIndex >= workout.phases.size) return
        val rows = workout.phases[phaseIndex].taskRows.toMutableList()
        val idx = rows.indexOfFirst { it.task?.id == taskId }
        if (idx < 0) return

        val row = rows.removeAt(idx)
        row.task?.let { rows.add(idx, TaskRow(task = it)) }
        row.advancedTasks.forEachIndexed { i, task ->
            rows.add(idx + 1 + i, TaskRow(task = task))
        }

        val phases = workout.phases.toMutableList()
        phases[phaseIndex] = phases[phaseIndex].copy(taskRows = rows)
        workout = workout.copy(phases = phases)
        isDirty = true
    }

    fun updateGroupRounds(taskId: String, rounds: Int) {
        val phases = workout.phases.toMutableList()
        for (pi in phases.indices) {
            val rows = phases[pi].taskRows.toMutableList()
            val idx = rows.indexOfFirst { it.task?.id == taskId }
            if (idx >= 0) {
                rows[idx] = rows[idx].copy(totalRounds = rounds, rounds = "$rounds rounds")
                phases[pi] = phases[pi].copy(taskRows = rows)
                workout = workout.copy(phases = phases)
                isDirty = true
                return
            }
        }
    }

    // Multi-Select

    fun toggleTaskSelection(taskId: String) {
        selectedTaskIds = if (selectedTaskIds.contains(taskId)) {
            selectedTaskIds - taskId
        } else {
            selectedTaskIds + taskId
        }
        if (selectedTaskIds.isEmpty()) isMultiSelectMode = false
    }

    fun cancelMultiSelect() {
        selectedTaskIds = emptySet()
        isMultiSelectMode = false
    }

    // Helpers

    private fun forTask(taskId: String, modify: (WorkoutTask) -> WorkoutTask) {
        val phases = workout.phases.toMutableList()
        for (pi in phases.indices) {
            val rows = phases[pi].taskRows.toMutableList()
            for (ri in rows.indices) {
                if (rows[ri].task?.id == taskId) {
                    rows[ri] = rows[ri].copy(task = modify(rows[ri].task!!))
                    phases[pi] = phases[pi].copy(taskRows = rows)
                    workout = workout.copy(phases = phases)
                    return
                }
                val advTasks = rows[ri].advancedTasks.toMutableList()
                for (ai in advTasks.indices) {
                    if (advTasks[ai].id == taskId) {
                        advTasks[ai] = modify(advTasks[ai])
                        rows[ri] = rows[ri].copy(advancedTasks = advTasks)
                        phases[pi] = phases[pi].copy(taskRows = rows)
                        workout = workout.copy(phases = phases)
                        return
                    }
                }
            }
        }
    }

    private fun formatRest(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }
}
