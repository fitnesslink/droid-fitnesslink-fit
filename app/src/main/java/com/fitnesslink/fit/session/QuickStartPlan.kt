package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.workouts.WorkoutRowOps

/**
 * The Quick Start rules that keep the workout's structure describing what
 * actually happened (Quick Start PRD §22, §28, §30, §43). Pure functions
 * over the editor models so they are unit-testable; `QuickStartViewModel`
 * applies them. Port of the iOS `QuickStartViewModel` statics.
 */
object QuickStartPlan {

    /** A movement task with one open set and no prescription (PRD §28). */
    fun makeOpenTask(movement: MovementLibraryItem): WorkoutTask =
        WorkoutRowOps.makeMovementTask(movement).copy(
            sets = 1, reps = 0, metric = "", sameAcrossSets = false,
            setConfigs = listOf(TaskSetConfig(setNumber = 1, reps = 0))
        )

    /** The same open-set shape applied to an existing (builder-seeded) task. */
    fun opened(task: WorkoutTask): WorkoutTask = task.copy(
        sets = 1, reps = 0, metric = "", sameAcrossSets = false,
        setConfigs = listOf(TaskSetConfig(setNumber = 1, reps = 0))
    )

    /**
     * A task whose [count] sets are the logged values (an unlogged slot stays
     * an open 0-rep set). Existing config ids are kept so an unchanged plan
     * compares equal.
     */
    fun plan(task: WorkoutTask, logs: List<TaskSetConfig>, count: Int): WorkoutTask {
        val n = maxOf(1, count)
        val configs = (1..n).map { setNumber ->
            val base = task.setConfigs.firstOrNull { it.setNumber == setNumber } ?: TaskSetConfig(setNumber = setNumber, reps = 0)
            val log = logs.firstOrNull { it.setNumber == setNumber }
            if (log != null) base.copy(
                setNumber = setNumber, reps = log.reps, weightKg = log.weightKg, durationSeconds = log.durationSeconds,
                distance = log.distance, distanceUnit = log.distanceUnit, isDropSet = false, isWarmup = false
            ) else base.copy(
                setNumber = setNumber, reps = 0, weightKg = null, durationSeconds = null,
                distance = null, distanceUnit = null, isDropSet = false, isWarmup = false
            )
        }
        var t = task.copy(sets = n, sameAcrossSets = false, setConfigs = configs)
        val last = logs.maxByOrNull { it.setNumber }
        if (last != null) {
            val seconds = last.durationSeconds
            val usesDuration = t.usesDuration || (seconds != null && seconds > 0)
            t = t.copy(
                reps = last.reps,
                weightKg = last.weightKg?.let { Math.round(it).toInt() },
                usesDuration = usesDuration,
                durationSeconds = if (seconds != null && seconds > 0) seconds else t.durationSeconds
            )
            t = t.copy(metric = if (t.usesDuration) "$n x ${WorkoutRowOps.formatDuration(t.durationSeconds ?: 0)}" else "$n x ${t.reps}")
        } else {
            t = t.copy(reps = 0, weightKg = null, metric = "")
        }
        return t
    }

    /**
     * One row with its plan rewritten from the logs. Loose movements get
     * `sets` = logged count; block members share one round count — the most
     * rounds any member has logged.
     */
    fun mirrorLogs(row: TaskRow, logs: (String) -> List<TaskSetConfig>): TaskRow {
        val isGroup = row.advancedTasks.isNotEmpty() || row.isSuperset || row.isCircuit || row.isInterval
        if (isGroup) {
            val members = listOfNotNull(row.task) + row.advancedTasks
            val rounds = maxOf(1, members.maxOfOrNull { logs(it.id).size } ?: 1)
            val lead = row.task?.let { plan(it, logs(it.id), rounds).copy(rounds = rounds, totalRounds = rounds) }
            val advanced = row.advancedTasks.map { plan(it, logs(it.id), rounds).copy(rounds = rounds, totalRounds = rounds) }
            return row.copy(task = lead, advancedTasks = advanced, totalRounds = rounds, rounds = "$rounds rounds")
        }
        val task = row.task ?: return row
        if (!task.isMovement) return row
        val logged = logs(task.id)
        return row.copy(task = plan(task, logged, maxOf(1, logged.size)))
    }

    /**
     * The workout with every never-logged exercise removed — and with it any
     * block or phase left empty. Rests are kept where they still separate
     * exercises. Pure, for Finish and for tests.
     */
    fun pruneUnlogged(workout: Workout, loggedTaskIds: Set<String>): Workout {
        val phases = workout.phases.map { phase -> phase.copy(taskRows = prunedRows(phase, loggedTaskIds)) }
            .filter { it.taskRows.isNotEmpty() }
        return workout.copy(phases = WorkoutRowOps.restampPhaseOrders(phases))
    }

    private fun prunedRows(phase: WorkoutPhase, loggedTaskIds: Set<String>): List<TaskRow> {
        val rows = mutableListOf<TaskRow>()
        for (row in phase.taskRows) {
            val lead = row.task ?: continue
            if (lead.isRest) { rows.add(row); continue }
            val isGroup = row.advancedTasks.isNotEmpty() || row.isSuperset || row.isCircuit || row.isInterval
            if (isGroup) {
                val members = (listOf(lead) + row.advancedTasks).filter { loggedTaskIds.contains(it.id) }.toMutableList()
                if (members.isEmpty()) continue
                if (members.size == 1) {
                    // A block of one is just an exercise.
                    val only = members.removeAt(0).copy(groupId = "", isSuperset = false, isCircuit = false, isInterval = false, rounds = null, totalRounds = 0)
                    rows.add(TaskRow(task = only))
                    continue
                }
                // Lead-owned block config moves with the first survivor.
                val newLead = members.removeAt(0).copy(
                    rounds = lead.rounds, totalRounds = lead.totalRounds, roundRestSeconds = lead.roundRestSeconds,
                    intervalMode = lead.intervalMode, intervalWorkSeconds = lead.intervalWorkSeconds,
                    intervalRestSeconds = lead.intervalRestSeconds, intervalWindowSeconds = lead.intervalWindowSeconds,
                    intervalSets = lead.intervalSets, intervalSetRestSeconds = lead.intervalSetRestSeconds,
                    restTypeId = lead.restTypeId, isSuperset = lead.isSuperset, isCircuit = lead.isCircuit, isInterval = lead.isInterval
                )
                rows.add(row.copy(task = newLead, advancedTasks = members))
            } else if (lead.isMovement) {
                if (loggedTaskIds.contains(lead.id)) rows.add(row)
            } else {
                rows.add(row)
            }
        }
        // Drop rests that no longer sit between two exercises.
        while (rows.firstOrNull()?.task?.isRest == true) rows.removeAt(0)
        while (rows.lastOrNull()?.task?.isRest == true) rows.removeAt(rows.size - 1)
        val deduped = mutableListOf<TaskRow>()
        for (row in rows) {
            if (row.task?.isRest == true && deduped.lastOrNull()?.task?.isRest == true) continue
            deduped.add(row)
        }
        return deduped
    }

    /** Finish always asks (it ends the session); the message names what is left out. */
    fun finishMessage(unlogged: Int, logged: Int): String {
        val parts = mutableListOf<String>()
        if (logged == 0) parts.add("You haven't logged any sets yet.")
        if (unlogged > 0) parts.add(
            if (unlogged == 1) "1 exercise has no logged sets and won't be saved."
            else "$unlogged exercises have no logged sets and won't be saved."
        )
        parts.add("The timer stops and your workout is saved to My Content.")
        return parts.joinToString(" ")
    }

    /** `25 lbs × 10`, `12 reps`, `1:30`, `25 lbs × 10 · 0:30`, `2 mi`. */
    fun setLine(set: TaskSetConfig): String {
        val parts = mutableListOf<String>()
        val weight = set.weightKg ?: 0.0
        when {
            weight > 0 && set.reps > 0 -> parts.add("${com.fitnesslink.fit.model.WeightUnit.displayString(fromLbs = weight)} × ${set.reps}")
            weight > 0 -> parts.add(com.fitnesslink.fit.model.WeightUnit.displayString(fromLbs = weight))
            set.reps > 0 -> parts.add("${set.reps} reps")
        }
        set.durationSeconds?.takeIf { it > 0 }?.let { parts.add(formatSeconds(it)) }
        set.distanceValue?.let { parts.add("${trimmedNumber(it.value)} ${it.unit.label}") }
        return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
    }

    /** "1:30" → 90, "45" → 45, "0:45" → 45; junk → 0. */
    fun parseSeconds(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        if (trimmed.contains(':')) {
            val parts = trimmed.split(':').map { it.toIntOrNull() ?: 0 }
            if (parts.size != 2) return 0
            return maxOf(0, parts[0]) * 60 + parts[1].coerceIn(0, 59)
        }
        return maxOf(0, trimmed.toIntOrNull() ?: 0)
    }

    fun formatSeconds(seconds: Int): String = String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    fun trimmedNumber(value: Double): String =
        if (value == Math.rint(value)) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)
}
