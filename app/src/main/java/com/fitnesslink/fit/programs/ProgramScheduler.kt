package com.fitnesslink.fit.programs

import com.fitnesslink.fit.calendar.CalendarService
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.model.UserProgram
import com.fitnesslink.fit.model.WorkoutList
import com.fitnesslink.fit.model.api.ProgramSchedule
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Port of iOS `ProgramScheduler`: lays a program's workout schedule onto
 * the calendar. The program's own week/day *numbering* is ignored — only
 * each slot's relative offset from the program's first scheduled slot
 * matters, so Mon/Tue/Wed lands on day 0/1/2 from the chosen start,
 * including across week boundaries.
 *
 * Every entry created in one run shares one enrollment id (lowercased UUID)
 * so that run can be removed / identified independently of other runs of
 * the same program (FTNSSLNK-164/165).
 */
object ProgramScheduler {

    /** One planned calendar entry before it is persisted. */
    data class PlannedEntry(
        val schedule: ProgramSchedule,
        /** Whole days after the chosen start date. */
        val offsetDays: Int,
        /** Scheduled instant: start date + offset, local midnight. */
        val dateMillis: Long
    )

    /** The anchor slot: smallest (week, day). */
    fun anchor(schedules: List<ProgramSchedule>): ProgramSchedule? =
        schedules.minWithOrNull(compareBy({ it.weekNumber }, { it.dayNumber }))

    /**
     * Pure: relative day offsets from the anchor slot for every schedule row,
     * sorted by (week, day). Empty when there are no rows.
     */
    fun plan(schedules: List<ProgramSchedule>, startDateMillis: Long): List<PlannedEntry> {
        val sorted = schedules.sortedWith(compareBy({ it.weekNumber }, { it.dayNumber }))
        val anchor = sorted.firstOrNull() ?: return emptyList()
        val start = ProgramDates.startOfDay(startDateMillis)
        return sorted.map { s ->
            val offset = (s.weekNumber - anchor.weekNumber) * 7 + (s.dayNumber - anchor.dayNumber)
            PlannedEntry(schedule = s, offsetDays = offset, dateMillis = ProgramDates.addDays(start, offset))
        }
    }

    /**
     * Pure: the calendar entry for one planned slot, stamped with the
     * program / enrollment / template week + day / image ids.
     */
    fun entry(
        planned: PlannedEntry,
        program: Program,
        enrollmentId: String,
        workout: WorkoutList?
    ): FitnessContent = FitnessContent(
        id = UUID.randomUUID().toString().lowercase(Locale.US),
        title = workout?.name ?: "Workout",
        programId = program.id.lowercase(Locale.US),
        workoutId = planned.schedule.workoutId.toString().lowercase(Locale.US),
        mealPlanId = "",
        status = "scheduled",
        scheduledDate = planned.dateMillis,
        workoutImageId = workout?.imageId,
        workoutThumbnailId = workout?.thumbnailId,
        programImageId = program.imageId?.toString(),
        programThumbnailId = program.thumbnailId?.toString(),
        programEnrollmentId = enrollmentId,
        programWeekNumber = planned.schedule.weekNumber,
        programDayNumber = planned.schedule.dayNumber
    )

    /** Pure: the `user_programs` row a start on [startDateMillis] creates. */
    fun instance(program: Program, schedules: List<ProgramSchedule>, startDateMillis: Long, enrollmentId: String, now: Long = System.currentTimeMillis()): UserProgram? {
        val anchor = anchor(schedules) ?: return null
        val weeks = maxOf(program.weeks ?: 0, schedules.maxOfOrNull { it.weekNumber } ?: 1, 1)
        return UserProgram(
            enrollmentId = enrollmentId,
            programId = program.id.lowercase(Locale.US),
            programName = program.name,
            startDate = ProgramDates.startOfDay(startDateMillis),
            weekOneStart = UserProgram.weekOneStart(startDateMillis, anchor.weekNumber, anchor.dayNumber),
            weeks = weeks,
            createdAt = now
        )
    }

    /**
     * Starts [programId] on [startDate], first removing the enrollment in
     * [replacing] (Replace Program / Restart). Returns the new enrollment
     * id. Throws [IllegalStateException] when the program (or its schedule)
     * isn't cached locally yet — callers load `GET programs/{id}` +
     * `weekly-workouts` before starting.
     */
    suspend fun addToCalendar(programId: String, startDate: Date, replacing: String? = null): String = withContext(Dispatchers.IO) {
        val program = DatabaseManager.program(programId) ?: error("Program $programId isn't cached")
        val schedules = DatabaseManager.schedulesForProgram(programId)
        check(schedules.isNotEmpty()) { "Program $programId has no schedule yet" }
        replacing?.let { UserProgramStore.remove(it) }

        val enrollmentId = UUID.randomUUID().toString().lowercase(Locale.US)
        val workouts = DatabaseManager.allWorkouts().associateBy { it.id.lowercase(Locale.US) }
        val planned = plan(schedules, startDate.time)
        var scheduled = 0
        for (slot in planned) {
            val workout = workouts[slot.schedule.workoutId.toString().lowercase(Locale.US)]
            val entry = entry(slot, program, enrollmentId, workout)
            // Local insert + device mirror + queued POST /calendar.
            CalendarService.create(entry)
            scheduled++
        }

        // Contributor-payout record (durably queued).
        ProgramEnrollmentService.recordAdd(enrollmentId, program, startDate, scheduled)

        // The program instance fixes the week boundaries for My Program.
        instance(program, schedules, startDate.time, enrollmentId)?.let { DatabaseManager.upsertUserProgram(it) }
        UserProgramStore.notifyChanged()
        enrollmentId
    }
}
