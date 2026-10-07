package com.fitnesslink.fit.nutrition

import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealSlotFood
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.dto.BatchCreateFoodEntriesRequest
import com.fitnesslink.fit.network.dto.MealTemplate
import com.fitnesslink.fit.network.dto.MealTemplateFood
import com.fitnesslink.fit.network.dto.RecentMeal
import com.fitnesslink.fit.network.dto.RecentMealItem
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Builders for `POST /food-entries/batch` payloads (FTNSSLNK-214/219/450):
 * every staged item becomes the exact single-create body; the batch is
 * atomic server-side (1–50 entries). Pure functions, unit-tested.
 */
object FoodEntryBatch {
    const val MAX_ENTRIES = 50

    private fun newId() = UUID.randomUUID().toString().lowercase(Locale.US)

    /** Each staged food → a diary entry with TOTAL macros (per-serving × servings), as the recent-meal re-log does. */
    fun fromMealSlotFoods(foods: List<MealSlotFood>, mealType: MealType, logDate: Date?): List<FoodEntry> {
        val loggedAt = FoodEntry.timestamp(logDate)
        return foods.map { food ->
            FoodEntry(
                id = newId(),
                name = food.name,
                calories = food.totalCalories.roundToInt(),
                protein = food.totalProtein,
                fat = food.totalFat,
                carbs = food.totalCarbs,
                servingSize = food.servings,
                servingUnit = food.servingUnit ?: "serving",
                mealType = mealType,
                loggedAt = loggedAt,
                foodId = food.foodId,
                quantity = food.servings,
                portionUnit = food.servingUnit,
                portionGramWeight = food.servingSize
            )
        }
    }

    /** A template's foods, in sortOrder, as [MealSlotFood]s (per-serving snapshot × servings). */
    fun templateFoods(template: MealTemplate): List<MealSlotFood> =
        template.foods.sortedBy { it.sortOrder }.map { it.toMealSlotFood() }

    fun MealTemplateFood.toMealSlotFood(): MealSlotFood = MealSlotFood(
        foodId = foodId, name = name, servings = if (servings > 0) servings else 1.0,
        servingSize = servingSize, servingUnit = servingUnit,
        calories = calories, protein = protein, fat = fat, carbs = carbs
    )

    /**
     * A recent meal's items staged as itemized foods: the wire macros are the
     * TOTAL for the stated quantity, so `servings` = that quantity and the
     * per-serving snapshot is scaled back out (total = per-serving × servings).
     */
    fun stagedFoods(meal: RecentMeal): List<MealSlotFood> = meal.items.map { it.toMealSlotFood() }

    fun RecentMealItem.toMealSlotFood(): MealSlotFood {
        val servings = (quantity ?: servingSize ?: 1.0).let { if (it > 0) it else 1.0 }
        return MealSlotFood(
            foodId = foodId, name = name, servings = servings,
            servingSize = portionGramWeight, servingUnit = portionUnit ?: servingUnit,
            calories = calories / servings, protein = protein / servings, fat = fat / servings, carbs = carbs / servings
        )
    }

    /** One reviewed AI row (Describe a Meal / Snap a Meal). */
    data class ReviewedItem(
        val name: String,
        val calories: Int,
        val protein: Double,
        val fat: Double,
        val carbs: Double,
        val servingSize: Double,
        val servingUnit: String,
        val confidence: String
    )

    /** Reviewed AI items → entries carrying provenance (`ai_estimate` or `ai_photo`), confidence and the shared generation id. */
    fun fromReviewedItems(
        items: List<ReviewedItem>,
        mealType: MealType,
        logDate: Date?,
        source: String,
        generationId: String?
    ): List<FoodEntry> {
        val loggedAt = FoodEntry.timestamp(logDate)
        return items.map { row ->
            FoodEntry(
                id = newId(),
                name = row.name.trim(),
                calories = row.calories,
                protein = row.protein,
                fat = row.fat,
                carbs = row.carbs,
                servingSize = row.servingSize,
                servingUnit = row.servingUnit.trim().ifEmpty { "serving" },
                mealType = mealType,
                loggedAt = loggedAt,
                source = source,
                aiConfidence = row.confidence,
                aiGenerationId = generationId
            )
        }
    }

    /** Stamp a provenance source / generation id onto entries that don't already carry one. */
    fun stamp(entries: List<FoodEntry>, source: String?, generationId: String?): List<FoodEntry> {
        if (source == null && generationId == null) return entries
        return entries.map { e ->
            e.copy(
                source = e.source ?: source,
                aiGenerationId = e.aiGenerationId ?: generationId
            )
        }
    }

    /** Validate the 1–50 contract and wrap. Throws [IllegalArgumentException] outside the range. */
    fun request(entries: List<FoodEntry>): BatchCreateFoodEntriesRequest {
        require(entries.isNotEmpty()) { "A batch needs at least one entry" }
        require(entries.size <= MAX_ENTRIES) { "A batch holds at most $MAX_ENTRIES entries" }
        return BatchCreateFoodEntriesRequest(entries = entries)
    }

    /** Split an oversized list into ≤50-entry batches, preserving order. */
    fun chunk(entries: List<FoodEntry>): List<List<FoodEntry>> = entries.chunked(MAX_ENTRIES)
}
