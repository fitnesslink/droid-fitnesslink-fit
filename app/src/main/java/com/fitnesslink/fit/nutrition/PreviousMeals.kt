package com.fitnesslink.fit.nutrition

import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealSlot
import com.fitnesslink.fit.model.MealSlotFood
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.model.mealTotalCalories
import com.fitnesslink.fit.model.mealTotalCarbs
import com.fitnesslink.fit.model.mealTotalFat
import com.fitnesslink.fit.model.mealTotalProtein
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * FTNSSLNK-206 — a "previous meal" a user can quick-add into a planner slot:
 * a distinct meal the user LOGGED (diary entries grouped by day + meal type)
 * or PLANNED (an itemized planner slot), assembled client-side into one shape.
 * Port of iOS `Models/PreviousMeal.swift`.
 */
enum class PreviousMealSource(val label: String) { LOGGED("Logged"), PLANNED("Planned") }

data class PreviousMeal(
    val id: String,
    val name: String,
    val mealType: MealType,
    val date: Date,
    val source: PreviousMealSource,
    /** Itemized foods (per-serving snapshot × servings). Stage DEEP COPIES. */
    val foods: List<MealSlotFood>
) {
    val totalCalories: Int get() = foods.mealTotalCalories
    val totalProtein: Double get() = foods.mealTotalProtein
    val totalFat: Double get() = foods.mealTotalFat
    val totalCarbs: Double get() = foods.mealTotalCarbs
    val itemCount: Int get() = foods.size
}

/** A value copy with a FRESH id (macros / servings / portion preserved). */
fun MealSlotFood.deepCopy(): MealSlotFood = copy(id = UUID.randomUUID().toString().lowercase(Locale.US))

fun List<MealSlotFood>.deepCopied(): List<MealSlotFood> = map { it.deepCopy() }

object PreviousMealsAssembler {

    /** Group the local diary into distinct logged meals: one per (calendar day, meal type), most-recent first, deduped. */
    fun loggedMeals(entries: List<FoodEntry>, limit: Int = 40): List<PreviousMeal> {
        val diary = entries.filter { !it.isCustomTemplate }
        data class Key(val day: Date, val meal: MealType)
        val buckets = LinkedHashMap<Key, MutableList<FoodEntry>>()
        for (entry in diary) {
            val key = Key(MealPlanDay.dayStart(entry.loggedAt), entry.mealType)
            buckets.getOrPut(key) { mutableListOf() }.add(entry)
        }
        val meals = buckets.map { (key, rows) ->
            val foods = rows.map { mealSlotFood(it) }
            PreviousMeal(
                id = "logged-${MealPlanDay.string(key.day)}-${key.meal.wireValue}",
                name = derivedName(key.meal, foods),
                mealType = key.meal,
                date = key.day,
                source = PreviousMealSource.LOGGED,
                foods = foods
            )
        }.sortedByDescending { it.date.time }
        return deduped(meals, limit)
    }

    /** Distinct planned meals from itemized planner slots (flat-only slots are skipped). */
    fun plannedMeals(slots: List<MealSlot>, limit: Int = 40): List<PreviousMeal> {
        val meals = slots.filter { it.foods.isNotEmpty() }.map { slot ->
            PreviousMeal(
                id = "planned-${slot.id}",
                name = if (slot.recipeName.isEmpty()) derivedName(slot.mealType, slot.foods) else slot.recipeName,
                mealType = slot.mealType,
                date = slot.dateValue,
                source = PreviousMealSource.PLANNED,
                foods = slot.foods
            )
        }.sortedByDescending { it.date.time }
        return deduped(meals, limit)
    }

    /** Planned + logged, deduped across both sources, preferred meal type floated to the top, then recency. */
    fun combined(entries: List<FoodEntry>, slots: List<MealSlot>, preferredMealType: MealType?, limit: Int = 40): List<PreviousMeal> {
        val planned = plannedMeals(slots, limit)
        val logged = loggedMeals(entries, limit)
        val merged = deduped(planned + logged, limit * 2)
        if (preferredMealType == null) return merged.sortedByDescending { it.date.time }.take(limit)
        return merged.sortedWith(
            compareByDescending<PreviousMeal> { it.mealType == preferredMealType }.thenByDescending { it.date.time }
        ).take(limit)
    }

    /**
     * One diary entry → an itemized food. The entry's macros are the TOTAL
     * for the quantity logged, so servings = the logged quantity and the
     * per-serving snapshot is scaled back out.
     */
    fun mealSlotFood(entry: FoodEntry): MealSlotFood {
        val servings = maxOf(entry.quantity ?: (if (entry.servingSize > 0) entry.servingSize else 1.0), 0.0001)
        return MealSlotFood(
            foodId = entry.foodId,
            name = entry.name,
            servings = servings,
            servingSize = entry.portionGramWeight,
            servingUnit = entry.portionUnit ?: entry.servingUnit,
            calories = entry.calories.toDouble() / servings,
            protein = entry.protein / servings,
            fat = entry.fat / servings,
            carbs = entry.carbs / servings
        )
    }

    fun derivedName(mealType: MealType, foods: List<MealSlotFood>): String = when {
        foods.size == 1 -> foods[0].name
        foods.size > 1 -> "${mealType.wireValue} · ${foods.size} items"
        else -> mealType.wireValue
    }

    private fun deduped(meals: List<PreviousMeal>, limit: Int): List<PreviousMeal> {
        val seen = HashSet<String>()
        val out = ArrayList<PreviousMeal>()
        for (meal in meals) {
            if (!seen.add(signature(meal))) continue
            out.add(meal)
            if (out.size >= limit) break
        }
        return out
    }

    private fun signature(meal: PreviousMeal): String {
        val parts = meal.foods
            .map { "${it.name.lowercase(Locale.US)}×${String.format(Locale.US, "%.2f", it.servings)}@${it.calories.roundToInt()}" }
            .sorted()
        return "${meal.mealType.wireValue}|" + parts.joinToString(",")
    }
}
