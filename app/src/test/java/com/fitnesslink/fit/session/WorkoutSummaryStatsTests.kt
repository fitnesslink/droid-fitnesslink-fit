package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.SessionExerciseRow
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/** Port of iOS `WorkoutSummaryStatsTests` (FTNSSLNK-412). */
class WorkoutSummaryStatsTests {

    @Before fun setUp() { WeightUnit.current = WeightUnit.LBS }
    @After fun tearDown() { WeightUnit.current = WeightUnit.LBS }

    private fun movement(
        id: String, name: String? = null, sets: Int = 3, reps: Int = 10, weight: Int? = null,
        usesDuration: Boolean = false, duration: Int? = null, groupId: String = "", rounds: Int? = null
    ) = WorkoutTask(
        id = id, name = name ?: "Movement $id", isMovement = true, movementId = "m-$id", sets = sets, reps = reps,
        weightKg = weight, usesDuration = usesDuration, durationSeconds = duration, groupId = groupId, rounds = rounds
    )

    private fun restTask(id: String) = WorkoutTask(id = id, name = "Rest", isRest = true, restSeconds = 60)

    private data class Fixture(
        val tasks: List<WorkoutTask>, val logs: Map<String, Map<Int, TaskSetConfig>>,
        val progress: SessionProgress, val required: Map<String, Int>
    )

    /** Squat 3 × 10 @ 135 (all logged), Push-Up 3 × 12 bodyweight (2 by Next, 1 skipped), Plank 2 × 30 s skipped. */
    private fun fixture(): Fixture {
        val squat = movement("sq", name = "Barbell Squat", sets = 3, reps = 10, weight = 135)
        val push = movement("pu", name = "Push-Up", sets = 3, reps = 12)
        val plank = movement("pl", name = "Plank", sets = 2, reps = 0, usesDuration = true, duration = 30)
        val tasks = listOf(squat, restTask("r1"), push, plank)
        val required = SessionProgress.requiredIterations(tasks)
        val progress = SessionProgress()
        val logs = mapOf("sq" to mapOf(
            1 to TaskSetConfig(setNumber = 1, reps = 10, weightKg = 135.0),
            2 to TaskSetConfig(setNumber = 2, reps = 10, weightKg = 135.0),
            3 to TaskSetConfig(setNumber = 3, reps = 8, weightKg = 145.0)
        ))
        for (i in 1..3) progress.log("sq", i)
        progress.complete("pu", 1)
        progress.complete("pu", 2)
        progress.skipSet("pu", 3)
        progress.skipRemaining("pl", 1, 2)
        return Fixture(tasks, logs, progress, required)
    }

    @Test fun testExercisesCountOnlyFullyCompletedOnesAndSetsCountCompletedState() {
        val f = fixture()
        val stats = WorkoutSummaryStats.build(f.tasks, f.logs, f.progress, f.required, 1_930)
        assertEquals(1, stats.exercisesCompleted)
        assertEquals(5, stats.setsCompleted)
        assertEquals(10 + 10 + 8 + 12 + 12, stats.totalReps)
        assertEquals(1_930, stats.durationSeconds)
        assertEquals("00:32:10", stats.durationText)
        assertEquals(63, stats.completionPercent)
        assertEquals("Completed · 63%", stats.completionText)
    }

    @Test fun testTotalVolumeSumsWeightTimesRepsInTheDisplayUnit() {
        val f = fixture()
        val stats = WorkoutSummaryStats.build(f.tasks, f.logs, f.progress, f.required, 60)
        assertEquals((135 * 10 + 135 * 10 + 145 * 8).toDouble(), stats.totalVolumeLbs!!, 0.001)
        val text = stats.volumeText
        assertNotNull(text)
        assertTrue(text!!.endsWith(" lbs"))
        assertTrue(text.contains("3") && text.contains("860"))
        assertNotEquals("3860 lbs", text)

        WeightUnit.current = WeightUnit.KG
        val kg = WorkoutSummaryStats.formatVolume(3_860.0)
        assertTrue(kg.endsWith(" kg"))
        assertTrue(kg, kg.contains("751"))
    }

    @Test fun testTotalVolumeIsHiddenWhenNoWeightedSetWasRecorded() {
        val push = movement("pu", name = "Push-Up", sets = 2, reps = 12)
        val progress = SessionProgress()
        progress.complete("pu", 1)
        progress.log("pu", 2)
        val logs = mapOf("pu" to mapOf(2 to TaskSetConfig(setNumber = 2, reps = 15, weightKg = null)))
        val stats = WorkoutSummaryStats.build(listOf(push), logs, progress, mapOf("pu" to 2), 300)
        assertNull(stats.totalVolumeLbs)
        assertNull(stats.volumeText)
        assertEquals(27, stats.totalReps)
        assertNull(stats.completionText)
    }

    @Test fun testMissedListsSkippedAndPartialExercisesInWorkoutOrder() {
        val f = fixture()
        val stats = WorkoutSummaryStats.build(f.tasks, f.logs, f.progress, f.required, 60)
        val missed = stats.missed!!
        assertEquals(listOf("Push-Up", "Plank"), missed.map { it.name })
        assertEquals("2 of 3 sets", missed[0].detail)
        assertEquals("Skipped", missed[1].detail)
        assertFalse(stats.isFullyCompleted)
    }

    @Test fun testAllCompletedReadsAsSuch() {
        val squat = movement("sq", sets = 2, reps = 5, weight = 225)
        val progress = SessionProgress()
        progress.completeExercise("sq", 2)
        val stats = WorkoutSummaryStats.build(listOf(squat), emptyMap(), progress, mapOf("sq" to 2), 60)
        assertEquals(emptyList<MissedExercise>(), stats.missed)
        assertTrue(stats.isFullyCompleted)
        assertEquals("All exercises completed ✓", WorkoutSummaryStats.allCompletedText)
        assertEquals(1, stats.exercisesCompleted)
        assertEquals((225 * 5 * 2).toDouble(), stats.totalVolumeLbs!!, 0.001)
    }

    @Test fun testGroupedMembersUseRoundsAsRequiredSets() {
        val a = movement("a", name = "A1", sets = 1, reps = 10, groupId = "g", rounds = 3)
        val b = movement("b", name = "A2", sets = 1, reps = 10, groupId = "g")
        val tasks = listOf(a, b)
        val required = SessionProgress.requiredIterations(tasks)
        val progress = SessionProgress()
        for (r in 1..3) progress.complete("a", r)
        progress.complete("b", 1)
        progress.skipRemaining("b", 2, 3)
        val stats = WorkoutSummaryStats.build(tasks, emptyMap(), progress, required, 60)
        assertEquals(1, stats.exercisesCompleted)
        assertEquals(4, stats.setsCompleted)
        assertEquals(listOf("1 of 3 sets"), stats.missed?.map { it.detail })
    }

    @Test fun testDistanceStatOnlyWhenLogged() {
        val run = movement("run", name = "Run", sets = 1, reps = 0, usesDuration = true, duration = 600)
        val progress = SessionProgress()
        progress.log("run", 1, distance = 2.5, distanceUnit = "km")
        val none = WorkoutSummaryStats.build(listOf(run), emptyMap(), progress, mapOf("run" to 1), 600)
        assertNull(none.distanceText)
        val some = WorkoutSummaryStats.build(listOf(run), emptyMap(), progress, mapOf("run" to 1), 600, DistanceValue(2.5, DistanceUnit.KM))
        assertEquals("2.5 km", some.distanceText)
    }

    @Test fun testStoredSessionTotalsAreTheSummaryNumbers() {
        val f = fixture()
        val live = WorkoutSummaryStats.build(f.tasks, f.logs, f.progress, f.required, 1_930)
        val rows = SessionHistoryRows.build(f.tasks, f.logs, f.progress, f.required)
        val stored = WorkoutSummaryStats.build(f.tasks, rows, f.required, 1_930, f.progress.completionPercent(f.required))
        assertEquals(live, stored)

        val row = SavedSessionRow(
            id = "s", workoutId = "w", programId = null, workoutName = "W", startDateMs = 0, completionDateMs = 0,
            durationSeconds = 1_930, isCompleted = true, exerciseCount = stored.exercisesCompleted,
            totalSets = stored.setsCompleted, totalReps = stored.totalReps, totalWeightLifted = stored.totalVolumeLbs ?: 0.0,
            totalCaloriesBurned = 0.0, rpeValue = null, rpeEditedAtMs = null, completionPercent = stored.completionPercent,
            progressJson = f.progress.encodedJSON()
        )
        val reopened = WorkoutSummaryStats.saved(row, emptyList(), f.tasks, SessionProgress.decode(f.progress.encodedJSON()), f.logs, null)
        assertEquals(live, reopened)

        val legacy = WorkoutSummaryStats.saved(
            row, listOf(SessionExerciseRow("Barbell Squat", 10, 1, 135.0, Date())), null, null, emptyMap(), null
        )
        assertEquals(1, legacy.exercisesCompleted)
        assertEquals(5, legacy.setsCompleted)
        assertEquals(1_350.0, legacy.totalVolumeLbs!!, 0.001)
        assertNull(legacy.missed)
        assertEquals("Completed · 63%", legacy.completionText)
    }
}
