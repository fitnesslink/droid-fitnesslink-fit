package com.fitnesslink.fit.home

import com.fitnesslink.fit.model.DashboardSession
import com.fitnesslink.fit.model.HomeDashboard
import com.fitnesslink.fit.model.HomeDashboardMath
import com.fitnesslink.fit.model.WeightUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/** Port of the tile math in iOS `HomeViewModel.computeDashboards` / `computeWorkoutStreak`. */
class DashboardMathTest {

    @Before fun setUp() { WeightUnit.current = WeightUnit.LBS }
    @After fun tearDown() { WeightUnit.current = WeightUnit.LBS }

    private fun at(y: Int, m: Int, d: Int, hour: Int = 9): Long = Calendar.getInstance().apply {
        clear(); set(y, m - 1, d, hour, 0, 0)
    }.timeInMillis

    private fun done(y: Int, m: Int, d: Int, lbs: Double = 0.0, hour: Int = 9) = DashboardSession(at(y, m, d, hour), true, lbs)
    private fun open(y: Int, m: Int, d: Int) = DashboardSession(at(y, m, d), false, 500.0)

    @Test fun `this week is the 7-day window ending on the reference day and only counts completed sessions`() {
        val ref = at(2026, 10, 6, 15)
        val sessions = listOf(
            done(2026, 9, 29),           // 7 days before — outside
            done(2026, 9, 30),           // 6 days before — inside (window start)
            done(2026, 10, 3),
            done(2026, 10, 6, hour = 23), // same day, later — inside
            done(2026, 10, 7),           // tomorrow — outside
            open(2026, 10, 4)            // incomplete — ignored
        )
        val week = HomeDashboardMath.sessionsThisWeek(sessions, ref)
        assertEquals(3, week.size)
        assertEquals(at(2026, 9, 30, 0), HomeDashboardMath.weekWindowStart(ref))
        assertEquals(at(2026, 10, 7, 0), HomeDashboardMath.weekWindowEnd(ref))
    }

    @Test fun `volume sums stored lbs and converts to the display unit`() {
        val week = listOf(done(2026, 10, 1, lbs = 1000.0), done(2026, 10, 2, lbs = 1205.0))
        assertEquals(2205, HomeDashboardMath.volumeDisplay(week, WeightUnit.LBS))
        assertEquals(1000, HomeDashboardMath.volumeDisplay(week, WeightUnit.KG)) // 2205 lbs ≈ 1000.2 kg
        assertEquals(0, HomeDashboardMath.volumeDisplay(emptyList(), WeightUnit.LBS))
    }

    @Test fun `streak counts consecutive days ending today and breaks on the first gap`() {
        val today = at(2026, 10, 6, 18)
        val sessions = listOf(
            done(2026, 10, 6, hour = 7), done(2026, 10, 6, hour = 19), // two today still count once
            done(2026, 10, 5), done(2026, 10, 4),
            done(2026, 10, 2) // gap on the 3rd
        )
        assertEquals(3, HomeDashboardMath.workoutStreak(sessions, today))
    }

    @Test fun `streak is zero without a completed session today`() {
        val today = at(2026, 10, 6)
        assertEquals(0, HomeDashboardMath.workoutStreak(listOf(done(2026, 10, 5), done(2026, 10, 4)), today))
        assertEquals(0, HomeDashboardMath.workoutStreak(listOf(open(2026, 10, 6)), today))
    }

    @Test fun `calorie unit names the day unless it is today`() {
        val today = at(2026, 10, 6, 12)
        assertEquals("cal today", HomeDashboardMath.calorieUnit(at(2026, 10, 6, 1), today, Locale.US))
        assertEquals("cal on Oct 3", HomeDashboardMath.calorieUnit(at(2026, 10, 3), today, Locale.US))
    }

    @Test fun `tiles always come back as four with the right ids and goal line`() {
        val withGoal = HomeDashboardMath.buildTiles(
            workoutCount = 3, weeklyGoal = 5, volumeDisplay = 2205, volumeUnitLabel = "lbs",
            dayCalories = 1450, calorieGoal = 2000, calorieUnit = "cal today", streak = 1
        )
        assertEquals(
            listOf(HomeDashboard.ID_WORKOUTS_WEEK, HomeDashboard.ID_VOLUME_WEEK, HomeDashboard.ID_CALORIES_TODAY, HomeDashboard.ID_STREAK),
            withGoal.map { it.id }
        )
        assertEquals("5", withGoal[0].goals)
        assertNull(withGoal[0].goalCTA)
        assertEquals("lbs · vol this week", withGoal[1].unit)
        assertEquals("2000", withGoal[2].goals)
        assertEquals("day", withGoal[3].unit)

        val noGoal = HomeDashboardMath.buildTiles(0, null, 0, "kg", 0, 2000, "cal today", 4)
        assertEquals("", noGoal[0].goals)
        assertEquals("Set weekly goal", noGoal[0].goalCTA)
        assertEquals("days", noGoal[3].unit)
    }
}
