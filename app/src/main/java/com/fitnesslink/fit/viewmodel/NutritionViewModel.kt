package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.model.NutritionGoal
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

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
        viewModelScope.launch {
            DatabaseManager.saveFoodEntry(entry)
            todayEntries = DatabaseManager.allFoodEntries()
        }
        viewModelScope.launch {
            try { ApiClient.nutritionApi.addFoodEntry(entry) } catch (_: Exception) {}
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            DatabaseManager.deleteFoodEntry(id)
            todayEntries = DatabaseManager.allFoodEntries()
        }
        viewModelScope.launch {
            try { ApiClient.nutritionApi.deleteFoodEntry(id) } catch (_: Exception) {}
        }
    }

    fun loadData() {
        todayEntries = DatabaseManager.allFoodEntries()
        goal = DatabaseManager.nutritionGoal()
        viewModelScope.launch { refreshFromServer() }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remoteEntries = ApiClient.nutritionApi.getFoodEntries()
            remoteEntries.forEach { DatabaseManager.saveFoodEntry(it) }
            todayEntries = DatabaseManager.allFoodEntries()
        } catch (_: Exception) { /* use cached */ }
        try {
            val remoteGoal = ApiClient.nutritionApi.getGoal()
            DatabaseManager.insertNutritionGoal(remoteGoal)
            goal = DatabaseManager.nutritionGoal()
        } catch (_: Exception) { /* use cached */ }
    }
}
