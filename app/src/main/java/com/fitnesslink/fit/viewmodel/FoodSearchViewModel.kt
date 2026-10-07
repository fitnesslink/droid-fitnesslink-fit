package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.FoodSearchResult
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Debounced `GET /foods/search` (iOS `FoodSearchView` state). The server
 * merges the caller's saved custom foods above the USDA catalog rows;
 * [localEntriesById] snapshots local food-entry rows so template rows can
 * be recognised on payloads that predate the `isTemplate` flag
 * (`FoodSearchRouting`).
 */
class FoodSearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<FoodSearchResult>>(emptyList())
        private set
    var localEntriesById by mutableStateOf<Map<String, FoodEntry>>(emptyMap())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var hasSearched by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var searchJob: Job? = null

    fun onQueryChange(newValue: String) {
        query = newValue
        scheduleSearch(newValue)
    }

    fun clear() {
        searchJob?.cancel()
        query = ""
        results = emptyList()
        hasSearched = false
        isSearching = false
        errorMessage = null
    }

    private fun scheduleSearch(newValue: String) {
        searchJob?.cancel()
        errorMessage = null
        val trimmed = newValue.trim()
        if (trimmed.isEmpty()) {
            results = emptyList(); hasSearched = false; isSearching = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            runSearch(trimmed)
        }
    }

    private suspend fun runSearch(trimmed: String) {
        isSearching = true
        try {
            val fetched = ApiClient.nutritionApi.searchFood(trimmed)
            localEntriesById = DatabaseManager.allFoodEntries().associateBy { it.id.lowercase(Locale.US) }
            results = fetched
            hasSearched = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            results = emptyList()
            hasSearched = true
            errorMessage = describe(e)
        } finally {
            isSearching = false
        }
    }

    /** True when the row is one of the user's saved custom foods rather than a catalog food (`FoodSearchRouting.isTemplate`). */
    fun isTemplate(result: FoodSearchResult): Boolean =
        result.isTemplate ?: (localEntriesById[result.id.lowercase(Locale.US)] != null)

    fun localEntry(result: FoodSearchResult): FoodEntry? = localEntriesById[result.id.lowercase(Locale.US)]

    private fun describe(e: Exception): String = when (e) {
        is ApiError.Offline -> "No internet connection. Check and try again."
        is ApiError.NetworkError -> "Network error. Check your connection and try again."
        is ApiError.DecodingError -> "Server response didn't match the expected format."
        is ApiError.Unauthorized -> "Session expired. Please log in again."
        is ApiError.ServerError -> "Server error (${e.statusCode}). Try again later."
        is ApiError -> e.message ?: "Search failed."
        else -> "Search failed: ${e.message}"
    }
}

/** A saved-custom-food search row reshaped for the template detail path (`FoodSearchResult.asTemplateEntry`). `foodId` stays null on purpose. */
fun FoodSearchResult.asTemplateEntry(mealType: com.fitnesslink.fit.model.MealType): FoodEntry = FoodEntry(
    id = id,
    name = displayName,
    calories = Math.round(calories).toInt(),
    protein = protein,
    fat = fat,
    carbs = carbs,
    servingSize = servingSizeGrams ?: 1.0,
    servingUnit = servingUnit ?: "serving",
    mealType = mealType,
    isCustomTemplate = true
)
