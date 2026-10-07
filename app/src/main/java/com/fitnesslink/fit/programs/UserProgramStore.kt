package com.fitnesslink.fit.programs

import android.util.Log
import com.fitnesslink.fit.calendar.CalendarService
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.model.ProgramJourneyState
import com.fitnesslink.fit.model.ProgramSnapshot
import com.fitnesslink.fit.model.UserProgram
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.calendarContentByEnrollment
import com.fitnesslink.fit.persistence.openEnrollmentEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * Port of iOS `UserProgramStore`: the user's program instances and
 * everything that changes them — start (via [ProgramScheduler]), replace,
 * restart, remove, move a workout. FitnessLink runs one active program at
 * a time (PRD §44). Calendar entries stay the single schedule both My
 * Program and the Calendar tab read, so the two can't disagree (PRD §34).
 */
object UserProgramStore {
    private const val TAG = "UserProgramStore"

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    /** Emits after any local mutation (start / remove / move / server sync). */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    internal fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

    private fun lc(s: String) = s.lowercase(Locale.US)

    /**
     * Every program instance that still has workouts on the calendar,
     * oldest first. Rows whose entries are gone are dropped; enrollments
     * scheduled before My Program existed (or pulled onto a fresh install)
     * get their row derived from the entries.
     */
    fun all(): List<UserProgram> {
        val groups = DatabaseManager.calendarContentByEnrollment()
        val rows = mutableListOf<UserProgram>()
        val known = mutableSetOf<String>()
        for (row in DatabaseManager.userPrograms()) {
            val id = lc(row.enrollmentId)
            if (groups[id] == null) {
                DatabaseManager.deleteUserProgram(id)
            } else {
                rows.add(row)
                known.add(id)
            }
        }
        for ((enrollmentId, entries) in groups) {
            if (enrollmentId in known) continue
            derive(enrollmentId, entries)?.let {
                DatabaseManager.upsertUserProgram(it)
                rows.add(it)
            }
        }
        return rows.sortedBy { it.createdAt }
    }

    private fun derive(enrollmentId: String, entries: List<FitnessContent>, startDate: Long? = null): UserProgram? {
        val dates = entries.mapNotNull { it.scheduledDate }
        val first = dates.minOrNull() ?: return null
        val last = dates.maxOrNull() ?: return null
        val programId = entries.firstOrNull { it.programId.isNotEmpty() }?.programId ?: return null
        val start = ProgramDates.startOfDay(startDate ?: first)
        val anchor = ProgramScheduler.anchor(DatabaseManager.schedulesForProgram(programId))
        val weekOne = anchor?.let { UserProgram.weekOneStart(start, it.weekNumber, it.dayNumber) } ?: start
        val cached = DatabaseManager.program(programId)
        val spanDays = ProgramDates.daysBetween(weekOne, last) + 1
        val weeks = maxOf(
            cached?.weeks ?: 0,
            entries.mapNotNull { it.programWeekNumber }.maxOrNull() ?: 0,
            ceil(spanDays / 7.0).toInt(),
            1
        )
        return UserProgram(
            enrollmentId = enrollmentId,
            programId = lc(programId),
            programName = cached?.name ?: "Program",
            startDate = start,
            weekOneStart = weekOne,
            weeks = weeks,
            createdAt = start
        )
    }

    fun program(enrollmentId: String): UserProgram? = all().firstOrNull { lc(it.enrollmentId) == lc(enrollmentId) }

    /** Latest instance of one catalog program, if the user has started it. */
    fun instance(ofProgramId: String): UserProgram? = all().lastOrNull { lc(it.programId) == lc(ofProgramId) }

    /**
     * The program My Program and Home show: an unfinished one whose dates
     * include today, else the next to start, else the most recent open one.
     * With nothing unfinished, a program completed in the last two weeks
     * stays up so its completion state is visible.
     */
    fun active(now: Long = System.currentTimeMillis()): UserProgram? =
        pickActive(all().map { snapshot(it, now) }, now)

    /** Pure precedence rule behind [active] — running → next upcoming → latest open → completed ≤14 d. */
    fun pickActive(snapshots: List<ProgramSnapshot>, now: Long = System.currentTimeMillis()): UserProgram? {
        val today = ProgramDates.startOfDay(now)
        val open = snapshots.filter { it.state != ProgramJourneyState.COMPLETED }
        open.lastOrNull { it.program.startDate <= today && it.program.endDate() >= today }?.let { return it.program }
        open.filter { it.program.startDate > today }.minByOrNull { it.program.startDate }?.let { return it.program }
        open.lastOrNull()?.let { return it.program }
        val recentCutoff = ProgramDates.addDays(today, -14)
        return snapshots.lastOrNull { it.program.endDate() >= recentCutoff }?.program
    }

    fun snapshot(program: UserProgram, now: Long = System.currentTimeMillis()): ProgramSnapshot {
        val cached = DatabaseManager.program(program.programId)
        return ProgramSnapshot.build(
            program = program,
            entries = DatabaseManager.calendarContentForEnrollment(program.enrollmentId),
            explicitPhases = cached?.weekPhases ?: emptyList(),
            now = now
        )
    }

    // MARK: Remove

    /**
     * Removes the instance: its workouts leave the calendar (completed
     * sessions stay in workout history) and the removal is recorded on the
     * enrollment history.
     */
    suspend fun remove(enrollmentId: String) {
        withContext(Dispatchers.IO) {
            for (entry in DatabaseManager.calendarContentForEnrollment(enrollmentId)) {
                runCatching { CalendarService.remove(entry.id) }
                    .onFailure { Log.w(TAG, "remove entry ${entry.id} failed", it) }
            }
            ProgramEnrollmentService.recordRemoval(enrollmentId)
            DatabaseManager.deleteUserProgram(enrollmentId)
        }
        notifyChanged()
    }

    // MARK: Move workout

    /** Another workout already on [day] (PRD §33), ignoring [excludingEntryId] itself. */
    fun conflict(day: Long, excludingEntryId: String): FitnessContent? =
        DatabaseManager.calendarContent(forDate = day).firstOrNull {
            it.workoutId.isNotEmpty() && lc(it.id) != lc(excludingEntryId)
        }

    /** Pure: [day]'s local midnight plus the time of day of [currentScheduled] (or midnight when null). */
    fun targetInstant(day: Long, currentScheduled: Long?): Long {
        val target = ProgramDates.startOfDay(day)
        if (currentScheduled == null) return target
        val cal = Calendar.getInstance().apply { timeInMillis = currentScheduled }
        return Calendar.getInstance().apply {
            timeInMillis = target
            set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
            set(Calendar.SECOND, cal.get(Calendar.SECOND))
        }.timeInMillis
    }

    /**
     * Moves one workout occurrence to [newDate]'s day, keeping its time of
     * day. Only that entry's date changes — its program week and every
     * other workout stay put. Goes through `CalendarService.reschedule`, so
     * the Calendar tab, the device mirror and the server all follow.
     */
    suspend fun move(entryId: String, newDate: Date) {
        val current = withContext(Dispatchers.IO) { DatabaseManager.calendarContentById(entryId)?.scheduledDate }
        val target = targetInstant(newDate.time, current)
        CalendarService.reschedule(entryId, Date(target))
        notifyChanged()
    }

    // MARK: Completion from any day

    /**
     * The entry a finished session of [workoutId] should complete when
     * nothing for that workout is scheduled today: the earliest still-open
     * occurrence in the active program (the user did Tuesday's workout on
     * Monday). It is completed where it was actually done — today.
     */
    fun openProgramEntry(workoutId: String): FitnessContent? {
        val active = active() ?: return null
        return DatabaseManager.openEnrollmentEntries(active.enrollmentId, workoutId).firstOrNull()
    }

    // MARK: Server

    /**
     * Fresh install / second device: the day- and month-scoped calendar
     * pulls never fetch a whole program, so pull each active enrollment's
     * full date range and (re)build its instance row from the server's
     * start date. Returns true when anything changed locally.
     */
    suspend fun syncFromServer(): Boolean {
        if (!NetworkMonitor.isConnected.value) return false
        ProgramEnrollmentService.ensureRegistered()
        val remote = withContext(Dispatchers.IO) { runCatching { ApiClient.programApi.myEnrollments() }.getOrNull() } ?: return false
        val known = withContext(Dispatchers.IO) { DatabaseManager.userPrograms().map { lc(it.enrollmentId) }.toSet() }
        var changed = false
        for (enrollment in remote) {
            if (!enrollment.status.equals("active", ignoreCase = true)) continue
            val id = lc(enrollment.enrollmentId)
            if (id in known) continue
            val startDay = ProgramDates.startOfDay(enrollment.startDate.time)
            val from = ProgramDates.addDays(startDay, -7)
            val spanDays = ((enrollment.weeks ?: 12) + 4) * 7
            val to = ProgramDates.addDays(from, spanDays)
            // GET /calendar/me for the range → local rows (pending local mutations are skipped by the service).
            val ok = runCatching { CalendarService.refresh(Date(from), Date(to)) }.isSuccess
            if (!ok) continue
            val mine = withContext(Dispatchers.IO) { DatabaseManager.calendarContentForEnrollment(id) }
            if (mine.isEmpty()) continue
            withContext(Dispatchers.IO) {
                derive(id, mine, startDate = enrollment.startDate.time)?.let { row ->
                    val named = enrollment.programName?.takeIf { it.isNotEmpty() }?.let { row.copy(programName = it) } ?: row
                    DatabaseManager.upsertUserProgram(named)
                }
            }
            changed = true
        }
        if (changed) notifyChanged()
        return changed
    }
}
