package com.fitnesslink.fit.goals

import com.fitnesslink.fit.model.CustomGoalMetric
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalActivity
import com.fitnesslink.fit.model.GoalConfig
import com.fitnesslink.fit.model.GoalDay
import com.fitnesslink.fit.model.GoalEntry
import com.fitnesslink.fit.model.GoalFrequency
import com.fitnesslink.fit.model.GoalMacroTarget
import com.fitnesslink.fit.model.GoalReminder
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.model.GoalSubtype
import com.fitnesslink.fit.model.GoalTrackingSource
import com.fitnesslink.fit.model.GoalType
import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.MacroKey
import com.fitnesslink.fit.model.WaterUnit
import com.fitnesslink.fit.model.WeightEntry
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.network.dto.NutritionTargets
import com.fitnesslink.fit.ui.navigation.DeepLinkResolver
import com.fitnesslink.fit.viewmodel.GoalSetupEnvironment
import com.fitnesslink.fit.viewmodel.GoalSetupViewModel
import com.fitnesslink.fit.viewmodel.GoalSetupViewModel.Step
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Port of iOS `FitnessLinkTests/GoalsPRDTests.swift` (provisional strength
 * formulas, target validation, the definition round-trip, the goal deep
 * link) plus engine coverage for the rules behind each goal family.
 */
class GoalsPRDTests {

    private val today: LocalDate = LocalDate.of(2026, 10, 28) // a Wednesday

    @Before
    fun fixCalendar() {
        GoalDay.zone = ZoneId.of("UTC")
        GoalDay.firstDayOfWeek = DayOfWeek.SUNDAY
    }

    private fun millis(day: LocalDate, hour: Int = 12) = GoalDay.startOfDayMillis(day) + hour * 3_600_000L

    // MARK: Strength (provisional rules)

    @Test fun epleyEstimateUsesOneToTwelveReps() {
        assertEquals(200.0, StrengthMetrics.estimatedOneRepMax(200.0, 1)!!, 0.0)
        assertEquals(200.0, StrengthMetrics.estimatedOneRepMax(150.0, 10)!!, 0.001)
        assertNull("High-rep sets aren't used for an estimate", StrengthMetrics.estimatedOneRepMax(100.0, 13))
        assertNull(StrengthMetrics.estimatedOneRepMax(0.0, 5))
    }

    @Test fun repsAtWeightOnlyMatchesTheSameLoad() {
        assertTrue(StrengthMetrics.sameLoad(95.0, 95.4))
        assertFalse(StrengthMetrics.sameLoad(100.0, 95.0))
    }

    // MARK: Validation

    @Test fun loseWeightTargetMustBeADecrease() {
        val model = GoalSetupViewModel(GoalType.BodyComposition, FakeEnv(), today)
        model.baselineText = "200"
        model.targetText = "210"
        assertNotNull(model.targetError)
        model.targetText = "180"
        assertNull(model.targetError)
    }

    @Test fun repsTargetMustExceedBaseline() {
        val model = GoalSetupViewModel(GoalType.Performance, FakeEnv(), today)
        model.select(GoalSubtype.RepsAtWeight)
        model.baselineText = "8"
        model.targetText = "8"
        assertNotNull(model.targetError)
        model.targetText = "12"
        assertNull(model.targetError)
    }

    @Test fun loseWeightFlowHasNoTypeStepAndEndsWithReview() {
        val model = GoalSetupViewModel(GoalType.BodyComposition, FakeEnv(), today)
        assertEquals(listOf(Step.CATEGORY, Step.BASELINE, Step.TARGET, Step.TIME_FRAME, Step.TRACKING, Step.REVIEW, Step.CONFIRMATION), model.flowSteps)
    }

    @Test fun recommendationIsAnEstimateFromThePace() {
        val model = GoalSetupViewModel(GoalType.BodyComposition, FakeEnv(), today)
        model.weightUnit = WeightUnit.LBS
        model.baselineText = "200"
        model.targetText = "180"
        // 0.5% of 200 lb = 1 lb/week → 20 weeks.
        assertEquals(20, model.recommendation.weeks)
        assertTrue(model.recommendation.basis.contains("estimate"))
    }

    /** The Sources screen states this pace and its limits, so the copy has to stay in step. */
    @Test fun sourcesCopyMatchesTheWeightLossPace() {
        val model = GoalSetupViewModel(GoalType.BodyComposition, FakeEnv(), today)
        model.weightUnit = WeightUnit.LBS

        // 0.5% of 400 lb = 2 lb/week, limited to 1.5 → 15 lb in 10 weeks.
        model.baselineText = "400"
        model.targetText = "385"
        assertEquals(10, model.recommendation.weeks)

        // 0.5% of 80 lb = 0.4 lb/week, raised to 0.5 → 10 lb in 20 weeks.
        model.baselineText = "80"
        model.targetText = "70"
        assertEquals(20, model.recommendation.weeks)

        val method = com.fitnesslink.fit.model.NutritionMethod.all.first { it.title == "Weight-loss goal pace" }
        assertTrue(method.explanation.contains("0.5% of your body weight"))
        assertTrue(method.explanation.contains("between 0.5 and 1.5 lb"))
        assertTrue(method.explanation.contains("about 1% of your body weight"))
        assertEquals(listOf(com.fitnesslink.fit.model.NutritionCitation.CDC_LOSING_WEIGHT), method.citations)
    }

    @Test fun builtGoalCarriesDefinitionAndStableId() {
        val model = GoalSetupViewModel(GoalType.BodyComposition, FakeEnv(), today)
        model.weightUnit = WeightUnit.LBS
        model.baselineText = "200"
        model.targetText = "180"
        val first = model.buildGoal()
        val second = model.buildGoal()
        assertEquals("One id per draft, so a repeated create can't duplicate", first.id, second.id)
        assertEquals(GoalSubtype.LoseWeight, first.subtype)
        assertEquals(200.0, first.baselineValue!!, 0.0)
        assertEquals(180.0, first.targetValue!!, 0.0)
        assertEquals("lbs", first.settings.unit)
    }

    @Test fun calorieTargetUnder1000IsRejected() {
        val model = GoalSetupViewModel(GoalType.Nutrition, FakeEnv(), today)
        model.select(GoalSubtype.CalorieTarget)
        model.targetText = "900"
        assertNotNull(model.targetError)
        model.targetText = "1800"
        assertNull(model.targetError)
    }

    @Test fun nutritionPrefillIsReadableBack() {
        // GoalFormat.number groups thousands ("2,000"), which a text field can't parse back.
        val env = FakeEnv(targets = NutritionTargets(dailyCalorieTarget = 2150.0, proteinGrams = 160.0, carbGrams = 220.0, fatGrams = 70.0))
        val model = GoalSetupViewModel(GoalType.Nutrition, env, today)
        model.select(GoalSubtype.CalorieTarget)
        assertEquals("2150", model.targetText)
    }

    @Test fun customRoutineSumsActivitiesIntoTheWeeklyTarget() {
        val model = GoalSetupViewModel(GoalType.Consistency, FakeEnv(), today)
        model.select(GoalSubtype.CustomRoutine)
        model.title = "My week"
        model.activities.add(GoalActivity(name = "Strength", kind = GoalActivity.Kind.WORKOUT, perWeek = 3))
        model.activities.add(GoalActivity(name = "Mobility", kind = GoalActivity.Kind.MOVEMENT, perWeek = 2))
        val goal = model.buildGoal()
        assertEquals(5, goal.frequencyTarget)
        assertEquals(GoalTrackingSource.WorkoutLogs, goal.trackingSource)
        assertEquals(2, goal.settings.activities?.size)
    }

    // MARK: Model

    @Test fun configRoundTripsThroughGoalJson() {
        val goal = Goal().withSettings(GoalConfig(
            macros = listOf(GoalMacroTarget(MacroKey.PROTEIN, 140.0)),
            reminder = GoalReminder(true, 8, 30, listOf(2, 4))
        ))
        assertEquals(140.0, goal.settings.macros?.first()?.grams!!, 0.0)
        assertEquals(listOf(2, 4), goal.settings.reminder?.weekdays)
        assertTrue("iOS raw values on the wire", goal.config!!.contains("\"protein\""))
    }

    @Test fun configWrittenByIOSDecodes() {
        val json = """{"activities":[{"id":"a1","kind":"movement","name":"Run","perWeek":3}],"customMetric":"duration","recoveryMetric":"hrv","unit":"min"}"""
        val settings = Goal(config = json).settings
        assertEquals(GoalActivity.Kind.MOVEMENT, settings.activities?.first()?.kind)
        assertEquals(com.fitnesslink.fit.model.CustomGoalMetric.DURATION, settings.customMetric)
        assertEquals(com.fitnesslink.fit.model.RecoveryMetric.HRV, settings.recoveryMetric)
    }

    @Test fun goalDecodesServerDefinitionFields() {
        val json = """{"id":"ABC","goalType":"Performance","title":"Squat","subtype":"ImproveLift","metricKey":"e1rm",
            "baselineValue":185,"frequency":"Weekly","trackingSource":"WorkoutLogs","isPinned":true,"config":"{}"}"""
        val goal = Gson().fromJson(json, Goal::class.java)
        assertEquals(GoalSubtype.ImproveLift, goal.subtype)
        assertEquals(185.0, goal.baselineValue!!, 0.0)
        assertEquals(GoalTrackingSource.WorkoutLogs, goal.trackingSource)
        assertTrue(goal.isPinned)
    }

    @Test fun unknownServerEnumsDecodeToNull() {
        val json = """{"id":"x","goalType":"Custom","title":"T","subtype":"FutureThing","frequency":"Hourly"}"""
        val goal = Gson().fromJson(json, Goal::class.java)
        assertNull(goal.subtype)
        assertNull(goal.frequency)
    }

    @Test fun goalEntryDecodesServerShape() {
        val json = """{"id":"E1","goalId":"G1","entryDate":"2026-10-27","value":30,"source":"Manual","createdAtUtc":"2026-10-27T10:00:00Z"}"""
        val entry = Gson().fromJson(json, GoalEntry::class.java)
        assertEquals("e1", entry.id)
        assertEquals("g1", entry.goalId)
        assertEquals(LocalDate.of(2026, 10, 27), entry.day)
    }

    @Test fun missedDeadlineKeepsGoalActive() {
        val goal = Goal(subtype = GoalSubtype.LoseWeight, targetDate = millis(today.minusDays(3)))
        assertEquals(GoalPhase.DEADLINE_PASSED, goal.phase(null, today))
        assertEquals(GoalStatus.Active, goal.status)
    }

    @Test fun goalDeepLinkOpensDetail() {
        assertEquals("goalDetail/abc", DeepLinkResolver.resolve("goals/abc"))
        assertEquals("goals", DeepLinkResolver.resolve("goals"))
    }

    // MARK: Engine

    @Test fun weightProgressUsesEntriesSinceBaseline() {
        val data = FakeData(weights = listOf(
            GoalWeightPoint("w0", millis(today.minusDays(20)), 205.0, WeightUnit.LBS), // before baseline: ignored
            GoalWeightPoint("w1", millis(today.minusDays(7)), 196.0, WeightUnit.LBS),
            GoalWeightPoint("w2", millis(today.minusDays(1)), 190.0, WeightUnit.LBS)
        ))
        val goal = Goal(subtype = GoalSubtype.LoseWeight, startDate = millis(today.minusDays(10)), baselineValue = 200.0,
            baselineDate = millis(today.minusDays(10)), targetValue = 180.0).withSettings(GoalConfig(unit = "lbs"))
        val p = GoalProgressEngine.progress(goal, data, today)
        assertEquals(0.5, p.fraction!!, 0.0001)
        assertFalse(p.targetReached)
        assertNotNull("two points → trend chart", p.chart)
        assertEquals(2, p.recent.size)
    }

    @Test fun weightGoalWithoutDataStaysMissingNotZero() {
        val goal = Goal(subtype = GoalSubtype.LoseWeight, startDate = millis(today), baselineValue = 200.0, targetValue = 180.0)
        val p = GoalProgressEngine.progress(goal, FakeData(), today)
        assertFalse(p.hasData)
        assertNull(p.fraction)
        assertEquals(GoalAction.LogWeight, p.action)
    }

    @Test fun workoutConsistencyCountsSessionsThisWeekOnce() {
        val sunday = GoalDay.weekStart(today)
        val data = FakeData(sessions = listOf(
            GoalSessionRecord("s1", "A", millis(sunday), null),
            GoalSessionRecord("S1", "A", millis(sunday), null), // duplicate id (case) counts once
            GoalSessionRecord("s2", "B", millis(today), null)
        ))
        val goal = Goal(subtype = GoalSubtype.WorkoutConsistency, startDate = millis(sunday.minusDays(14)),
            frequency = GoalFrequency.Weekly, frequencyTarget = 3)
        val p = GoalProgressEngine.progress(goal, data, today)
        assertEquals("2 of 3 workouts this week", p.summary)
        assertEquals("1 workouts remaining this week", p.detail)
        assertEquals(3, p.weeks.size)
    }

    @Test fun personalRecordUsesAllTimeBestAndEstimatedLabel() {
        val sets = listOf(
            GoalSetRecord("s1", "Squat", millis(today.minusDays(60)), 5, 200.0),
            GoalSetRecord("s2", "Squat", millis(today.minusDays(2)), 3, 210.0)
        )
        val goal = Goal(subtype = GoalSubtype.PersonalRecord, startDate = millis(today.minusDays(7)), metricKey = "e1rm",
            baselineValue = 225.0, targetValue = 250.0).withSettings(GoalConfig(unit = "lbs", movementName = "Squat"))
        val p = GoalProgressEngine.progress(goal, FakeData(sets = sets), today)
        // 200 × (1 + 5/30) = 233.3 beats 210 × 1.1 = 231
        assertTrue(p.summary, p.summary.contains("(estimated)"))
        assertTrue(p.summary.startsWith("233.3"))
    }

    @Test fun hydrationDayWithoutWaterIsNoData() {
        val data = FakeData(water = mapOf(today.minusDays(1) to 90.0, today to 40.0))
        val goal = Goal(subtype = GoalSubtype.Hydration, startDate = millis(today.minusDays(3)), frequency = GoalFrequency.Daily, frequencyTarget = 7)
        val p = GoalProgressEngine.progress(goal, data, today)
        assertEquals(0.5, p.fraction!!, 0.0001)
        val week = p.weeks.last()
        assertEquals(1, week.completed)
        assertEquals("days with no water aren't counted as data", 2, week.daysWithData)
    }

    @Test fun dailyHabitCountsDistinctCheckInDays() {
        val goal = Goal(id = "g", subtype = GoalSubtype.DailyHabit, startDate = millis(GoalDay.weekStart(today)),
            frequency = GoalFrequency.Weekly, frequencyTarget = 5)
        val entries = listOf(
            GoalEntry(goalId = "g", entryDate = GoalDay.key(today)),
            GoalEntry(goalId = "g", entryDate = GoalDay.key(today)),
            GoalEntry(goalId = "g", entryDate = GoalDay.key(today.minusDays(1)))
        )
        val p = GoalProgressEngine.progress(goal, FakeData(entries = entries), today)
        assertEquals("2 of 5 days this week", p.summary)
        assertTrue(p.stats.any { it.label == "Today" && it.value == "Completed" })
    }

    @Test fun calorieGoalReadsTheLiveNutritionTarget() {
        val data = FakeData(targets = GoalNutritionTargets(calories = 2000), nutrition = listOf(GoalNutritionDay(today, 1950.0, 120.0, 200.0, 60.0)))
        val goal = Goal(subtype = GoalSubtype.CalorieTarget, startDate = millis(today.minusDays(2)), targetValue = 2500.0,
            frequency = GoalFrequency.Daily, frequencyTarget = 7)
        val p = GoalProgressEngine.progress(goal, data, today)
        assertEquals("In your target range", p.detail)
    }

    @Test fun customRoutineAssignsEachWorkoutOnce() {
        val activities = listOf(
            GoalActivity(id = "a", name = "Strength", kind = GoalActivity.Kind.WORKOUT, perWeek = 1),
            GoalActivity(id = "b", name = "Cardio", kind = GoalActivity.Kind.WORKOUT, perWeek = 2),
            GoalActivity(id = "c", name = "Stretch", kind = GoalActivity.Kind.MOVEMENT, perWeek = 2)
        )
        val goal = Goal(id = "g", subtype = GoalSubtype.CustomRoutine, startDate = millis(today.minusDays(30)))
            .withSettings(GoalConfig(activities = activities))
        val data = FakeData(
            sessions = listOf(GoalSessionRecord("s1", "W", millis(today), null), GoalSessionRecord("s2", "W", millis(today.minusDays(1)), null)),
            entries = listOf(GoalEntry(goalId = "g", entryDate = GoalDay.key(today), activityKey = "c"))
        )
        val p = GoalProgressEngine.progress(goal, data, today)
        assertEquals(listOf(1, 1, 1), p.activities.map { it.completed })
        assertEquals("3 of 5 activities this week", p.summary)
    }

    @Test fun legacyGoalKeepsStoredValue() {
        val p = GoalProgressEngine.progress(Goal(targetValue = 10.0, currentValue = 4.0, targetUnit = "km"), FakeData(), today)
        assertEquals(0.4, p.fraction!!, 0.0001)
    }

    @Test fun reminderNextOccurrenceUsesSundayFirstWeekdays() {
        val now = java.time.LocalDateTime.of(2026, 10, 28, 20, 0) // Wednesday 8 pm
        assertEquals(java.time.LocalDateTime.of(2026, 10, 29, 19, 0), GoalReminderScheduler.nextOccurrence(now, 19, 0, 0))
        // 1 = Sunday
        assertEquals(java.time.LocalDateTime.of(2026, 11, 1, 8, 30), GoalReminderScheduler.nextOccurrence(now, 8, 30, 1))
    }

    // MARK: Health Connect

    @Test fun healthSleepGoalOffersConnectWhenNotConnected() {
        val goal = Goal(subtype = GoalSubtype.SleepDuration, trackingSource = GoalTrackingSource.AppleHealth,
            targetValue = 420.0, startDate = millis(today.minusDays(5)))
        val p = GoalProgressEngine.progress(goal, FakeData(), today)
        assertEquals(GoalAction.ConnectHealth, p.action)
        assertTrue(p.notes.any { it.startsWith("Health Connect isn't connected") })
    }

    @Test fun healthSleepGoalReadsHealthConnectNights() {
        val goal = Goal(subtype = GoalSubtype.SleepDuration, trackingSource = GoalTrackingSource.AppleHealth,
            targetValue = 420.0, startDate = millis(today.minusDays(5)))
        val data = FakeData(healthConnected = true, sleep = mapOf(today.minusDays(1) to 6 * 3600.0, today to 7.5 * 3600.0))
        val p = GoalProgressEngine.progress(goal, data, today)
        assertEquals(GoalAction.LogSleep, p.action)
        assertEquals("7 h 30 min of 7 h target", p.summary)
        assertEquals("Health Connect", p.recent.first().detail?.substringBefore(" ·"))
    }

    @Test fun stepGoalNeedsConnection() {
        val goal = Goal(subtype = GoalSubtype.CustomGoal, trackingSource = GoalTrackingSource.AppleHealth, targetValue = 8000.0,
            startDate = millis(today.minusDays(2))).withSettings(GoalConfig(customMetric = CustomGoalMetric.STEPS))
        val p = GoalProgressEngine.progress(goal, FakeData(), today)
        assertEquals(GoalAction.ConnectHealth, p.action)
        assertEquals("Connect Health Connect to track steps", p.detail)
    }

    // MARK: Fakes

    private class FakeData(
        val weights: List<GoalWeightPoint> = emptyList(),
        val sets: List<GoalSetRecord> = emptyList(),
        val targets: GoalNutritionTargets = GoalNutritionTargets(),
        val nutrition: List<GoalNutritionDay> = emptyList(),
        val water: Map<LocalDate, Double> = emptyMap(),
        val sessions: List<GoalSessionRecord> = emptyList(),
        val entries: List<GoalEntry> = emptyList(),
        override val healthConnected: Boolean = false,
        val sleep: Map<LocalDate, Double> = emptyMap()
    ) : GoalDataSource {
        override fun sleepByNight(from: LocalDate, to: LocalDate) = sleep.filterKeys { !it.isBefore(from) && !it.isAfter(to) }
        override fun weightEntries() = weights
        override fun setRecords(movementName: String?) =
            sets.filter { movementName == null || it.exerciseName.equals(movementName.trim(), ignoreCase = true) }.sortedBy { it.date }
        override fun nutritionTargets() = targets
        override fun nutritionDays(since: LocalDate) = nutrition.filter { !it.day.isBefore(since) }
        override fun foodItems(day: LocalDate) = emptyList<GoalFoodItem>()
        override fun hydrationGoal() = HydrationGoal(dailyGoal = 80.0, unit = WaterUnit.OZ)
        override fun waterTotal(day: LocalDate, unit: WaterUnit) = water[day] ?: 0.0
        override fun completedSessions(since: LocalDate) = sessions.filter { !GoalDay.localDate(it.date).isBefore(since) }
        override fun goalEntries(goalId: String) = entries.filter { it.goalId == goalId }.sortedByDescending { it.entryDate }
    }

    private class FakeEnv(val targets: NutritionTargets? = null) : GoalSetupEnvironment {
        override fun weightEntries() = listOf(WeightEntry(weight = 200.0, unit = WeightUnit.LBS, date = Date(0)))
        override fun hydrationGoal() = HydrationGoal(dailyGoal = 64.0, unit = WaterUnit.OZ)
        override fun hasPinnedGoal() = false
        override fun setRecords(movementName: String?) = emptyList<GoalSetRecord>()
        override fun nutritionGoal() = GoalNutritionTargets(2000, 150, 250, 65)
        override fun nutritionTargets() = targets
        override fun hasNutritionProfile() = targets != null
        override fun nutritionDays(since: LocalDate) = emptyList<GoalNutritionDay>()
        override fun activeProgram(): Pair<String, String>? = null
        override val healthConnected = false
        override val isOnline = true
        override suspend fun overrideNutritionTargets(calories: Int, protein: Double, fat: Double, carbs: Double) {}
        override suspend fun saveHydrationGoal(goal: HydrationGoal) {}
        override suspend fun createGoal(goal: Goal) {}
    }
}
