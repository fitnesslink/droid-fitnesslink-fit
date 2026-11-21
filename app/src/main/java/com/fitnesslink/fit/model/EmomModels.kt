package com.fitnesslink.fit.model

import com.fitnesslink.fit.session.VoicePhrase
import java.util.Locale

// MARK: - EMOM PRD v2.0 (docs/parity/EMOM_PRD.md)
//
// EMOM = one exercise per interval, rotating through the members. Interval /
// Round / Cycle per §1; everything lives on the interval group's LEAD task
// with no new numeric columns (§2):
//   intervalWindowSeconds = total seconds, rounds / totalRounds = total rounds,
//   interval = window / rounds (derived), cycles = rounds / exercise count (derived).

/**
 * A resolved EMOM block (§2.1). [totalRounds] = exercises × cycles for every
 * EMOM saved by this model; legacy rows may carry a partial last cycle.
 */
data class EmomPlan(
    /** Seconds allotted to ONE exercise (one round). */
    val intervalSeconds: Int,
    val totalRounds: Int,
    /** Movement members of the block, min 1. */
    val exerciseCount: Int
) {
    val totalSeconds: Int get() = totalRounds * intervalSeconds

    /** Display cycles — rounded UP so a legacy partial cycle still counts. */
    val cycles: Int get() = (totalRounds + exerciseCount - 1) / exerciseCount

    /** True when every cycle runs every exercise. */
    val isCompleteCycles: Boolean get() = totalRounds % exerciseCount == 0

    /** 0-based member that runs in 1-based [round]. */
    fun memberIndex(round: Int): Int = (maxOf(1, round) - 1) % exerciseCount

    /** 1-based cycle that [round] belongs to. */
    fun cycle(round: Int): Int = (maxOf(1, round) - 1) / exerciseCount + 1

    /** Rounds in which member [memberIndex] runs (§2.2) — the member's required iterations. */
    fun occurrences(memberIndex: Int): Int {
        if (memberIndex < 0 || memberIndex >= exerciseCount || memberIndex >= totalRounds) return 0
        return (totalRounds - 1 - memberIndex) / exerciseCount + 1
    }

    /** The round in which member [memberIndex] runs for the [iteration]-th time (iteration = cycle). */
    fun round(memberIndex: Int, iteration: Int): Int = (maxOf(1, iteration) - 1) * exerciseCount + memberIndex + 1

    /**
     * The [IntervalPlan] the shared interval code reads: one "work" slot per round of
     * [intervalSeconds], no rest slot (REST is the remainder of each interval), one set.
     */
    val intervalPlan: IntervalPlan
        get() = IntervalPlan(
            mode = IntervalMode.EMOM, workSeconds = intervalSeconds, restSeconds = 0,
            roundsPerSet = totalRounds, sets = 1, betweenSetsRestSeconds = 0, windowSeconds = totalSeconds
        )
}

/** Pure EMOM math, formatting and builder writes. Every EMOM surface reads through here. */
object EmomMath {
    const val defaultIntervalSeconds = 60
    /** Shortest interval a stored window / rounds pair may resolve to before the legacy minutes model wins. */
    const val minimumIntervalSeconds = 5

    /** Edit EMOM "Interval Duration" options (§3.4). */
    val intervalOptions: List<Int> = listOf(15, 20, 30, 40, 45, 60, 75, 90, 120, 150, 180, 240, 300)

    // MARK: - Resolution (§2.1)

    /** Members that take a round: the group's movements (lead first), as iOS `EmomMath.members`. */
    fun members(lead: WorkoutTask, advanced: List<WorkoutTask>): List<WorkoutTask> =
        (listOf(lead) + advanced).filter(::isMember)

    /** A movement member of an EMOM block (iOS: `isMovement || movementId` non-empty). */
    fun isMember(task: WorkoutTask): Boolean = !task.isRest && (task.isMovement || !task.movementId.isNullOrEmpty())

    fun members(row: TaskRow): List<WorkoutTask> = row.task?.let { members(it, row.advancedTasks) } ?: emptyList()

    /** Resolve a stored lead into an EMOM plan (§2.1). */
    fun plan(lead: WorkoutTask, exerciseCount: Int, rowTotalRounds: Int = 0): EmomPlan {
        val n = maxOf(1, exerciseCount)
        val sets = maxOf(1, lead.intervalSets ?: 1)
        val storedRounds = positive(lead.rounds) ?: positive(lead.totalRounds) ?: positive(rowTotalRounds)
        val window = positive(lead.intervalWindowSeconds)
        val interval: Int
        val perSet: Int
        if (storedRounds != null && window != null && window % storedRounds == 0 && window / storedRounds >= minimumIntervalSeconds) {
            interval = window / storedRounds; perSet = storedRounds
        } else if (window != null) {
            interval = defaultIntervalSeconds; perSet = maxOf(1, window / 60)
        } else if (storedRounds != null) {
            interval = defaultIntervalSeconds; perSet = storedRounds
        } else {
            interval = defaultIntervalSeconds; perSet = n
        }
        return EmomPlan(intervalSeconds = interval, totalRounds = perSet * sets, exerciseCount = n)
    }

    /** Plan for an editor / detail row; null when the row isn't an EMOM. */
    fun plan(row: TaskRow): EmomPlan? {
        val lead = row.task ?: return null
        val mode = row.intervalMode ?: lead.intervalMode
        if (mode != IntervalMode.EMOM) return null
        return plan(lead, members(row).size, row.totalRounds)
    }

    // MARK: - Session requirements (§2.2)

    /**
     * Required iterations for every member of an EMOM group in a flat task list ([groupTasks]
     * = the group's tasks in order, lead first): the number of rounds each member occurs in.
     */
    fun requiredIterations(groupTasks: List<WorkoutTask>): Map<String, Int> {
        val lead = groupTasks.firstOrNull() ?: return emptyMap()
        val members = groupTasks.filter(::isMember)
        val plan = plan(lead, members.size, lead.totalRounds)
        val out = LinkedHashMap<String, Int>()
        members.forEachIndexed { i, task -> if (task.isMovement) out[task.id] = maxOf(1, plan.occurrences(i)) }
        return out
    }

    // MARK: - Exercise targets

    /** True when the member runs a timed WORK (no Round Done button). */
    fun isTimeBased(task: WorkoutTask): Boolean = task.usesDuration

    /** The member's timed target in seconds, null when rep-based or unset. */
    fun timeTarget(task: WorkoutTask): Int? = if (task.usesDuration) task.durationSeconds?.takeIf { it > 0 } else null

    /** Length of the WORK phase for [task] in an [intervalSeconds] round (§7.2). */
    fun workSeconds(task: WorkoutTask, intervalSeconds: Int): Int {
        if (!task.usesDuration) return intervalSeconds
        return minOf(timeTarget(task) ?: intervalSeconds, intervalSeconds)
    }

    /** True when no target is set (reps 0 and not duration-mode, or duration-mode with no duration). */
    fun hasNoTarget(task: WorkoutTask): Boolean = if (task.usesDuration) timeTarget(task) == null else task.reps <= 0

    /** Builder / preview target: "20 reps", "30 sec", "1:30" (≥ 60 s), or "Reps – Time" when unset. */
    fun targetText(task: WorkoutTask): String = displayTarget(task) ?: "Reps – Time"

    /** "20 reps" / "30 sec" / "1:30", or null when no target is set (Interactive overlay). */
    fun displayTarget(task: WorkoutTask): String? {
        if (task.usesDuration) {
            val d = timeTarget(task) ?: return null
            return if (d < 60) "$d sec" else IntervalMath.formatTime(d)
        }
        if (task.reps <= 0) return null
        return "${task.reps} ${if (task.reps == 1) "rep" else "reps"}"
    }

    /** Spoken target: "20 reps" / "30 seconds" / "1 minute 30 seconds", null when unset. */
    fun spokenTarget(task: WorkoutTask): String? {
        if (task.usesDuration) {
            val d = timeTarget(task) ?: return null
            return VoicePhrase.spokenSeconds(d)
        }
        if (task.reps <= 0) return null
        return "${task.reps} ${if (task.reps == 1) "rep" else "reps"}"
    }

    // MARK: - Formatting

    /** Builder title duration: "20-min" on whole minutes, else "7:30". */
    fun titleDuration(seconds: Int): String = if (seconds > 0 && seconds % 60 == 0) "${seconds / 60}-min" else IntervalMath.formatTime(seconds)

    /** Builder tile duration: "20 min" on whole minutes, else "7:30". */
    fun tileDuration(seconds: Int): String = if (seconds > 0 && seconds % 60 == 0) "${seconds / 60} min" else IntervalMath.formatTime(seconds)

    /** Interval option / builder tile label: "60 sec", "90 sec", "2 min". */
    fun intervalLabel(seconds: Int): String = if (seconds >= 120 && seconds % 60 == 0) "${seconds / 60} min" else "$seconds sec"

    /** Validation-message durations: "N min" on whole minutes, else "m:ss". */
    fun messageDuration(seconds: Int): String = if (seconds > 0 && seconds % 60 == 0) "${seconds / 60} min" else IntervalMath.formatTime(seconds)

    /** "20-min EMOM · 60s Interval · 20:00" (§3.3). */
    fun summaryTitle(plan: EmomPlan): String =
        "${titleDuration(plan.totalSeconds)} EMOM · ${plan.intervalSeconds}s Interval · ${IntervalMath.formatTime(plan.totalSeconds)}"

    /** "20 rounds · 10 cycles"; single exercise "10 rounds". */
    fun roundsCyclesLine(plan: EmomPlan): String {
        val rounds = "${plan.totalRounds} ${if (plan.totalRounds == 1) "round" else "rounds"}"
        if (plan.exerciseCount == 1) return rounds
        return "$rounds · ${plan.cycles} ${if (plan.cycles == 1) "cycle" else "cycles"}"
    }

    /** Edit EMOM preview footer (§3.4). */
    fun previewFooter(plan: EmomPlan): String {
        val times = "${plan.cycles} ${if (plan.cycles == 1) "time" else "times"}"
        val rounds = "${plan.totalRounds} total ${if (plan.totalRounds == 1) "round" else "rounds"}"
        return if (plan.exerciseCount == 1) "Repeat $times · $rounds" else "Repeat sequence $times · $rounds"
    }

    /** Caption under the Cycles field (§3.4). */
    fun cyclesCaption(exerciseCount: Int): String = when {
        exerciseCount <= 1 -> "Number of times to repeat the exercise."
        exerciseCount == 2 -> "One cycle includes both exercises."
        else -> "One cycle includes all $exerciseCount exercises."
    }

    /** Caption under Total Rounds; null for a single exercise (the row is hidden). */
    fun totalRoundsCaption(exerciseCount: Int): String? =
        if (exerciseCount <= 1) null else "Calculated automatically · $exerciseCount exercises selected"

    /** Builder card tiles (§3.3): Duration "20 min", Interval "60 sec", Rounds, Cycles (n ≥ 2). */
    fun builderTiles(plan: EmomPlan): List<IntervalMetrics.Tile> = buildList {
        add(IntervalMetrics.Tile("Duration", tileDuration(plan.totalSeconds)))
        add(IntervalMetrics.Tile("Interval", intervalLabel(plan.intervalSeconds)))
        add(IntervalMetrics.Tile("Rounds", "${plan.totalRounds}"))
        if (plan.exerciseCount > 1) add(IntervalMetrics.Tile("Cycles", "${plan.cycles}"))
    }

    /** Overview / Playlist tiles (§5, §6): Duration "20:00", Interval "60s", Rounds, Cycles (n ≥ 2). */
    fun overviewTiles(plan: EmomPlan): List<IntervalMetrics.Tile> = buildList {
        add(IntervalMetrics.Tile("Duration", IntervalMath.formatTime(plan.totalSeconds)))
        add(IntervalMetrics.Tile("Interval", "${plan.intervalSeconds}s"))
        add(IntervalMetrics.Tile("Rounds", "${plan.totalRounds}"))
        if (plan.exerciseCount > 1) add(IntervalMetrics.Tile("Cycles", "${plan.cycles}"))
    }

    // MARK: - Validation (§3.5)

    /**
     * The partial-cycle message for an entered [durationSeconds], or null when it divides into
     * complete cycles of [exerciseCount] × [intervalSeconds]. Shorter than one cycle offers only
     * the one-cycle total ("Try 3 min."), as iOS does.
     */
    fun partialCycleMessage(durationSeconds: Int, intervalSeconds: Int, exerciseCount: Int): String? {
        val n = maxOf(1, exerciseCount)
        val interval = maxOf(1, intervalSeconds)
        val cycle = n * interval
        if (durationSeconds >= cycle && durationSeconds % cycle == 0) return null
        val lowerCycles = durationSeconds / cycle
        val upper = (lowerCycles + 1) * cycle
        val options = if (lowerCycles >= 1) "Try ${messageDuration(lowerCycles * cycle)} or ${messageDuration(upper)}." else "Try ${messageDuration(upper)}."
        val exercises = "$n ${if (n == 1) "exercise" else "exercises"}"
        return "${messageDuration(durationSeconds)} doesn't divide into complete cycles. " +
            "Each cycle is ${messageDuration(cycle)} ($exercises × ${interval}s). $options"
    }

    /** The "time target too long" message for [task], or null when it fits the interval. */
    fun targetTooLongMessage(task: WorkoutTask, intervalSeconds: Int): String? {
        val target = timeTarget(task) ?: return null
        if (target <= intervalSeconds) return null
        return "${task.name} is set to ${targetText(task)}, longer than the ${intervalSeconds}s interval. " +
            "Shorten the target or lengthen the interval."
    }

    /** Every target-too-long message for the members of a block at [intervalSeconds]. */
    fun targetMessages(members: List<WorkoutTask>, intervalSeconds: Int): List<String> =
        members.mapNotNull { targetTooLongMessage(it, intervalSeconds) }

    /** The first EMOM problem that must block a workout save (§3.5), or null. */
    fun saveBlockingMessage(phases: List<WorkoutPhase>): String? {
        for (phase in phases) for (row in phase.taskRows) {
            val plan = plan(row) ?: continue
            targetMessages(members(row), plan.intervalSeconds).firstOrNull()?.let { return it }
        }
        return null
    }

    // MARK: - Builder writes (§2, §3.1, §3.2)

    /**
     * Write an EMOM's timing onto a row: rounds = cycles × n, window = rounds × interval,
     * interval mirrored to `intervalWorkSeconds`, sets / between-sets rest / rest-per-minute and
     * active recovery cleared. Rounds are mirrored onto every member.
     */
    fun applyTiming(row: TaskRow, intervalSeconds: Int, cycles: Int): TaskRow {
        val lead = row.task ?: return row
        val n = maxOf(1, members(row).size)
        val rounds = maxOf(1, cycles) * n
        val interval = maxOf(1, intervalSeconds)
        val newLead = lead.copy(
            intervalMode = IntervalMode.EMOM,
            isInterval = true,
            rounds = rounds, totalRounds = rounds,
            intervalWindowSeconds = rounds * interval,
            intervalWorkSeconds = interval,
            intervalSets = 1,
            intervalRestSeconds = null,
            intervalSetRestSeconds = null,
            roundRestSeconds = null
        ).withRestType(RestType.PASSIVE)
        return row.copy(
            intervalMode = IntervalMode.EMOM,
            totalRounds = rounds,
            rounds = "$rounds rounds",
            task = newLead,
            advancedTasks = row.advancedTasks.map { it.copy(rounds = rounds, totalRounds = rounds) }
        )
    }

    /** Creation defaults (§3.1): 60 s interval, one cycle. */
    fun applyDefaults(row: TaskRow): TaskRow = applyTiming(row, defaultIntervalSeconds, 1)

    /**
     * Membership change (§3.2): keep the OLD block's interval and cycles and recompute rounds /
     * window for the new member count. [before] must be the row prior to the change.
     */
    fun rebalanced(before: TaskRow, after: TaskRow): TaskRow {
        val old = plan(before) ?: return after
        if (after.task == null) return after
        return applyTiming(after.copy(intervalMode = IntervalMode.EMOM), old.intervalSeconds, old.cycles)
    }

    private fun positive(value: Int?): Int? = value?.takeIf { it > 0 }
}

/** Instructional text (§4) — generated, never stored. */
object EmomText {
    const val heading = "How this EMOM works"

    private val words = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    /** 1–9 → "one"…"nine", ≥ 10 (and ≤ 0) → digits. */
    fun numberWord(n: Int): String = if (n in 1..9) words[n - 1] else "$n"

    /** "60 seconds"; "2 minutes" when ≥ 120 s on whole minutes. */
    fun intervalPhrase(seconds: Int): String =
        if (seconds >= 120 && seconds % 60 == 0) "${seconds / 60} minutes" else "$seconds seconds"

    fun instruction(plan: EmomPlan, members: List<WorkoutTask>): String {
        val interval = intervalPhrase(plan.intervalSeconds)
        if (plan.exerciseCount <= 1) {
            val task = members.firstOrNull()
            val name = (task?.name?.takeIf { it.isNotBlank() } ?: "the exercise").lowercase(Locale.US)
            val target = task?.let { t ->
                if (t.usesDuration) EmomMath.timeTarget(t)?.let { "${VoicePhrase.spokenSeconds(it)} of" }
                else t.reps.takeIf { it > 0 }?.let { "$it" }
            }
            val subject = listOfNotNull(target, name).joinToString(" ")
            val rounds = "${plan.totalRounds} ${if (plan.totalRounds == 1) "round" else "rounds"}"
            return "Complete $subject every $interval for $rounds."
        }
        val group = if (plan.exerciseCount == 2) "both" else "all ${numberWord(plan.exerciseCount)}"
        val cycles = "${numberWord(plan.cycles)} ${if (plan.cycles == 1) "cycle" else "cycles"}"
        return "Complete one exercise every $interval. Repeat $group exercises for $cycles."
    }
}

/**
 * Edit EMOM dual-input sync (§3.5). Holds the sheet's draft; [interval] / [cycles] are the
 * committed values (always valid), the entered duration lives beside them until it divides.
 */
class EmomEditorState(
    val members: List<WorkoutTask>,
    interval: Int,
    cycles: Int,
    var coachNotes: String? = null
) {
    enum class Primary { DURATION, CYCLES }

    val exerciseCount: Int = maxOf(1, members.size)

    var interval: Int = maxOf(1, interval)
        private set
    var cycles: Int = maxOf(1, cycles)
        private set
    /** The most recently edited primary value. */
    var lastPrimary: Primary = Primary.CYCLES
        private set
    /** Seconds the user typed into Total Duration; null until they do (or after Cycles is edited). */
    var enteredDurationSeconds: Int? = null
        private set

    val rounds: Int get() = cycles * exerciseCount
    val totalSeconds: Int get() = rounds * interval
    val plan: EmomPlan get() = EmomPlan(interval, rounds, exerciseCount)

    /** Partial-cycle problem with the entered duration (only while Duration is the last primary edit). */
    val durationMessage: String?
        get() {
            if (lastPrimary != Primary.DURATION) return null
            val entered = enteredDurationSeconds ?: return null
            return EmomMath.partialCycleMessage(entered, interval, exerciseCount)
        }

    val targetMessages: List<String> get() = EmomMath.targetMessages(members, interval)

    val messages: List<String> get() = listOfNotNull(durationMessage) + targetMessages

    /** Done is enabled only when no message shows. */
    val isValid: Boolean get() = messages.isEmpty()

    fun editCycles(value: Int) {
        cycles = maxOf(1, value)
        lastPrimary = Primary.CYCLES
        enteredDurationSeconds = null
    }

    fun editDuration(seconds: Int) {
        lastPrimary = Primary.DURATION
        enteredDurationSeconds = maxOf(0, seconds)
        applyDuration()
    }

    /** Minutes as typed ("7.5" → 450 s). Unparseable / blank input is ignored. */
    fun editDurationMinutes(text: String) {
        val seconds = parseMinutes(text) ?: return
        editDuration(seconds)
    }

    fun editInterval(seconds: Int) {
        interval = maxOf(1, seconds)
        if (lastPrimary == Primary.DURATION) applyDuration()
    }

    /** Only writes [cycles] when the entered duration divides into complete cycles. */
    private fun applyDuration() {
        val entered = enteredDurationSeconds ?: return
        if (EmomMath.partialCycleMessage(entered, interval, exerciseCount) != null) return
        cycles = entered / (interval * exerciseCount)
    }

    companion object {
        fun from(row: TaskRow): EmomEditorState? {
            val plan = EmomMath.plan(row) ?: return null
            return EmomEditorState(EmomMath.members(row), plan.intervalSeconds, plan.cycles, row.task?.coachNotes)
        }

        /** "20" → 1200, "7.5" → 450, "7,5" → 450. Null when blank / not a positive number. */
        fun parseMinutes(text: String): Int? {
            val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
            if (value <= 0 || value.isNaN() || value.isInfinite()) return null
            return Math.round(value * 60).toInt()
        }

        /** Minutes for the Total Duration field: "20", "7.5", "7.25". */
        fun formatMinutes(seconds: Int): String {
            if (seconds % 60 == 0) return "${seconds / 60}"
            val text = String.format(Locale.US, "%.2f", seconds / 60.0)
            return text.trimEnd('0').trimEnd('.')
        }
    }
}
