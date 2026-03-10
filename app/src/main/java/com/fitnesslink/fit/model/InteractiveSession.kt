package com.fitnesslink.fit.model

data class InteractiveSession(
    val id: String = "",
    val taskRows: List<TaskRow> = emptyList()
)
