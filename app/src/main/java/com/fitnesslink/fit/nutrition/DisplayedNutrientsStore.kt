package com.fitnesslink.fit.nutrition

import android.util.Log
import com.fitnesslink.fit.model.api.Micronutrient
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.DisplayedNutrientsPreference
import com.fitnesslink.fit.network.dto.UpdateDisplayedNutrientsRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.nutritionCacheDelete
import com.fitnesslink.fit.persistence.nutritionCacheObject
import com.fitnesslink.fit.persistence.nutritionCachePutObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FTNSSLNK-217 — which micronutrients the user wants displayed (dashboard
 * micros card). The preference is SERVER-side (`/preferences/displayed-
 * nutrients`) so it follows the user across devices; this store mirrors it
 * into `nutrition_cache` so the dashboard renders the right set offline and
 * on cold launch. Toggles flip the local set OPTIMISTICALLY, then PUT the
 * full replacement and revert on failure.
 *
 * Keys are the contract's app-side identifiers (`fiber`, `sugars`, `sodium`,
 * `saturatedFat`, `cholesterol` — see [Micronutrient.key]).
 */
object DisplayedNutrientsStore {
    private const val TAG = "DisplayedNutrients"
    private const val KEY_CACHE = "displayedMicros"

    private val _displayed = MutableStateFlow(Micronutrient.defaultDisplayed.map { it.key }.toSet())
    /** Keys of the micros to display. */
    val displayed: StateFlow<Set<String>> = _displayed.asStateFlow()

    private val _isDefault = MutableStateFlow(true)
    /** True while the server default is in effect (user never saved). */
    val isDefault: StateFlow<Boolean> = _isDefault.asStateFlow()

    private val hydrated = AtomicBoolean(false)
    private val refreshing = Mutex()

    /** The displayed micros in canonical order. */
    val displayedMicros: List<Micronutrient> get() = Micronutrient.ordered(_displayed.value)

    fun isDisplayed(micro: Micronutrient): Boolean = micro.key in _displayed.value

    fun hydrateFromCache() {
        if (!hydrated.compareAndSet(false, true)) return
        runCatching {
            DatabaseManager.nutritionCacheObject<DisplayedNutrientsPreference>(KEY_CACHE)?.let { applyLocally(it) }
        }.onFailure { Log.w(TAG, "hydrateFromCache failed", it) }
    }

    /** Fetch the server's preference; failures keep whatever we had (cached or default). */
    suspend fun refresh() {
        hydrateFromCache()
        if (!NetworkMonitor.isConnected.value) return
        if (!refreshing.tryLock()) return
        try {
            val remote = ApiClient.nutritionApi.getDisplayedNutrients()
            apply(remote)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed", e)
        } finally {
            refreshing.unlock()
        }
    }

    /**
     * Replace the displayed set (FULL replacement; empty allowed). Optimistic:
     * the set flips immediately; a failed PUT reverts. Throws on failure so
     * the settings screen can surface an error line.
     */
    suspend fun set(keys: Set<String>) {
        hydrateFromCache()
        val previous = _displayed.value
        val previousDefault = _isDefault.value
        val known = Micronutrient.ordered(keys)
        _displayed.value = known.map { it.key }.toSet()
        _isDefault.value = false
        try {
            val saved = ApiClient.nutritionApi.updateDisplayedNutrients(UpdateDisplayedNutrientsRequest(known.map { it.wireName }))
            apply(saved)
        } catch (e: Exception) {
            _displayed.value = previous
            _isDefault.value = previousDefault
            throw e
        }
    }

    /** Toggle one micro. Returns false on failure (the set has been reverted). */
    suspend fun setDisplayed(micro: Micronutrient, shouldDisplay: Boolean): Boolean {
        val next = _displayed.value.toMutableSet()
        if (shouldDisplay) next.add(micro.key) else next.remove(micro.key)
        return try {
            set(next)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "setDisplayed failed", e)
            false
        }
    }

    /** Drop local knowledge (sign-out). */
    fun reset() {
        _displayed.value = Micronutrient.defaultDisplayed.map { it.key }.toSet()
        _isDefault.value = true
        hydrated.set(false)
        runCatching { DatabaseManager.nutritionCacheDelete(KEY_CACHE) }
    }

    private fun apply(preference: DisplayedNutrientsPreference) {
        applyLocally(preference)
        runCatching { DatabaseManager.nutritionCachePutObject(KEY_CACHE, preference) }
    }

    private fun applyLocally(preference: DisplayedNutrientsPreference) {
        _displayed.value = preference.displayedMicros.mapNotNull { Micronutrient.fromWire(it)?.key }.toSet()
        _isDefault.value = preference.isDefault
    }
}
