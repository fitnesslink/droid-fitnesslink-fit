package com.fitnesslink.fit.goals

import com.fitnesslink.fit.model.CustomGoalMetric
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalActivity
import com.fitnesslink.fit.model.GoalDay
import com.fitnesslink.fit.model.GoalEntry
import com.fitnesslink.fit.model.GoalFrequency
import com.fitnesslink.fit.model.GoalMacroTarget
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.model.GoalSubtype
import com.fitnesslink.fit.model.GoalTemplate
import com.fitnesslink.fit.model.GoalTrackingSource
import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.MacroKey
import com.fitnesslink.fit.model.NutritionHabit
import com.fitnesslink.fit.model.RecoveryMetric
import com.fitnesslink.fit.model.StrengthMetric
import com.fitnesslink.fit.model.WaterUnit
import com.fitnesslink.fit.model.WeightUnit
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Port of iOS `Services/GoalProgressEngine.swift`. Pure Kotlin: every input
// comes through [GoalDataSource], so the rules are unit-testable on the JVM.
// Progress is built fresh from source data on every read, so editing or
// deleting a weight entry, workout, food or water log, or a check-in is
// reflected immediately. Missing data stays missing: `fraction` is null
// when no meaningful ratio exists, and empty periods are "no data", never 0.

// MARK: - Inputs

/** One recorded working set, for strength goals (iOS `GoalSetRecord`). [date] is epoch millis. */
data class GoalSetRecord(
    val sessionId: String,
    val exerciseName: String,
    val date: Long,
    val reps: Int,
    val weightLbs: Double
)

data class GoalWeightPoint(val id: String, val date: Long, val weight: Double, val unit: WeightUnit)

data class GoalNutritionDay(val day: LocalDate, val calories: Double, val protein: Double, val carbs: Double, val fat: Double)

data class GoalFoodItem(val mealType: String, val protein: Double)

/** A completed workout session. [date] = completion date (start date when missing), epoch millis. */
data class GoalSessionRecord(val id: String, val workoutName: String, val date: Long, val programId: String?)

/** The live Nutrition targets (iOS `DatabaseManager.nutritionGoal()`); 0 = not set. */
data class GoalNutritionTargets(val calories: Int = 0, val protein: Int = 0, val carbs: Int = 0, val fat: Int = 0)

/**
 * Everything the engine reads. The production implementation is
 * [LocalGoalDataSource] (SQLite cache + Health Connect); tests pass a fake.
 * Health readings (sleep, resting heart rate / HRV, steps) default to "not
 * connected" so fakes only implement them when a test needs them.
 */
interface GoalDataSource {
    fun weightEntries(): List<GoalWeightPoint>
    /** Working sets (warm-ups and Warm-Up / Cool Down phases excluded), oldest first; null name = every exercise. */
    fun setRecords(movementName: String?): List<GoalSetRecord>
    fun nutritionTargets(): GoalNutritionTargets
    fun nutritionDays(since: LocalDate): List<GoalNutritionDay>
    fun foodItems(day: LocalDate): List<GoalFoodItem>
    fun hydrationGoal(): HydrationGoal
    fun waterTotal(day: LocalDate, unit: WaterUnit): Double
    /** Completed sessions on/after [since], any order. */
    fun completedSessions(since: LocalDate): List<GoalSessionRecord>
    /** A goal's manual entries, newest first. */
    fun goalEntries(goalId: String): List<GoalEntry>

    val healthConnected: Boolean get() = false
    /** Seconds asleep per night, keyed by the morning the night ended. */
    fun sleepByNight(from: LocalDate, to: LocalDate): Map<LocalDate, Double> = emptyMap()
    fun recoveryDailyAverages(metric: RecoveryMetric, from: LocalDate, to: LocalDate): Map<LocalDate, Double> = emptyMap()
    fun dailySteps(from: LocalDate, to: LocalDate): Map<LocalDate, Double> = emptyMap()
}

// MARK: - Output

/** What a goal's screens show (iOS `GoalProgress`). */
data class GoalProgress(
    /** Card headline, e.g. "200 lb → 180 lb" or "2 of 4 workouts this week". */
    var summary: String,
    /** Card second line, e.g. "20 lb change remaining". */
    var detail: String? = null,
    /** 0…1, capped. Null = no meaningful progress ratio. */
    var fraction: Double? = null,
    var targetReached: Boolean = false,
    /** False when no qualifying data exists yet for the current period. */
    var hasData: Boolean = true,
    var stats: MutableList<GoalStat> = mutableListOf(),
    var chart: GoalChart? = null,
    var weeks: List<GoalWeek> = emptyList(),
    var recent: List<GoalRecentItem> = emptyList(),
    var activities: List<GoalActivityProgress> = emptyList(),
    /** The logging action that moves this goal forward. */
    var action: GoalAction? = null,
    var notes: MutableList<String> = mutableListOf(),
    /** "Last logged Oct 25 · Weight Log". */
    var latestEntryText: String? = null
) {
    companion object {
        fun empty(summary: String, action: GoalAction? = null) = GoalProgress(summary = summary, hasData = false, action = action)
    }
}

data class GoalStat(val label: String, val value: String)

data class GoalChart(
    val points: List<GoalChartPoint>,
    val target: Double?,
    val unit: String,
    val style: Style,
    val title: String
) {
    enum class Style { LINE, BAR }
}

data class GoalChartPoint(val date: LocalDate, val value: Double)

data class GoalWeek(
    val weekStart: LocalDate,
    val completed: Int,
    val required: Int,
    val inProgress: Boolean,
    /** Days in the week that had any qualifying data at all. */
    val daysWithData: Int
) {
    val met: Boolean get() = completed >= required
}

data class GoalRecentItem(
    val id: String,
    val date: LocalDate,
    val title: String,
    val detail: String?,
    /** Set for manual entries, which the user can delete to correct. */
    val entry: GoalEntry? = null
)

data class GoalActivityProgress(
    val id: String,
    val name: String,
    val completed: Int,
    val required: Int,
    val tracking: GoalTrackingSource
)

sealed class GoalAction {
    object LogWeight : GoalAction()
    object LogFood : GoalAction()
    object LogWater : GoalAction()
    object LogSleep : GoalAction()
    object LogWorkout : GoalAction()
    object ConnectHealth : GoalAction()
    /** Check-in for the goal (or one routine activity). */
    data class CheckIn(val activityKey: String?) : GoalAction()
    /** Log an amount (servings, minutes, miles, count). */
    data class LogAmount(val unit: String) : GoalAction()

    val title: String
        get() = when (this) {
            LogWeight -> "Log Weight"
            LogFood -> "Log Food"
            LogWater -> "Log Water"
            LogSleep -> "Log Sleep"
            LogWorkout -> "Log Workout"
            ConnectHealth -> "Data Source Options"
            is CheckIn -> "Complete Habit"
            is LogAmount -> "Log Activity"
        }
}

/** Lifecycle shown to the user. A missed deadline never changes the stored status — the goal stays active (PRD 1.7). */
enum class GoalPhase(val label: String) {
    IN_PROGRESS("In Progress"),
    TARGET_REACHED("Target Reached"),
    DEADLINE_PASSED("Deadline Passed"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    ARCHIVED("Archived")
}

fun Goal.phase(progress: GoalProgress?, today: LocalDate = GoalDay.today()): GoalPhase {
    when (status) {
        GoalStatus.Paused -> return GoalPhase.PAUSED
        GoalStatus.Completed -> return GoalPhase.COMPLETED
        GoalStatus.Archived -> return GoalPhase.ARCHIVED
        GoalStatus.Active -> Unit
    }
    if (progress?.targetReached == true) return GoalPhase.TARGET_REACHED
    val target = targetDate
    if (target != null && GoalDay.localDate(target).isBefore(today)) return GoalPhase.DEADLINE_PASSED
    return GoalPhase.IN_PROGRESS
}

/** "23 days left", "Due today", "Deadline passed Oct 1". */
fun Goal.deadlineText(today: LocalDate = GoalDay.today()): String? {
    val target = targetDate ?: return null
    val day = GoalDay.localDate(target)
    val days = ChronoUnit.DAYS.between(today, day)
    return when {
        days > 1 -> "$days days left"
        days == 1L -> "1 day left"
        days == 0L -> "Due today"
        else -> "Deadline passed ${GoalFormat.shortDate(day)}"
    }
}

// MARK: - Formatting

object GoalFormat {
    fun number(value: Double, maxDecimals: Int = 1): String {
        val f = NumberFormat.getNumberInstance(Locale.getDefault())
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = maxDecimals
        return f.format(if (value == -0.0) 0.0 else value)
    }

    fun weightUnit(goal: Goal): WeightUnit =
        WeightUnit.entries.firstOrNull { it.label == goal.settings.unit } ?: WeightUnit.defaultUnit

    /** "lb" / "kg" for display. */
    fun label(unit: WeightUnit): String = if (unit == WeightUnit.LBS) "lb" else "kg"

    fun weight(value: Double, unit: WeightUnit): String = "${number(value)} ${label(unit)}"

    fun sleep(minutes: Double): String {
        val total = minutes.roundToInt()
        val h = total / 60
        val m = total % 60
        if (h == 0) return "$m min"
        return if (m == 0) "$h h" else "$h h $m min"
    }

    private val shortFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    fun shortDate(day: LocalDate): String = day.format(shortFormat)
}

/**
 * Provisional strength rules (the Strength Metrics Calculation PRD does not
 * exist yet; replace these when it does):
 * - Load lifted = heaviest completed working set.
 * - Estimated 1RM = Epley, weight × (1 + reps / 30), from sets of 1–12 reps;
 *   a single is its own 1RM. Always labelled "Estimated".
 * - Volume = Σ weight × reps over working sets (warm-up sets and Warm-Up /
 *   Cool Down phases excluded), the Workout Report's definition.
 */
object StrengthMetrics {
    const val MAX_REPS_FOR_ESTIMATE = 12

    fun estimatedOneRepMax(weight: Double, reps: Int): Double? {
        if (weight <= 0 || reps < 1 || reps > MAX_REPS_FOR_ESTIMATE) return null
        return if (reps == 1) weight else weight * (1 + reps / 30.0)
    }

    fun value(metric: StrengthMetric, set: GoalSetRecord): Double? = when (metric) {
        StrengthMetric.LOAD -> set.weightLbs.takeIf { it > 0 }
        StrengthMetric.ESTIMATED_ONE_REP_MAX -> estimatedOneRepMax(set.weightLbs, set.reps)
        StrengthMetric.REPS -> set.reps.takeIf { it > 0 }?.toDouble()
        StrengthMetric.VOLUME -> set.weightLbs * set.reps
    }

    /** Loads within half a unit match (logged loads are rounded in the UI). */
    fun sameLoad(lbs: Double, targetLbs: Double): Boolean = abs(lbs - targetLbs) < 0.75
}

// MARK: - Engine

object GoalProgressEngine {
    fun progress(goal: Goal, data: GoalDataSource, today: LocalDate = GoalDay.today()): GoalProgress {
        val subtype = goal.subtype ?: return legacy(goal)
        val ctx = Ctx(goal, data, today)
        return when (subtype) {
            GoalSubtype.LoseWeight -> ctx.weight()
            GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord -> ctx.liftBest()
            GoalSubtype.RepsAtWeight -> ctx.repsAtWeight()
            GoalSubtype.TrainingVolume -> ctx.volume()
            GoalSubtype.SleepDuration, GoalSubtype.SleepConsistency -> ctx.sleep()
            GoalSubtype.RecoveryMetric -> ctx.recoveryMetric()
            GoalSubtype.CalorieTarget -> ctx.calories()
            GoalSubtype.MacroTarget -> ctx.macros()
            GoalSubtype.Hydration -> ctx.hydration()
            GoalSubtype.HealthyHabit -> ctx.nutritionHabit()
            GoalSubtype.WorkoutConsistency -> ctx.workouts()
            GoalSubtype.RecoveryRoutine, GoalSubtype.MovementRoutine, GoalSubtype.WellnessRoutine, GoalSubtype.DailyHabit -> ctx.checkIns()
            GoalSubtype.CustomRoutine -> ctx.customRoutine()
            GoalSubtype.CustomGoal -> ctx.custom()
        }
    }

    /** Goals created before the typed flow keep their stored value. */
    private fun legacy(goal: Goal): GoalProgress {
        val unit = goal.targetUnit ?: ""
        val target = goal.targetValue
        if (target == null || target <= 0) return GoalProgress.empty(goal.title)
        return GoalProgress(
            summary = "${GoalFormat.number(goal.currentValue)} of ${GoalFormat.number(target)} $unit".trimEnd(),
            fraction = min(1.0, goal.currentValue / target)
        )
    }

    private class Ctx(val goal: Goal, val data: GoalDataSource, val today: LocalDate) {
        val settings = goal.settings
        val startDay: LocalDate = GoalDay.localDate(goal.startDate)

        fun day(millis: Long): LocalDate = GoalDay.localDate(millis)

        // MARK: Helpers

        /** Week starts from the goal's first week to now, newest last. */
        fun weekStarts(limit: Int = 12): List<LocalDate> {
            val starts = mutableListOf<LocalDate>()
            var cursor = GoalDay.weekStart(today)
            val first = GoalDay.weekStart(startDay)
            while (!cursor.isBefore(first) && starts.size < limit) {
                starts.add(cursor)
                cursor = cursor.minusDays(7)
            }
            return starts.reversed()
        }

        /** Days of a week that have happened (on/after the goal start, up to today). */
        fun elapsedDays(weekStart: LocalDate): List<LocalDate> =
            (0L..6L).map { weekStart.plusDays(it) }.filter { !it.isBefore(startDay) && !it.isAfter(today) }

        fun weeklyRequired(): Int = if (goal.frequency == GoalFrequency.Daily) 7 else max(1, goal.frequencyTarget ?: 1)

        /** Build week rows by asking [qualifies] about each elapsed day (null = no data that day). */
        fun dayWeeks(qualifies: (LocalDate) -> Boolean?): List<GoalWeek> {
            val thisWeek = GoalDay.weekStart(today)
            return weekStarts().map { start ->
                val results = elapsedDays(start).map(qualifies)
                GoalWeek(start, results.count { it == true }, weeklyRequired(), start == thisWeek, results.count { it != null })
            }
        }

        fun frequencySummary(week: GoalWeek?, noun: String): Triple<String, String?, Double?> {
            if (week == null) return Triple("No activity yet", null, null)
            if (goal.frequency == GoalFrequency.Daily) {
                val summary = "${week.completed} of ${min(7, week.required)} days this week"
                return Triple(summary, if (week.met) "Weekly target met" else null, week.completed.toDouble() / week.required)
            }
            val remaining = max(0, week.required - week.completed)
            val detail = if (remaining == 0) "Weekly target met" else "$remaining $noun remaining this week"
            return Triple("${week.completed} of ${week.required} $noun this week", detail,
                min(1.0, week.completed.toDouble() / week.required))
        }

        fun entries(): List<GoalEntry> = data.goalEntries(goal.id).filter { !it.day.isBefore(startDay) }

        fun recentItems(entries: List<GoalEntry>, unit: String?, limit: Int = 10, title: (GoalEntry) -> String): List<GoalRecentItem> =
            entries.take(limit).map { entry ->
                var detail = if (entry.source == "Manual") "Manual entry" else entry.source
                val value = entry.value
                if (value != null && !unit.isNullOrEmpty()) detail = "${GoalFormat.number(value)} $unit · $detail"
                GoalRecentItem(entry.id, entry.day, title(entry), detail, entry)
            }

        // MARK: Lose Weight

        fun weight(): GoalProgress {
            val unit = GoalFormat.weightUnit(goal)
            val baseline = goal.baselineValue ?: 0.0
            val target = goal.targetValue ?: 0.0
            val baselineDay = day(goal.baselineDate ?: goal.startDate)
            val qualifying = data.weightEntries()
                .map { Triple(day(it.date), it.unit.convert(it.weight, unit), it) }
                .sortedBy { it.third.date }
                .filter { !it.first.isBefore(baselineDay) }

            val progress = GoalProgress(summary = "${GoalFormat.weight(baseline, unit)} → ${GoalFormat.weight(target, unit)}", action = GoalAction.LogWeight)
            progress.stats = mutableListOf(GoalStat("Starting", GoalFormat.weight(baseline, unit)), GoalStat("Target", GoalFormat.weight(target, unit)))
            progress.recent = qualifying.reversed().take(10).map {
                GoalRecentItem(it.third.id, it.first, GoalFormat.weight(it.second, unit), "Weight Log")
            }
            val latest = qualifying.lastOrNull()
            if (latest == null) {
                progress.hasData = false
                progress.detail = "Log a weight to start tracking"
                progress.stats.add(0, GoalStat("Current", "—"))
                return progress
            }
            val current = latest.second
            val total = baseline - target
            val changed = baseline - current
            val remaining = max(0.0, current - target)
            progress.fraction = if (total > 0) min(1.0, max(0.0, changed / total)) else null
            progress.targetReached = current <= target
            progress.summary = "${GoalFormat.number(current)} ${GoalFormat.label(unit)} → ${GoalFormat.weight(target, unit)}"
            progress.detail = if (progress.targetReached) "Target reached" else "${GoalFormat.weight(remaining, unit)} change remaining"
            progress.stats.add(0, GoalStat("Current", GoalFormat.weight(current, unit)))
            val changeText = if (changed >= 0) "−${GoalFormat.weight(changed, unit)}" else "+${GoalFormat.weight(-changed, unit)}"
            progress.stats.add(GoalStat("Change", changeText))
            progress.stats.add(GoalStat("Remaining", GoalFormat.weight(remaining, unit)))
            progress.latestEntryText = "Last logged ${GoalFormat.shortDate(latest.first)} · Weight Log"
            if (qualifying.size >= 2) {
                progress.chart = GoalChart(qualifying.map { GoalChartPoint(it.first, it.second) }, target,
                    GoalFormat.label(unit), GoalChart.Style.LINE, "Weight trend")
            }
            return progress
        }

        // MARK: Get Stronger

        fun strengthMetric(): StrengthMetric = StrengthMetric.fromRaw(goal.metricKey) ?: StrengthMetric.LOAD

        /** Improve a Specific Lift / Achieve a Personal Record. */
        fun liftBest(): GoalProgress {
            val unit = GoalFormat.weightUnit(goal)
            val metric = strengthMetric()
            val name = settings.movementName ?: goal.title
            val isPR = goal.subtype == GoalSubtype.PersonalRecord
            val sets = data.setRecords(name)
            val toUnit: (Double) -> Double = { WeightUnit.LBS.convert(it, unit) }
            val baseline = goal.baselineValue ?: 0.0
            val target = goal.targetValue ?: 0.0
            val estimated = if (metric.isEstimated) " (estimated)" else ""
            // A PR goal compares against the all-time record; a lift goal
            // measures what's been done since the goal started.
            val window = if (isPR) sets else sets.filter { !day(it.date).isBefore(startDay) }
            val scored = window.mapNotNull { set -> StrengthMetrics.value(metric, set)?.let { set to toUnit(it) } }

            val progress = GoalProgress(summary = "$name: ${GoalFormat.weight(target, unit)} target", action = GoalAction.LogWorkout)
            progress.stats = mutableListOf(
                GoalStat(if (isPR) "Best at start" else "Baseline", GoalFormat.weight(baseline, unit)),
                GoalStat("Target", GoalFormat.weight(target, unit))
            )
            progress.notes.add("Metric: ${metric.displayName}$estimated. Tracked from completed working sets of $name; warm-up and cool-down sets don't count.")
            if (metric.isEstimated) {
                progress.notes.add("Estimated 1-rep max uses the Epley formula on sets of 1–${StrengthMetrics.MAX_REPS_FOR_ESTIMATE} reps. It is an estimate, not a tested max.")
            }
            progress.recent = window.reversed().take(10).map {
                GoalRecentItem("${it.sessionId}-${it.date}-${it.reps}", day(it.date),
                    "${GoalFormat.weight(toUnit(it.weightLbs), unit)} × ${it.reps}", it.exerciseName)
            }

            val sinceStart = scored.filter { !day(it.first.date).isBefore(startDay) }
            val pool = if (isPR) scored else sinceStart
            val best = pool.maxByOrNull { it.second }
            if (best == null) {
                progress.hasData = false
                progress.detail = "Log $name in a workout to track progress"
                progress.stats.add(0, GoalStat("Current best", "—"))
                return progress
            }
            val current = best.second
            progress.fraction = if (target > baseline) min(1.0, max(0.0, (current - baseline) / (target - baseline))) else null
            progress.targetReached = current >= target
            val remaining = max(0.0, target - current)
            progress.summary = "${GoalFormat.number(current)} of ${GoalFormat.weight(target, unit)} target$estimated"
            progress.detail = if (progress.targetReached) "Target reached" else "${GoalFormat.weight(remaining, unit)} improvement remaining"
            progress.stats.add(0, GoalStat("Current best", GoalFormat.weight(current, unit)))
            progress.stats.add(GoalStat("Improvement", "+${GoalFormat.weight(max(0.0, current - baseline), unit)}"))
            progress.stats.add(GoalStat("Remaining", GoalFormat.weight(remaining, unit)))
            progress.latestEntryText = "Best set ${GoalFormat.shortDate(day(best.first.date))} · Workout log"

            // Trend: best per session (running record for PRs).
            var points = pool.groupBy { it.first.sessionId }.values
                .mapNotNull { group -> group.maxByOrNull { it.second }?.let { it.first.date to it.second } }
                .sortedBy { it.first }
            if (isPR) {
                var record = Double.NEGATIVE_INFINITY
                points = points.filter { p -> if (p.second > record) { record = p.second; true } else false }
            }
            if (points.size >= 2) {
                progress.chart = GoalChart(points.map { GoalChartPoint(day(it.first), it.second) }, target, GoalFormat.label(unit),
                    GoalChart.Style.LINE, if (isPR) "Record history" else "Strength trend")
            }
            return progress
        }

        fun repsAtWeight(): GoalProgress {
            val unit = GoalFormat.weightUnit(goal)
            val name = settings.movementName ?: goal.title
            val load = settings.load ?: 0.0
            val loadLbs = unit.convert(load, WeightUnit.LBS)
            val baseline = (goal.baselineValue ?: 0.0).toInt()
            val target = (goal.targetValue ?: 0.0).toInt()
            val sets = data.setRecords(name).filter { !day(it.date).isBefore(startDay) && StrengthMetrics.sameLoad(it.weightLbs, loadLbs) }

            val progress = GoalProgress(summary = "$target reps at ${GoalFormat.weight(load, unit)}", action = GoalAction.LogWorkout)
            progress.stats = mutableListOf(GoalStat("Baseline", "$baseline reps"), GoalStat("Target", "$target reps"),
                GoalStat("Load", GoalFormat.weight(load, unit)))
            progress.notes.add("Only completed working sets of $name at ${GoalFormat.weight(load, unit)} count.")
            progress.recent = sets.reversed().take(10).map {
                GoalRecentItem("${it.sessionId}-${it.date}-${it.reps}", day(it.date), "${it.reps} reps",
                    "${GoalFormat.weight(load, unit)} · ${it.exerciseName}")
            }
            val best = sets.maxByOrNull { it.reps }
            if (best == null) {
                progress.hasData = false
                progress.detail = "Log $name at ${GoalFormat.weight(load, unit)} to track progress"
                progress.stats.add(0, GoalStat("Current best", "—"))
                return progress
            }
            progress.targetReached = best.reps >= target
            progress.fraction = if (target > baseline) min(1.0, max(0.0, (best.reps - baseline).toDouble() / (target - baseline))) else null
            progress.summary = "${best.reps} of $target reps at ${GoalFormat.weight(load, unit)}"
            progress.detail = if (progress.targetReached) "Target reached" else "${max(0, target - best.reps)} reps remaining"
            progress.stats.add(0, GoalStat("Current best", "${best.reps} reps"))
            progress.latestEntryText = "Best set ${GoalFormat.shortDate(day(best.date))} · Workout log"
            val points = sets.groupBy { it.sessionId }.values
                .mapNotNull { g -> g.maxByOrNull { it.reps }?.let { it.date to it.reps.toDouble() } }
                .sortedBy { it.first }
            if (points.size >= 2) {
                progress.chart = GoalChart(points.map { GoalChartPoint(day(it.first), it.second) }, target.toDouble(), "reps",
                    GoalChart.Style.LINE, "Reps at load")
            }
            return progress
        }

        fun volume(): GoalProgress {
            val unit = GoalFormat.weightUnit(goal)
            val scopeName = if (settings.volumeScopeIsExercise == true) settings.movementName else null
            val target = goal.targetValue ?: 0.0
            val perWorkout = goal.frequency == GoalFrequency.PerWorkout
            val sets = data.setRecords(scopeName).filter { !day(it.date).isBefore(startDay) && it.weightLbs > 0 }
            val toUnit: (Double) -> Double = { WeightUnit.LBS.convert(it, unit) }
            val scopeText = scopeName?.let { "$it volume" } ?: "Total working-set volume"
            val progress = GoalProgress(summary = "${GoalFormat.weight(target, unit)} ${if (perWorkout) "per workout" else "per week"}",
                action = GoalAction.LogWorkout)
            progress.notes.add("$scopeText: weight × reps of completed working sets. Warm-up and cool-down sets don't count, and weeks without workouts show as no data.")

            data class Session(val id: String, val date: Long, val volume: Double)
            // Each session counted once.
            val sessions = sets.groupBy { it.sessionId }.map { (id, group) ->
                Session(id, group.minOf { it.date }, toUnit(group.sumOf { it.weightLbs * it.reps }))
            }.sortedBy { it.date }
            progress.recent = sessions.reversed().take(10).map {
                GoalRecentItem(it.id, day(it.date), GoalFormat.weight(it.volume, unit), "Workout volume")
            }
            progress.stats = mutableListOf(GoalStat("Target", GoalFormat.weight(target, unit)),
                GoalStat("Period", if (perWorkout) "Per workout" else "Per week"))

            if (perWorkout) {
                val last = sessions.lastOrNull()
                if (last == null) {
                    progress.hasData = false
                    progress.detail = "Log a workout to track volume"
                    return progress
                }
                val best = sessions.maxOf { it.volume }
                progress.targetReached = best >= target
                progress.fraction = if (target > 0) min(1.0, last.volume / target) else null
                progress.summary = "Last workout: ${GoalFormat.number(last.volume, 0)} of ${GoalFormat.weight(target, unit)}"
                progress.detail = if (last.volume >= target) "Target met last workout" else "${GoalFormat.weight(target - last.volume, unit)} to go"
                progress.stats.add(0, GoalStat("Last workout", GoalFormat.weight(last.volume, unit)))
                progress.stats.add(1, GoalStat("Best workout", GoalFormat.weight(best, unit)))
                if (sessions.size >= 2) {
                    progress.chart = GoalChart(sessions.map { GoalChartPoint(day(it.date), it.volume) }, target, GoalFormat.label(unit),
                        GoalChart.Style.BAR, "Volume per workout")
                }
                return progress
            }

            val byWeek = sessions.groupBy { GoalDay.weekStart(day(it.date)) }
            val thisWeek = GoalDay.weekStart(today)
            val weekVolume = byWeek[thisWeek]?.sumOf { it.volume }
            val weeksWithData = byWeek.keys.sorted()
            if (weekVolume != null) {
                progress.fraction = if (target > 0) min(1.0, weekVolume / target) else null
                progress.summary = "${GoalFormat.number(weekVolume, 0)} of ${GoalFormat.weight(target, unit)} this week"
                progress.detail = if (weekVolume >= target) "Weekly target met" else "${GoalFormat.weight(target - weekVolume, unit)} to go this week"
                progress.stats.add(0, GoalStat("This week", GoalFormat.weight(weekVolume, unit)))
            } else {
                progress.hasData = false
                progress.detail = "No workouts logged this week yet"
                progress.stats.add(0, GoalStat("This week", "—"))
            }
            progress.targetReached = byWeek.values.any { w -> w.sumOf { it.volume } >= target } && (weekVolume?.let { it >= target } ?: false)
            if (weeksWithData.size >= 2) {
                progress.chart = GoalChart(weeksWithData.map { w -> GoalChartPoint(w, byWeek.getValue(w).sumOf { it.volume }) }, target,
                    GoalFormat.label(unit), GoalChart.Style.BAR, "Weekly volume")
            }
            return progress
        }

        // MARK: Sleep & Recover

        data class Night(val minutes: Double, val source: String, val entry: GoalEntry?)

        /** Minutes asleep per night (keyed by the morning the night ended); Health first, manual logs filling the gaps. */
        fun sleepNights(): Map<LocalDate, Night> {
            val nights = HashMap<LocalDate, Night>()
            for (entry in entries()) {
                val value = entry.value ?: continue
                if (nights[entry.day] == null) nights[entry.day] = Night(value, "Manual entry", entry)
            }
            if (goal.trackingSource == GoalTrackingSource.AppleHealth && data.healthConnected) {
                for ((d, seconds) in data.sleepByNight(startDay.minusDays(1), today)) {
                    if (seconds > 0 && !d.isBefore(startDay)) nights[d] = Night(seconds / 60, "Health Connect", null)
                }
            }
            return nights
        }

        fun sleep(): GoalProgress {
            val target = goal.targetValue ?: 420.0
            val nights = sleepNights()
            val sorted = nights.keys.sorted()
            val progress = GoalProgress(summary = "${GoalFormat.sleep(target)} per night", action = GoalAction.LogSleep)
            progress.recent = sorted.reversed().take(14).map { d ->
                val night = nights.getValue(d)
                GoalRecentItem(GoalDay.key(d), d, GoalFormat.sleep(night.minutes),
                    if (night.minutes >= target) "${night.source} · Target met" else night.source, night.entry)
            }
            if (goal.trackingSource == GoalTrackingSource.AppleHealth) {
                progress.notes.add(if (data.healthConnected)
                    "Sleep from Health Connect; nights it doesn't cover can be logged manually."
                else "Health Connect isn't connected. Connect it in Connected Apps, or log sleep manually.")
                if (!data.healthConnected) progress.action = GoalAction.ConnectHealth
            }
            progress.notes.add("Nights without data are shown as missing, not as zero sleep.")

            val weeks = dayWeeks { d -> nights[d]?.let { it.minutes >= target } }
            progress.weeks = weeks
            if (sorted.size >= 2) {
                progress.chart = GoalChart(sorted.map { GoalChartPoint(it, nights.getValue(it).minutes / 60) }, target / 60, "h",
                    GoalChart.Style.BAR, "Sleep per night")
            }
            val recentWeek = sorted.filter { !it.isBefore(today.minusDays(6)) }
            if (recentWeek.size >= 3) {
                val average = recentWeek.sumOf { nights.getValue(it).minutes } / recentWeek.size
                progress.stats.add(GoalStat("7-night average", GoalFormat.sleep(average)))
            }

            if (goal.subtype == GoalSubtype.SleepConsistency) {
                val (summary, detail, fraction) = frequencySummary(weeks.lastOrNull(), "nights")
                progress.summary = summary.replace("nights this week", "target nights this week")
                progress.detail = detail ?: "${GoalFormat.sleep(target)}+ counts as a target night"
                progress.fraction = fraction
                progress.hasData = (weeks.lastOrNull()?.daysWithData ?: 0) > 0
                progress.stats.add(0, GoalStat("Target", "${GoalFormat.sleep(target)} · ${weeklyRequired()} nights/wk"))
                sorted.lastOrNull()?.let { progress.latestEntryText = "Last night logged ${GoalFormat.shortDate(it)} · ${nights.getValue(it).source}" }
                return progress
            }

            progress.stats.add(0, GoalStat("Target", GoalFormat.sleep(target)))
            val last = sorted.lastOrNull()
            val night = last?.let { nights[it] }
            if (last == null || night == null) {
                progress.hasData = false
                progress.detail = "Log your sleep to start tracking"
                return progress
            }
            progress.summary = "${GoalFormat.sleep(night.minutes)} of ${GoalFormat.sleep(target)} target"
            progress.fraction = min(1.0, night.minutes / target)
            val isLastNight = last == today
            progress.detail = (if (isLastNight) "Last night" else "Night ending ${GoalFormat.shortDate(last)}") +
                (if (night.minutes >= target) " · target met" else " · ${GoalFormat.sleep(target - night.minutes)} short")
            progress.stats.add(0, GoalStat("Most recent", GoalFormat.sleep(night.minutes)))
            progress.latestEntryText = "${GoalFormat.shortDate(last)} · ${night.source}"
            return progress
        }

        fun recoveryMetric(): GoalProgress {
            val metric = settings.recoveryMetric ?: RecoveryMetric.RESTING_HEART_RATE
            val target = goal.targetValue ?: 0.0
            val baseline = goal.baselineValue
            val progress = GoalProgress(summary = "${metric.displayName}: ${GoalFormat.number(target)} ${metric.unit} target")
            progress.notes.add("From Health Connect. Readings from different devices or methods may not be comparable, and no single reading says whether you're recovered.")
            if (!data.healthConnected) {
                progress.hasData = false
                progress.action = GoalAction.ConnectHealth
                progress.detail = "Connect Health Connect to track this goal"
                return progress
            }
            val daily = data.recoveryDailyAverages(metric, startDay.minusDays(1), today)
            val sorted = daily.keys.sorted()
            progress.recent = sorted.reversed().take(14).map {
                GoalRecentItem(GoalDay.key(it), it, "${GoalFormat.number(daily.getValue(it))} ${metric.unit}", "Health Connect")
            }
            baseline?.let { progress.stats.add(GoalStat("Baseline", "${GoalFormat.number(it)} ${metric.unit}")) }
            progress.stats.add(GoalStat("Target", "${GoalFormat.number(target)} ${metric.unit}"))
            if (sorted.size >= 2) {
                progress.chart = GoalChart(sorted.map { GoalChartPoint(it, daily.getValue(it)) }, target, metric.unit,
                    GoalChart.Style.LINE, "${metric.displayName} trend")
            }
            val recent = sorted.filter { !it.isBefore(today.minusDays(7)) }.map { daily.getValue(it) }
            if (recent.size < 3) {
                progress.hasData = false
                progress.detail = "Needs at least 3 readings in the last 7 days"
                return progress
            }
            val current = recent.sum() / recent.size
            progress.summary = "${GoalFormat.number(current)} ${metric.unit} · target ${GoalFormat.number(target)} ${metric.unit}"
            progress.stats.add(0, GoalStat("7-day average", "${GoalFormat.number(current)} ${metric.unit}"))
            if (baseline != null && baseline != target) {
                val movingDown = target < baseline
                progress.targetReached = if (movingDown) current <= target else current >= target
                progress.fraction = min(1.0, max(0.0, (baseline - current) / (baseline - target)))
                progress.detail = if (progress.targetReached) "At or past your target"
                else "${GoalFormat.number(abs(current - target))} ${metric.unit} from target"
            }
            progress.latestEntryText = sorted.lastOrNull()?.let { "Latest reading ${GoalFormat.shortDate(it)} · Health Connect" }
            return progress
        }

        // MARK: Eat Better

        data class CalorieBounds(val min: Double, val max: Double, val target: Double, val isRange: Boolean)

        fun calorieBounds(): CalorieBounds {
            val low = settings.calorieMin
            val high = settings.calorieMax
            if (low != null && high != null) return CalorieBounds(low, high, (low + high) / 2, true)
            // The goal reads the Nutrition target, so a later change there is reflected here.
            val live = data.nutritionTargets().calories.toDouble()
            val target = if (live > 0) live else (goal.targetValue ?: 2000.0)
            return CalorieBounds(target * 0.9, target * 1.1, target, false)
        }

        fun nutritionByDay(): Map<LocalDate, GoalNutritionDay> =
            data.nutritionDays(startDay).filter { it.calories > 0 || it.protein > 0 }.associateBy { it.day }

        fun calories(): GoalProgress {
            val bounds = calorieBounds()
            val byDay = nutritionByDay()
            val targetText = if (bounds.isRange)
                "${GoalFormat.number(bounds.min, 0)}–${GoalFormat.number(bounds.max, 0)} cal"
            else "${GoalFormat.number(bounds.target, 0)} cal"
            val progress = GoalProgress(summary = "$targetText per day", action = GoalAction.LogFood)
            progress.notes.add(if (bounds.isRange) "A day counts when logged calories fall in your range."
            else "A day counts when logged calories are within 10% of your target. Days without food logs show as not logged, not as missed.")
            progress.weeks = dayWeeks { d -> byDay[d]?.let { it.calories >= bounds.min && it.calories <= bounds.max } }
            progress.stats.add(GoalStat("Daily target", targetText))
            val logged = byDay.keys.sorted()
            if (logged.size >= 3) {
                val average = logged.sumOf { byDay.getValue(it).calories } / logged.size
                progress.stats.add(GoalStat("Average logged", "${GoalFormat.number(average, 0)} cal"))
            }
            progress.weeks.lastOrNull()?.let { progress.stats.add(GoalStat("Days on target", "${it.completed} of ${it.required} this week")) }
            progress.recent = logged.reversed().take(10).map { d ->
                val cal = byDay.getValue(d).calories
                GoalRecentItem(GoalDay.key(d), d, "${GoalFormat.number(cal, 0)} cal",
                    if (cal >= bounds.min && cal <= bounds.max) "On target" else "Outside target")
            }
            if (logged.size >= 2) {
                progress.chart = GoalChart(logged.map { GoalChartPoint(it, byDay.getValue(it).calories) }, bounds.target, "cal",
                    GoalChart.Style.BAR, "Logged calories")
            }
            val todayRow = byDay[today]
            if (todayRow == null) {
                progress.hasData = false
                progress.detail = "Nothing logged today"
                return progress
            }
            val eaten = todayRow.calories
            progress.summary = "${GoalFormat.number(eaten, 0)} of $targetText today"
            progress.fraction = min(1.0, eaten / bounds.max)
            progress.detail = when {
                eaten < bounds.min -> "${GoalFormat.number(bounds.target - eaten, 0)} cal remaining"
                eaten <= bounds.max -> "In your target range"
                else -> "${GoalFormat.number(eaten - bounds.max, 0)} cal over"
            }
            progress.latestEntryText = "Today · Nutrition logs"
            return progress
        }

        fun macroTargets(): List<GoalMacroTarget> {
            val live = data.nutritionTargets()
            return (settings.macros ?: emptyList()).map { macro ->
                val value = when (macro.key) {
                    MacroKey.PROTEIN -> live.protein
                    MacroKey.CARBS -> live.carbs
                    MacroKey.FAT -> live.fat
                }
                GoalMacroTarget(macro.key, if (value > 0) value.toDouble() else macro.grams)
            }
        }

        fun macroValue(key: MacroKey, row: GoalNutritionDay): Double = when (key) {
            MacroKey.PROTEIN -> row.protein
            MacroKey.CARBS -> row.carbs
            MacroKey.FAT -> row.fat
        }

        fun macroMet(macro: GoalMacroTarget, value: Double): Boolean =
            if (macro.key == MacroKey.PROTEIN) value >= macro.grams else abs(value - macro.grams) <= macro.grams * 0.1

        fun macros(): GoalProgress {
            val targets = macroTargets()
            val byDay = nutritionByDay()
            val first = targets.firstOrNull()
            val g0: (Double) -> String = { GoalFormat.number(it, 0) }
            val progress = GoalProgress(summary = targets.joinToString(" · ") { "${it.key.displayName} ${g0(it.grams)} g" }, action = GoalAction.LogFood)
            progress.notes = targets.map { "${it.key.displayName}: ${it.key.ruleText}" }.toMutableList()
            progress.weeks = dayWeeks { d -> byDay[d]?.let { row -> targets.all { macroMet(it, macroValue(it.key, row)) } } }
            val todayRow = byDay[today]
            progress.activities = targets.map { macro ->
                GoalActivityProgress(macro.key.rawValue, macro.key.displayName,
                    (todayRow?.let { macroValue(macro.key, it) } ?: 0.0).roundToInt(), macro.grams.roundToInt(), GoalTrackingSource.NutritionLogs)
            }
            for (macro in targets) {
                progress.stats.add(GoalStat(macro.key.displayName,
                    todayRow?.let { "${g0(macroValue(macro.key, it))} / ${g0(macro.grams)} g" } ?: "— / ${g0(macro.grams)} g"))
            }
            val logged = byDay.keys.sorted()
            progress.recent = logged.reversed().take(10).map { d ->
                val row = byDay.getValue(d)
                GoalRecentItem(GoalDay.key(d), d,
                    targets.joinToString(" · ") { "${it.key.displayName.take(1)} ${g0(macroValue(it.key, row))} g" },
                    if (targets.all { macroMet(it, macroValue(it.key, row)) }) "All targets met" else "Nutrition logs")
            }
            if (first != null && logged.size >= 2) {
                progress.chart = GoalChart(logged.map { GoalChartPoint(it, macroValue(first.key, byDay.getValue(it))) }, first.grams, "g",
                    GoalChart.Style.BAR, "${first.key.displayName} logged")
            }
            if (todayRow == null || first == null) {
                progress.hasData = false
                progress.detail = "Nothing logged today"
                return progress
            }
            val value = macroValue(first.key, todayRow)
            progress.summary = "${first.key.displayName}: ${g0(value)} of ${g0(first.grams)} g today"
            progress.fraction = min(1.0, value / max(1.0, first.grams))
            progress.detail = if (value >= first.grams) "Target reached today" else "${g0(first.grams - value)} g remaining"
            progress.latestEntryText = "Today · Nutrition logs"
            return progress
        }

        fun hydration(): GoalProgress {
            val hydrationGoal = data.hydrationGoal()
            val unit = hydrationGoal.displayUnit
            val target = hydrationGoal.unit.convert(hydrationGoal.dailyGoal, unit)
            val g0: (Double) -> String = { GoalFormat.number(it, 0) }
            val progress = GoalProgress(summary = "${g0(target)} ${unit.rawValue} per day", action = GoalAction.LogWater)
            progress.notes.add("Counts water logged in the water tracker. Other drinks don't count unless you log them as water.")
            val perDay = HashMap<LocalDate, Double>()
            progress.weeks = dayWeeks { d ->
                val total = data.waterTotal(d, unit)
                perDay[d] = total
                if (total > 0) total >= target else null
            }
            val todayTotal = data.waterTotal(today, unit)
            val pct = if (target > 0) todayTotal / target else 0.0
            progress.stats = mutableListOf(
                GoalStat("Today", "${g0(todayTotal)} ${unit.rawValue}"),
                GoalStat("Daily target", "${g0(target)} ${unit.rawValue}"),
                GoalStat("Completed", "${(min(pct, 9.99) * 100).roundToInt()}%")
            )
            progress.weeks.lastOrNull()?.let { progress.stats.add(GoalStat("Days on target", "${it.completed} of ${it.required} this week")) }
            val logged = perDay.filter { it.value > 0 }.keys.sorted()
            progress.recent = logged.reversed().take(10).map {
                val v = perDay.getValue(it)
                GoalRecentItem(GoalDay.key(it), it, "${g0(v)} ${unit.rawValue}", if (v >= target) "Target met" else "Water tracker")
            }
            if (logged.size >= 2) {
                progress.chart = GoalChart(logged.map { GoalChartPoint(it, perDay.getValue(it)) }, target, unit.rawValue,
                    GoalChart.Style.BAR, "Water per day")
            }
            if (todayTotal <= 0) {
                progress.hasData = false
                progress.summary = "0 of ${g0(target)} ${unit.rawValue} logged today"
                progress.detail = "No water logged yet today"
                return progress
            }
            progress.summary = "${g0(todayTotal)} of ${g0(target)} ${unit.rawValue} today"
            progress.fraction = min(1.0, pct)
            progress.detail = if (todayTotal >= target) "Daily target reached" else "${g0(target - todayTotal)} ${unit.rawValue} remaining"
            progress.latestEntryText = "Today · Water tracker"
            return progress
        }

        fun nutritionHabit(): GoalProgress {
            val habit = NutritionHabit.habit(settings.habitKey) ?: return checkIns()
            val target = goal.targetValue ?: habit.defaultTarget ?: 1.0
            val manual = entries()
            val manualByDay = manual.groupBy { it.day }

            /** Meals (distinct meal types) logged that day, optionally with ≥ 20 g protein. */
            fun mealCount(d: LocalDate, proteinOnly: Boolean): Int? {
                val foods = data.foodItems(d)
                if (foods.isEmpty()) return null
                return foods.groupBy { it.mealType }.values.count { meal -> !proteinOnly || meal.sumOf { it.protein } >= 20 }
            }
            val qualifies: (LocalDate) -> Boolean? = { d ->
                when (habit.id) {
                    "log-meals" -> mealCount(d, false)?.let { it >= target }
                    "protein-source" -> mealCount(d, true)?.let { it >= target }
                    "fruits-veg" -> manualByDay[d]?.let { list -> list.sumOf { it.value ?: 0.0 } >= target }
                    else -> if (manualByDay[d] == null) null else true
                }
            }
            val progress = GoalProgress(summary = habit.name)
            progress.notes.add(habit.rule)
            if (habit.id == "log-meals") progress.notes.add("This tracks logging consistency only — it doesn't measure whether your diet is balanced.")
            progress.weeks = dayWeeks(qualifies)
            val (summary, detail, fraction) = frequencySummary(progress.weeks.lastOrNull(), "days")
            progress.summary = summary
            progress.detail = detail
            progress.fraction = fraction
            progress.hasData = (progress.weeks.lastOrNull()?.daysWithData ?: 0) > 0
            progress.action = when (habit.tracking) {
                GoalTrackingSource.NutritionLogs -> GoalAction.LogFood
                else -> if (habit.defaultTarget == null) GoalAction.CheckIn(null) else GoalAction.LogAmount(habit.unit ?: "")
            }
            if (habit.tracking == GoalTrackingSource.Manual) {
                progress.recent = recentItems(manual, habit.unit) { habit.name }
            }
            if (habit.unit != null && habit.defaultTarget != null) {
                progress.stats.add(GoalStat("Daily target", "${GoalFormat.number(target)} ${habit.unit}"))
            }
            progress.stats.add(GoalStat("Days per week", "${weeklyRequired()}"))
            return progress
        }

        // MARK: Build a Routine

        fun completedSessions(since: LocalDate): List<GoalSessionRecord> {
            val programId = if (goal.trackingSource == GoalTrackingSource.Program) settings.programId?.lowercase(Locale.US) else null
            val seen = HashSet<String>()
            return data.completedSessions(since)
                .filter { programId == null || it.programId?.lowercase(Locale.US) == programId }
                .filter { seen.add(it.id.lowercase(Locale.US)) }
        }

        fun workouts(): GoalProgress {
            val sessions = completedSessions(GoalDay.weekStart(startDay)).filter { !day(it.date).isBefore(startDay) }
            val byWeek = sessions.groupBy { GoalDay.weekStart(day(it.date)) }
            val thisWeek = GoalDay.weekStart(today)
            val required = weeklyRequired()
            val progress = GoalProgress(summary = "$required workouts per week", action = GoalAction.LogWorkout)
            progress.weeks = weekStarts().map { start ->
                val count = byWeek[start]?.size ?: 0
                GoalWeek(start, count, required, start == thisWeek, count)
            }
            val (summary, detail, fraction) = frequencySummary(progress.weeks.lastOrNull(), "workouts")
            progress.summary = summary
            progress.detail = detail
            progress.fraction = fraction
            progress.notes.add(if (goal.trackingSource == GoalTrackingSource.Program)
                "Completed workouts from ${settings.programName ?: "your program"} count, once each."
            else "Each completed workout counts once.")
            progress.recent = sessions.sortedByDescending { it.date }.take(10).map {
                GoalRecentItem(it.id, day(it.date), it.workoutName, "Completed workout")
            }
            val completedWeeks = progress.weeks.filter { !it.inProgress }
            if (completedWeeks.isNotEmpty()) {
                progress.stats.add(GoalStat("Weeks met", "${completedWeeks.count { it.met }} of ${completedWeeks.size}"))
            }
            progress.stats.add(0, GoalStat("Weekly target", "$required workouts"))
            progress.recent.firstOrNull()?.let { progress.latestEntryText = "Last workout ${GoalFormat.shortDate(it.date)}" }
            return progress
        }

        /** Movement / wellness / recovery routines and daily habits: distinct days with a check-in, against the weekly target. */
        fun checkIns(): GoalProgress {
            val manual = entries()
            val days = manual.map { it.day }.toSet()
            val name = GoalTemplate.template(settings.templateId)?.name ?: goal.title
            val progress = GoalProgress(summary = name, action = GoalAction.CheckIn(null))
            progress.weeks = dayWeeks { d -> if (d in days) true else null }
            val noun = if (goal.subtype == GoalSubtype.DailyHabit) "days" else "sessions"
            val (summary, detail, fraction) = frequencySummary(progress.weeks.lastOrNull(), noun)
            progress.summary = summary
            progress.detail = detail
            progress.fraction = fraction
            progress.hasData = (progress.weeks.lastOrNull()?.daysWithData ?: 0) > 0
            settings.completionRule?.let { progress.notes.add(it) }
            settings.sessionMinutes?.let { progress.stats.add(GoalStat("Session", "$it min")) }
            progress.stats.add(0, GoalStat("Target", if (goal.frequency == GoalFrequency.Daily) "Every day" else "${weeklyRequired()} per week"))
            progress.stats.add(GoalStat("Today", if (today in days) "Completed" else "Not yet"))
            progress.recent = recentItems(manual, "min") { "Completed" }
            manual.firstOrNull()?.let { progress.latestEntryText = "Last check-in ${GoalFormat.shortDate(it.day)}" }
            return progress
        }

        fun customRoutine(): GoalProgress {
            val activities = settings.activities ?: emptyList()
            val thisWeek = GoalDay.weekStart(today)
            val manual = entries()
            var workoutsLeft = completedSessions(thisWeek).count { !day(it.date).isBefore(startDay) }
            val rows = mutableListOf<GoalActivityProgress>()
            for (activity in activities) {
                val done: Int
                if (activity.kind == GoalActivity.Kind.WORKOUT) {
                    // Each logged workout is assigned to one workout activity only.
                    done = min(activity.perWeek, workoutsLeft)
                    workoutsLeft -= done
                } else {
                    done = manual.filter { it.activityKey == activity.id && !it.day.isBefore(thisWeek) }.map { it.day }.toSet().size
                }
                rows.add(GoalActivityProgress(activity.id, activity.name, done, activity.perWeek, activity.kind.tracking))
            }
            val required = rows.sumOf { it.required }
            val completed = rows.sumOf { min(it.completed, it.required) }
            val progress = GoalProgress(
                summary = "$completed of $required activities this week",
                detail = if (required > completed) "${required - completed} remaining this week" else "Weekly target met",
                fraction = if (required > 0) completed.toDouble() / required else null,
                action = GoalAction.CheckIn(activities.firstOrNull { it.kind != GoalActivity.Kind.WORKOUT }?.id)
            )
            progress.activities = rows
            progress.hasData = completed > 0
            progress.notes.add("Workout activities count completed workouts (each once); other activities count check-ins, one per day.")
            progress.recent = recentItems(manual, null) { entry -> activities.firstOrNull { it.id == entry.activityKey }?.name ?: "Check-in" }
            return progress
        }

        // MARK: Custom

        fun custom(): GoalProgress {
            val metric = settings.customMetric ?: CustomGoalMetric.MANUAL
            val unit = settings.unit ?: metric.defaultUnit
            val target = goal.targetValue ?: 1.0
            val actionName = settings.actionName ?: goal.title
            if (metric == CustomGoalMetric.STEPS) return customSteps(target)
            val isCheckIn = metric == CustomGoalMetric.MANUAL || metric == CustomGoalMetric.FREQUENCY
            val manual = entries()
            val byDay = manual.groupBy { it.day }
            val sum: (List<GoalEntry>) -> Double = { list -> list.sumOf { it.value ?: 1.0 } }
            val progress = GoalProgress(summary = goal.title,
                action = if (isCheckIn) GoalAction.CheckIn(null) else GoalAction.LogAmount(unit))
            progress.recent = recentItems(manual, if (isCheckIn) null else unit) { actionName }
            manual.firstOrNull()?.let { progress.latestEntryText = "Last entry ${GoalFormat.shortDate(it.day)}" }
            val amountText: (Double) -> String = { "${GoalFormat.number(it)} $unit".trimEnd() }
            val frequency = goal.frequency ?: GoalFrequency.Daily

            when {
                metric == CustomGoalMetric.MANUAL && frequency == GoalFrequency.Overall -> {
                    progress.targetReached = manual.isNotEmpty()
                    progress.summary = if (progress.targetReached) "Completed" else "Not yet completed"
                    progress.hasData = manual.isNotEmpty()
                }
                isCheckIn -> {
                    progress.weeks = dayWeeks { d -> if (byDay[d] == null) null else true }
                    val (summary, detail, fraction) = frequencySummary(progress.weeks.lastOrNull(), "days")
                    progress.summary = summary
                    progress.detail = detail
                    progress.fraction = fraction
                    progress.hasData = (progress.weeks.lastOrNull()?.daysWithData ?: 0) > 0
                }
                frequency == GoalFrequency.Overall -> {
                    val total = sum(manual)
                    progress.summary = "${GoalFormat.number(total)} of ${amountText(target)}"
                    progress.fraction = if (target > 0) min(1.0, total / target) else null
                    progress.targetReached = total >= target
                    progress.detail = if (progress.targetReached) "Target reached" else "${amountText(target - total)} remaining"
                    progress.hasData = manual.isNotEmpty()
                    progress.stats = mutableListOf(GoalStat("Total", amountText(total)), GoalStat("Target", amountText(target)))
                }
                frequency == GoalFrequency.Weekly || frequency == GoalFrequency.PerWorkout -> {
                    val thisWeek = GoalDay.weekStart(today)
                    val byWeek = manual.groupBy { GoalDay.weekStart(it.day) }
                    val weekTotal = byWeek[thisWeek]?.let(sum)
                    progress.weeks = weekStarts().map { start ->
                        val total = byWeek[start]?.let(sum) ?: 0.0
                        GoalWeek(start, total.roundToInt(), target.roundToInt(), start == thisWeek, byWeek[start]?.size ?: 0)
                    }
                    progress.summary = "${GoalFormat.number(weekTotal ?: 0.0)} of ${amountText(target)} this week"
                    progress.fraction = weekTotal?.let { min(1.0, it / target) }
                    progress.hasData = weekTotal != null
                    progress.detail = if ((weekTotal ?: 0.0) >= target) "Weekly target met" else "${amountText(target - (weekTotal ?: 0.0))} remaining this week"
                }
                else -> { // Daily
                    progress.weeks = dayWeeks { d -> byDay[d]?.let { sum(it) >= target } }
                    val todayTotal = byDay[today]?.let(sum)
                    progress.summary = "${GoalFormat.number(todayTotal ?: 0.0)} of ${amountText(target)} today"
                    progress.fraction = todayTotal?.let { min(1.0, it / target) }
                    progress.hasData = todayTotal != null
                    progress.detail = if ((todayTotal ?: 0.0) >= target) "Today's target met" else "${amountText(target - (todayTotal ?: 0.0))} remaining today"
                }
            }
            if (isCheckIn) progress.notes.add("Counts check-ins: “$actionName”. One per day.")
            val points = byDay.keys.sorted().map { GoalChartPoint(it, sum(byDay.getValue(it))) }
            if (!isCheckIn && points.size >= 2) {
                progress.chart = GoalChart(points, if (goal.frequency == GoalFrequency.Daily) target else null, unit,
                    GoalChart.Style.BAR, "Logged $unit")
            }
            return progress
        }

        fun customSteps(target: Double): GoalProgress {
            val g0: (Double) -> String = { GoalFormat.number(it, 0) }
            val progress = GoalProgress(summary = "${g0(target)} steps per day")
            if (!data.healthConnected) {
                progress.hasData = false
                progress.action = GoalAction.ConnectHealth
                progress.detail = "Connect Health Connect to track steps"
                return progress
            }
            val byDay = data.dailySteps(startDay, today)
            progress.weeks = dayWeeks { d -> byDay[d]?.let { it >= target } }
            progress.notes.add("Steps from Health Connect. Days without step data show as missing.")
            val sorted = byDay.keys.sorted()
            progress.recent = sorted.reversed().take(10).map {
                GoalRecentItem(GoalDay.key(it), it, "${g0(byDay.getValue(it))} steps", "Health Connect")
            }
            if (sorted.size >= 2) {
                progress.chart = GoalChart(sorted.map { GoalChartPoint(it, byDay.getValue(it)) }, target, "steps",
                    GoalChart.Style.BAR, "Steps per day")
            }
            val todaySteps = byDay[today]
            if (todaySteps == null) {
                progress.hasData = false
                progress.detail = "No steps recorded yet today"
                return progress
            }
            progress.summary = "${g0(todaySteps)} of ${g0(target)} steps today"
            progress.fraction = min(1.0, todaySteps / target)
            progress.detail = if (todaySteps >= target) "Today's target met" else "${g0(target - todaySteps)} steps to go"
            return progress
        }
    }
}
