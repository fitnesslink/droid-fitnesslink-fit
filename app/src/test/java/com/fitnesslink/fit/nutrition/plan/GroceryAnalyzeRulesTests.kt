package com.fitnesslink.fit.nutrition.plan

import com.fitnesslink.fit.model.GroceryCategory
import com.fitnesslink.fit.network.dto.GroceryAnalyzeResponse
import com.fitnesslink.fit.network.dto.GroceryDuplicateMatch
import com.fitnesslink.fit.network.dto.GroceryStaple
import com.fitnesslink.fit.nutrition.GroceryAnalyzeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryAnalyzeRulesTests {

    private val duplicate = GroceryDuplicateMatch(itemId = "i1", name = "Chicken breast", quantity = 1.0, unit = "lb", proposedQuantity = 3.0, quantitySummable = true, rowVersion = "rv")

    @Test
    fun `new item with a duplicate prompts to merge`() {
        val decision = GroceryAnalyzeRules.decide(isNew = true, analysis = GroceryAnalyzeResponse(duplicate = duplicate), canMerge = true)
        assertTrue(decision is GroceryAnalyzeRules.AddDecision.PromptMerge)
        assertEquals(duplicate, (decision as GroceryAnalyzeRules.AddDecision.PromptMerge).duplicate)
    }

    @Test
    fun `edits, misses, failed analyses and no merge handler all add separately`() {
        assertEquals(GroceryAnalyzeRules.AddDecision.AddSeparately, GroceryAnalyzeRules.decide(isNew = false, analysis = GroceryAnalyzeResponse(duplicate = duplicate), canMerge = true))
        assertEquals(GroceryAnalyzeRules.AddDecision.AddSeparately, GroceryAnalyzeRules.decide(isNew = true, analysis = GroceryAnalyzeResponse(duplicate = null), canMerge = true))
        assertEquals(GroceryAnalyzeRules.AddDecision.AddSeparately, GroceryAnalyzeRules.decide(isNew = true, analysis = null, canMerge = true))
        assertEquals(GroceryAnalyzeRules.AddDecision.AddSeparately, GroceryAnalyzeRules.decide(isNew = true, analysis = GroceryAnalyzeResponse(duplicate = duplicate), canMerge = false))
    }

    @Test
    fun `category suggestion maps backend names and respects dismissal and staleness`() {
        val analysis = GroceryAnalyzeResponse(suggestedCategory = "Meat")
        assertEquals(GroceryCategory.PROTEIN, GroceryAnalyzeRules.visibleSuggestion(true, analysis, "chicken", "chicken", null, GroceryCategory.OTHER))
        // Already picked → no chip.
        assertNull(GroceryAnalyzeRules.visibleSuggestion(true, analysis, "chicken", "chicken", null, GroceryCategory.PROTEIN))
        // Dismissed for this name → no chip.
        assertNull(GroceryAnalyzeRules.visibleSuggestion(true, analysis, "chicken", "chicken", "chicken", GroceryCategory.OTHER))
        // Analysis for a different (stale) name → no chip.
        assertNull(GroceryAnalyzeRules.visibleSuggestion(true, analysis, "chick", "chicken", null, GroceryCategory.OTHER))
        // Never on edits; never without a suggestion.
        assertNull(GroceryAnalyzeRules.visibleSuggestion(false, analysis, "chicken", "chicken", null, GroceryCategory.OTHER))
        assertNull(GroceryAnalyzeRules.visibleSuggestion(true, GroceryAnalyzeResponse(), "chicken", "chicken", null, GroceryCategory.OTHER))
    }

    @Test
    fun `combine label shows the summed amount when summable`() {
        assertEquals("Combine into 3 lb", GroceryAnalyzeRules.combineLabel(duplicate, typedUnit = "lb"))
        assertEquals("Combine into 2.5 lb", GroceryAnalyzeRules.combineLabel(duplicate.copy(proposedQuantity = 2.5), typedUnit = ""))
        assertEquals("Combine into 3", GroceryAnalyzeRules.combineLabel(duplicate.copy(unit = null), typedUnit = ""))
        assertEquals("Combine", GroceryAnalyzeRules.combineLabel(duplicate.copy(quantitySummable = false), typedUnit = "lb"))
        assertEquals("Combine", GroceryAnalyzeRules.combineLabel(duplicate.copy(proposedQuantity = null), typedUnit = "lb"))
    }

    @Test
    fun `staple subtitle`() {
        assertEquals("usually 2 lb · on 4 lists", GroceryAnalyzeRules.stapleSubtitle(GroceryStaple(name = "Chicken", lastQuantity = 2.0, lastUnit = "lb", listCount = 4)))
        assertEquals("usually 1.5 · on 1 list", GroceryAnalyzeRules.stapleSubtitle(GroceryStaple(name = "Milk", lastQuantity = 1.5, lastUnit = null, listCount = 1)))
        assertEquals("on 2 lists", GroceryAnalyzeRules.stapleSubtitle(GroceryStaple(name = "Eggs", listCount = 2)))
    }
}
