package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.WorkoutTask
import java.util.Locale

/**
 * Pure transition logic for Interactive Mode (PRD §6.10, FTNSSLNK-397).
 *
 * Given the flat task list of a session (phases → loose movements /
 * superset & circuit groups / interval blocks / rest blocks, in playback
 * order) and a cursor (task index + 1-based set + 1-based round), the
 * engine answers "what happens when the user finishes what they're on?"
 * as a [Transition]. It owns no timers and no completion state —
 * `InteractiveSessionViewModel` applies the result — so every branch is
 * unit-testable. Exact port of iOS `SessionTransitionEngine`.
 *
 * Rest semantics: for straight sets `restSeconds` is the rest between
 * sets; inside a superset / circuit `restSeconds` on a member is the rest
 * before the NEXT member of the round and the lead's `roundRestSeconds`
 * is the rest between rounds. A Rest task authored as a group member is
 * itself the rest — it never stacks a member or round rest on top.
 * Interval blocks are driven by [IntervalRunner]; the engine only decides
 * what follows the block.
 */
class SessionTransitionEngine(val tasks: List<WorkoutTask>) {

    /** Where the user is: task index, 1-based set (loose movements) and 1-based round (group members). */
    data class Cursor(val position: Int, val set: Int = 1, val round: Int = 1)

    enum class RestKind {
        /** Between sets of the same loose movement (`restSeconds`). */
        BETWEEN_SETS,
        /** Between members of a superset / circuit round (`restSeconds` on the member just finished). */
        BETWEEN_MEMBERS,
        /** Between rounds of a superset / circuit (`roundRestSeconds` on the lead), and the chained-Tabata recovery pause. */
        BETWEEN_ROUNDS
    }

    /** The next item starts a new phase — everything the phase-transition prep screen shows (FTNSSLNK-399). */
    data class PhaseChange(
        val completedPhaseName: String,
        val nextPhaseName: String,
        /** "6 Exercises • 3 Sets Each" / "10-minute HIIT". */
        val summary: String
    )

    sealed class Transition {
        /** Same movement, next set, no rest. */
        data class NextSet(val cursor: Cursor) : Transition()
        /** Next member of the round, or the first member of the next round. No rest, no preparation screen. */
        data class Step(val cursor: Cursor) : Transition()
        /** Count [seconds] down, then apply [then]. */
        data class Rest(val seconds: Int, val kind: RestKind, val then: Transition) : Transition()
        /** Land on a standalone Rest task; it counts itself down. */
        data class RestBlock(val cursor: Cursor) : Transition()
        /** A new exercise / group / interval block begins at [cursor] — show the preparation screen first. */
        data class Prepare(val cursor: Cursor, val phaseChange: PhaseChange?) : Transition()
        object WorkoutComplete : Transition() {
            override fun toString(): String = "WorkoutComplete"
        }

        /** Where the transition lands (after any rest). Null for [WorkoutComplete]. */
        val targetCursor: Cursor?
            get() = when (this) {
                is NextSet -> cursor
                is Step -> cursor
                is RestBlock -> cursor
                is Prepare -> cursor
                is Rest -> then.targetCursor
                WorkoutComplete -> null
            }

        val isPrepare: Boolean get() = this is Prepare
    }

    // MARK: - Transitions

    /** What follows the item under [cursor] once the user finishes it (NEXT, or a rest / interval block running out). */
    fun next(from: Cursor): Transition {
        val cursor = from
        if (cursor.position !in tasks.indices) return Transition.WorkoutComplete
        val task = tasks[cursor.position]

        val range = groupRange(cursor.position)
        if (range != null) {
            // Interval blocks: the runner owns every phase; reaching the engine means the block is finished.
            if (task.isInterval) return afterItem(range.last)
            if (cursor.position < range.last) {
                // More members in this round. A member's own `restSeconds` is the pause before the
                // next member; a Rest task authored inside the group already counted down as itself.
                val step = Cursor(position = cursor.position + 1, set = 1, round = cursor.round)
                if (!task.isRest && task.restSeconds > 0) {
                    return Transition.Rest(task.restSeconds, RestKind.BETWEEN_MEMBERS, Transition.Step(step))
                }
                return Transition.Step(step)
            }
            // Last member of the round.
            val lead = tasks[range.first]
            val rounds = groupRounds(range)
            if (cursor.round < rounds) {
                val step = Cursor(position = range.first, set = 1, round = cursor.round + 1)
                // A Rest task as the last member IS the round rest.
                val rrs = lead.roundRestSeconds
                if ((lead.isSuperset || lead.isCircuit) && !task.isRest && rrs != null && rrs > 0) {
                    return Transition.Rest(rrs, RestKind.BETWEEN_ROUNDS, Transition.Step(step))
                }
                return Transition.Step(step)
            }
            return afterItem(range.last)
        }

        // Loose movement: sets, with the optional between-sets rest.
        if (task.sets > 0 && cursor.set < task.sets) {
            val nextCursor = Cursor(position = cursor.position, set = cursor.set + 1, round = 1)
            if (task.restSeconds > 0) {
                return Transition.Rest(task.restSeconds, RestKind.BETWEEN_SETS, Transition.NextSet(nextCursor))
            }
            return Transition.NextSet(nextCursor)
        }

        return afterItem(cursor.position)
    }

    /**
     * The "Up Next" / skip affordance: walk forward without any rest — next member, next round,
     * and only past the final round (or a loose movement's sets) on to the next item.
     */
    fun skipForward(from: Cursor): Transition {
        val cursor = from
        if (cursor.position !in tasks.indices) return Transition.WorkoutComplete
        val task = tasks[cursor.position]
        val range = groupRange(cursor.position)
        if (range != null) {
            if (task.isInterval) return afterItem(range.last)
            if (cursor.position < range.last) {
                return Transition.Step(Cursor(position = cursor.position + 1, set = 1, round = cursor.round))
            }
            if (cursor.round < groupRounds(range)) {
                return Transition.Step(Cursor(position = range.first, set = 1, round = cursor.round + 1))
            }
            return afterItem(range.last)
        }
        return afterItem(cursor.position)
    }

    /**
     * What follows the item whose last task sits at [position]: a Rest block counts itself down, a
     * movement / group / interval block gets the preparation screen (phase-transition form when its
     * phase differs), anything else lands directly, and the end of the list completes the workout.
     *
     * Programmed rest replaces the preparation screen (FTNSSLNK-422): when the item just finished IS
     * a standalone Rest block, what follows it starts directly.
     */
    fun afterItem(endingAt: Int): Transition {
        val position = endingAt
        val nextPosition = position + 1
        if (nextPosition >= tasks.size) return Transition.WorkoutComplete
        val next = tasks[nextPosition]
        val cursor = Cursor(position = nextPosition, set = 1, round = 1)
        if (next.isRest && groupRange(nextPosition) == null) {
            return Transition.RestBlock(cursor)
        }
        if (next.isMovement || groupRange(nextPosition) != null) {
            if (endsOnRestBlock(position)) return Transition.Step(cursor)
            return Transition.Prepare(cursor, phaseChange(position, nextPosition))
        }
        // Non-movement filler (notes / placeholders): no countdown.
        return Transition.Step(cursor)
    }

    /** True when [position] is a standalone Rest block (not a rest authored inside a group). */
    fun endsOnRestBlock(position: Int): Boolean {
        if (position !in tasks.indices) return false
        return tasks[position].isRest && groupRange(position) == null
    }

    // MARK: - Groups

    /**
     * Range of consecutive tasks sharing the `groupId` at [position]; null for loose tasks.
     * The lead (carrying `rounds`) is at `range.first`.
     */
    fun groupRange(position: Int): IntRange? {
        if (position !in tasks.indices) return null
        val gid = tasks[position].groupId
        if (gid.isEmpty()) return null
        var start = position
        while (start > 0 && tasks[start - 1].groupId == gid) start -= 1
        var end = position + 1
        while (end < tasks.size && tasks[end].groupId == gid) end += 1
        return start until end
    }

    /** Rounds a superset / circuit iterates (`rounds`, falling back to `totalRounds`, never below 1). */
    fun groupRounds(range: IntRange): Int {
        val lead = tasks[range.first]
        return maxOf(lead.rounds ?: lead.totalRounds, 1)
    }

    /**
     * Coaching notation for a superset / circuit member: the group's letter (A for the first
     * non-interval group in the workout, B for the second, …) plus the member's 1-based index —
     * `A1`, `A2`, `B3`. Null for loose tasks and interval-block members.
     */
    fun memberLabel(position: Int): String? {
        val range = groupRange(position) ?: return null
        if (tasks[position].isInterval) return null
        var ordinal = 0
        val seen = mutableSetOf<String>()
        for (i in tasks.indices) {
            val t = tasks[i]
            if (t.groupId.isEmpty() || t.isInterval) continue
            if (seen.add(t.groupId)) ordinal += 1
            if (i >= position) break
        }
        val letter = ('A'.code + maxOf(1, minOf(ordinal, 26)) - 1).toChar()
        return "$letter${position - range.first + 1}"
    }

    // MARK: - Phases

    /**
     * Identity of the phase a task belongs to: its `workoutPhaseId`, falling back to the phase
     * name for locally-authored tasks that carry no id. Empty when the task has neither.
     */
    fun phaseKey(position: Int): String {
        if (position !in tasks.indices) return ""
        val task = tasks[position]
        val id = task.workoutPhaseId?.trim()?.lowercase(Locale.US)
        if (!id.isNullOrEmpty()) return id
        return task.phaseName.trim().lowercase(Locale.US)
    }

    /** True when the task at [to] belongs to a different (named) phase than the task at [from]. */
    fun isNewPhase(from: Int, to: Int): Boolean {
        val nextKey = phaseKey(to)
        if (nextKey.isEmpty()) return false
        return nextKey != phaseKey(from)
    }

    /** The phase-transition payload for moving from [from] to [to], or null when both sit in the same phase. */
    fun phaseChange(from: Int, to: Int): PhaseChange? {
        if (!isNewPhase(from, to)) return null
        val completed = if (from in tasks.indices) tasks[from].phaseName else ""
        return PhaseChange(
            completedPhaseName = phaseDisplayName(completed),
            nextPhaseName = phaseDisplayName(tasks[to].phaseName),
            summary = phaseSummary(to)
        )
    }

    /**
     * One line describing the phase that starts at [position]: "6 Exercises • 3 Sets Each",
     * "3 Exercises • 4 Rounds", or for a phase that is a single interval block "10-minute HIIT".
     */
    fun phaseSummary(startingAt: Int): String {
        val position = startingAt
        if (position !in tasks.indices) return ""
        val key = phaseKey(position)
        var end = position
        while (end < tasks.size && phaseKey(end) == key) end += 1
        val phaseTasks = tasks.subList(position, end)

        val lead = phaseTasks.firstOrNull()
        if (lead != null && lead.isInterval && lead.groupId.isNotEmpty() && phaseTasks.all { it.groupId == lead.groupId }) {
            val plan = IntervalMath.plan(lead, rowTotalRounds = lead.totalRounds)
            val seconds = IntervalMath.blockSeconds(plan)
            val mode = plan.mode.displayName
            if (seconds > 0 && seconds % 60 == 0) return "${seconds / 60}-minute $mode"
            return "${IntervalMath.formatTime(seconds)} $mode"
        }

        val movements = phaseTasks.filter { it.isMovement }
        val count = movements.size
        val parts = mutableListOf("$count ${if (count == 1) "Exercise" else "Exercises"}")
        val loose = movements.filter { it.groupId.isEmpty() }
        if (loose.isNotEmpty() && loose.size == count) {
            val sets = loose.map { maxOf(it.sets, 1) }.toSet()
            if (sets.size == 1) {
                val s = sets.first()
                if (s > 1) parts.add("$s Sets Each")
            }
        } else if (loose.isEmpty() && count > 0) {
            val groupIds = movements.map { it.groupId }.toSet()
            if (groupIds.size == 1) {
                val first = (position until end).firstOrNull { tasks[it].isMovement }
                val range = first?.let { groupRange(it) }
                if (range != null) {
                    val rounds = groupRounds(range)
                    if (rounds > 1) parts.add("$rounds Rounds")
                }
            }
        }
        return parts.joinToString(" • ")
    }

    /** 1-based ordinal of the phase containing [position] among the named phases in playback order; null when the task has no phase. */
    fun phaseOrdinal(position: Int): Int? {
        val key = phaseKey(position)
        if (key.isEmpty()) return null
        val seen = mutableListOf<String>()
        for (i in tasks.indices) {
            val k = phaseKey(i)
            if (k.isEmpty()) continue
            if (seen.lastOrNull() != k && !seen.contains(k)) seen.add(k)
            if (i == position) break
        }
        val index = seen.indexOf(key)
        return if (index >= 0) index + 1 else null
    }

    /** "PHASE 2 — STRENGTH" for the active-screen label (FTNSSLNK-399); null when the task has no phase name. */
    fun phaseLabel(position: Int): String? {
        if (position !in tasks.indices) return null
        val ordinal = phaseOrdinal(position) ?: return null
        val name = phaseDisplayName(tasks[position].phaseName)
        if (name.isEmpty()) return null
        return "PHASE $ordinal — ${name.uppercase(Locale.US)}"
    }

    companion object {
        /** System phases render as Warm-Up / Exercises / Cool Down; creator names come back verbatim (trimmed). */
        fun phaseDisplayName(raw: String): String {
            val trimmed = raw.trim()
            val folded = trimmed.lowercase(Locale.US)
                .replace("-", " ")
                .replace("_", " ")
                .split(" ").filter { it.isNotEmpty() }.joinToString(" ")
            return when (folded) {
                "warmup", "warm up" -> "Warm-Up"
                "exercises", "exercise" -> "Exercises"
                "cooldown", "cool down" -> "Cool Down"
                else -> trimmed
            }
        }
    }
}
