package com.fitnesslink.fit.model

data class ProgressTracker(
    val totalPages: Int = 1,
    val pages: List<ProgressPage> = emptyList()
)

data class ProgressPage(
    val id: Int,
    val selected: Boolean
)
