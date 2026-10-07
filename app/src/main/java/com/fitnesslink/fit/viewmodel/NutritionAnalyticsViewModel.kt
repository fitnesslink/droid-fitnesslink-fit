package com.fitnesslink.fit.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.ChartPoint
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.NutritionConsistencyResponse
import com.fitnesslink.fit.network.dto.NutritionTrendBucket
import com.fitnesslink.fit.network.dto.NutritionTrendPeriod
import com.fitnesslink.fit.network.dto.NutritionTrendsResponse
import com.fitnesslink.fit.network.dto.WeightProgressResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Date
import java.util.TimeZone

/** Range picker options (iOS `NutritionAnalyticsRange`). Week/Month = daily buckets; 3 Months = weekly. */
enum class NutritionAnalyticsRange(val label: String, val dayCount: Int) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    THREE_MONTHS("3 Months", 91);

    val period: String get() = if (this == THREE_MONTHS) NutritionTrendPeriod.WEEKLY else NutritionTrendPeriod.DAILY
    val isWeekly: Boolean get() = period == NutritionTrendPeriod.WEEKLY
}

/** A contiguous run of points for one line; sparse buckets split a line into segments so gaps render as gaps. */
data class AnalyticsLineSegment(val id: String, val series: String, val points: List<ChartPoint>)

/** One macro's consumed + target lines for the Macros chart. */
data class MacroTrendLine(
    val id: String,
    val name: String,
    val consumedSegments: List<AnalyticsLineSegment>,
    val targetSegments: List<AnalyticsLineSegment>
) {
    val consumedPoints: List<ChartPoint> get() = consumedSegments.flatMap { it.points }
    val targetPoints: List<ChartPoint> get() = targetSegments.flatMap { it.points }
}

/**
 * Drives the nutrition analytics screen (port of iOS
 * `NutritionAnalyticsViewModel`, FTNSSLNK-212): three read-only endpoint
 * calls per range (trends, consistency, weight-progress), fetched
 * concurrently. Every displayed number is the server's value verbatim; the
 * only client-side math is picking WHICH field to plot per bucket
 * granularity (see [NutritionAnalyticsMath]).
 */
class NutritionAnalyticsViewModel : ViewModel() {
    var selectedRange by mutableStateOf(NutritionAnalyticsRange.WEEK)
        private set

    var trends by mutableStateOf<NutritionTrendsResponse?>(null)
        private set
    var buckets by mutableStateOf<List<NutritionTrendBucket>>(emptyList())
        private set
    var consistency by mutableStateOf<NutritionConsistencyResponse?>(null)
        private set
    var weightProgress by mutableStateOf<WeightProgressResponse?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var hasContent by mutableStateOf(false)
        private set

    private var loadToken = 0

    val isWeekly: Boolean get() = selectedRange.isWeekly

    fun selectRange(range: NutritionAnalyticsRange) {
        selectedRange = range
    }

    suspend fun load() {
        loadToken += 1
        val token = loadToken
        val to = MealPlanDay.dayStart(Date())
        val from = MealPlanDay.addDays(to, -(selectedRange.dayCount - 1))
        val fromKey = MealPlanDay.string(from)
        val toKey = MealPlanDay.string(to)
        val tz = TimeZone.getDefault().id
        isLoading = true
        try {
            coroutineScope {
                val trendsTask = async { fetchAllTrendBuckets(fromKey, toKey, selectedRange.period, tz) }
                val consistencyTask = async { ApiClient.nutritionApi.getNutritionConsistency(fromKey, toKey, tz) }
                val weightTask = async { ApiClient.nutritionApi.getWeightProgress(fromKey, toKey, tz) }
                val (trendsResponse, allBuckets) = trendsTask.await()
                val consistencyResponse = consistencyTask.await()
                val weightResponse = weightTask.await()
                if (token != loadToken) return@coroutineScope
                trends = trendsResponse
                buckets = allBuckets
                consistency = consistencyResponse
                weightProgress = weightResponse
                hasContent = true
                errorMessage = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (token == loadToken) {
                Log.w("NutritionAnalytics", "load failed", e)
                errorMessage = describe(e)
            }
        } finally {
            if (token == loadToken) isLoading = false
        }
    }

    /** Walk the `data`/`hasNextPage` envelope so long ranges aren't truncated. */
    private suspend fun fetchAllTrendBuckets(from: String, to: String, period: String, tz: String): Pair<NutritionTrendsResponse, List<NutritionTrendBucket>> {
        var page = 1
        var first: NutritionTrendsResponse? = null
        val all = ArrayList<NutritionTrendBucket>()
        while (true) {
            val response = ApiClient.nutritionApi.getNutritionTrends(from, to, period, tz, page = page, pageSize = 100)
            if (first == null) first = response
            all.addAll(response.buckets.data)
            if (!response.buckets.hasNextPage || response.buckets.data.isEmpty() || page >= 10) break
            page += 1
        }
        return (first ?: NutritionTrendsResponse()) to all.sortedBy { it.periodStart }
    }

    private fun describe(e: Exception): String = when (e) {
        is ApiError.Unauthorized -> "Session expired — please sign in again."
        is ApiError.ServerError -> {
            val message = e.envelope?.displayMessage ?: e.body
            if (message.isNotBlank() && message.length <= 200) "Couldn't load analytics: $message" else "Couldn't load analytics."
        }
        else -> "Couldn't load analytics. Check your connection and try again."
    }

    // MARK: - Calories

    val caloriePoints: List<ChartPoint>
        get() = buckets.mapNotNull { b ->
            NutritionAnalyticsMath.perDayValue(isWeekly, b.daysLogged, b.caloriesConsumed, b.avgCaloriesPerLoggedDay)
                ?.let { ChartPoint(MealPlanDay.parse(b.periodStart), it) }
        }

    val calorieTargetSegments: List<AnalyticsLineSegment>
        get() = NutritionAnalyticsMath.segments(buckets, "CalTarget") { it.avgDailyCalorieTarget }

    /** Average per-day calorie target across buckets that had one (the dashed reference line). */
    val calorieTargetAverage: Double?
        get() = buckets.mapNotNull { it.avgDailyCalorieTarget }.takeIf { it.isNotEmpty() }?.average()

    val hasAnyFoodData: Boolean get() = buckets.any { it.daysLogged > 0 }

    // MARK: - Macros

    val macroLines: List<MacroTrendLine>
        get() = listOf(
            MacroTrendLine(
                id = "Protein", name = "Protein",
                consumedSegments = NutritionAnalyticsMath.segments(buckets, "Protein") {
                    NutritionAnalyticsMath.perDayValue(isWeekly, it.daysLogged, it.proteinConsumed, it.avgProteinPerLoggedDay)
                },
                targetSegments = NutritionAnalyticsMath.segments(buckets, "ProteinTarget") { it.avgDailyProteinTarget }
            ),
            MacroTrendLine(
                id = "Carbs", name = "Carbs",
                consumedSegments = NutritionAnalyticsMath.segments(buckets, "Carbs") {
                    NutritionAnalyticsMath.perDayValue(isWeekly, it.daysLogged, it.carbsConsumed, it.avgCarbsPerLoggedDay)
                },
                targetSegments = NutritionAnalyticsMath.segments(buckets, "CarbsTarget") { it.avgDailyCarbsTarget }
            ),
            MacroTrendLine(
                id = "Fat", name = "Fat",
                consumedSegments = NutritionAnalyticsMath.segments(buckets, "Fat") {
                    NutritionAnalyticsMath.perDayValue(isWeekly, it.daysLogged, it.fatConsumed, it.avgFatPerLoggedDay)
                },
                targetSegments = NutritionAnalyticsMath.segments(buckets, "FatTarget") { it.avgDailyFatTarget }
            )
        )

    // MARK: - Water

    val waterPoints: List<ChartPoint>
        get() = buckets.mapNotNull { b ->
            val value = if (isWeekly) b.avgWaterPerLoggedDay else if (b.waterDaysLogged > 0) b.waterTotal else null
            value?.let { ChartPoint(MealPlanDay.parse(b.periodStart), it) }
        }

    val waterUnit: String get() = trends?.waterUnit?.takeIf { it.isNotEmpty() } ?: "oz"
    val waterDailyGoal: Double? get() = trends?.waterDailyGoal
    val hasAnyWaterData: Boolean get() = buckets.any { it.waterDaysLogged > 0 }

    // MARK: - Weight / goal progress

    val weightPoints: List<ChartPoint>
        get() = (weightProgress?.points ?: emptyList()).map { ChartPoint(MealPlanDay.parse(it.date), it.weight) }

    /** Server 7-day average; falls back to a client trailing average when the server sent zeros. */
    val movingAvgPoints: List<ChartPoint>
        get() {
            val points = weightProgress?.points ?: return emptyList()
            val serverHasAvg = points.any { it.movingAvg7Day != 0.0 }
            return if (serverHasAvg) points.map { ChartPoint(MealPlanDay.parse(it.date), it.movingAvg7Day) }
            else {
                val avg = NutritionAnalyticsMath.trailingMovingAverage(points.map { it.weight }, 7)
                points.mapIndexed { i, p -> ChartPoint(MealPlanDay.parse(p.date), avg[i]) }
            }
        }

    /** Two-point plan projection across the visible range. */
    val planLinePoints: List<ChartPoint>
        get() {
            val progress = weightProgress ?: return emptyList()
            val plan = progress.plan ?: return emptyList()
            return NutritionAnalyticsMath.planLine(
                rangeStart = MealPlanDay.parse(progress.from),
                rangeEnd = MealPlanDay.parse(progress.to),
                anchorDate = MealPlanDay.parse(plan.anchorDate),
                anchorWeight = plan.anchorWeight,
                weeklyChange = plan.weeklyChange,
                projectedWeightAtRangeEnd = plan.projectedWeightAtRangeEnd
            )
        }

    /** Server estimate first; client projection as the offline fallback. */
    val estimatedGoalDate: Date?
        get() {
            val plan = weightProgress?.plan ?: return null
            plan.estimatedGoalDate?.let { MealPlanDay.date(it) }?.let { return it }
            val goal = plan.goalWeight ?: return null
            return NutritionAnalyticsMath.estimatedGoalDate(MealPlanDay.parse(plan.anchorDate), plan.anchorWeight, plan.weeklyChange, goal)
        }
}

/** Pure chart/projection math (plain JVM, unit-tested). */
object NutritionAnalyticsMath {
    private const val DAY_MS = 86_400_000.0

    /**
     * Daily buckets plot the consumed total; weekly buckets plot the
     * server's avg-per-logged-day. Null for 0-logged-day buckets (gaps).
     */
    fun perDayValue(isWeekly: Boolean, daysLogged: Int, daily: Double, weeklyAvg: Double?): Double? {
        if (isWeekly) return weeklyAvg
        return if (daysLogged > 0) daily else null
    }

    /** Split a per-bucket optional value into contiguous runs (null ends a run). */
    fun segments(buckets: List<NutritionTrendBucket>, series: String, value: (NutritionTrendBucket) -> Double?): List<AnalyticsLineSegment> {
        val result = ArrayList<AnalyticsLineSegment>()
        var run = ArrayList<ChartPoint>()
        fun flush() {
            if (run.isEmpty()) return
            result.add(AnalyticsLineSegment("$series-${result.size}", series, run))
            run = ArrayList()
        }
        for (bucket in buckets) {
            val v = value(bucket)
            if (v != null) run.add(ChartPoint(MealPlanDay.parse(bucket.periodStart), v)) else flush()
        }
        flush()
        return result
    }

    /** Trailing moving average over up to [window] points (partial windows at the start). */
    fun trailingMovingAverage(values: List<Double>, window: Int): List<Double> {
        if (window <= 0) return values
        val out = ArrayList<Double>(values.size)
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= window) sum -= values[i - window]
            val count = minOf(i + 1, window)
            out.add(sum / count)
        }
        return out
    }

    /**
     * Two-point plan line: starts at the anchor (or the plan's straight line
     * evaluated at the range start when the anchor is off-screen) and ends at
     * the server's projected weight at the range end.
     */
    fun planLine(
        rangeStart: Date,
        rangeEnd: Date,
        anchorDate: Date,
        anchorWeight: Double,
        weeklyChange: Double,
        projectedWeightAtRangeEnd: Double
    ): List<ChartPoint> {
        val start = if (!anchorDate.before(rangeStart)) {
            ChartPoint(anchorDate, anchorWeight)
        } else {
            ChartPoint(rangeStart, projectWeight(anchorDate, anchorWeight, weeklyChange, rangeStart))
        }
        val end = ChartPoint(rangeEnd, projectedWeightAtRangeEnd)
        if (!start.date.before(end.date)) return listOf(end)
        return listOf(start, end)
    }

    /** The plan's linear projection at [date]: anchor + weeklyChange × weeks elapsed. */
    fun projectWeight(anchorDate: Date, anchorWeight: Double, weeklyChange: Double, date: Date): Double {
        val days = (date.time - anchorDate.time) / DAY_MS
        return anchorWeight + weeklyChange * (days / 7.0)
    }

    /**
     * When the plan reaches [goalWeight] at [weeklyChange] per week from the
     * anchor, or null when the pace is zero or moves away from the goal.
     */
    fun estimatedGoalDate(anchorDate: Date, anchorWeight: Double, weeklyChange: Double, goalWeight: Double): Date? {
        if (weeklyChange == 0.0) return null
        val delta = goalWeight - anchorWeight
        if (delta == 0.0) return anchorDate
        if ((delta < 0) != (weeklyChange < 0)) return null
        val weeks = delta / weeklyChange
        val days = Math.ceil(weeks * 7).toLong()
        return Date(anchorDate.time + days * DAY_MS.toLong())
    }
}
