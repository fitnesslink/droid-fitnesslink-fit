package com.fitnesslink.fit.model

import java.util.UUID

enum class DayOfWeek(val short: String, val fullName: String) {
    MONDAY("Mon", "Monday"),
    TUESDAY("Tue", "Tuesday"),
    WEDNESDAY("Wed", "Wednesday"),
    THURSDAY("Thu", "Thursday"),
    FRIDAY("Fri", "Friday"),
    SATURDAY("Sat", "Saturday"),
    SUNDAY("Sun", "Sunday")
}

enum class GroceryCategory(val displayName: String, val icon: String) {
    PRODUCE("Produce", "leaf"),
    PROTEIN("Protein", "fish"),
    DAIRY("Dairy", "cup"),
    GRAINS("Grains & Bread", "cake"),
    PANTRY("Pantry", "cabinet"),
    FROZEN("Frozen", "snowflake"),
    OTHER("Other", "basket")
}

data class MealSlot(
    val id: String = UUID.randomUUID().toString(),
    val day: DayOfWeek = DayOfWeek.MONDAY,
    val mealType: MealType = MealType.BREAKFAST,
    val recipeName: String = "",
    val calories: Int = 0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val isAISuggestion: Boolean = false,
    val ingredients: List<GroceryItem> = emptyList()
)

data class GroceryItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val quantity: String = "",
    val unit: String = "",
    val category: GroceryCategory = GroceryCategory.OTHER,
    val isChecked: Boolean = false
)

data class DailyMealSummary(
    val id: String = UUID.randomUUID().toString(),
    val day: DayOfWeek = DayOfWeek.MONDAY,
    val totalCalories: Int = 0,
    val totalProtein: Double = 0.0,
    val totalFat: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val goal: Int = 2000
)
