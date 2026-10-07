package com.fitnesslink.fit.goals

import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.TrajectoryStatus

/**
 * Pure goal arithmetic shared by the list cards, the detail header and the
 * unit tests. Progress mirrors iOS `Goal.progressPercent`; the trajectory
 * derivation is the client-side fallback for goals the server hasn't scored
 * yet (a goal created offline, or one whose `trajectoryStatus` is null).
 */
object GoalMath {

    /** 0 without a positive target, otherwise `current / target` capped at 100 (iOS `Goal.progressPercent`). */
    fun progressPercent(currentValue: Double, targetValue: Double?): Double {
        val target = targetValue ?: return 0.0
        if (target <= 0) return 0.0
        return minOf(currentValue / target * 100.0, 100.0).coerceAtLeast(0.0)
    }

    /**
     * How far through the goal's time window [now] is, 0–100; null when the
     * goal has no target date or an inverted window.
     */
    fun expectedPercent(startDate: Long, targetDate: Long?, now: Long): Double? {
        val end = targetDate ?: return null
        if (end <= startDate) return null
        val fraction = (now - startDate).toDouble() / (end - startDate).toDouble()
        return (fraction * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Compare actual progress with the time-elapsed expectation:
     * ≥ 10 points ahead → [TrajectoryStatus.Ahead]; within 10 → OnTrack;
     * within 30 behind → Behind; further (or past the target date while
     * unfinished) → AtRisk. A goal with no target or date is always OnTrack.
     */
    fun deriveTrajectory(goal: Goal, now: Long = System.currentTimeMillis()): TrajectoryStatus {
        val progress = progressPercent(goal.currentValue, goal.targetValue)
        if (progress >= 100.0) return TrajectoryStatus.Ahead
        val expected = expectedPercent(goal.startDate, goal.targetDate, now) ?: return TrajectoryStatus.OnTrack
        val targetDate = goal.targetDate
        if (targetDate != null && now > targetDate) return TrajectoryStatus.AtRisk
        val diff = progress - expected
        return when {
            diff >= 10.0 -> TrajectoryStatus.Ahead
            diff >= -10.0 -> TrajectoryStatus.OnTrack
            diff >= -30.0 -> TrajectoryStatus.Behind
            else -> TrajectoryStatus.AtRisk
        }
    }
}
