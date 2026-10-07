package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.RecentWaterAmount
import com.fitnesslink.fit.model.WaterIntakeEntry
import com.fitnesslink.fit.model.WaterUnit
import com.fitnesslink.fit.nutrition.HydrationStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Drives `LogWaterScreen` + `HydrationCardView` for ONE day (port of iOS
 * `HydrationViewModel`): the selected [day] may be a past date (backfill),
 * amounts display in the preferred unit, and every write goes through
 * [HydrationStore] (offline-first, water drain).
 */
class HydrationViewModel : ViewModel() {
    /** `yyyy-MM-dd` being viewed / logged to. */
    var day by mutableStateOf(MealPlanDay.string(Date()))
        private set
    var dayEntries by mutableStateOf<List<WaterIntakeEntry>>(emptyList())
        private set
    var goal by mutableStateOf(HydrationGoal())
        private set
    var recentAmounts by mutableStateOf<List<RecentWaterAmount>>(emptyList())
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    /** Bumps whenever the undo stack may have changed so the card re-reads [canUndo]. */
    var undoVersion by mutableStateOf(0)
        private set

    private var observing = false

    /** Legacy alias used by the pre-Wave-3 screens. */
    val todayEntries: List<WaterIntakeEntry> get() = dayEntries

    val isViewingToday: Boolean get() = day == MealPlanDay.string(Date())

    /** Future days are plan-only — no logging. */
    val canLog: Boolean get() = day <= MealPlanDay.string(Date())

    val displayUnit: WaterUnit get() = goal.displayUnit

    val dailyGoalDisplay: Double get() = goal.unit.convert(goal.dailyGoal, displayUnit)

    /** Day total in the display unit regardless of each entry's logged unit. */
    val totalIntake: Double get() = dayEntries.sumOf { it.unit.convert(it.amount, displayUnit) }

    val hydrationProgress: Double
        get() {
            if (dailyGoalDisplay <= 0) return 0.0
            return (totalIntake / dailyGoalDisplay).coerceAtMost(1.0)
        }

    val remainingIntake: Double get() = (dailyGoalDisplay - totalIntake).coerceAtLeast(0.0)

    val canUndo: Boolean get() { undoVersion; return HydrationStore.canUndo(day) }

    // MARK: - Loading

    fun loadData(date: String = MealPlanDay.string(Date())) {
        observe()
        day = MealPlanDay.normalize(date)
        hydrate()
        viewModelScope.launch {
            isRefreshing = true
            val requested = day
            try {
                HydrationStore.refresh()
                HydrationStore.refreshEntries(requested)
            } finally {
                isRefreshing = false
            }
            if (day == requested) hydrate()
        }
    }

    fun loadRecentAmounts() {
        viewModelScope.launch { recentAmounts = HydrationStore.recentAmounts() }
    }

    private fun hydrate() {
        goal = HydrationStore.currentGoal()
        dayEntries = HydrationStore.entries(day)
        undoVersion++
    }

    private fun observe() {
        if (observing) return
        observing = true
        viewModelScope.launch { HydrationStore.changes.collectLatest { hydrate() } }
        viewModelScope.launch { HydrationStore.goal.collectLatest { g -> if (g != null) goal = g } }
    }

    // MARK: - Writes

    fun log(amount: Double, unit: WaterUnit, notes: String = "") {
        if (amount <= 0 || !canLog) return
        viewModelScope.launch {
            HydrationStore.log(amount, unit, day, notes)
            hydrate()
            if (notes.isNotEmpty() || amount !in presetAmounts(unit)) recentAmounts = HydrationStore.recentAmounts()
        }
    }

    /** Pre-Wave-3 API kept for callers that build the entry themselves. */
    fun addEntry(entry: WaterIntakeEntry) = log(entry.amount, entry.unit, entry.notes)

    /** +8 / +16 / +24 oz converted into the goal unit; undoable. */
    fun logPreset(ounces: Double) {
        if (!canLog) return
        viewModelScope.launch { HydrationStore.logPreset(ounces, day); hydrate() }
    }

    fun undoLastTap() {
        viewModelScope.launch { HydrationStore.undoLast(); hydrate() }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch { HydrationStore.delete(id, day); hydrate() }
    }

    fun updateGoal(newGoal: HydrationGoal) {
        goal = newGoal
        viewModelScope.launch { HydrationStore.updateGoal(newGoal) }
    }

    fun setPreferredUnit(unit: WaterUnit?) {
        viewModelScope.launch { HydrationStore.setPreferredUnit(unit); hydrate() }
    }

    /** The quick-add presets for [unit] (iOS `LogWaterView.quickAmounts`). */
    fun presetAmounts(unit: WaterUnit): List<Double> = if (unit == WaterUnit.ML) listOf(250.0, 500.0, 750.0) else listOf(8.0, 16.0, 24.0)
}
