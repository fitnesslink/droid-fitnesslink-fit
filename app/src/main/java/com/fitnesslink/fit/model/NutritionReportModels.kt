package com.fitnesslink.fit.model

import java.util.Date

enum class NutritionReportTab(val label: String) {
    OVERVIEW("Overview"), BY_MEAL("By Meal"), BY_FOOD("By Food");
}

enum class NutritionMetric(val title: String, val iconName: String, val colorName: String) {
    AVG_CALORIES("Avg Calories", "flame", "OrangeTheme"),
    GOAL_ADHERENCE("Goal Hit", "target", "FLPrimary"),
    AVG_PROTEIN("Avg Protein", "fish", "BlueTheme"),
    AVG_CARBS("Avg Carbs", "leaf", "FLPrimary"),
    AVG_FAT("Avg Fat", "drop", "PurpleTheme"),
    MACRO_BALANCE("Macro Split", "pie_chart", "BlueTheme"),
    LOGGING_STREAK("Streak", "bolt", "OrangeTheme"),
    CALORIE_SURPLUS("Avg Surplus", "swap_vert", "PurpleTheme");
}

data class NutritionReportData(
    val avgCalories: Double = 0.0,
    val goalAdherencePercent: Double = 0.0,
    val avgProtein: Double = 0.0,
    val avgCarbs: Double = 0.0,
    val avgFat: Double = 0.0,
    val proteinRatio: Double = 0.0,
    val fatRatio: Double = 0.0,
    val carbsRatio: Double = 0.0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val avgSurplus: Double = 0.0,
    val daysLogged: Int = 0,
    val totalDays: Int = 0
)

data class DailyNutritionRow(
    val id: String,
    val date: Date,
    val calories: Int,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val goalCalories: Int,
    val isOnTarget: Boolean
)

data class MealTypeAggregate(
    val id: String,
    val mealType: MealType,
    val avgCalories: Double = 0.0,
    val avgProtein: Double = 0.0,
    val avgFat: Double = 0.0,
    val avgCarbs: Double = 0.0,
    val percentOfDailyCalories: Double = 0.0,
    val timesLogged: Int = 0
)

data class FoodAggregate(
    val id: String,
    val foodName: String,
    val timesLogged: Int = 0,
    val avgCalories: Double = 0.0,
    val totalCalories: Int = 0,
    val totalProtein: Double = 0.0,
    val avgProtein: Double = 0.0
)
