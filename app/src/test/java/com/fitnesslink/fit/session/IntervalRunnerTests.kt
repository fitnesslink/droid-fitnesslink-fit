package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.IntervalPhase
import com.fitnesslink.fit.model.IntervalPreset
import com.fitnesslink.fit.model.ModelGson
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.IntervalRunner.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of iOS `IntervalRunnerTests`: an injected clock drives `reconcile`, and `onEvent` is captured. */
class IntervalRunnerTests {

    private class FakeClock(var now: Long = 1_700_000_000_000L) {
        fun advance(seconds: Double) { now += (seconds * 1000).toLong() }
    }

    private class Harness(val runner: IntervalRunner, val clock: FakeClock, val events: MutableList<Event>) {
        fun clearEvents() = events.clear()
        fun advance(seconds: Double) { clock.advance(seconds); runner.reconcile(clock.now) }
        fun advance(seconds: Int) = advance(seconds.toDouble())
        fun finishPhase() = advance(runner.secondsRemaining.toDouble())
        fun runToCompletion(): List<Pair<IntervalPhase, Int>> {
            val out = mutableListOf<Pair<IntervalPhase, Int>>()
            var steps = 0
            while (runner.phase != IntervalPhase.DONE && steps < 10_000) {
                if (runner.phase != IntervalPhase.PREPARE) out.add(runner.phase to runner.secondsRemaining)
                finishPhase()
                steps += 1
            }
            assertTrue("runner never reached DONE", steps < 10_000)
            return out
        }
    }

    private fun makeLead(
        mode: IntervalMode, work: Int? = null, rest: Int? = null, window: Int? = null, rounds: Int? = null,
        totalRounds: Int = 0, sets: Int? = null, setRest: Int? = null
    ) = WorkoutTask(
        id = "lead", name = "Burpees", isMovement = true, movementId = "mvt-1", isInterval = true, groupId = "g1",
        intervalMode = mode, intervalWorkSeconds = work, intervalRestSeconds = rest, intervalWindowSeconds = window,
        intervalSets = sets, intervalSetRestSeconds = setRest, rounds = rounds, totalRounds = totalRounds
    )

    private fun makeGroup(
        mode: IntervalMode, work: Int? = null, rest: Int? = null, window: Int? = null, rounds: Int = 0,
        sets: Int? = null, setRest: Int? = null, exerciseCount: Int = 1
    ): TaskRow {
        val lead = makeLead(mode, work, rest, window, rounds = if (rounds > 0) rounds else null, totalRounds = rounds, sets = sets, setRest = setRest)
        val siblings = (2..exerciseCount).map { i ->
            WorkoutTask(id = "ex$i", name = "Exercise $i", isMovement = true, movementId = "mvt-$i", isInterval = true, groupId = "g1")
        }
        return TaskRow(task = lead, advanced = "Interval", totalRounds = rounds, isInterval = true, intervalMode = mode, advancedTasks = siblings)
    }

    private fun harness(group: TaskRow, prepare: Int = 3, start: Boolean = true): Harness {
        val clock = FakeClock()
        val events = mutableListOf<Event>()
        val runner = IntervalRunner(group, prepare) { clock.now }
        runner.onEvent = { events.add(it) }
        if (start) runner.start()
        return Harness(runner, clock, events)
    }

    private fun transitions(events: List<Event>) = events.filter {
        it == Event.TransitionToWork || it == Event.TransitionToRest || it == Event.TransitionToBetweenSetsRest || it == Event.BlockComplete
    }

    private fun blockSeconds(group: TaskRow) = IntervalMath.blockSeconds(IntervalMath.plan(group.task!!, group.totalRounds, group.intervalMode))

    private fun restore(h: Harness, group: TaskRow, snapshot: IntervalRunner.Snapshot? = null): Harness {
        val snap = snapshot ?: h.runner.snapshot()
        val events = mutableListOf<Event>()
        val runner = IntervalRunner(group, snap) { h.clock.now }
        runner.onEvent = { events.add(it) }
        runner.reconcile(h.clock.now)
        return Harness(runner, h.clock, events)
    }

    // MARK: - Round counts

    @Test fun testFiveRoundsRunExactlyFiveWorkPhasesWithNoTrailingRest() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 45, rest = 15, rounds = 5, sets = 1))
        val phases = h.runToCompletion()
        assertEquals(5, phases.count { it.first == IntervalPhase.WORK })
        assertEquals(4, phases.count { it.first == IntervalPhase.REST })
        assertEquals(IntervalPhase.WORK, phases.last().first)
        assertEquals(5 * 45 + 4 * 15, phases.sumOf { it.second })
        assertEquals(Event.BlockComplete, h.events.last())
        assertEquals(5, h.events.count { it == Event.TransitionToWork })
    }

    @Test fun testRoundsComeFromRoundsThenTotalRoundsNeverSix() {
        var lead = makeLead(IntervalMode.HIIT, work = 30, rest = 15, rounds = 5)
        var row = TaskRow(task = lead, advanced = "Interval", isInterval = true, intervalMode = IntervalMode.HIIT)
        assertEquals(5, IntervalRunner(row).roundsTotal)
        lead = lead.copy(rounds = null, totalRounds = 4); row = row.copy(task = lead)
        assertEquals(4, IntervalRunner(row).roundsTotal)
        lead = lead.copy(totalRounds = 0); row = row.copy(task = lead, totalRounds = 3)
        assertEquals(3, IntervalRunner(row).roundsTotal)
        row = row.copy(totalRounds = 0)
        val fallback = IntervalRunner(row).roundsTotal
        assertEquals(IntervalPreset.hiitDefault.rounds, fallback)
        assertTrue(fallback >= 1)
        assertEquals(5, harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 5)).runToCompletion().count { it.first == IntervalPhase.WORK })
    }

    @Test fun testMultiSetUsesIntervalSetRestSecondsAndEndsOnWork() {
        val group = makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3, sets = 2, setRest = 45)
        val h = harness(group)
        val phases = h.runToCompletion()
        val between = phases.filter { it.first == IntervalPhase.BETWEEN_SETS_REST }
        assertEquals(1, between.size)
        assertEquals(45, between.first().second)
        assertEquals(IntervalPhase.WORK, phases.last().first)
        assertEquals(blockSeconds(group), phases.sumOf { it.second })
        assertEquals(1, h.events.count { it == Event.SetComplete })
    }

    @Test fun testEmomRunsEveryMinuteToItsBoundary() {
        val phases = harness(makeGroup(IntervalMode.EMOM, work = 40, window = 180, sets = 1)).runToCompletion()
        assertEquals(listOf(IntervalPhase.WORK, IntervalPhase.REST, IntervalPhase.WORK, IntervalPhase.REST, IntervalPhase.WORK, IntervalPhase.REST), phases.map { it.first })
        assertEquals(listOf(40, 20, 40, 20, 40, 20), phases.map { it.second })
        assertEquals(180, phases.sumOf { it.second })
    }

    @Test fun testAmrapIsOneWindowAndCountsTappedRounds() {
        val h = harness(makeGroup(IntervalMode.AMRAP, window = 600, exerciseCount = 2))
        h.finishPhase()
        assertEquals(IntervalPhase.WORK, h.runner.phase)
        assertEquals(600, h.runner.secondsRemaining)
        assertEquals(0, h.runner.currentExerciseIndex)
        h.runner.markAmrapRound()
        assertEquals(1, h.runner.roundsCompleted)
        assertEquals(1, h.runner.currentExerciseIndex)
        assertTrue(h.events.contains(Event.RoundComplete))
        h.finishPhase()
        assertEquals(IntervalPhase.DONE, h.runner.phase)
        assertEquals(Event.BlockComplete, h.events.last())
    }

    // MARK: - Previous inside a block

    @Test fun testBackWithinThreeSecondsStepsToPreviousPhase() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3, exerciseCount = 2))
        h.finishPhase(); h.finishPhase()
        assertEquals(IntervalPhase.REST, h.runner.phase)
        h.advance(1)
        assertTrue(h.runner.back())
        assertEquals(IntervalPhase.WORK, h.runner.phase)
        assertEquals(1, h.runner.currentRound)
        assertEquals(30, h.runner.secondsRemaining)

        h.finishPhase(); h.finishPhase()
        assertEquals(2, h.runner.currentRound)
        assertEquals(1, h.runner.currentExerciseIndex)
        h.advance(2)
        assertTrue(h.runner.back())
        assertEquals(IntervalPhase.REST, h.runner.phase)
        assertEquals(1, h.runner.currentRound)
        assertEquals(0, h.runner.currentExerciseIndex)
        assertEquals(15, h.runner.secondsRemaining)
    }

    @Test fun testBackAfterThreeSecondsRestartsPhase() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3))
        h.finishPhase(); h.finishPhase(); h.finishPhase()
        assertEquals(2, h.runner.currentRound)
        h.advance(5)
        assertEquals(25, h.runner.secondsRemaining)
        assertTrue(h.runner.back())
        assertEquals(IntervalPhase.WORK, h.runner.phase)
        assertEquals(2, h.runner.currentRound)
        assertEquals(30, h.runner.secondsRemaining)
        h.advance(30)
        assertEquals(IntervalPhase.REST, h.runner.phase)
    }

    @Test fun testBackAtBlockStartReturnsFalse() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3))
        assertEquals(IntervalPhase.PREPARE, h.runner.phase)
        assertFalse(h.runner.back())
        h.finishPhase()
        h.advance(1)
        assertFalse(h.runner.back())
        assertEquals(IntervalPhase.WORK, h.runner.phase)
        assertEquals(1, h.runner.currentRound)
    }

    @Test fun testBackAcrossSetBoundaryLandsOnBetweenSetsRest() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 2, sets = 2, setRest = 30))
        h.finishPhase(); h.finishPhase(); h.finishPhase(); h.finishPhase()
        assertEquals(IntervalPhase.BETWEEN_SETS_REST, h.runner.phase)
        h.finishPhase()
        assertEquals(2, h.runner.currentSet)
        assertEquals(1, h.runner.currentRound)
        h.advance(1)
        assertTrue(h.runner.back())
        assertEquals(IntervalPhase.BETWEEN_SETS_REST, h.runner.phase)
        assertEquals(1, h.runner.currentSet)
        assertEquals(2, h.runner.currentRound)
        assertEquals(30, h.runner.secondsRemaining)
        assertTrue(h.runner.back())
        assertEquals(IntervalPhase.WORK, h.runner.phase)
        assertEquals(1, h.runner.currentSet)
        assertEquals(2, h.runner.currentRound)
    }

    // MARK: - Wall clock

    @Test fun testReconcileFastForwardEmitsOneTransitionForLandingPhase() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3))
        h.finishPhase()
        h.clearEvents()
        h.advance(80)
        assertEquals(IntervalPhase.REST, h.runner.phase)
        assertEquals(2, h.runner.currentRound)
        assertEquals(10, h.runner.secondsRemaining)
        assertEquals(listOf<Event>(Event.TransitionToRest), transitions(h.events))
        assertFalse("no countdown beeps replayed for the skipped phases", h.events.any { it is Event.Tick })
    }

    @Test fun testReconcilePastTheEndEmitsOnlyBlockComplete() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3))
        h.finishPhase()
        h.clearEvents()
        h.advance(10_000)
        assertEquals(IntervalPhase.DONE, h.runner.phase)
        assertEquals(0, h.runner.secondsRemaining)
        assertEquals(listOf<Event>(Event.BlockComplete), transitions(h.events))
        assertEquals(0, h.runner.blockRemainingSeconds)
    }

    @Test fun testReconcileAcrossSetBoundaryReplaysSetComplete() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 2, sets = 2, setRest = 60))
        h.finishPhase()
        h.clearEvents()
        h.advance(30 + 15 + 30 + 10)
        assertEquals(IntervalPhase.BETWEEN_SETS_REST, h.runner.phase)
        assertEquals(50, h.runner.secondsRemaining)
        assertEquals(listOf(Event.SetComplete, Event.TransitionToBetweenSetsRest), h.events)
    }

    @Test fun testTicksArePublishedOncePerSecondAndOnlyOnChange() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 5, rest = 5, rounds = 2))
        h.finishPhase()
        h.clearEvents()
        h.advance(0.3)
        assertEquals(5, h.runner.secondsRemaining)
        assertTrue(h.events.isEmpty())
        h.advance(0.8)
        assertEquals(listOf<Event>(Event.Tick(4)), h.events)
        h.advance(0.1)
        assertEquals(listOf<Event>(Event.Tick(4)), h.events)
        h.advance(3)
        assertEquals(Event.Tick(1), h.events.last())
    }

    @Test fun testPauseResumePreservesSubSecondRemainder() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 2))
        h.finishPhase()
        h.advance(10.4)
        assertEquals(20, h.runner.secondsRemaining)
        h.runner.pause()
        assertTrue(h.runner.isPaused)
        h.advance(100)
        assertEquals(20, h.runner.secondsRemaining)
        assertEquals(IntervalPhase.WORK, h.runner.phase)
        h.runner.resume()
        assertFalse(h.runner.isPaused)
        h.advance(19.5)
        assertEquals(IntervalPhase.WORK, h.runner.phase)
        assertEquals(1, h.runner.secondsRemaining)
        h.advance(0.1)
        assertEquals(IntervalPhase.REST, h.runner.phase)
        assertEquals(15, h.runner.secondsRemaining)
    }

    @Test fun testSkipWhilePausedLandsOnNextPhaseStillPaused() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 2))
        h.finishPhase()
        h.runner.pause()
        h.runner.skip()
        assertEquals(IntervalPhase.REST, h.runner.phase)
        assertTrue(h.runner.isPaused)
        assertEquals(15, h.runner.secondsRemaining)
        h.advance(100)
        assertEquals("paused runner must not advance", IntervalPhase.REST, h.runner.phase)
    }

    // MARK: - Events

    @Test fun testStartEmitsPrepareStartedWithConfiguredSeconds() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 2), prepare = 10)
        assertEquals(IntervalPhase.PREPARE, h.runner.phase)
        assertEquals(10, h.runner.secondsRemaining)
        assertEquals(listOf<Event>(Event.PrepareStarted), h.events)
        assertEquals(10, h.runner.phaseDuration)
        assertEquals(0.0, h.runner.phaseProgress, 0.001)
    }

    @Test fun testSkipEmitsOneTransitionPerPhase() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 2))
        h.clearEvents()
        h.runner.skip()
        assertEquals(listOf<Event>(Event.TransitionToWork), h.events)
        h.clearEvents()
        h.runner.skip()
        assertEquals(listOf<Event>(Event.TransitionToRest), h.events)
    }

    @Test fun testFinalRoundEmittedOnLastRoundOfLastSet() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 2, sets = 2, setRest = 30))
        h.runToCompletion()
        assertEquals(1, h.events.count { it == Event.FinalRound })
        val idx = h.events.indexOf(Event.FinalRound)
        assertEquals(Event.TransitionToWork, h.events[idx - 1])
        val workIndices = h.events.indices.filter { h.events[it] == Event.TransitionToWork }
        assertEquals(idx, workIndices.last() + 1)
    }

    @Test fun testIsFinalRoundFlag() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 2, sets = 1))
        h.finishPhase()
        assertFalse(h.runner.isFinalRound)
        h.finishPhase(); h.finishPhase()
        assertEquals(2, h.runner.currentRound)
        assertTrue(h.runner.isFinalRound)
        assertEquals(1, h.events.count { it == Event.FinalRound })
    }

    @Test fun testSingleRoundBlockNeverSaysFinalRound() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 1, sets = 1))
        h.runToCompletion()
        assertFalse(h.events.contains(Event.FinalRound))
    }

    @Test fun testCancelEmitsNothing() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3))
        h.finishPhase()
        h.clearEvents()
        h.runner.cancel()
        assertEquals(IntervalPhase.DONE, h.runner.phase)
        assertTrue(h.events.isEmpty())
        h.advance(1_000)
        assertTrue(h.events.isEmpty())
    }

    // MARK: - Block timers

    @Test fun testBlockRemainingEqualsBlockSecondsAtStartAndZeroAtDone() {
        val group = makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3, sets = 2, setRest = 60)
        val h = harness(group)
        val total = blockSeconds(group)
        assertEquals(total, h.runner.blockTotalSeconds)
        assertEquals(total, h.runner.blockRemainingSeconds)
        h.finishPhase()
        assertEquals(total, h.runner.blockRemainingSeconds)
        h.advance(10)
        assertEquals(total - 10, h.runner.blockRemainingSeconds)
        h.runToCompletion()
        assertEquals(0, h.runner.blockRemainingSeconds)
    }

    @Test fun testPhaseProgressWalksZeroToOne() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 20, rest = 10, rounds = 2))
        h.finishPhase()
        assertEquals(0.0, h.runner.phaseProgress, 0.001)
        h.advance(5)
        assertEquals(0.25, h.runner.phaseProgress, 0.001)
        h.advance(14)
        assertEquals(0.95, h.runner.phaseProgress, 0.001)
    }

    // MARK: - Exercise rotation / Up Next

    @Test fun testExerciseIndexFollowsRoundAndResetsEachSet() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 10, rest = 5, rounds = 4, sets = 2, setRest = 20, exerciseCount = 3))
        val seen = mutableListOf<Int>()
        var steps = 0
        while (h.runner.phase != IntervalPhase.DONE && steps < 1_000) {
            if (h.runner.phase == IntervalPhase.WORK) seen.add(h.runner.currentExerciseIndex)
            h.finishPhase(); steps += 1
        }
        assertEquals(listOf(0, 1, 2, 0, 0, 1, 2, 0), seen)
    }

    @Test fun testNextExerciseAndSlot() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 10, rest = 5, rounds = 2, sets = 2, setRest = 20, exerciseCount = 2))
        assertEquals("lead", h.runner.nextExercise?.id)
        h.finishPhase()
        assertEquals("ex2", h.runner.nextExercise?.id)
        assertEquals(1 to 2, h.runner.nextSlot)
        h.finishPhase()
        assertEquals("ex2", h.runner.nextExercise?.id)
        h.finishPhase()
        assertEquals("next set restarts at the first member", "lead", h.runner.nextExercise?.id)
        assertEquals(2 to 1, h.runner.nextSlot)
        h.finishPhase()
        assertEquals("lead", h.runner.nextExercise?.id)
        h.finishPhase(); h.finishPhase(); h.finishPhase()
        assertTrue(h.runner.isFinalRound)
        assertNull(h.runner.nextExercise)
        assertNull(h.runner.nextSlot)
    }

    @Test fun testJumpToLastRoundLandsOnFinalWork() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3, sets = 2, setRest = 60, exerciseCount = 2))
        h.clearEvents()
        h.runner.jumpToLastRound()
        assertEquals(IntervalPhase.WORK, h.runner.phase)
        assertEquals(2, h.runner.currentSet)
        assertEquals(3, h.runner.currentRound)
        assertEquals(0, h.runner.currentExerciseIndex)
        assertEquals(30, h.runner.secondsRemaining)
        assertTrue(h.runner.isFinalRound)
        assertEquals(30, h.runner.blockRemainingSeconds)
        assertEquals(listOf(Event.TransitionToWork, Event.FinalRound), h.events)
        h.finishPhase()
        assertEquals(IntervalPhase.DONE, h.runner.phase)
        assertEquals(Event.BlockComplete, h.events.last())
    }

    // MARK: - Snapshot / restore

    @Test fun testSnapshotRoundTripMidRestRestoresPositionAndContinues() {
        val group = makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3, sets = 2, setRest = 60, exerciseCount = 2)
        val h = harness(group)
        h.finishPhase(); h.finishPhase(); h.finishPhase(); h.finishPhase()
        h.advance(5)
        assertEquals(IntervalPhase.REST, h.runner.phase)
        assertEquals(2, h.runner.currentRound)
        assertEquals(1, h.runner.currentExerciseIndex)

        val snap = h.runner.snapshot()
        assertEquals("g1", snap.groupId)
        assertEquals(IntervalPhase.REST, snap.phase)
        assertEquals(1, snap.currentSet)
        assertEquals(2, snap.currentRound)
        assertEquals(1, snap.currentExerciseIndex)
        assertEquals(15, snap.phaseDuration)
        assertNotNull(snap.phaseEndMs)
        assertNull(snap.pausedRemainingMs)
        assertEquals(3, snap.prepareSeconds)
        h.runner.cancel()

        val r = restore(h, group, snap)
        assertFalse(r.runner.isPaused)
        assertEquals(IntervalPhase.REST, r.runner.phase)
        assertEquals(1, r.runner.currentSet)
        assertEquals(2, r.runner.currentRound)
        assertEquals(1, r.runner.currentExerciseIndex)
        assertEquals(10, r.runner.secondsRemaining)
        assertEquals(15, r.runner.phaseDuration)
        assertEquals(h.runner.blockTotalSeconds - (30 + 15 + 30 + 5), r.runner.blockRemainingSeconds)
        assertTrue(r.events.isEmpty())

        r.advance(10)
        assertEquals(IntervalPhase.WORK, r.runner.phase)
        assertEquals(3, r.runner.currentRound)
        assertEquals(0, r.runner.currentExerciseIndex)
        assertEquals(30, r.runner.secondsRemaining)
        assertEquals(listOf<Event>(Event.TransitionToWork), transitions(r.events))
        val rest = r.runToCompletion()
        assertEquals(listOf(IntervalPhase.WORK, IntervalPhase.BETWEEN_SETS_REST, IntervalPhase.WORK, IntervalPhase.REST, IntervalPhase.WORK, IntervalPhase.REST, IntervalPhase.WORK), rest.map { it.first })
        assertEquals(Event.BlockComplete, r.events.last())
    }

    @Test fun testSnapshotRestoredAfterTimeAwayFastForwardsInWallClock() {
        val group = makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3, exerciseCount = 2)
        val h = harness(group)
        h.finishPhase(); h.finishPhase()
        h.advance(5)
        val snap = h.runner.snapshot()
        h.runner.cancel()

        h.clock.advance(12.0)
        val events = mutableListOf<Event>()
        val runner = IntervalRunner(group, snap) { h.clock.now }
        runner.onEvent = { events.add(it) }
        runner.reconcile(h.clock.now)
        assertEquals(IntervalPhase.WORK, runner.phase)
        assertEquals(2, runner.currentRound)
        assertEquals(1, runner.currentExerciseIndex)
        assertEquals(28, runner.secondsRemaining)
        assertEquals(listOf<Event>(Event.TransitionToWork), transitions(events))

        h.clock.advance(10_000.0)
        val lateEvents = mutableListOf<Event>()
        val late = IntervalRunner(group, snap) { h.clock.now }
        late.onEvent = { lateEvents.add(it) }
        late.reconcile(h.clock.now)
        assertEquals(IntervalPhase.DONE, late.phase)
        assertEquals(listOf<Event>(Event.BlockComplete), lateEvents)
    }

    @Test fun testPausedSnapshotRestoresPausedWithRemainderIntact() {
        val group = makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 2)
        val h = harness(group)
        h.finishPhase()
        h.advance(10.4)
        h.runner.pause()
        val snap = h.runner.snapshot()
        assertNull(snap.phaseEndMs)
        assertEquals(19_600L, snap.pausedRemainingMs)
        h.runner.cancel()

        h.clock.advance(500.0)
        val r = restore(h, group, snap)
        assertTrue(r.runner.isPaused)
        assertEquals(IntervalPhase.WORK, r.runner.phase)
        assertEquals(1, r.runner.currentRound)
        assertEquals(20, r.runner.secondsRemaining)
        r.advance(100)
        assertEquals(IntervalPhase.WORK, r.runner.phase)
        assertEquals(20, r.runner.secondsRemaining)
        assertTrue(r.events.isEmpty())

        r.runner.resume()
        assertFalse(r.runner.isPaused)
        r.advance(19.5)
        assertEquals(IntervalPhase.WORK, r.runner.phase)
        assertEquals(1, r.runner.secondsRemaining)
        r.advance(0.1)
        assertEquals(IntervalPhase.REST, r.runner.phase)
        assertEquals(15, r.runner.secondsRemaining)
    }

    @Test fun testSnapshotDuringPrepareRestoresPrepare() {
        val group = makeGroup(IntervalMode.TABATA, work = 20, rest = 10, rounds = 8, exerciseCount = 2)
        val h = harness(group, prepare = 10)
        h.advance(4)
        assertEquals(6, h.runner.secondsRemaining)
        val r = restore(h, group)
        assertEquals(IntervalPhase.PREPARE, r.runner.phase)
        assertEquals(6, r.runner.secondsRemaining)
        assertEquals(10, r.runner.phaseDuration)
        assertEquals(r.runner.blockTotalSeconds, r.runner.blockRemainingSeconds)
        r.advance(6)
        assertEquals(IntervalPhase.WORK, r.runner.phase)
        assertEquals(1, r.runner.currentRound)
        assertEquals(0, r.runner.currentExerciseIndex)
    }

    @Test fun testAmrapSnapshotKeepsScoreAndRotation() {
        val group = makeGroup(IntervalMode.AMRAP, window = 600, exerciseCount = 3)
        val h = harness(group)
        h.finishPhase()
        h.runner.markAmrapRound(); h.runner.markAmrapRound()
        h.runner.amrapPartialReps = 4
        h.advance(100)
        val r = restore(h, group)
        assertEquals(2, r.runner.roundsCompleted)
        assertEquals(4, r.runner.amrapPartialReps)
        assertEquals(2, r.runner.currentExerciseIndex)
        assertEquals(500, r.runner.secondsRemaining)
    }

    @Test fun testSnapshotIsCodableRoundTrip() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3, sets = 2, setRest = 60))
        h.finishPhase(); h.advance(7)
        val snap = h.runner.snapshot()
        val json = ModelGson.instance.toJson(snap)
        val decoded = ModelGson.instance.fromJson(json, IntervalRunner.Snapshot::class.java)
        assertEquals(snap, decoded)
    }

    // MARK: - Voice 3-2-1 gate

    @Test fun testLeadsIntoWorkIsTrueOnlyBeforeWork() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 2, sets = 2, setRest = 30))
        assertEquals(IntervalPhase.PREPARE, h.runner.phase)
        assertTrue(h.runner.leadsIntoWork)
        h.finishPhase()
        assertFalse(h.runner.leadsIntoWork)
        h.finishPhase()
        assertTrue(h.runner.leadsIntoWork)
        h.finishPhase()
        assertFalse(h.runner.leadsIntoWork)
        h.finishPhase()
        assertEquals(IntervalPhase.BETWEEN_SETS_REST, h.runner.phase)
        assertTrue(h.runner.leadsIntoWork)
        h.finishPhase(); h.finishPhase(); h.finishPhase()
        assertTrue(h.runner.isFinalRound)
        assertFalse(h.runner.leadsIntoWork)
        h.finishPhase()
        assertEquals(IntervalPhase.DONE, h.runner.phase)
        assertFalse(h.runner.leadsIntoWork)
    }

    @Test fun testLeadsIntoWorkHandlesZeroRestAndEmomLastMinute() {
        val noRest = harness(makeGroup(IntervalMode.HIIT, work = 30, rest = 0, rounds = 3))
        noRest.finishPhase()
        assertTrue(noRest.runner.leadsIntoWork)
        noRest.finishPhase(); noRest.finishPhase()
        assertFalse(noRest.runner.leadsIntoWork)

        val emom = harness(makeGroup(IntervalMode.EMOM, work = 40, window = 120))
        emom.finishPhase()
        assertFalse(emom.runner.leadsIntoWork)
        emom.finishPhase()
        assertTrue(emom.runner.leadsIntoWork)
        emom.finishPhase(); emom.finishPhase()
        assertEquals(IntervalPhase.REST, emom.runner.phase)
        assertFalse(emom.runner.leadsIntoWork)

        val amrap = harness(makeGroup(IntervalMode.AMRAP, window = 600, sets = 2, setRest = 60))
        amrap.finishPhase()
        assertFalse(amrap.runner.leadsIntoWork)
        amrap.finishPhase()
        assertEquals(IntervalPhase.BETWEEN_SETS_REST, amrap.runner.phase)
        assertTrue(amrap.runner.leadsIntoWork)
    }

    // MARK: - Up Next tap

    @Test fun testJumpToNextWorkGoesToTheNextRoundSetOrOutOfTheBlock() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 20, rest = 10, rounds = 2, sets = 2, setRest = 30, exerciseCount = 2), prepare = 3)
        val r = h.runner
        assertEquals(IntervalPhase.PREPARE, r.phase)
        assertTrue(r.jumpToNextWork())
        assertEquals(IntervalPhase.WORK, r.phase)
        assertEquals(1, r.currentSet)
        assertEquals(1, r.currentRound)
        assertEquals(20, r.secondsRemaining)

        h.advance(5)
        assertTrue(r.jumpToNextWork())
        assertEquals(IntervalPhase.WORK, r.phase)
        assertEquals(2, r.currentRound)
        assertEquals(1, r.currentExerciseIndex)

        h.clearEvents()
        assertTrue(r.jumpToNextWork())
        assertEquals(IntervalPhase.WORK, r.phase)
        assertEquals(2, r.currentSet)
        assertEquals(1, r.currentRound)
        assertTrue(h.events.contains(Event.SetComplete))

        h.finishPhase()
        assertEquals(IntervalPhase.REST, r.phase)
        assertTrue(r.jumpToNextWork())
        assertEquals(2, r.currentRound)
        assertTrue(r.isFinalRound)
        assertNull(r.nextSlot)
        assertFalse(r.jumpToNextWork())
        assertEquals(IntervalPhase.WORK, r.phase)
    }

    @Test fun testJumpToNextWorkWhilePausedLandsFrozen() {
        val h = harness(makeGroup(IntervalMode.HIIT, work = 20, rest = 10, rounds = 3), prepare = 0)
        val r = h.runner
        h.finishPhase()
        assertEquals(IntervalPhase.REST, r.phase)
        r.pause()
        assertTrue(r.jumpToNextWork())
        assertEquals(IntervalPhase.WORK, r.phase)
        assertEquals(2, r.currentRound)
        assertTrue(r.isPaused)
        h.advance(30)
        assertEquals(20, r.secondsRemaining)
        r.resume()
        h.advance(5)
        assertEquals(15, r.secondsRemaining)
    }

    @Test fun testEmomRoundDoneKeepsTheMinuteBoundary() {
        val h = harness(makeGroup(IntervalMode.EMOM, work = 40, window = 120))
        h.finishPhase()
        h.advance(25)
        assertEquals(15, h.runner.secondsRemaining)
        h.runner.markEmomRoundDone()
        assertEquals(IntervalPhase.REST, h.runner.phase)
        assertEquals(15 + 20, h.runner.secondsRemaining)
        assertEquals(35 + 60, h.runner.blockRemainingSeconds)
        h.advance(34)
        assertEquals(IntervalPhase.REST, h.runner.phase)
        h.advance(1)
        assertEquals(IntervalPhase.WORK, h.runner.phase)
        assertEquals(2, h.runner.currentRound)
        assertEquals(40, h.runner.secondsRemaining)
    }
}
