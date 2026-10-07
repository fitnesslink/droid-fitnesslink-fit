package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.CopyMealSlotDayMode
import com.fitnesslink.fit.model.DailyMealSummary
import com.fitnesslink.fit.model.DayOfWeek
import com.fitnesslink.fit.model.GroceryItem
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealSlot
import com.fitnesslink.fit.model.MealSlotDayRollup
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.model.NutritionGoal
import com.fitnesslink.fit.model.PlannedDayTargetState
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.ApplyMealPlanWeekResult
import com.fitnesslink.fit.network.dto.CopyMealSlotDayResult
import com.fitnesslink.fit.network.dto.CopyMealSlotWeekResult
import com.fitnesslink.fit.nutrition.GroceryStore
import com.fitnesslink.fit.nutrition.MealPlanStore
import com.fitnesslink.fit.nutrition.NutritionGoalsStore
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drives the multi-week planner (port of iOS `MealPlanViewModel`'s view
 * half). Slots are DATE-anchored and loaded one Sunday-start week at a time
 * from [MealPlanStore]; navigating weeks swaps [weekSlots]. All writes go
 * through the store (offline-first, queued).
 *
 * `loadData()`, `weekSlots`, `weeklyAverageCalories`, `uncheckedGroceryCount`
 * and `goal` are kept for `NutritionHomeScreen`'s quick stats.
 */
class MealPlanViewModel : ViewModel() {
    /** Slots for the viewed week only, itemized foods attached. */
    var weekSlots by mutableStateOf<List<MealSlot>>(emptyList())
        private set
    /** Server per-day rollups for the viewed week (authoritative planned totals). */
    var dayRollups by mutableStateOf<List<MealSlotDayRollup>>(emptyList())
        private set
    /** Sunday (`yyyy-MM-dd`) that starts the viewed week. */
    var weekStart by mutableStateOf(MealPlanDay.string(MealPlanDay.weekStart(Date())))
        private set
    /** The day within the week whose meals are shown (`yyyy-MM-dd`). */
    var selectedDate by mutableStateOf(MealPlanDay.string(Date()))
    var groceryItems by mutableStateOf<List<GroceryItem>>(emptyList())
        private set
    var goal by mutableStateOf(NutritionGoal())
        private set
    var previousWeekHasSlots by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    // Result banners (cleared on week navigation).
    var lastCopyResult by mutableStateOf<CopyMealSlotWeekResult?>(null)
    var lastCopyDayResult by mutableStateOf<CopyMealSlotDayResult?>(null)
    var lastIdeasResult by mutableStateOf<ApplyMealPlanWeekResult?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    private var observing = false

    // MARK: - Week context

    val weekDates: List<String> get() = MealPlanDay.weekDates(MealPlanDay.parse(weekStart)).map { MealPlanDay.string(it) }

    val isViewingCurrentWeek: Boolean get() = weekStart == MealPlanDay.string(MealPlanDay.weekStart(Date()))

    val isWeekEmpty: Boolean get() = weekSlots.isEmpty()

    /** "Jul 6 – Jul 12" for the viewed week. */
    val weekRangeLabel: String get() = weekRangeLabel(weekStart)

    // MARK: - Per-day computed

    fun slotsForDate(date: String): List<MealSlot> = weekSlots.filter { it.date == date }

    fun slotsFor(date: String, meal: MealType): List<MealSlot> = weekSlots.filter { it.date == date && it.mealType == meal }

    fun slot(id: String): MealSlot? = weekSlots.firstOrNull { it.id.equals(id, true) }

    fun caloriesForDate(date: String): Int = slotsForDate(date).sumOf { it.calories }
    fun proteinForDate(date: String): Double = slotsForDate(date).sumOf { it.protein }
    fun fatForDate(date: String): Double = slotsForDate(date).sumOf { it.fat }
    fun carbsForDate(date: String): Double = slotsForDate(date).sumOf { it.carbs }

    /** Current calorie goal (profile engine first, legacy row otherwise); null when unset. */
    val effectiveCalorieTarget: Int? get() = goal.calorieGoal.takeIf { it > 0 }

    fun targetState(date: String): PlannedDayTargetState = PlannedDayTargetState.from(caloriesForDate(date), effectiveCalorieTarget)

    fun isDayEmpty(date: String): Boolean = slotsForDate(date).isEmpty()

    val dailySummaries: List<DailyMealSummary>
        get() = weekDates.map { date ->
            DailyMealSummary(
                day = DayOfWeek.of(MealPlanDay.parse(date)), date = date,
                totalCalories = caloriesForDate(date), totalProtein = proteinForDate(date),
                totalFat = fatForDate(date), totalCarbs = carbsForDate(date), goal = goal.calorieGoal
            )
        }

    val weeklyAverageCalories: Int
        get() {
            val dates = weekDates
            return dates.sumOf { caloriesForDate(it) } / dates.size.coerceAtLeast(1)
        }

    val uncheckedGroceryCount: Int get() = groceryItems.count { !it.isChecked }

    // MARK: - Loading

    /** Cache read + background server refresh of the viewed week. Safe to call on every appearance. */
    fun loadData() {
        observeStore()
        hydrateFromCache()
        viewModelScope.launch { refreshFromServer() }
    }

    /** Jump the planner to the week holding [date] and select it (dated summary / deep links). */
    fun showDate(date: String) {
        val day = MealPlanDay.normalize(date)
        val start = MealPlanDay.string(MealPlanDay.weekStart(MealPlanDay.parse(day)))
        if (start == weekStart) { selectedDate = day; loadData() } else setWeek(start, day)
    }

    /** Sync cache read — no network. */
    fun hydrateFromCache() {
        MealPlanStore.pendingIdeasResult?.let { lastIdeasResult = it; MealPlanStore.pendingIdeasResult = null }
        weekSlots = MealPlanStore.week(weekStart)
        dayRollups = MealPlanStore.rollups(weekStart)
        previousWeekHasSlots = MealPlanStore.previousWeekHasSlots(weekStart)
        goal = currentGoal()
        groceryItems = GroceryStore.cachedLists().firstOrNull { it.isDefault }?.let { GroceryStore.items(it.id) }
            ?: DatabaseManager.groceryItems()
    }

    private fun currentGoal(): NutritionGoal {
        val local = DatabaseManager.nutritionGoal()
        val engine = runCatching { NutritionGoalsStore.calorieGoal() }.getOrDefault(0)
        return if (engine > 0) local.copy(calorieGoal = engine) else local
    }

    private fun observeStore() {
        if (observing) return
        observing = true
        viewModelScope.launch { MealPlanStore.changes.collectLatest { hydrateFromCache() } }
        viewModelScope.launch { GroceryStore.changes.collectLatest { hydrateFromCache() } }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        isRefreshing = true
        val loading = weekStart
        try {
            MealPlanStore.refreshWeek(loading)
            runCatching { GroceryStore.refreshDefaultList() }
        } finally {
            isRefreshing = false
        }
        if (weekStart == loading) hydrateFromCache()
    }

    // MARK: - Week navigation

    fun goToPreviousWeek() = shiftWeek(-7)
    fun goToNextWeek() = shiftWeek(7)

    fun goToToday() {
        setWeek(MealPlanDay.string(MealPlanDay.weekStart(Date())), MealPlanDay.string(Date()))
    }

    private fun shiftWeek(days: Int) {
        val newStart = MealPlanDay.addDays(MealPlanDay.parse(weekStart), days)
        val offset = weekDates.indexOf(selectedDate).coerceAtLeast(0)
        setWeek(MealPlanDay.string(newStart), MealPlanDay.string(MealPlanDay.addDays(newStart, offset)))
    }

    private fun setWeek(start: String, selected: String) {
        weekStart = start
        selectedDate = selected
        lastCopyResult = null
        lastCopyDayResult = null
        lastIdeasResult = null
        errorMessage = null
        loadData()
    }

    // MARK: - Actions

    /** Commit a meal built in the builder (new → create, existing → foods replace). */
    fun commitBuiltMeal(slot: MealSlot) {
        viewModelScope.launch {
            MealPlanStore.upsert(slot)
            hydrateFromCache()
        }
    }

    fun removeSlot(id: String) {
        viewModelScope.launch {
            MealPlanStore.delete(id)
            hydrateFromCache()
        }
    }

    /** Copy last week into the viewed week; `overwrite` replaces clashing meals, else keeps yours. */
    fun copyPreviousWeek(overwrite: Boolean) {
        viewModelScope.launch {
            try {
                lastCopyResult = MealPlanStore.copyWeek(weekStart, overwrite)
            } catch (e: Exception) {
                errorMessage = describe(e)
            }
            hydrateFromCache()
        }
    }

    /** Copy [sourceDay] onto [targetDays] (online only). Returns null on failure (message in [errorMessage]). */
    suspend fun copyDay(sourceDay: String, targetDays: List<String>, mode: CopyMealSlotDayMode): CopyMealSlotDayResult? = try {
        val result = MealPlanStore.copyDay(sourceDay, targetDays, mode)
        lastCopyDayResult = result
        hydrateFromCache()
        result
    } catch (e: Exception) {
        errorMessage = describe(e)
        null
    }

    fun toggleGroceryItem(id: String) {
        val item = groceryItems.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            runCatching { GroceryStore.toggleItem(item) }
            hydrateFromCache()
        }
    }

    // MARK: - Labels

    companion object {
        private val mediumDay = SimpleDateFormat("MMM d", Locale.getDefault())
        private val weekdayShort = SimpleDateFormat("EEE", Locale.getDefault())
        private val weekdayFull = SimpleDateFormat("EEEE", Locale.getDefault())
        private val dayNumber = SimpleDateFormat("d", Locale.getDefault())

        fun mediumDay(date: String): String = mediumDay.format(MealPlanDay.parse(date))
        fun weekdayShort(date: String): String = weekdayShort.format(MealPlanDay.parse(date))
        fun weekdayFull(date: String): String = weekdayFull.format(MealPlanDay.parse(date))
        fun dayNumber(date: String): String = dayNumber.format(MealPlanDay.parse(date))

        fun weekRangeLabel(anyDate: String): String {
            val start = MealPlanDay.weekStart(MealPlanDay.parse(anyDate))
            return "${mediumDay.format(start)} – ${mediumDay.format(MealPlanDay.addDays(start, 6))}"
        }

        fun isToday(date: String): Boolean = date == MealPlanDay.string(Date())

        fun describe(e: Throwable): String = when (e) {
            is ApiError.Offline -> "You're offline — this needs a connection."
            is ApiError.ServerError -> if (e.isBadRequest) (e.errorDetail ?: e.envelope?.displayMessage ?: "The server rejected that change.") else "Server error (${e.statusCode}). Try again later."
            is ApiError.NetworkError -> "Network error. Check your connection and try again."
            is ApiError -> e.message ?: "Something went wrong."
            else -> "Something went wrong. Please try again."
        }
    }
}

/** Shared by the planner VMs: run a block off the main thread. */
internal suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
