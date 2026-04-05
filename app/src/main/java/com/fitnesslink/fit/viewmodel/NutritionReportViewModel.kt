package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.persistence.DatabaseManager
import java.util.Calendar
import java.util.Date

class NutritionReportViewModel : ViewModel() {
    var selectedTimeRange by mutableStateOf(ReportTimeRange.THIRTY_DAYS)
    var selectedTab by mutableStateOf(NutritionReportTab.OVERVIEW)
    var reportData by mutableStateOf(NutritionReportData())
    var dailyRows by mutableStateOf<List<DailyNutritionRow>>(emptyList())
    var mealAggregates by mutableStateOf<List<MealTypeAggregate>>(emptyList())
    var foodAggregates by mutableStateOf<List<FoodAggregate>>(emptyList())
    var goal by mutableStateOf(NutritionGoal())
    var calorieChartPoints by mutableStateOf<List<ChartPoint>>(emptyList())

    fun displayValue(metric: NutritionMetric): String = when (metric) {
        NutritionMetric.AVG_CALORIES -> "${reportData.avgCalories.toInt()}"
        NutritionMetric.GOAL_ADHERENCE -> "${reportData.goalAdherencePercent.toInt()}%"
        NutritionMetric.AVG_PROTEIN -> "${reportData.avgProtein.toInt()}g"
        NutritionMetric.AVG_CARBS -> "${reportData.avgCarbs.toInt()}g"
        NutritionMetric.AVG_FAT -> "${reportData.avgFat.toInt()}g"
        NutritionMetric.MACRO_BALANCE -> "${(reportData.proteinRatio * 100).toInt()}/${(reportData.carbsRatio * 100).toInt()}/${(reportData.fatRatio * 100).toInt()}"
        NutritionMetric.LOGGING_STREAK -> "${reportData.currentStreak}"
        NutritionMetric.CALORIE_SURPLUS -> { val v = reportData.avgSurplus.toInt(); if (v >= 0) "+$v" else "$v" }
    }

    fun subtitle(metric: NutritionMetric): String = when (metric) {
        NutritionMetric.AVG_CALORIES -> "kcal/day"
        NutritionMetric.GOAL_ADHERENCE -> "days on target"
        NutritionMetric.AVG_PROTEIN -> "protein/day"
        NutritionMetric.AVG_CARBS -> "carbs/day"
        NutritionMetric.AVG_FAT -> "fat/day"
        NutritionMetric.MACRO_BALANCE -> "P/C/F %"
        NutritionMetric.LOGGING_STREAK -> "day streak"
        NutritionMetric.CALORIE_SURPLUS -> "kcal vs goal"
    }

    fun loadData() {
        val since = sinceMillis(selectedTimeRange)
        goal = DatabaseManager.nutritionGoal()
        dailyRows = DatabaseManager.dailyNutritionRows(since)
        mealAggregates = DatabaseManager.mealTypeAggregates(since)
        foodAggregates = DatabaseManager.foodAggregates(since)
        computeReportData(since)
        calorieChartPoints = dailyRows.map { ChartPoint(it.date, it.calories.toDouble()) }.sortedBy { it.date }
    }

    private fun sinceMillis(range: ReportTimeRange): Long {
        val days = range.days ?: return 0L
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return cal.timeInMillis
    }

    private fun computeReportData(since: Long) {
        if (dailyRows.isEmpty()) { reportData = NutritionReportData(); return }
        val days = dailyRows
        val totalCals = days.sumOf { it.calories }
        val totalP = days.sumOf { it.protein }; val totalF = days.sumOf { it.fat }; val totalC = days.sumOf { it.carbs }
        val avgCal = totalCals.toDouble() / days.size
        val pCal = totalP * 4; val cCal = totalC * 4; val fCal = totalF * 9
        val macroCals = pCal + cCal + fCal
        val onTarget = days.count { it.isOnTarget }

        val logDates = DatabaseManager.nutritionLogDates(since)
        val streak = computeStreak(logDates)

        reportData = NutritionReportData(
            avgCalories = avgCal, goalAdherencePercent = onTarget.toDouble() / days.size * 100,
            avgProtein = totalP / days.size, avgCarbs = totalC / days.size, avgFat = totalF / days.size,
            proteinRatio = if (macroCals > 0) pCal / macroCals else 0.0,
            carbsRatio = if (macroCals > 0) cCal / macroCals else 0.0,
            fatRatio = if (macroCals > 0) fCal / macroCals else 0.0,
            currentStreak = streak.first, longestStreak = streak.second,
            avgSurplus = avgCal - goal.calorieGoal, daysLogged = days.size, totalDays = days.size
        )
    }

    private fun computeStreak(dates: List<Date>): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        val dayMs = 86400000L
        val unique = dates.map { d -> cal.time = d; cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0); cal.timeInMillis }.toSortedSet().toList()
        if (unique.isEmpty()) return 0 to 0
        var cur = 1; var longest = 1
        for (i in 1 until unique.size) { if (unique[i] - unique[i - 1] == dayMs) { cur++; longest = maxOf(longest, cur) } else cur = 1 }
        longest = maxOf(longest, cur)
        cal.time = Date(); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis; val yesterday = today - dayMs
        var currentStreak = 0
        if (unique.last() == today || unique.last() == yesterday) {
            currentStreak = 1
            for (i in unique.size - 2 downTo 0) { if (unique[i + 1] - unique[i] == dayMs) currentStreak++ else break }
        }
        return currentStreak to longest
    }
}
