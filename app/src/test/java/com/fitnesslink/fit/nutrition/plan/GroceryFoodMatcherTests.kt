package com.fitnesslink.fit.nutrition.plan

import com.fitnesslink.fit.viewmodel.GroceryFoodMatcher
import com.fitnesslink.fit.viewmodel.MatchConfidence
import org.junit.Assert.assertEquals
import org.junit.Test

class GroceryFoodMatcherTests {

    @Test
    fun `normalize lowercases, drops brand parenthetical and punctuation`() {
        assertEquals("oats", GroceryFoodMatcher.normalize("Oats (Quaker)"))
        assertEquals("greekyogurt plain", GroceryFoodMatcher.normalize("  Greek-Yogurt,  plain! "))
        assertEquals("greek yogurt plain", GroceryFoodMatcher.normalize("  Greek Yogurt,  plain! "))
    }

    @Test
    fun `exact match is high`() {
        assertEquals(MatchConfidence.HIGH, GroceryFoodMatcher.confidence("Chicken Breast", "chicken breast"))
    }

    @Test
    fun `token subset is medium`() {
        assertEquals(MatchConfidence.MEDIUM, GroceryFoodMatcher.confidence("chicken", "Chicken breast"))
        assertEquals(MatchConfidence.MEDIUM, GroceryFoodMatcher.confidence("greek yogurt", "greek yogurt plain"))
    }

    @Test
    fun `jaccard thresholds`() {
        // 3 of 4 tokens overlap → 0.75 → high
        assertEquals(MatchConfidence.HIGH, GroceryFoodMatcher.confidence("brown rice long grain", "brown rice short grain"))
        // nothing in common → low
        assertEquals(MatchConfidence.LOW, GroceryFoodMatcher.confidence("banana", "almond milk"))
    }

    @Test
    fun `blank names are low`() {
        assertEquals(MatchConfidence.LOW, GroceryFoodMatcher.confidence("", "apple"))
        assertEquals(MatchConfidence.LOW, GroceryFoodMatcher.confidence("(brand)", "apple"))
    }
}
