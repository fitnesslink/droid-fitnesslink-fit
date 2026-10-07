package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.SessionExerciseRow
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// MARK: - RPE (FTNSSLNK-411)

/**
 * The Workout Summary's optional 0–10 effort rating. Integer steps, anchored `0 = Not difficult` /
 * `10 = Maximum difficulty`; null when the user finished without rating. Once saved it may be
 * changed once, within 24 hours.
 */
object SummaryRPE {
    val range: IntRange = 0..10
    const val prompt = "How did it feel?"
    const val minAnchor = "0 = Not difficult"
    const val maxAnchor = "10 = Maximum difficulty"
    /** How long after completion the rating may still be changed. */
    const val editWindowMs: Long = 24L * 60 * 60 * 1000

    fun clamp(value: Int): Int = value.coerceIn(range.first, range.last)

    /** Slider thumb fraction (0…1 along the track) → the nearest whole step. */
    fun valueFromFraction(fraction: Double): Int {
        val bounded = fraction.coerceIn(0.0, 1.0)
        return clamp((bounded * range.last).roundToInt())
    }

    /** 0…1 position of a value along the track. */
    fun fraction(value: Int): Double = clamp(value).toDouble() / range.last.toDouble()

    fun label(value: Int): String = when (clamp(value)) {
        0 -> "Not difficult"
        1, 2 -> "Very light"
        3, 4 -> "Light"
        5, 6 -> "Moderate"
        7, 8 -> "Hard"
        9 -> "Very hard"
        else -> "Maximum difficulty"
    }

    /** What goes on the wire: the whole number, or nothing at all. */
    fun wireValue(value: Int?): Int? = value?.let(::clamp)

    /** What the local row stores (the `rpeValue` REAL column). */
    fun storedValue(value: Int?): Double? = value?.let { clamp(it).toDouble() }

    /** Numeric text for history — `7`, or `7.5` for a legacy half-step row. */
    fun displayText(stored: Double?): String? = stored?.let { WeightUnit.format(it) }

    /** The rating may be changed once, within 24 h of completion. A row with no completion date is never editable. */
    fun isEditable(completionDateMs: Long?, editedAtMs: Long?, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (completionDateMs == null || editedAtMs != null) return false
        val age = nowMs - completionDateMs
        return age >= 0 && age <= editWindowMs
    }
}

// MARK: - Actual stats (FTNSSLNK-412)

/** An exercise the user did not fully complete — skipped outright or stopped short of its required sets. */
data class MissedExercise(
    val id: String,
    val name: String,
    val completedSets: Int,
    val requiredSets: Int
) {
    /** `Skipped` when nothing was done; otherwise `2 of 3 sets`. */
    val detail: String
        get() = if (completedSets <= 0) "Skipped" else "$completedSets of $requiredSets ${if (requiredSets == 1) "set" else "sets"}"
}

/**
 * The saved session row as the summary needs it (reopen from history). Mirrors the columns iOS
 * reads through `SessionRow`; `SessionDao.sessionDetailRow` builds it.
 */
data class SavedSessionRow(
    val id: String,
    val workoutId: String,
    val programId: String?,
    val workoutName: String,
    val startDateMs: Long,
    val completionDateMs: Long?,
    val durationSeconds: Int,
    val isCompleted: Boolean,
    val exerciseCount: Int,
    val totalSets: Int,
    val totalReps: Int,
    val totalWeightLifted: Double,
    val totalCaloriesBurned: Double,
    val rpeValue: Double?,
    val rpeEditedAtMs: Long?,
    val completionPercent: Int?,
    val progressJson: String?
) {
    fun canEditRpe(nowMs: Long = System.currentTimeMillis()): Boolean =
        SummaryRPE.isEditable(completionDateMs, rpeEditedAtMs, nowMs)
}

/**
 * What the user actually did in a session, built from the same per-set rows the history table and
 * the sessions API receive ([SessionHistoryRows.build]) so the summary and Workout History agree.
 */
data class WorkoutSummaryStats(
    /** Wall-clock session time, rest and pauses included. */
    val durationSeconds: Int,
    /** Exercises whose every required set is completed. */
    val exercisesCompleted: Int,
    /** Sets in the completed state, logged or not. */
    val setsCompleted: Int,
    /** Reps across completed sets. */
    val totalReps: Int,
    /** Σ(weight × reps) over completed sets that carry a load, stored lbs. Null hides the stat. */
    val totalVolumeLbs: Double?,
    /** Exercises skipped or not fully completed, in workout order. Empty = everything done; null = unknown. */
    val missed: List<MissedExercise>?,
    /** Completed sets ÷ required sets; null when unknown. */
    val completionPercent: Int?,
    /** Total logged distance, already in the display unit; null when no set carried one. */
    val distance: DistanceValue?
) {
    /** `hh:mm:ss`, frozen at completion. */
    val durationText: String
        get() {
            val s = durationSeconds
            return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        }

    /** `12,450 lbs` in the display unit; null hides the stat. */
    val volumeText: String? get() = totalVolumeLbs?.let { formatVolume(it) }

    /** `2.5 km`; null hides the stat. */
    val distanceText: String?
        get() {
            val d = distance ?: return null
            if (d.value <= 0) return null
            return DistanceUnit.display(d.value, d.unit)
        }

    /** `Completed · 62%` when the session stopped short; null at 100 (or when unknown). */
    val completionText: String?
        get() {
            val p = completionPercent ?: return null
            if (p >= 100) return null
            return "Completed · $p%"
        }

    /** The missed-exercises section: null hides it, an empty list reads `All exercises completed ✓`. */
    val isFullyCompleted: Boolean get() = missed?.isEmpty() == true

    companion object {
        const val allCompletedText = "All exercises completed ✓"

        /** Build from a session's per-set state. [distance] is the session's total in the display unit. */
        fun build(
            tasks: List<WorkoutTask>,
            actualSetLogs: Map<String, Map<Int, TaskSetConfig>>,
            progress: SessionProgress,
            requiredIterations: Map<String, Int>,
            durationSeconds: Int,
            distance: DistanceValue? = null
        ): WorkoutSummaryStats {
            val rows = SessionHistoryRows.build(tasks, actualSetLogs, progress, requiredIterations)
            return build(tasks, rows, requiredIterations, durationSeconds, progress.completionPercent(requiredIterations), distance)
        }

        /** The row-level rule, shared with `SessionRecorder` so the stored session totals are the summary's numbers. */
        fun build(
            tasks: List<WorkoutTask>,
            rows: List<SessionHistoryRow>,
            requiredIterations: Map<String, Int>,
            durationSeconds: Int,
            completionPercent: Int?,
            distance: DistanceValue? = null
        ): WorkoutSummaryStats {
            val performed = rows.filter { it.isPerformed }
            val completedByTask = mutableMapOf<String, Int>()
            for (row in performed) completedByTask[row.task.id] = (completedByTask[row.task.id] ?: 0) + 1

            var exercises = 0
            val missed = mutableListOf<MissedExercise>()
            for (task in tasks) {
                if (!task.isMovement) continue
                val required = maxOf(
                    requiredIterations[task.id]
                        ?: (if (task.groupId.isEmpty()) maxOf(task.sets, 1) else maxOf(task.rounds ?: task.totalRounds, 1)),
                    1
                )
                val done = completedByTask[task.id] ?: 0
                if (done >= required) exercises += 1
                else missed.add(MissedExercise(task.id, task.name, done, required))
            }

            val weighted = performed.filter { it.weightLbs > 0 && it.reps > 0 }
            val volume: Double? = if (weighted.isEmpty()) null else weighted.sumOf { it.weightLbs * it.reps }

            return WorkoutSummaryStats(
                durationSeconds = maxOf(0, durationSeconds),
                exercisesCompleted = exercises,
                setsCompleted = performed.size,
                totalReps = performed.sumOf { it.reps },
                totalVolumeLbs = volume,
                missed = missed,
                completionPercent = completionPercent,
                distance = distance
            )
        }

        /**
         * Stats for a saved session reopened from history. Rebuilds the per-set rows when the workout
         * is still cached and the row carries its per-set state; otherwise the stored totals stand in
         * and the missed list is unknown.
         */
        fun saved(
            row: SavedSessionRow,
            exercises: List<SessionExerciseRow>,
            tasks: List<WorkoutTask>?,
            progress: SessionProgress?,
            logs: Map<String, Map<Int, TaskSetConfig>>,
            distance: DistanceValue?
        ): WorkoutSummaryStats {
            if (tasks != null && progress != null && tasks.isNotEmpty()) {
                return build(tasks, logs, progress, SessionProgress.requiredIterations(tasks), row.durationSeconds, distance)
            }
            val weighted = exercises.filter { it.weightLifted > 0 && it.reps > 0 }
            val volume: Double? = if (weighted.isEmpty()) null else weighted.sumOf { it.weightLifted * it.reps }
            return WorkoutSummaryStats(
                durationSeconds = row.durationSeconds,
                exercisesCompleted = row.exerciseCount,
                setsCompleted = if (row.totalSets > 0) row.totalSets else exercises.size,
                totalReps = if (row.totalReps > 0) row.totalReps else exercises.sumOf { it.reps },
                totalVolumeLbs = volume,
                missed = null,
                completionPercent = row.completionPercent,
                distance = distance
            )
        }

        fun formatVolume(lbs: Double): String {
            val value = WeightUnit.displayValue(fromLbs = lbs).roundToLong()
            val formatter = NumberFormat.getIntegerInstance(Locale.US).apply { isGroupingUsed = true }
            return "${formatter.format(value)} ${WeightUnit.current.label}"
        }
    }
}

// MARK: - Celebration layout (FTNSSLNK-414)

/** How the RPE block appears on the summary. */
sealed class SummaryRPEMode {
    /** The slider — a fresh finish, or a reopen still inside the edit window. */
    object Editable : SummaryRPEMode()
    /** The stored rating, shown as a number. */
    data class ReadOnly(val value: Int) : SummaryRPEMode()
    /** A reopen with no rating and the window closed. */
    object Hidden : SummaryRPEMode()
}

/** The summary's section order and visibility rules, as a pure model the view renders top to bottom. */
object WorkoutSummaryLayout {
    enum class Section { CELEBRATION, WORKOUT_NAME, RPE, STATS, MISSED_EXERCISES, ACHIEVEMENTS }

    const val congratulations = "🎉 Congratulations!"
    const val headline = "WORKOUT COMPLETE ✓"
    const val primaryAction = "Done"
    const val secondaryAction = "Share"

    fun sections(rpe: SummaryRPEMode, stats: WorkoutSummaryStats, achievements: SessionAchievements): List<Section> {
        val out = mutableListOf(Section.CELEBRATION, Section.WORKOUT_NAME)
        if (rpe != SummaryRPEMode.Hidden) out.add(Section.RPE)
        out.add(Section.STATS)
        if (stats.missed != null) out.add(Section.MISSED_EXERCISES)
        if (!achievements.isEmpty) out.add(Section.ACHIEVEMENTS)
        return out
    }

    /** Copy reinforces accomplishment — never storage language. */
    val forbiddenCopy = listOf("saved", "database", "synced", "recorded")

    fun copyIsCelebratory(text: String): Boolean {
        val lowered = text.lowercase(Locale.US)
        return forbiddenCopy.none { lowered.contains(it) }
    }

    /** The RPE block for a session reopened from history. */
    fun rpeMode(row: SavedSessionRow, nowMs: Long = System.currentTimeMillis()): SummaryRPEMode {
        if (row.canEditRpe(nowMs)) return SummaryRPEMode.Editable
        row.rpeValue?.let { return SummaryRPEMode.ReadOnly(it.roundToInt()) }
        return SummaryRPEMode.Hidden
    }
}
