package com.fitnesslink.fit.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.MacroProgress
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealSlot
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.model.NutritionDayDotState
import com.fitnesslink.fit.model.NutritionGoal
import com.fitnesslink.fit.model.WaterIntakeEntry
import com.fitnesslink.fit.model.api.Micronutrient
import com.fitnesslink.fit.model.api.line
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.DailyMicrosSummary
import com.fitnesslink.fit.network.dto.MacroSummaryLine
import com.fitnesslink.fit.network.dto.MicroSummaryLine
import com.fitnesslink.fit.network.dto.NutritionDailySummary
import com.fitnesslink.fit.network.dto.NutritionDailyTargets
import com.fitnesslink.fit.network.dto.NutritionDayRollup
import com.fitnesslink.fit.network.dto.QuickLogCopyRequest
import com.fitnesslink.fit.network.dto.QuickLogMealSource
import com.fitnesslink.fit.nutrition.FoodLogStore
import com.fitnesslink.fit.nutrition.HydrationStore
import com.fitnesslink.fit.nutrition.MealPlanStore
import com.fitnesslink.fit.nutrition.NutritionGoalsStore
import com.fitnesslink.fit.nutrition.NutritionSummaryStore
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.foodEntries
import com.fitnesslink.fit.persistence.loggedDayKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Drives the nutrition dashboard (port of iOS `NutritionViewModel`,
 * FTNSSLNK-193/194/196/213). The dashboard shows ONE selected day:
 *
 * - entries come from `FoodLogStore` (agent B) — the day-filtered local
 *   rows, refreshed from the server per day (fixes the all-time "today"
 *   totals bug the old screen had);
 * - every displayed number prefers the server's daily summary
 *   (`NutritionSummaryStore`), falling back to local sums + the current
 *   goal while offline or right after a local mutation;
 * - the week strip / calendar dots come from the RANGE summary;
 * - planned meals come from `MealPlanStore` (agent C).
 */
class NutritionViewModel : ViewModel() {
    private val tag = "NutritionViewModel"

    var selectedDate by mutableStateOf(MealPlanDay.dayStart(Date()))
        private set
    var entries by mutableStateOf<List<FoodEntry>>(emptyList())
        private set
    var goal by mutableStateOf(NutritionGoal())
        private set

    /** Effective-dated targets for the selected day (past days keep the goal in effect then). */
    var dayTargets by mutableStateOf<NutritionDailyTargets?>(null)
        private set
    /** The full daily summary — the ONE round trip the dashboard renders from. Null = render local sums. */
    var daySummary by mutableStateOf<NutritionDailySummary?>(null)
        private set
    /** The day's micro lines; kept across reloads (only cleared on day change). */
    var dayMicros by mutableStateOf<DailyMicrosSummary?>(null)
        private set
    /** Per-day rollups for the Sunday-anchored week containing the selected day. */
    var weekRollups by mutableStateOf<List<NutritionDayRollup>>(emptyList())
        private set
    /** Offline completeness fallback: day keys with locally logged food this week. */
    var locallyLoggedDays by mutableStateOf<Set<String>>(emptySet())
        private set
    var plannedSlots by mutableStateOf<List<MealSlot>>(emptyList())
        private set
    var waterEntries by mutableStateOf<List<WaterIntakeEntry>>(emptyList())
        private set
    var hydrationGoal by mutableStateOf(HydrationGoal())
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    // Quick log (FTNSSLNK-213)
    var quickLogSources by mutableStateOf<List<QuickLogMealSource>>(emptyList())
        private set
    var quickLogInFlight by mutableStateOf<Set<MealType>>(emptySet())
        private set
    var quickLogError by mutableStateOf<String?>(null)
        private set

    private var observers: Job? = null

    init {
        NutritionSummaryStore.ensureRegistered()
        NutritionGoalsStore.hydrateFromCache()
        observers = viewModelScope.launch {
            launch { FoodLogStore.changes.collect { onLocalDataChanged() } }
            launch { NutritionGoalsStore.changes.collect { goal = NutritionGoalsStore.goal.value } }
            launch { MealPlanStore.changes.collect { reloadPlannedSlots() } }
            launch { HydrationStore.changes.collect { onWaterChanged() } }
        }
    }

    // MARK: - Day semantics

    val selectedDayKey: String get() = MealPlanDay.string(selectedDate)

    val isViewingToday: Boolean get() = MealPlanDay.dayStart(selectedDate) == MealPlanDay.dayStart(Date())

    /** Future days are viewable for planning but never loggable. */
    val isViewingFutureDay: Boolean get() = MealPlanDay.dayStart(selectedDate).after(MealPlanDay.dayStart(Date()))

    val canLogToSelectedDay: Boolean get() = !isViewingFutureDay

    val weekDates: List<Date> get() = MealPlanDay.weekDates(selectedDate)

    // MARK: - Totals + effective targets

    val totalCalories: Int get() = entries.sumOf { it.calories }
    val totalProtein: Double get() = entries.sumOf { it.protein }
    val totalFat: Double get() = entries.sumOf { it.fat }
    val totalCarbs: Double get() = entries.sumOf { it.carbs }

    val effectiveCalorieGoal: Int get() = dayTargets?.calories?.target?.let { Math.round(it).toInt() } ?: goal.calorieGoal
    val effectiveProteinTarget: Int get() = dayTargets?.protein?.target?.let { Math.round(it).toInt() } ?: goal.proteinTarget
    val effectiveFatTarget: Int get() = dayTargets?.fat?.target?.let { Math.round(it).toInt() } ?: goal.fatTarget
    val effectiveCarbsTarget: Int get() = dayTargets?.carbs?.target?.let { Math.round(it).toInt() } ?: goal.carbsTarget

    val calorieLine: MacroProgress get() = line({ it.calories }, effectiveCalorieGoal, totalCalories.toDouble())
    val proteinLine: MacroProgress get() = line({ it.protein }, effectiveProteinTarget, totalProtein)
    val fatLine: MacroProgress get() = line({ it.fat }, effectiveFatTarget, totalFat)
    val carbsLine: MacroProgress get() = line({ it.carbs }, effectiveCarbsTarget, totalCarbs)

    private fun line(select: (NutritionDailyTargets) -> MacroSummaryLine, fallbackGoal: Int, fallbackConsumed: Double): MacroProgress {
        val goal = if (fallbackGoal > 0) fallbackGoal.toDouble() else null
        val summary = daySummary ?: return MacroProgress(goal = goal, consumed = fallbackConsumed)
        val l = select(summary.targets)
        return MacroProgress(goal = l.target ?: goal, consumed = l.consumed)
    }

    /** One micro's line, or null when the summary hasn't provided it (renders "—", never 0). */
    fun microLine(micro: Micronutrient): MicroSummaryLine? = dayMicros?.line(micro)

    val displayedCalories: Int get() = Math.round(calorieLine.consumed).toInt()
    val calorieProgress: Double get() = calorieLine.rawFraction.coerceAtMost(1.0)
    /** Signed: negative when over target. */
    val remainingCalories: Int get() = Math.round(calorieLine.remaining ?: 0.0).toInt()

    // MARK: - Water (display only; logging lives in HydrationCardView)

    val waterTotalDisplay: Double
        get() {
            daySummary?.water?.let { return it.total }
            val unit = hydrationGoal.displayUnit
            return waterEntries.sumOf { it.unit.convert(it.amount, unit) }
        }

    // MARK: - Week rollups

    fun rollup(date: Date): NutritionDayRollup? {
        val key = MealPlanDay.string(date)
        return weekRollups.firstOrNull { it.date == key }
    }

    fun dotState(date: Date): NutritionDayDotState {
        val key = MealPlanDay.string(date)
        val hasData: Boolean? = rollup(date)?.hasData ?: if (key in locallyLoggedDays) true else null
        return NutritionDayDotState.state(date, hasData)
    }

    // MARK: - Meals

    fun entriesForMeal(mealType: MealType): List<FoodEntry> = entries.filter { it.mealType == mealType }
    fun caloriesForMeal(mealType: MealType): Int = entriesForMeal(mealType).sumOf { it.calories }
    fun plannedSlotsForMeal(mealType: MealType): List<MealSlot> = plannedSlots.filter { it.mealType == mealType }

    /** Quick-log source for an EMPTY meal on a loggable day, else null (chip hidden). */
    fun quickLogSource(mealType: MealType): QuickLogMealSource? {
        if (!canLogToSelectedDay) return null
        if (entriesForMeal(mealType).isNotEmpty()) return null
        return quickLogSources.firstOrNull { MealType.fromWire(it.mealType) == mealType && it.hasSource }
    }

    /** One-tap copy of the previewed source meal onto the selected day. */
    fun performQuickLog(mealType: MealType) {
        val source = quickLogSource(mealType) ?: return
        if (mealType in quickLogInFlight) return
        quickLogInFlight = quickLogInFlight + mealType
        quickLogError = null
        val requestedKey = selectedDayKey
        viewModelScope.launch {
            try {
                val response = ApiClient.nutritionApi.quickLogCopy(
                    QuickLogCopyRequest(
                        mealType = mealType.wireValue,
                        targetDate = requestedKey,
                        sourceDate = source.sourceDate,
                        tz = TimeZone.getDefault().id
                    )
                )
                FoodLogStore.adopt(response.entries)
                if (selectedDayKey == requestedKey) {
                    daySummary = null
                    NutritionSummaryStore.invalidate(requestedKey)
                    loadData()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError.Offline) {
                quickLogError = "You're offline — quick log needs a connection."
            } catch (e: Exception) {
                Log.w(tag, "quick log failed", e)
                quickLogError = "Couldn't copy that meal. Try again."
            } finally {
                quickLogInFlight = quickLogInFlight - mealType
            }
        }
    }

    // MARK: - Navigation between days

    fun select(date: Date) {
        dayTargets = null
        dayMicros = null
        daySummary = null
        quickLogSources = emptyList()
        quickLogError = null
        selectedDate = MealPlanDay.dayStart(date)
        loadData()
    }

    fun goToPreviousDay() = select(MealPlanDay.addDays(selectedDate, -1))
    fun goToNextDay() = select(MealPlanDay.addDays(selectedDate, 1))
    fun goToToday() = select(Date())

    // MARK: - Loading

    /** Local rows first (instant), then the server. */
    fun loadData() {
        daySummary = null
        reloadLocal()
        viewModelScope.launch { refreshFromServer() }
        viewModelScope.launch { refreshQuickLogSources() }
    }

    private fun reloadLocal() {
        val dayKey = selectedDayKey
        entries = localEntries(dayKey)
        waterEntries = runCatching { DatabaseManager.waterEntries(selectedDate) }.getOrDefault(emptyList())
        goal = NutritionGoalsStore.goal.value.takeIf { it.calorieGoal > 0 } ?: DatabaseManager.nutritionGoal()
        hydrationGoal = runCatching { DatabaseManager.hydrationGoal() }.getOrDefault(HydrationGoal())
        plannedSlots = runCatching { MealPlanStore.slots(dayKey) }.getOrDefault(emptyList())
        // Any cached summary renders immediately; the refresh trues it up.
        NutritionSummaryStore.cached(dayKey)?.let { adoptSummary(it, dayKey) }
        val week = weekDates
        NutritionSummaryStore.cachedRange(MealPlanDay.string(week.first()), MealPlanDay.string(week.last()))?.let { weekRollups = it.days }
        locallyLoggedDays = runCatching { DatabaseManager.loggedDayKeys(week.first(), week.last()) }.getOrDefault(emptySet())
    }

    private fun localEntries(dayKey: String): List<FoodEntry> {
        val fromStore = runCatching { FoodLogStore.entries(dayKey) }.getOrNull()
        val rows = fromStore ?: DatabaseManager.foodEntries(dayKey)
        return rows.filter { !it.isCustomTemplate }.sortedBy { it.loggedAt }
    }

    private fun onLocalDataChanged() {
        daySummary = null
        entries = localEntries(selectedDayKey)
        locallyLoggedDays = runCatching { DatabaseManager.loggedDayKeys(weekDates.first(), weekDates.last()) }.getOrDefault(emptySet())
    }

    private fun onWaterChanged() {
        daySummary = null
        waterEntries = runCatching { DatabaseManager.waterEntries(selectedDate) }.getOrDefault(emptyList())
        hydrationGoal = runCatching { DatabaseManager.hydrationGoal() }.getOrDefault(HydrationGoal())
    }

    private fun reloadPlannedSlots() {
        plannedSlots = runCatching { MealPlanStore.slots(selectedDayKey) }.getOrDefault(emptyList())
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        isRefreshing = true
        val requestedKey = selectedDayKey
        val requestedDate = selectedDate
        try {
            // Food-entry pulls (and the pending-write drain) belong to the food log store.
            try { FoodLogStore.refresh(requestedKey) } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(tag, "food log refresh failed", e) }
            if (selectedDayKey == requestedKey) entries = localEntries(requestedKey)

            val summary = NutritionSummaryStore.daily(requestedKey)
            if (summary != null && selectedDayKey == requestedKey) adoptSummary(summary, requestedKey)

            val week = MealPlanDay.weekDates(requestedDate)
            val range = NutritionSummaryStore.range(MealPlanDay.string(week.first()), MealPlanDay.string(week.last()))
            if (range != null && MealPlanDay.weekStart(selectedDate) == MealPlanDay.weekStart(requestedDate)) weekRollups = range.days

            try {
                MealPlanStore.refreshWeek(MealPlanDay.string(MealPlanDay.weekStart(requestedDate)))
                if (selectedDayKey == requestedKey) reloadPlannedSlots()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(tag, "planner refresh failed", e) }
        } finally {
            isRefreshing = false
        }
    }

    private fun adoptSummary(summary: NutritionDailySummary, dayKey: String) {
        if (dayKey != selectedDayKey) return
        dayTargets = summary.targets.takeIf { it.hasTargets }
        dayMicros = summary.micros ?: dayMicros
        daySummary = summary
        // The summary's meal groups carry the authoritative entries for the day.
        val remote = summary.meals?.flatMap { it.entries }
        if (!remote.isNullOrEmpty() && entries.isEmpty()) entries = remote.filter { !it.isCustomTemplate }.sortedBy { it.loggedAt }
    }

    private suspend fun refreshQuickLogSources() {
        if (!NetworkMonitor.isConnected.value || !canLogToSelectedDay) {
            quickLogSources = emptyList()
            return
        }
        val requestedKey = selectedDayKey
        val response = try {
            ApiClient.nutritionApi.getQuickLogSources(date = requestedKey, tz = TimeZone.getDefault().id)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { return }
        if (selectedDayKey == requestedKey) quickLogSources = response.sources
    }

    companion object {
        /** Default meal for the dashboard shortcuts by time of day (iOS `inferredMealType`). */
        fun inferredMealType(now: Date = Date()): MealType {
            val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
            return when {
                hour < 11 -> MealType.BREAKFAST
                hour < 15 -> MealType.LUNCH
                hour < 17 -> MealType.SNACK
                else -> MealType.DINNER
            }
        }

        /** "Same as yesterday" / "Copy from Jul 12" (iOS `QuickLogMealSource.copyLabel`). */
        fun quickLogLabel(sourceDate: String?, targetDayKey: String): String {
            val source = sourceDate?.let { MealPlanDay.date(it) } ?: return "Copy previous meal"
            val target = MealPlanDay.date(targetDayKey) ?: return "Copy previous meal"
            val days = Math.round((target.time - source.time).toDouble() / 86_400_000.0)
            return if (days == 1L) "Same as yesterday"
            else "Copy from " + java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(source)
        }
    }
}
