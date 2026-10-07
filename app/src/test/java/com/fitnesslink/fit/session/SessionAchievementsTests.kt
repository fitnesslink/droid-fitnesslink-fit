package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.Achievement
import com.fitnesslink.fit.model.AchievementType
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.MovementSetRecord
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.network.dto.AchievementDefinition
import com.fitnesslink.fit.network.dto.MovementHistorySummary
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Port of the pure half of iOS `SessionAchievementsTests` (FTNSSLNK-413). */
class SessionAchievementsTests {

    private val tz: TimeZone = TimeZone.getTimeZone("America/New_York")

    @Before fun setUp() { WeightUnit.current = WeightUnit.LBS; DistanceUnit.current = DistanceUnit.KM }
    @After fun tearDown() { WeightUnit.current = WeightUnit.LBS }

    private fun day(y: Int, m: Int, d: Int, hour: Int = 9): Long = Calendar.getInstance(tz, Locale.US).apply {
        clear(); set(y, m - 1, d, hour, 0, 0)
    }.timeInMillis

    private fun set(weight: Double = 0.0, reps: Int = 0, seconds: Int? = null, km: Double? = null) =
        MovementSetRecord(weight, reps, seconds, km?.let { DistanceValue(it, DistanceUnit.KM) })

    @Test fun testHeaviestWeightPR() {
        val prs = PersonalRecordDetector.detect("sq", "Barbell Squat",
            session = listOf(set(135.0, 10), set(185.0, 5)), prior = listOf(set(175.0, 5), set(155.0, 8)))
        assertEquals(listOf(PersonalRecordKind.HEAVIEST_WEIGHT), prs.map { it.kind })
        assertEquals("🔥 NEW PR · Barbell Squat · 185 lbs", prs[0].line)
    }

    @Test fun testMostRepsAtAWeightLiftedBefore() {
        val prs = PersonalRecordDetector.detect("bp", "Bench Press",
            session = listOf(set(135.0, 12)), prior = listOf(set(135.0, 10), set(155.0, 5)))
        assertEquals(listOf(PersonalRecordKind.MOST_REPS_AT_WEIGHT), prs.map { it.kind })
        assertEquals("12 reps @ 135 lbs", prs[0].valueText)
        val none = PersonalRecordDetector.detect("bp", "Bench Press", listOf(set(135.0, 10)), listOf(set(135.0, 10)))
        assertTrue(none.isEmpty())
    }

    @Test fun testLongestDurationAndDistance() {
        val prs = PersonalRecordDetector.detect("run", "Run", listOf(set(seconds = 1_800, km = 5.2)), listOf(set(seconds = 1_500, km = 5.0)))
        assertEquals(listOf(PersonalRecordKind.LONGEST_DURATION, PersonalRecordKind.LONGEST_DISTANCE), prs.map { it.kind })
        assertEquals("30:00 min", prs[0].valueText)
        assertEquals("5.2 km", prs[1].valueText)
        val miles = PersonalRecordDetector.detect("run", "Run", listOf(set(km = 5.0)), listOf(MovementSetRecord(distance = DistanceValue(3.2, DistanceUnit.MI))))
        assertTrue("3.2 mi ≈ 5.15 km beats 5 km", miles.isEmpty())
    }

    @Test fun testNoPRWithoutPriorHistory() {
        assertTrue(PersonalRecordDetector.detect("sq", "Squat", listOf(set(300.0, 1)), emptyList()).isEmpty())
        assertTrue(PersonalRecordDetector.detect("pl", "Plank", listOf(set(seconds = 60)), listOf(set(0.0, 10))).isEmpty())
    }

    @Test fun testStreakCountsConsecutiveDaysEndingToday() {
        val today = day(2026, 9, 19, hour = 18)
        assertEquals(1, TrainingStreak.days(emptyList(), today, tz))
        assertEquals(3, TrainingStreak.days(listOf(day(2026, 9, 18), day(2026, 9, 17, 22), day(2026, 9, 17, 6)), today, tz))
        assertEquals(1, TrainingStreak.days(listOf(day(2026, 9, 17), day(2026, 9, 16)), today, tz))
    }

    private fun entry(id: String, workout: String, on: Long, status: String = "scheduled") =
        FitnessContent(id = id, title = id, programId = "p", workoutId = workout, mealPlanId = "", status = status, scheduledDate = on)

    @Test fun testProgramWeekAndProgramCompletion() {
        val entries = listOf(
            entry("e1", "w1", day(2026, 9, 14), "completed"),
            entry("e2", "w2", day(2026, 9, 16), "completed"),
            entry("e3", "w3", day(2026, 9, 18)),
            entry("e4", "w1", day(2026, 9, 21)),
            entry("e5", "w2", day(2026, 9, 23))
        )
        assertEquals(ProgramMilestone.WeekComplete(1), ProgramMilestone.evaluate(entries, "w3", day(2026, 9, 18, 20), tz))
        assertNull(ProgramMilestone.evaluate(entries, "w1", day(2026, 9, 21), tz))
        val done = entries.toMutableList()
        done[2] = done[2].copy(status = "completed"); done[3] = done[3].copy(status = "completed")
        assertEquals(ProgramMilestone.ProgramComplete, ProgramMilestone.evaluate(done, "w2", day(2026, 9, 23), tz))
        assertNull(ProgramMilestone.evaluate(entries, "other", day(2026, 9, 18), tz))
        assertEquals("Program milestone · Week 2 complete", ProgramMilestone.WeekComplete(2).text)
    }

    private fun movement(id: String, name: String, sets: Int = 1) =
        WorkoutTask(id = id, name = name, isMovement = true, movementId = "m-$id", sets = sets, reps = 10)

    @Test fun testAssembleOrdersItemsFoldsRecordsAndDedupesTypes() {
        val tasks = listOf(movement("a", "Squat"), movement("b", "Bench"), movement("c", "Row"), movement("d", "Curl"), movement("e", "Plank"))
        val progress = SessionProgress()
        val logs = mutableMapOf<String, Map<Int, TaskSetConfig>>()
        for ((id, weight) in listOf("a" to 200.0, "b" to 150.0, "c" to 120.0, "d" to 40.0)) {
            progress.log(id, 1)
            logs[id] = mapOf(1 to TaskSetConfig(setNumber = 1, reps = 8, weightKg = weight))
        }
        progress.complete("e", 1)
        val rows = SessionHistoryRows.build(tasks, logs, progress, SessionProgress.requiredIterations(tasks))
        val prior = mapOf("m-a" to listOf(set(180.0, 8)), "m-b" to listOf(set(140.0, 8)), "m-c" to listOf(set(110.0, 8)))
        val result = SessionAchievements.assemble(
            rows, { prior[it] ?: emptyList() }, priorCompletedSessions = 0,
            sessionDatesMs = listOf(day(2026, 9, 18)), programEntries = emptyList(), workoutId = "w",
            asOfMs = day(2026, 9, 19), timeZone = tz
        )
        assertEquals(3, result.records.size)
        assertEquals(listOf("Curl"), result.firstTimeLogged.map { it.second })
        assertTrue(result.isFirstWorkout)
        assertEquals(2, result.streakDays)
        assertNull(result.milestone)
        assertEquals(listOf(
            "🔥 NEW PR · Squat · 200 lbs", "🔥 NEW PR · Bench · 150 lbs", "🔥 NEW PR · Row · 120 lbs",
            "First time logged · Curl", "First workout completed", "2-day training streak"
        ), result.items.map { it.text })
        assertEquals(1, result.foldedRecordCount)
        assertEquals(listOf(AchievementType.PersonalRecord, AchievementType.FirstWorkout, AchievementType.StreakMaster), result.achievementTypes)
        assertFalse(result.isEmpty)
    }

    @Test fun testNothingToCelebrateIsEmpty() {
        val task = movement("a", "Squat")
        val progress = SessionProgress()
        progress.log("a", 1)
        val rows = SessionHistoryRows.build(listOf(task), mapOf("a" to mapOf(1 to TaskSetConfig(setNumber = 1, reps = 8, weightKg = 100.0))), progress, mapOf("a" to 1))
        val result = SessionAchievements.assemble(
            rows, { listOf(set(120.0, 8)) }, priorCompletedSessions = 4,
            sessionDatesMs = listOf(day(2026, 9, 10)), programEntries = emptyList(), workoutId = "w",
            asOfMs = day(2026, 9, 19), timeZone = tz
        )
        assertTrue(result.isEmpty)
        assertEquals(emptyList<AchievementType>(), result.achievementTypes)
    }

    @Test fun testServerSummaryBecomesPriorSets() {
        val summary = MovementHistorySummary(movementId = "m", bestWeight = 185.0, bestReps = 5, bestDurationSeconds = 90, bestDistance = 3.0, distanceUnit = "mi")
        val prior = SessionAchievements.priorSets(summary)
        assertEquals(listOf(set(185.0, 5), set(seconds = 90), MovementSetRecord(distance = DistanceValue(3.0, DistanceUnit.MI))), prior)
    }

    @Test fun testUnlockSkipsEarnedTypesAndUnknownDefinitionsAndOffline() = runBlocking {
        val earned = listOf(Achievement(achievementType = AchievementType.FirstWorkout))
        val definitions = listOf(
            AchievementDefinition(id = "def-first", achievementType = "FirstWorkout"),
            AchievementDefinition(id = "def-pr", achievementType = "personalrecord")
        )
        val pending = AchievementUnlocker.pending(listOf(AchievementType.PersonalRecord, AchievementType.FirstWorkout, AchievementType.StreakMaster), earned, definitions)
        assertEquals(listOf("def-pr"), pending.map { it.id })

        val posted = mutableListOf<String>()
        val client = AchievementUnlocker.Client({ earned }, { definitions }, { posted.add(it) })
        assertEquals(listOf("def-pr"), AchievementUnlocker.unlock(listOf(AchievementType.PersonalRecord, AchievementType.FirstWorkout), client, true))
        assertEquals(listOf("def-pr"), posted)

        posted.clear()
        assertEquals(emptyList<String>(), AchievementUnlocker.unlock(listOf(AchievementType.PersonalRecord), client, false))
        assertTrue(posted.isEmpty())

        val failing = AchievementUnlocker.Client({ throw IllegalStateException("down") }, { definitions }, { })
        assertEquals(emptyList<String>(), AchievementUnlocker.unlock(listOf(AchievementType.PersonalRecord), failing, true))
    }
}
