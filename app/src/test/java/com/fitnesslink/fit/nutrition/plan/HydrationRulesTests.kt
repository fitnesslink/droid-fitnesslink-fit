package com.fitnesslink.fit.nutrition.plan

import com.fitnesslink.fit.model.HydrationGoal
import com.fitnesslink.fit.model.HydrationPresets
import com.fitnesslink.fit.model.WaterTapUndoStack
import com.fitnesslink.fit.model.WaterUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HydrationRulesTests {

    @Test
    fun `unit conversion round trips through millilitres`() {
        assertEquals(236.588, WaterUnit.OZ.convert(8.0, WaterUnit.ML), 0.001)
        assertEquals(1.0, WaterUnit.ML.convert(236.588, WaterUnit.CUPS), 0.0001)
        assertEquals(8.0, WaterUnit.CUPS.convert(1.0, WaterUnit.OZ), 0.001)
        assertEquals(500.0, WaterUnit.ML.convert(500.0, WaterUnit.ML), 0.0)
        assertEquals(12.0, WaterUnit.ML.convert(WaterUnit.OZ.convert(12.0, WaterUnit.ML), WaterUnit.OZ), 1e-9)
    }

    @Test
    fun `preset ounces convert into the goal unit, millilitres rounded`() {
        assertEquals(8.0, HydrationPresets.amount(8.0, WaterUnit.OZ), 0.0)
        assertEquals(237.0, HydrationPresets.amount(8.0, WaterUnit.ML), 0.0)
        assertEquals(473.0, HydrationPresets.amount(16.0, WaterUnit.ML), 0.0)
        assertEquals(3.0, HydrationPresets.amount(24.0, WaterUnit.CUPS), 0.01)
        assertEquals(listOf(8.0, 16.0, 24.0), HydrationPresets.presetOunces)
    }

    @Test
    fun `display unit prefers the preferred unit, falls back to the goal unit`() {
        assertEquals(WaterUnit.OZ, HydrationGoal(unit = WaterUnit.OZ, preferredUnit = null).displayUnit)
        assertEquals(WaterUnit.ML, HydrationGoal(unit = WaterUnit.OZ, preferredUnit = WaterUnit.ML).displayUnit)
    }

    @Test
    fun `undo stack pops in reverse tap order for the same day`() {
        val stack = WaterTapUndoStack()
        assertFalse(stack.canUndo)
        stack.recordTap("a", "2026-10-06")
        stack.recordTap("b", "2026-10-06")
        assertTrue(stack.canUndo("2026-10-06"))
        assertFalse(stack.canUndo("2026-10-05"))
        assertEquals("b", stack.popLastTap("2026-10-06"))
        assertEquals("a", stack.popLastTap("2026-10-06"))
        assertNull(stack.popLastTap("2026-10-06"))
        assertFalse(stack.canUndo)
    }

    @Test
    fun `switching days clears the stack`() {
        val stack = WaterTapUndoStack()
        stack.recordTap("a", "2026-10-06")
        assertNull(stack.popLastTap("2026-10-07"))
        assertEquals("2026-10-07", stack.dayKey)
        assertFalse(stack.canUndo)
        stack.recordTap("c", "2026-10-07")
        stack.recordTap("d", "2026-10-08") // new day resets before recording
        assertEquals("d", stack.popLastTap("2026-10-08"))
        assertNull(stack.popLastTap("2026-10-08"))
    }

    @Test
    fun `clear empties everything`() {
        val stack = WaterTapUndoStack()
        stack.recordTap("a", "2026-10-06")
        stack.clear()
        assertFalse(stack.canUndo); assertEquals("", stack.dayKey)
    }
}
