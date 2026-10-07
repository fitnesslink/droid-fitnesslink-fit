package com.fitnesslink.fit.home

import com.fitnesslink.fit.model.HabitTodayItem
import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.model.Streak
import com.fitnesslink.fit.model.advanceHabitStreak
import com.fitnesslink.fit.model.dailyProgressFor
import com.fitnesslink.fit.model.rollBackHabitStreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Port of iOS `HomeViewModel.updateDailyProgress` / `advanceStreak` behaviour. */
class DailyHabitSummaryTest {

    private fun habit(done: Boolean, streak: Int = 0) =
        HabitTodayItem(title = "h", isCompletedToday = done, streak = Streak(currentCount = streak))

    private fun at(y: Int, m: Int, d: Int, hour: Int = 9): Long = Calendar.getInstance().apply {
        clear(); set(y, m - 1, d, hour, 0, 0)
    }.timeInMillis

    @Test fun `summary counts completions and percent`() {
        val p = dailyProgressFor(listOf(habit(true), habit(false), habit(true), habit(false)))
        assertEquals(2, p.completedToday)
        assertEquals(4, p.totalToday)
        assertEquals(50.0, p.completionPercent, 0.0001)
    }

    @Test fun `empty day is zero percent not NaN`() {
        val p = dailyProgressFor(emptyList())
        assertEquals(0, p.totalToday)
        assertEquals(0.0, p.completionPercent, 0.0)
        assertFalse(p.streakAtRisk)
    }

    @Test fun `streak at risk only for an unfinished habit with a streak over seven`() {
        assertTrue(dailyProgressFor(listOf(habit(false, streak = 8))).streakAtRisk)
        assertFalse(dailyProgressFor(listOf(habit(false, streak = 7))).streakAtRisk)
        assertFalse(dailyProgressFor(listOf(habit(true, streak = 30))).streakAtRisk)
    }

    @Test fun `first completion starts a streak of one`() {
        val now = at(2026, 10, 6)
        val s = advanceHabitStreak(Streak(), habitId = "H1", userId = "U1", nowMillis = now)
        assertEquals(1, s.currentCount)
        assertEquals(1, s.longestCount)
        assertEquals("H1", s.habitId)
        assertEquals("U1", s.userId)
        assertEquals(ProgramDates.startOfDay(now), s.streakStartDate)
        assertEquals(now, s.lastCompletedDate)
    }

    @Test fun `consecutive day extends and grows the longest count`() {
        val prior = Streak(habitId = "H1", currentCount = 3, longestCount = 3, lastCompletedDate = at(2026, 10, 5), streakStartDate = at(2026, 10, 3, 0))
        val s = advanceHabitStreak(prior, "H1", "U1", nowMillis = at(2026, 10, 6))
        assertEquals(4, s.currentCount)
        assertEquals(4, s.longestCount)
        assertEquals(prior.streakStartDate, s.streakStartDate)
    }

    @Test fun `a gap resets to one and rebases the start but keeps the longest`() {
        val prior = Streak(habitId = "H1", currentCount = 5, longestCount = 9, lastCompletedDate = at(2026, 10, 2))
        val now = at(2026, 10, 6)
        val s = advanceHabitStreak(prior, "H1", "U1", nowMillis = now)
        assertEquals(1, s.currentCount)
        assertEquals(9, s.longestCount)
        assertEquals(ProgramDates.startOfDay(now), s.streakStartDate)
    }

    @Test fun `same day returns the prior streak untouched`() {
        val prior = Streak(habitId = "H1", currentCount = 2, longestCount = 2, lastCompletedDate = at(2026, 10, 6, 7))
        assertSame(prior, advanceHabitStreak(prior, "H1", "U1", nowMillis = at(2026, 10, 6, 20)))
    }

    @Test fun `roll back drops one clears last date and keeps the longest`() {
        val s = rollBackHabitStreak(Streak(currentCount = 4, longestCount = 10, lastCompletedDate = 123L))
        assertEquals(3, s.currentCount)
        assertEquals(10, s.longestCount)
        assertNull(s.lastCompletedDate)
        assertEquals(0, rollBackHabitStreak(Streak(currentCount = 0)).currentCount)
    }
}
