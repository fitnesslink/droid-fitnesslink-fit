package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.TrainingFocus
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.AvailableWorkoutPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/** Quick Start PRD: focus → name, the plan-mirrors-logs rule, suggestions, finish pruning and resume routing. */
class QuickStartTests {

    private fun movement(id: String, movementId: String? = null, group: String = ""): WorkoutTask = WorkoutTask(
        id = id, name = id, isMovement = true, movementId = movementId ?: "m-$id", groupId = group,
        sets = 1, reps = 0, setConfigs = listOf(TaskSetConfig(setNumber = 1, reps = 0))
    )

    private fun rest(id: String = "r-${System.nanoTime()}") = WorkoutTask(id = id, name = "Rest", isRest = true, restSeconds = 60)

    private fun log(n: Int, reps: Int, weight: Double? = null, seconds: Int? = null) =
        TaskSetConfig(setNumber = n, reps = reps, weightKg = weight, durationSeconds = seconds)

    private fun phase(name: String, rows: List<TaskRow>) = WorkoutPhase(id = name, name = name, taskRows = rows)

    // MARK: - Training focus (PRD §8–9)

    @Test fun `default workout name uses focus and day`() {
        val cal = Calendar.getInstance(Locale.US).apply { set(2026, Calendar.OCTOBER, 5, 12, 0, 0) }
        assertEquals("Lower Body — Oct 5", TrainingFocus.defaultWorkoutName(TrainingFocus.presets[0], cal.time))
        assertEquals("Glute Day — Oct 5", TrainingFocus.defaultWorkoutName(TrainingFocus("Glute Day"), cal.time))
    }

    @Test fun `custom focus trims and folds into presets`() {
        assertNull(TrainingFocus.custom("   "))
        assertEquals("Coach Session", TrainingFocus.custom("  Coach Session ")?.name)
        assertFalse(TrainingFocus.custom("coach session")!!.isPreset)
        assertEquals(TrainingFocus.presets.first { it.name == "Legs" }.symbol, TrainingFocus.custom("legs")?.symbol)
        assertTrue(TrainingFocus.resolve("Push").isPreset)
        assertEquals("Athletic Performance", TrainingFocus.resolve("Athletic Performance").name)
    }

    // MARK: - Plan mirrors logs (PRD §22, §30)

    @Test fun `plan mirrors logged sets and keeps one open set`() {
        val task = movement("A")
        val open = QuickStartPlan.plan(task, emptyList(), 1)
        assertEquals(1, open.sets)
        assertEquals(0, open.reps)
        assertEquals("no artificial prescription (PRD §28)", "", open.metric)

        val planned = QuickStartPlan.plan(task, listOf(log(1, 12, 20.0), log(2, 10, 25.0)), 2)
        assertEquals(2, planned.sets)
        assertEquals(listOf(12, 10), planned.setConfigs.map { it.reps })
        assertEquals(listOf(20.0, 25.0), planned.setConfigs.map { it.weightKg })
        assertEquals("task-level values follow the last set", 10, planned.reps)
        assertEquals(25, planned.weightKg)
        assertEquals("2 x 10", planned.metric)
        assertFalse(planned.sameAcrossSets)
    }

    @Test fun `plan keeps config ids so unchanged plans compare equal`() {
        val task = movement("A")
        val first = QuickStartPlan.plan(task, listOf(log(1, 8, 100.0)), 1)
        val second = QuickStartPlan.plan(first, listOf(log(1, 8, 100.0)), 1)
        assertEquals(first, second)
        assertEquals(task.setConfigs.first().id, first.setConfigs.first().id)
    }

    @Test fun `plan switches to duration when a set logs time`() {
        val planned = QuickStartPlan.plan(movement("Plank"), listOf(log(1, 0, seconds = 45)), 1)
        assertTrue(planned.usesDuration)
        assertEquals(45, planned.durationSeconds)
        assertEquals("1 x 0:45", planned.metric)
    }

    @Test fun `mirror logs onto block uses most rounds logged`() {
        val a = movement("A", group = "g")
        val b = movement("B", group = "g")
        val row = TaskRow(task = a, advanced = "Superset", rounds = "1 rounds", totalRounds = 1, isSuperset = true, advancedTasks = listOf(b))
        val logs = mapOf("A" to listOf(log(1, 10), log(2, 10), log(3, 8)), "B" to listOf(log(1, 12)))
        val mirrored = QuickStartPlan.mirrorLogs(row) { logs[it] ?: emptyList() }
        assertEquals(3, mirrored.totalRounds)
        assertEquals("3 rounds", mirrored.rounds)
        assertEquals(3, mirrored.task?.rounds)
        assertEquals("members mirror the lead's round count", 3, mirrored.advancedTasks.first().rounds)
        assertEquals(listOf(12, 0, 0), mirrored.advancedTasks.first().setConfigs.map { it.reps })
    }

    // MARK: - Finish pruning (PRD §30, §43)

    @Test fun `prune unlogged drops never-logged exercises, empty phases and stray rests`() {
        val a = movement("A"); val b = movement("B"); val c = movement("C")
        val workout = Workout(id = "w", phases = listOf(
            phase("Warm-Up", listOf(TaskRow(task = c))),
            phase("Strength", listOf(TaskRow(task = a), TaskRow(task = rest("r1")), TaskRow(task = b), TaskRow(task = rest("r2"))))
        ))
        val pruned = QuickStartPlan.pruneUnlogged(workout, setOf("A"))
        assertEquals(listOf("Strength"), pruned.phases.map { it.name })
        assertEquals(listOf("A"), pruned.phases[0].taskRows.mapNotNull { it.task?.id })
        assertEquals(1, pruned.phases[0].order)
    }

    @Test fun `prune unlogged keeps rest between two logged exercises`() {
        val w = Workout(phases = listOf(phase("Main", listOf(TaskRow(task = movement("A")), TaskRow(task = rest("r")), TaskRow(task = movement("B"))))))
        val pruned = QuickStartPlan.pruneUnlogged(w, setOf("A", "B"))
        assertEquals(3, pruned.phases[0].taskRows.size)
        assertEquals(true, pruned.phases[0].taskRows[1].task?.isRest)
    }

    @Test fun `prune unlogged ungroups a block left with one member`() {
        val lead = movement("A", group = "g").copy(isSuperset = true, rounds = 3)
        val row = TaskRow(task = lead, advanced = "Superset", rounds = "3 rounds", totalRounds = 3, isSuperset = true, advancedTasks = listOf(movement("B", group = "g")))
        val pruned = QuickStartPlan.pruneUnlogged(Workout(phases = listOf(phase("Main", listOf(row)))), setOf("B"))
        val only = pruned.phases[0].taskRows[0]
        assertEquals("B", only.task?.id)
        assertEquals("", only.task?.groupId)
        assertFalse(only.task?.isSuperset ?: true)
        assertTrue(only.advancedTasks.isEmpty())
    }

    @Test fun `prune unlogged promotes next member to lead with block config`() {
        val lead = movement("A", group = "g").copy(isCircuit = true, rounds = 2, roundRestSeconds = 45)
        val row = TaskRow(task = lead, advanced = "Circuit", rounds = "2 rounds", totalRounds = 2, isCircuit = true, advancedTasks = listOf(movement("B", group = "g"), movement("C", group = "g")))
        val pruned = QuickStartPlan.pruneUnlogged(Workout(phases = listOf(phase("Main", listOf(row)))), setOf("B", "C"))
        val kept = pruned.phases[0].taskRows[0]
        assertEquals("B", kept.task?.id)
        assertEquals(2, kept.task?.rounds)
        assertEquals(45, kept.task?.roundRestSeconds)
        assertEquals(listOf("C"), kept.advancedTasks.map { it.id })
        assertTrue(kept.isCircuit)
    }

    @Test fun `finish message names unlogged exercises`() {
        assertEquals("The timer stops and your workout is saved to My Content.", QuickStartPlan.finishMessage(0, 12))
        assertTrue(QuickStartPlan.finishMessage(1, 3).startsWith("1 exercise has no logged sets and won't be saved."))
        assertTrue(QuickStartPlan.finishMessage(2, 0).startsWith("You haven't logged any sets yet. 2 exercises have no logged sets"))
    }

    // MARK: - Open task (PRD §20, §28)

    @Test fun `open task has one open set and no prescription`() {
        val task = QuickStartPlan.makeOpenTask(MovementLibraryItem(id = "lib-x", name = "Goblet Squat", thumbnailId = "t"))
        assertTrue(task.isMovement)
        assertEquals("lib-x", task.movementId)
        assertEquals(1, task.sets)
        assertEquals(0, task.reps)
        assertEquals("", task.metric)
        assertEquals(1, task.setConfigs.size)
        assertEquals(0, task.setConfigs.first().reps)
    }

    // MARK: - Suggestions (PRD §17)

    @Test fun `suggestions rank same-named phase movements by frequency then recents`() {
        val w1 = Workout(phases = listOf(
            phase("Warm-Up", listOf(TaskRow(task = movement("a", "M-A")), TaskRow(task = movement("b", "M-B")))),
            phase("Strength", listOf(TaskRow(task = movement("z", "M-Z"))))
        ))
        val w2 = Workout(phases = listOf(phase("Warmup", listOf(TaskRow(task = movement("b2", "m-b")), TaskRow(task = movement("c", "m-c"))))))
        assertEquals(listOf("m-b", "m-a", "m-c", "m-r"), QuickStartSuggestions.rank("warm up", listOf(w1, w2), listOf("m-r", "m-a"), 6))
        assertEquals(listOf("m-r"), QuickStartSuggestions.rank("Cool Down", listOf(w1, w2), listOf("m-r"), 1))
        assertEquals(emptyList<String>(), QuickStartSuggestions.rank("Cool Down", emptyList(), emptyList(), 6))
    }

    // MARK: - Resume routing (PRD §38)

    @Test fun `quick start session resumes on the canvas`() {
        val quick = InProgressSessionEntry("s", "w", "Lower Body — Oct 5", 0, 0, SessionMode.QUICK_START, 0, hasCompletionPercent = false)
        assertEquals("quickStartCanvas/w", quick.resumeRoute)
        val player = InProgressSessionEntry("s2", "w2", "Leg Day", 40, 0, SessionMode.INTERACTIVE, 0, programId = "p")
        assertEquals("playlistSession/w2", player.resumeRoute)
        assertEquals(SessionMode.QUICK_START, SessionMode.fromRaw("quickStart"))
    }

    // MARK: - Logging page formatting (PRD §32)

    @Test fun `set line formatting`() {
        assertEquals("12 reps", QuickStartPlan.setLine(log(1, 12)))
        assertEquals("1:30", QuickStartPlan.setLine(log(1, 0, seconds = 90)))
        assertEquals(90, QuickStartPlan.parseSeconds("1:30"))
        assertEquals(45, QuickStartPlan.parseSeconds("45"))
        assertEquals(0, QuickStartPlan.parseSeconds("x"))
    }

    // MARK: - Exercise note (PRD §34–36)

    @Test fun `exercise note emptiness and RPE scale`() {
        assertTrue(SessionExerciseNote("s", "t", null, "A", null, "   ", 0).isEmpty)
        assertFalse(SessionExerciseNote("s", "t", null, "A", 8, null, 0).isEmpty)
        assertEquals(10, ExerciseRPE.clamp(14))
        assertEquals(1, ExerciseRPE.clamp(0))
        assertEquals("Hard", ExerciseRPE.descriptor(8))
    }

    @Test fun `standard phase options match catalog names loosely`() {
        val available = listOf(
            AvailableWorkoutPhase("p1", "Warmup", 1, true), AvailableWorkoutPhase("p3", "Cool Down", 3, true), AvailableWorkoutPhase("p4", "Power", 100, false)
        )
        fun match(name: String) = available.firstOrNull { QuickStartSuggestions.normalizedPhaseName(it.name) == QuickStartSuggestions.normalizedPhaseName(name) }
        assertEquals("p1", match("Warm-Up")?.id)
        assertEquals("p3", match("cooldown")?.id)
        assertEquals("p4", match("power")?.id)
        assertNull(match("Strength"))
    }
}
