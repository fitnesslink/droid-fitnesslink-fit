package com.fitnesslink.fit.progress

import com.fitnesslink.fit.model.BodyMeasurementValue
import com.fitnesslink.fit.model.BodyPart
import com.fitnesslink.fit.model.DailyNutritionRow
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.MeasurementUnit
import com.fitnesslink.fit.model.MetricAvailability
import com.fitnesslink.fit.model.MetricChange
import com.fitnesslink.fit.model.MetricResult
import com.fitnesslink.fit.model.ProgressCategory
import com.fitnesslink.fit.model.ProgressMetric
import com.fitnesslink.fit.model.ProgressReport
import com.fitnesslink.fit.model.QuickStatsStore
import com.fitnesslink.fit.model.ReportTimeRange
import com.fitnesslink.fit.model.SessionRow
import com.fitnesslink.fit.model.WeightEntry
import com.fitnesslink.fit.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Rules of the Progress metrics engine (port of iOS `ProgressMetricsEngine`). */
class ProgressMetricsEngineTests {
    private val tz: TimeZone = TimeZone.getTimeZone("America/New_York")

    /** 2026-10-31 12:00 local. */
    private val now: Long = Calendar.getInstance(tz).apply {
        clear(); set(2026, Calendar.OCTOBER, 31, 12, 0, 0)
    }.timeInMillis

    private fun daysAgo(days: Int, hour: Int = 9): Date = Date(
        Calendar.getInstance(tz).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -days)
            set(Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis
    )

    private fun session(daysAgo: Int, completed: Boolean = true, seconds: Int = 1800, volume: Double = 1000.0,
                        calories: Double = 0.0, rpe: Double? = null, sets: Int = 0, reps: Int = 0) =
        SessionRow(
            id = "s$daysAgo-${Math.random()}", workoutName = "W", date = daysAgo(daysAgo), durationSeconds = seconds,
            isCompleted = completed, exerciseCount = 1, totalWeightLifted = volume, totalCaloriesBurned = calories,
            rpeValue = rpe, totalSets = sets, totalReps = reps
        )

    private class FakeSource(
        val sessions: List<SessionRow> = emptyList(),
        val prCount: Int = 0,
        val dates: List<Date> = emptyList(),
        val nutrition: List<DailyNutritionRow> = emptyList(),
        val weights: List<WeightEntry> = emptyList(),
        val measurements: List<MeasurementEntry> = emptyList(),
        val goalList: List<Goal> = emptyList(),
        override val isHealthConnected: Boolean = false,
        /** Per-metric daily health values keyed by local start-of-day ms. */
        val health: Map<ProgressMetric, Map<Long, Double>> = emptyMap(),
        val bodyFat: List<Pair<Double, Long>> = emptyList()
    ) : ProgressDataSource {
        override fun healthDaily(metric: ProgressMetric, from: Long, to: Long): Map<Long, Double> {
            val key = if (metric == ProgressMetric.TOTAL_STEPS) ProgressMetric.AVERAGE_STEPS else metric
            return health[key].orEmpty().filterKeys { it in from until to }
        }
        override fun latestBodyFat(before: Long) = bodyFat.filter { it.second <= before }.maxByOrNull { it.second }
        override fun sessionRows(since: Long) = sessions.filter { it.date.time >= since }
        override fun personalRecordCount(since: Long) = prCount
        override fun workoutDates() = dates
        override fun dailyNutritionRows(since: Long) = nutrition.filter { it.date.time >= since }
        override fun weightEntries() = weights
        override fun measurementEntries() = measurements
        override fun goals() = goalList
    }

    private fun compute(metrics: Set<ProgressMetric>, source: ProgressDataSource, range: ReportTimeRange = ReportTimeRange.SEVEN_DAYS) =
        ProgressMetricsEngine.compute(metrics, range, now, source, tz)

    // MARK: - Periods

    @Test fun previousIntervalIsTheEquivalentWindowBeforeTheCurrentOne() {
        val range = ReportTimeRange.THIRTY_DAYS
        val start = range.startMillis(now)!!
        val previous = range.previousInterval(now)!!
        assertEquals(now - start, start - previous.first)
        assertEquals(start - 1, previous.last)
        assertNull(ReportTimeRange.ALL_TIME.previousInterval(now))
        assertNull(ReportTimeRange.ALL_TIME.startMillis(now))
        assertEquals(0L, ReportTimeRange.ALL_TIME.sinceMillis(now))
    }

    @Test fun weeklyBucketsBeyondAMonth() {
        assertTrue(!ReportTimeRange.SEVEN_DAYS.groupsByWeek)
        assertTrue(!ReportTimeRange.THIRTY_DAYS.groupsByWeek)
        assertTrue(ReportTimeRange.THREE_MONTHS.groupsByWeek)
        assertTrue(ReportTimeRange.ALL_TIME.groupsByWeek)
    }

    // MARK: - Workouts

    @Test fun totalWorkoutsComparesWithThePreviousPeriod() {
        val source = FakeSource(sessions = listOf(
            session(1), session(2), session(3), session(4, completed = false), // current: 3 completed
            session(9), session(10) // previous 7 days: 2 completed
        ))
        val r = compute(setOf(ProgressMetric.TOTAL_WORKOUTS, ProgressMetric.INCOMPLETE_WORKOUTS), source)
        val total = r.getValue(ProgressMetric.TOTAL_WORKOUTS)
        assertEquals(3.0, total.current!!, 0.0)
        assertEquals(2.0, total.previous!!, 0.0)
        assertEquals(MetricChange.Up(50.0), total.change)
        assertEquals(1.0, r.getValue(ProgressMetric.INCOMPLETE_WORKOUTS).current!!, 0.0)
        // Daily buckets over the 7-day window, empty days count as zero.
        assertTrue(total.series.size in 7..8)
        assertEquals(3.0, total.series.sum(), 0.0)
    }

    @Test fun zeroWorkoutCaloriesIsNoDataNotZero() {
        val source = FakeSource(sessions = listOf(session(1, calories = 0.0)))
        val r = compute(setOf(ProgressMetric.CALORIES_BURNED), source)
        assertEquals(MetricAvailability.NO_DATA, r.getValue(ProgressMetric.CALORIES_BURNED).availability)
        assertEquals("—", r.getValue(ProgressMetric.CALORIES_BURNED).displayValue)
    }

    @Test fun averageRpeIgnoresUnratedAndIncompleteSessions() {
        val source = FakeSource(sessions = listOf(
            session(1, rpe = 6.0), session(2, rpe = 8.0), session(3, rpe = null), session(4, completed = false, rpe = 2.0)
        ))
        val r = compute(setOf(ProgressMetric.AVERAGE_RPE), source)
        assertEquals(7.0, r.getValue(ProgressMetric.AVERAGE_RPE).current!!, 0.0001)
    }

    @Test fun setsAndRepsCountOnlyFinishedWorkouts() {
        val source = FakeSource(sessions = listOf(session(1, sets = 10, reps = 80), session(2, completed = false, sets = 4, reps = 30)))
        val r = compute(setOf(ProgressMetric.TOTAL_SETS, ProgressMetric.TOTAL_REPS), source)
        assertEquals(10.0, r.getValue(ProgressMetric.TOTAL_SETS).current!!, 0.0)
        assertEquals(80.0, r.getValue(ProgressMetric.TOTAL_REPS).current!!, 0.0)
    }

    @Test fun allTimeHasNoPreviousPeriod() {
        val source = FakeSource(sessions = listOf(session(1), session(400)))
        val total = compute(setOf(ProgressMetric.TOTAL_WORKOUTS), source, ReportTimeRange.ALL_TIME).getValue(ProgressMetric.TOTAL_WORKOUTS)
        assertEquals(2.0, total.current!!, 0.0)
        assertNull(total.previous)
        assertNull(total.change)
    }

    // MARK: - Streaks

    @Test fun currentStreakEndsTodayOrYesterday() {
        val dates = listOf(daysAgo(1), daysAgo(2), daysAgo(3), daysAgo(10), daysAgo(11))
        assertEquals(3 to 3, ProgressMetricsEngine.streaks(dates, now, tz))
        val withToday = dates + daysAgo(0)
        assertEquals(4 to 4, ProgressMetricsEngine.streaks(withToday, now, tz))
    }

    @Test fun brokenStreakKeepsLongest() {
        val dates = listOf(daysAgo(5), daysAgo(6), daysAgo(7), daysAgo(8))
        assertEquals(0 to 4, ProgressMetricsEngine.streaks(dates, now, tz))
        assertEquals(0 to 0, ProgressMetricsEngine.streaks(emptyList(), now, tz))
    }

    @Test fun twoWorkoutsOnOneDayCountOnce() {
        val dates = listOf(daysAgo(0, hour = 7), daysAgo(0, hour = 18), daysAgo(1))
        assertEquals(2 to 2, ProgressMetricsEngine.streaks(dates, now, tz))
    }

    // MARK: - Nutrition

    @Test fun nutritionAveragesOverLoggedDaysOnly() {
        fun row(d: Int, cals: Int, protein: Double, onTarget: Boolean) =
            DailyNutritionRow("d$d", daysAgo(d), cals, protein, 50.0, 200.0, 2000, onTarget)
        val source = FakeSource(nutrition = listOf(row(1, 2000, 150.0, true), row(3, 1500, 100.0, false)))
        val r = compute(setOf(ProgressMetric.AVG_CALORIES_CONSUMED, ProgressMetric.AVG_PROTEIN, ProgressMetric.CALORIE_GOAL_ADHERENCE), source)
        assertEquals(1750.0, r.getValue(ProgressMetric.AVG_CALORIES_CONSUMED).current!!, 0.0)
        assertEquals(125.0, r.getValue(ProgressMetric.AVG_PROTEIN).current!!, 0.0)
        assertEquals(50.0, r.getValue(ProgressMetric.CALORIE_GOAL_ADHERENCE).current!!, 0.0)
    }

    @Test fun noFoodLoggedIsNoData() {
        val r = compute(setOf(ProgressMetric.AVG_FAT), FakeSource())
        assertEquals(MetricAvailability.NO_DATA, r.getValue(ProgressMetric.AVG_FAT).availability)
    }

    // MARK: - Body log

    @Test fun weightIsStoredInPoundsAndChangeNeedsTwoWeighIns() {
        val one = FakeSource(weights = listOf(WeightEntry(weight = 80.0, unit = WeightUnit.KG, date = daysAgo(2))))
        val r1 = compute(setOf(ProgressMetric.BODY_WEIGHT, ProgressMetric.WEIGHT_CHANGE), one)
        assertEquals(80.0 / 0.453592, r1.getValue(ProgressMetric.BODY_WEIGHT).current!!, 0.01)
        assertEquals(MetricAvailability.NO_DATA, r1.getValue(ProgressMetric.WEIGHT_CHANGE).availability)

        val two = FakeSource(weights = listOf(
            WeightEntry(weight = 200.0, unit = WeightUnit.LBS, date = daysAgo(5)),
            WeightEntry(weight = 196.0, unit = WeightUnit.LBS, date = daysAgo(1))
        ))
        val r2 = compute(setOf(ProgressMetric.BODY_WEIGHT, ProgressMetric.WEIGHT_CHANGE), two)
        assertEquals(196.0, r2.getValue(ProgressMetric.BODY_WEIGHT).current!!, 0.0)
        assertEquals(-4.0, r2.getValue(ProgressMetric.WEIGHT_CHANGE).current!!, 0.0001)
        // Weight change is a state within the period — no comparison arrow.
        assertNull(r2.getValue(ProgressMetric.WEIGHT_CHANGE).change)
    }

    @Test fun waistIsReportedInInches() {
        val entry = MeasurementEntry(
            date = daysAgo(2),
            measurements = listOf(BodyMeasurementValue(bodyPart = BodyPart.WAIST, value = 81.28, unit = MeasurementUnit.CM))
        )
        val r = compute(setOf(ProgressMetric.WAIST, ProgressMetric.HIPS), FakeSource(measurements = listOf(entry)))
        assertEquals(32.0, r.getValue(ProgressMetric.WAIST).current!!, 0.001)
        assertEquals(MetricAvailability.NO_DATA, r.getValue(ProgressMetric.HIPS).availability)
    }

    // MARK: - Goals

    @Test fun goalsCountActiveAndCompletedInPeriod() {
        val goals = listOf(
            Goal(status = GoalStatus.Active),
            Goal(status = GoalStatus.Active),
            Goal(status = GoalStatus.Completed, completedDate = daysAgo(2).time),
            Goal(status = GoalStatus.Completed, completedDate = daysAgo(40).time)
        )
        val r = compute(setOf(ProgressMetric.ACTIVE_GOALS, ProgressMetric.GOALS_COMPLETED), FakeSource(goalList = goals))
        assertEquals(2.0, r.getValue(ProgressMetric.ACTIVE_GOALS).current!!, 0.0)
        assertEquals(1.0, r.getValue(ProgressMetric.GOALS_COMPLETED).current!!, 0.0)
        assertEquals(0.0, r.getValue(ProgressMetric.GOALS_COMPLETED).previous!!, 0.0)
    }

    @Test fun noGoalsIsNoData() {
        val r = compute(setOf(ProgressMetric.ACTIVE_GOALS), FakeSource())
        assertEquals(MetricAvailability.NO_DATA, r.getValue(ProgressMetric.ACTIVE_GOALS).availability)
    }

    // MARK: - Health

    @Test fun healthMetricsNeedAConnection() {
        val r = compute(setOf(ProgressMetric.AVERAGE_STEPS, ProgressMetric.AVERAGE_SLEEP, ProgressMetric.BODY_FAT), FakeSource())
        r.values.forEach { assertEquals(MetricAvailability.NOT_CONNECTED, it.availability) }
        assertEquals(3, r.size)
    }

    private fun dayStart(daysAgo: Int): Long = Calendar.getInstance(tz).apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun connectedHealthAveragesAndTotalsOnlyDaysWithData() {
        val steps = mapOf(dayStart(0) to 4000.0, dayStart(1) to 8000.0, dayStart(10) to 2000.0)
        val r = compute(setOf(ProgressMetric.AVERAGE_STEPS, ProgressMetric.TOTAL_STEPS),
            FakeSource(isHealthConnected = true, health = mapOf(ProgressMetric.AVERAGE_STEPS to steps)))
        val avg = r.getValue(ProgressMetric.AVERAGE_STEPS)
        assertEquals(MetricAvailability.AVAILABLE, avg.availability)
        assertEquals(6000.0, avg.current!!, 0.001)
        assertEquals(2000.0, avg.previous!!, 0.001)
        assertEquals(12000.0, r.getValue(ProgressMetric.TOTAL_STEPS).current!!, 0.001)
    }

    @Test fun connectedHealthWithoutReadingsIsNoData() {
        val r = compute(setOf(ProgressMetric.AVERAGE_SLEEP, ProgressMetric.HRV, ProgressMetric.BODY_FAT), FakeSource(isHealthConnected = true))
        r.values.forEach { assertEquals(MetricAvailability.NO_DATA, it.availability) }
    }

    @Test fun bodyFatIsLatestReadingInPeriod() {
        val source = FakeSource(isHealthConnected = true, bodyFat = listOf(22.5 to daysAgo(9).time, 21.0 to daysAgo(2).time))
        val r = compute(setOf(ProgressMetric.BODY_FAT), source).getValue(ProgressMetric.BODY_FAT)
        assertEquals(21.0, r.current!!, 0.001)
        assertEquals(22.5, r.previous!!, 0.001)
        val stale = compute(setOf(ProgressMetric.BODY_FAT), FakeSource(isHealthConnected = true, bodyFat = listOf(22.5 to daysAgo(9).time)))
        assertEquals(MetricAvailability.NO_DATA, stale.getValue(ProgressMetric.BODY_FAT).availability)
    }

    @Test fun onlyRequestedMetricsAreReturned() {
        val r = compute(setOf(ProgressMetric.TOTAL_TIME), FakeSource(sessions = listOf(session(1))))
        assertEquals(setOf(ProgressMetric.TOTAL_TIME), r.keys)
    }

    // MARK: - Change

    @Test fun smallChangeIsSteadyAndZeroBaselineHasNoChange() {
        val steady = MetricResult(ProgressMetric.TOTAL_TIME, 1000.0, 995.0, availability = MetricAvailability.AVAILABLE)
        assertEquals(MetricChange.Steady, steady.change)
        val zero = MetricResult(ProgressMetric.TOTAL_TIME, 1000.0, 0.0, availability = MetricAvailability.AVAILABLE)
        assertNull(zero.change)
        val down = MetricResult(ProgressMetric.TOTAL_TIME, 50.0, 100.0, availability = MetricAvailability.AVAILABLE)
        assertEquals(MetricChange.Down(50.0), down.change)
    }

    // MARK: - Insight

    @Test fun insightNamesTheLargestMeaningfulChange() {
        val results = mapOf(
            ProgressMetric.TRAINING_VOLUME to MetricResult(ProgressMetric.TRAINING_VOLUME, 1200.0, 1000.0, availability = MetricAvailability.AVAILABLE)
        )
        val insight = ProgressMetricsEngine.insight(results, ReportTimeRange.THIRTY_DAYS, now, tz)
        assertEquals("Training Volume is up 20%", insight.headline)
        assertEquals("Your training volume is up 20% compared with the previous 30 days.", insight.detail)
        assertEquals(ProgressReport.WORKOUT, insight.report)
        assertEquals(true, insight.isUp)
    }

    @Test fun insightFallsBackToSteadyThenTotalsThenEmpty() {
        val steady = mapOf(
            ProgressMetric.TOTAL_TIME to MetricResult(ProgressMetric.TOTAL_TIME, 1010.0, 1000.0, availability = MetricAvailability.AVAILABLE)
        )
        assertEquals("Holding steady", ProgressMetricsEngine.insight(steady, ReportTimeRange.SEVEN_DAYS, now, tz).headline)

        val allTime = mapOf(
            ProgressMetric.TOTAL_WORKOUTS to MetricResult(ProgressMetric.TOTAL_WORKOUTS, 12.0, availability = MetricAvailability.AVAILABLE)
        )
        val totals = ProgressMetricsEngine.insight(allTime, ReportTimeRange.ALL_TIME, now, tz)
        assertEquals("12 workouts completed", totals.headline)
        assertTrue(totals.detail.contains("in total"))

        assertEquals(ProgressInsight.empty, ProgressMetricsEngine.insight(emptyMap(), ReportTimeRange.SEVEN_DAYS, now, tz))
    }

    @Test fun insightReportsCompletedGoals() {
        val results = mapOf(
            ProgressMetric.GOALS_COMPLETED to MetricResult(ProgressMetric.GOALS_COMPLETED, 2.0, 2.0, availability = MetricAvailability.AVAILABLE)
        )
        // A 0% change is "steady", but completed goals take precedence over steady.
        val insight = ProgressMetricsEngine.insight(results, ReportTimeRange.SEVEN_DAYS, now, tz)
        assertEquals("2 goals completed", insight.headline)
        assertEquals(ProgressReport.GOALS, insight.report)
    }

    // MARK: - Registry

    @Test fun registryCoversEveryMetricAndReport() {
        assertEquals(ProgressMetric.entries.size, ProgressCategory.entries.sumOf { it.metrics.size })
        ProgressReport.entries.forEach { assertEquals(it, it.category.report) }
        assertEquals("progressReport/activity", ProgressReport.ACTIVITY.route)
        assertEquals("workoutReport", ProgressReport.WORKOUT.route)
        assertEquals(ProgressReport.RECOVERY, ProgressReport.fromRaw("recovery"))
    }

    @Test fun rawValuesMatchIos() {
        assertEquals("totalWorkouts", ProgressMetric.TOTAL_WORKOUTS.rawValue)
        assertEquals("avgCaloriesConsumed", ProgressMetric.AVG_CALORIES_CONSUMED.rawValue)
        assertEquals("hrv", ProgressMetric.HRV.rawValue)
        assertEquals("averageRPE", ProgressMetric.AVERAGE_RPE.rawValue)
    }

    @Test fun quickStatsSanitizeDropsUnknownsAndDuplicatesAndCapsAtSix() {
        assertEquals(ProgressMetric.defaultQuickStats, QuickStatsStore.sanitize(null))
        assertEquals(ProgressMetric.defaultQuickStats, QuickStatsStore.sanitize(listOf("bogus")))
        val raw = listOf("hrv", "hrv", "totalSteps", "bodyWeight", "waist", "hips", "activeGoals", "totalSets")
        val result = QuickStatsStore.sanitize(raw)
        assertEquals(6, result.size)
        assertEquals(ProgressMetric.HRV, result.first())
        assertEquals(result.distinct(), result)
    }

    @Test fun formattingHonoursUnits() {
        WeightUnit.current = WeightUnit.LBS
        assertEquals("1h 5m", ProgressMetric.TOTAL_TIME.format(3900.0))
        assertEquals("1.5k lbs", ProgressMetric.TRAINING_VOLUME.format(1500.0))
        assertEquals("1 day", ProgressMetric.WORKOUT_STREAK.format(1.0))
        assertEquals("+2.0 lbs", ProgressMetric.WEIGHT_CHANGE.format(2.0))
        assertEquals("7.0 / 10", ProgressMetric.AVERAGE_RPE.format(7.0))
    }
}
