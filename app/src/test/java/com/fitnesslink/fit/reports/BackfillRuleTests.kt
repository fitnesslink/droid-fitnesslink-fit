package com.fitnesslink.fit.reports

import com.fitnesslink.fit.model.WorkoutSession
import com.fitnesslink.fit.model.api.WorkoutSessionStatusId
import com.fitnesslink.fit.network.dto.SessionHistoryEntry
import com.fitnesslink.fit.network.dto.SessionSetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.UUID

/**
 * FTNSSLNK-368: the `/sessions/me` backfill only ever creates local rows
 * for sessions the server considers completed, and never for ones we
 * already hold (the originating device owns the stats).
 */
class BackfillRuleTests {

    private val completedStatus = UUID.fromString(WorkoutSessionStatusId.completed)
    private val inProgressStatus = UUID.fromString(WorkoutSessionStatusId.inProgress)

    private fun session(id: String, completionDate: Date? = null, statusId: UUID? = null) =
        WorkoutSession(id = id, workoutId = "w1", name = "Workout", completionDate = completionDate, statusId = statusId)

    @Test
    fun `completed sessions missing locally are backfilled`() {
        val byDate = session("a", completionDate = Date())
        val byStatus = session("b", statusId = completedStatus)
        val picked = SessionHistoryHydrator.sessionsToBackfill(listOf(byDate, byStatus)) { false }
        assertEquals(listOf("a", "b"), picked.map { it.id })
    }

    @Test
    fun `in-progress sessions are never backfilled`() {
        val inProgress = session("c", statusId = inProgressStatus)
        val legacyNoStatus = session("d")
        val picked = SessionHistoryHydrator.sessionsToBackfill(listOf(inProgress, legacyNoStatus)) { false }
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `sessions already stored locally are skipped`() {
        val have = session("have", completionDate = Date())
        val missing = session("missing", completionDate = Date())
        val picked = SessionHistoryHydrator.sessionsToBackfill(listOf(have, missing)) { it == "have" }
        assertEquals(listOf("missing"), picked.map { it.id })
    }

    @Test
    fun `existence check is consulted with the session id`() {
        val asked = mutableListOf<String>()
        SessionHistoryHydrator.sessionsToBackfill(listOf(session("x", completionDate = Date()))) { asked += it; false }
        assertEquals(listOf("x"), asked)
    }

    @Test
    fun `only performed sets count toward local history`() {
        fun entry(status: String?) = SessionHistoryEntry(
            workoutTaskId = "t", workoutId = "w", logDate = Date(), reps = 10, set = 1, weightLifted = 100.0, status = status
        )
        assertTrue(SessionHistoryHydrator.isPerformedSet(entry(null)))
        assertTrue(SessionHistoryHydrator.isPerformedSet(entry(SessionSetStatus.COMPLETED)))
        assertTrue(SessionHistoryHydrator.isPerformedSet(entry("completed")))
        assertFalse(SessionHistoryHydrator.isPerformedSet(entry(SessionSetStatus.SKIPPED)))
        assertFalse(SessionHistoryHydrator.isPerformedSet(entry(SessionSetStatus.INCOMPLETE)))
    }

    @Test
    fun `derived stats mirror the session engine writer`() {
        val start = Date(1_800_000_000_000L)
        val end = Date(start.time + 45 * 60_000L)
        val s = WorkoutSession(id = "s", workoutId = "w", startDate = start, completionDate = end)
        val history = listOf(
            SessionHistoryEntry(workoutTaskId = "A", workoutId = "w", logDate = start, reps = 10, set = 1, weightLifted = 100.0),
            SessionHistoryEntry(workoutTaskId = "a", workoutId = "w", logDate = start, reps = 8, set = 2, weightLifted = 100.0),
            // Duration-only set: reps 0 contributes no weight.
            SessionHistoryEntry(workoutTaskId = "B", workoutId = "w", logDate = start, reps = 0, set = 1, intervalSeconds = 60, weightLifted = 50.0),
            SessionHistoryEntry(workoutTaskId = "C", workoutId = "w", logDate = start, reps = 12, set = 1, weightLifted = null)
        )
        val stats = SessionHistoryHydrator.deriveStats(s, history)
        assertEquals(3, stats.exerciseCount)   // A (case-insensitive), B, C
        assertEquals(4, stats.totalSets)
        assertEquals(30, stats.totalReps)
        assertEquals(1800.0, stats.totalWeightLifted, 0.0001)
        assertEquals(45 * 60, stats.durationSeconds)
    }

    @Test
    fun `duration is zero without both timestamps`() {
        val stats = SessionHistoryHydrator.deriveStats(WorkoutSession(id = "s", startDate = Date()), emptyList())
        assertEquals(0, stats.durationSeconds)
        assertEquals(0, stats.exerciseCount)
    }
}
