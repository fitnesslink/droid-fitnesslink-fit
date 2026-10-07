package com.fitnesslink.fit.nutrition

import android.util.Log
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.NutritionFavorite
import com.fitnesslink.fit.network.dto.NutritionFavoriteKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * FTNSSLNK-215 — app-wide cache of the user's favorite foods and meal
 * templates (iOS `NutritionFavoritesStore`). Every star toggle (Food Detail,
 * search rows, the Favorites section in Recents) goes through here so all
 * surfaces agree instantly: toggles flip the local set OPTIMISTICALLY, then
 * fire the idempotent POST/DELETE and revert on failure.
 */
object NutritionFavoritesStore {
    private const val TAG = "NutritionFavorites"

    private val _favorites = MutableStateFlow<List<NutritionFavorite>>(emptyList())
    /** Full favorite rows, newest first (the server's order). */
    val favorites: StateFlow<List<NutritionFavorite>> = _favorites.asStateFlow()

    private val _foodIds = MutableStateFlow<Set<String>>(emptySet())
    /** Lowercased catalog-food ids for O(1) star lookups. */
    val foodIds: StateFlow<Set<String>> = _foodIds.asStateFlow()

    private val _mealTemplateIds = MutableStateFlow<Set<String>>(emptySet())
    val mealTemplateIds: StateFlow<Set<String>> = _mealTemplateIds.asStateFlow()

    @Volatile var hasLoaded: Boolean = false
        private set

    private val refreshMutex = Mutex()

    /** Food favorites that can be shown and logged: available, with the food summary present. */
    val availableFoodFavorites: List<NutritionFavorite>
        get() = _favorites.value.filter { it.kind.equals(NutritionFavoriteKind.FOOD, true) && it.isAvailable && it.food != null }

    fun isFavorite(foodId: String): Boolean = foodId.lowercase(Locale.US) in _foodIds.value

    fun isFavoriteMeal(templateId: String): Boolean = templateId.lowercase(Locale.US) in _mealTemplateIds.value

    /** Fetch the full favorites list; concurrent calls collapse. Failures keep whatever we had. */
    suspend fun refresh() {
        if (!NetworkMonitor.isConnected.value) return
        if (refreshMutex.isLocked) return
        refreshMutex.withLock {
            val remote = try {
                ApiClient.nutritionApi.getNutritionFavorites()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.i(TAG, "refresh failed: ${e.message}")
                return
            }
            _favorites.value = remote
            _foodIds.value = remote.mapNotNull { it.foodId?.lowercase(Locale.US) }.toSet()
            _mealTemplateIds.value = remote.mapNotNull { it.mealTemplateId?.lowercase(Locale.US) }.toSet()
            hasLoaded = true
        }
    }

    suspend fun refreshIfNeeded() { if (!hasLoaded) refresh() }

    /**
     * Star/unstar a catalog food. Optimistic: the set flips immediately; the
     * server call follows and a failure reverts. Returns the NEW favorite state.
     */
    suspend fun toggleFood(foodId: String): Boolean {
        val key = foodId.lowercase(Locale.US)
        val wasFavorite = key in _foodIds.value
        setFoodFavorite(key, !wasFavorite)
        try {
            if (wasFavorite) ApiClient.nutritionApi.unfavoriteFood(foodId) else ApiClient.nutritionApi.favoriteFood(foodId)
            refresh()
            return !wasFavorite
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "toggleFood $foodId failed: ${e.message}")
            setFoodFavorite(key, wasFavorite)
            return wasFavorite
        }
    }

    /** Star/unstar a meal-plan template — same optimistic pattern. */
    suspend fun toggleMeal(templateId: String): Boolean {
        val key = templateId.lowercase(Locale.US)
        val wasFavorite = key in _mealTemplateIds.value
        setMealFavorite(key, !wasFavorite)
        try {
            if (wasFavorite) ApiClient.nutritionApi.unfavoriteMeal(templateId) else ApiClient.nutritionApi.favoriteMeal(templateId)
            refresh()
            return !wasFavorite
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setMealFavorite(key, wasFavorite)
            return wasFavorite
        }
    }

    /** Drop local knowledge (sign-out). */
    fun reset() {
        _favorites.value = emptyList()
        _foodIds.value = emptySet()
        _mealTemplateIds.value = emptySet()
        hasLoaded = false
    }

    private fun setFoodFavorite(key: String, favorite: Boolean) {
        _foodIds.update { if (favorite) it + key else it - key }
        if (!favorite) _favorites.update { list -> list.filter { it.foodId?.lowercase(Locale.US) != key } }
    }

    private fun setMealFavorite(key: String, favorite: Boolean) {
        _mealTemplateIds.update { if (favorite) it + key else it - key }
        if (!favorite) _favorites.update { list -> list.filter { it.mealTemplateId?.lowercase(Locale.US) != key } }
    }
}
