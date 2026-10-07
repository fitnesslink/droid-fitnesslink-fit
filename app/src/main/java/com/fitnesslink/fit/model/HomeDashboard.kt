package com.fitnesslink.fit.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One Home dashboard tile (iOS `HomeDashboard`). Tiles are computed from
 * local data by [HomeDashboardMath] — the legacy `home_dashboards` table is
 * no longer read.
 */
data class HomeDashboard(
    val id: String = "",
    val name: String = "",
    val progress: String = "",
    val unit: String = "",
    val goals: String = "",
    /**
     * Call-to-action rendered in place of the "Goal:" line when the tile
     * has no goal yet (FTNSSLNK-365: "Set weekly goal").
     */
    val goalCTA: String? = null
) {
    companion object {
        const val ID_WORKOUTS_WEEK = "workouts-week"
        const val ID_VOLUME_WEEK = "weight-lifted-week"
        const val ID_CALORIES_TODAY = "calories-today"
        const val ID_STREAK = "streak"
    }
}

// MARK: - Date strip

/** Status values for a Home date-strip cell (iOS `HorizontalCalendar.status`). */
object DateStripStatus {
    const val COMPLETED = "completed"
    const val SCHEDULED = "scheduled"
    /** A day inside a program span with no workout — a prescribed rest day. */
    const val REST = "rest"
    const val NONE = ""

    /**
     * iOS `HomeViewModel.decorateCalendarItems`: completed when every entry
     * on the day is done, scheduled when at least one isn't, rest when the
     * day has no entries but falls inside a program's calendar span,
     * nothing otherwise.
     */
    fun derive(entryCompletion: List<Boolean>, inProgramSpan: Boolean): String = when {
        entryCompletion.isNotEmpty() -> if (entryCompletion.all { it }) COMPLETED else SCHEDULED
        inProgramSpan -> REST
        else -> NONE
    }
}

/**
 * One cell of the Home date strip. Replaces the mock-driven
 * `HorizontalCalendar` on Home: carries the first workout's [title] for the
 * accessibility label and a real [status] derived from calendar rows.
 */
data class HomeDateItem(
    val dayNumber: Int,
    /** Day-of-month label ("5"). */
    val name: String,
    /** Short weekday label ("Mon"). */
    val weekDay: String,
    val status: String = DateStripStatus.NONE,
    val selected: Boolean = false,
    val title: String = ""
) {
    val id: Int get() = dayNumber

    /** iOS `DateCellView` accessibility label. */
    val accessibilityLabel: String
        get() = buildString {
            append(weekDay).append(' ').append(name)
            if (title.isNotEmpty()) append(", ").append(title)
            if (status == DateStripStatus.COMPLETED) append(", completed")
        }
}

/** A calendar entry reduced to what the date strip needs. */
data class DateStripEntry(val dayNumber: Int, val title: String, val isCompleted: Boolean)

/**
 * Pure builder for the Home date strip. [programSpanDays] are the
 * day-of-month numbers (in the strip's month) covered by a program's
 * calendar span; the caller derives them from
 * `DatabaseManager.programCalendarSpan()`.
 */
object DateStripBuilder {
    fun build(
        year: Int,
        monthZeroBased: Int,
        selectedDay: Int,
        entries: List<DateStripEntry>,
        programSpanDays: Set<Int>,
        locale: Locale = Locale.getDefault()
    ): List<HomeDateItem> {
        val cal = Calendar.getInstance(locale).apply {
            clear(); set(year, monthZeroBased, 1)
        }
        val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val weekdayFormat = SimpleDateFormat("EEE", locale)
        val byDay = entries.groupBy { it.dayNumber }
        return (1..totalDays).map { day ->
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dayEntries = byDay[day].orEmpty()
            val status = DateStripStatus.derive(dayEntries.map { it.isCompleted }, day in programSpanDays)
            HomeDateItem(
                dayNumber = day,
                name = day.toString(),
                weekDay = weekdayFormat.format(cal.time),
                status = status,
                selected = day == selectedDay,
                title = when (status) {
                    DateStripStatus.REST -> "Rest"
                    DateStripStatus.NONE -> ""
                    else -> dayEntries.first().title
                }
            )
        }
    }

    /**
     * Day numbers of [year]/[monthZeroBased] that fall inside [span]
     * (inclusive, start compared at local midnight — iOS
     * `decorateCalendarItems`).
     */
    fun programSpanDays(year: Int, monthZeroBased: Int, span: Pair<Long, Long>?): Set<Int> {
        if (span == null) return emptySet()
        val spanStart = ProgramDates.startOfDay(span.first)
        val spanEnd = span.second
        val cal = Calendar.getInstance().apply { clear(); set(year, monthZeroBased, 1) }
        val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (1..totalDays).filter { day ->
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dayStart = cal.timeInMillis
            dayStart >= spanStart && dayStart <= spanEnd
        }.toSet()
    }
}

// MARK: - Dashboard tiles

/** A completed-or-not session reduced to what the dashboard tiles need. */
data class DashboardSession(
    val dateMillis: Long,
    val isCompleted: Boolean,
    /** Stored lbs (app-wide canonical lifting unit). */
    val totalWeightLbs: Double
)

/**
 * Pure math behind the four Home dashboard tiles (iOS
 * `HomeViewModel.computeDashboards` / `computeWorkoutStreak`). Everything
 * is day-granular on the default local calendar via [ProgramDates].
 */
object HomeDashboardMath {
    /** Start (inclusive) of the 7-day window ending on the day containing [referenceMillis]. */
    fun weekWindowStart(referenceMillis: Long): Long = ProgramDates.addDays(ProgramDates.startOfDay(referenceMillis), -6)

    /** End (exclusive) of that window — the midnight after the reference day. */
    fun weekWindowEnd(referenceMillis: Long): Long = ProgramDates.addDays(ProgramDates.startOfDay(referenceMillis), 1)

    /** Completed sessions whose start falls inside the 7-day window ending on the reference day. */
    fun sessionsThisWeek(sessions: List<DashboardSession>, referenceMillis: Long): List<DashboardSession> {
        val start = weekWindowStart(referenceMillis)
        val end = weekWindowEnd(referenceMillis)
        return sessions.filter { it.isCompleted && it.dateMillis >= start && it.dateMillis < end }
    }

    /** Sum of stored-lbs volume across [sessions], converted to the display unit and rounded. */
    fun volumeDisplay(sessions: List<DashboardSession>, unit: WeightUnit = WeightUnit.current): Int {
        val lbs = sessions.sumOf { it.totalWeightLbs }
        val display = if (unit == WeightUnit.KG) lbs / WeightUnit.lbsPerKg else lbs
        return display.roundToInt()
    }

    /**
     * Consecutive days ending on [todayMillis]'s day with at least one
     * completed session. A day without one breaks the streak immediately
     * (no grace for "haven't trained yet today").
     */
    fun workoutStreak(sessions: List<DashboardSession>, todayMillis: Long): Int {
        val daysWithWork = sessions.filter { it.isCompleted }.mapTo(HashSet()) { ProgramDates.startOfDay(it.dateMillis) }
        var streak = 0
        var cursor = ProgramDates.startOfDay(todayMillis)
        while (daysWithWork.contains(cursor)) {
            streak += 1
            cursor = ProgramDates.addDays(cursor, -1)
        }
        return streak
    }

    /** "cal today" or "cal on Oct 3". */
    fun calorieUnit(referenceMillis: Long, todayMillis: Long, locale: Locale = Locale.getDefault()): String {
        if (ProgramDates.isSameDay(referenceMillis, todayMillis)) return "cal today"
        return "cal on " + SimpleDateFormat("MMM d", locale).format(Date(referenceMillis))
    }

    /** Always four tiles so the Dashboard section is never empty. */
    fun buildTiles(
        workoutCount: Int,
        weeklyGoal: Int?,
        volumeDisplay: Int,
        volumeUnitLabel: String,
        dayCalories: Int,
        calorieGoal: Int,
        calorieUnit: String,
        streak: Int
    ): List<HomeDashboard> = listOf(
        HomeDashboard(
            id = HomeDashboard.ID_WORKOUTS_WEEK,
            name = "Workouts",
            progress = workoutCount.toString(),
            unit = "this week",
            goals = weeklyGoal?.toString() ?: "",
            goalCTA = if (weeklyGoal == null) "Set weekly goal" else null
        ),
        HomeDashboard(
            id = HomeDashboard.ID_VOLUME_WEEK,
            name = "Volume",
            progress = volumeDisplay.toString(),
            unit = "$volumeUnitLabel · vol this week",
            goals = ""
        ),
        HomeDashboard(
            id = HomeDashboard.ID_CALORIES_TODAY,
            name = "Calories",
            progress = dayCalories.toString(),
            unit = calorieUnit,
            goals = calorieGoal.toString()
        ),
        HomeDashboard(
            id = HomeDashboard.ID_STREAK,
            name = "Streak",
            progress = streak.toString(),
            unit = if (streak == 1) "day" else "days",
            goals = ""
        )
    )
}
