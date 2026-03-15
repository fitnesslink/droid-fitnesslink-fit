package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.model.NutritionGoal

class NutritionViewModel : ViewModel() {
    var todayEntries by mutableStateOf<List<FoodEntry>>(emptyList())
    var goal by mutableStateOf(NutritionGoal())

    val totalCalories: Int get() = todayEntries.sumOf { it.calories }
    val totalProtein: Double get() = todayEntries.sumOf { it.protein }
    val totalFat: Double get() = todayEntries.sumOf { it.fat }
    val totalCarbs: Double get() = todayEntries.sumOf { it.carbs }

    val calorieProgress: Double
        get() {
            if (goal.calorieGoal <= 0) return 0.0
            return (totalCalories.toDouble() / goal.calorieGoal).coerceAtMost(1.0)
        }

    val remainingCalories: Int get() = maxOf(goal.calorieGoal - totalCalories, 0)

    fun entriesForMeal(mealType: MealType): List<FoodEntry> =
        todayEntries.filter { it.mealType == mealType }

    fun caloriesForMeal(mealType: MealType): Int =
        entriesForMeal(mealType).sumOf { it.calories }

    fun addEntry(entry: FoodEntry) {
        todayEntries = todayEntries + entry
    }

    fun deleteEntry(id: String) {
        todayEntries = todayEntries.filter { it.id != id }
    }

    fun loadData() {
        todayEntries = MockDataProvider.todayFoodEntries
        goal = MockDataProvider.nutritionGoal
    }
}
