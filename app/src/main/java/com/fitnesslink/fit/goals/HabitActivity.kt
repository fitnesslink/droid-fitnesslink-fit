package com.fitnesslink.fit.goals

import com.fitnesslink.fit.model.HabitStats
import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.model.Streak

/** iOS `HabitCalendarDay.status` values rendered by `CalendarHeatmapView`. */
enum class HabitDayStatus(val wire: String) {
    COMPLETED("completed"),
    MISSED("missed"),
    FREEZE("freeze"),
    /** Future days, today-not-yet-done, and days before the habit existed. */
    PENDING("pending")
}

/**
 * Pure derivation of the habit-detail heatmap and statistics from local
 * `habit_logs` (iOS only had mock data here). Days are local start-of-day
 * millis throughout.
 */
object HabitActivity {

    /**
     * Every day from the Sunday [weeks]-1 weeks before [today]'s week through
     * the Saturday of [today]'s week, ascending.
     */
    fun windowDays(today: Long, weeks: Int): List<Long> {
        val todayStart = ProgramDates.startOfDay(today)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = todayStart }
        val daysSinceSunday = (cal.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.SUNDAY + 7) % 7
        val firstSunday = ProgramDates.addDays(todayStart, -daysSinceSunday - (weeks - 1) * 7)
        return List(weeks * 7) { ProgramDates.addDays(firstSunday, it) }
    }

    /**
     * Status per day: completed wins; a freeze day is a freeze; a day before
     * the habit's tracked window, today (not yet done) or any future day is
     * pending; every other past day without a log is missed.
     *
     * [trackedFrom] is the habit's first tracked day (its `createdAt`, or the
     * earliest log when that is older). Null tracks every day in the window.
     */
    fun dayStatuses(
        days: List<Long>,
        completedDays: Set<Long>,
        today: Long,
        trackedFrom: Long? = null,
        freezeDays: Set<Long> = emptySet()
    ): Map<Long, HabitDayStatus> {
        val todayStart = ProgramDates.startOfDay(today)
        val trackedStart = trackedFrom?.let { ProgramDates.startOfDay(it) }
        return days.associateWith { day ->
            val d = ProgramDates.startOfDay(day)
            when {
                d in completedDays -> HabitDayStatus.COMPLETED
                d in freezeDays -> HabitDayStatus.FREEZE
                d >= todayStart -> HabitDayStatus.PENDING
                trackedStart != null && d < trackedStart -> HabitDayStatus.PENDING
                else -> HabitDayStatus.MISSED
            }
        }
    }

    /** iOS `HabitDetailViewModel.calculateStats`: pending days are not counted. */
    fun stats(statuses: Collection<HabitDayStatus>, streak: Streak): HabitStats {
        val completed = statuses.count { it == HabitDayStatus.COMPLETED }
        val missed = statuses.count { it == HabitDayStatus.MISSED }
        val freeze = statuses.count { it == HabitDayStatus.FREEZE }
        val total = completed + missed + freeze
        return HabitStats(
            totalDays = total,
            completedDays = completed,
            missedDays = missed,
            freezeDays = freeze,
            completionRate = if (total > 0) completed.toDouble() / total * 100.0 else 0.0,
            currentStreak = streak.currentCount,
            longestStreak = streak.longestCount
        )
    }
}
