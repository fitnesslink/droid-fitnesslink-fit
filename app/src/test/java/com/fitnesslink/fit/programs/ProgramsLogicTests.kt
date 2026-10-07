package com.fitnesslink.fit.programs

import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.model.ProgramJourneyState
import com.fitnesslink.fit.model.ProgramSnapshot
import com.fitnesslink.fit.model.UserProgram
import com.fitnesslink.fit.model.WorkoutList
import com.fitnesslink.fit.model.api.ProgramSchedule
import com.fitnesslink.fit.ui.programs.StartProgramPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.UUID

/** Pure-logic coverage for the Programs port: active() precedence, scheduler offsets / stamping, CTA state machine, timeline ranges. */
class ProgramsLogicTests {

    private fun day(year: Int, month: Int, dayOfMonth: Int): Long = Calendar.getInstance().apply {
        clear(); set(year, month - 1, dayOfMonth)
    }.timeInMillis

    private val today = day(2026, 10, 7)

    private fun instance(id: String, start: Long, weeks: Int, createdAt: Long = start) = UserProgram(
        enrollmentId = id, programId = "p-$id", programName = "P $id",
        startDate = start, weekOneStart = start, weeks = weeks, createdAt = createdAt
    )

    private fun entry(enrollmentId: String, date: Long, completed: Boolean = false, workoutId: String = UUID.randomUUID().toString(), week: Int? = null, day: Int? = null) =
        FitnessContent(
            id = UUID.randomUUID().toString(), title = "W", programId = "p", workoutId = workoutId,
            status = if (completed) "completed" else "scheduled", scheduledDate = date,
            programEnrollmentId = enrollmentId, programWeekNumber = week, programDayNumber = day
        )

    private fun snapshot(program: UserProgram, entries: List<FitnessContent>, now: Long = today) =
        ProgramSnapshot.build(program, entries, explicitPhases = emptyList(), now = now)

    // MARK: active() precedence

    @Test
    fun `active prefers the running program over an upcoming one`() {
        val running = instance("running", start = ProgramDates.addDays(today, -3), weeks = 4)
        val upcoming = instance("upcoming", start = ProgramDates.addDays(today, 5), weeks = 4, createdAt = today)
        val snaps = listOf(
            snapshot(running, listOf(entry("running", ProgramDates.addDays(today, -3)))),
            snapshot(upcoming, listOf(entry("upcoming", ProgramDates.addDays(today, 5))))
        )
        assertEquals("running", UserProgramStore.pickActive(snaps, today)?.enrollmentId)
    }

    @Test
    fun `active falls back to the next upcoming program`() {
        val later = instance("later", start = ProgramDates.addDays(today, 10), weeks = 4)
        val sooner = instance("sooner", start = ProgramDates.addDays(today, 2), weeks = 4)
        val snaps = listOf(
            snapshot(later, listOf(entry("later", ProgramDates.addDays(today, 10)))),
            snapshot(sooner, listOf(entry("sooner", ProgramDates.addDays(today, 2))))
        )
        assertEquals("sooner", UserProgramStore.pickActive(snaps, today)?.enrollmentId)
    }

    @Test
    fun `active falls back to the latest open program when nothing is running or upcoming`() {
        val old = instance("old", start = ProgramDates.addDays(today, -60), weeks = 2)
        val newer = instance("newer", start = ProgramDates.addDays(today, -40), weeks = 2, createdAt = ProgramDates.addDays(today, -40))
        val snaps = listOf(
            snapshot(old, listOf(entry("old", ProgramDates.addDays(today, -60)))),
            snapshot(newer, listOf(entry("newer", ProgramDates.addDays(today, -40))))
        )
        assertEquals("newer", UserProgramStore.pickActive(snaps, today)?.enrollmentId)
    }

    @Test
    fun `active keeps a program completed within 14 days and drops older ones`() {
        val recentStart = ProgramDates.addDays(today, -10)
        val recent = instance("recent", start = recentStart, weeks = 1)
        val recentSnap = snapshot(recent, listOf(entry("recent", recentStart, completed = true)))
        assertEquals(ProgramJourneyState.COMPLETED, recentSnap.state)
        assertEquals("recent", UserProgramStore.pickActive(listOf(recentSnap), today)?.enrollmentId)

        val staleStart = ProgramDates.addDays(today, -40)
        val stale = instance("stale", start = staleStart, weeks = 1)
        val staleSnap = snapshot(stale, listOf(entry("stale", staleStart, completed = true)))
        assertNull(UserProgramStore.pickActive(listOf(staleSnap), today))
    }

    // MARK: Scheduler

    private fun schedule(week: Int, day: Int, workoutId: UUID = UUID.randomUUID()) = ProgramSchedule(
        id = UUID.randomUUID(), programId = UUID.randomUUID(), workoutId = workoutId, weekNumber = week, dayNumber = day
    )

    @Test
    fun `scheduler offsets are relative to the first slot and cross week boundaries`() {
        // Mon/Wed of week 1 (1 = Sunday), Tue of week 2.
        val rows = listOf(schedule(2, 3), schedule(1, 2), schedule(1, 4))
        val planned = ProgramScheduler.plan(rows, today)
        assertEquals(listOf(0, 2, 8), planned.map { it.offsetDays })
        assertEquals(today, planned[0].dateMillis)
        assertEquals(ProgramDates.addDays(today, 8), planned[2].dateMillis)
    }

    @Test
    fun `scheduler stamps program, enrollment, template week and day and image ids`() {
        val workoutId = UUID.randomUUID()
        val program = Program(id = "ABC", name = "Base", imageId = UUID.randomUUID(), thumbnailId = UUID.randomUUID(), weeks = 2)
        val planned = ProgramScheduler.plan(listOf(schedule(2, 5, workoutId)), today).single()
        val workout = WorkoutList(id = workoutId.toString(), name = "Legs", time = "30 min", isFavorite = false, imageId = "img", thumbnailId = "thumb")
        val entry = ProgramScheduler.entry(planned, program, "enroll-1", workout)

        assertEquals("abc", entry.programId)
        assertEquals("enroll-1", entry.programEnrollmentId)
        assertEquals(2, entry.programWeekNumber)
        assertEquals(5, entry.programDayNumber)
        assertEquals(workoutId.toString().lowercase(), entry.workoutId)
        assertEquals("Legs", entry.title)
        assertEquals("thumb", entry.workoutThumbnailId)
        assertEquals(program.thumbnailId.toString(), entry.programThumbnailId)
        assertEquals("scheduled", entry.status)
        assertEquals(today, entry.scheduledDate)
    }

    @Test
    fun `scheduler instance backs week one out from the anchor slot`() {
        // First slot is week 1 / day 3 → week one starts two days before the chosen start.
        val program = Program(id = "p", name = "P", weeks = 3)
        val rows = listOf(schedule(1, 3), schedule(2, 1))
        val instance = ProgramScheduler.instance(program, rows, today, "e1")
        assertNotNull(instance)
        assertEquals(ProgramDates.addDays(today, -2), instance!!.weekOneStart)
        assertEquals(today, instance.startDate)
        assertEquals(3, instance.weeks)
    }

    // MARK: CTA state machine

    @Test
    fun `cta is Start with no instance`() {
        assertEquals(ProgramCTA.Start, ProgramCTA.from(null))
        assertEquals("startProgram/p1", ProgramCTA.Start.route("p1"))
    }

    @Test
    fun `cta is View Results when every workout is complete`() {
        val program = instance("e", start = ProgramDates.addDays(today, -7), weeks = 1)
        val snap = snapshot(program, listOf(entry("e", ProgramDates.addDays(today, -7), completed = true)))
        val cta = ProgramCTA.from(snap, today)
        assertEquals(ProgramCTA.ViewResults("e"), cta)
        assertEquals("programProgress?enrollmentId=e", cta.route("p"))
    }

    @Test
    fun `cta is Start Todays Workout when an open workout is scheduled today`() {
        val program = instance("e", start = ProgramDates.addDays(today, -1), weeks = 1)
        val snap = snapshot(program, listOf(entry("e", ProgramDates.addDays(today, -1), completed = true), entry("e", today, workoutId = "w-today")))
        val cta = ProgramCTA.from(snap, today)
        assertEquals(ProgramCTA.StartTodaysWorkout("w-today", "e"), cta)
        assertEquals("workoutDetail/w-today", cta.route("p"))
    }

    @Test
    fun `cta is View My Program when started but nothing is due today`() {
        val program = instance("e", start = ProgramDates.addDays(today, -1), weeks = 1)
        val snap = snapshot(program, listOf(entry("e", ProgramDates.addDays(today, -1)), entry("e", ProgramDates.addDays(today, 2))))
        val cta = ProgramCTA.from(snap, today)
        assertEquals(ProgramCTA.ViewMyProgram("e"), cta)
        assertEquals("myProgram?enrollmentId=e", cta.route("p"))
    }

    // MARK: Timeline week ranges

    @Test
    fun `timeline week ranges start at the chosen date and run seven days apart`() {
        val program = Program(id = "p", name = "P", weeks = 3)
        val rows = listOf(schedule(1, 1), schedule(1, 3), schedule(3, 2))
        val preview = StartProgramPreview.instance(program, "p", rows, today)
        assertEquals(3, preview.weeks)
        assertEquals(today, preview.weekOneStart)
        assertEquals(ProgramDates.addDays(today, 7), preview.weekStart(2))
        assertEquals(ProgramDates.addDays(today, 20), preview.weekEnd(3))
        val label = preview.weekRangeLabel(1)
        assertTrue(label, label.contains(" – "))
    }

    @Test
    fun `timeline week one never shows a date before the chosen start`() {
        // Anchor slot is week 1 / day 4 → week one technically starts three days before the start date.
        val program = Program(id = "p", name = "P", weeks = 2)
        val preview = StartProgramPreview.instance(program, "p", listOf(schedule(1, 4)), today)
        assertEquals(ProgramDates.addDays(today, -3), preview.weekOneStart)
        val expectedStart = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(today))
        assertTrue(preview.weekRangeLabel(1).startsWith(expectedStart))
        assertEquals(2, StartProgramPreview.totalWeeks(program, listOf(schedule(1, 4))))
        assertEquals(1, StartProgramPreview.workoutsPerWeek(listOf(schedule(1, 4), schedule(2, 4)), 2))
    }

    // MARK: Move keeps time of day

    @Test
    fun `move target keeps the original time of day`() {
        val current = Calendar.getInstance().apply { timeInMillis = today; set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 30) }.timeInMillis
        val newDay = ProgramDates.addDays(today, 3)
        val target = UserProgramStore.targetInstant(newDay, current)
        val cal = Calendar.getInstance().apply { timeInMillis = target }
        assertEquals(18, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertEquals(ProgramDates.startOfDay(newDay), ProgramDates.startOfDay(target))
    }
}
