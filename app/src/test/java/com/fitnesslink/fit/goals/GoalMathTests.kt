package com.fitnesslink.fit.goals

import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.TrajectoryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalMathTests {

    private val day = 86_400_000L

    // MARK: - progressPercent (iOS `Goal.progressPercent`)

    @Test
    fun `no target means zero progress`() {
        assertEquals(0.0, GoalMath.progressPercent(12.0, null), 0.0)
        assertEquals(0.0, GoalMath.progressPercent(12.0, 0.0), 0.0)
        assertEquals(0.0, Goal(currentValue = 12.0, targetValue = null).progressPercent, 0.0)
    }

    @Test
    fun `progress is current over target as a percentage`() {
        assertEquals(25.0, GoalMath.progressPercent(5.0, 20.0), 0.0001)
        assertEquals(25.0, Goal(currentValue = 5.0, targetValue = 20.0).progressPercent, 0.0001)
    }

    @Test
    fun `progress caps at 100`() {
        assertEquals(100.0, GoalMath.progressPercent(30.0, 20.0), 0.0)
        assertEquals(100.0, Goal(currentValue = 30.0, targetValue = 20.0).progressPercent, 0.0)
    }

    // MARK: - expectedPercent

    @Test
    fun `expected percent is the elapsed fraction of the window`() {
        val start = 0L
        val end = 100 * day
        assertEquals(50.0, GoalMath.expectedPercent(start, end, 50 * day)!!, 0.0001)
        assertEquals(0.0, GoalMath.expectedPercent(start, end, -5 * day)!!, 0.0)
        assertEquals(100.0, GoalMath.expectedPercent(start, end, 500 * day)!!, 0.0)
    }

    @Test
    fun `expected percent is null without a usable target date`() {
        assertNull(GoalMath.expectedPercent(10L, null, 20L))
        assertNull(GoalMath.expectedPercent(10L, 10L, 20L))
    }

    // MARK: - deriveTrajectory

    private fun goal(current: Double, target: Double? = 100.0, start: Long = 0L, end: Long? = 100 * day) =
        Goal(currentValue = current, targetValue = target, startDate = start, targetDate = end)

    @Test
    fun `no target date is always on track`() {
        assertEquals(TrajectoryStatus.OnTrack, GoalMath.deriveTrajectory(goal(10.0, end = null), now = 50 * day))
    }

    @Test
    fun `well ahead of schedule is Ahead`() {
        // 50 % of the way through, 70 % done.
        assertEquals(TrajectoryStatus.Ahead, GoalMath.deriveTrajectory(goal(70.0), now = 50 * day))
    }

    @Test
    fun `within ten points of schedule is OnTrack`() {
        assertEquals(TrajectoryStatus.OnTrack, GoalMath.deriveTrajectory(goal(45.0), now = 50 * day))
        assertEquals(TrajectoryStatus.OnTrack, GoalMath.deriveTrajectory(goal(58.0), now = 50 * day))
    }

    @Test
    fun `ten to thirty points behind is Behind`() {
        assertEquals(TrajectoryStatus.Behind, GoalMath.deriveTrajectory(goal(30.0), now = 50 * day))
    }

    @Test
    fun `more than thirty behind or past the date is AtRisk`() {
        assertEquals(TrajectoryStatus.AtRisk, GoalMath.deriveTrajectory(goal(10.0), now = 50 * day))
        assertEquals(TrajectoryStatus.AtRisk, GoalMath.deriveTrajectory(goal(90.0), now = 120 * day))
    }

    @Test
    fun `a finished goal is Ahead even past its date`() {
        assertEquals(TrajectoryStatus.Ahead, GoalMath.deriveTrajectory(goal(100.0), now = 120 * day))
    }
}
