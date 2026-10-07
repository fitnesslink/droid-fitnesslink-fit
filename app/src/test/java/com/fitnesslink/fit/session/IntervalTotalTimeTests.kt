package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.IntervalPhase
import com.fitnesslink.fit.model.IntervalPlan
import com.fitnesslink.fit.model.IntervalPreset
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.WorkoutTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of iOS `IntervalTotalTimeTests`: pins `IntervalMath.blockSeconds` (the "Total Time") to
 * `IntervalRunner`'s actual elapsed time, driven synchronously with `skip()`.
 */
class IntervalTotalTimeTests {

    private fun makeLead(
        mode: IntervalMode, work: Int? = null, rest: Int? = null, window: Int? = null, rounds: Int? = null,
        totalRounds: Int = 0, sets: Int? = null, setRest: Int? = null, legacyRoundRest: Int? = null, isInterval: Boolean = true
    ) = WorkoutTask(
        id = "lead", name = "Burpees", isMovement = true, movementId = "mvt-1", isInterval = isInterval, groupId = "g1",
        intervalMode = mode, intervalWorkSeconds = work, intervalRestSeconds = rest, intervalWindowSeconds = window,
        intervalSets = sets, intervalSetRestSeconds = setRest, roundRestSeconds = legacyRoundRest, rounds = rounds, totalRounds = totalRounds
    )

    private fun makeGroup(
        mode: IntervalMode, work: Int? = null, rest: Int? = null, window: Int? = null, rounds: Int = 0,
        sets: Int? = null, setRest: Int? = null, legacyRoundRest: Int? = null
    ): TaskRow {
        val lead = makeLead(mode, work, rest, window, rounds = if (rounds > 0) rounds else null, totalRounds = rounds, sets = sets, setRest = setRest, legacyRoundRest = legacyRoundRest)
        return TaskRow(task = lead, advanced = "Interval", totalRounds = rounds, isInterval = true, intervalMode = mode)
    }

    private fun plan(group: TaskRow): IntervalPlan = IntervalMath.plan(group.task!!, rowTotalRounds = group.totalRounds, mode = group.intervalMode)
    private fun blockSeconds(group: TaskRow) = IntervalMath.blockSeconds(plan(group))

    /** Drives the runner phase-by-phase with `skip()` and records every non-prepare phase with its duration. */
    private fun runnerPhases(group: TaskRow): List<Pair<IntervalPhase, Int>> {
        var now = 1_700_000_000_000L
        val runner = IntervalRunner(group, clock = { now })
        runner.start()
        val out = mutableListOf<Pair<IntervalPhase, Int>>()
        var steps = 0
        while (runner.phase != IntervalPhase.DONE && steps < 10_000) {
            if (runner.phase != IntervalPhase.PREPARE) out.add(runner.phase to runner.secondsRemaining)
            now += 1 // keep anchors moving forward
            runner.skip()
            steps += 1
        }
        runner.stop()
        assertTrue("runner never reached DONE", steps < 10_000)
        return out
    }

    private fun runnerElapsedSeconds(group: TaskRow) = runnerPhases(group).sumOf { it.second }

    private fun assertHelperMatchesRunner(group: TaskRow) {
        assertEquals(blockSeconds(group), runnerElapsedSeconds(group))
    }

    // MARK: - HIIT

    @Test fun testHiitSingleSetHasNoTrailingRest() {
        val group = makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3, sets = 1)
        assertEquals(120, blockSeconds(group))
        assertHelperMatchesRunner(group)
        val phases = runnerPhases(group)
        assertEquals(listOf(IntervalPhase.WORK, IntervalPhase.REST, IntervalPhase.WORK, IntervalPhase.REST, IntervalPhase.WORK), phases.map { it.first })
    }

    @Test fun testHiitFiveRoundsRunsExactlyFiveWorkPhases() {
        val group = makeGroup(IntervalMode.HIIT, work = 45, rest = 15, rounds = 5, sets = 1)
        val phases = runnerPhases(group)
        assertEquals(5, phases.count { it.first == IntervalPhase.WORK })
        assertEquals(IntervalPhase.WORK, phases.last().first)
        assertEquals(5 * 45 + 4 * 15, blockSeconds(group))
        assertHelperMatchesRunner(group)
    }

    @Test fun testHiitMultiSetIncludesBetweenSetsRest() {
        val group = makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3, sets = 2, setRest = 60)
        assertEquals(300, blockSeconds(group))
        assertHelperMatchesRunner(group)
        val phases = runnerPhases(group)
        assertEquals(1, phases.count { it.first == IntervalPhase.BETWEEN_SETS_REST })
        assertEquals("block ends on work, not rest", IntervalPhase.WORK, phases.last().first)
    }

    @Test fun testHiitMultiSetDefaultsBetweenSetsRestTo60() {
        val group = makeGroup(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3, sets = 2, setRest = null)
        assertEquals(IntervalMath.defaultSetRestSeconds, plan(group).betweenSetsRestSeconds)
        assertEquals(300, blockSeconds(group))
        assertHelperMatchesRunner(group)
    }

    @Test fun testHiitZeroRestSkipsRestPhases() {
        val group = makeGroup(IntervalMode.HIIT, work = 30, rest = 0, rounds = 4, sets = 1)
        assertEquals(120, blockSeconds(group))
        assertHelperMatchesRunner(group)
        assertFalse(runnerPhases(group).any { it.first == IntervalPhase.REST })
    }

    // MARK: - Tabata

    @Test fun testTabataReadsStoredRounds() {
        val eight = makeGroup(IntervalMode.TABATA, work = 20, rest = 10, rounds = 8, sets = 1)
        assertEquals(230, blockSeconds(eight))
        assertHelperMatchesRunner(eight)

        val six = makeGroup(IntervalMode.TABATA, work = 20, rest = 10, rounds = 6, sets = 1)
        assertEquals(6, plan(six).roundsPerSet)
        assertEquals(170, blockSeconds(six))
        assertHelperMatchesRunner(six)
        assertEquals(6, runnerPhases(six).count { it.first == IntervalPhase.WORK })
    }

    @Test fun testTabataMultiSet() {
        val group = makeGroup(IntervalMode.TABATA, work = 20, rest = 10, rounds = 8, sets = 3, setRest = 60)
        assertEquals(230 * 3 + 120, blockSeconds(group))
        assertHelperMatchesRunner(group)
    }

    @Test fun testTabataCustomWorkRestRounds() {
        val group = makeGroup(IntervalMode.TABATA, work = 30, rest = 15, rounds = 6, sets = 1)
        val p = plan(group)
        assertEquals(30, p.workSeconds)
        assertEquals(15, p.restSeconds)
        assertEquals(6, p.roundsPerSet)
        assertEquals(255, blockSeconds(group))
        assertHelperMatchesRunner(group)
        val phases = runnerPhases(group)
        assertEquals(List(6) { 30 }, phases.filter { it.first == IntervalPhase.WORK }.map { it.second })
        assertEquals(List(5) { 15 }, phases.filter { it.first == IntervalPhase.REST }.map { it.second })

        val bare = makeGroup(IntervalMode.TABATA)
        assertEquals(IntervalPreset.tabata.workSeconds, plan(bare).workSeconds)
        assertEquals(IntervalPreset.tabata.restSeconds, plan(bare).restSeconds)
        assertEquals(IntervalPreset.tabata.rounds, plan(bare).roundsPerSet)
        assertEquals(230, blockSeconds(bare))
    }

    // MARK: - EMOM / AMRAP

    @Test fun testEmomSingleSetEqualsWindow() {
        val group = makeGroup(IntervalMode.EMOM, work = 40, window = 180, sets = 1)
        assertEquals(180, blockSeconds(group))
        assertHelperMatchesRunner(group)
    }

    @Test fun testEmomMultiSet() {
        val group = makeGroup(IntervalMode.EMOM, work = 40, window = 180, sets = 2, setRest = 30)
        assertEquals(390, blockSeconds(group))
        assertHelperMatchesRunner(group)
    }

    @Test fun testAmrapEqualsWindow() {
        val group = makeGroup(IntervalMode.AMRAP, window = 600)
        assertEquals(600, blockSeconds(group))
        assertHelperMatchesRunner(group)
    }

    // MARK: - Between-sets rest source

    @Test fun testLegacyRoundRestFallsBackForIntervalLeads() {
        val lead = makeLead(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3, sets = 2, setRest = null, legacyRoundRest = 90)
        assertEquals(90, lead.effectiveIntervalSetRestSeconds)
        assertEquals(90, IntervalMath.plan(lead).betweenSetsRestSeconds)
        val group = TaskRow(task = lead, advanced = "Interval", totalRounds = 3, isInterval = true, intervalMode = IntervalMode.HIIT)
        assertEquals(2 * 120 + 90, blockSeconds(group))
        assertHelperMatchesRunner(group)
    }

    @Test fun testExplicitSetRestWinsOverLegacy() {
        val lead = makeLead(IntervalMode.HIIT, work = 30, rest = 15, rounds = 3, sets = 2, setRest = 45, legacyRoundRest = 90)
        assertEquals(45, lead.effectiveIntervalSetRestSeconds)
        assertEquals(45, IntervalMath.plan(lead).betweenSetsRestSeconds)
    }

    @Test fun testNonIntervalTaskDoesNotFallBackToRoundRest() {
        val superset = makeLead(IntervalMode.HIIT, rounds = 3, legacyRoundRest = 90, isInterval = false).copy(intervalMode = null, isSuperset = true)
        assertNull(superset.effectiveIntervalSetRestSeconds)
    }

    @Test fun testRoundsComeFromRoundsThenTotalRoundsThenRow() {
        var lead = makeLead(IntervalMode.HIIT, work = 30, rest = 15, rounds = 5, totalRounds = 0)
        assertEquals(5, IntervalMath.plan(lead).roundsPerSet)
        lead = lead.copy(rounds = null, totalRounds = 4)
        assertEquals(4, IntervalMath.plan(lead).roundsPerSet)
        lead = lead.copy(totalRounds = 0)
        assertEquals(3, IntervalMath.plan(lead, rowTotalRounds = 3).roundsPerSet)
        assertEquals(IntervalPreset.hiitDefault.rounds, IntervalMath.plan(lead).roundsPerSet)
        assertTrue(IntervalMath.plan(lead).roundsPerSet >= 1)
    }

    @Test fun testRemainingWalksTheBlock() {
        val p = IntervalPlan(IntervalMode.HIIT, workSeconds = 30, restSeconds = 15, roundsPerSet = 3, sets = 2, betweenSetsRestSeconds = 60, windowSeconds = 120)
        val total = IntervalMath.blockSeconds(p)
        assertEquals(300, total)
        assertEquals(total, IntervalMath.remaining(p, 1, 1, IntervalPhase.PREPARE, 3))
        assertEquals(total, IntervalMath.remaining(p, 1, 1, IntervalPhase.WORK, 30))
        assertEquals(total - 30, IntervalMath.remaining(p, 1, 1, IntervalPhase.REST, 15))
        assertEquals(10 + 60 + 120, IntervalMath.remaining(p, 1, 3, IntervalPhase.WORK, 10))
        assertEquals(20 + 120, IntervalMath.remaining(p, 1, 3, IntervalPhase.BETWEEN_SETS_REST, 20))
        assertEquals(7, IntervalMath.remaining(p, 2, 3, IntervalPhase.WORK, 7))
        assertEquals(0, IntervalMath.remaining(p, 2, 3, IntervalPhase.DONE, 0))
    }

    @Test fun testModeDisplayNames() {
        assertEquals("Tabata", IntervalMode.TABATA.displayName)
        assertEquals("HIIT", IntervalMode.HIIT.displayName)
        assertEquals("EMOM", IntervalMode.EMOM.displayName)
        assertEquals("AMRAP", IntervalMode.AMRAP.displayName)
    }
}
