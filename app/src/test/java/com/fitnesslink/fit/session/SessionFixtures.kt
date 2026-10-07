package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask

/** Shared fixtures mirroring the iOS test helpers. */
object SessionFixtures {
    fun movement(
        id: String, sets: Int = 1, reps: Int = 10, rest: Int = 0, group: String = "",
        phase: String = "Exercises", phaseId: String? = null, seconds: Int? = null,
        name: String = id, weight: Int? = null, thumb: String? = null
    ): WorkoutTask {
        var t = WorkoutTask(
            id = id, name = name, isMovement = true, movementId = "m-$id", sets = sets, reps = reps,
            restSeconds = rest, groupId = group, phaseName = phase, workoutPhaseId = phaseId, weightKg = weight,
            movementThumbnailId = thumb
        )
        if (seconds != null) t = t.copy(usesDuration = true, durationSeconds = seconds, reps = 0)
        return t
    }

    fun restTask(id: String, seconds: Int, group: String = "", phase: String = "Exercises"): WorkoutTask =
        WorkoutTask(id = id, name = "Rest", isRest = true, restSeconds = seconds, groupId = group, phaseName = phase)

    /** Superset / circuit: lead carries rounds + round rest; every member carries groupId and (optionally) a member rest. */
    fun group(
        ids: List<String>, group: String, rounds: Int, memberRest: Int = 0, roundRest: Int? = null,
        circuit: Boolean = false, phase: String = "Exercises"
    ): List<WorkoutTask> = ids.mapIndexed { i, id ->
        var t = movement(id, rest = memberRest, group = group, phase = phase)
        if (i == 0) t = t.copy(isSuperset = !circuit, isCircuit = circuit, rounds = rounds, totalRounds = rounds, roundRestSeconds = roundRest)
        t
    }

    fun intervalBlock(
        ids: List<String>, group: String, mode: IntervalMode = IntervalMode.HIIT, work: Int = 30, rest: Int = 15,
        rounds: Int = 4, sets: Int = 1, phase: String = "Exercises"
    ): List<WorkoutTask> = ids.mapIndexed { i, id ->
        var t = movement(id, group = group, phase = phase).copy(isInterval = true)
        if (i == 0) t = t.copy(intervalMode = mode, intervalWorkSeconds = work, intervalRestSeconds = rest, rounds = rounds, totalRounds = rounds, intervalSets = sets)
        t
    }

    fun indexed(tasks: List<WorkoutTask>): List<WorkoutTask> = tasks.mapIndexed { i, t -> t.copy(order = i) }

    fun engine(tasks: List<WorkoutTask>) = SessionTransitionEngine(indexed(tasks))

    fun at(position: Int, set: Int = 1, round: Int = 1) = SessionTransitionEngine.Cursor(position, set, round)

    fun supersetRow(ids: List<String>, rounds: Int, group: String = "s-${ids.joinToString("")}"): TaskRow {
        val lead = movement(ids[0], group = group).copy(isSuperset = true, rounds = rounds, totalRounds = rounds)
        return TaskRow(task = lead, advancedTasks = ids.drop(1).map { movement(it, group = group) }, isSuperset = true, totalRounds = rounds)
    }

    fun hiitRow(ids: List<String>, work: Int = 30, rest: Int = 15, rounds: Int = 4, group: String = "i-${ids.joinToString("")}"): TaskRow {
        val lead = movement(ids[0], group = group).copy(
            isInterval = true, intervalMode = IntervalMode.HIIT, intervalWorkSeconds = work, intervalRestSeconds = rest,
            rounds = rounds, totalRounds = rounds, intervalSets = 1
        )
        val members = ids.drop(1).map { movement(it, group = group).copy(isInterval = true) }
        return TaskRow(task = lead, advanced = "Interval", totalRounds = rounds, isInterval = true, intervalMode = IntervalMode.HIIT, advancedTasks = members)
    }

    fun workout(rows: List<TaskRow>, id: String = "w-progress"): Workout =
        Workout(id = id, name = "Progress test", phases = listOf(WorkoutPhase(id = "p-main", name = "Exercises", taskRows = rows)))

    fun TaskRow.allMembers(): List<WorkoutTask> = listOfNotNull(task) + advancedTasks
}
