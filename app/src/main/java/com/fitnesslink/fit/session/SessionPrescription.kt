package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.prescribedDistance
import java.util.Locale

/**
 * Prescription text for the Interactive Mode preparation screen and Up Next labels
 * (FTNSSLNK-398 / -392). Adapts to what the task actually prescribes. Port of iOS
 * `SessionPrescription`.
 */
object SessionPrescription {

    /**
     * Prep-screen line, upper-case: `8 REPS` / `30 SECONDS` / `3 SETS` / `3 SETS × 10 REPS` /
     * `4 ROUNDS × 10 REPS` / `HIIT · 5:45`. Null when the task carries nothing to prescribe.
     * [groupRounds] is the lead's round count for superset / circuit members; null for loose movements.
     */
    fun line(task: WorkoutTask, groupRounds: Int? = null): String? {
        if (task.isRest) return null
        if (task.isInterval) {
            val mode = task.intervalMode ?: return null
            val plan = IntervalMath.plan(task, rowTotalRounds = task.totalRounds, mode = mode)
            return "${mode.displayName.uppercase(Locale.US)} · ${IntervalMath.formatTime(IntervalMath.blockSeconds(plan))}"
        }
        if (task.hasPerSetBreakdown) {
            val summary = task.displayMetric.trim()
            return if (summary.isEmpty()) null else summary.uppercase(Locale.US)
        }
        val work = workUnit(task)
        if (groupRounds != null && task.groupId.isNotEmpty()) {
            val rounds = maxOf(groupRounds, 1)
            val head = "$rounds ${if (rounds == 1) "ROUND" else "ROUNDS"}"
            return work?.let { "$head × $it" } ?: head
        }
        val sets = task.sets
        if (sets > 1) {
            val head = "$sets SETS"
            return work?.let { "$head × $it" } ?: head
        }
        return work
    }

    /** Compact Up Next form: `3 × 8` / `3 × 30s` / `8 reps` / `30s` / `4 rounds` / `4 rounds × 8` / `HIIT · 5:45`. */
    fun compact(task: WorkoutTask, groupRounds: Int? = null): String? {
        if (task.isRest) return null
        if (task.isInterval) {
            val mode = task.intervalMode ?: return null
            val plan = IntervalMath.plan(task, rowTotalRounds = task.totalRounds, mode = mode)
            return "${mode.displayName} · ${IntervalMath.formatTime(IntervalMath.blockSeconds(plan))}"
        }
        if (task.hasPerSetBreakdown) {
            val summary = task.displayMetric.trim()
            return summary.ifEmpty { null }
        }
        val unit: String? = run {
            val d = task.durationSeconds
            if (task.usesDuration && d != null && d > 0) return@run WorkoutTask.humanizeDuration(d)
            if (task.reps > 0) return@run task.reps.toString()
            null
        }
        if (groupRounds != null && task.groupId.isNotEmpty()) {
            val rounds = maxOf(groupRounds, 1)
            val head = "$rounds ${if (rounds == 1) "round" else "rounds"}"
            return unit?.let { "$head × $it" } ?: head
        }
        if (task.sets > 1) {
            return unit?.let { "${task.sets} × $it" } ?: "${task.sets} sets"
        }
        if (unit == null) return null
        if (task.usesDuration) return unit
        return "$unit reps"
    }

    /** The per-set / per-round unit alone, compact: `10 reps` / `30s`. Null when the task prescribes neither. */
    fun unit(task: WorkoutTask): String? {
        if (task.isRest) return null
        val d = task.durationSeconds
        if (task.usesDuration && d != null && d > 0) return WorkoutTask.humanizeDuration(d)
        if (task.reps > 0) return "${task.reps} reps"
        return null
    }

    /**
     * Target for the active set / round (FTNSSLNK-396): `12 REPS` / `30 SEC` / `2 MIN` / `1:30`,
     * plus `@ 135 lbs` (display unit) when a load is prescribed. Null for rests, interval members
     * and unconfigured fillers.
     */
    fun target(task: WorkoutTask, iteration: Int): String? {
        if (!task.isMovement || task.isRest || task.isInterval) return null
        var work: String? = null
        var loadLbs: Double? = null
        val planned = if (task.hasPerSetBreakdown) task.setConfigs.firstOrNull { it.setNumber == iteration } else null
        if (planned != null) {
            val d = planned.durationSeconds
            if (d != null && d > 0) work = compactSeconds(d)
            else if (planned.reps > 0) work = "${planned.reps} ${if (planned.reps == 1) "REP" else "REPS"}"
            loadLbs = planned.weightKg
        } else {
            val d = task.durationSeconds
            if (task.usesDuration && d != null && d > 0) work = compactSeconds(d)
            else if (task.reps > 0) work = "${task.reps} ${if (task.reps == 1) "REP" else "REPS"}"
            loadLbs = task.weightKg?.toDouble()
        }
        val parts = mutableListOf<String>()
        task.prescribedDistance?.let { parts.add(it.targetText) }
        work?.let { parts.add(it) }
        if (loadLbs != null && loadLbs > 0) parts.add("@ ${WeightUnit.displayString(fromLbs = loadLbs)}")
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    /** `30 SEC` / `2 MIN` / `1:30` — the compact timed-work unit for the active-screen target. */
    fun compactSeconds(s: Int): String {
        if (s <= 0) return "0 SEC"
        if (s < 60) return "$s SEC"
        if (s % 60 == 0) return "${s / 60} MIN"
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    /** `30 SECONDS` / `2 MINUTES` / `1:30` — the timed-work unit for the prep line. */
    fun seconds(s: Int): String {
        if (s <= 0) return "0 SECONDS"
        if (s < 60) return "$s SECONDS"
        if (s % 60 == 0) {
            val m = s / 60
            return "$m ${if (m == 1) "MINUTE" else "MINUTES"}"
        }
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    // MARK: - Structured blocks (FTNSSLNK-428)

    /**
     * What the preparation screen shows about the structured block that starts at [position] —
     * superset, circuit, or an interval block — or null for a loose movement. Any member position
     * resolves to its block.
     */
    fun block(tasks: List<WorkoutTask>, position: Int): PreparationBlockSummary? {
        val engine = SessionTransitionEngine(tasks)
        val range = engine.groupRange(position) ?: return null
        val lead = tasks[range.first]
        val movements = range.map { tasks[it] }.filter { it.isMovement }
        val currentIndex = movements.indexOfFirst { it.id == tasks[position].id }
        val currentOrdinal = if (currentIndex >= 0) currentIndex + 1 else null

        if (lead.isInterval) {
            val mode = lead.intervalMode ?: return null
            val plan = IntervalMath.plan(lead, rowTotalRounds = lead.totalRounds, mode = mode)
            val setsSuffix = if (plan.sets > 1) " · ${plan.sets} SETS" else ""
            val spokenSets = if (plan.sets > 1) ", ${plan.sets} sets" else ""
            val detail: String
            val spokenDetail: String
            val memberMetric: (WorkoutTask) -> String?
            when (mode) {
                IntervalMode.HIIT, IntervalMode.TABATA -> {
                    val rounds = "${plan.roundsPerSet} ${if (plan.roundsPerSet == 1) "ROUND" else "ROUNDS"}"
                    val work = "${compactSeconds(plan.workSeconds)} WORK"
                    val rest = if (plan.restSeconds > 0) " / ${compactSeconds(plan.restSeconds)} REST" else ""
                    detail = "$rounds · $work$rest$setsSuffix"
                    val spokenRest = if (plan.restSeconds > 0) ", ${VoicePhrase.spokenSeconds(plan.restSeconds)} rest" else ""
                    spokenDetail = "${plan.roundsPerSet} ${if (plan.roundsPerSet == 1) "round" else "rounds"}, " +
                        "${VoicePhrase.spokenSeconds(plan.workSeconds)} work$spokenRest$spokenSets"
                    memberMetric = { null }
                }
                IntervalMode.EMOM -> {
                    detail = "${compactSeconds(plan.windowSeconds)} · ${compactSeconds(plan.workSeconds)} WORK EVERY MINUTE$setsSuffix"
                    spokenDetail = "${VoicePhrase.spokenSeconds(plan.windowSeconds)}, " +
                        "${VoicePhrase.spokenSeconds(plan.workSeconds)} work every minute$spokenSets"
                    memberMetric = { unit(it) }
                }
                IntervalMode.AMRAP -> {
                    detail = "${compactSeconds(plan.windowSeconds)}$setsSuffix"
                    spokenDetail = "${VoicePhrase.spokenSeconds(plan.windowSeconds)}$spokenSets"
                    memberMetric = { unit(it) }
                }
            }
            return PreparationBlockSummary(
                kind = PreparationBlockSummary.Kind.Interval(mode),
                title = mode.displayName.uppercase(Locale.US),
                detail = detail,
                members = movements.mapIndexed { i, t -> PreparationBlockSummary.Member(i + 1, t.name, memberMetric(t)) },
                currentOrdinal = currentOrdinal,
                spokenDetail = spokenDetail
            )
        }

        val rounds = engine.groupRounds(range)
        val stored = lead.advancedMovement.trim()
        val kind = if (lead.isSuperset) PreparationBlockSummary.Kind.Superset else PreparationBlockSummary.Kind.Circuit
        return PreparationBlockSummary(
            kind = kind,
            title = if (stored.isEmpty()) (if (kind == PreparationBlockSummary.Kind.Superset) "SUPERSET" else "CIRCUIT") else stored.uppercase(Locale.US),
            detail = "$rounds ${if (rounds == 1) "ROUND" else "ROUNDS"}",
            members = movements.mapIndexed { i, t -> PreparationBlockSummary.Member(i + 1, t.name, unit(t)) },
            currentOrdinal = currentOrdinal,
            spokenDetail = "$rounds ${if (rounds == 1) "round" else "rounds"}"
        )
    }

    /** The per-set / per-round work unit: `10 REPS` or `30 SECONDS`. */
    private fun workUnit(task: WorkoutTask): String? {
        val d = task.durationSeconds
        if (task.usesDuration && d != null && d > 0) return seconds(d)
        if (task.reps > 0) return "${task.reps} ${if (task.reps == 1) "REP" else "REPS"}"
        return null
    }
}

/**
 * What the preparation screen shows — and the coach says — about a structured block the user is
 * about to start (FTNSSLNK-428). Built by [SessionPrescription.block].
 */
data class PreparationBlockSummary(
    val kind: Kind,
    /** `SUPERSET` / `CIRCUIT` (or the stored group name) / `HIIT` / `TABATA` / `EMOM` / `AMRAP`. */
    val title: String,
    /** `3 ROUNDS` / `4 ROUNDS · 30 SEC WORK / 15 SEC REST` / `10 MIN · 40 SEC WORK EVERY MINUTE` / `12 MIN`. */
    val detail: String,
    val members: List<Member>,
    /** 1-based ordinal of the member the user is about to start. */
    val currentOrdinal: Int?,
    /** `3 rounds` / `4 rounds, 30 seconds work, 15 seconds rest`. */
    val spokenDetail: String
) {
    data class Member(val ordinal: Int, val name: String, val metric: String?)

    sealed class Kind {
        object Superset : Kind()
        object Circuit : Kind()
        data class Interval(val mode: IntervalMode) : Kind()
    }

    /** "Superset" / "Circuit" / "HIIT" — the title as the coach says it. */
    val spokenTitle: String
        get() = when (val k = kind) {
            Kind.Superset -> "Superset"
            Kind.Circuit -> "Circuit"
            is Kind.Interval -> k.mode.displayName
        }

    /** The whole announcement ([SessionCuePhrasing.spokenBlock]). */
    val spoken: String get() = SessionCuePhrasing.spokenBlock(this)
}

/**
 * Headline for the rest overlay (FTNSSLNK-393). Every rest reads `REST`; an Active Recovery block
 * reads `ACTIVE RECOVERY` and names the recovery movement. Nothing ever says "STOP".
 */
sealed class SessionRestHeadline {
    object Rest : SessionRestHeadline()
    data class ActiveRecovery(val movement: String?) : SessionRestHeadline()

    val title: String
        get() = when (this) {
            Rest -> "REST"
            is ActiveRecovery -> "ACTIVE RECOVERY"
        }

    val movementName: String? get() = (this as? ActiveRecovery)?.movement

    companion object {
        /** [isTransientRest] is true for the engine-driven rests between sets / members / rounds; those are always passive. */
        fun headline(task: WorkoutTask, isTransientRest: Boolean): SessionRestHeadline {
            if (isTransientRest || !task.isRest || task.restType != RestType.ACTIVE_RECOVERY) return Rest
            val name = task.restMovementName?.trim() ?: ""
            return ActiveRecovery(name.ifEmpty { null })
        }
    }
}
