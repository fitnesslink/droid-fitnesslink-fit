package com.fitnesslink.fit.nutrition.core

import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.network.dto.NutritionTrendBucket
import com.fitnesslink.fit.viewmodel.NutritionAnalyticsMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Moving-average, projection and gap-segment math behind the analytics screen. */
class NutritionAnalyticsMathTest {

    @Test fun `trailing moving average uses partial windows at the start`() {
        val avg = NutritionAnalyticsMath.trailingMovingAverage(listOf(10.0, 20.0, 30.0, 40.0), window = 3)
        assertEquals(listOf(10.0, 15.0, 20.0, 30.0), avg)
    }

    @Test fun `seven day average of a constant series is the constant`() {
        val avg = NutritionAnalyticsMath.trailingMovingAverage(List(10) { 180.0 }, window = 7)
        avg.forEach { assertEquals(180.0, it, 0.0001) }
    }

    @Test fun `plan line starts at the anchor when it is inside the range`() {
        val start = MealPlanDay.parse("2026-09-01")
        val end = MealPlanDay.parse("2026-09-30")
        val anchor = MealPlanDay.parse("2026-09-10")
        val line = NutritionAnalyticsMath.planLine(start, end, anchor, 180.0, -1.0, 177.0)
        assertEquals(2, line.size)
        assertEquals(anchor, line[0].date)
        assertEquals(180.0, line[0].value, 0.0001)
        assertEquals(end, line[1].date)
        assertEquals(177.0, line[1].value, 0.0001)
    }

    @Test fun `plan line enters from the left edge when the anchor is before the range`() {
        val start = MealPlanDay.parse("2026-09-15")
        val end = MealPlanDay.parse("2026-09-29")
        val anchor = MealPlanDay.parse("2026-09-01") // 14 days = 2 weeks before range start
        val line = NutritionAnalyticsMath.planLine(start, end, anchor, 180.0, -1.0, 176.0)
        assertEquals(start, line[0].date)
        assertEquals(178.0, line[0].value, 0.0001)
    }

    @Test fun `projection reaches the goal date at the weekly rate`() {
        val anchor = MealPlanDay.parse("2026-09-01")
        val goal = NutritionAnalyticsMath.estimatedGoalDate(anchor, 180.0, -1.0, 176.0)
        assertEquals("2026-09-29", MealPlanDay.string(goal!!))
        assertNull(NutritionAnalyticsMath.estimatedGoalDate(anchor, 180.0, 0.0, 176.0))
        assertNull(NutritionAnalyticsMath.estimatedGoalDate(anchor, 180.0, 1.0, 176.0)) // moving away
        assertEquals(anchor, NutritionAnalyticsMath.estimatedGoalDate(anchor, 180.0, -1.0, 180.0))
    }

    @Test fun `daily buckets gap on unlogged days, weekly buckets use the per-day average`() {
        assertNull(NutritionAnalyticsMath.perDayValue(isWeekly = false, daysLogged = 0, daily = 0.0, weeklyAvg = null))
        assertEquals(1800.0, NutritionAnalyticsMath.perDayValue(isWeekly = false, daysLogged = 1, daily = 1800.0, weeklyAvg = null)!!, 0.0001)
        assertEquals(1900.0, NutritionAnalyticsMath.perDayValue(isWeekly = true, daysLogged = 5, daily = 9500.0, weeklyAvg = 1900.0)!!, 0.0001)
    }

    @Test fun `segments split on missing targets`() {
        val buckets = listOf(
            NutritionTrendBucket(periodStart = "2026-10-01", avgDailyCalorieTarget = 2000.0),
            NutritionTrendBucket(periodStart = "2026-10-02", avgDailyCalorieTarget = 2000.0),
            NutritionTrendBucket(periodStart = "2026-10-03", avgDailyCalorieTarget = null),
            NutritionTrendBucket(periodStart = "2026-10-04", avgDailyCalorieTarget = 1900.0)
        )
        val segments = NutritionAnalyticsMath.segments(buckets, "CalTarget") { it.avgDailyCalorieTarget }
        assertEquals(2, segments.size)
        assertEquals("CalTarget-0", segments[0].id)
        assertEquals(2, segments[0].points.size)
        assertEquals(1, segments[1].points.size)
        assertEquals(1900.0, segments[1].points[0].value, 0.0001)
    }
}
