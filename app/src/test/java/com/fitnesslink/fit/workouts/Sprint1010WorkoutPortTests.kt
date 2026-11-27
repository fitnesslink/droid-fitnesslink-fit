package com.fitnesslink.fit.workouts

import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.movements.MovementStore
import com.fitnesslink.fit.viewmodel.WorkoutDurationBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS 23ef55d ports: FTNSSLNK-501 / 503 / 511. */
class Sprint1010WorkoutPortTests {

    // FTNSSLNK-511

    @Test
    fun `set duration parser accepts m ss and bare seconds`() {
        assertEquals(45, WorkoutRowOps.parseSetDuration("45"))
        assertEquals(90, WorkoutRowOps.parseSetDuration("90"))
        assertEquals(90, WorkoutRowOps.parseSetDuration("1:30"))
        assertEquals(30, WorkoutRowOps.parseSetDuration(":30"))
        assertEquals(60, WorkoutRowOps.parseSetDuration("1:"))
        assertNull(WorkoutRowOps.parseSetDuration(""))
        assertNull(WorkoutRowOps.parseSetDuration("1:75"))
        assertNull(WorkoutRowOps.parseSetDuration("1:2:3"))
        assertNull(WorkoutRowOps.parseSetDuration("abc"))
    }

    private fun timed(same: Boolean) = WorkoutTask(
        id = "p", name = "Plank", isMovement = true, sets = 3, reps = 12, usesDuration = true,
        durationSeconds = 40, sameAcrossSets = same,
        setConfigs = listOf(
            TaskSetConfig(setNumber = 1, reps = 12, durationSeconds = 30),
            TaskSetConfig(setNumber = 2, reps = 12, durationSeconds = 45),
            TaskSetConfig(setNumber = 3, reps = 12, durationSeconds = null)
        )
    )

    @Test
    fun `switching a timed task to per-set gives every row a window and no reps`() {
        val out = WorkoutRowOps.toggledSameAcrossSets(timed(same = true))
        assertFalse(out.sameAcrossSets)
        assertEquals(listOf(30, 45, 40), out.setConfigs.map { it.durationSeconds })
        assertTrue(out.setConfigs.all { it.reps == 0 })
    }

    @Test
    fun `collapsing a timed task takes set 1's window for the task and every row`() {
        val out = WorkoutRowOps.toggledSameAcrossSets(timed(same = false))
        assertTrue(out.sameAcrossSets)
        assertEquals(30, out.durationSeconds)
        assertTrue(out.setConfigs.all { it.durationSeconds == 30 && it.reps == 0 })
        assertEquals("3 x 0:30", out.metric)
    }

    @Test
    fun `timed per-set configs ship no rep target`() {
        val task = timed(same = false)
        val input = WorkoutTaskInputs.makeInput(task, phaseId = "ph", order = 0, groupId = null)
        assertTrue(input.setConfigs!!.all { it.reps == null })
    }

    // FTNSSLNK-503

    @Test
    fun `duration buckets OR together and need a positive estimate`() {
        assertTrue(WorkoutDurationBuckets.matches(emptySet(), null))
        assertFalse(WorkoutDurationBuckets.matches(setOf("lt20"), null))
        assertFalse(WorkoutDurationBuckets.matches(setOf("lt20"), 0))
        assertTrue(WorkoutDurationBuckets.matches(setOf("lt20"), 19 * 60))
        assertFalse(WorkoutDurationBuckets.matches(setOf("lt20"), 20 * 60))
        assertTrue(WorkoutDurationBuckets.matches(setOf("lt20", "40to60"), 45 * 60))
        assertTrue(WorkoutDurationBuckets.matches(setOf("60plus"), 3 * 60 * 60))
    }

    // FTNSSLNK-501

    @Test
    fun `server error text only surfaces sentence-like messages`() {
        assertEquals("Movement is used by 2 workouts.", MovementStore.serverErrorText("""{"error":"Movement is used by 2 workouts."}"""))
        assertNull(MovementStore.serverErrorText("""{"error":"forbidden"}"""))
        assertNull(MovementStore.serverErrorText("not json"))
        assertNull(MovementStore.serverErrorText(""))
    }
}
