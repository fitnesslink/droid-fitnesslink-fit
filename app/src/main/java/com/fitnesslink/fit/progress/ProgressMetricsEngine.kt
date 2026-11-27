package com.fitnesslink.fit.progress

import com.fitnesslink.fit.health.HealthConnectService
import com.fitnesslink.fit.model.BodyPart
import com.fitnesslink.fit.model.DailyNutritionRow
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.MeasurementUnit
import com.fitnesslink.fit.model.MetricAvailability
import com.fitnesslink.fit.model.MetricChange
import com.fitnesslink.fit.model.MetricResult
import com.fitnesslink.fit.model.MetricSource
import com.fitnesslink.fit.model.ProgressMetric
import com.fitnesslink.fit.model.ProgressReport
import com.fitnesslink.fit.model.ReportTimeRange
import com.fitnesslink.fit.model.SessionRow
import com.fitnesslink.fit.model.WeightEntry
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.fullSessionRows
import com.fitnesslink.fit.persistence.goalsCached
import com.fitnesslink.fit.persistence.measurementEntriesCached
import com.fitnesslink.fit.persistence.weightEntriesCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Everything the engine reads. [ProgressMetricsEngine.compute] is pure over
 * this interface so the rules are unit-testable; [LocalProgressDataSource]
 * is the production implementation over local SQLite.
 */
interface ProgressDataSource {
    /** Session rows (completed and not) starting at or after [since] epoch ms. */
    fun sessionRows(since: Long): List<SessionRow>
    /** Movements with a weighted top set logged since [since] (Workout Report's PR count). */
    fun personalRecordCount(since: Long): Int
    /** Start dates of every completed workout. */
    fun workoutDates(): List<Date>
    /** Per-day nutrition rollups since [since]. */
    fun dailyNutritionRows(since: Long): List<DailyNutritionRow>
    fun weightEntries(): List<WeightEntry>
    fun measurementEntries(): List<MeasurementEntry>
    fun goals(): List<Goal>
    /** The user connected Health Connect and at least one read is still granted. */
    val isHealthConnected: Boolean
    /**
     * Health Connect per-day values for [metric] in `[from, to)` epoch ms,
     * keyed by local start-of-day ms. Sleep is seconds asleep keyed by the
     * morning the night ended. Days without data are absent, never zero.
     */
    fun healthDaily(metric: ProgressMetric, from: Long, to: Long): Map<Long, Double> = emptyMap()
    /** Latest body-fat reading at or before [before]: (percent 0–100, epoch ms). */
    fun latestBodyFat(before: Long): Pair<Double, Long>? = null
}

/** Production source: the same local tables the Workout / Nutrition reports read. */
object LocalProgressDataSource : ProgressDataSource {
    private val userId: String get() = DatabaseManager.user()?.id ?: "user1"

    override fun sessionRows(since: Long): List<SessionRow> =
        runCatching { DatabaseManager.fullSessionRows(userId, since) }.getOrDefault(emptyList())

    override fun personalRecordCount(since: Long): Int =
        runCatching { DatabaseManager.personalRecords(userId, since).size }.getOrDefault(0)

    override fun workoutDates(): List<Date> =
        runCatching { DatabaseManager.workoutDates(userId, 0L) }.getOrDefault(emptyList())

    override fun dailyNutritionRows(since: Long): List<DailyNutritionRow> =
        runCatching { DatabaseManager.dailyNutritionRows(since) }.getOrDefault(emptyList())

    override fun weightEntries(): List<WeightEntry> =
        runCatching { DatabaseManager.weightEntriesCached() }.getOrDefault(emptyList())

    override fun measurementEntries(): List<MeasurementEntry> =
        runCatching { DatabaseManager.measurementEntriesCached() }.getOrDefault(emptyList())

    override fun goals(): List<Goal> =
        runCatching { DatabaseManager.goalsCached() }.getOrDefault(emptyList())

    override val isHealthConnected: Boolean get() = HealthConnectService.isConnected

    // The engine is synchronous and runs on Dispatchers.IO, so blocking on the suspend reads is fine here.
    override fun healthDaily(metric: ProgressMetric, from: Long, to: Long): Map<Long, Double> {
        val kind = when (metric) {
            ProgressMetric.AVERAGE_STEPS, ProgressMetric.TOTAL_STEPS -> HealthConnectService.Daily.STEPS
            ProgressMetric.ACTIVE_CALORIES -> HealthConnectService.Daily.ACTIVE_CALORIES
            ProgressMetric.ACTIVE_MINUTES -> HealthConnectService.Daily.ACTIVE_MINUTES
            ProgressMetric.RESTING_HEART_RATE -> HealthConnectService.Daily.RESTING_HEART_RATE
            ProgressMetric.HRV -> HealthConnectService.Daily.HRV
            ProgressMetric.AVERAGE_SLEEP -> HealthConnectService.Daily.SLEEP_SECONDS
            else -> return emptyMap()
        }
        val zone = ZoneId.systemDefault()
        val start = HealthConnectService.clampStart(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to))
        return runBlocking { HealthConnectService.daily(kind, start, Instant.ofEpochMilli(to), zone) }
            .mapKeys { it.key.atStartOfDay(zone).toInstant().toEpochMilli() }
    }

    override fun latestBodyFat(before: Long): Pair<Double, Long>? =
        runBlocking { HealthConnectService.latestBodyFat(Instant.ofEpochMilli(before)) }?.let { it.first to it.second.toEpochMilli() }
}

/** One insight card (Progress PRD §4). */
data class ProgressInsight(
    val headline: String,
    val detail: String,
    /** iOS SF Symbol name (mapped to Material in the UI). */
    val icon: String,
    /** Where tapping the card leads; null for the empty state. */
    val report: ProgressReport?,
    /** Direction for the trend glyph; null = neutral / no trend. */
    val isUp: Boolean?
) {
    companion object {
        val empty = ProgressInsight(
            headline = "Your progress starts here",
            detail = "Complete more workouts to start seeing your progress trends.",
            icon = "chart.bar.fill",
            report = null,
            isUp = null
        )
    }
}

/**
 * Port of iOS `ProgressMetricsEngine`: calculates [ProgressMetric]s for a
 * period and its previous equivalent period (Progress PRD §10). One engine
 * feeds the landing page, All Stats, Customize and the category reports so
 * every screen agrees. Only the sources the requested metrics need are read.
 */
object ProgressMetricsEngine {

    /** Reads local data off the main thread. */
    suspend fun load(
        metrics: Set<ProgressMetric>,
        range: ReportTimeRange,
        nowMs: Long = System.currentTimeMillis(),
        source: ProgressDataSource = LocalProgressDataSource
    ): Map<ProgressMetric, MetricResult> = withContext(Dispatchers.IO) {
        // Pick up grants changed in Health Connect's settings since the last check.
        if (source === LocalProgressDataSource && metrics.any { it.source == MetricSource.HEALTH }) HealthConnectService.refresh()
        compute(metrics, range, nowMs, source)
    }

    val isHealthConnected: Boolean get() = LocalProgressDataSource.isHealthConnected

    fun compute(
        metrics: Set<ProgressMetric>,
        range: ReportTimeRange,
        nowMs: Long,
        source: ProgressDataSource,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Map<ProgressMetric, MetricResult> {
        val window = Window(range, nowMs, timeZone)
        val sources = metrics.map { it.source }.toSet()
        val out = mutableMapOf<ProgressMetric, MetricResult>()
        if (MetricSource.WORKOUTS in sources) out += workoutResults(metrics, window, source)
        if (MetricSource.NUTRITION in sources) out += nutritionResults(metrics, window, source)
        if (MetricSource.BODY_LOG in sources) out += bodyLogResults(metrics, window, source)
        if (MetricSource.GOALS in sources) out += goalResults(metrics, window, source)
        if (MetricSource.HEALTH in sources) out += healthResults(metrics, window, source)
        return out.filterKeys { it in metrics }
    }

    // MARK: - Period window

    class Window(val range: ReportTimeRange, val nowMs: Long, val timeZone: TimeZone = TimeZone.getDefault()) {
        /** Start of the current period; null = All Time. */
        val start: Long? = range.startMillis(nowMs)
        /** The previous equivalent period `[start, end)`; null = All Time. */
        val previous: LongRange? = range.previousInterval(nowMs)

        /** Earliest date any query needs (covers the previous period). */
        val fetchStart: Long get() = previous?.first ?: start ?: 0L

        fun isCurrent(ms: Long): Boolean = ms <= nowMs && (start?.let { ms >= it } ?: true)

        fun isPrevious(ms: Long): Boolean = previous?.let { ms >= it.first && ms <= it.last } ?: false

        private fun calendar(ms: Long): Calendar = Calendar.getInstance(timeZone).apply { timeInMillis = ms }

        fun startOfDay(ms: Long): Long = calendar(ms).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        /** Bucket key for trend points: day, or week beyond 30 days. */
        fun bucket(ms: Long): Long {
            val day = startOfDay(ms)
            if (!range.groupsByWeek) return day
            return calendar(day).apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }.let {
                // set(DAY_OF_WEEK) may land in the following week on some calendars.
                if (it.timeInMillis > day) it.add(Calendar.WEEK_OF_YEAR, -1)
                it.timeInMillis
            }
        }

        /** Every bucket in the current period, oldest first — where an empty bucket genuinely means "none". */
        fun allBuckets(firstRecord: Long?): List<Long> {
            val first = start ?: firstRecord ?: return emptyList()
            val buckets = mutableListOf<Long>()
            val cursor = calendar(bucket(first))
            val step = if (range.groupsByWeek) Calendar.WEEK_OF_YEAR else Calendar.DAY_OF_YEAR
            while (cursor.timeInMillis <= nowMs && buckets.size < 400) {
                buckets += cursor.timeInMillis
                cursor.add(step, 1)
            }
            return buckets
        }
    }

    // MARK: - Workouts (mirrors DatabaseManager.workoutReportData)

    private fun workoutResults(metrics: Set<ProgressMetric>, window: Window, source: ProgressDataSource): Map<ProgressMetric, MetricResult> {
        val out = mutableMapOf<ProgressMetric, MetricResult>()
        val needsRows = metrics.any {
            it.source == MetricSource.WORKOUTS && it != ProgressMetric.PERSONAL_RECORDS &&
                it != ProgressMetric.WORKOUT_STREAK && it != ProgressMetric.LONGEST_STREAK
        }
        val rows = if (needsRows) source.sessionRows(window.fetchStart) else emptyList()
        val current = rows.filter { window.isCurrent(it.date.time) }
        val previous = rows.filter { window.isPrevious(it.date.time) }
        val hasPrevious = window.previous != null
        val buckets = window.allBuckets(rows.minOfOrNull { it.date.time })

        /** Sum per bucket, empty buckets = 0 (no workout that day is a real zero). */
        fun countSeries(value: (List<SessionRow>) -> Double): List<Double> {
            val grouped = current.groupBy { window.bucket(it.date.time) }
            return buckets.map { value(grouped[it] ?: emptyList()) }
        }

        fun set(metric: ProgressMetric, make: () -> MetricResult) {
            if (metric in metrics) out[metric] = make()
        }

        fun completed(list: List<SessionRow>) = list.filter { it.isCompleted }
        val available = MetricAvailability.AVAILABLE

        set(ProgressMetric.TOTAL_WORKOUTS) {
            MetricResult(
                ProgressMetric.TOTAL_WORKOUTS,
                current = completed(current).size.toDouble(),
                previous = if (hasPrevious) completed(previous).size.toDouble() else null,
                series = countSeries { completed(it).size.toDouble() },
                availability = available
            )
        }
        set(ProgressMetric.INCOMPLETE_WORKOUTS) {
            MetricResult(
                ProgressMetric.INCOMPLETE_WORKOUTS,
                current = current.count { !it.isCompleted }.toDouble(),
                previous = if (hasPrevious) previous.count { !it.isCompleted }.toDouble() else null,
                availability = available
            )
        }
        set(ProgressMetric.TOTAL_TIME) {
            val total: (List<SessionRow>) -> Double = { list -> list.sumOf { it.durationSeconds }.toDouble() }
            MetricResult(
                ProgressMetric.TOTAL_TIME, total(current), if (hasPrevious) total(previous) else null,
                countSeries(total), available
            )
        }
        set(ProgressMetric.TRAINING_VOLUME) {
            // `totalWeightLifted` is stored working-set volume (FTNSSLNK-497), the figure the Workout Report sums.
            val total: (List<SessionRow>) -> Double = { list -> list.sumOf { it.totalWeightLifted } }
            MetricResult(
                ProgressMetric.TRAINING_VOLUME, total(current), if (hasPrevious) total(previous) else null,
                countSeries(total), available
            )
        }
        set(ProgressMetric.CALORIES_BURNED) {
            // Sessions backfilled from the server carry no calories, so a zero
            // total means "not recorded", not "burned nothing".
            val total: (List<SessionRow>) -> Double = { list -> list.sumOf { it.totalCaloriesBurned } }
            val value = total(current)
            if (value <= 0) return@set MetricResult.unavailable(ProgressMetric.CALORIES_BURNED)
            val prev = total(previous)
            MetricResult(
                ProgressMetric.CALORIES_BURNED, value, if (hasPrevious && prev > 0) prev else null,
                countSeries(total), available
            )
        }
        set(ProgressMetric.AVG_WORKOUT_DURATION) {
            fun avg(list: List<SessionRow>): Double? {
                val durations = completed(list).map { it.durationSeconds }.filter { it > 0 }
                return if (durations.isEmpty()) null else durations.sum().toDouble() / durations.size
            }
            val value = avg(current) ?: return@set MetricResult.unavailable(ProgressMetric.AVG_WORKOUT_DURATION)
            MetricResult(ProgressMetric.AVG_WORKOUT_DURATION, value, avg(previous), availability = available)
        }
        set(ProgressMetric.AVERAGE_RPE) {
            fun avg(list: List<SessionRow>): Double? {
                val rpes = completed(list).mapNotNull { it.rpeValue }
                return if (rpes.isEmpty()) null else rpes.average()
            }
            val value = avg(current) ?: return@set MetricResult.unavailable(ProgressMetric.AVERAGE_RPE)
            MetricResult(ProgressMetric.AVERAGE_RPE, value, avg(previous), availability = available)
        }
        set(ProgressMetric.TOTAL_SETS) {
            val total: (List<SessionRow>) -> Double = { list -> completed(list).sumOf { it.totalSets }.toDouble() }
            MetricResult(ProgressMetric.TOTAL_SETS, total(current), if (hasPrevious) total(previous) else null, countSeries(total), available)
        }
        set(ProgressMetric.TOTAL_REPS) {
            val total: (List<SessionRow>) -> Double = { list -> completed(list).sumOf { it.totalReps }.toDouble() }
            MetricResult(ProgressMetric.TOTAL_REPS, total(current), if (hasPrevious) total(previous) else null, countSeries(total), available)
        }
        set(ProgressMetric.PERSONAL_RECORDS) {
            val count = source.personalRecordCount(window.start ?: 0L)
            MetricResult(ProgressMetric.PERSONAL_RECORDS, count.toDouble(), availability = available)
        }
        if (ProgressMetric.WORKOUT_STREAK in metrics || ProgressMetric.LONGEST_STREAK in metrics) {
            val allDates = source.workoutDates()
            set(ProgressMetric.WORKOUT_STREAK) {
                MetricResult(
                    ProgressMetric.WORKOUT_STREAK,
                    streaks(allDates, window.nowMs, window.timeZone).first.toDouble(),
                    availability = available
                )
            }
            set(ProgressMetric.LONGEST_STREAK) {
                val inPeriod = allDates.filter { window.isCurrent(it.time) }
                MetricResult(
                    ProgressMetric.LONGEST_STREAK,
                    streaks(inPeriod, window.nowMs, window.timeZone).second.toDouble(),
                    availability = available
                )
            }
        }
        return out
    }

    /**
     * (current, longest) runs of consecutive workout days — current ends
     * today or yesterday; the Workout Report's streak rules. DST-safe: days
     * compare as local calendar dates, not 24 h steps.
     */
    fun streaks(dates: List<Date>, nowMs: Long, timeZone: TimeZone = TimeZone.getDefault()): Pair<Int, Int> {
        val zone = timeZone.toZoneId()
        fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()
        val days = dates.map { day(it.time) }.toSortedSet().toList()
        if (days.isEmpty()) return 0 to 0
        var longest = 1
        var run = 1
        for (i in 1 until days.size) {
            run = if (days[i] - days[i - 1] == 1L) run + 1 else 1
            longest = maxOf(longest, run)
        }
        val today = day(nowMs)
        val last = days.last()
        if (last != today && last != today - 1) return 0 to longest
        var current = 1
        for (i in days.size - 2 downTo 0) {
            if (days[i + 1] - days[i] != 1L) break
            current += 1
        }
        return current to longest
    }

    // MARK: - Nutrition (mirrors NutritionReportViewModel: averages over logged days)

    private fun nutritionResults(metrics: Set<ProgressMetric>, window: Window, source: ProgressDataSource): Map<ProgressMetric, MetricResult> {
        val rows = source.dailyNutritionRows(window.fetchStart)
        val current = rows.filter { window.isCurrent(it.date.time) }
        val previous = rows.filter { window.isPrevious(it.date.time) }

        fun average(list: List<DailyNutritionRow>, value: (DailyNutritionRow) -> Double): Double? =
            if (list.isEmpty()) null else list.sumOf(value) / list.size

        fun series(value: (DailyNutritionRow) -> Double): List<Double> {
            val grouped = current.groupBy { window.bucket(it.date.time) }
            return grouped.keys.sorted().mapNotNull { average(grouped[it] ?: emptyList(), value) }
        }

        val definitions: List<Pair<ProgressMetric, (DailyNutritionRow) -> Double>> = listOf(
            ProgressMetric.AVG_CALORIES_CONSUMED to { it.calories.toDouble() },
            ProgressMetric.AVG_PROTEIN to { it.protein },
            ProgressMetric.AVG_CARBS to { it.carbs },
            ProgressMetric.AVG_FAT to { it.fat },
            ProgressMetric.CALORIE_GOAL_ADHERENCE to { if (it.isOnTarget) 100.0 else 0.0 }
        )
        val out = mutableMapOf<ProgressMetric, MetricResult>()
        for ((metric, value) in definitions) {
            if (metric !in metrics) continue
            val currentValue = average(current, value)
            out[metric] = if (currentValue == null) MetricResult.unavailable(metric)
            else MetricResult(metric, currentValue, average(previous, value), series(value), MetricAvailability.AVAILABLE)
        }
        return out
    }

    // MARK: - Body log (weight + measurements entered in the app)

    private fun bodyLogResults(metrics: Set<ProgressMetric>, window: Window, source: ProgressDataSource): Map<ProgressMetric, MetricResult> {
        val out = mutableMapOf<ProgressMetric, MetricResult>()

        if (ProgressMetric.BODY_WEIGHT in metrics || ProgressMetric.WEIGHT_CHANGE in metrics) {
            val entries = source.weightEntries()
                .map { it.date.time to it.unit.convert(it.weight, WeightUnit.LBS) }
                .sortedBy { it.first }
            val current = entries.filter { window.isCurrent(it.first) }
            val previous = entries.filter { window.isPrevious(it.first) }

            if (ProgressMetric.BODY_WEIGHT in metrics) {
                val latest = current.lastOrNull()
                out[ProgressMetric.BODY_WEIGHT] = if (latest == null) MetricResult.unavailable(ProgressMetric.BODY_WEIGHT)
                else MetricResult(
                    ProgressMetric.BODY_WEIGHT, latest.second, previous.lastOrNull()?.second,
                    current.map { it.second }, MetricAvailability.AVAILABLE
                )
            }
            if (ProgressMetric.WEIGHT_CHANGE in metrics) {
                out[ProgressMetric.WEIGHT_CHANGE] = if (current.size >= 2) MetricResult(
                    ProgressMetric.WEIGHT_CHANGE, current.last().second - current.first().second,
                    series = current.map { it.second }, availability = MetricAvailability.AVAILABLE
                ) else MetricResult.unavailable(ProgressMetric.WEIGHT_CHANGE)
            }
        }

        val parts = listOf(ProgressMetric.WAIST to BodyPart.WAIST, ProgressMetric.HIPS to BodyPart.HIPS)
        if (parts.any { it.first in metrics }) {
            val entries = source.measurementEntries().sortedBy { it.date.time }
            for ((metric, part) in parts) {
                if (metric !in metrics) continue
                val values = entries.mapNotNull { entry ->
                    entry.measurement(part)?.let { entry.date.time to it.unit.convert(it.value, MeasurementUnit.INCHES) }
                }
                val current = values.filter { window.isCurrent(it.first) }
                val latest = current.lastOrNull()
                out[metric] = if (latest == null) MetricResult.unavailable(metric)
                else MetricResult(
                    metric, latest.second, values.filter { window.isPrevious(it.first) }.lastOrNull()?.second,
                    current.map { it.second }, MetricAvailability.AVAILABLE
                )
            }
        }
        return out
    }

    // MARK: - Goals

    private fun goalResults(metrics: Set<ProgressMetric>, window: Window, source: ProgressDataSource): Map<ProgressMetric, MetricResult> {
        val goals = source.goals()
        val out = mutableMapOf<ProgressMetric, MetricResult>()
        if (ProgressMetric.ACTIVE_GOALS in metrics) {
            out[ProgressMetric.ACTIVE_GOALS] = if (goals.isEmpty()) MetricResult.unavailable(ProgressMetric.ACTIVE_GOALS)
            else MetricResult(
                ProgressMetric.ACTIVE_GOALS, goals.count { it.status == GoalStatus.Active }.toDouble(),
                availability = MetricAvailability.AVAILABLE
            )
        }
        if (ProgressMetric.GOALS_COMPLETED in metrics) {
            out[ProgressMetric.GOALS_COMPLETED] = if (goals.isEmpty()) MetricResult.unavailable(ProgressMetric.GOALS_COMPLETED)
            else {
                val completedDates = goals.mapNotNull { it.completedDate }
                MetricResult(
                    ProgressMetric.GOALS_COMPLETED,
                    current = completedDates.count { window.isCurrent(it) }.toDouble(),
                    previous = if (window.previous == null) null else completedDates.count { window.isPrevious(it) }.toDouble(),
                    availability = MetricAvailability.AVAILABLE
                )
            }
        }
        return out
    }

    // MARK: - Health platform

    private fun healthResults(metrics: Set<ProgressMetric>, window: Window, source: ProgressDataSource): Map<ProgressMetric, MetricResult> {
        val healthMetrics = metrics.filter { it.source == MetricSource.HEALTH }.toSet()
        if (!source.isHealthConnected) return healthMetrics.associateWith { MetricResult.unavailable(it, MetricAvailability.NOT_CONNECTED) }
        // All Time (fetchStart 0) reads back as far as the source allows.
        val start = window.fetchStart
        val end = window.nowMs
        val out = mutableMapOf<ProgressMetric, MetricResult>()

        /** Daily values → result. The period's days become one figure (mean or total); days without data are left out. */
        fun result(metric: ProgressMetric, daily: Map<Long, Double>, total: Boolean = false): MetricResult {
            fun aggregate(values: Collection<Double>): Double? =
                if (values.isEmpty()) null else if (total) values.sum() else values.sum() / values.size
            val current = daily.filterKeys { window.isCurrent(it) }
            val previous = daily.filterKeys { window.isPrevious(it) }
            val value = aggregate(current.values) ?: return MetricResult.unavailable(metric, MetricAvailability.NO_DATA)
            val grouped = current.entries.groupBy({ window.bucket(it.key) }, { it.value })
            val series = grouped.keys.sorted().map { key -> grouped.getValue(key).let { it.sum() / it.size } }
            return MetricResult(
                metric, value, if (window.previous == null) null else aggregate(previous.values), series, MetricAvailability.AVAILABLE
            )
        }

        if (ProgressMetric.AVERAGE_STEPS in healthMetrics || ProgressMetric.TOTAL_STEPS in healthMetrics) {
            val steps = source.healthDaily(ProgressMetric.AVERAGE_STEPS, start, end)
            if (ProgressMetric.AVERAGE_STEPS in healthMetrics) out[ProgressMetric.AVERAGE_STEPS] = result(ProgressMetric.AVERAGE_STEPS, steps)
            if (ProgressMetric.TOTAL_STEPS in healthMetrics) out[ProgressMetric.TOTAL_STEPS] = result(ProgressMetric.TOTAL_STEPS, steps, total = true)
        }
        for (metric in listOf(ProgressMetric.ACTIVE_CALORIES, ProgressMetric.ACTIVE_MINUTES, ProgressMetric.RESTING_HEART_RATE,
            ProgressMetric.HRV, ProgressMetric.AVERAGE_SLEEP)) {
            if (metric in healthMetrics) out[metric] = result(metric, source.healthDaily(metric, start, end))
        }
        if (ProgressMetric.BODY_FAT in healthMetrics) {
            val latest = source.latestBodyFat(end)
            out[ProgressMetric.BODY_FAT] = if (latest != null && window.isCurrent(latest.second)) {
                val before = window.previous?.let { source.latestBodyFat(it.last) }
                MetricResult(ProgressMetric.BODY_FAT, latest.first, before?.first, availability = MetricAvailability.AVAILABLE)
            } else MetricResult.unavailable(ProgressMetric.BODY_FAT, MetricAvailability.NO_DATA)
        }
        return out
    }

    // MARK: - Insight card (Progress PRD §4)

    /**
     * Metrics the insight card draws from, in priority order. Personal
     * Records is deliberately absent: its query doesn't compare with earlier
     * history, so it can't back a "new record" claim.
     */
    val insightMetrics: List<ProgressMetric> = listOf(
        ProgressMetric.TRAINING_VOLUME, ProgressMetric.TOTAL_WORKOUTS, ProgressMetric.TOTAL_TIME,
        ProgressMetric.AVERAGE_STEPS, ProgressMetric.AVERAGE_SLEEP, ProgressMetric.GOALS_COMPLETED
    )

    /**
     * One insight from real data. Wording stays descriptive — "up" or "down",
     * never "better" — and no percentage appears without a valid baseline
     * (PRD §4.4). Among several meaningful changes the pick rotates daily.
     */
    fun insight(
        results: Map<ProgressMetric, MetricResult>,
        range: ReportTimeRange,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): ProgressInsight {
        val meaningfulPercent = 5.0
        val phrase = range.previousPeriodPhrase

        val changes = mutableListOf<Pair<ProgressMetric, Double>>()
        val steady = mutableListOf<ProgressMetric>()
        for (metric in insightMetrics) {
            when (val change = results[metric]?.change ?: continue) {
                is MetricChange.Up -> if (change.percent >= meaningfulPercent) changes += metric to change.percent else steady += metric
                is MetricChange.Down -> if (change.percent >= meaningfulPercent) changes += metric to -change.percent else steady += metric
                MetricChange.Steady -> steady += metric
            }
        }

        if (changes.isNotEmpty()) {
            val ranked = changes.sortedByDescending { abs(it.second) }.take(3)
            val day = Calendar.getInstance(timeZone).apply { timeInMillis = nowMs }.get(Calendar.DAY_OF_YEAR)
            val (metric, pct) = ranked[day % ranked.size]
            val direction = if (pct > 0) "up" else "down"
            val percent = abs(pct).roundToInt()
            return ProgressInsight(
                headline = "${metric.title} is $direction $percent%",
                detail = "Your ${metric.title.lowercase()} is $direction $percent% compared with the $phrase.",
                icon = metric.icon,
                report = metric.category.report,
                isUp = pct > 0
            )
        }

        val completedGoals = results[ProgressMetric.GOALS_COMPLETED]?.current
        if (completedGoals != null && completedGoals > 0) {
            val n = completedGoals.toInt()
            return ProgressInsight(
                headline = if (n == 1) "Goal completed" else "$n goals completed",
                detail = "You completed ${if (n == 1) "a goal" else "$n goals"} in this period.",
                icon = "checkmark.seal.fill",
                report = ProgressReport.GOALS,
                isUp = null
            )
        }

        steady.firstOrNull()?.let { metric ->
            return ProgressInsight(
                headline = "Holding steady",
                detail = "Your ${metric.title.lowercase()} is about the same as the $phrase.",
                icon = metric.icon,
                report = metric.category.report,
                isUp = null
            )
        }

        // All Time (no comparison) or no previous data yet: a plain total.
        val workouts = results[ProgressMetric.TOTAL_WORKOUTS]?.current
        if (workouts != null && workouts > 0) {
            val n = workouts.toInt()
            val plural = if (n == 1) "" else "s"
            val span = if (range == ReportTimeRange.ALL_TIME) "in total" else "in the last ${range.label.lowercase()}"
            return ProgressInsight(
                headline = "$n workout$plural completed",
                detail = "You've completed $n workout$plural $span. Keep logging to see trends.",
                icon = "dumbbell.fill",
                report = ProgressReport.WORKOUT,
                isUp = null
            )
        }
        return ProgressInsight.empty
    }
}
