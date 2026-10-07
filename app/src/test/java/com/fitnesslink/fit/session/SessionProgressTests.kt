package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.SessionFixtures.hiitRow
import com.fitnesslink.fit.session.SessionFixtures.movement
import com.fitnesslink.fit.session.SessionFixtures.supersetRow
import com.fitnesslink.fit.session.SessionFixtures.workout
import com.fitnesslink.fit.session.SessionTransitionEngine.Cursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of the pure-model half of iOS `SessionProgressTests` (FTNSSLNK-400, PRD §7.4 / §9). */
class SessionProgressTests {

    @Test fun testNextIsCompletedNotLogged() {
        val p = SessionProgress()
        assertEquals(SetState.UPCOMING, p.state("A", 1))
        p.complete("A", 1, durationSeconds = 42)
        assertEquals(SetState.COMPLETED, p.state("A", 1))
        assertFalse(p.isLogged("A", 1))
        assertEquals(42, p.entry("A", 1).durationSeconds)
    }

    @Test fun testSaveSetIsCompletedAndLoggedAndNextKeepsTheLog() {
        val p = SessionProgress()
        p.log("A", 2)
        assertEquals(SetState.COMPLETED, p.state("A", 2))
        assertTrue(p.isLogged("A", 2))
        p.complete("A", 2)
        assertTrue("Next after Save Set keeps the log", p.isLogged("A", 2))
        assertEquals("other sets untouched", SetState.UPCOMING, p.state("A", 1))
    }

    @Test fun testClearingALogRevertsALogOnlySetButNotOneCompletedByNext() {
        val p = SessionProgress()
        p.log("A", 1)
        p.clearLog("A", 1)
        assertEquals(SetState.UPCOMING, p.state("A", 1))
        assertTrue(p.isEmpty)

        p.skipSet("B", 1)
        p.log("B", 1)
        assertEquals("logging a skipped set completes it", SetState.COMPLETED, p.state("B", 1))
        p.clearLog("B", 1)
        assertEquals(SetState.UPCOMING, p.state("B", 1))
    }

    @Test fun testSkipSetIsIncompleteAndSkipExerciseSkipsOnlyTheRemainingUncompletedSets() {
        val p = SessionProgress()
        p.skipSet("A", 1)
        assertEquals(SetState.INCOMPLETE, p.state("A", 1))

        p.complete("B", 1)
        p.skipRemaining("B", 2, 4)
        assertEquals(listOf(SetState.COMPLETED, SetState.SKIPPED, SetState.SKIPPED, SetState.SKIPPED), p.states("B", 4))

        p.complete("C", 2)
        p.skipRemaining("C", 1, 3)
        assertEquals(listOf(SetState.SKIPPED, SetState.COMPLETED, SetState.SKIPPED), p.states("C", 3))

        p.skipRemaining("D", 5, 3)
        assertEquals(listOf(SetState.UPCOMING, SetState.UPCOMING, SetState.UPCOMING), p.states("D", 3))
    }

    @Test fun testPlaylistCheckboxCompletesAndRevertsEverySetKeepingLogs() {
        val p = SessionProgress()
        p.log("A", 2)
        p.completeExercise("A", 3)
        assertEquals(listOf(SetState.COMPLETED, SetState.COMPLETED, SetState.COMPLETED), p.states("A", 3))
        assertFalse("checkbox completes without logging", p.isLogged("A", 1))
        assertTrue(p.isLogged("A", 2))

        p.revertExercise("A", 3)
        assertEquals(listOf(SetState.UPCOMING, SetState.UPCOMING, SetState.UPCOMING), p.states("A", 3))
        assertTrue("unchecking keeps the logged values", p.isLogged("A", 2))
    }

    @Test fun testExerciseStateRollUp() {
        val p = SessionProgress()
        assertEquals(ExerciseState.UPCOMING, p.exerciseState("A", 3))
        p.complete("A", 1)
        assertEquals(ExerciseState.IN_PROGRESS, p.exerciseState("A", 3))
        p.complete("A", 2)
        p.complete("A", 3)
        assertEquals(ExerciseState.COMPLETED, p.exerciseState("A", 3))
        assertTrue(p.isExerciseCompleted("A", 3))

        p.complete("B", 1)
        p.skipRemaining("B", 2, 3)
        assertEquals(ExerciseState.SKIPPED, p.exerciseState("B", 3))
        assertTrue(p.exerciseState("B", 3).isResolved)

        p.complete("C", 1)
        p.skipSet("C", 2)
        assertEquals(ExerciseState.INCOMPLETE, p.exerciseState("C", 2))
        assertFalse(p.exerciseState("C", 2).isResolved)

        p.skipSet("D", 1)
        assertEquals("an incomplete set with sets still upcoming", ExerciseState.IN_PROGRESS, p.exerciseState("D", 2))
    }

    @Test fun testCompletionPercentCountsOnlyCompletedSets() {
        val p = SessionProgress()
        val required = mapOf("A" to 3, "B" to 3, "C" to 2)
        assertEquals(0, p.completionPercent(required))
        p.complete("A", 1)
        p.complete("A", 2)
        p.log("A", 3)
        p.skipSet("B", 1)
        p.complete("B", 2)
        p.skipRemaining("C", 1, 2)
        assertEquals(4, p.completedSetCount(required))
        assertEquals(50, p.completionPercent(required))
        p.complete("B", 3)
        assertEquals("5 of 8, rounded half up", 63, p.completionPercent(required))
        assertEquals(0, p.completionPercent(emptyMap()))
        assertEquals(setOf("A"), p.completedTaskIds(required))
        assertEquals("C is skipped, A completed", setOf("B"), p.unresolvedTaskIds(required))
    }

    @Test fun testPhaseCompletedWhenEveryExerciseIsCompletedOrSkipped() {
        val p = SessionProgress()
        val required = mapOf("A" to 2, "B" to 1, "C" to 1)
        assertFalse(p.isPhaseCompleted(listOf("A", "B"), required))
        p.completeExercise("A", 2)
        assertFalse(p.isPhaseCompleted(listOf("A", "B"), required))
        p.skipRemaining("B", 1, 1)
        assertTrue(p.isPhaseCompleted(listOf("A", "B"), required))
        p.skipSet("C", 1)
        assertFalse("an incomplete set leaves the phase open", p.isPhaseCompleted(listOf("A", "B", "C"), required))
        assertFalse("no movements → not complete", p.isPhaseCompleted(listOf("rest-only"), required))
    }

    @Test fun testRequiredIterationsUseSetsForLooseAndRoundsForGroupMembers() {
        val w = workout(listOf(TaskRow(task = movement("A", sets = 4)), supersetRow(listOf("B", "C"), rounds = 3), hiitRow(listOf("D", "E"), rounds = 6))).hydrateGroupRounds()
        val required = SessionProgress.requiredIterations(w.allTasks)
        assertEquals(mapOf("A" to 4, "B" to 3, "C" to 3, "D" to 6, "E" to 6), required)
        val rest = WorkoutTask(id = "R", isRest = true, restSeconds = 30)
        assertEquals("rests need nothing", emptyMap<String, Int>(), SessionProgress.requiredIterations(listOf(rest)))
        assertEquals("never below 1", mapOf("Z" to 1), SessionProgress.requiredIterations(listOf(movement("Z", sets = 0))))
    }

    @Test fun testJSONRoundTrip() {
        val p = SessionProgress()
        p.log("A", 1, durationSeconds = 30)
        p.skipSet("A", 2)
        p.skipRemaining("B", 1, 2)
        val json = p.encodedJSON()
        assertNotNull(json)
        assertTrue(json!!.contains("\"skipped\""))
        val back = SessionProgress.decode(json)
        assertEquals(p, back)
        assertNull("nothing recorded → no JSON", SessionProgress().encodedJSON())
        assertNull(SessionProgress.decode(null))
        assertNull(SessionProgress.decode("not json"))
    }

    @Test fun testFirstUpcomingCursorWalksLooseSetsAndGroupRoundsInOrder() {
        val w = workout(listOf(TaskRow(task = movement("A", sets = 3)), supersetRow(listOf("B", "C"), rounds = 2), TaskRow(task = movement("D")))).hydrateGroupRounds()
        val tasks = w.allTasks
        val required = SessionProgress.requiredIterations(tasks)
        val p = SessionProgress()
        assertEquals(Cursor(0, 1, 1), p.firstUpcomingCursor(tasks, required))
        p.complete("A", 1)
        assertEquals(Cursor(0, 2, 1), p.firstUpcomingCursor(tasks, required))
        p.skipRemaining("A", 2, 3)
        p.complete("B", 1)
        assertEquals("C round 1 before B round 2", Cursor(2, 1, 1), p.firstUpcomingCursor(tasks, required))
        p.complete("C", 1)
        p.complete("B", 2)
        assertEquals(Cursor(2, 1, 2), p.firstUpcomingCursor(tasks, required))
        p.complete("C", 2)
        assertEquals(Cursor(3, 1, 1), p.firstUpcomingCursor(tasks, required))
        p.skipSet("D", 1)
        assertNull("nothing upcoming — incomplete sets are not revisited", p.firstUpcomingCursor(tasks, required))
    }

    @Test fun testFirstUpcomingCursorLandsOnAnIntervalBlockLeadWhileAnyRoundIsUpcoming() {
        val w = workout(listOf(hiitRow(listOf("A", "B"), rounds = 4), TaskRow(task = movement("C")))).hydrateGroupRounds()
        val tasks = w.allTasks
        val required = SessionProgress.requiredIterations(tasks)
        val p = SessionProgress()
        p.complete("A", 1)
        p.complete("B", 1)
        assertEquals(Cursor(0, 1, 1), p.firstUpcomingCursor(tasks, required))
        p.completeExercise("A", 4)
        p.skipRemaining("B", 2, 4)
        assertEquals(Cursor(2, 1, 1), p.firstUpcomingCursor(tasks, required))
    }

    @Test fun testContinueCursorFallsBackToTheFirstIncompleteExercise() {
        val tasks = SessionFixtures.indexed(listOf(movement("A", sets = 2), movement("B", sets = 2)))
        val required = SessionProgress.requiredIterations(tasks)
        val p = SessionProgress()
        p.complete("A", 1)
        p.skipSet("A", 2)
        p.skipRemaining("B", 1, 2)
        assertNull(p.firstUpcomingCursor(tasks, required))
        assertEquals(Cursor(0, 2, 1), p.continueCursor(tasks, required))
        p.complete("A", 2)
        assertNull("everything resolved", p.continueCursor(tasks, required))
    }
}
