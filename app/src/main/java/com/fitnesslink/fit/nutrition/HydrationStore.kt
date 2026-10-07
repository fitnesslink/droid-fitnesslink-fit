package com.fitnesslink.fit.nutrition

import android.util.Log
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.HydrationPresets
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.RecentWaterAmount
import com.fitnesslink.fit.model.WaterIntakeEntry
import com.fitnesslink.fit.model.WaterTapUndoStack
import com.fitnesslink.fit.model.WaterUnit
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.PreferredWaterUnitRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.sync.SyncManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Hydration — port of iOS `HydrationViewModel` + `HydrationService` + the
 * water half of `NutritionViewModel` (preset taps, undo, FTNSSLNK-195/221).
 *
 * Water writes are offline-first through the EXISTING `SyncManager`
 * water drain (`create` = full entry JSON, `delete` = empty payload); this
 * store only produces queue rows in the same codec. The goal row (daily
 * goal + preferred display unit) is cached locally and refreshed from
 * `GET /hydration-goals/me`.
 */
object HydrationStore {
    private const val TAG = "HydrationStore"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Same codec as `SyncManager.drainWaterEntries` decodes with. */
    private val queueGson: Gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").create()

    private val _goal = MutableStateFlow<HydrationGoal?>(null)
    /** The user's hydration goal (null until first load). */
    val goal: StateFlow<HydrationGoal?> = _goal.asStateFlow()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** Fires after any local water change (log / delete / refresh / goal). */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val undo = WaterTapUndoStack()

    /** The unit every water surface displays in: preferred unit, else the goal's own unit. */
    val displayUnit: WaterUnit get() = currentGoal().displayUnit

    /** Loaded goal, falling back to the cached row so callers never see null. */
    fun currentGoal(): HydrationGoal = _goal.value ?: DatabaseManager.hydrationGoal().also { _goal.value = it }

    /** Daily goal expressed in [displayUnit]. */
    val dailyGoalDisplay: Double get() = currentGoal().let { it.unit.convert(it.dailyGoal, displayUnit) }

    // MARK: - Reads

    /** Cached entries logged on [date] (`yyyy-MM-dd`), newest first. */
    fun entries(date: String): List<WaterIntakeEntry> = DatabaseManager.waterEntries(MealPlanDay.parse(date))

    /** Day total in [unit] regardless of each entry's logged unit. */
    fun total(date: String, unit: WaterUnit = displayUnit): Double = entries(date).sumOf { it.unit.convert(it.amount, unit) }

    fun progress(date: String): Double {
        val goal = dailyGoalDisplay
        if (goal <= 0) return 0.0
        return (total(date) / goal).coerceAtMost(1.0)
    }

    fun canUndo(date: String): Boolean = undo.canUndo(MealPlanDay.normalize(date))

    // MARK: - Refresh

    /** Goal from cache, then server. */
    suspend fun refresh() {
        _goal.value = withContext(Dispatchers.IO) { DatabaseManager.hydrationGoal() }
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remote = ApiClient.nutritionApi.getHydrationGoal()
            withContext(Dispatchers.IO) { DatabaseManager.saveHydrationGoal(remote) }
            _goal.value = remote
            notifyChanged()
        } catch (e: Exception) {
            Log.i(TAG, "goal refresh failed — using cache (${e.javaClass.simpleName})")
        }
    }

    /** `GET /water-entries/me?date=` → upsert the day's rows. */
    suspend fun refreshEntries(date: String) {
        if (!NetworkMonitor.isConnected.value) return
        val day = MealPlanDay.normalize(date)
        try {
            val remote = ApiClient.nutritionApi.getWaterEntries(day)
            withContext(Dispatchers.IO) { remote.forEach { DatabaseManager.saveWaterEntry(it) } }
            notifyChanged()
        } catch (e: Exception) {
            Log.i(TAG, "entries refresh $day failed — using cache (${e.javaClass.simpleName})")
        }
    }

    // MARK: - Writes

    /**
     * Log water on [date] (backfill-aware: a past day is stamped local noon,
     * iOS `FoodEntry.timestamp(loggingTo:)`). SQLite + queue first, inline
     * flush after; the water drain retries failures.
     */
    suspend fun log(amount: Double, unit: WaterUnit, date: String, notes: String? = null): WaterIntakeEntry {
        val day = MealPlanDay.normalize(date)
        val entry = WaterIntakeEntry(
            amount = amount, unit = unit,
            loggedAt = FoodEntry.timestamp(loggingTo = MealPlanDay.parse(day)),
            notes = notes ?: ""
        )
        val queueId = withContext(Dispatchers.IO) {
            DatabaseManager.saveWaterEntry(entry)
            DatabaseManager.enqueueSyncEntry(SyncManager.WATER_ENTRIES_TYPE, entry.id, "create", queueGson.toJson(entry))
        }
        notifyChanged()
        invalidateSummary(day)
        flush(queueId) { ApiClient.nutritionApi.addWaterEntry(entry) }
        return entry
    }

    /** One preset tap (+8/+16/+24 oz) converted into the goal unit and recorded for undo. */
    suspend fun logPreset(ounces: Double, date: String): WaterIntakeEntry {
        val unit = displayUnit
        val entry = log(HydrationPresets.amount(ounces, unit), unit, date)
        undo.recordTap(entry.id, MealPlanDay.normalize(date))
        return entry
    }

    /** Remove the LAST preset tap's entry on the day the stack is keyed to. Returns false when nothing to undo. */
    suspend fun undoLast(): Boolean {
        val day = undo.dayKey
        val id = undo.popLastTap(day) ?: return false
        delete(id, day)
        return true
    }

    suspend fun delete(id: String, date: String? = null) {
        val queueId = withContext(Dispatchers.IO) {
            DatabaseManager.deleteWaterEntry(id)
            DatabaseManager.enqueueSyncEntry(SyncManager.WATER_ENTRIES_TYPE, id, "delete", "")
        }
        notifyChanged()
        date?.let { invalidateSummary(MealPlanDay.normalize(it)) }
        flush(queueId) { ApiClient.nutritionApi.deleteWaterEntry(id) }
    }

    /** Distinct recently-logged custom amounts (chips). Empty offline / on failure. */
    suspend fun recentAmounts(limit: Int = 5): List<RecentWaterAmount> {
        if (!NetworkMonitor.isConnected.value) return emptyList()
        return try {
            ApiClient.nutritionApi.getRecentWaterAmounts(limit).map { RecentWaterAmount(it.amount, it.unit, it.lastLoggedAt ?: Date()) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Set (or clear with null) the preferred display unit. A 404 means no
     * goal row exists yet — upsert the goal carrying the preference instead.
     */
    suspend fun setPreferredUnit(unit: WaterUnit?) {
        val updated = currentGoal().copy(preferredUnit = unit)
        withContext(Dispatchers.IO) { DatabaseManager.saveHydrationGoal(updated) }
        _goal.value = updated
        notifyChanged()
        if (!NetworkMonitor.isConnected.value) return
        try {
            ApiClient.nutritionApi.setPreferredWaterUnit(PreferredWaterUnitRequest(unit?.rawValue))
        } catch (e: ApiError.NotFound) {
            runCatching { ApiClient.nutritionApi.upsertHydrationGoal(updated) }
        } catch (e: Exception) {
            Log.w(TAG, "preferred-unit update failed (${e.javaClass.simpleName})")
        }
    }

    /** Android-only daily goal editor (iOS has no UI for the amount): local + `POST /hydration-goals`. */
    suspend fun updateGoal(goal: HydrationGoal) {
        withContext(Dispatchers.IO) { DatabaseManager.saveHydrationGoal(goal) }
        _goal.value = goal
        notifyChanged()
        if (!NetworkMonitor.isConnected.value) return
        runCatching { ApiClient.nutritionApi.upsertHydrationGoal(goal) }
            .onFailure { Log.w(TAG, "goal upsert failed (${it.javaClass.simpleName})") }
    }

    // MARK: - Helpers

    /** Fire-and-forget inline flush; success marks the queue row synced, failure leaves it for the water drain. */
    private fun flush(queueId: String, call: suspend () -> Unit) {
        if (!NetworkMonitor.isConnected.value) return
        scope.launch {
            try {
                call()
                DatabaseManager.markSyncEntrySynced(queueId)
            } catch (_: Exception) { /* stays queued — drain retries */ }
        }
    }

    private fun invalidateSummary(day: String) {
        runCatching { NutritionSummaryStore.invalidate(day) }
    }

    private fun notifyChanged() { _changes.tryEmit(Unit) }
}
