package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.DayOfWeek
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.fitnesslink.fit.model.DailyMealSummary
import com.fitnesslink.fit.model.GroceryItem
import com.fitnesslink.fit.model.MealSlot
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.model.NutritionGoal

class MealPlanViewModel : ViewModel() {
    var weekSlots by mutableStateOf<List<MealSlot>>(emptyList())
    var selectedDay by mutableStateOf(DayOfWeek.MONDAY)
    var groceryItems by mutableStateOf<List<GroceryItem>>(emptyList())
    var goal by mutableStateOf(NutritionGoal())

    fun slotsForDay(day: DayOfWeek): List<MealSlot> =
        weekSlots.filter { it.day == day }

    fun slotFor(day: DayOfWeek, meal: MealType): MealSlot? =
        weekSlots.firstOrNull { it.day == day && it.mealType == meal }

    fun caloriesForDay(day: DayOfWeek): Int =
        slotsForDay(day).sumOf { it.calories }

    fun proteinForDay(day: DayOfWeek): Double =
        slotsForDay(day).sumOf { it.protein }

    fun fatForDay(day: DayOfWeek): Double =
        slotsForDay(day).sumOf { it.fat }

    fun carbsForDay(day: DayOfWeek): Double =
        slotsForDay(day).sumOf { it.carbs }

    val dailySummaries: List<DailyMealSummary>
        get() = DayOfWeek.entries.map { day ->
            DailyMealSummary(
                day = day,
                totalCalories = caloriesForDay(day),
                totalProtein = proteinForDay(day),
                totalFat = fatForDay(day),
                totalCarbs = carbsForDay(day),
                goal = goal.calorieGoal
            )
        }

    val weeklyAverageCalories: Int
        get() {
            val total = DayOfWeek.entries.sumOf { caloriesForDay(it) }
            return total / DayOfWeek.entries.size.coerceAtLeast(1)
        }

    val uncheckedGroceryCount: Int
        get() = groceryItems.count { !it.isChecked }

    fun assignSlot(slot: MealSlot) {
        viewModelScope.launch { DatabaseManager.saveMealSlot(slot) }
        val idx = weekSlots.indexOfFirst { it.day == slot.day && it.mealType == slot.mealType }
        weekSlots = if (idx >= 0) {
            weekSlots.toMutableList().apply { set(idx, slot) }
        } else {
            weekSlots + slot
        }
        regenerateGroceryList()
    }

    fun removeSlot(day: DayOfWeek, meal: MealType) {
        val slot = weekSlots.firstOrNull { it.day == day && it.mealType == meal }
        slot?.let { viewModelScope.launch { DatabaseManager.deleteMealSlot(it.id) } }
        weekSlots = weekSlots.filter { !(it.day == day && it.mealType == meal) }
        regenerateGroceryList()
    }

    fun toggleGroceryItem(id: String) {
        viewModelScope.launch { DatabaseManager.toggleGroceryItem(id) }
        groceryItems = groceryItems.map {
            if (it.id == id) it.copy(isChecked = !it.isChecked) else it
        }
    }

    fun regenerateGroceryList() {
        val combined = mutableMapOf<String, GroceryItem>()
        for (slot in weekSlots) {
            for (ingredient in slot.ingredients) {
                val key = ingredient.name.lowercase()
                val existing = combined[key]
                if (existing != null) {
                    combined[key] = existing.copy(
                        quantity = "${existing.quantity}, ${ingredient.quantity} ${ingredient.unit}"
                    )
                } else {
                    combined[key] = ingredient
                }
            }
        }
        groceryItems = combined.values.sortedBy { it.category.displayName }
        viewModelScope.launch { DatabaseManager.saveGroceryItems(groceryItems) }
    }

    fun loadData() {
        weekSlots = DatabaseManager.weeklyMealSlots()
        goal = DatabaseManager.nutritionGoal()
        regenerateGroceryList()
        viewModelScope.launch { refreshFromServer() }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remoteSlots = ApiClient.nutritionApi.getMealSlots()
            remoteSlots.forEach { DatabaseManager.saveMealSlot(it) }
            weekSlots = DatabaseManager.weeklyMealSlots()
        } catch (_: Exception) { /* use cached */ }
        try {
            val remoteGrocery = ApiClient.nutritionApi.getGroceryItems()
            groceryItems = remoteGrocery.sortedBy { it.category.displayName }
        } catch (_: Exception) { /* use cached */ }
    }
}
