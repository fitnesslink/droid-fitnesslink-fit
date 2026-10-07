package com.fitnesslink.fit.home

import com.fitnesslink.fit.model.DateStripBuilder
import com.fitnesslink.fit.model.DateStripEntry
import com.fitnesslink.fit.model.DateStripStatus
import com.fitnesslink.fit.model.HomeDateItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/** Port of the date-strip half of iOS `HomeViewModel.decorateCalendarItems`. */
class DateStripStatusTest {

    @Test fun `completed only when every entry on the day is done`() {
        assertEquals(DateStripStatus.COMPLETED, DateStripStatus.derive(listOf(true, true), inProgramSpan = false))
        assertEquals(DateStripStatus.SCHEDULED, DateStripStatus.derive(listOf(true, false), inProgramSpan = false))
        assertEquals(DateStripStatus.SCHEDULED, DateStripStatus.derive(listOf(false), inProgramSpan = false))
    }

    @Test fun `entries win over program span`() {
        assertEquals(DateStripStatus.SCHEDULED, DateStripStatus.derive(listOf(false), inProgramSpan = true))
        assertEquals(DateStripStatus.COMPLETED, DateStripStatus.derive(listOf(true), inProgramSpan = true))
    }

    @Test fun `empty day inside a program span is rest, outside is nothing`() {
        assertEquals(DateStripStatus.REST, DateStripStatus.derive(emptyList(), inProgramSpan = true))
        assertEquals(DateStripStatus.NONE, DateStripStatus.derive(emptyList(), inProgramSpan = false))
    }

    @Test fun `builder stamps one cell per day with status title and selection`() {
        // October 2026 has 31 days.
        val items = DateStripBuilder.build(
            year = 2026, monthZeroBased = Calendar.OCTOBER, selectedDay = 6,
            entries = listOf(
                DateStripEntry(3, "Push Day", isCompleted = true),
                DateStripEntry(6, "Legs", isCompleted = false),
                DateStripEntry(6, "Core", isCompleted = true)
            ),
            programSpanDays = setOf(1, 2, 3, 4, 5, 6, 7),
            locale = Locale.US
        )
        assertEquals(31, items.size)
        assertEquals(listOf(6), items.filter { it.selected }.map { it.dayNumber })

        val day3 = items.first { it.dayNumber == 3 }
        assertEquals(DateStripStatus.COMPLETED, day3.status)
        assertEquals("Push Day", day3.title)
        assertEquals("Sat", day3.weekDay)

        val day6 = items.first { it.dayNumber == 6 }
        assertEquals(DateStripStatus.SCHEDULED, day6.status)
        assertEquals("Legs", day6.title)

        val day4 = items.first { it.dayNumber == 4 }
        assertEquals(DateStripStatus.REST, day4.status)
        assertEquals("Rest", day4.title)

        val day20 = items.first { it.dayNumber == 20 }
        assertEquals(DateStripStatus.NONE, day20.status)
        assertEquals("", day20.title)
    }

    @Test fun `program span days are inclusive and compare the start at local midnight`() {
        val cal = Calendar.getInstance()
        cal.clear(); cal.set(2026, Calendar.OCTOBER, 5, 14, 30) // span starts mid-afternoon on the 5th
        val start = cal.timeInMillis
        cal.clear(); cal.set(2026, Calendar.OCTOBER, 9, 7, 0)
        val end = cal.timeInMillis
        val days = DateStripBuilder.programSpanDays(2026, Calendar.OCTOBER, start to end)
        assertEquals(setOf(5, 6, 7, 8, 9), days)
        assertTrue(DateStripBuilder.programSpanDays(2026, Calendar.OCTOBER, null).isEmpty())
        // A span entirely in another month covers nothing here.
        cal.clear(); cal.set(2026, Calendar.NOVEMBER, 1); val novStart = cal.timeInMillis
        cal.clear(); cal.set(2026, Calendar.NOVEMBER, 10); val novEnd = cal.timeInMillis
        assertTrue(DateStripBuilder.programSpanDays(2026, Calendar.OCTOBER, novStart to novEnd).isEmpty())
    }

    @Test fun `accessibility label carries weekday day title and completion`() {
        val done = HomeDateItem(dayNumber = 3, name = "3", weekDay = "Sat", status = DateStripStatus.COMPLETED, title = "Push Day")
        assertEquals("Sat 3, Push Day, completed", done.accessibilityLabel)
        val empty = HomeDateItem(dayNumber = 20, name = "20", weekDay = "Tue")
        assertEquals("Tue 20", empty.accessibilityLabel)
        assertFalse(empty.selected)
    }
}
