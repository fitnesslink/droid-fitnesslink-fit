package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.DistanceRule
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.prescribedDistance
import java.util.Locale
import kotlin.math.roundToLong

// Universal Workout Logging (PRD v2.0, Jan 2027): the rules behind the one logging screen every
// mode (Quick Start, Playlist, Interactive) opens. Pure and UI-free so the tests can pin them
// down; the session state lives on `ActiveWorkoutSession`, the screen is `UniversalLogSheet`.
// Port of iOS `UniversalLogging.swift`.

// MARK: - Metrics (PRD §5)

/**
 * A performance metric a set can record (PRD §5.2). Set RPE is not a metric here: every table
 * has it (§7.2) and it can't be added or removed.
 */
enum class LogMetric(val rawValue: String) {
    REPS("reps"), WEIGHT("weight"), DURATION("duration"), DISTANCE("distance"), SPEED("speed"), INCLINE("incline");

    /** Column header / selector title. */
    val title: String
        get() = when (this) {
            REPS -> "Reps"
            WEIGHT -> "Weight"
            DURATION -> "Time"
            DISTANCE -> "Distance"
            SPEED -> "Speed"
            INCLINE -> "Incline"
        }

    /** Selector row title ("Duration (Time)" per the mock). */
    val selectorTitle: String get() = if (this == DURATION) "Duration (Time)" else title

    /** Unit shown in the column header, in the user's display units. */
    val unitLabel: String?
        get() = when (this) {
            REPS, DURATION -> null
            WEIGHT -> WeightUnit.current.label
            DISTANCE -> DistanceUnit.current.label
            SPEED -> SpeedUnit.current.label
            INCLINE -> "%"
        }

    val header: String get() = unitLabel?.let { "$title ($it)" } ?: title

    companion object {
        /** Table display order. */
        val displayOrder: List<LogMetric> = listOf(DURATION, REPS, WEIGHT, DISTANCE, SPEED, INCLINE)

        fun fromRaw(raw: String?): LogMetric? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** mph / km/h, following the user's distance unit. */
enum class SpeedUnit {
    MPH, KMH;

    val label: String get() = if (this == MPH) "mph" else "km/h"

    fun fromMph(mph: Double): Double = if (this == MPH) mph else mph * DistanceUnit.kmPerMile
    fun toMph(value: Double): Double = if (this == MPH) value else value / DistanceUnit.kmPerMile

    companion object {
        val current: SpeedUnit get() = if (DistanceUnit.current == DistanceUnit.KM) KMH else MPH
    }
}

/**
 * Which metrics an exercise shows by default and which it may add (PRD §5.4). The Exercise
 * Library carries no explicit mapping yet (open decision §18.2 #4), so the profile is derived from
 * what the task already says about itself: a timed task leads with Duration, distance / cardio
 * work with Duration + Distance, everything else is strength (Reps + Weight).
 */
data class LogMetricProfile(val defaults: List<LogMetric>, val compatible: List<LogMetric>) {

    /** Columns to show: defaults, then any metric the user added or that already carries a value in this session, in display order. */
    fun visible(added: List<LogMetric>, withValues: Set<LogMetric>): List<LogMetric> {
        val shown = defaults.toSet() + added + withValues
        return LogMetric.displayOrder.filter { shown.contains(it) }
    }

    /** The Add Metric selector: compatible and not on screen (PRD §5.3, AC-10). Empty → "All available metrics have been added." */
    fun addable(visible: List<LogMetric>): List<LogMetric> = compatible.filter { !visible.contains(it) }

    companion object {
        fun forTask(task: WorkoutTask, anatomyNames: List<String> = emptyList()): LogMetricProfile {
            val distanceCapable = DistanceRule.isDistanceCapable(task, anatomyNames)
            if (distanceCapable && isCardio(task, anatomyNames)) {
                // Treadmill Walking: Duration + Distance, adds Speed / Incline.
                return LogMetricProfile(
                    listOf(LogMetric.DURATION, LogMetric.DISTANCE),
                    listOf(LogMetric.SPEED, LogMetric.INCLINE, LogMetric.WEIGHT, LogMetric.REPS)
                )
            }
            if (task.usesDuration) {
                // Plank: Duration, adds Weight (reps-in-window stays available for AMRAP-style timed work — FTNSSLNK-326).
                return LogMetricProfile(listOf(LogMetric.DURATION), listOf(LogMetric.WEIGHT, LogMetric.REPS, LogMetric.DISTANCE))
            }
            if (distanceCapable) {
                // Walking Lunges / Farmer Carry: strength that can travel.
                return LogMetricProfile(listOf(LogMetric.REPS, LogMetric.WEIGHT), listOf(LogMetric.DISTANCE, LogMetric.DURATION))
            }
            // Dumbbell Squat.
            return LogMetricProfile(listOf(LogMetric.REPS, LogMetric.WEIGHT), listOf(LogMetric.DURATION, LogMetric.DISTANCE))
        }

        val cardioKeywords: Set<String> = setOf(
            "run", "running", "jog", "jogging", "sprint", "sprints", "treadmill", "elliptical", "bike", "biking",
            "cycle", "cycling", "swim", "swimming", "stair", "stairs", "stairmaster", "erg", "rower", "ski",
            "walk", "walking", "hike", "hiking", "airdyne"
        )

        /** Names that read as strength even with a cardio word in them ("Walking Lunges", "Farmer Carry Walk"). */
        val strengthKeywords: Set<String> = setOf(
            "lunge", "lunges", "carry", "squat", "squats", "press", "curl", "curls", "deadlift", "sled",
            "push", "pull", "row", "rows", "raise", "raises", "extension", "fly"
        )

        /** Cardio-programmed work: pace / speed / incline on the task, a Cardio anatomy tag, or a cardio name that isn't a strength move. */
        fun isCardio(task: WorkoutTask, anatomyNames: List<String> = emptyList()): Boolean {
            if (task.paceSeconds != null || task.speed != null || task.incline != null) return true
            val words = task.name.lowercase(Locale.US).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
            if (words.any { strengthKeywords.contains(it) }) return false
            if (anatomyNames.any { it.contains("cardio", ignoreCase = true) }) return true
            return words.any { cardioKeywords.contains(it) }
        }
    }
}

// MARK: - Set values

object UniversalSetValues {
    /** Whether a set config holds any performance value at all (a blank set is excluded from the unconfirmed alert, PRD §11.3). */
    fun hasAnyValue(c: TaskSetConfig): Boolean =
        c.reps > 0 || (c.weightKg ?: 0.0) > 0 || (c.durationSeconds ?: 0) > 0 ||
            (c.distance ?: 0.0) > 0 || (c.speedMph ?: 0.0) > 0 || (c.incline ?: 0.0) > 0 || c.rpe != null

    /** The value a metric holds in a set, null when unset (0 reps = unset). */
    fun value(metric: LogMetric, c: TaskSetConfig): Double? = when (metric) {
        LogMetric.REPS -> if (c.reps > 0) c.reps.toDouble() else null
        LogMetric.WEIGHT -> c.weightKg?.takeIf { it > 0 }
        LogMetric.DURATION -> c.durationSeconds?.takeIf { it > 0 }?.toDouble()
        LogMetric.DISTANCE -> c.distance?.takeIf { it > 0 }
        LogMetric.SPEED -> c.speedMph?.takeIf { it > 0 }
        LogMetric.INCLINE -> c.incline?.takeIf { it > 0 }
    }

    /** Metrics that carry a value in any of the given sets. */
    fun metricsWithValues(sets: List<TaskSetConfig>): Set<LogMetric> =
        LogMetric.entries.filter { m -> sets.any { value(m, it) != null } }.toSet()

    /** The set with one metric cleared (Remove Metric, PRD §5.5). */
    fun clearing(metric: LogMetric, c: TaskSetConfig): TaskSetConfig = when (metric) {
        LogMetric.REPS -> c.copy(reps = 0)
        LogMetric.WEIGHT -> c.copy(weightKg = null)
        LogMetric.DURATION -> c.copy(durationSeconds = null)
        LogMetric.DISTANCE -> c.copy(distance = null, distanceUnit = null)
        LogMetric.SPEED -> c.copy(speedMph = null)
        LogMetric.INCLINE -> c.copy(incline = null)
    }

    /**
     * Only the performance values of a set (Quick Start carry-forward, PRD §2.1 / §16.5): Set RPE
     * is how *that* set felt and is not inherited.
     */
    fun carryForward(from: TaskSetConfig, setNumber: Int): TaskSetConfig = TaskSetConfig(
        setNumber = setNumber, reps = from.reps, weightKg = from.weightKg, durationSeconds = from.durationSeconds,
        distance = from.distance, distanceUnit = from.distanceUnit, speedMph = from.speedMph, incline = from.incline
    )

    // MARK: Validation (PRD §11.1, §16.7)

    /** Reps: non-negative whole numbers. Empty → 0; junk → null (rejected). */
    fun parseReps(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return 0
        if (!t.all { it in '0'..'9' }) return null
        return t.toIntOrNull()?.takeIf { it >= 0 }
    }

    /** Weight / distance / speed / incline: non-negative decimals, a comma accepted as the decimal separator. Empty → 0; junk → null. */
    fun parseDecimal(text: String): Double? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return 0.0
        if (t.count { it == '.' } > 1 || !t.all { it in '0'..'9' || it == '.' }) return null
        val v = t.toDoubleOrNull() ?: return null
        return if (v >= 0 && v.isFinite()) v else null
    }

    /** Keep only what a numeric field may hold (typing filter). */
    fun sanitize(text: String, decimal: Boolean): String {
        var seenDot = false
        val out = StringBuilder()
        for (ch in text.replace(',', '.')) {
            if (ch in '0'..'9') out.append(ch)
            else if (decimal && ch == '.' && !seenDot) { seenDot = true; out.append(ch) }
        }
        return out.toString()
    }

    /** "1:30" (minutes:seconds) for the duration cell. */
    fun formatDuration(seconds: Int): String = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    fun formatNumber(value: Double): String {
        val rounded = (value * 100).roundToLong() / 100.0
        if (rounded == Math.rint(rounded)) return rounded.toLong().toString()
        var s = String.format(Locale.US, "%.2f", rounded)
        while (s.endsWith("0")) s = s.dropLast(1)
        return s
    }
}

// MARK: - Session set plan (PRD §6.6, §6.8)

object SessionSetPlan {
    /** The default plan: programmed sets 1…n. */
    fun programmed(count: Int): List<Int> = if (count >= 1) (1..count).toList() else emptyList()

    /** Programmed set number behind session set [setNumber] (1-based); null for an added set or out of range. */
    fun templateSetNumber(plan: List<Int>, setNumber: Int): Int? {
        if (setNumber < 1 || setNumber > plan.size) return null
        val n = plan[setNumber - 1]
        return if (n > 0) n else null
    }

    /**
     * The programmed target of a session set (PRD §10.3): the per-set config when the builder
     * varied sets, else the task-level plan. Null for an added set or a set with no target at all.
     */
    fun prescribed(task: WorkoutTask, plan: List<Int>, setNumber: Int): TaskSetConfig? {
        val n = templateSetNumber(plan, setNumber) ?: return null
        val planned = task.setConfigs.firstOrNull { it.setNumber == n }
        var c: TaskSetConfig
        if (planned != null) {
            c = planned
            val w = task.weightKg
            if (c.weightKg == null && w != null && w > 0) c = c.copy(weightKg = w.toDouble())
            if (task.usesDuration && (c.durationSeconds ?: 0) <= 0) c = c.copy(durationSeconds = task.durationSeconds)
        } else {
            c = TaskSetConfig(
                setNumber = n,
                reps = if (task.usesDuration) 0 else task.reps,
                weightKg = task.weightKg?.takeIf { it > 0 }?.toDouble(),
                durationSeconds = if (task.usesDuration) task.durationSeconds else null
            )
        }
        // A timed task's leftover task-level reps aren't a target (FTNSSLNK-326).
        if (task.usesDuration && c.reps < 0) c = c.copy(reps = 0)
        if (c.distance == null) task.prescribedDistance?.let { d -> c = c.copy(distance = d.value, distanceUnit = d.unit.rawValue) }
        val speed = task.speed
        if (c.speedMph == null && speed != null && speed > 0) c = c.copy(speedMph = speed)
        val incline = task.incline
        if (c.incline == null && incline != null && incline > 0) c = c.copy(incline = incline)
        c = c.copy(setNumber = setNumber, rpe = null)
        return if (UniversalSetValues.hasAnyValue(c)) c else null
    }
}

// MARK: - RPE scale (PRD §7.4)

/**
 * The shared 1–10 scale for Set, Exercise and Workout RPE: number, short label and one
 * explanatory sentence. Copy is the PRD's proposed wording (final copy approval pending, §18.2 #6);
 * cardio / timed work gets effort-based explanations instead of reps-in-reserve.
 */
object RPEScale {
    val range: IntRange = 1..10

    data class Level(val value: Int, val label: String, val explanation: String)

    val labels: List<String> = listOf(
        "Minimal Effort", "Very Easy", "Easy", "Comfortable", "Moderate",
        "Somewhat Hard", "Challenging", "Hard", "Very Hard", "Maximal Effort"
    )

    val strengthExplanations: List<String> = listOf(
        "Almost no effort; extremely easy.",
        "Very light effort; could continue comfortably.",
        "Light effort; little fatigue.",
        "Steady, manageable effort with plenty left.",
        "Noticeable effort, but still comfortable.",
        "Working, but still in control.",
        "Hard effort; could do 2–3 more reps.",
        "Very challenging; could do 1–2 more reps.",
        "Near-maximal effort; approximately 1 rep left.",
        "Maximum effort; no additional reps possible."
    )

    val cardioExplanations: List<String> = listOf(
        "Almost no effort; extremely easy.",
        "Very light effort; could continue comfortably.",
        "Light effort; easy breathing.",
        "Steady, manageable pace with plenty left.",
        "Noticeable effort, but still comfortable.",
        "Working, but still in control; talking takes effort.",
        "Hard effort; could hold this pace a few more minutes.",
        "Very challenging; only a short push left.",
        "Near-maximal effort; seconds left at this pace.",
        "Maximum effort; nothing left."
    )

    fun clamp(value: Int): Int = value.coerceIn(range.first, range.last)

    fun level(value: Int, cardio: Boolean = false): Level {
        val v = clamp(value)
        val explanations = if (cardio) cardioExplanations else strengthExplanations
        return Level(v, labels[v - 1], explanations[v - 1])
    }
}

// MARK: - Performance header (PRD §4, §16.9)

/** One completed historical set of an exercise, with its session. */
data class HistoricalSet(
    val sessionId: String,
    val dateMs: Long,
    val setNumber: Int,
    val reps: Int,
    val weightLbs: Double,
    val durationSeconds: Int? = null,
    val distance: DistanceValue? = null,
    val rpe: Int? = null
)

/**
 * The compact cards above the set table. Only completed sets of the same exercise feed them
 * (§4.7); each figure is null when no comparable history exists and the card shows its empty state.
 */
data class ExercisePerformanceSummary(
    /** Heaviest completed set of the most recent session with a weighted set; ties → most reps (§4.2). */
    val lastTime: HistoricalSet? = null,
    /** Heaviest completed set ever; ties → most reps, then most recent (§4.3). */
    val topSet: HistoricalSet? = null,
    /** Longest completed duration (§4.4). */
    val bestTime: HistoricalSet? = null,
    /** Greatest completed distance, compared in the user's unit (§4.5). */
    val bestDistance: HistoricalSet? = null,
    /**
     * Last Time for timed / distance work (the mocks' Plank and Treadmill cards): the longest
     * duration and the greatest distance of the most recent session that recorded one.
     */
    val lastDuration: HistoricalSet? = null,
    val lastDistance: HistoricalSet? = null
) {
    val isEmpty: Boolean
        get() = lastTime == null && topSet == null && bestTime == null && bestDistance == null &&
            lastDuration == null && lastDistance == null

    companion object {
        /** Lighter-than ordering for "heaviest, then most reps". */
        private val heavier: Comparator<HistoricalSet> = compareBy<HistoricalSet> { it.weightLbs }.thenBy { it.reps }

        fun build(sets: List<HistoricalSet>, unit: DistanceUnit = DistanceUnit.current): ExercisePerformanceSummary {
            val weighted = sets.filter { it.weightLbs > 0 }
            val lastTime = weighted.maxByOrNull { it.dateMs }?.let { latest ->
                weighted.filter { it.sessionId == latest.sessionId }.maxWithOrNull(heavier)
            }
            val topSet = weighted.maxWithOrNull(compareBy<HistoricalSet> { it.weightLbs }.thenBy { it.reps }.thenBy { it.dateMs })
            val timed = sets.filter { (it.durationSeconds ?: 0) > 0 }
            val bestTime = timed.maxWithOrNull(compareBy<HistoricalSet> { it.durationSeconds ?: 0 }.thenBy { it.dateMs })
            val byDistance = compareBy<HistoricalSet> { it.distance!!.converted(unit).value }.thenBy { it.dateMs }
            val distanced = sets.filter { it.distance != null }
            val bestDistance = distanced.maxWithOrNull(byDistance)
            val lastDistance = distanced.maxByOrNull { it.dateMs }?.let { latest ->
                distanced.filter { it.sessionId == latest.sessionId }.maxWithOrNull(byDistance)
            }
            val lastDuration = timed.maxByOrNull { it.dateMs }?.let { latest ->
                timed.filter { it.sessionId == latest.sessionId }.maxByOrNull { it.durationSeconds ?: 0 }
            }
            return ExercisePerformanceSummary(lastTime, topSet, bestTime, bestDistance, lastDuration, lastDistance)
        }

        /** "30 lbs × 8" in the user's weight unit. */
        fun weightRepsLine(s: HistoricalSet): String = weightRepsLine(s.weightLbs, s.reps)

        fun weightRepsLine(lbs: Double, reps: Int): String {
            val weight = WeightUnit.displayString(fromLbs = lbs)
            return if (reps > 0) "$weight × $reps" else weight
        }
    }
}

/** One previous session of an exercise, for Exercise History (PRD §9). */
data class ExerciseHistorySession(
    val sessionId: String,
    val dateMs: Long,
    val workoutName: String,
    val sets: List<HistoricalSet>,
    val exerciseRPE: Int? = null,
    val trainingNotes: String? = null
) {
    val id: String get() = sessionId

    /** Σ reps × weight over completed sets with both (PRD §16.8). */
    val volumeLbs: Double get() = sets.sumOf { if (it.weightLbs > 0 && it.reps > 0) it.weightLbs * it.reps else 0.0 }

    /** Exercise RPE + Training Notes of one session (keyed by lowercased session id in [group]). */
    data class Note(val rpe: Int?, val notes: String?)

    companion object {
        /** Group flat set rows into sessions, newest first (§9.2). */
        fun group(sets: List<HistoricalSet>, names: Map<String, String>, notes: Map<String, Note>): List<ExerciseHistorySession> =
            sets.groupBy { it.sessionId }.map { (id, rows) ->
                val note = notes[id.lowercase(Locale.US)]
                ExerciseHistorySession(
                    sessionId = id,
                    dateMs = rows.minOfOrNull { it.dateMs } ?: 0L,
                    workoutName = names[id] ?: "Workout",
                    sets = rows.sortedBy { it.setNumber },
                    exerciseRPE = note?.rpe,
                    trainingNotes = note?.notes
                )
            }.sortedByDescending { it.dateMs }
    }
}

// MARK: - Workout-end unconfirmed sets (§11.3)

/** One row of the "You Have Unconfirmed Sets" sheet. */
data class UnconfirmedSetItem(val task: WorkoutTask, val setNumber: Int, val values: TaskSetConfig?) {
    val id: String get() = "${task.id}#$setNumber"

    companion object {
        /** "12 reps · 25 lbs" — what the set holds, in display units. */
        fun line(c: TaskSetConfig): String {
            val parts = mutableListOf<String>()
            if (c.reps > 0) parts.add("${c.reps} reps")
            c.weightKg?.takeIf { it > 0 }?.let { parts.add(WeightUnit.displayString(fromLbs = it)) }
            c.durationSeconds?.takeIf { it > 0 }?.let { parts.add(UniversalSetValues.formatDuration(it)) }
            c.distanceValue?.let { parts.add(DistanceUnit.displayInCurrent(it.value, it.unit)) }
            return parts.joinToString(" · ")
        }

        /** "You have 2 sets with recorded performance that haven't been marked complete. Did you complete them?" */
        fun message(count: Int): String {
            val one = count == 1
            return "You have $count ${if (one) "set" else "sets"} with recorded performance that ${if (one) "hasn't" else "haven't"} been marked complete. Did you complete ${if (one) "it" else "them"}?"
        }
    }
}
