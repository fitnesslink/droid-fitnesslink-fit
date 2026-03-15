package com.fitnesslink.fit.model

import java.util.UUID

data class BarcodeProduct(
    val id: String = UUID.randomUUID().toString(),
    val barcode: String = "",
    val name: String = "",
    val brand: String = "",
    val imageUrl: String = "",
    val caloriesPer100g: Double = 0.0,
    val proteinPer100g: Double = 0.0,
    val fatPer100g: Double = 0.0,
    val carbsPer100g: Double = 0.0,
    val servingSizeGrams: Double = 100.0,
    val servingUnit: String = "g"
) {
    fun toFoodEntry(mealType: MealType, servingMultiplier: Double = 1.0): FoodEntry {
        val factor = (servingSizeGrams * servingMultiplier) / 100.0
        return FoodEntry(
            name = if (brand.isEmpty()) name else "$name ($brand)",
            calories = (caloriesPer100g * factor).toInt(),
            protein = proteinPer100g * factor,
            fat = fatPer100g * factor,
            carbs = carbsPer100g * factor,
            servingSize = servingSizeGrams * servingMultiplier,
            servingUnit = servingUnit,
            mealType = mealType
        )
    }
}
