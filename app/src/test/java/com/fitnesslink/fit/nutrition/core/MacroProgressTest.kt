package com.fitnesslink.fit.nutrition.core

import com.fitnesslink.fit.model.MacroProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of the iOS `MacroProgress` over-target rules (FTNSSLNK-194). */
class MacroProgressTest {

    @Test fun `under target keeps remaining positive and bar partial`() {
        val line = MacroProgress(goal = 2000.0, consumed = 1500.0)
        assertEquals(500.0, line.remaining!!, 0.001)
        assertFalse(line.isOver)
        assertEquals(0.0, line.overAmount, 0.001)
        assertEquals(0.75, line.fraction, 0.001)
        assertEquals(0.75, line.rawFraction, 0.001)
    }

    @Test fun `over target is a signed remaining, capped bar and uncapped raw fraction`() {
        val line = MacroProgress(goal = 2000.0, consumed = 2300.0)
        assertEquals(-300.0, line.remaining!!, 0.001)
        assertTrue(line.isOver)
        assertEquals(300.0, line.overAmount, 0.001)
        assertEquals(1.0, line.fraction, 0.001)
        assertEquals(1.15, line.rawFraction, 0.001)
    }

    @Test fun `exactly at target is not over`() {
        val line = MacroProgress(goal = 150.0, consumed = 150.0)
        assertFalse(line.isOver)
        assertEquals(0.0, line.remaining!!, 0.001)
        assertEquals(1.0, line.fraction, 0.001)
    }

    @Test fun `no goal renders unfilled with no remaining`() {
        val line = MacroProgress(goal = null, consumed = 80.0)
        assertNull(line.remaining)
        assertFalse(line.isOver)
        assertEquals(0.0, line.fraction, 0.001)
        assertEquals(0.0, line.rawFraction, 0.001)
    }

    @Test fun `zero goal is treated like no goal for the bar`() {
        val line = MacroProgress(goal = 0.0, consumed = 80.0)
        assertEquals(0.0, line.fraction, 0.001)
        assertTrue(line.isOver) // remaining is -80: still a signal
    }
}
