package com.fitnesslink.fit.model

import java.util.Date
import java.util.UUID

enum class MealType(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack")
}

data class FoodEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val calories: Int = 0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val servingSize: Double = 1.0,
    val servingUnit: String = "serving",
    val mealType: MealType = MealType.BREAKFAST,
    val loggedAt: Date = Date(),
    val isCustomTemplate: Boolean = false
)
