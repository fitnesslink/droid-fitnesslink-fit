package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.SessionExerciseRow
import com.fitnesslink.fit.model.SessionOverrides
import com.fitnesslink.fit.model.SessionSetOverride
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.viewmodel.PlaylistEditRequest
import com.fitnesslink.fit.viewmodel.SessionEditAction
import com.fitnesslink.fit.viewmodel.SessionWorkoutEditor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * Session half of iOS 23ef55d (Oct 10 sprint, FTNSSLNK-497/505/512/515/516): working-set volume,
 * timed set overrides and the in-session edit rules.
 */
class Sprint1010SessionPortTests {

    @Before fun setUp() { WeightUnit.current = WeightUnit.LBS }
    @After fun tearDown() { WeightUnit.current = WeightUnit.LBS }

    private fun movement(id: String, name: String, sets: Int, reps: Int, weight: Int?, phase: String) = WorkoutTask(
        id = id, name = name, isMovement = true, movementId = "m-$id", sets = sets, reps = reps, weightKg = weight, phaseName = phase
    )

    // MARK: - Working-set volume (FTNSSLNK-497)

    private data class Fixture(
        val tasks: List<WorkoutTask>, val logs: Map<String, Map<Int, TaskSetConfig>>,
        val progress: SessionProgress, val required: Map<String, Int>
    )

    /**
     * Bench: set 1 is a flagged warm-up (10 × 45), working sets 10 × 60 + 8 × 65 + 8 × 65 = 1,640.
     * A loaded Warm-Up phase task and a loaded Cool Down phase task add nothing either.
     */
    private fun fixture(): Fixture {
        val warm = movement("gs", "Goblet Squat", 2, 10, 25, "Warmup")
        val bench = movement("bp", "Bench Press", 4, 10, 60, "Exercises").copy(setConfigs = listOf(
            TaskSetConfig(setNumber = 1, reps = 10, weightKg = 45.0, isWarmup = true),
            TaskSetConfig(setNumber = 2, reps = 10, weightKg = 60.0),
            TaskSetConfig(setNumber = 3, reps = 8, weightKg = 65.0),
            TaskSetConfig(setNumber = 4, reps = 8, weightKg = 65.0)
        ))
        val cool = movement("kb", "Kettlebell Swing", 1, 15, 20, "Cool Down")
        val tasks = listOf(warm, bench, cool)
        val required = SessionProgress.requiredIterations(tasks)
        val progress = SessionProgress()
        val logs = mapOf("bp" to mapOf(
            1 to TaskSetConfig(setNumber = 1, reps = 10, weightKg = 45.0),
            2 to TaskSetConfig(setNumber = 2, reps = 10, weightKg = 60.0),
            3 to TaskSetConfig(setNumber = 3, reps = 8, weightKg = 65.0),
            4 to TaskSetConfig(setNumber = 4, reps = 8, weightKg = 65.0)
        ))
        for (i in 1..4) progress.log("bp", i)
        progress.complete("gs", 1); progress.complete("gs", 2); progress.complete("kb", 1)
        return Fixture(tasks, logs, progress, required)
    }

    @Test fun totalVolumeCountsOnlyWorkingSets() {
        val f = fixture()
        val stats = WorkoutSummaryStats.build(f.tasks, f.logs, f.progress, f.required, 600)
        assertEquals(1_640.0, stats.totalVolumeLbs!!, 0.001)
        assertEquals("excluded sets still count as completed sets", 7, stats.setsCompleted)

        val rows = SessionHistoryRows.build(f.tasks, f.logs, f.progress, f.required)
        val stored = WorkoutSummaryStats.build(f.tasks, rows, f.required, 600, 100)
        assertEquals("SessionRecorder stores the same figure", 1_640.0, stored.totalVolumeLbs!!, 0.001)
    }

    @Test fun savedFallbackExcludesWarmupAndCooldownByName() {
        val f = fixture()
        val row = SavedSessionRow(
            id = "s", workoutId = "w", programId = null, workoutName = "W", startDateMs = 0, completionDateMs = 0,
            durationSeconds = 600, isCompleted = true, exerciseCount = 3, totalSets = 7, totalReps = 0,
            totalWeightLifted = 1_640.0, totalCaloriesBurned = 0.0, rpeValue = null, rpeEditedAtMs = null,
            completionPercent = 100, progressJson = null
        )
        val now = Date()
        val exercises = listOf(
            SessionExerciseRow("Goblet Squat", 10, 1, 25.0, now),
            SessionExerciseRow("Goblet Squat", 10, 2, 25.0, now),
            SessionExerciseRow("Bench Press", 10, 1, 45.0, now),
            SessionExerciseRow("Bench Press", 10, 2, 60.0, now),
            SessionExerciseRow("Bench Press", 8, 3, 65.0, now),
            SessionExerciseRow("Bench Press", 8, 4, 65.0, now),
            SessionExerciseRow("Kettlebell Swing", 15, 1, 20.0, now)
        )
        val stats = WorkoutSummaryStats.saved(row, exercises, f.tasks, null, emptyMap(), null)
        assertEquals(1_640.0, stats.totalVolumeLbs!!, 0.001)
    }

    @Test fun exclusionsMatchPhaseNameSpellingsAndWarmupFlags() {
        assertTrue(WorkingSetVolume.isExcludedPhase("Warmup"))
        assertTrue(WorkingSetVolume.isExcludedPhase("Warm-Up"))
        assertTrue(WorkingSetVolume.isExcludedPhase(" cool down "))
        assertTrue(WorkingSetVolume.isExcludedPhase("Cooldown"))
        assertFalse(WorkingSetVolume.isExcludedPhase("Exercises"))
        assertFalse(WorkingSetVolume.isExcludedPhase("Finisher"))

        val exclusions = WorkingSetVolume.Exclusions(fixture().tasks)
        assertTrue("ids match case-insensitively", exclusions.excludes("GS", 1))
        assertTrue("flagged warm-up set", exclusions.excludes("bp", 1))
        assertFalse(exclusions.excludes("bp", 2))
        assertTrue(exclusions.excludes("kb", 1))
        assertFalse(exclusions.excludes("unknown", 1))
    }

    @Test fun volumeExclusionCacheExcludesNothingForAnUncachedWorkout() {
        val tasks = fixture().tasks
        val cache = com.fitnesslink.fit.persistence.VolumeExclusionCache { id -> if (id.equals("W1", true)) tasks else null }
        assertTrue(cache.excludes("w1", "bp", 1))
        assertFalse(cache.excludes("w1", "bp", 2))
        assertFalse("uncached workout", cache.excludes("other", "bp", 1))
        assertFalse("no task id", cache.excludes("w1", "", 1))
    }

    // MARK: - Timed set overrides (FTNSSLNK-512)

    @Test fun durationOverrideSetsTimeForEverySetAndKeepsNoReps() {
        val task = movement("p", "Plank", 2, 0, null, "Exercises").copy(usesDuration = true, durationSeconds = 30)
        val out = SessionSetOverride(sets = 3, reps = 0, durationSeconds = 45).apply(task)
        assertEquals(3, out.sets)
        assertEquals(45, out.durationSeconds)
        assertEquals("3 x 0:45", out.metric)
        assertEquals(3, out.setConfigs.size)
        assertTrue(out.setConfigs.all { it.durationSeconds == 45 && it.reps == 0 })
    }

    @Test fun durationOverrideWithLoadWritesWeightAndMetric() {
        val task = movement("c", "Farmer Carry", 2, 0, 40, "Exercises").copy(usesDuration = true, durationSeconds = 60)
        val out = SessionOverrides.applyDuration(task, 2, 90, 50)
        assertEquals(50, out.weightKg)
        assertEquals("2 x 1:30 @ 50 lbs", out.metric)
        assertTrue(out.setConfigs.all { it.weightKg == 50.0 })

        val cleared = SessionOverrides.applyDuration(task, 2, 90, 0)
        assertNull("0 clears the load", cleared.weightKg)
        val kept = SessionOverrides.applyDuration(task, 2, 90, null)
        assertEquals("null keeps the task's own load", 40, kept.weightKg)
    }

    @Test fun durationOverrideOnPerSetTaskKeepsPerSetLoads() {
        val task = movement("c", "Carry", 2, 0, null, "Exercises").copy(
            usesDuration = true, durationSeconds = 60, sameAcrossSets = false,
            setConfigs = listOf(
                TaskSetConfig(setNumber = 1, reps = 0, weightKg = 30.0, durationSeconds = 60),
                TaskSetConfig(setNumber = 2, reps = 0, weightKg = 40.0, durationSeconds = 50)
            )
        )
        val out = SessionOverrides.applyDuration(task, 3, 40, null)
        assertEquals(3, out.setConfigs.size)
        assertEquals(30.0, out.setConfigs[0].weightKg!!, 0.0)
        assertEquals(40.0, out.setConfigs[1].weightKg!!, 0.0)
        assertTrue(out.setConfigs.all { it.durationSeconds == 40 })
    }

    @Test fun repOverrideStillAppliesSetsAndReps() {
        val task = movement("b", "Bench", 3, 10, null, "Exercises")
        val out = SessionSetOverride(sets = 4, reps = 8).apply(task)
        assertEquals("4 x 8", out.metric)
    }

    // MARK: - In-session edit rules (FTNSSLNK-505/512/515/516)

    @Test fun editRulesCoverTimedWorkAndIntervalSwaps() {
        val timed = movement("p", "Plank", 2, 0, null, "Exercises").copy(usesDuration = true, durationSeconds = 30)
        val reps = movement("b", "Bench", 3, 10, null, "Exercises")
        val interval = movement("i", "Burpee", 1, 0, null, "Exercises").copy(isInterval = true, groupId = "g")
        assertTrue("timed loose work edits sets × time", SessionWorkoutEditor.canEditSets(timed))
        assertEquals("Edit Sets & Time", SessionWorkoutEditor.editSetsTitle(timed))
        assertEquals("Edit Sets & Reps", SessionWorkoutEditor.editSetsTitle(reps))
        assertTrue("interval members swap", SessionWorkoutEditor.canSwap(interval))
        assertFalse("grouped members don't edit sets", SessionWorkoutEditor.canEditSets(interval))
    }

    @Test fun swapPickersAreTitledSwapExercise() {
        assertEquals("Swap Exercise", SessionEditAction.Swap.pickerTitle)
        assertEquals("Swap Exercise", PlaylistEditRequest.Swap("t").pickerTitle)
        assertTrue(SessionEditAction.Swap.needsMovementPick)
        assertFalse(SessionEditAction.EditSets.needsMovementPick)
    }
}
