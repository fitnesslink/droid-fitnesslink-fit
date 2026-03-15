package com.fitnesslink.fit.model

import java.util.UUID

data class NutritionGoal(
    val id: String = UUID.randomUUID().toString(),
    val calorieGoal: Int = 2000,
    val proteinTarget: Int = 150,
    val fatTarget: Int = 65,
    val carbsTarget: Int = 250
)
