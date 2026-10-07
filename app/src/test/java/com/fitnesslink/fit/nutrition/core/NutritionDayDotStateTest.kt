package com.fitnesslink.fit.nutrition.core

import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.NutritionCalendarMonth
import com.fitnesslink.fit.model.NutritionDayDotState
import com.fitnesslink.fit.model.NutritionDayTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** Week-strip / calendar dot derivation (FTNSSLNK-196) and month-grid math. */
class NutritionDayDotStateTest {

    private fun day(y: Int, m: Int, d: Int): Date = Calendar.getInstance().apply {
        set(y, m, d, 13, 45, 0); set(Calendar.MILLISECOND, 0)
    }.time

    private val today = day(2026, Calendar.OCTOBER, 6)

    @Test fun `future days are future regardless of data`() {
        val tomorrow = day(2026, Calendar.OCTOBER, 7)
        assertEquals(NutritionDayDotState.FUTURE, NutritionDayDotState.state(tomorrow, hasData = true, today = today))
        assertEquals(NutritionDayDotState.FUTURE, NutritionDayDotState.state(tomorrow, hasData = null, today = today))
    }

    @Test fun `today and past days follow hasData`() {
        assertEquals(NutritionDayDotState.LOGGED, NutritionDayDotState.state(today, hasData = true, today = today))
        assertEquals(NutritionDayDotState.EMPTY, NutritionDayDotState.state(today, hasData = false, today = today))
        val yesterday = day(2026, Calendar.OCTOBER, 5)
        assertEquals(NutritionDayDotState.LOGGED, NutritionDayDotState.state(yesterday, hasData = true, today = today))
    }

    @Test fun `missing rollup degrades to empty, never blocks`() {
        assertEquals(NutritionDayDotState.EMPTY, NutritionDayDotState.state(today, hasData = null, today = today))
    }

    @Test fun `later time on the same day is still today`() {
        val lateToday = Calendar.getInstance().apply { time = today; set(Calendar.HOUR_OF_DAY, 23) }.time
        assertEquals(NutritionDayDotState.LOGGED, NutritionDayDotState.state(lateToday, hasData = true, today = today))
    }

    @Test fun `month grid is Sunday-first and padded to whole weeks`() {
        // October 2026 starts on a Thursday (4 leading blanks) and has 31 days -> 35 cells.
        val cells = NutritionCalendarMonth.gridDays(day(2026, Calendar.OCTOBER, 15))
        assertEquals(35, cells.size)
        repeat(4) { assertNull(cells[it]) }
        assertEquals("2026-10-01", MealPlanDay.string(cells[4]!!))
        assertEquals("2026-10-31", MealPlanDay.string(cells[34]!!))
        assertTrue(cells.size % 7 == 0)
    }

    @Test fun `first and last of month shift by offset`() {
        val anchor = day(2026, Calendar.OCTOBER, 15)
        assertEquals("2026-10-01", MealPlanDay.string(NutritionCalendarMonth.firstOfMonth(anchor)))
        assertEquals("2026-11-01", MealPlanDay.string(NutritionCalendarMonth.firstOfMonth(anchor, 1)))
        assertEquals("2026-09-01", MealPlanDay.string(NutritionCalendarMonth.firstOfMonth(anchor, -1)))
        assertEquals("2026-10-31", MealPlanDay.string(NutritionCalendarMonth.lastOfMonth(anchor)))
    }

    @Test fun `relative day titles`() {
        assertEquals("Today", NutritionDayTitle.title(today, today) { "X" })
        assertEquals("Yesterday", NutritionDayTitle.title(day(2026, Calendar.OCTOBER, 5), today) { "X" })
        assertEquals("Tomorrow", NutritionDayTitle.title(day(2026, Calendar.OCTOBER, 7), today) { "X" })
        assertEquals("Friday", NutritionDayTitle.title(day(2026, Calendar.OCTOBER, 9), today) { "Friday" })
    }
}
