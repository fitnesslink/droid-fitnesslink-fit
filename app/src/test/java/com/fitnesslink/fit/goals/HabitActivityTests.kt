package com.fitnesslink.fit.goals

import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.model.Streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HabitActivityTests {

    private fun localDay(y: Int, m1: Int, d: Int): Long = Calendar.getInstance().apply {
        clear(); set(y, m1 - 1, d, 0, 0, 0)
    }.timeInMillis

    private val today = localDay(2026, 10, 6) + 10 * 3_600_000L   // a Tuesday, 10:00 local

    @Test
    fun `window starts on a Sunday and covers whole weeks`() {
        val days = HabitActivity.windowDays(today, weeks = 12)
        assertEquals(84, days.size)
        val first = Calendar.getInstance().apply { timeInMillis = days.first() }
        assertEquals(Calendar.SUNDAY, first.get(Calendar.DAY_OF_WEEK))
        assertTrue(days.last() >= ProgramDates.startOfDay(today))
        assertEquals(ProgramDates.startOfDay(today), days[days.size - 5])   // Tue is the 3rd day of the last week
    }

    @Test
    fun `completed days win and future days are pending`() {
        val days = HabitActivity.windowDays(today, weeks = 1)
        val completed = setOf(localDay(2026, 10, 4), localDay(2026, 10, 5))
        val statuses = HabitActivity.dayStatuses(days, completed, today)
        assertEquals(HabitDayStatus.COMPLETED, statuses[localDay(2026, 10, 4)])
        assertEquals(HabitDayStatus.COMPLETED, statuses[localDay(2026, 10, 5)])
        assertEquals(HabitDayStatus.PENDING, statuses[localDay(2026, 10, 6)])   // today, not yet done
        assertEquals(HabitDayStatus.PENDING, statuses[localDay(2026, 10, 7)])   // future
        assertEquals(HabitDayStatus.PENDING, statuses[localDay(2026, 10, 10)])
    }

    @Test
    fun `past untracked days before the habit existed are pending, after are missed`() {
        val days = HabitActivity.windowDays(today, weeks = 1)
        val statuses = HabitActivity.dayStatuses(days, emptySet(), today, trackedFrom = localDay(2026, 10, 5) + 3_600_000L)
        assertEquals(HabitDayStatus.PENDING, statuses[localDay(2026, 10, 4)])   // before createdAt
        assertEquals(HabitDayStatus.MISSED, statuses[localDay(2026, 10, 5)])    // tracked, no log
    }

    @Test
    fun `freeze days render as freeze`() {
        val days = HabitActivity.windowDays(today, weeks = 1)
        val statuses = HabitActivity.dayStatuses(days, emptySet(), today, freezeDays = setOf(localDay(2026, 10, 5)))
        assertEquals(HabitDayStatus.FREEZE, statuses[localDay(2026, 10, 5)])
        assertEquals(HabitDayStatus.MISSED, statuses[localDay(2026, 10, 4)])
    }

    @Test
    fun `today completed is completed not pending`() {
        val days = HabitActivity.windowDays(today, weeks = 1)
        val statuses = HabitActivity.dayStatuses(days, setOf(localDay(2026, 10, 6)), today)
        assertEquals(HabitDayStatus.COMPLETED, statuses[localDay(2026, 10, 6)])
    }

    @Test
    fun `stats ignore pending days and mirror the streak`() {
        val statuses = listOf(
            HabitDayStatus.COMPLETED, HabitDayStatus.COMPLETED, HabitDayStatus.COMPLETED,
            HabitDayStatus.MISSED, HabitDayStatus.FREEZE,
            HabitDayStatus.PENDING, HabitDayStatus.PENDING
        )
        val stats = HabitActivity.stats(statuses, Streak(currentCount = 3, longestCount = 9))
        assertEquals(5, stats.totalDays)
        assertEquals(3, stats.completedDays)
        assertEquals(1, stats.missedDays)
        assertEquals(1, stats.freezeDays)
        assertEquals(60.0, stats.completionRate, 0.0001)
        assertEquals(3, stats.currentStreak)
        assertEquals(9, stats.longestStreak)
    }

    @Test
    fun `completion rate is zero with nothing tracked`() {
        assertEquals(0.0, HabitActivity.stats(listOf(HabitDayStatus.PENDING), Streak()).completionRate, 0.0)
    }
}
