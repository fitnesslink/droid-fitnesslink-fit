package com.fitnesslink.fit.workouts

import com.fitnesslink.fit.model.ExerciseGroupType
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMetrics
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.RestTypeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRowOpsTest {
    private fun movement(id: String) = WorkoutTask(id = id, name = id, isMovement = true, movementId = "m-$id", sets = 3, reps = 10)
    private fun rest(id: String) = WorkoutTask(id = id, name = "Rest", isRest = true, restSeconds = 60, rest = "1:00")

    @Test
    fun `grouping stamps one groupId and flags on every member and drops interleaved rests`() {
        val rows = listOf(TaskRow(task = movement("a")), TaskRow(task = rest("r")), TaskRow(task = movement("b")), TaskRow(task = movement("c")))
        val out = WorkoutRowOps.groupRows(rows, setOf("a", "b"), ExerciseGroupType.Superset, null, RestTypeId.passive)

        assertEquals(2, out.size)
        val group = out[0]
        assertTrue(group.isSuperset)
        assertEquals("a", group.task?.id)
        assertEquals(listOf("b"), group.advancedTasks.map { it.id })
        val gid = group.task!!.groupId
        assertTrue(gid.isNotEmpty())
        assertTrue(group.advancedTasks.all { it.groupId == gid && it.isSuperset && it.rounds == 3 })
        assertEquals(3, group.task!!.rounds)
        assertEquals(60, group.task!!.roundRestSeconds)
        assertEquals("c", out[1].task?.id)
    }

    @Test
    fun `superset takes only the first two selections and circuits need two`() {
        val rows = listOf(TaskRow(task = movement("a")), TaskRow(task = movement("b")), TaskRow(task = movement("c")))
        val superset = WorkoutRowOps.groupRows(rows, setOf("a", "b", "c"), ExerciseGroupType.Superset, null, RestTypeId.passive)
        assertEquals(2, superset.size)
        assertEquals(1, superset[0].advancedTasks.size)

        val unchanged = WorkoutRowOps.groupRows(rows, setOf("a"), ExerciseGroupType.Circuit, null, RestTypeId.passive)
        assertTrue(unchanged === rows)
    }

    @Test
    fun `interval groups allow a single movement and seed the preset plus passive rest`() {
        val rows = listOf(TaskRow(task = movement("a")))
        val out = WorkoutRowOps.groupRows(rows, setOf("a"), ExerciseGroupType.Interval, IntervalMode.TABATA, RestTypeId.passive)
        val lead = out[0].task!!
        assertTrue(out[0].isInterval)
        assertEquals(IntervalMode.TABATA, lead.intervalMode)
        assertEquals(20, lead.intervalWorkSeconds)
        assertEquals(10, lead.intervalRestSeconds)
        assertEquals(8, lead.rounds)
        assertEquals(1, lead.intervalSets)
        assertEquals(IntervalMath.defaultSetRestSeconds, lead.intervalSetRestSeconds)
        assertEquals(RestTypeId.passive, lead.restTypeId)
        assertNull(lead.roundRestSeconds)
    }

    @Test
    fun `ungroup clears group fields and restores loose rows`() {
        val rows = WorkoutRowOps.groupRows(
            listOf(TaskRow(task = movement("a")), TaskRow(task = movement("b"))), setOf("a", "b"), ExerciseGroupType.Superset, null, RestTypeId.passive
        )
        val out = WorkoutRowOps.ungroupRows(rows, "a")
        assertEquals(2, out.size)
        assertTrue(out.all { it.task!!.groupId.isEmpty() && !it.task!!.isSuperset })
        assertNull(out[0].task!!.rounds)
    }

    @Test
    fun `removing the lead promotes the next member and migrates lead config`() {
        val lead = movement("a").copy(groupId = "g", isCircuit = true, roundRestSeconds = 45, rounds = 3)
        val row = TaskRow(task = lead, advancedTasks = listOf(movement("b").copy(groupId = "g", isCircuit = true), movement("c").copy(groupId = "g", isCircuit = true)), isCircuit = true, totalRounds = 3)
        val out = WorkoutRowOps.removedGroupMember(listOf(row), "a")
        assertEquals(1, out.size)
        assertEquals("b", out[0].task?.id)
        assertEquals(45, out[0].task?.roundRestSeconds)
        assertEquals(listOf("c"), out[0].advancedTasks.map { it.id })
    }

    @Test
    fun `interval total time matches IntervalMath for a 3-set HIIT block`() {
        val lead = movement("a").copy(
            isInterval = true, intervalMode = IntervalMode.HIIT, intervalWorkSeconds = 45, intervalRestSeconds = 15,
            rounds = 6, totalRounds = 6, intervalSets = 3, intervalSetRestSeconds = 120
        )
        val row = TaskRow(task = lead, isInterval = true, intervalMode = IntervalMode.HIIT, totalRounds = 6)
        val metrics = IntervalMetrics.from(row)
        assertNotNull(metrics)
        // One set = 6×45 + 5×15 = 345 s; block = 3×345 + 2×120 = 1275 s.
        assertEquals(1275, metrics!!.totalSeconds)
        assertEquals("21:15", metrics.totalText)
        assertEquals(1275, IntervalMath.blockSeconds(IntervalMath.plan(lead, rowTotalRounds = 6, mode = IntervalMode.HIIT)))
        assertEquals("Tabata · 3:50", WorkoutStructure.intervalSummary(
            movement("t").copy(isInterval = true, intervalMode = IntervalMode.TABATA), rowTotalRounds = 0, mode = IntervalMode.TABATA
        ))
    }

    @Test
    fun `EMOM window drives rounds and withIntervalWindow keeps them in sync`() {
        val lead = movement("e").copy(isInterval = true, intervalMode = IntervalMode.EMOM, intervalWorkSeconds = 40, intervalWindowSeconds = 600)
        val row = TaskRow(task = lead, isInterval = true, intervalMode = IntervalMode.EMOM, totalRounds = 10)
        assertEquals(600, IntervalMath.blockSeconds(IntervalMath.plan(lead, rowTotalRounds = 10, mode = IntervalMode.EMOM)))
        val resized = WorkoutRowOps.withIntervalWindow(row, 12 * 60)
        assertEquals(12, resized.totalRounds)
        assertEquals(12, resized.task?.rounds)
        assertEquals(720, resized.task?.intervalWindowSeconds)
    }

    @Test
    fun `estimated time round-trips through the display string in seconds`() {
        assertEquals("45 min", Workout.formatEstimatedTime(2700))
        assertEquals(2700, Workout.secondsFromDisplayTime("45 min"))
        assertEquals("1 hr 30 min", Workout.formatEstimatedTime(5400))
        assertEquals(5400, Workout.secondsFromDisplayTime("1 hr 30 min"))
        assertNull(Workout.secondsFromDisplayTime("~38 min"))
        val recovered = Workout(time = "45 min").recoverEstimatedTimeFromDisplayString()
        assertEquals(2700, recovered.estimatedTime)
        assertEquals("45 min", Workout(estimatedTime = 2700).displayTime)
    }

    @Test
    fun `copiedTaskRows mints fresh ids and remaps group ids per group`() {
        val row = TaskRow(task = movement("a").copy(groupId = "g1"), advancedTasks = listOf(movement("b").copy(groupId = "g1")), isSuperset = true)
        val copies = WorkoutRowOps.copiedTaskRows(listOf(row), "newPhase", "Copy")
        val lead = copies[0].task!!
        assertFalse(lead.id == "a")
        assertEquals("newPhase", lead.workoutPhaseId)
        assertTrue(lead.groupId != "g1")
        assertEquals(lead.groupId, copies[0].advancedTasks[0].groupId)
    }

    @Test
    fun `mergePreservingPhases keeps local tasks when the server echoes empty phases`() {
        val local = Workout(id = "local", phases = listOf(com.fitnesslink.fit.model.WorkoutPhase(id = "p", taskRows = listOf(TaskRow(task = movement("a"))))))
        val server = Workout(id = "server", phases = emptyList())
        val merged = WorkoutRowOps.mergePreservingPhases(local, server)
        assertEquals("server", merged.id)
        assertEquals(1, merged.phases.sumOf { it.taskRows.size })
    }
}
