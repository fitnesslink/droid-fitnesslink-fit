package com.fitnesslink.fit.nutrition

import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.network.dto.FoodSearchResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory hand-off between logging screens, replacing the value payloads
 * iOS rides inside `AppRoute` (string routes can't carry a `FoodEntry`):
 *
 * - [FoodDetailHandoff] parks a `FoodSearchResult` (so Food Detail doesn't
 *   re-fetch a food the search just returned) or a template `FoodEntry`
 *   that has no DB row yet (barcode product hit, search template row) under
 *   a token the route carries.
 * - [FoodCaptureBus] is the Android analogue of iOS's `mealBuilderCapture`
 *   environment flag for screens that assemble a meal LOCALLY (the recent
 *   meal re-log editor): pickers opened with `mode=capture` push the picked
 *   food here and pop; the editor drains it when it recomposes.
 */
object FoodDetailHandoff {
    private val foods = ConcurrentHashMap<String, FoodSearchResult>()
    private val entries = ConcurrentHashMap<String, FoodEntry>()

    /** Cache a catalog food under its own id so `foodDetail/{foodId}` opens without a network hop. */
    fun putFood(food: FoodSearchResult) { foods[food.id.lowercase()] = food }

    fun food(foodId: String): FoodSearchResult? = foods[foodId.lowercase()]

    /** Park a template entry (no local DB row) and return the token for `mode=handoff:{token}`. */
    fun putEntry(entry: FoodEntry): String {
        val token = UUID.randomUUID().toString()
        entries[token] = entry
        return token
    }

    fun entry(token: String): FoodEntry? = entries[token]

    fun clearEntry(token: String) { entries.remove(token) }
}

/** A picked food headed for a locally-assembled meal (see [FoodCaptureBus]). */
data class CapturedFood(
    val foodId: String?,
    val name: String,
    val servings: Double,
    val servingSize: Double?,
    val servingUnit: String?,
    /** Per-serving snapshot. */
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double
)

object FoodCaptureBus {
    private val pending = ArrayList<CapturedFood>()

    @Synchronized fun push(item: CapturedFood) { pending.add(item) }

    /** Remove and return everything pushed since the last take. */
    @Synchronized fun take(): List<CapturedFood> {
        val out = pending.toList()
        pending.clear()
        return out
    }

    @Synchronized fun clear() { pending.clear() }
}

/** Snap a Meal → Describe a Meal prefill (iOS `SnapMealPrefill`), parked by token. */
data class SnapMealPrefill(val description: String, val sourceGenerationId: String)

object SnapMealPrefillHandoff {
    private val prefills = ConcurrentHashMap<String, SnapMealPrefill>()

    fun put(prefill: SnapMealPrefill): String {
        val token = UUID.randomUUID().toString()
        prefills[token] = prefill
        return token
    }

    fun get(token: String): SnapMealPrefill? = prefills[token]
}

/** Generic token → value hand-off for route payloads that have no DB row (e.g. a `RecentMeal` picked for re-log). */
object NavPayloadHandoff {
    private val payloads = ConcurrentHashMap<String, Any>()

    fun put(value: Any): String {
        val token = UUID.randomUUID().toString()
        payloads[token] = value
        return token
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(token: String): T? = payloads[token] as? T

    fun clear(token: String) { payloads.remove(token) }
}
