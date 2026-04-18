package com.fitnesslink.fit.model

import java.util.UUID

data class ProgramDaySlot(
    val id: String = UUID.randomUUID().toString(),
    val weekNumber: Int,
    val dayNumber: Int, // 1=Mon, 2=Tue, ..., 7=Sun
    val workout: WorkoutList? = null
) {
    val dayLabel: String
        get() = when (dayNumber) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            7 -> "Sun"
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
