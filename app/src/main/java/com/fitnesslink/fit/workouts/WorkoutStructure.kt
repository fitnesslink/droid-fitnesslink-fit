package com.fitnesslink.fit.workouts

import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.SessionTransitionEngine

/**
 * One row of the Workout Details "Workout Structure" section
 * (FTNSSLNK-407): the phase, in workout order, with a one-line summary.
 */
data class WorkoutPhaseSummary(
    /** Phase id, or a positional fallback for phases without one. */
    val id: String,
    val name: String,
    /** `5 exercises`, `6 exercises · 3 sets each`, `2 supersets`, `1 circuit · 3 rounds`, `10-minute HIIT`, `Tabata · 3:50`. */
    val summary: String,
    val exerciseCount: Int
) {
    val line: String get() = "$name · $summary"
    val anchorId: String get() = WorkoutStructure.anchorId(id)
}

/** Pure summariser of a workout's phases for the details screen — port of iOS `WorkoutStructure`. */
object WorkoutStructure {
    fun anchorId(phaseId: String): String = "workout-phase-$phaseId"

    /** Phases that contain at least one movement, in order. */
    fun summaries(phases: List<WorkoutPhase>): List<WorkoutPhaseSummary> =
        phases.mapIndexedNotNull { index, phase ->
            val count = exerciseCount(phase)
            if (count <= 0) return@mapIndexedNotNull null
            WorkoutPhaseSummary(
                id = phase.id.ifEmpty { "index-$index" },
                name = displayName(phase, index),
                summary = summary(phase),
                exerciseCount = count
            )
        }

    fun displayName(phase: WorkoutPhase, index: Int = 0): String {
        val name = SessionTransitionEngine.phaseDisplayName(phase.name)
        return name.ifEmpty { "Phase ${index + 1}" }
    }

    fun exerciseCount(phase: WorkoutPhase): Int = phase.allTasks.count { it.isMovement }

    fun summary(phase: WorkoutPhase): String {
        val rows = phase.taskRows.mapNotNull(::classify)
        val count = exerciseCount(phase)
        val loose = rows.filterIsInstance<RowKind.Loose>()
        val supersets = rows.filterIsInstance<RowKind.Superset>()
        val circuits = rows.filterIsInstance<RowKind.Circuit>()
        val intervals = rows.filterIsInstance<RowKind.Interval>()
        val groups = supersets.size + circuits.size

        if (loose.size == rows.size) return straightSetsSummary(loose.map { it.task }, count)
        if (groups == rows.size) {
            if (circuits.isEmpty()) return groupSummary(supersets.map { it.rounds }, "superset", "supersets")
            if (supersets.isEmpty()) return groupSummary(circuits.map { it.rounds }, "circuit", "circuits")
            return exerciseCountText(count)
        }
        if (intervals.size == 1 && rows.size == 1) {
            val block = intervals.first()
            return intervalSummary(block.lead, block.rowTotalRounds, block.mode)
        }
        return exerciseCountText(count)
    }

    fun exerciseCountText(count: Int): String = "$count ${if (count == 1) "exercise" else "exercises"}"

    private fun straightSetsSummary(tasks: List<WorkoutTask>, count: Int): String {
        val parts = mutableListOf(exerciseCountText(count))
        val sets = tasks.map { maxOf(it.sets, 1) }.toSet()
        if (sets.size == 1) {
            val s = sets.first()
            if (s > 1) parts.add("$s sets each")
        }
        return parts.joinToString(" · ")
    }

    private fun groupSummary(rounds: List<Int>, singular: String, plural: String): String {
        val parts = mutableListOf("${rounds.size} ${if (rounds.size == 1) singular else plural}")
        if (rounds.size == 1 && rounds.first() > 1) parts.add("${rounds.first()} rounds")
        return parts.joinToString(" · ")
    }

    /** `10-minute HIIT` on a whole number of minutes, else `Tabata · 3:50`. */
    fun intervalSummary(lead: WorkoutTask, rowTotalRounds: Int, mode: IntervalMode?): String {
        val plan = IntervalMath.plan(lead, rowTotalRounds = rowTotalRounds, mode = mode)
        val seconds = IntervalMath.blockSeconds(plan)
        val name = plan.mode.displayName
        if (seconds > 0 && seconds % 60 == 0) return "${seconds / 60}-minute $name"
        return "$name · ${IntervalMath.formatTime(seconds)}"
    }

    sealed class RowKind {
        data class Loose(val task: WorkoutTask) : RowKind()
        data class Superset(val rounds: Int) : RowKind()
        data class Circuit(val rounds: Int) : RowKind()
        data class Interval(val lead: WorkoutTask, val rowTotalRounds: Int, val mode: IntervalMode?) : RowKind()
    }

    /** Mirrors `Workout.hydrateGroupRounds`: row flags first, else member count. Rest-only rows are null. */
    fun classify(row: TaskRow): RowKind? {
        val members = (listOfNotNull(row.task) + row.advancedTasks).filter { it.isMovement }
        val lead = members.firstOrNull() ?: return null
        val isInterval = row.isInterval || (row.task?.isInterval ?: false)
        if (isInterval) return RowKind.Interval(row.task ?: lead, row.totalRounds, row.intervalMode)
        val isSuperset = row.isSuperset || (!row.isCircuit && members.size == 2)
        val isCircuit = row.isCircuit || (!row.isSuperset && members.size >= 3)
        if (!isSuperset && !isCircuit) return RowKind.Loose(lead)
        val rounds = lead.rounds?.takeIf { it > 0 } ?: row.totalRounds.takeIf { it > 0 } ?: 3
        return if (isCircuit) RowKind.Circuit(rounds) else RowKind.Superset(rounds)
    }
}
