package com.fitnesslink.fit.model

data class WorkoutPhase(
    val id: String = "",
    val name: String = "",
    val taskRows: List<TaskRow> = emptyList()
)
