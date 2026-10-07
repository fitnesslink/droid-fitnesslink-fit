package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.session.SessionFixtures.at
import com.fitnesslink.fit.session.SessionFixtures.engine
import com.fitnesslink.fit.session.SessionFixtures.group
import com.fitnesslink.fit.session.SessionFixtures.intervalBlock
import com.fitnesslink.fit.session.SessionFixtures.movement
import com.fitnesslink.fit.session.SessionFixtures.restTask
import com.fitnesslink.fit.session.SessionTransitionEngine.PhaseChange
import com.fitnesslink.fit.session.SessionTransitionEngine.RestKind
import com.fitnesslink.fit.session.SessionTransitionEngine.Transition as T
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of iOS `SessionTransitionEngineTests` (PRD §6.10, FTNSSLNK-397). */
class SessionTransitionEngineTests {

    // MARK: - §6.10 straight sets

    @Test fun testNotFinalSetWithRestRestsThenNextSet() {
        val e = engine(listOf(movement("A", sets = 3, rest = 60), movement("B")))
        assertEquals(T.Rest(60, RestKind.BETWEEN_SETS, T.NextSet(at(0, set = 2))), e.next(at(0, set = 1)))
        assertEquals(T.Rest(60, RestKind.BETWEEN_SETS, T.NextSet(at(0, set = 3))), e.next(at(0, set = 2)))
    }

    @Test fun testNotFinalSetWithoutRestGoesStraightToNextSet() {
        val e = engine(listOf(movement("A", sets = 3, rest = 0), movement("B")))
        assertEquals(T.NextSet(at(0, set = 2)), e.next(at(0, set = 1)))
    }

    @Test fun testFinalSetWithMoreExercisesPreparesNextExercise() {
        val e = engine(listOf(movement("A", sets = 3, rest = 60), movement("B", sets = 3)))
        assertEquals(T.Prepare(at(1), null), e.next(at(0, set = 3)))
    }

    @Test fun testFinalSetOfFinalExerciseCompletesWorkout() {
        val e = engine(listOf(movement("A", sets = 2, rest = 45)))
        assertEquals(T.WorkoutComplete, e.next(at(0, set = 2)))
        assertEquals(T.Rest(45, RestKind.BETWEEN_SETS, T.NextSet(at(0, set = 2))), e.next(at(0, set = 1)))
    }

    @Test fun testSingleSetMovementPreparesNextImmediately() {
        val e = engine(listOf(movement("A", sets = 1, rest = 90), movement("B")))
        assertEquals(T.Prepare(at(1), null), e.next(at(0)))
    }

    @Test fun testZeroSetsTreatedAsSingleSet() {
        val e = engine(listOf(movement("A", sets = 0), movement("B")))
        assertEquals(T.Prepare(at(1), null), e.next(at(0)))
    }

    // MARK: - Rest blocks

    @Test fun testStandaloneRestBlockRunsThenStartsNextExerciseWithoutPrep() {
        val e = engine(listOf(movement("A"), restTask("R", 60), movement("B")))
        assertEquals(T.RestBlock(at(1)), e.next(at(0)))
        assertEquals(T.Step(at(2)), e.next(at(1)))
        assertTrue(e.endsOnRestBlock(1))
        assertFalse(e.endsOnRestBlock(0))
    }

    @Test fun testRestBlockAsLastTaskCompletesWorkout() {
        val e = engine(listOf(movement("A"), restTask("R", 60)))
        assertEquals(T.RestBlock(at(1)), e.next(at(0)))
        assertEquals(T.WorkoutComplete, e.next(at(1)))
    }

    // MARK: - Supersets

    @Test fun testSupersetWalksMembersThenRoundRestThenNextRound() {
        val e = engine(group(listOf("A1", "A2"), group = "s", rounds = 3, roundRest = 60) + movement("D"))
        assertEquals(T.Step(at(1, round = 1)), e.next(at(0, round = 1)))
        assertEquals(T.Rest(60, RestKind.BETWEEN_ROUNDS, T.Step(at(0, round = 2))), e.next(at(1, round = 1)))
        assertEquals(T.Rest(60, RestKind.BETWEEN_ROUNDS, T.Step(at(0, round = 3))), e.next(at(1, round = 2)))
        assertEquals(T.Prepare(at(2), null), e.next(at(1, round = 3)))
    }

    @Test fun testSupersetWithoutRoundRestFlipsRoundDirectly() {
        val e = engine(group(listOf("A1", "A2"), group = "s", rounds = 2) + movement("D"))
        assertEquals(T.Step(at(0, round = 2)), e.next(at(1, round = 1)))
    }

    @Test fun testSupersetAsFinalItemCompletesAfterLastRound() {
        val e = engine(group(listOf("A1", "A2"), group = "s", rounds = 2, roundRest = 30))
        assertEquals(T.WorkoutComplete, e.next(at(1, round = 2)))
    }

    // MARK: - Circuits

    @Test fun testCircuitMemberRestBetweenMembersButNotAfterLastMember() {
        val e = engine(group(listOf("A", "B", "C"), group = "c", rounds = 2, memberRest = 30, circuit = true) + movement("D"))
        assertEquals(T.Rest(30, RestKind.BETWEEN_MEMBERS, T.Step(at(1))), e.next(at(0)))
        assertEquals(T.Rest(30, RestKind.BETWEEN_MEMBERS, T.Step(at(2))), e.next(at(1)))
        assertEquals(T.Step(at(0, round = 2)), e.next(at(2, round = 1)))
        assertEquals(T.Prepare(at(3), null), e.next(at(2, round = 2)))
    }

    @Test fun testCircuitRoundRestOnlyBetweenRounds() {
        val e = engine(group(listOf("A", "B", "C"), group = "c", rounds = 2, roundRest = 45, circuit = true) + movement("D"))
        assertEquals(T.Rest(45, RestKind.BETWEEN_ROUNDS, T.Step(at(0, round = 2))), e.next(at(2, round = 1)))
        assertEquals(T.Prepare(at(3), null), e.next(at(2, round = 2)))
    }

    @Test fun testRestTaskInsideCircuitDoesNotRestTwice() {
        val tasks = group(listOf("A", "R1", "B"), group = "c", rounds = 2, circuit = true).toMutableList()
        tasks[1] = restTask("R1", 20, group = "c")
        val e = engine(tasks + movement("D"))
        assertEquals(T.Step(at(1)), e.next(at(0)))
        assertEquals(T.Step(at(2)), e.next(at(1)))
    }

    @Test fun testRestTaskAsLastMemberIsTheRoundRest() {
        val tasks = group(listOf("A", "B", "R"), group = "c", rounds = 2, roundRest = 45, circuit = true).toMutableList()
        tasks[2] = restTask("R", 20, group = "c")
        val e = engine(tasks + movement("D"))
        assertEquals("the authored Rest task already rested — no round rest on top", T.Step(at(0, round = 2)), e.next(at(2, round = 1)))
    }

    @Test fun testMemberRestOnLastMemberIsIgnoredEvenWhenSet() {
        val e = engine(group(listOf("A", "B"), group = "s", rounds = 2, memberRest = 30, roundRest = 10) + movement("D"))
        assertEquals(T.Rest(10, RestKind.BETWEEN_ROUNDS, T.Step(at(0, round = 2))), e.next(at(1, round = 1)))
    }

    @Test fun testGroupRoundsFallBackToTotalRoundsThenOne() {
        val tasks = group(listOf("A", "B"), group = "s", rounds = 3).toMutableList()
        tasks[0] = tasks[0].copy(rounds = null)
        assertEquals(3, engine(tasks).groupRounds(0..1))
        tasks[0] = tasks[0].copy(totalRounds = 0)
        assertEquals(1, engine(tasks).groupRounds(0..1))
    }

    // MARK: - Interval blocks

    @Test fun testIntervalBlockEndPreparesNextItem() {
        val e = engine(intervalBlock(listOf("Burpees", "Squats"), group = "i") + movement("D"))
        assertEquals(T.Prepare(at(2), null), e.next(at(0)))
        assertEquals(T.Prepare(at(2), null), e.next(at(1)))
        assertEquals(T.Prepare(at(2), null), e.afterItem(1))
    }

    @Test fun testIntervalBlockAsFinalItemCompletes() {
        val e = engine(listOf(movement("A")) + intervalBlock(listOf("Burpees"), group = "i", mode = IntervalMode.TABATA))
        assertEquals(T.Prepare(at(1), null), e.next(at(0)))
        assertEquals(T.WorkoutComplete, e.next(at(1)))
    }

    @Test fun testEveryIntervalModeLandsOnPrepareAtBlockEntry() {
        for (mode in IntervalMode.entries) {
            val e = engine(listOf(movement("A")) + intervalBlock(listOf("X"), group = "i", mode = mode) + movement("D"))
            assertEquals("$mode", T.Prepare(at(1), null), e.next(at(0)))
            assertEquals("$mode", T.Prepare(at(2), null), e.next(at(1)))
        }
    }

    // MARK: - Phase changes

    @Test fun testPrepareCarriesPhaseChangeWhenNextItemStartsNewPhase() {
        val e = engine(listOf(
            movement("Arm Circles", phase = "Warmup"),
            movement("Squat", sets = 3, phase = "Exercises"),
            movement("Lunge", sets = 3, phase = "Exercises"),
            movement("Stretch", phase = "Cool Down")
        ))
        assertEquals(T.Prepare(at(1), PhaseChange("Warm-Up", "Exercises", "2 Exercises • 3 Sets Each")), e.next(at(0)))
        assertEquals("same phase → plain prep", T.Prepare(at(2), null), e.next(at(1, set = 3)))
        assertEquals(T.Prepare(at(3), PhaseChange("Exercises", "Cool Down", "1 Exercise")), e.next(at(2, set = 3)))
    }

    @Test fun testPhaseIdentityPrefersWorkoutPhaseIdOverName() {
        val e = engine(listOf(
            movement("A", phase = "Block", phaseId = "P1"),
            movement("B", phase = "Block", phaseId = "p1"),
            movement("C", phase = "Block", phaseId = "P2")
        ))
        assertFalse(e.isNewPhase(0, 1))
        assertTrue(e.isNewPhase(1, 2))
    }

    @Test fun testUnnamedNextPhaseIsNeverATransition() {
        val e = engine(listOf(movement("A", phase = "Warmup"), movement("B", phase = "")))
        assertNull(e.phaseChange(0, 1))
    }

    @Test fun testRestBlockBeforePhaseChangeCarriesThePhaseChangeItself() {
        val e = engine(listOf(movement("A", phase = "Warmup"), restTask("R", 60, phase = "Warmup"), movement("B", phase = "Exercises")))
        assertEquals(T.RestBlock(at(1)), e.next(at(0)))
        assertEquals(T.Step(at(2)), e.next(at(1)))
        val change = e.phaseChange(1, 2)
        assertEquals("Warm-Up", change?.completedPhaseName)
        assertEquals("Exercises", change?.nextPhaseName)
    }

    @Test fun testPhaseChangeOutOfGroupAndIntoIntervalBlock() {
        val tasks = group(listOf("A1", "A2"), group = "s", rounds = 2, phase = "Strength") +
            intervalBlock(listOf("Burpees"), group = "i", mode = IntervalMode.HIIT, work = 30, rest = 15, rounds = 4, sets = 1, phase = "Conditioning")
        val e = engine(tasks)
        assertEquals(T.Prepare(at(2), PhaseChange("Strength", "Conditioning", "2:45 HIIT")), e.next(at(1, round = 2)))
    }

    // MARK: - Phase display + summary + label

    @Test fun testSystemPhaseNamesRenderCanonically() {
        assertEquals("Warm-Up", SessionTransitionEngine.phaseDisplayName("Warmup"))
        assertEquals("Warm-Up", SessionTransitionEngine.phaseDisplayName("Warm Up"))
        assertEquals("Warm-Up", SessionTransitionEngine.phaseDisplayName("warm-up"))
        assertEquals("Exercises", SessionTransitionEngine.phaseDisplayName("Exercises"))
        assertEquals("Cool Down", SessionTransitionEngine.phaseDisplayName("Cool Down"))
        assertEquals("Cool Down", SessionTransitionEngine.phaseDisplayName("cooldown"))
        assertEquals("Conditioning", SessionTransitionEngine.phaseDisplayName("  Conditioning "))
        assertEquals("Core Finisher", SessionTransitionEngine.phaseDisplayName("Core Finisher"))
    }

    @Test fun testPhaseSummaryForms() {
        val straight = engine((1..6).map { movement("M$it", sets = 3) })
        assertEquals("6 Exercises • 3 Sets Each", straight.phaseSummary(0))

        val mixedSets = engine(listOf(movement("A", sets = 3), movement("B", sets = 4)))
        assertEquals("2 Exercises", mixedSets.phaseSummary(0))

        val singleSets = engine(listOf(movement("A", sets = 1), movement("B", sets = 1)))
        assertEquals("2 Exercises", singleSets.phaseSummary(0))

        val circuit = engine(group(listOf("A", "B", "C"), group = "c", rounds = 4, circuit = true))
        assertEquals("3 Exercises • 4 Rounds", circuit.phaseSummary(0))

        val hiit = engine(intervalBlock(listOf("X"), group = "i", mode = IntervalMode.HIIT, work = 45, rest = 15, rounds = 10))
        assertEquals("9:45 HIIT", hiit.phaseSummary(0))

        val emomTasks = intervalBlock(listOf("X"), group = "i", mode = IntervalMode.EMOM).toMutableList()
        emomTasks[0] = emomTasks[0].copy(intervalWindowSeconds = 600, intervalWorkSeconds = 40)
        assertEquals("10-minute EMOM", engine(emomTasks).phaseSummary(0))

        val twoPhases = engine(listOf(movement("A", sets = 2, phase = "Warmup"), movement("B", sets = 5, phase = "Exercises"), movement("C", sets = 5, phase = "Exercises")))
        assertEquals("2 Exercises • 5 Sets Each", twoPhases.phaseSummary(1))
    }

    @Test fun testPhaseOrdinalAndLabel() {
        val e = engine(listOf(movement("A", phase = "Warmup"), movement("B", phase = "Strength"), movement("C", phase = "Strength"), movement("D", phase = "Cool Down")))
        assertEquals(1, e.phaseOrdinal(0))
        assertEquals(2, e.phaseOrdinal(2))
        assertEquals(3, e.phaseOrdinal(3))
        assertEquals("PHASE 1 — WARM-UP", e.phaseLabel(0))
        assertEquals("PHASE 2 — STRENGTH", e.phaseLabel(1))
        assertEquals("PHASE 3 — COOL DOWN", e.phaseLabel(3))
        assertNull(engine(listOf(movement("A", phase = ""))).phaseLabel(0))
    }

    // MARK: - Skip forward

    @Test fun testSkipForwardWalksGroupWithoutRests() {
        val e = engine(group(listOf("A", "B", "C"), group = "c", rounds = 2, memberRest = 30, roundRest = 45, circuit = true) + movement("D"))
        assertEquals(T.Step(at(1)), e.skipForward(at(0)))
        assertEquals(T.Step(at(0, round = 2)), e.skipForward(at(2, round = 1)))
        assertEquals(T.Prepare(at(3), null), e.skipForward(at(2, round = 2)))
    }

    @Test fun testSkipForwardLeavesLooseMovementRegardlessOfSet() {
        val e = engine(listOf(movement("A", sets = 5, rest = 60), movement("B")))
        assertEquals(T.Prepare(at(1), null), e.skipForward(at(0, set = 2)))
    }

    @Test fun testSkipForwardSkipsWholeIntervalBlock() {
        val e = engine(intervalBlock(listOf("X", "Y"), group = "i") + movement("D"))
        assertEquals(T.Prepare(at(2), null), e.skipForward(at(0)))
    }

    @Test fun testSkipForwardAtEndReportsComplete() {
        assertEquals(T.WorkoutComplete, engine(listOf(movement("A", sets = 3))).skipForward(at(0)))
    }

    // MARK: - Transition helpers

    @Test fun testTargetCursorLooksThroughRests() {
        val rest = T.Rest(30, RestKind.BETWEEN_ROUNDS, T.Prepare(at(4), null))
        assertEquals(at(4), rest.targetCursor)
        assertNull(T.WorkoutComplete.targetCursor)
        assertTrue(T.Prepare(at(1), null).isPrepare)
        assertFalse(T.Step(at(1)).isPrepare)
    }

    @Test fun testOutOfRangeCursorCompletes() {
        assertEquals(T.WorkoutComplete, engine(emptyList()).next(at(0)))
        assertEquals(T.WorkoutComplete, engine(listOf(movement("A"))).next(at(7)))
    }

    @Test fun testMemberLabels() {
        val tasks = group(listOf("A", "B"), group = "g1", rounds = 2) + movement("Loose") +
            group(listOf("C", "D", "E"), group = "g2", rounds = 2, circuit = true) + intervalBlock(listOf("X"), group = "i")
        val e = engine(tasks)
        assertEquals("A1", e.memberLabel(0))
        assertEquals("A2", e.memberLabel(1))
        assertNull(e.memberLabel(2))
        assertEquals("B1", e.memberLabel(3))
        assertEquals("B3", e.memberLabel(5))
        assertNull("interval members are not lettered", e.memberLabel(6))
    }
}
