package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.GroceryItem
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.FoodSearchResult
import com.fitnesslink.fit.nutrition.MealBuilderItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale

/** How well a grocery line matched a catalog food (iOS `MatchConfidence`). */
enum class MatchConfidence(val label: String) {
    LOW("Weak match"),
    MEDIUM("Likely match"),
    HIGH("Strong match")
}

/**
 * Name-similarity scoring for grocery → food matches (port of iOS
 * `GroceryFoodMatcher`). Pure and unit-tested.
 */
object GroceryFoodMatcher {
    /** Lowercase, drop a parenthetical brand suffix, keep alphanumerics + spaces, collapse whitespace. */
    fun normalize(raw: String): String {
        var s = raw.lowercase(Locale.US)
        s.indexOf('(').takeIf { it >= 0 }?.let { s = s.substring(0, it) }
        s = s.filter { it.isLetterOrDigit() || it.isWhitespace() }
        return s.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun tokens(s: String): Set<String> = normalize(s).split(' ').filter { it.isNotEmpty() }.toSet()

    fun confidence(itemName: String, candidateName: String): MatchConfidence {
        val a = normalize(itemName)
        val b = normalize(candidateName)
        if (a.isEmpty() || b.isEmpty()) return MatchConfidence.LOW
        if (a == b) return MatchConfidence.HIGH
        val ta = tokens(itemName)
        val tb = tokens(candidateName)
        if (ta.isEmpty() || tb.isEmpty()) return MatchConfidence.LOW
        // One name's tokens fully contained in the other ("chicken" ⊂ "chicken breast").
        if (tb.containsAll(ta) || ta.containsAll(tb)) return if (ta == tb) MatchConfidence.HIGH else MatchConfidence.MEDIUM
        // Whole-string prefix ("greek yogurt" vs "greek yogurt plain").
        if (b.startsWith(a) || a.startsWith(b)) return MatchConfidence.MEDIUM
        val overlap = ta.intersect(tb).size.toDouble()
        val union = ta.union(tb).size.toDouble()
        val jaccard = if (union > 0) overlap / union else 0.0
        return when {
            jaccard >= 0.6 -> MatchConfidence.HIGH
            jaccard >= 0.34 -> MatchConfidence.MEDIUM
            else -> MatchConfidence.LOW
        }
    }
}

/**
 * Match selected grocery items to catalog foods and collect the user's
 * decisions (port of iOS `GroceryMealBuilderViewModel`, FTNSSLNK-209).
 * Only an exact / strong match is pre-accepted; anything weaker waits for an
 * explicit decision.
 */
class GroceryMealBuilderViewModel : ViewModel() {
    sealed class Resolution {
        data object NeedsReview : Resolution()
        /** Use the candidate. */
        data object Accepted : Resolution()
        data class Manual(val food: MealBuilderItem) : Resolution()
        data object Skipped : Resolution()
    }

    data class ItemState(
        val id: String,
        val itemName: String,
        val isSearching: Boolean = false,
        val candidate: FoodSearchResult? = null,
        val confidence: MatchConfidence? = null,
        val resolution: Resolution = Resolution.NeedsReview
    ) {
        val isResolved: Boolean get() = resolution != Resolution.NeedsReview
        val stagedFood: MealBuilderItem?
            get() = when (val r = resolution) {
                Resolution.Accepted -> candidate?.let { MealBuilderItem.from(it) }
                is Resolution.Manual -> r.food
                else -> null
            }
    }

    var items by mutableStateOf<List<ItemState>>(emptyList())
        private set

    fun start(selected: List<GroceryItem>) {
        items = selected.map { ItemState(id = it.id, itemName = it.name) }
    }

    // MARK: - Matching

    suspend fun matchAll() = coroutineScope {
        items.map { item -> async { match(item.id) } }.awaitAll()
        Unit
    }

    suspend fun match(itemId: String) {
        val idx = items.indexOfFirst { it.id == itemId }
        if (idx < 0) return
        items = items.update(itemId) { it.copy(isSearching = true) }
        val name = items[idx].itemName
        val results = runCatching { ApiClient.nutritionApi.searchFood(name) }.getOrDefault(emptyList())
        val top = results.firstOrNull()
        items = items.update(itemId) { cur ->
            if (top == null) {
                cur.copy(
                    isSearching = false, candidate = null, confidence = null,
                    resolution = if (cur.resolution == Resolution.Accepted) Resolution.NeedsReview else cur.resolution
                )
            } else {
                val conf = GroceryFoodMatcher.confidence(name, top.name)
                val resolution = if (cur.resolution == Resolution.NeedsReview || cur.resolution == Resolution.Accepted) {
                    if (conf == MatchConfidence.HIGH) Resolution.Accepted else Resolution.NeedsReview
                } else cur.resolution
                cur.copy(isSearching = false, candidate = top, confidence = conf, resolution = resolution)
            }
        }
    }

    // MARK: - Decisions

    fun accept(itemId: String) = setResolution(itemId, Resolution.Accepted)
    fun skip(itemId: String) = setResolution(itemId, Resolution.Skipped)
    fun setManual(itemId: String, food: MealBuilderItem) = setResolution(itemId, Resolution.Manual(food))

    fun replaceCandidate(itemId: String, result: FoodSearchResult) {
        items = items.update(itemId) {
            it.copy(candidate = result, confidence = GroceryFoodMatcher.confidence(it.itemName, result.name), resolution = Resolution.Accepted)
        }
    }

    private fun setResolution(itemId: String, resolution: Resolution) {
        items = items.update(itemId) { cur ->
            if (resolution == Resolution.Accepted && cur.candidate == null) cur else cur.copy(resolution = resolution)
        }
    }

    private fun List<ItemState>.update(id: String, transform: (ItemState) -> ItemState): List<ItemState> =
        map { if (it.id == id) transform(it) else it }

    // MARK: - Derived

    val allResolved: Boolean get() = items.all { it.isResolved }
    val resolvedFoods: List<MealBuilderItem> get() = items.mapNotNull { it.stagedFood }
    val canBuild: Boolean get() = allResolved && resolvedFoods.isNotEmpty()
    val unresolvedCount: Int get() = items.count { !it.isResolved }
    val includedCount: Int get() = resolvedFoods.size

    val suggestedName: String
        get() {
            val foods = resolvedFoods
            return when {
                foods.size == 1 -> foods[0].name
                foods.size > 1 -> "Grocery Meal · ${foods.size} items"
                else -> "Grocery Meal"
            }
        }
}
