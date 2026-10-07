package com.fitnesslink.fit.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of iOS `WorkoutSummaryRPETests` (FTNSSLNK-411). */
class WorkoutSummaryRPETests {

    @Test fun testRangeIsZeroToTenInWholeSteps() {
        assertEquals(0..10, SummaryRPE.range)
        assertEquals(0, SummaryRPE.clamp(-3))
        assertEquals(10, SummaryRPE.clamp(14))
        assertEquals(0, SummaryRPE.valueFromFraction(0.0))
        assertEquals(5, SummaryRPE.valueFromFraction(0.5))
        assertEquals(10, SummaryRPE.valueFromFraction(1.0))
        assertEquals(7, SummaryRPE.valueFromFraction(0.68))
        assertEquals(0.7, SummaryRPE.fraction(7), 0.0001)
    }

    @Test fun testAnchorsAndPrompt() {
        assertEquals("How did it feel?", SummaryRPE.prompt)
        assertEquals("0 = Not difficult", SummaryRPE.minAnchor)
        assertEquals("10 = Maximum difficulty", SummaryRPE.maxAnchor)
        assertEquals("Not difficult", SummaryRPE.label(0))
        assertEquals("Moderate", SummaryRPE.label(5))
        assertEquals("Very hard", SummaryRPE.label(9))
        assertEquals("Maximum difficulty", SummaryRPE.label(10))
    }

    @Test fun testUnratedIsNilLocallyAndOmittedOnTheWire() {
        assertNull(SummaryRPE.wireValue(null))
        assertNull(SummaryRPE.storedValue(null))
        assertNull(SummaryRPE.displayText(null))
    }

    @Test fun testRatedValueIsStoredAndSentWhole() {
        assertEquals(7, SummaryRPE.wireValue(7))
        assertEquals(7.0, SummaryRPE.storedValue(7)!!, 0.0001)
        assertEquals(10, SummaryRPE.wireValue(12))
    }

    @Test fun testHistoryShowsTheNumericValue() {
        assertEquals("7", SummaryRPE.displayText(7.0))
        assertEquals("7.5", SummaryRPE.displayText(7.5))
    }

    @Test fun testEditableOnceWithinTwentyFourHours() {
        val completed = 1_700_000_000_000L
        assertTrue(SummaryRPE.isEditable(completed, null, completed + 60_000))
        assertTrue(SummaryRPE.isEditable(completed, null, completed + SummaryRPE.editWindowMs))
        assertFalse("window closed", SummaryRPE.isEditable(completed, null, completed + SummaryRPE.editWindowMs + 1))
        assertFalse("already edited once", SummaryRPE.isEditable(completed, completed + 1_000, completed + 60_000))
        assertFalse("no completion date", SummaryRPE.isEditable(null, null, completed))
        assertFalse("completion in the future", SummaryRPE.isEditable(completed, null, completed - 1))
    }

    @Test fun testRpeModeForReopenedRow() {
        val completed = 1_700_000_000_000L
        fun row(rpe: Double?, editedAt: Long?) = SavedSessionRow(
            "s", "w", null, "W", completed - 1000, completed, 600, true, 1, 1, 10, 0.0, 0.0,
            rpe, editedAt, 100, null
        )
        assertEquals(SummaryRPEMode.Editable, WorkoutSummaryLayout.rpeMode(row(null, null), completed + 1_000))
        assertEquals(SummaryRPEMode.ReadOnly(7), WorkoutSummaryLayout.rpeMode(row(7.0, completed + 500), completed + 1_000))
        assertEquals(SummaryRPEMode.Hidden, WorkoutSummaryLayout.rpeMode(row(null, null), completed + SummaryRPE.editWindowMs + 1))
    }
}
