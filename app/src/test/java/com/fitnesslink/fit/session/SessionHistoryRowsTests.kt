package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.network.dto.SessionHistoryEntry
import com.fitnesslink.fit.network.dto.SessionHistoryLogRequest
import com.fitnesslink.fit.session.SessionFixtures.movement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** History rows and offline payloads (FTNSSLNK-405 / -406), including the server rebuild. */
class SessionHistoryRowsTests {

    @Test fun testTerminalSetsOnlyAndSkippedRowsPostStatusWithoutMetrics() {
        val task = movement("A", sets = 3, reps = 10, weight = 100)
        val progress = SessionProgress()
        progress.log("A", 1)
        progress.complete("A", 2)
        progress.skipSet("A", 3)
        val logs = mapOf("A" to mapOf(1 to TaskSetConfig(setNumber = 1, reps = 8, weightKg = 120.0)))
        val rows = SessionHistoryRows.build(listOf(task), logs, progress, mapOf("A" to 3))
        assertEquals(listOf(1, 2, 3), rows.map { it.setNumber })
        assertEquals(listOf(SessionSetStatus.COMPLETED, SessionSetStatus.COMPLETED, SessionSetStatus.INCOMPLETE), rows.map { it.status })
        assertTrue(rows[0].metricsLogged)
        assertEquals(8, rows[0].reps)
        assertEquals(120.0, rows[0].weightLbs, 0.001)
        assertFalse(rows[1].metricsLogged)
        assertEquals("Next-only completion carries the plan", 10, rows[1].reps)
        assertEquals(100.0, rows[1].weightLbs, 0.001)

        val skipped = rows[2].request("w", "u", Date(0))
        assertEquals("Incomplete", skipped.status)
        assertNull(skipped.reps)
        assertNull(skipped.weightLifted)
        assertEquals(false, skipped.metricsLogged)

        val upcoming = SessionHistoryRows.build(listOf(task), emptyMap(), SessionProgress(), mapOf("A" to 3))
        assertTrue("nothing recorded → no rows", upcoming.isEmpty())
    }

    @Test fun testTimedTasksCarryIntervalSecondsAndRepsIndependently() {
        val plank = movement("P", sets = 1, seconds = 45)
        val progress = SessionProgress()
        progress.complete("P", 1, durationSeconds = 45)
        val rows = SessionHistoryRows.build(listOf(plank), emptyMap(), progress, mapOf("P" to 1))
        assertEquals(45, rows[0].intervalSeconds)
        assertEquals(0, rows[0].reps)
    }

    @Test fun testQueuedPayloadsRoundTrip() {
        val complete = QueuedCompletePayload("s1", rpeValue = 7, completionPercent = 80)
        assertEquals(complete, QueuedCompletePayload.decode(complete.encoded(), "ignored"))
        assertEquals(QueuedCompletePayload("bare-id"), QueuedCompletePayload.decode("bare-id", "e"))
        assertEquals(QueuedCompletePayload("entity"), QueuedCompletePayload.decode("", "entity"))

        val history = QueuedHistoryPayload("s1", SessionHistoryLogRequest(
            workoutTaskId = "t", workoutId = "w", userId = "u", logDate = Date(1_700_000_000_000L), reps = 8, set = 2,
            weightLifted = 100.0, status = "Completed", metricsLogged = true
        ))
        val decoded = QueuedHistoryPayload.decode(history.encoded()!!)!!
        assertEquals("s1", decoded.sessionId)
        assertEquals(2, decoded.entry.set)
        assertEquals(1_700_000_000_000L, decoded.entry.logDate.time)
        assertNull(QueuedHistoryPayload.decode("garbage"))

        val progress = QueuedProgressPayload("s1", "phase", 3, "m")
        assertEquals(progress, QueuedProgressPayload.decode(progress.encoded()))

        val start = QueuedStartPayload(id = "s1", workoutId = "w", userId = "u", platform = "android")
        assertEquals(start, QueuedStartPayload.decode(start.encoded()))
    }

    @Test fun testRebuiltFromServerHistoryKeepsLatestValuesAndNeverDowngradesCompleted() {
        val t0 = Date(1_700_000_000_000L)
        fun at(offset: Int) = Date(t0.time + offset * 1000L)
        val entries = listOf(
            SessionHistoryEntry("T-A", "w", logDate = at(0), reps = 8, set = 1, weightLifted = 100.0, status = "Completed", metricsLogged = true),
            SessionHistoryEntry("t-a", "w", logDate = at(5), reps = 10, set = 1, weightLifted = 110.0, status = "Completed", metricsLogged = true),
            SessionHistoryEntry("t-a", "w", logDate = at(6), set = 2, status = "Completed", metricsLogged = false),
            SessionHistoryEntry("t-a", "w", logDate = at(7), set = 2, status = "Incomplete"),
            SessionHistoryEntry("t-b", "w", logDate = at(8), set = 1, status = "Skipped")
        )
        val rebuilt = rebuiltProgress(entries, taskIds = listOf("t-A", "t-b"))
        assertEquals("latest values win", 110.0, rebuilt.logs["t-A"]!![1]!!.weightKg!!, 0.001)
        assertEquals(10, rebuilt.logs["t-A"]!![1]!!.reps)
        assertTrue(rebuilt.progress.isLogged("t-A", 1))
        assertEquals("a completed outcome is never downgraded", SetState.COMPLETED, rebuilt.progress.state("t-A", 2))
        assertEquals(SetState.SKIPPED, rebuilt.progress.state("t-b", 1))
    }
}
