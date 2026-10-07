package com.fitnesslink.fit.workouts

import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.WorkoutTaskTypeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTaskInputsTest {
    private fun movement(id: String, sets: Int = 3, reps: Int = 10) =
        WorkoutTask(id = id, name = id, isMovement = true, movementId = "m-$id", sets = sets, reps = reps)

    @Test
    fun `flattening keeps phase order and stamps sequential order across phases`() {
        val warm = WorkoutPhase(id = "P1", name = "Warmup", taskRows = listOf(TaskRow(task = movement("a")), TaskRow(task = movement("b"))))
        val main = WorkoutPhase(id = "P2", name = "Exercises", taskRows = listOf(TaskRow(task = movement("c"))))
        val inputs = WorkoutTaskInputs.taskInputs(Workout(phases = listOf(warm, main)))

        assertEquals(listOf("a", "b", "c"), inputs.map { it.id })
        assertEquals(listOf(0, 1, 2), inputs.map { it.order })
        assertEquals(listOf("p1", "p1", "p2"), inputs.map { it.workoutPhaseId })
    }

    @Test
    fun `loose rows carry no groupId and grouped rows share one`() {
        val group = TaskRow(task = movement("lead"), advancedTasks = listOf(movement("m2"), movement("m3")), isCircuit = true)
        val phase = WorkoutPhase(id = "p", name = "Exercises", taskRows = listOf(TaskRow(task = movement("solo")), group))
        val inputs = WorkoutTaskInputs.taskInputs(phase)

        assertNull(inputs[0].groupId)
        val groupIds = inputs.drop(1).map { it.groupId }.toSet()
        assertEquals(1, groupIds.size)
        assertNotNull(groupIds.first())
    }

    @Test
    fun `existing lead groupId is reused`() {
        val row = TaskRow(task = movement("lead").copy(groupId = "G-1"), advancedTasks = listOf(movement("m2")), isSuperset = true)
        val inputs = WorkoutTaskInputs.taskInputs(WorkoutPhase(id = "p", taskRows = listOf(row)))
        assertTrue(inputs.all { it.groupId == "G-1" })
    }

    @Test
    fun `single-movement interval row still gets a groupId`() {
        val row = TaskRow(task = movement("lead").copy(isInterval = true), isInterval = true)
        val inputs = WorkoutTaskInputs.taskInputs(WorkoutPhase(id = "p", taskRows = listOf(row)))
        assertEquals(1, inputs.size)
        assertNotNull(inputs[0].groupId)
    }

    @Test
    fun `duration mode ships intervalSeconds and drops reps`() {
        val task = movement("t").copy(usesDuration = true, durationSeconds = 45, reps = 12)
        val input = WorkoutTaskInputs.makeInput(task, "p", 0, null)
        assertNull(input.reps)
        assertEquals(45, input.intervalSeconds)
    }

    @Test
    fun `interval lead writes intervalSetRestSeconds not roundRestSeconds`() {
        val lead = movement("lead").copy(isInterval = true, intervalSetRestSeconds = 90, roundRestSeconds = 30)
        val input = WorkoutTaskInputs.makeInput(lead, "p", 0, "g")
        assertEquals(90, input.intervalSetRestSeconds)
        assertNull(input.roundRestSeconds)

        val circuitLead = movement("c").copy(isCircuit = true, roundRestSeconds = 30)
        val circuitInput = WorkoutTaskInputs.makeInput(circuitLead, "p", 0, "g")
        assertEquals(30, circuitInput.roundRestSeconds)
        assertNull(circuitInput.intervalSetRestSeconds)
    }

    @Test
    fun `rounds fall back to totalRounds after a server round-trip`() {
        val task = movement("t").copy(rounds = null, totalRounds = 4)
        assertEquals(4, WorkoutTaskInputs.makeInput(task, "p", 0, "g").rounds)
    }

    @Test
    fun `per-set configs ship only in individual-edit mode`() {
        val configs = listOf(TaskSetConfig(setNumber = 1, reps = 10, weightKg = 100.0), TaskSetConfig(setNumber = 2, reps = 8, weightKg = 110.0))
        val uniform = movement("u").copy(sameAcrossSets = true, setConfigs = configs)
        assertNull(WorkoutTaskInputs.makeInput(uniform, "p", 0, null).setConfigs)

        val varied = movement("v").copy(sameAcrossSets = false, setConfigs = configs)
        val shipped = WorkoutTaskInputs.makeInput(varied, "p", 0, null).setConfigs
        assertEquals(2, shipped?.size)
        assertEquals(110, shipped?.get(1)?.weightKg)
    }

    @Test
    fun `task type resolves rest and movement from the fallback ids`() {
        val rest = WorkoutTask(id = "r", isRest = true, restSeconds = 60)
        assertEquals(WorkoutTaskTypeId.rest, WorkoutTaskInputs.makeInput(rest, "p", 0, null).workoutTaskTypeId)
        assertEquals(WorkoutTaskTypeId.movement, WorkoutTaskInputs.makeInput(movement("m"), "p", 0, null).workoutTaskTypeId)
        val stored = movement("s").copy(workoutTaskTypeId = "ABCDEF00-0000-0000-0000-000000000000")
        assertEquals("abcdef00-0000-0000-0000-000000000000", WorkoutTaskInputs.makeInput(stored, "p", 0, null).workoutTaskTypeId)
    }

    @Test
    fun `createRequest sends estimatedTime in seconds and collapses empties to null`() {
        val workout = Workout(name = "W", estimatedTime = 45 * 60, location = "", trainingLevels = emptyList())
        val req = WorkoutTaskInputs.createRequest(workout, emptyList(), statusId = "status")
        assertEquals(2700, req.estimatedTime)
        assertNull(req.location)
        assertNull(req.trainingLevels)
        assertNull(req.equipmentIds)
        assertEquals("status", req.statusId)
    }

    @Test
    fun `updateRequest sends the whole location and level set`() {
        val workout = Workout(name = "W", location = "", trainingLevels = emptyList(), estimatedTime = 1800)
        val req = WorkoutTaskInputs.updateRequest(workout, equipmentIds = emptyList())
        assertEquals("", req.location)
        assertEquals(emptyList<String>(), req.trainingLevels)
        assertEquals(emptyList<String>(), req.equipmentIds)
        assertNull(req.anatomyIds)
        assertEquals(1800, req.estimatedTime)
    }
}
