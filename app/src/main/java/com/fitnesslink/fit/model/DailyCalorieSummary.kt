package com.fitnesslink.fit.model

import java.util.Calendar
import java.util.Date
import java.util.UUID

/**
 * One day of the Weekly Progress bars. Fed from the RANGE summary endpoint
 * (`GET /summary?from=&to=`) when online, summed from the local food-entry
 * cache otherwise (iOS `WeeklyProgressView.loadWeek`).
 */
data class DailyCalorieSummary(
    val id: String = UUID.randomUUID().toString(),
    val dayLabel: String = "",
    val calories: Int = 0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val goal: Int = 2000
)

// MARK: - Dashboard display logic (port of iOS `NutritionSummaryDisplay.swift`)
//
// Kept out of the composables so the over-target states and calendar-dot
// mapping are plain-JVM unit-testable (FTNSSLNK-193/194/196).

/**
 * One goal / consumed / remaining line (calories or a macro).
 *
 * `remaining` keeps its sign so the over-target state is derived, not
 * clamped away: consumed > goal is a SIGNAL (amber styling), never an error.
 * `goal` null means no target was in effect — the bar renders unfilled and
 * no remaining label is shown.
 */
data class MacroProgress(
    val goal: Double?,
    val consumed: Double
) {
    /** Signed: negative when over target. Null when there is no goal. */
    val remaining: Double? get() = goal?.let { it - consumed }

    val isOver: Boolean get() = (remaining ?: 0.0) < 0

    /** How far past the goal, >= 0. Zero when at/under target or no goal. */
    val overAmount: Double get() = maxOf(-(remaining ?: 0.0), 0.0)

    /** Bar fill 0…1 (capped — the over state is styled, not overdrawn). */
    val fraction: Double
        get() {
            val g = goal ?: return 0.0
            if (g <= 0) return 0.0
            return (consumed / g).coerceAtMost(1.0)
        }

    /** Uncapped consumed/goal ratio for the calorie ring. */
    val rawFraction: Double
        get() {
            val g = goal ?: return 0.0
            if (g <= 0) return 0.0
            return consumed / g
        }
}

/** Visual state of one day cell in the calendar sheet / weekly strip. */
enum class NutritionDayDotState {
    /** Past/today with food or water logged — filled dot. */
    LOGGED,
    /** Past/today with nothing logged — no dot. */
    EMPTY,
    /** After today — planning context: muted styling, no dot, no logging. */
    FUTURE;

    companion object {
        /**
         * `hasData` comes from the RANGE summary rollup; null means no rollup
         * is available (offline / not fetched yet), which renders as [EMPTY]
         * so the dots degrade gracefully.
         */
        fun state(date: Date, hasData: Boolean?, today: Date = Date()): NutritionDayDotState {
            val day = MealPlanDay.dayStart(date)
            val todayStart = MealPlanDay.dayStart(today)
            if (day.after(todayStart)) return FUTURE
            return if (hasData == true) LOGGED else EMPTY
        }
    }
}

/** Month-grid math for the nutrition calendar sheet (Sunday-first columns). */
object NutritionCalendarMonth {
    /**
     * The cells of the month containing [date]: leading nulls pad to the
     * Sunday column, then one Date per day of the month, trailing nulls pad
     * the last row. Count is always a multiple of 7.
     */
    fun gridDays(containing: Date): List<Date?> {
        val first = firstOfMonth(containing)
        val cal = MealPlanDay.calendar().apply { time = first }
        val dayCount = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val leading = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
        val cells = ArrayList<Date?>(42)
        repeat(leading) { cells.add(null) }
        for (day in 0 until dayCount) cells.add(MealPlanDay.addDays(first, day))
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }

    /** First day (local midnight) of the month [offset] months from the one containing [date]. */
    fun firstOfMonth(date: Date, offset: Int = 0): Date = MealPlanDay.calendar().apply {
        time = MealPlanDay.dayStart(date)
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, offset)
    }.time

    /** Last day (local midnight) of the month containing [date]. */
    fun lastOfMonth(date: Date): Date = MealPlanDay.calendar().apply {
        time = firstOfMonth(date)
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
    }.time
}

/** Relative day labels for the dashboard header ("Today" / "Yesterday" / "Tomorrow" / weekday). */
object NutritionDayTitle {
    fun relativeDays(date: Date, today: Date = Date()): Long {
        val a = MealPlanDay.dayStart(date).time
        val b = MealPlanDay.dayStart(today).time
        return Math.round((a - b).toDouble() / 86_400_000.0)
    }

    fun title(date: Date, today: Date = Date(), weekdayName: (Date) -> String): String = when (relativeDays(date, today)) {
        0L -> "Today"
        -1L -> "Yesterday"
        1L -> "Tomorrow"
        else -> weekdayName(date)
    }
}
