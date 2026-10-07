package com.fitnesslink.fit.model

data class CalendarCell(
    val id: String = "",
    val dayNumber: Int = 0,
    val name: String = "",
    val status: String = "",
    val selected: Boolean = false,
    /** At least one program-scheduled entry lands on this day (iOS FTNSSLNK-168 marker). */
    val hasProgram: Boolean = false
)
