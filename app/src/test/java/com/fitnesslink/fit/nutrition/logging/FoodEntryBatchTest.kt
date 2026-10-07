package com.fitnesslink.fit.nutrition.logging

import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealSlotFood
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.dto.MealTemplate
import com.fitnesslink.fit.network.dto.MealTemplateFood
import com.fitnesslink.fit.network.dto.RecentMeal
import com.fitnesslink.fit.network.dto.RecentMealItem
import com.fitnesslink.fit.nutrition.FoodEntryBatch
import com.fitnesslink.fit.nutrition.PreviousMealsAssembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** `POST /food-entries/batch` payload building (recent meals, templates, AI review rows). */
class FoodEntryBatchTest {

    private val rice = MealSlotFood(foodId = "fdc-rice", name = "Rice", servings = 2.0, servingSize = 158.0, servingUnit = "cup", calories = 206.0, protein = 4.3, fat = 0.4, carbs = 44.5)
    private val egg = MealSlotFood(name = "Egg", servings = 3.0, servingUnit = "large", calories = 72.0, protein = 6.3, fat = 4.8, carbs = 0.4)

    @Test fun `meal slot foods become total-macro entries with the structured portion`() {
        val entries = FoodEntryBatch.fromMealSlotFoods(listOf(rice, egg), MealType.DINNER, null)
        assertEquals(2, entries.size)
        val r = entries[0]
        assertEquals("Rice", r.name)
        assertEquals(412, r.calories)                 // 206 × 2
        assertEquals(89.0, r.carbs, 1e-9)
        assertEquals("fdc-rice", r.foodId)
        assertEquals(2.0, r.quantity)
        assertEquals("cup", r.portionUnit)
        assertEquals(158.0, r.portionGramWeight)
        assertEquals(MealType.DINNER, r.mealType)
        val e = entries[1]
        assertEquals(216, e.calories)
        assertNull(e.foodId)
        assertEquals("large", e.servingUnit)
        assertNotEquals(r.id, e.id)
        assertEquals(r.id, r.id.lowercase())
    }

    @Test fun `backfilled entries are stamped at local noon of the target day`() {
        val past = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -3) }.time
        val entry = FoodEntryBatch.fromMealSlotFoods(listOf(egg), MealType.BREAKFAST, past).single()
        val cal = Calendar.getInstance().apply { time = entry.loggedAt }
        val target = Calendar.getInstance().apply { time = past }
        assertEquals(target.get(Calendar.DAY_OF_YEAR), cal.get(Calendar.DAY_OF_YEAR))
        assertEquals(12, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test fun `recent meal items are re-portioned so total equals per-serving times servings`() {
        val meal = RecentMeal(
            mealType = "Lunch", date = "2026-10-01", entryCount = 2, calories = 500.0,
            items = listOf(
                RecentMealItem(name = "Chicken", calories = 330.0, protein = 62.0, fat = 7.2, carbs = 0.0, foodId = "fdc-c", quantity = 2.0, portionUnit = "breast", portionGramWeight = 120.0),
                RecentMealItem(name = "Salad", calories = 170.0, protein = 3.0, fat = 12.0, carbs = 14.0, servingSize = 1.0, servingUnit = "bowl")
            )
        )
        val staged = FoodEntryBatch.stagedFoods(meal)
        assertEquals(2.0, staged[0].servings, 1e-9)
        assertEquals(165.0, staged[0].calories, 1e-9)
        assertEquals(330.0, staged[0].totalCalories, 1e-9)
        assertEquals("breast", staged[0].servingUnit)
        assertEquals(120.0, staged[0].servingSize)
        assertEquals(1.0, staged[1].servings, 1e-9)
        assertEquals("bowl", staged[1].servingUnit)
        val entries = FoodEntryBatch.fromMealSlotFoods(staged, MealType.LUNCH, null)
        assertEquals(330, entries[0].calories)
        assertEquals(170, entries[1].calories)
    }

    @Test fun `template foods are applied in sort order`() {
        val template = MealTemplate(
            id = "t", name = "Breakfast", foods = listOf(
                MealTemplateFood(id = "b", name = "Second", servings = 1.0, calories = 10.0, sortOrder = 2),
                MealTemplateFood(id = "a", name = "First", servings = 0.0, calories = 20.0, sortOrder = 1)
            )
        )
        val foods = FoodEntryBatch.templateFoods(template)
        assertEquals(listOf("First", "Second"), foods.map { it.name })
        assertEquals(1.0, foods[0].servings, 1e-9) // zero servings normalised
    }

    @Test fun `reviewed AI rows carry provenance confidence and the shared generation id`() {
        val rows = listOf(
            FoodEntryBatch.ReviewedItem(" Burrito ", 650, 30.0, 22.0, 70.0, 1.0, "", "medium"),
            FoodEntryBatch.ReviewedItem("Guac", 120, 1.5, 11.0, 6.0, 2.0, "tbsp", "high")
        )
        val entries = FoodEntryBatch.fromReviewedItems(rows, MealType.DINNER, null, FoodEntry.Source.aiPhoto, "gen-9")
        assertEquals("Burrito", entries[0].name)
        assertEquals("serving", entries[0].servingUnit)
        assertEquals("tbsp", entries[1].servingUnit)
        assertTrue(entries.all { it.source == FoodEntry.Source.aiPhoto && it.aiGenerationId == "gen-9" })
        assertEquals("medium", entries[0].aiConfidence)
        assertEquals("high", entries[1].aiConfidence)
    }

    @Test fun `stamp fills missing provenance without overwriting existing values`() {
        val manual = FoodEntry(name = "A", calories = 1)
        val ai = FoodEntry(name = "B", calories = 1, source = FoodEntry.Source.aiEstimate, aiGenerationId = "own")
        val stamped = FoodEntryBatch.stamp(listOf(manual, ai), FoodEntry.Source.copied, "shared")
        assertEquals(FoodEntry.Source.copied, stamped[0].source)
        assertEquals("shared", stamped[0].aiGenerationId)
        assertEquals(FoodEntry.Source.aiEstimate, stamped[1].source)
        assertEquals("own", stamped[1].aiGenerationId)
        assertEquals(listOf(manual, ai), FoodEntryBatch.stamp(listOf(manual, ai), null, null))
    }

    @Test fun `request enforces the 1 to 50 contract and chunk splits larger lists`() {
        val many = (1..120).map { FoodEntry(name = "f$it", calories = it) }
        assertEquals(listOf(50, 50, 20), FoodEntryBatch.chunk(many).map { it.size })
        assertEquals(50, FoodEntryBatch.request(many.take(50)).entries.size)
        try { FoodEntryBatch.request(emptyList()); throw AssertionError("expected failure") } catch (_: IllegalArgumentException) { }
        try { FoodEntryBatch.request(many); throw AssertionError("expected failure") } catch (_: IllegalArgumentException) { }
    }

    @Test fun `previous meals group the diary by day and meal and skip template rows`() {
        val day = Date()
        val entries = listOf(
            FoodEntry(name = "Oats", calories = 300, mealType = MealType.BREAKFAST, loggedAt = day, quantity = 1.0, portionUnit = "cup"),
            FoodEntry(name = "Banana", calories = 100, mealType = MealType.BREAKFAST, loggedAt = day),
            FoodEntry(name = "Saved", calories = 1, isCustomTemplate = true, loggedAt = Date(0))
        )
        val meals = PreviousMealsAssembler.loggedMeals(entries)
        assertEquals(1, meals.size)
        assertEquals("Breakfast · 2 items", meals[0].name)
        assertEquals(400, meals[0].totalCalories)
        assertEquals("cup", meals[0].foods[0].servingUnit)
    }
}
