package com.fitnesslink.fit.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.roundToInt

// MARK: - Program instance

/**
 * One run of a program the user started ("User Program Instance", Programs
 * PRD §46). The calendar entries sharing [enrollmentId] are its schedule;
 * this row fixes the week boundaries, which never move when an individual
 * workout is rescheduled (PRD §48). Persisted in `user_programs`.
 */
data class UserProgram(
    val enrollmentId: String,
    val programId: String,
    val programName: String,
    /** The day the user chose to begin (the first workout's date), local midnight millis. */
    val startDate: Long,
    /** The day template Week 1 / Day 1 falls on. Equals [startDate] unless the first workout isn't day 1 of week 1. */
    val weekOneStart: Long,
    val weeks: Int,
    val createdAt: Long
) {
    val id: String get() = enrollmentId

    fun weekStart(week: Int): Long = ProgramDates.addDays(weekOneStart, (week - 1) * 7)
    fun weekEnd(week: Int): Long = ProgramDates.addDays(weekStart(week), 6)

    /** Last day of the final week. */
    fun endDate(): Long = weekEnd(maxOf(weeks, 1))

    /** The program week [date] falls in, clamped to 1…weeks. */
    fun weekNumber(containing: Long): Int {
        val days = ProgramDates.daysBetween(weekOneStart, ProgramDates.startOfDay(containing))
        val week = floor(days / 7.0).toInt() + 1
        return minOf(maxOf(week, 1), maxOf(weeks, 1))
    }

    /** "Sep 29 – Oct 5". Week 1 never shows a date before the chosen start. */
    fun weekRangeLabel(week: Int): String {
        val start = if (week == 1) maxOf(weekStart(1), ProgramDates.startOfDay(startDate)) else weekStart(week)
        val f = SimpleDateFormat("MMM d", Locale.getDefault())
        return "${f.format(Date(start))} – ${f.format(Date(weekEnd(week)))}"
    }

    companion object {
        /**
         * The scheduler lands the program's first scheduled slot on the chosen
         * start date; back out where Week 1 / Day 1 sits from there.
         */
        fun weekOneStart(startDate: Long, anchorWeek: Int, anchorDay: Int): Long {
            val offset = (anchorWeek - 1) * 7 + (anchorDay - 1)
            return ProgramDates.addDays(ProgramDates.startOfDay(startDate), -offset)
        }
    }
}

/** Local-midnight day math shared by the program models (default calendar / time zone). */
object ProgramDates {
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun addDays(millis: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        add(Calendar.DAY_OF_MONTH, days)
    }.timeInMillis

    /** Whole local days from [from] to [to] (negative when [to] precedes [from]). */
    fun daysBetween(from: Long, to: Long): Int {
        val a = startOfDay(from)
        val b = startOfDay(to)
        // Round to absorb DST shifts (23 / 25 h days).
        return ((b - a).toDouble() / TimeUnit.DAYS.toMillis(1)).roundToInt()
    }

    fun isSameDay(a: Long, b: Long): Boolean = startOfDay(a) == startOfDay(b)
}

/**
 * Phase name for a program week: the contributor's explicit per-week names
 * when the program has them, else the Base / Build / Peak thirds (hidden
 * under 3 weeks).
 */
object ProgramPhases {
    fun name(week: Int, totalWeeks: Int, explicit: List<String>): String? {
        if (week >= 1 && week <= explicit.size) {
            val name = explicit[week - 1].trim()
            if (name.isNotEmpty()) return name
        }
        if (!explicit.all { it.trim().isEmpty() } || totalWeeks < 3) return null
        val baseEnd = (totalWeeks + 2) / 3
        val buildEnd = (2 * totalWeeks + 2) / 3
        return when {
            week <= baseEnd -> "Base"
            week <= buildEnd -> "Build"
            else -> "Peak"
        }
    }
}

// MARK: - Snapshot (week plan + progress)

data class ProgramDayPlan(
    /** Local midnight millis. */
    val date: Long,
    val entries: List<FitnessContent>
) {
    val id: Long get() = date
    val isRest: Boolean get() = entries.isEmpty()
}

data class ProgramWeekPlan(
    val weekNumber: Int,
    val rangeLabel: String,
    val phase: String?,
    val days: List<ProgramDayPlan>
) {
    val id: Int get() = weekNumber
    val total: Int get() = days.sumOf { it.entries.size }
    val completed: Int get() = days.sumOf { d -> d.entries.count { it.isCompleted } }
    val percent: Int get() = ProgramSnapshot.percent(completed, total)
    /** A week counts as completed only when every workout in it is done (PRD §47). */
    val isComplete: Boolean get() = total > 0 && completed == total
}

enum class ProgramJourneyState {
    /** Started, nothing completed yet (includes a start date in the future). */
    ACTIVE,
    IN_PROGRESS,
    COMPLETED
}

data class ProgramSnapshot(
    val program: UserProgram,
    val weeks: List<ProgramWeekPlan>,
    /** Week the calendar says the user is in (PRD §48). */
    val currentWeek: Int,
    val hasStarted: Boolean
) {
    val totalWeeks: Int get() = weeks.size
    val totalWorkouts: Int get() = weeks.sumOf { it.total }
    val completedWorkouts: Int get() = weeks.sumOf { it.completed }
    val weeksCompleted: Int get() = weeks.count { it.isComplete }
    val weeksPercent: Int get() = percent(weeksCompleted, totalWeeks)
    val workoutsPercent: Int get() = percent(completedWorkouts, totalWorkouts)
    val current: ProgramWeekPlan? get() = weeks.firstOrNull { it.weekNumber == currentWeek }

    val state: ProgramJourneyState
        get() {
            if (totalWorkouts > 0 && completedWorkouts == totalWorkouts) return ProgramJourneyState.COMPLETED
            return if (completedWorkouts > 0) ProgramJourneyState.IN_PROGRESS else ProgramJourneyState.ACTIVE
        }

    /** First not-yet-completed program workout scheduled on [day]. */
    fun pendingEntry(day: Long): FitnessContent? =
        weeks.flatMap { it.days }.flatMap { it.entries }.firstOrNull { entry ->
            val date = entry.scheduledDate ?: return@firstOrNull false
            !entry.isCompleted && ProgramDates.isSameDay(date, day)
        }

    companion object {
        fun percent(part: Int, whole: Int): Int =
            if (whole > 0) (part.toDouble() / whole * 100).roundToInt() else 0

        /**
         * Pure: lays [entries] (one enrollment's calendar rows) into weeks. An
         * entry belongs to its stamped template week; rows that predate the
         * stamp fall back to the week their date lands in.
         */
        fun build(
            program: UserProgram,
            entries: List<FitnessContent>,
            explicitPhases: List<String>,
            now: Long = System.currentTimeMillis()
        ): ProgramSnapshot {
            fun weekOf(entry: FitnessContent): Int {
                entry.programWeekNumber?.takeIf { it >= 1 }?.let { return it }
                return program.weekNumber(entry.scheduledDate ?: program.startDate)
            }
            val dated = entries.filter { it.scheduledDate != null && it.workoutId.isNotEmpty() }
            val totalWeeks = maxOf(program.weeks, dated.maxOfOrNull { weekOf(it) } ?: 1, 1)
            val sized = program.copy(weeks = totalWeeks)
            val byWeek = dated.groupBy { weekOf(it) }

            val plans = (1..totalWeeks).map { number ->
                val start = sized.weekStart(number)
                val byDay = (byWeek[number] ?: emptyList()).groupBy { ProgramDates.startOfDay(it.scheduledDate ?: start) }
                var dates: Set<Long> = (0 until 7).map { ProgramDates.addDays(start, it) }.toSet()
                // Week 1 begins on the chosen start date, not before it.
                if (number == 1) {
                    val first = ProgramDates.startOfDay(sized.startDate)
                    dates = dates.filter { it >= first || byDay[it] != null }.toSet()
                }
                // A workout moved outside its week's dates still belongs here.
                dates = dates + byDay.keys
                val days = dates.sorted().map { date ->
                    ProgramDayPlan(
                        date = date,
                        entries = (byDay[date] ?: emptyList()).sortedBy { it.scheduledDate ?: date }
                    )
                }
                ProgramWeekPlan(
                    weekNumber = number,
                    rangeLabel = sized.weekRangeLabel(number),
                    phase = ProgramPhases.name(number, totalWeeks, explicitPhases),
                    days = days
                )
            }
            return ProgramSnapshot(
                program = sized,
                weeks = plans,
                currentWeek = sized.weekNumber(now),
                hasStarted = ProgramDates.startOfDay(now) >= ProgramDates.startOfDay(sized.startDate)
            )
        }
    }
}

// MARK: - Enrollment history record

/**
 * Append-only enrollment ("download") / removal record for contributor
 * payouts. Durably queued (`sync_queue`) and replayed by the sync layer.
 */
data class ProgramEnrollmentRecord(
    val enrollmentId: String = "",
    val userId: String = "",
    val programId: String = "",
    val contributorId: String? = null,
    val startDate: Date = Date(),
    // Denormalized snapshots — captured at write time.
    val programName: String = "",
    val splitId: String? = null,
    val weeks: Int? = null,
    val workoutCount: Int = 0,
    val source: String = "Android",
    val appVersion: String? = null,
    /** "active" | "removed" */
    val status: String = "active"
)

/** One row of `GET /program-enrollments/me`. */
data class ProgramEnrollmentWire(
    val enrollmentId: String = "",
    val programId: String = "",
    val startDate: Date = Date(),
    val programName: String? = null,
    val weeks: Int? = null,
    val status: String = ""
)

// MARK: - Workout row metadata

/** What a Week Plan / Home row shows about a workout beyond its title. */
data class ProgramWorkoutMeta(
    val duration: String? = null,
    val exerciseCount: Int = 0,
    val focus: String? = null
) {
    /** "45 min · 5 exercises" */
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            duration?.let { parts.add(it) }
            if (exerciseCount > 0) parts.add("$exerciseCount exercise${if (exerciseCount == 1) "" else "s"}")
            return parts.joinToString(" · ")
        }
}
