package com.fitnesslink.fit.nutrition.logging

import com.fitnesslink.fit.network.dto.FoodPortion
import com.fitnesslink.fit.network.dto.FoodSearchResult
import com.fitnesslink.fit.nutrition.FoodNutrientBasis
import com.fitnesslink.fit.nutrition.FoodPortionMath
import com.fitnesslink.fit.nutrition.StandardFoodPortions
import com.fitnesslink.fit.nutrition.nutrientBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of iOS `FoodPortionMath` / `FoodNutrientBasis` behaviour (backend `FoodPortionConverter` mirror). */
class FoodPortionMathTest {

    private val slice = FoodPortion(unit = "slice", gramWeight = 28.0, category = "Household", isStandard = false)
    private val cupOfFood = FoodPortion(unit = "cup", gramWeight = 140.0, category = "Volume", isStandard = false)

    @Test fun `serving grams mirrors the backend synonym table`() {
        assertEquals(100.0 to false, FoodNutrientBasis.servingGrams(100.0, "g"))
        assertEquals(100.0 to false, FoodNutrientBasis.servingGrams(100.0, " Grams "))
        assertEquals(2.0 * StandardFoodPortions.GRAMS_PER_OZ to false, FoodNutrientBasis.servingGrams(2.0, "oz"))
        assertEquals(500.0 to false, FoodNutrientBasis.servingGrams(0.5, "kg"))
        assertEquals(StandardFoodPortions.ML_PER_CUP to true, FoodNutrientBasis.servingGrams(1.0, "cup"))
        assertEquals(250.0 to true, FoodNutrientBasis.servingGrams(250.0, "ml"))
        assertNull(FoodNutrientBasis.servingGrams(1.0, "serving"))
        assertNull(FoodNutrientBasis.servingGrams(0.0, "g"))
    }

    @Test fun `basis is per gram of the stated serving`() {
        val basis = FoodNutrientBasis.from(calories = 200.0, protein = 10.0, fat = 5.0, carbs = 30.0, servingSize = 100.0, servingSizeUnit = "g")
        assertNotNull(basis)
        assertEquals(2.0, basis!!.caloriesPerGram, 1e-9)
        assertEquals(0.1, basis.proteinPerGram, 1e-9)
        assertEquals(100.0, basis.servingGrams, 1e-9)
        assertFalse(basis.densityAssumed)
        assertNull(FoodNutrientBasis.from(100.0, 1.0, 1.0, 1.0, servingSize = 1.0, servingSizeUnit = "piece"))
    }

    @Test fun `available portions backfill the standard units once`() {
        assertEquals(StandardFoodPortions.all, FoodPortionMath.availablePortions(null))
        assertEquals(StandardFoodPortions.all, FoodPortionMath.availablePortions(emptyList()))
        val portions = FoodPortionMath.availablePortions(listOf(slice, cupOfFood))
        assertEquals("slice", portions.first().unit)
        assertEquals(1, portions.count { it.unit.equals("cup", true) })
        assertSame(cupOfFood, portions.first { it.unit == "cup" })
        assertEquals(2 + StandardFoodPortions.all.size - 1, portions.size)
    }

    @Test fun `portion matching is case-insensitive and trimmed`() {
        val portions = FoodPortionMath.availablePortions(listOf(slice))
        assertEquals(slice, FoodPortionMath.portion(" SLICE ", portions))
        assertEquals("fl oz", FoodPortionMath.portion("FL OZ", portions)?.unit)
        assertNull(FoodPortionMath.portion("", portions))
        assertNull(FoodPortionMath.portion("bucket", portions))
    }

    @Test fun `convert scales nutrients by portion grams`() {
        val basis = FoodNutrientBasis.from(200.0, 10.0, 5.0, 30.0, 100.0, "g")!!
        val portions = FoodPortionMath.availablePortions(listOf(slice))
        val two = FoodPortionMath.convert(basis, portions, "slice", 2.0)!!
        assertEquals(56.0, two.grams, 1e-9)
        assertEquals(112.0, two.calories, 1e-9)
        assertEquals(5.6, two.protein, 1e-9)
        assertFalse(two.usedDensityFallback)
        assertEquals("Household", two.category)
        assertNull(FoodPortionMath.convert(basis, portions, "slice", 0.0))
        assertNull(FoodPortionMath.convert(basis, portions, "bucket", 1.0))
    }

    @Test fun `standard volume units flag the water-density fallback but a food cup does not`() {
        val basis = FoodNutrientBasis.from(100.0, 1.0, 1.0, 1.0, 100.0, "g")!!
        val standard = FoodPortionMath.convert(basis, StandardFoodPortions.all, "cup", 1.0)!!
        assertTrue(standard.usedDensityFallback)
        assertEquals(StandardFoodPortions.ML_PER_CUP, standard.grams, 1e-9)

        val foodSpecific = FoodPortionMath.convert(basis, FoodPortionMath.availablePortions(listOf(cupOfFood)), "cup", 1.0)!!
        assertFalse(foodSpecific.usedDensityFallback)
        assertEquals(140.0, foodSpecific.grams, 1e-9)

        // A basis derived through a volume serving carries the flag onto every conversion.
        val volumeBasis = FoodNutrientBasis.from(100.0, 1.0, 1.0, 1.0, 1.0, "cup")!!
        assertTrue(FoodPortionMath.convert(volumeBasis, StandardFoodPortions.all, "g", 10.0)!!.usedDensityFallback)
    }

    @Test fun `search result exposes a basis only when its serving unit is known`() {
        val known = FoodSearchResult(id = "a", name = "Oats", calories = 389.0, protein = 16.9, fat = 6.9, carbs = 66.3, servingSizeGrams = 100.0, servingUnit = "g")
        assertEquals(3.89, known.nutrientBasis!!.caloriesPerGram, 1e-9)
        val unknown = FoodSearchResult(id = "b", name = "Bar", calories = 200.0, servingSizeGrams = 1.0, servingUnit = "bar")
        assertNull(unknown.nutrientBasis)
    }

    @Test fun `quantity formatting trims zeros and keeps meaningful decimals`() {
        assertEquals("2", FoodPortionMath.formatQuantity(2.0))
        assertEquals("1.5", FoodPortionMath.formatQuantity(1.5))
        assertEquals("1.25", FoodPortionMath.formatQuantity(1.25))
        assertEquals(1.5, FoodPortionMath.parseQuantity("1,5"), 1e-9)
        assertEquals(0.0, FoodPortionMath.parseQuantity("x"), 1e-9)
    }

    private fun assertSame(expected: Any, actual: Any) = assertTrue(expected === actual)
}
