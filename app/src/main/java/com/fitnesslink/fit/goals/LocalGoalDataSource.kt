package com.fitnesslink.fit.goals

import com.fitnesslink.fit.health.HealthConnectService
import com.fitnesslink.fit.model.GoalDay
import com.fitnesslink.fit.model.GoalEntry
import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.RecoveryMetric
import com.fitnesslink.fit.model.WaterUnit
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.goalCompletedSessions
import com.fitnesslink.fit.persistence.goalEntriesFor
import com.fitnesslink.fit.persistence.goalSetRecords
import com.fitnesslink.fit.persistence.weightEntriesCached
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * [GoalDataSource] over the local SQLite cache — the same tables the Weight
 * Log, workout history, Nutrition and the water tracker write, so a goal
 * reflects edits there on its next read. Health readings come from Health
 * Connect. Memoizes per instance; build a new one for each progress pass,
 * off the main thread (Health Connect reads block).
 */
class LocalGoalDataSource : GoalDataSource {
    private val userId: String by lazy { runCatching { DatabaseManager.user()?.id }.getOrNull() ?: "user1" }
    private val setCache = HashMap<String, List<GoalSetRecord>>()
    private val foodCache = HashMap<LocalDate, List<GoalFoodItem>>()
    private val waterCache = HashMap<Pair<LocalDate, WaterUnit>, Double>()
    private val hydration: HydrationGoal by lazy { runCatching { DatabaseManager.hydrationGoal() }.getOrDefault(HydrationGoal()) }
    private val weights: List<GoalWeightPoint> by lazy {
        runCatching { DatabaseManager.weightEntriesCached() }.getOrDefault(emptyList())
            .map { GoalWeightPoint(it.id, it.date.time, it.weight, it.unit) }
    }

    override fun weightEntries(): List<GoalWeightPoint> = weights

    override fun setRecords(movementName: String?): List<GoalSetRecord> =
        setCache.getOrPut(movementName?.trim()?.lowercase() ?: "\u0000all") {
            runCatching { DatabaseManager.goalSetRecords(userId, movementName) }.getOrDefault(emptyList())
        }

    override fun nutritionTargets(): GoalNutritionTargets =
        runCatching { DatabaseManager.nutritionGoal() }.getOrNull()?.let {
            GoalNutritionTargets(it.calorieGoal, it.proteinTarget, it.carbsTarget, it.fatTarget)
        } ?: GoalNutritionTargets()

    override fun nutritionDays(since: LocalDate): List<GoalNutritionDay> =
        runCatching { DatabaseManager.dailyNutritionRows(GoalDay.startOfDayMillis(since)) }.getOrDefault(emptyList()).map {
            GoalNutritionDay(GoalDay.localDate(it.date.time), it.calories.toDouble(), it.protein, it.carbs, it.fat)
        }

    override fun foodItems(day: LocalDate): List<GoalFoodItem> = foodCache.getOrPut(day) {
        runCatching { DatabaseManager.foodEntries(GoalDay.startOfDayMillis(day)) }.getOrDefault(emptyList())
            .map { GoalFoodItem(it.mealType.name, it.protein) }
    }

    override fun hydrationGoal(): HydrationGoal = hydration

    override fun waterTotal(day: LocalDate, unit: WaterUnit): Double = waterCache.getOrPut(day to unit) {
        runCatching { DatabaseManager.waterEntries(Date(GoalDay.startOfDayMillis(day))) }.getOrDefault(emptyList())
            .sumOf { it.unit.convert(it.amount, unit) }
    }

    override fun completedSessions(since: LocalDate): List<GoalSessionRecord> =
        runCatching { DatabaseManager.goalCompletedSessions(userId, GoalDay.startOfDayMillis(since)) }.getOrDefault(emptyList())

    override fun goalEntries(goalId: String): List<GoalEntry> =
        runCatching { DatabaseManager.goalEntriesFor(goalId) }.getOrDefault(emptyList())

    override val healthConnected: Boolean by lazy { HealthConnectService.refreshBlocking() }

    override fun sleepByNight(from: LocalDate, to: LocalDate): Map<LocalDate, Double> =
        health(HealthConnectService.Daily.SLEEP_SECONDS, from, to)

    override fun recoveryDailyAverages(metric: RecoveryMetric, from: LocalDate, to: LocalDate): Map<LocalDate, Double> =
        health(when (metric) {
            RecoveryMetric.RESTING_HEART_RATE -> HealthConnectService.Daily.RESTING_HEART_RATE
            RecoveryMetric.HRV -> HealthConnectService.Daily.HRV
        }, from, to)

    override fun dailySteps(from: LocalDate, to: LocalDate): Map<LocalDate, Double> =
        health(HealthConnectService.Daily.STEPS, from, to)

    /** Days [from]…[to] inclusive, up to now. */
    private fun health(kind: HealthConnectService.Daily, from: LocalDate, to: LocalDate): Map<LocalDate, Double> {
        if (!healthConnected) return emptyMap()
        val zone = ZoneId.systemDefault()
        val end = minOf(Instant.now(), to.plusDays(1).atStartOfDay(zone).toInstant())
        return runBlocking { HealthConnectService.daily(kind, from.atStartOfDay(zone).toInstant(), end, zone) }
    }
}
