package com.fitnesslink.fit.nutrition.plan

import com.fitnesslink.fit.model.DayOfWeek
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.PlannedDayTargetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MealPlanWeekMathTests {

    private fun date(y: Int, m1: Int, d: Int) = MealPlanDay.calendar().apply { clear(); set(y, m1 - 1, d, 13, 45, 0) }.time

    @Test
    fun `week starts on Sunday for any weekday`() {
        // 2026-10-06 is a Tuesday → week starts Sunday 2026-10-04.
        assertEquals("2026-10-04", MealPlanDay.string(MealPlanDay.weekStart(date(2026, 10, 6))))
        // A Sunday is its own week start.
        assertEquals("2026-10-04", MealPlanDay.string(MealPlanDay.weekStart(date(2026, 10, 4))))
        // A Saturday still belongs to the week that started the previous Sunday.
        assertEquals("2026-10-04", MealPlanDay.string(MealPlanDay.weekStart(date(2026, 10, 10))))
    }

    @Test
    fun `week dates are seven consecutive days Sunday through Saturday`() {
        val dates = MealPlanDay.weekDates(date(2026, 10, 6)).map { MealPlanDay.string(it) }
        assertEquals(listOf("2026-10-04", "2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-10"), dates)
        val first = MealPlanDay.calendar().apply { time = MealPlanDay.weekDates(date(2026, 10, 6)).first() }
        assertEquals(Calendar.SUNDAY, first.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `week start crosses a month boundary`() {
        // 2026-11-02 (Monday) → Sunday 2026-11-01; 2026-10-01 (Thursday) → Sunday 2026-09-27.
        assertEquals("2026-11-01", MealPlanDay.string(MealPlanDay.weekStart(date(2026, 11, 2))))
        assertEquals("2026-09-27", MealPlanDay.string(MealPlanDay.weekStart(date(2026, 10, 1))))
    }

    @Test
    fun `parse tolerates ISO, timestamps and legacy weekday names`() {
        assertEquals("2026-10-06", MealPlanDay.normalize("2026-10-06"))
        assertEquals("2026-10-06", MealPlanDay.normalize("2026-10-06T00:00:00"))
        // Legacy day-of-week maps onto the CURRENT week's matching weekday.
        val expected = DayOfWeek.MONDAY.dateStringInWeek()
        assertEquals(expected, MealPlanDay.normalize("Mon"))
        assertEquals(expected, MealPlanDay.normalize("MONDAY"))
    }

    @Test
    fun `day start strips the time`() {
        val start = MealPlanDay.dayStart(date(2026, 10, 6))
        val cal = MealPlanDay.calendar().apply { time = start }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY)); assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals("2026-10-06", MealPlanDay.string(start))
    }

    // MARK: - Planned-day target state

    @Test
    fun `no target when goal missing or zero`() {
        assertEquals(PlannedDayTargetState.NO_TARGET, PlannedDayTargetState.from(1800, null))
        assertEquals(PlannedDayTargetState.NO_TARGET, PlannedDayTargetState.from(1800, 0))
        assertFalse(PlannedDayTargetState.from(1800, null).isOver)
    }

    @Test
    fun `under at or below target, over strictly above`() {
        assertEquals(PlannedDayTargetState.UNDER, PlannedDayTargetState.from(1999, 2000))
        assertEquals(PlannedDayTargetState.UNDER, PlannedDayTargetState.from(2000, 2000))
        assertEquals(PlannedDayTargetState.OVER, PlannedDayTargetState.from(2001, 2000))
        assertTrue(PlannedDayTargetState.from(2001, 2000).isOver)
    }
}
