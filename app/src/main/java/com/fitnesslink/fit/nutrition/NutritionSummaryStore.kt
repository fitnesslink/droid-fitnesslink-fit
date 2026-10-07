package com.fitnesslink.fit.nutrition

import android.util.Log
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.NutritionDailySummary
import com.fitnesslink.fit.network.dto.NutritionSummaryRange
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.cacheDailySummary
import com.fitnesslink.fit.persistence.cacheSummaryRange
import com.fitnesslink.fit.persistence.cachedDailySummary
import com.fitnesslink.fit.persistence.cachedSummaryRange
import com.fitnesslink.fit.persistence.invalidateAllSummaries
import com.fitnesslink.fit.persistence.invalidateDailySummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-memory + SQLite cache of `GET /summary` (daily and range shapes), the
 * ONE round trip the dashboard renders from (FTNSSLNK-193/194). Daily
 * summaries are keyed by `yyyy-MM-dd`; ranges by `from:to`.
 *
 * Invalidation: any food-log mutation (`FoodLogStore.changes`, agent B)
 * drops every cached summary — the local SQLite rows are fresher than a
 * summary fetched before the write, so the dashboard renders local sums
 * until the next fetch trues them up. Water writes (`HydrationCardView`,
 * agent C) should call [invalidate] for the day they touched.
 */
object NutritionSummaryStore {
    private const val TAG = "NutritionSummaryStore"

    private val daily = ConcurrentHashMap<String, NutritionDailySummary>()
    private val ranges = ConcurrentHashMap<String, NutritionSummaryRange>()
    private val registered = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fires whenever a cached summary is replaced or invalidated. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val tz: String get() = TimeZone.getDefault().id

    /** Subscribe to the food log so a logged / edited / deleted entry drops stale summaries. */
    fun ensureRegistered() {
        if (!registered.compareAndSet(false, true)) return
        scope.launch {
            try {
                FoodLogStore.changes.collect { invalidateAll() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "FoodLogStore.changes collection ended", e)
            }
        }
        // Water writes (HydrationCardView / LogWater, agent C) change the day's water line too.
        scope.launch {
            try {
                HydrationStore.changes.collect { invalidateAll() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "HydrationStore.changes collection ended", e)
            }
        }
    }

    /** The last known summary for [date] without hitting the network (memory → SQLite). */
    fun cached(date: String): NutritionDailySummary? =
        daily[date] ?: runCatching { DatabaseManager.cachedDailySummary(date) }.getOrNull()?.also { daily[date] = it }

    /**
     * Fresh summary for [date] when online (and cached), the cached one
     * otherwise. Null when nothing is known (offline on first visit).
     */
    suspend fun daily(date: String): NutritionDailySummary? {
        ensureRegistered()
        if (!NetworkMonitor.isConnected.value) return cached(date)
        return try {
            val summary = ApiClient.nutritionApi.getDailySummary(date = date, tz = tz)
            val keyed = if (summary.date.isEmpty()) summary.copy(date = date) else summary
            daily[date] = keyed
            runCatching { DatabaseManager.cacheDailySummary(keyed) }
            _changes.tryEmit(Unit)
            keyed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "daily($date) failed", e)
            cached(date)
        }
    }

    /** The last known range rollups without hitting the network. */
    fun cachedRange(from: String, to: String): NutritionSummaryRange? =
        ranges["$from:$to"] ?: runCatching { DatabaseManager.cachedSummaryRange(from, to) }.getOrNull()?.also { ranges["$from:$to"] = it }

    /** One rollup per local day in the inclusive range (max 92 days); cached fallback offline. */
    suspend fun range(from: String, to: String): NutritionSummaryRange? {
        ensureRegistered()
        if (!NetworkMonitor.isConnected.value) return cachedRange(from, to)
        return try {
            val range = ApiClient.nutritionApi.getSummaryRange(from = from, to = to, tz = tz)
            val keyed = if (range.from.isEmpty()) range.copy(from = from, to = to) else range
            ranges["$from:$to"] = keyed
            runCatching { DatabaseManager.cacheSummaryRange(keyed) }
            keyed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "range($from,$to) failed", e)
            cachedRange(from, to)
        }
    }

    /** Drop the cached summary for one day (and every range that could contain it). */
    fun invalidate(date: String) {
        daily.remove(date)
        ranges.keys.removeAll { key ->
            val parts = key.split(":")
            parts.size == 2 && parts[0] <= date && date <= parts[1]
        }
        runCatching {
            DatabaseManager.invalidateDailySummary(date)
        }
        _changes.tryEmit(Unit)
    }

    /** Drop everything (any food-log mutation, sign-out). */
    fun invalidateAll() {
        daily.clear()
        ranges.clear()
        runCatching { DatabaseManager.invalidateAllSummaries() }
        _changes.tryEmit(Unit)
    }
}
