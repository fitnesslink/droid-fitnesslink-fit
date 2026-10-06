package com.fitnesslink.fit.model

import java.util.UUID

data class ProgramDaySlot(
    val id: String = UUID.randomUUID().toString(),
    val weekNumber: Int,
    /** 1 = Sunday … 7 = Saturday — matches the backend seeders and the grid's Sun-first column order. */
    val dayNumber: Int,
    val workout: WorkoutList? = null,
    /**
     * Set whenever the schedule has an entry for this day, even if the
     * workout's metadata hasn't been hydrated locally yet, so the renderer
     * shows a "Workout" placeholder instead of calling it a rest day.
     */
    val workoutId: String? = null
) {
    val dayLabel: String
        get() = when (dayNumber) {
            1 -> "Sun"
            2 -> "Mon"
            3 -> "Tue"
            4 -> "Wed"
            5 -> "Thu"
            6 -> "Fri"
            7 -> "Sat"
            else -> ""
        }

    companion object {
        fun emptyWeek(weekNumber: Int): List<ProgramDaySlot> =
            (1..7).map { day -> ProgramDaySlot(weekNumber = weekNumber, dayNumber = day) }
    }
}

data class ProgramWeek(
    val id: String = UUID.randomUUID().toString(),
    val weekNumber: Int,
    val days: List<ProgramDaySlot>
) {
    companion object {
        fun empty(weekNumber: Int): ProgramWeek = ProgramWeek(
            weekNumber = weekNumber,
            days = ProgramDaySlot.emptyWeek(weekNumber)
        )
    }
}
