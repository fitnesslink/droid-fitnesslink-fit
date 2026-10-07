package com.fitnesslink.fit.nutrition.logging

import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.dto.FoodMacros
import com.fitnesslink.fit.nutrition.ManualFoodForm
import com.fitnesslink.fit.nutrition.ManualFoodForm.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Estimate-macros lock/fill merge + manual-form rules (iOS `ManualFoodForm`). */
class ManualFoodFormTest {

    private val estimate = FoodMacros(name = "Chicken breast", calories = 165.0, protein = 31.0, fat = 3.6, carbs = 0.0, servingSize = 100.0, servingUnit = "g")

    @Test fun `validation requires name calories serving size and unit`() {
        val blank = ManualFoodForm()
        assertFalse(blank.isValid)
        assertEquals("Name is required", blank.error(Field.NAME))
        assertEquals("Calories are required", blank.error(Field.CALORIES))
        assertEquals("Enter a number", ManualFoodForm(calories = "abc").error(Field.CALORIES))
        assertEquals("Must be 0 or more", ManualFoodForm(protein = "-1").error(Field.PROTEIN))
        assertNull(ManualFoodForm(protein = "").error(Field.PROTEIN))
        assertEquals("Must be more than 0", ManualFoodForm(servingSize = "0").error(Field.SERVING_SIZE))
        assertTrue(ManualFoodForm(name = "Egg", calories = "70").isValid)
    }

    @Test fun `first fill writes blanks only and locks what was typed`() {
        val form = ManualFoodForm(name = "Chicken breast", calories = "200")
        val request = form.estimateRequest()
        assertEquals(200.0, request.knownCalories)
        assertNull(request.knownProtein)
        assertEquals("1 serving", request.servingDescription)

        val filled = form.applyFill(estimate, source = "ai_estimate", confidence = "medium", generationId = "gen-1")
        assertEquals("200", filled.calories)            // typed value kept
        assertEquals("31", filled.protein)              // blank filled
        assertEquals("3.6", filled.fat)
        assertEquals("0", filled.carbs)
        assertEquals(setOf(Field.CALORIES), filled.fill!!.lockedFields)
        assertEquals(setOf(Field.PROTEIN, Field.FAT, Field.CARBS), filled.fill!!.filledFields)
        // Default serving fields take over the estimate's serving.
        assertEquals("100", filled.servingSize)
        assertEquals("g", filled.servingUnit)
        assertEquals(FoodEntry.Source.aiEstimate, filled.source)
        assertEquals("medium", filled.aiConfidence)
        assertEquals("gen-1", filled.aiGenerationId)
        assertEquals("AI estimate · medium confidence", filled.fill!!.badgeText)
    }

    @Test fun `library source carries no AI provenance`() {
        val filled = ManualFoodForm(name = "Oats").applyFill(estimate, source = "library", confidence = null, generationId = "gen")
        assertEquals(FoodEntry.Source.library, filled.source)
        assertNull(filled.aiConfidence)
        assertNull(filled.aiGenerationId)
        assertEquals("From food library", filled.fill!!.badgeText)
    }

    @Test fun `weight unit with per-100g values computes locally and leaves the serving alone`() {
        val per100 = estimate.copy(caloriesPer100g = 165.0, proteinPer100g = 31.0, fatPer100g = 3.6, carbsPer100g = 0.0)
        val filled = ManualFoodForm(name = "Chicken", servingSize = "150", servingUnit = "g").applyFill(per100, "ai_estimate", "high", "g")
        assertEquals("248", filled.calories)       // 165 × 1.5 rounded
        assertEquals("46.5", filled.protein)
        assertEquals("150", filled.servingSize)
        assertEquals("g", filled.servingUnit)
        assertEquals(150.0, filled.fill!!.servingSize, 1e-9)
    }

    @Test fun `re-estimate re-opens AI-written fields but keeps the original locks`() {
        val first = ManualFoodForm(name = "Chicken", calories = "200").applyFill(estimate, "ai_estimate", "low", "g1")
        val reRequest = first.estimateRequest(locking = first.fill!!.lockedFields)
        assertEquals(200.0, reRequest.knownCalories)
        assertNull(reRequest.knownProtein) // AI-written protein is open again
        val second = first.applyFill(estimate.copy(protein = 40.0), "ai_estimate", "high", "g2", overwriting = first.fill!!.filledFields)
        assertEquals("200", second.calories)
        assertEquals("40", second.protein)
        assertEquals("g2", second.aiGenerationId)
    }

    @Test fun `rescale is proportional to the filled serving and respects locks`() {
        val filled = ManualFoodForm(name = "Chicken", calories = "200").applyFill(estimate, "ai_estimate", "high", "g")
        val kept = filled.rescale(toServingSize = 200.0, includingLocked = false)
        assertEquals("200", kept.calories)   // locked, untouched
        assertEquals("62", kept.protein)     // 31 × 2
        assertEquals(200.0, kept.fill!!.servingSize, 1e-9)
        val all = filled.rescale(toServingSize = 50.0, includingLocked = true)
        assertEquals("100", all.calories)
        assertEquals("15.5", all.protein)
        assertEquals(ManualFoodForm(), ManualFoodForm().rescale(2.0, true)) // no fill → no-op
    }

    @Test fun `clearFill drops provenance but keeps the numbers`() {
        val cleared = ManualFoodForm(name = "Chicken").applyFill(estimate, "ai_estimate", "high", "g").clearFill()
        assertNull(cleared.fill); assertNull(cleared.source); assertNull(cleared.aiGenerationId)
        assertEquals("165", cleared.calories)
    }

    @Test fun `template entry gets the sentinel date and flag, logged entry gets the day`() {
        val form = ManualFoodForm(name = "Egg", calories = "70", protein = "6", servingSize = "2", servingUnit = "piece", saveAsCustomFood = true)
        val template = form.templateEntry(MealType.SNACK)!!
        assertTrue(template.isCustomTemplate)
        assertEquals(0L, template.loggedAt.time)
        assertEquals(2.0, template.quantity)
        assertEquals("piece", template.portionUnit)
        assertNull(template.portionGramWeight)
        val logged = form.loggedEntry(MealType.LUNCH, null)!!
        assertFalse(logged.isCustomTemplate)
        assertEquals(MealType.LUNCH, logged.mealType)
        assertNull(ManualFoodForm(name = "Egg", calories = "70").templateEntry(MealType.SNACK))
        assertEquals(1.0, ManualFoodForm(name = "Milk", calories = "100", servingUnit = "g").loggedEntry(MealType.BREAKFAST, null)!!.portionGramWeight)
    }

    @Test fun `barcode cache projects to per-100g only for gram-convertible units`() {
        val grams = ManualFoodForm(name = "Bar", calories = "200", protein = "10", servingSize = "50", servingUnit = "g", barcode = "012345").cacheableBarcodeProduct()
        assertNotNull(grams)
        assertEquals(400.0, grams!!.caloriesPer100g, 1e-9)
        assertEquals(20.0, grams.proteinPer100g, 1e-9)
        assertEquals(50.0, grams.servingSizeGrams, 1e-9)
        assertEquals("012345", grams.barcode)
        assertNull(ManualFoodForm(name = "Bar", calories = "200", servingUnit = "serving", barcode = "012345").cacheableBarcodeProduct())
        assertNull(ManualFoodForm(name = "Bar", calories = "200", servingUnit = "g").cacheableBarcodeProduct())
    }

    @Test fun `from entry round-trips the editable fields`() {
        val entry = FoodEntry(name = "Yogurt", calories = 120, protein = 10.0, fat = 2.5, carbs = 14.0, servingSize = 1.0, servingUnit = "cup", isCustomTemplate = true, source = "manual")
        val form = ManualFoodForm.from(entry)
        assertEquals("Yogurt", form.name); assertEquals("120", form.calories); assertEquals("2.5", form.fat); assertEquals("cup", form.servingUnit)
        assertTrue(form.saveAsCustomFood)
        assertTrue(form.isValid)
    }
}
