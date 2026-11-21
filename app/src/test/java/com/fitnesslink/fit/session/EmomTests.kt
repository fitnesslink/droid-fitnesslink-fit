package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.EmomEditorState
import com.fitnesslink.fit.model.EmomMath
import com.fitnesslink.fit.model.EmomPlan
import com.fitnesslink.fit.model.EmomText
import com.fitnesslink.fit.model.ExerciseGroupType
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMetrics
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.IntervalPhase
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.RestTypeId
import com.fitnesslink.fit.session.IntervalRunner.Event
import com.fitnesslink.fit.workouts.WorkoutRowOps
import com.fitnesslink.fit.workouts.WorkoutTaskInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** EMOM PRD v2.0 (docs/parity/EMOM_PRD.md) — the pure parts: plan, editor sync, text, runner, coach. */
class EmomTests {

    // MARK: - Fixtures

    private fun reps(id: String, name: String, reps: Int = 10) =
        WorkoutTask(id = id, name = name, isMovement = true, movementId = "m-$id", isInterval = true, groupId = "g", reps = reps, sets = 1)

    private fun timed(id: String, name: String, seconds: Int?) =
        WorkoutTask(id = id, name = name, isMovement = true, movementId = "m-$id", isInterval = true, groupId = "g", usesDuration = true, durationSeconds = seconds, sets = 1)

    /** A v2 EMOM row: [interval] × (cycles × members). */
    private fun emomRow(members: List<WorkoutTask>, interval: Int = 60, cycles: Int = 1): TaskRow {
        val base = TaskRow(
            task = members[0].copy(intervalMode = IntervalMode.EMOM), advanced = "Interval", isInterval = true,
            intervalMode = IntervalMode.EMOM, advancedTasks = members.drop(1)
        )
        return EmomMath.applyTiming(base, interval, cycles)
    }

    private fun legacyLead(window: Int? = null, rounds: Int? = null, work: Int? = 40, sets: Int? = null) = WorkoutTask(
        id = "lead", name = "Burpees", isMovement = true, movementId = "m", isInterval = true, groupId = "g",
        intervalMode = IntervalMode.EMOM, intervalWindowSeconds = window, rounds = rounds, totalRounds = rounds ?: 0,
        intervalWorkSeconds = work, intervalSets = sets, reps = 10
    )

    private class FakeClock(var now: Long = 1_700_000_000_000L)

    private class Harness(val runner: IntervalRunner, val clock: FakeClock, val events: MutableList<Event>, val cues: MutableList<EmomCue>) {
        fun advance(seconds: Int) { clock.now += seconds * 1000L; runner.reconcile(clock.now) }
        fun finishPhase() = advance(runner.secondsRemaining)
        fun lines(): List<String> = cues.flatMap { it.lines }
    }

    private fun harness(row: TaskRow, prepare: Int = 0, coach: EmomCoach = EmomCoach()): Harness {
        val clock = FakeClock()
        val events = mutableListOf<Event>()
        val cues = mutableListOf<EmomCue>()
        val runner = IntervalRunner(row, prepare) { clock.now }
        runner.onEvent = { e -> events.add(e); cues.addAll(coach.cues(e, EmomCueContext.from(runner))) }
        coach.beginBlock()
        runner.start()
        return Harness(runner, clock, events, cues)
    }

    // MARK: - Plan resolution (§2.1)

    @Test fun testV2RowResolvesIntervalFromWindowOverRounds() {
        val row = emomRow(listOf(reps("a", "Push-ups"), reps("b", "Squats")), interval = 45, cycles = 10)
        val plan = EmomMath.plan(row)!!
        assertEquals(45, plan.intervalSeconds)
        assertEquals(20, plan.totalRounds)
        assertEquals(10, plan.cycles)
        assertEquals(900, plan.totalSeconds)
        assertTrue(plan.isCompleteCycles)
        val lead = row.task!!
        assertEquals(900, lead.intervalWindowSeconds)
        assertEquals(20, lead.rounds)
        assertEquals(45, lead.intervalWorkSeconds)
        assertEquals(1, lead.intervalSets)
        assertNull(lead.intervalRestSeconds)
        assertNull(lead.intervalSetRestSeconds)
        assertEquals(RestType.PASSIVE, lead.restType)
        assertEquals(listOf(20), row.advancedTasks.map { it.rounds })
    }

    @Test fun testLegacyMinutesRowsRunAsBefore() {
        // window = minutes × 60, rounds = minutes; the legacy work-per-minute is ignored.
        val plan = EmomMath.plan(legacyLead(window = 600, rounds = 10, work = 40), exerciseCount = 1)
        assertEquals(EmomPlan(60, 10, 1), plan)
        assertEquals(600, IntervalMath.blockSeconds(IntervalMath.plan(legacyLead(window = 600, rounds = 10), mode = IntervalMode.EMOM)))
        // Window only → 60 s × minutes.
        assertEquals(EmomPlan(60, 5, 2), EmomMath.plan(legacyLead(window = 300), exerciseCount = 2))
        // Rounds only → 60 s × rounds.
        assertEquals(EmomPlan(60, 7, 1), EmomMath.plan(legacyLead(rounds = 7), exerciseCount = 1))
        // Nothing stored → one cycle at 60 s.
        assertEquals(EmomPlan(60, 3, 3), EmomMath.plan(legacyLead(work = null), exerciseCount = 3))
        // Row-level totalRounds is the last rounds fallback.
        assertEquals(EmomPlan(60, 4, 1), EmomMath.plan(legacyLead(), exerciseCount = 1, rowTotalRounds = 4))
    }

    @Test fun testLegacyWindowThatDoesNotDivideFallsBackToMinutes() {
        assertEquals(EmomPlan(60, 1, 1), EmomMath.plan(legacyLead(window = 100, rounds = 3), exerciseCount = 1))
        // window / rounds < 5 s is not an interval: legacy minutes model (min 1 round).
        assertEquals(EmomPlan(60, 1, 1), EmomMath.plan(legacyLead(window = 8, rounds = 4), exerciseCount = 1))
    }

    @Test fun testLegacyMultiSetEmomIsFlattened() {
        val plan = EmomMath.plan(legacyLead(window = 180, rounds = 3, sets = 2), exerciseCount = 1)
        assertEquals(6, plan.totalRounds)
        assertEquals(360, plan.totalSeconds)
        val ip = plan.intervalPlan
        assertEquals(1, ip.sets)
        assertEquals(0, ip.betweenSetsRestSeconds)
        assertEquals(0, ip.restSeconds)
        assertEquals(60, ip.workSeconds)
        assertEquals(360, IntervalMath.blockSeconds(ip))
    }

    @Test fun testPartialLegacyCycleRoundsUp() {
        val plan = EmomPlan(60, 5, 2)
        assertEquals(3, plan.cycles)
        assertFalse(plan.isCompleteCycles)
        assertEquals(3, plan.occurrences(0))
        assertEquals(2, plan.occurrences(1))
    }

    // MARK: - Session requirements (§2.2)

    @Test fun testRequiredIterationsAreOccurrencesNotTotalRounds() {
        val row = emomRow(listOf(reps("a", "A"), reps("b", "B"), reps("c", "C")), cycles = 4)
        val flat = listOf(row.task!!) + row.advancedTasks
        val required = SessionProgress.requiredIterations(flat)
        assertEquals(mapOf("a" to 4, "b" to 4, "c" to 4), required)

        val legacy = listOf(legacyLead(window = 420, rounds = 7).copy(id = "a"), reps("b", "B"), reps("c", "C"))
        assertEquals(mapOf("a" to 3, "b" to 2, "c" to 2), SessionProgress.requiredIterations(legacy))

        val plan = EmomPlan(60, 7, 3)
        assertEquals(7, plan.round(0, 3))
        assertEquals(5, plan.round(1, 2))
        assertEquals(3, plan.cycle(7))
        assertEquals(0, plan.memberIndex(7))
    }

    @Test fun testNonEmomGroupsKeepLeadRounds() {
        val hiit = listOf(
            WorkoutTask(id = "a", isMovement = true, isInterval = true, groupId = "h", intervalMode = IntervalMode.HIIT, rounds = 6),
            WorkoutTask(id = "b", isMovement = true, isInterval = true, groupId = "h")
        )
        assertEquals(mapOf("a" to 6, "b" to 6), SessionProgress.requiredIterations(hiit))
    }

    // MARK: - Targets and formatting (§3.3)

    @Test fun testTargetTextAndFormatting() {
        assertEquals("20 reps", EmomMath.targetText(reps("a", "A", 20)))
        assertEquals("30 sec", EmomMath.targetText(timed("a", "A", 30)))
        assertEquals("1:30", EmomMath.targetText(timed("a", "A", 90)))
        assertEquals("1:00", EmomMath.targetText(timed("a", "A", 60)))
        assertEquals("Reps – Time", EmomMath.targetText(reps("a", "A", 0)))
        assertEquals("Reps – Time", EmomMath.targetText(timed("a", "A", null)))
        assertNull(EmomMath.displayTarget(reps("a", "A", 0)))
        assertEquals("30 seconds", EmomMath.spokenTarget(timed("a", "A", 30)))
        assertEquals("1 minute 30 seconds", EmomMath.spokenTarget(timed("a", "A", 90)))
        assertEquals("Repeat 1 time · 1 total round", EmomMath.previewFooter(EmomPlan(60, 1, 1)))
        assertEquals("20 reps", EmomMath.spokenTarget(reps("a", "A", 20)))

        val plan = EmomPlan(60, 20, 2)
        assertEquals("20-min EMOM · 60s Interval · 20:00", EmomMath.summaryTitle(plan))
        assertEquals("7:30 EMOM · 45s Interval · 7:30", EmomMath.summaryTitle(EmomPlan(45, 10, 2)))
        assertEquals(listOf("Duration" to "20 min", "Interval" to "60 sec", "Rounds" to "20", "Cycles" to "10"),
            EmomMath.builderTiles(plan).map { it.label to it.value })
        assertEquals(listOf("Duration" to "7:30", "Interval" to "45 sec", "Rounds" to "10"),
            EmomMath.builderTiles(EmomPlan(45, 10, 1)).map { it.label to it.value })
        assertEquals(listOf("Duration" to "20:00", "Interval" to "60s", "Rounds" to "20", "Cycles" to "10"),
            EmomMath.overviewTiles(plan).map { it.label to it.value })
        assertEquals(3, EmomMath.overviewTiles(EmomPlan(60, 10, 1)).size)
        assertEquals("60 sec", EmomMath.intervalLabel(60))
        assertEquals("90 sec", EmomMath.intervalLabel(90))
        assertEquals("2 min", EmomMath.intervalLabel(120))
        assertEquals("150 sec", EmomMath.intervalLabel(150))
        assertEquals("5 min", EmomMath.intervalLabel(300))
        assertEquals("20 rounds · 10 cycles", EmomMath.roundsCyclesLine(plan))
        assertEquals("10 rounds", EmomMath.roundsCyclesLine(EmomPlan(60, 10, 1)))
        assertEquals("Repeat sequence 10 times · 20 total rounds", EmomMath.previewFooter(plan))
        assertEquals("Repeat 10 times · 10 total rounds", EmomMath.previewFooter(EmomPlan(60, 10, 1)))
        assertEquals("One cycle includes both exercises.", EmomMath.cyclesCaption(2))
        assertEquals("One cycle includes all 3 exercises.", EmomMath.cyclesCaption(3))
        assertEquals("Number of times to repeat the exercise.", EmomMath.cyclesCaption(1))
        assertEquals("Calculated automatically · 2 exercises selected", EmomMath.totalRoundsCaption(2))
        assertNull(EmomMath.totalRoundsCaption(1))
    }

    @Test fun testIntervalMetricsForEmomRows() {
        val row = emomRow(listOf(reps("a", "Push-ups", 10), reps("b", "Squats", 15)), cycles = 10)
        val metrics = IntervalMetrics.from(row)!!
        assertEquals("INTERVAL · EMOM", metrics.badgeText)
        assertEquals(2, metrics.exerciseCount)
        assertEquals("20:00", metrics.totalText)
        assertEquals(listOf("Duration", "Interval", "Rounds", "Cycles"), metrics.tiles.map { it.label })
        assertEquals("20-min EMOM · 60s Interval · 20:00", metrics.compactLine)
        assertEquals("Complete one exercise every 60 seconds. Repeat both exercises for 10 cycles.", metrics.emomInstruction)
        assertEquals(1200 + IntervalMath.defaultPrepareSeconds, com.fitnesslink.fit.model.WorkoutDurationEstimator.plannedSeconds(listOf(row)))
    }

    // MARK: - Dual-input sync + validation (§3.5)

    @Test fun testCyclesEditIsAlwaysValid() {
        val s = EmomEditorState(listOf(reps("a", "A"), reps("b", "B")), interval = 60, cycles = 10)
        assertEquals(1200, s.totalSeconds)
        s.editCycles(3)
        assertEquals(6, s.rounds)
        assertEquals(360, s.totalSeconds)
        assertTrue(s.isValid)
        s.editCycles(0)
        assertEquals(1, s.cycles)
    }

    @Test fun testDurationEditWritesOnlyWhenItDividesIntoCompleteCycles() {
        val s = EmomEditorState(listOf(reps("a", "A"), reps("b", "B")), interval = 60, cycles = 10)
        s.editDurationMinutes("7.5")
        assertEquals(10, s.cycles)
        assertFalse(s.isValid)
        assertEquals(
            "7:30 doesn't divide into complete cycles. Each cycle is 2 min (2 exercises × 60s). Try 6 min or 8 min.",
            s.durationMessage
        )
        s.editDurationMinutes("8")
        assertTrue(s.isValid)
        assertEquals(4, s.cycles)
        assertEquals(8, s.rounds)
        assertEquals(480, s.totalSeconds)
    }

    @Test fun testIntervalEditFollowsTheLastPrimaryValue() {
        val s = EmomEditorState(listOf(reps("a", "A"), reps("b", "B")), interval = 60, cycles = 2)
        s.editDurationMinutes("8")
        assertEquals(4, s.cycles)
        // Duration was last → re-run the duration rule with the new interval (may become invalid).
        s.editInterval(45)
        assertEquals(4, s.cycles)
        assertEquals(
            "8 min doesn't divide into complete cycles. Each cycle is 1:30 (2 exercises × 45s). Try 7:30 or 9 min.",
            s.durationMessage
        )
        s.editInterval(40)
        assertTrue(s.isValid)
        assertEquals(6, s.cycles)
        // Cycles last → keep cycles, recompute duration.
        s.editCycles(3)
        s.editInterval(60)
        assertEquals(3, s.cycles)
        assertEquals(360, s.totalSeconds)
        assertNull(s.durationMessage)
    }

    @Test fun testDurationShorterThanOneCycleUsesThePartialCycleMessage() {
        val s = EmomEditorState(listOf(reps("a", "A"), reps("b", "B"), reps("c", "C")), interval = 60, cycles = 1)
        s.editDurationMinutes("2")
        assertEquals(
            "2 min doesn't divide into complete cycles. Each cycle is 3 min (3 exercises × 60s). Try 3 min.",
            s.durationMessage
        )
        assertEquals(1, s.cycles)
        val single = EmomEditorState(listOf(reps("a", "A")), interval = 45, cycles = 4)
        single.editDurationMinutes("1")
        assertEquals(
            "1 min doesn't divide into complete cycles. Each cycle is 0:45 (1 exercise × 45s). Try 0:45 or 1:30.",
            single.durationMessage
        )
        single.editDurationMinutes("1.5")
        assertTrue(single.isValid)
        assertEquals(2, single.cycles)
    }

    @Test fun testTimeTargetLongerThanIntervalBlocksDone() {
        val plank = timed("p", "Plank", 90)
        val s = EmomEditorState(listOf(reps("a", "Push-ups"), plank), interval = 60, cycles = 2)
        assertEquals(listOf("Plank is set to 1:30, longer than the 60s interval. Shorten the target or lengthen the interval."), s.messages)
        assertFalse(s.isValid)
        s.editInterval(90)
        assertTrue(s.isValid)

        val row = emomRow(listOf(reps("a", "Push-ups"), plank), interval = 60, cycles = 2)
        val phases = listOf(WorkoutPhase(id = "p", name = "Main", taskRows = listOf(row)))
        assertEquals("Plank is set to 1:30, longer than the 60s interval. Shorten the target or lengthen the interval.", EmomMath.saveBlockingMessage(phases))
        assertNull(EmomMath.saveBlockingMessage(listOf(WorkoutPhase(id = "p", name = "Main", taskRows = listOf(emomRow(listOf(plank), interval = 90))))))
    }

    @Test fun testMinutesParsingAndFormatting() {
        assertEquals(450, EmomEditorState.parseMinutes("7.5"))
        assertEquals(450, EmomEditorState.parseMinutes("7,5"))
        assertEquals(1200, EmomEditorState.parseMinutes(" 20 "))
        assertNull(EmomEditorState.parseMinutes(""))
        assertNull(EmomEditorState.parseMinutes("0"))
        assertEquals("20", EmomEditorState.formatMinutes(1200))
        assertEquals("7.5", EmomEditorState.formatMinutes(450))
        assertEquals("7.25", EmomEditorState.formatMinutes(435))
    }

    // MARK: - Instruction text (§4)

    @Test fun testInstructionText() {
        assertEquals("Complete 20 bodyweight squats every 60 seconds for 10 rounds.",
            EmomText.instruction(EmomPlan(60, 10, 1), listOf(reps("a", "Bodyweight Squats", 20))))
        assertEquals("Complete 30 seconds of plank every 60 seconds for 10 rounds.",
            EmomText.instruction(EmomPlan(60, 10, 1), listOf(timed("a", "Plank", 30))))
        assertEquals("Complete 1 minute 30 seconds of plank every 2 minutes for 4 rounds.",
            EmomText.instruction(EmomPlan(120, 4, 1), listOf(timed("a", "Plank", 90))))
        assertEquals("Complete the exercise every 60 seconds for 2 rounds.", EmomText.instruction(EmomPlan(60, 2, 1), emptyList()))
        assertEquals("Complete burpees every 90 seconds for 1 round.",
            EmomText.instruction(EmomPlan(90, 1, 1), listOf(reps("a", "Burpees", 0))))
        assertEquals("Complete one exercise every 60 seconds. Repeat both exercises for 10 cycles.",
            EmomText.instruction(EmomPlan(60, 20, 2), emptyList()))
        assertEquals("Complete one exercise every 2 minutes. Repeat all three exercises for four cycles.",
            EmomText.instruction(EmomPlan(120, 12, 3), emptyList()))
        assertEquals("Complete one exercise every 45 seconds. Repeat all four exercises for one cycle.",
            EmomText.instruction(EmomPlan(45, 4, 4), emptyList()))
        assertEquals("Complete one exercise every 150 seconds. Repeat all 11 exercises for one cycle.",
            EmomText.instruction(EmomPlan(150, 11, 11), emptyList()))
        assertEquals("nine", EmomText.numberWord(9))
        assertEquals("10", EmomText.numberWord(10))
        assertEquals("How this EMOM works", EmomText.heading)
    }

    // MARK: - Builder writes (§3.1, §3.2)

    @Test fun testCreationDefaultsAreSixtySecondsOneCycle() {
        val rows = listOf(TaskRow(task = WorkoutTask(id = "a", name = "A", isMovement = true, movementId = "ma", sets = 3, reps = 10)),
            TaskRow(task = WorkoutTask(id = "b", name = "B", isMovement = true, movementId = "mb", sets = 3, reps = 10)))
        val out = WorkoutRowOps.groupRows(rows, setOf("a", "b"), ExerciseGroupType.Interval, IntervalMode.EMOM, RestTypeId.passive)
        val row = out.single()
        val plan = EmomMath.plan(row)!!
        assertEquals(EmomPlan(60, 2, 2), plan)
        assertEquals("2:00", IntervalMath.formatTime(plan.totalSeconds))
        assertEquals(1, row.task!!.intervalSets)
        assertNull(row.task!!.intervalSetRestSeconds)

        val hiit = TaskRow(task = reps("a", "A").copy(intervalMode = IntervalMode.HIIT, rounds = 6), isInterval = true, intervalMode = IntervalMode.HIIT,
            advancedTasks = listOf(reps("b", "B"), reps("c", "C")))
        assertEquals(EmomPlan(60, 3, 3), EmomMath.plan(WorkoutRowOps.withIntervalMode(hiit, IntervalMode.EMOM)))
    }

    @Test fun testRemovingAMemberKeepsIntervalAndCycles() {
        val base = emomRow(listOf(reps("a", "A"), reps("b", "B"), reps("c", "C")), interval = 45, cycles = 4)
        val row = base.copy(task = base.task!!.copy(coachNotes = "Stay tall"))
        assertEquals(12, row.totalRounds)
        val afterMember = WorkoutRowOps.removedGroupMember(listOf(row), "b").single()
        assertEquals(EmomPlan(45, 8, 2), EmomMath.plan(afterMember))
        assertEquals(360, afterMember.task!!.intervalWindowSeconds)
        // Removing the lead promotes the next member with the block config (coach notes included).
        val afterLead = WorkoutRowOps.removedGroupMember(listOf(row), "a").single()
        assertEquals("b", afterLead.task!!.id)
        assertEquals("Stay tall", afterLead.task!!.coachNotes)
        assertEquals(EmomPlan(45, 8, 2), EmomMath.plan(afterLead))
        // Reorder changes nothing.
        val moved = WorkoutRowOps.movedGroupMember(row, "b", up = true)
        assertEquals(EmomPlan(45, 12, 3), EmomMath.plan(moved))
        assertEquals("Stay tall", moved.task!!.coachNotes)
    }

    @Test fun testCoachNotesTravelOnTheWireAndSurviveAServerEchoWithoutThem() {
        val row = emomRow(listOf(reps("a", "A"), reps("b", "B"))).let { it.copy(task = it.task!!.copy(coachNotes = "  Breathe  ")) }
        val input = WorkoutTaskInputs.makeInput(row.task!!, "phase", 0, "g")
        assertEquals("Breathe", input.coachNotes)
        assertNull(WorkoutTaskInputs.makeInput(reps("b", "B"), "phase", 1, "g").coachNotes)

        val local = Workout(id = "w", phases = listOf(WorkoutPhase(id = "p", name = "Main", taskRows = listOf(row))))
        val serverRow = row.copy(task = row.task!!.copy(coachNotes = null))
        val server = Workout(id = "w", phases = listOf(WorkoutPhase(id = "p", name = "Main", taskRows = listOf(serverRow))))
        val merged = WorkoutRowOps.mergePreservingPhases(local, server)
        assertEquals("  Breathe  ", merged.phases[0].taskRows[0].task!!.coachNotes)

        // Decoding a payload without the key (backend column not deployed yet) is fine.
        val decoded = com.fitnesslink.fit.model.ModelGson.instance.fromJson("""{"id":"x","name":"X"}""", WorkoutTask::class.java)
        assertNull(decoded.coachNotes)
        val withNotes = com.fitnesslink.fit.model.ModelGson.instance.fromJson("""{"id":"x","coachNotes":"Go"}""", WorkoutTask::class.java)
        assertEquals("Go", withNotes.coachNotes)
    }

    // MARK: - Runner (§7.2)

    private fun mixedRow(cycles: Int = 2) = emomRow(listOf(reps("a", "Push-ups", 20), timed("b", "Plank", 30)), interval = 60, cycles = cycles)

    @Test fun testRepsRoundDoneTimedAutoCompleteUnconfirmedAndSkipped() {
        val h = harness(mixedRow())
        val r = h.runner
        assertEquals(IntervalPhase.WORK, r.phase)
        assertEquals(1, r.currentRound)
        assertEquals(60, r.secondsRemaining)
        assertTrue(r.canConfirmEmomRound)
        assertEquals(240, r.blockRemainingSeconds)

        h.advance(20)
        r.markEmomRoundDone()
        assertEquals(IntervalPhase.REST, r.phase)
        assertEquals(40, r.secondsRemaining)
        assertEquals(EmomRoundRecord(1, EmomRoundOutcome.CONFIRMED, 20), r.emomOutcome(1))
        assertEquals(40 + 180, r.blockRemainingSeconds)
        h.finishPhase()

        // Round 2: timed 30 s → no Round Done, WORK 30 then REST 30.
        assertEquals(2, r.currentRound)
        assertEquals(IntervalPhase.WORK, r.phase)
        assertEquals(30, r.secondsRemaining)
        assertFalse(r.canConfirmEmomRound)
        assertEquals(30 + 30 + 120, r.blockRemainingSeconds)
        h.finishPhase()
        assertEquals(IntervalPhase.REST, r.phase)
        assertEquals(30, r.secondsRemaining)
        assertEquals(EmomRoundRecord(2, EmomRoundOutcome.AUTO_COMPLETED, 30), r.emomOutcome(2))
        h.finishPhase()

        // Round 3: reps, the interval expires → unconfirmed.
        assertEquals(3, r.currentRound)
        h.finishPhase()
        assertEquals(EmomRoundOutcome.UNCONFIRMED, r.emomOutcome(3)?.outcome)
        assertEquals(4, r.currentRound)

        // Round 4 (last): Next → skipped, and the caller leaves the block.
        assertFalse(r.emomNext())
        assertEquals(EmomRoundOutcome.SKIPPED, r.emomOutcome(4)?.outcome)

        val writes = EmomOutcomeWriter.writes(r.emomPlan!!, r.memberTasks.map { it.id }, r.emomOutcomes)
        assertEquals(listOf(
            EmomProgressWrite("a", 1, SetState.COMPLETED, 20, "manual"),
            EmomProgressWrite("b", 1, SetState.COMPLETED, 30, "auto"),
            EmomProgressWrite("a", 2, SetState.INCOMPLETE),
            EmomProgressWrite("b", 2, SetState.SKIPPED)
        ), writes)
    }

    @Test fun testRoundDoneNeedsMoreThanOneSecondLeft() {
        val h = harness(mixedRow())
        h.advance(59)
        assertEquals(1, h.runner.secondsRemaining)
        assertFalse(h.runner.canConfirmEmomRound)
        h.runner.markEmomRoundDone()
        assertEquals(IntervalPhase.WORK, h.runner.phase)
        assertNull(h.runner.emomOutcome(1))
    }

    @Test fun testTimedTargetEqualToIntervalGoesStraightToTheNextRound() {
        val h = harness(emomRow(listOf(timed("a", "Plank", 60), timed("b", "Hollow", null)), cycles = 1))
        h.finishPhase()
        assertEquals(2, h.runner.currentRound)
        assertEquals(IntervalPhase.WORK, h.runner.phase)
        // Unset target = the whole interval.
        assertEquals(60, h.runner.secondsRemaining)
        h.finishPhase()
        assertEquals(IntervalPhase.DONE, h.runner.phase)
        assertEquals(EmomRoundOutcome.AUTO_COMPLETED, h.runner.emomOutcome(2)?.outcome)
        assertTrue(h.events.none { it == Event.TransitionToRest })
    }

    @Test fun testRunnerElapsedEqualsTotalRoundsTimesInterval() {
        val row = mixedRow(cycles = 3)
        val h = harness(row, prepare = 3)
        var elapsed = 0
        var steps = 0
        while (h.runner.phase != IntervalPhase.DONE && steps < 100) {
            if (h.runner.phase != IntervalPhase.PREPARE) elapsed += h.runner.secondsRemaining
            h.finishPhase(); steps += 1
        }
        assertEquals(360, elapsed)
        assertEquals(360, IntervalMath.blockSeconds(EmomMath.plan(row)!!.intervalPlan))
        assertEquals(Event.BlockComplete, h.events.last())
    }

    @Test fun testPreviousRestartsCurrentOrPreviousRoundAndLeavesFromRoundOne() {
        val h = harness(mixedRow())
        val r = h.runner
        // ≤ 3 s into round 1 → leave the block.
        h.advance(2)
        assertFalse(r.back())
        // > 3 s into round 1's WORK → restart round 1.
        h.advance(10)
        assertTrue(r.back())
        assertEquals(1, r.currentRound)
        assertEquals(60, r.secondsRemaining)
        // Round Done then Previous during REST: > 3 s into the round → restart it (outcome cleared).
        h.advance(5); r.markEmomRoundDone()
        assertEquals(IntervalPhase.REST, r.phase)
        assertTrue(r.back())
        assertEquals(IntervalPhase.WORK, r.phase)
        assertNull(r.emomOutcome(1))
        h.finishPhase()
        assertEquals(2, r.currentRound)
        // ≤ 3 s into round 2 → previous round's FULL interval; its outcome is cleared.
        h.advance(1)
        assertTrue(r.back())
        assertEquals(1, r.currentRound)
        assertEquals(IntervalPhase.WORK, r.phase)
        assertEquals(60, r.secondsRemaining)
        assertNull(r.emomOutcome(1))
        // REST of a timed round is > 3 s into it → that round restarts.
        h.finishPhase(); h.finishPhase()
        assertEquals(IntervalPhase.REST, r.phase)
        assertEquals(2, r.currentRound)
        assertTrue(r.back())
        assertEquals(2, r.currentRound)
        assertEquals(IntervalPhase.WORK, r.phase)
        assertEquals(30, r.secondsRemaining)
        assertEquals(180, r.blockRemainingSeconds)
    }

    @Test fun testNextKeepsCompletedOutcomesAndStartsAFullInterval() {
        val h = harness(mixedRow(), prepare = 5)
        val r = h.runner
        assertEquals(IntervalPhase.PREPARE, r.phase)
        assertTrue(r.emomNext())
        assertEquals(1, r.currentRound)
        assertEquals(IntervalPhase.WORK, r.phase)
        h.advance(10); r.markEmomRoundDone()
        assertTrue(r.emomNext())
        assertEquals(EmomRoundOutcome.CONFIRMED, r.emomOutcome(1)?.outcome)
        assertEquals(2, r.currentRound)
        assertEquals(30, r.secondsRemaining)
        assertTrue(r.emomNext())
        assertEquals(EmomRoundOutcome.SKIPPED, r.emomOutcome(2)?.outcome)
        // jumpToNextWork (Up Next tap) is the same as Next.
        assertTrue(r.jumpToNextWork())
        assertEquals(4, r.currentRound)
        assertFalse(r.jumpToNextWork())
    }

    @Test fun testPauseFreezesTheIntervalAndResumeContinues() {
        val h = harness(mixedRow())
        val r = h.runner
        h.advance(15)
        r.pause()
        h.advance(100)
        assertEquals(45, r.secondsRemaining)
        assertEquals(1, r.currentRound)
        r.markEmomRoundDone()
        assertEquals(IntervalPhase.REST, r.phase)
        assertEquals(45, r.secondsRemaining)
        assertEquals(15, r.emomOutcome(1)?.completionSeconds)
        r.resume()
        h.advance(44)
        assertEquals(IntervalPhase.REST, r.phase)
        h.advance(1)
        assertEquals(2, r.currentRound)
    }

    @Test fun testSnapshotRoundTripKeepsOutcomes() {
        val row = mixedRow()
        val h = harness(row)
        h.advance(12); h.runner.markEmomRoundDone()
        val snap = h.runner.snapshot()
        assertEquals(listOf(EmomRoundRecord(1, EmomRoundOutcome.CONFIRMED, 12)), snap.emomOutcomes)
        val json = com.fitnesslink.fit.model.ModelGson.instance.toJson(snap)
        val decoded = com.fitnesslink.fit.model.ModelGson.instance.fromJson(json, IntervalRunner.Snapshot::class.java)
        val restored = IntervalRunner(row, decoded) { h.clock.now }
        restored.reconcile(h.clock.now)
        assertEquals(IntervalPhase.REST, restored.phase)
        assertEquals(48, restored.secondsRemaining)
        assertEquals(EmomRoundOutcome.CONFIRMED, restored.emomOutcome(1)?.outcome)
    }

    @Test fun testOutcomesNeverDowngradeACompletedSet() {
        val progress = SessionProgress()
        progress.complete("a", 1, 20, "manual")
        progress.recordUnfinished("a", 1, SetState.SKIPPED)
        assertEquals(SetState.COMPLETED, progress.state("a", 1))
        assertEquals("manual", progress.entry("a", 1).completionMethod)
        progress.recordUnfinished("a", 2, SetState.INCOMPLETE)
        assertEquals(SetState.INCOMPLETE, progress.state("a", 2))
        val decoded = SessionProgress.decode(progress.encodedJSON())!!
        assertEquals("manual", decoded.entry("a", 1).completionMethod)
        assertEquals(20, decoded.entry("a", 1).durationSeconds)
        // Rows written before EMOM v2 decode with no method.
        val legacy = SessionProgress.decode("""{"entries":{"a#1":{"state":"completed","metricsLogged":false}}}""")!!
        assertNull(legacy.entry("a", 1).completionMethod)
    }

    // MARK: - Voice coach (§8)

    @Test fun testRoundStartLinesCyclePrefixAndFinalRound() {
        val h = harness(emomRow(listOf(reps("a", "Push-ups", 20), timed("b", "Plank", 30)), cycles = 2))
        // Round 1 from the lead-in: "Let's work!" then the round line, one announcement.
        assertEquals(EmomCue(listOf("Let's work!", "Round 1. Push-ups. 20 reps. Let's go!"), EmomCuePriority.roundStart), h.cues.first())
        h.cues.clear()
        h.finishPhase()
        assertEquals("Round 2. Plank. 30 seconds. You've got this!", h.lines().single())
        h.cues.clear()
        h.finishPhase(); h.finishPhase()
        assertEquals("Cycle 2. Round 3. Push-ups. 20 reps. Time to work!", h.lines().last())
        h.cues.clear()
        h.finishPhase()
        assertEquals("Last round! Give it all you've got! Plank. 30 seconds!", h.lines().last())
        h.cues.clear()
        h.finishPhase(); h.finishPhase()
        assertEquals("Block complete. Way to work!", h.lines().last())
    }

    @Test fun testStartersRotateInOrderAndNeverRepeat() {
        val h = harness(emomRow(listOf(reps("a", "Squat", 10)), cycles = 7))
        repeat(5) { h.finishPhase() }
        val starters = h.lines().filter { it.startsWith("Round") }.map { it.substringAfterLast(". ") }
        assertEquals(listOf("Let's go!", "You've got this!", "Time to work!", "Let's get after it!", "Let's go!", "You've got this!"), starters)
        assertTrue(starters.zipWithNext().none { (a, b) -> a == b })
    }

    @Test fun testSingleRoundBlockHasNoLastRoundLineAndUnsetTargetIsOmitted() {
        val h = harness(emomRow(listOf(reps("a", "Burpees", 0)), cycles = 1))
        assertEquals(listOf("Let's work!", "Round 1. Burpees. Let's go!"), h.lines())
    }

    @Test fun testTenSecondWarningAndCountdown() {
        val h = harness(emomRow(listOf(reps("a", "Push-ups", 20), timed("b", "Plank", 12)), cycles = 1))
        h.cues.clear()
        for (i in 1..60) h.advance(1)
        val work1 = h.lines()
        assertEquals(listOf("Ten seconds remaining!", "5", "4", "3", "2", "1"), work1.dropLast(1))
        assertTrue(work1.last().startsWith("Last round!"))
        // A 12 s timed WORK is shorter than 15 s: numbers only, no warning; no countdown in REST.
        h.cues.clear()
        for (i in 1..60) h.advance(1)
        assertEquals(listOf("5", "4", "3", "2", "1", "Block complete. Way to work!"), h.lines().filter { it != "Time! Nice work." && it != "Great job! Take your rest." })
        assertTrue(h.cues.none { it.lines.contains("Ten seconds remaining!") })
        assertTrue(h.cues.all { it.level <= EmomCuePriority.completion })
    }

    @Test fun testCompletionPraiseDependsOnRestLength() {
        val coach = EmomCoach()
        fun ctx(outcome: EmomRoundOutcome, rest: Int) = EmomCueContext(IntervalPhase.REST, 1, 4, 2, "X", null, outcome == EmomRoundOutcome.AUTO_COMPLETED, rest, rest, outcome)
        fun say(outcome: EmomRoundOutcome, rest: Int) = coach.cues(Event.TransitionToRest, ctx(outcome, rest)).flatMap { it.lines }
        assertEquals(listOf("Nice work! Take your rest."), say(EmomRoundOutcome.CONFIRMED, 40))
        assertEquals(listOf("Well done! Catch your breath."), say(EmomRoundOutcome.CONFIRMED, 20))
        assertEquals(listOf("Strong work! You've earned that rest."), say(EmomRoundOutcome.CONFIRMED, 25))
        assertEquals(listOf("Nice work! Take your rest."), say(EmomRoundOutcome.CONFIRMED, 25))
        assertEquals(listOf("Great job! Recover."), say(EmomRoundOutcome.CONFIRMED, 19))
        assertEquals(listOf("Nice work! Recover."), say(EmomRoundOutcome.CONFIRMED, 8))
        assertEquals(emptyList<String>(), say(EmomRoundOutcome.CONFIRMED, 7))
        assertEquals(listOf("Time! Nice work."), say(EmomRoundOutcome.AUTO_COMPLETED, 30))
        assertEquals(listOf("Great job! Take your rest."), say(EmomRoundOutcome.AUTO_COMPLETED, 30))
        assertEquals(listOf("Work complete. Recover."), say(EmomRoundOutcome.AUTO_COMPLETED, 30))
        assertEquals(listOf("Time! Recover."), say(EmomRoundOutcome.AUTO_COMPLETED, 10))
        assertEquals(emptyList<String>(), say(EmomRoundOutcome.AUTO_COMPLETED, 5))
        assertEquals(EmomCuePriority.completion, coach.cues(Event.TransitionToRest, ctx(EmomRoundOutcome.CONFIRMED, 30)).single().level)
        // No praise for unconfirmed / skipped rounds.
        assertEquals(emptyList<String>(), say(EmomRoundOutcome.UNCONFIRMED, 30))
        assertEquals(emptyList<String>(), say(EmomRoundOutcome.SKIPPED, 30))
    }

    @Test fun testNextAndPreviousSpeakOnlyTheRoundStartLine() {
        val h = harness(mixedRow())
        h.cues.clear()
        h.runner.emomNext()
        assertEquals(listOf("Round 2. Plank. 30 seconds. You've got this!"), h.lines())
        assertEquals(EmomCuePriority.roundStart, h.cues.single().level)
        h.cues.clear()
        h.runner.back()
        assertEquals(listOf("Round 1. Push-ups. 20 reps. Time to work!"), h.lines())
    }

    @Test fun testRunnerLeadInCountsWhatFits() {
        val five = harness(mixedRow(), prepare = 5)
        repeat(5) { five.advance(1) }
        assertEquals(listOf("5", "4", "3", "2", "1", "Let's work!", "Round 1. Push-ups. 20 reps. Let's go!"), five.lines())
        val ten = harness(mixedRow(), prepare = 10)
        repeat(10) { ten.advance(1) }
        assertEquals(listOf("5", "4", "3", "2", "1", "Let's work!", "Round 1. Push-ups. 20 reps. Let's go!"), ten.lines())
        val three = harness(mixedRow(), prepare = 3)
        repeat(3) { three.advance(1) }
        assertEquals(listOf("3", "2", "1", "Let's work!", "Round 1. Push-ups. 20 reps. Let's go!"), three.lines())
    }

    @Test fun testSpeechArbitrationDropsLowerPriority() {
        assertEquals(CoachSpeechArbiter.Decision.SPEAK, CoachSpeechArbiter.decide(EmomCuePriority.completion, null))
        assertEquals(CoachSpeechArbiter.Decision.DROP, CoachSpeechArbiter.decide(EmomCuePriority.completion, EmomCuePriority.countdown))
        assertEquals(CoachSpeechArbiter.Decision.DROP, CoachSpeechArbiter.decide(EmomCuePriority.motivation, EmomCuePriority.completion))
        assertEquals(CoachSpeechArbiter.Decision.INTERRUPT, CoachSpeechArbiter.decide(EmomCuePriority.countdown, EmomCuePriority.countdown))
        assertEquals(CoachSpeechArbiter.Decision.INTERRUPT, CoachSpeechArbiter.decide(EmomCuePriority.transition, EmomCuePriority.completion))
        assertTrue(VoicePhrase.Coach("Round 1", 2).isShort)
        assertEquals("Round 1", VoicePhrase.Coach("Round 1", 2).text)
    }

    @Test fun testPrepCountdownFromFiveAndBeepsStayOnTheLastThree() {
        val policy = SessionCuePolicy()
        assertEquals(listOf(SessionCueOutput.Speak(VoicePhrase.Five)), policy.outputs(SessionCueEvent.PrepTick(5, spokenFrom = 5)))
        assertEquals(listOf(SessionCueOutput.Speak(VoicePhrase.Four)), policy.outputs(SessionCueEvent.PrepTick(4, spokenFrom = 5)))
        val three = policy.outputs(SessionCueEvent.PrepTick(3, spokenFrom = 5))
        assertTrue(three.contains(SessionCueOutput.Beep))
        assertTrue(three.contains(SessionCueOutput.Speak(VoicePhrase.Three)))
        assertEquals(emptyList<SessionCueOutput>(), policy.outputs(SessionCueEvent.PrepTick(5)))
        assertNull(VoicePhrase.countdown(5))
        assertEquals(VoicePhrase.Five, VoicePhrase.countdownFromFive(5))
        // The interval router keeps beeps / haptics for EMOM but leaves the voice to the coach.
        val muted = IntervalCueRouter.outputs(Event.Tick(2), finalRound = false, leadsToWork = true, policy = policy, voice = false)
        assertTrue(muted.contains(SessionCueOutput.Beep))
        assertTrue(muted.none { it is SessionCueOutput.Speak })
        assertTrue(IntervalCueRouter.outputs(Event.TransitionToWork, false, false, policy, voice = false).none { it is SessionCueOutput.Speak })
    }

    // MARK: - Playlist (§6)

    @Test fun testPlaylistShowsOneCirclePerEmomMember() {
        assertEquals(emptyList<SetState>(), EmomPlaylist.circleStates(emptyList()))
        assertEquals(listOf(SetState.COMPLETED), EmomPlaylist.circleStates(listOf(SetState.COMPLETED, SetState.COMPLETED)))
        assertEquals(listOf(SetState.UPCOMING), EmomPlaylist.circleStates(listOf(SetState.COMPLETED, SetState.UPCOMING)))
        assertEquals(listOf(SetState.SKIPPED), EmomPlaylist.circleStates(listOf(SetState.SKIPPED, SetState.UPCOMING)))
        // The checkbox completes every occurrence (required = cycles).
        val row = emomRow(listOf(reps("a", "A"), reps("b", "B")), cycles = 3)
        val required = SessionProgress.requiredIterations(listOf(row.task!!) + row.advancedTasks)
        val progress = SessionProgress()
        progress.completeExercise("a", required.getValue("a"))
        assertEquals(listOf(SetState.COMPLETED, SetState.COMPLETED, SetState.COMPLETED), progress.states("a", 3))
        assertEquals(listOf(SetState.UPCOMING, SetState.UPCOMING, SetState.UPCOMING), progress.states("b", 3))
        assertNotNull(required["b"])
    }
}
