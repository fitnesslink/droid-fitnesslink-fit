package com.fitnesslink.fit.model

import java.util.UUID

data class DailyCalorieSummary(
    val id: String = UUID.randomUUID().toString(),
    val dayLabel: String = "",
    val calories: Int = 0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val goal: Int = 2000
)
