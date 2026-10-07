package com.fitnesslink.fit.reports

import com.fitnesslink.fit.model.SessionRow
import com.fitnesslink.fit.model.SessionRpeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** FTNSSLNK-411: the RPE may be changed once, within 24 h of completion. */
class SessionRpeRuleTests {

    private val now = Date(1_800_000_000_000L)
    private fun hoursAgo(h: Double) = Date(now.time - (h * 3_600_000L).toLong())

    @Test
    fun `editable inside the 24 h window when never edited`() {
        assertTrue(SessionRpeRule.isEditable(completionDate = hoursAgo(1.0), editedAt = null, now = now))
        assertTrue(SessionRpeRule.isEditable(completionDate = hoursAgo(23.9), editedAt = null, now = now))
        assertTrue(SessionRpeRule.isEditable(completionDate = now, editedAt = null, now = now))
    }

    @Test
    fun `not editable once the window has passed`() {
        assertFalse(SessionRpeRule.isEditable(completionDate = hoursAgo(24.1), editedAt = null, now = now))
        assertFalse(SessionRpeRule.isEditable(completionDate = hoursAgo(72.0), editedAt = null, now = now))
    }

    @Test
    fun `exactly 24 h is still editable`() {
        assertTrue(SessionRpeRule.isEditable(completionDate = hoursAgo(24.0), editedAt = null, now = now))
    }

    @Test
    fun `one edit closes the window even when still inside 24 h`() {
        assertFalse(SessionRpeRule.isEditable(completionDate = hoursAgo(1.0), editedAt = hoursAgo(0.5), now = now))
    }

    @Test
    fun `no completion date is never editable`() {
        assertFalse(SessionRpeRule.isEditable(completionDate = null, editedAt = null, now = now))
    }

    @Test
    fun `a completion date in the future is not editable`() {
        assertFalse(SessionRpeRule.isEditable(completionDate = Date(now.time + 60_000), editedAt = null, now = now))
    }

    @Test
    fun `session row requires completion as well as the window`() {
        val base = SessionRow(
            id = "s1", workoutName = "Push", date = hoursAgo(2.0), durationSeconds = 1800, isCompleted = true,
            exerciseCount = 5, totalWeightLifted = 1000.0, totalCaloriesBurned = 200.0, rpeValue = 7.0,
            completionDate = hoursAgo(1.0)
        )
        assertTrue(base.canEditRpe(now))
        assertFalse(base.copy(isCompleted = false).canEditRpe(now))
        assertFalse(base.copy(rpeEditedAt = hoursAgo(0.2)).canEditRpe(now))
        assertFalse(base.copy(completionDate = hoursAgo(30.0)).canEditRpe(now))
    }

    @Test
    fun `display text drops the trailing zero and keeps legacy half steps`() {
        assertEquals("7", SessionRpeRule.displayText(7.0))
        assertEquals("7.5", SessionRpeRule.displayText(7.5))
        assertNull(SessionRpeRule.displayText(null))
    }

    @Test
    fun `clamp and labels follow the 0 to 10 scale`() {
        assertEquals(0, SessionRpeRule.clamp(-3))
        assertEquals(10, SessionRpeRule.clamp(14))
        assertEquals("Not difficult", SessionRpeRule.label(0))
        assertEquals("Moderate", SessionRpeRule.label(6))
        assertEquals("Very hard", SessionRpeRule.label(9))
        assertEquals("Maximum difficulty", SessionRpeRule.label(10))
        assertEquals(5.0, SessionRpeRule.storedValue(5)!!, 0.0)
        assertNull(SessionRpeRule.storedValue(null))
    }

    @Test
    fun `status label reflects partial completion`() {
        val row = SessionRow(
            id = "s", workoutName = "W", date = now, durationSeconds = 0, isCompleted = true,
            exerciseCount = 0, totalWeightLifted = 0.0, totalCaloriesBurned = 0.0, rpeValue = null
        )
        assertEquals("Completed", row.statusLabel)
        assertEquals("Completed · 62%", row.copy(completionPercent = 62).statusLabel)
        assertEquals("Completed", row.copy(completionPercent = 100).statusLabel)
        assertEquals("Incomplete", row.copy(isCompleted = false, completionPercent = 40).statusLabel)
    }
}
