package com.fitnesslink.fit.nutrition.logging

import com.fitnesslink.fit.network.dto.IdentifiedFood
import com.fitnesslink.fit.ui.nutrition.ConfirmLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Snap a Meal confirm lines (FTNSSLNK-493): what the estimate reads once the user confirms. */
class SnapMealLinesTest {

    @Test fun `describe keeps the portion attached to its food`() {
        val line = ConfirmLine.from(IdentifiedFood(name = "Grilled chicken breast", portionDescription = "about 6 oz", confidence = "high"))
        assertEquals("Grilled chicken breast (about 6 oz)", line.describe)
        assertEquals("portions with commas stay attached when lines are joined",
            "Chicken (1 piece, about 3-4 oz); Rice (1 cup)",
            listOf(ConfirmLine.added("Chicken", "1 piece, about 3-4 oz"), ConfirmLine.added("Rice", "1 cup")).joinToString("; ") { it.describe })
    }

    @Test fun `blank portion describes the food alone and blank name is invalid`() {
        assertEquals("Butter", ConfirmLine.added(" Butter ", "  ").describe)
        assertFalse(ConfirmLine.added("", "1 tbsp").isValid)
        assertTrue(ConfirmLine.added("Butter", "").isValid)
        assertEquals("added", ConfirmLine.added("Sauce", "2 tbsp").confidence)
    }
}
