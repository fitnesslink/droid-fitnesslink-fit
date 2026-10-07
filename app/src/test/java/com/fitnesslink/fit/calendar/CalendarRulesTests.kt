package com.fitnesslink.fit.calendar

import com.fitnesslink.fit.model.FitnessContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CalendarRulesTests {

    private fun localMillis(y: Int, m1: Int, d: Int, h: Int = 7, min: Int = 30): Long = Calendar.getInstance().apply {
        clear(); set(y, m1 - 1, d, h, min, 0)
    }.timeInMillis

    // MARK: - tapRoute

    @Test
    fun `completed entry with a day routes to the workout report for that day`() {
        val entry = FitnessContent(id = "e1", workoutId = "w-123", status = "completed", scheduledDate = localMillis(2026, 10, 6))
        assertEquals("completedWorkoutReport/w-123/2026-10-06", entry.tapRoute())
    }

    @Test
    fun `scheduled entry routes to workout detail`() {
        val entry = FitnessContent(id = "e1", workoutId = "w-123", status = "scheduled", scheduledDate = localMillis(2026, 10, 6))
        assertEquals("workoutDetail/w-123", entry.tapRoute())
    }

    @Test
    fun `completed entry without a date falls back to workout detail`() {
        val entry = FitnessContent(id = "e1", workoutId = "w-123", status = "Completed", scheduledDate = null)
        assertEquals("workoutDetail/w-123", entry.tapRoute())
    }

    // MARK: - Duration chips

    @Test
    fun `presets alone when there is no estimate`() {
        assertEquals(listOf(15, 30, 45, 60, 75, 90), CalendarDurationChips.options(null))
        assertEquals(listOf(15, 30, 45, 60, 75, 90), CalendarDurationChips.options(0))
    }

    @Test
    fun `estimate already a preset is not duplicated`() {
        assertEquals(listOf(15, 30, 45, 60, 75, 90), CalendarDurationChips.options(45))
    }

    @Test
    fun `odd estimate is merged in sorted order and labelled est`() {
        assertEquals(listOf(15, 30, 38, 45, 60, 75, 90), CalendarDurationChips.options(38))
        assertEquals("38 min (est.)", CalendarDurationChips.label(38, 38))
        assertEquals("45 min", CalendarDurationChips.label(45, 38))
        assertEquals("45 min", CalendarDurationChips.label(45, 45))
    }

    @Test
    fun `estimated minutes parses display time strings`() {
        assertEquals(30, CalendarDurationChips.estimatedMinutes("30 min"))
        assertEquals(90, CalendarDurationChips.estimatedMinutes("1 hr 30 min"))
        assertEquals(60, CalendarDurationChips.estimatedMinutes("1 hr"))
        assertNull(CalendarDurationChips.estimatedMinutes(""))
        assertNull(CalendarDurationChips.estimatedMinutes("~38 min"))
        assertNull(CalendarDurationChips.estimatedMinutes("soon"))
    }

    @Test
    fun `resolved duration prefers the entry then the workout time then the content estimate`() {
        val base = FitnessContent(id = "e", workoutId = "w", scheduledDate = 0L)
        assertEquals(50, CalendarDurationChips.resolvedDurationMinutes(base.copy(durationMinutes = 50), "30 min", 20))
        assertEquals(30, CalendarDurationChips.resolvedDurationMinutes(base, "30 min", 20))
        assertEquals(20, CalendarDurationChips.resolvedDurationMinutes(base, "", 20))
        assertNull(CalendarDurationChips.resolvedDurationMinutes(base, null, null))
        assertNull(CalendarDurationChips.resolvedDurationMinutes(base.copy(workoutId = ""), "30 min", 20))
    }

    // MARK: - Delete scope

    @Test
    fun `standalone workout gets the single remove action`() {
        val entry = FitnessContent(id = "e", workoutId = "w", programId = "")
        assertTrue(CalendarDeleteScope.forEntry(entry, emptyList(), emptyList()) is CalendarDeleteScope.Single)
    }

    @Test
    fun `program workout with an enrollment counts the enrollment run`() {
        val entry = FitnessContent(id = "e", workoutId = "w", programId = "p", programEnrollmentId = "enr")
        val run = List(12) { FitnessContent(id = "r$it", programId = "p", programEnrollmentId = "enr") }
        val legacy = List(30) { FitnessContent(id = "l$it", programId = "p") }
        val scope = CalendarDeleteScope.forEntry(entry, run, legacy)
        assertEquals(CalendarDeleteScope.ProgramChoice(12), scope)
        assertEquals(run.map { it.id }, CalendarDeleteScope.wholeProgramEntryIds(entry, run, legacy))
    }

    @Test
    fun `legacy program workout without an enrollment falls back to every row of the program`() {
        val entry = FitnessContent(id = "e", workoutId = "w", programId = "p", programEnrollmentId = null)
        val legacy = List(5) { FitnessContent(id = "l$it", programId = "p") }
        assertEquals(CalendarDeleteScope.ProgramChoice(5), CalendarDeleteScope.forEntry(entry, emptyList(), legacy))
        assertEquals(legacy.map { it.id }, CalendarDeleteScope.wholeProgramEntryIds(entry, emptyList(), legacy))
    }

    // MARK: - Pull guard

    @Test
    fun `pulled rows with a queued delete or reschedule are skipped case-insensitively`() {
        val remote = listOf(
            FitnessContent(id = "AAA", workoutId = "w"),
            FitnessContent(id = "bbb", workoutId = "w"),
            FitnessContent(id = "ccc", workoutId = "w")
        )
        val kept = CalendarPullGuard.filterPulled(remote, setOf("aaa", "CCC"))
        assertEquals(listOf("bbb"), kept.map { it.id })
    }

    @Test
    fun `no pending mutations leaves the pull untouched`() {
        val remote = listOf(FitnessContent(id = "a"), FitnessContent(id = "b"))
        assertEquals(remote, CalendarPullGuard.filterPulled(remote, emptySet()))
    }

    // MARK: - Day statuses

    @Test
    fun `completed wins over scheduled on the same day and program days are tracked`() {
        val entries = listOf(
            FitnessContent(id = "1", status = "scheduled", scheduledDate = localMillis(2026, 10, 6)),
            FitnessContent(id = "2", status = "completed", scheduledDate = localMillis(2026, 10, 6, 18)),
            FitnessContent(id = "3", status = "scheduled", scheduledDate = localMillis(2026, 10, 8), programId = "p"),
            FitnessContent(id = "4", status = "scheduled", scheduledDate = localMillis(2026, 11, 1))
        )
        val s = CalendarDayStatuses.from(entries, 2026, 10)
        assertEquals("completed", s.statusFor(6))
        assertEquals("scheduled", s.statusFor(8))
        assertEquals("", s.statusFor(1))
        assertEquals(setOf(8), s.program)
    }
}
