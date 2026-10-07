package com.fitnesslink.fit.calendar

import com.fitnesslink.fit.model.FitnessContent
import java.util.Calendar
import java.util.Locale

/**
 * Pure calendar rules shared by the service, the view model and the sheets
 * (and exercised by the unit tests). Nothing here touches the DB, the
 * network or Android.
 */

/** iOS `ScheduleDatePickerSheet.durationOptions` (FTNSSLNK-329). */
object CalendarDurationChips {
    val presets: List<Int> = listOf(15, 30, 45, 60, 75, 90)

    /**
     * The preset chips with the workout's own estimate merged in (sorted)
     * when it isn't already one of them, so the pre-selected default is
     * always tappable. A null / non-positive estimate yields the presets.
     */
    fun options(estimateMinutes: Int?): List<Int> {
        if (estimateMinutes == null || estimateMinutes <= 0 || presets.contains(estimateMinutes)) return presets
        return (presets + estimateMinutes).sorted()
    }

    /** Chip label — the merged estimate is marked "(est.)" so it reads as the workout's own number. */
    fun label(minutes: Int, estimateMinutes: Int?): String =
        if (minutes == estimateMinutes && !presets.contains(minutes)) "$minutes min (est.)" else "$minutes min"

    /**
     * iOS `CalendarWorkoutPickerSheet.estimatedMinutes(from:)` — best-effort
     * parse of a display `time` string ("30 min", "1 hr 15 min") into
     * minutes. Null when the workout has no usable estimate.
     */
    fun estimatedMinutes(fromTime: String): Int? {
        val trimmed = fromTime.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("~")) return null
        trimmed.removeSuffix(" min").trim().toIntOrNull()?.let { return it.takeIf { m -> m > 0 } }
        var minutes = 0
        var matched = false
        val parts = trimmed.split(" ").filter { it.isNotEmpty() }
        var i = 0
        while (i + 1 < parts.size) {
            val n = parts[i].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            when (parts[i + 1].lowercase(Locale.US)) {
                "hr", "hrs", "hour", "hours" -> minutes += n * 60
                "min", "mins", "minute", "minutes" -> minutes += n
                else -> return null
            }
            matched = true
            i += 2
        }
        return if (matched && i == parts.size && minutes > 0) minutes else null
    }

    /**
     * iOS `DateContentView.resolvedDurationMinutes`: the entry's explicit
     * duration when set, else the workout's display estimate, else the
     * content-derived estimate. Null only when nothing is known.
     */
    fun resolvedDurationMinutes(entry: FitnessContent, workoutTime: String?, contentEstimateMinutes: Int?): Int? {
        entry.durationMinutes?.takeIf { it > 0 }?.let { return it }
        if (entry.workoutId.isEmpty()) return null
        return workoutTime?.let { estimatedMinutes(it) } ?: contentEstimateMinutes?.takeIf { it > 0 }
    }
}

/**
 * iOS `DateContentView` delete confirmation: a standalone workout gets a
 * single "Remove from Calendar"; a program workout gets a scope choice —
 * just this entry or the whole program run it came from.
 */
sealed class CalendarDeleteScope {
    object Single : CalendarDeleteScope()
    data class ProgramChoice(val programWorkoutCount: Int) : CalendarDeleteScope()

    companion object {
        /**
         * @param entry the row being removed
         * @param enrollmentEntries entries sharing the row's `programEnrollmentId` (used when it is set)
         * @param programEntries entries sharing the row's `programId` (legacy rows without an enrollment)
         */
        fun forEntry(
            entry: FitnessContent,
            enrollmentEntries: List<FitnessContent>,
            programEntries: List<FitnessContent>
        ): CalendarDeleteScope {
            if (entry.programId.isEmpty()) return Single
            val count = if (entry.programEnrollmentId.isNullOrEmpty()) programEntries.size else enrollmentEntries.size
            return ProgramChoice(programWorkoutCount = count)
        }

        /** Whole-program removal targets the enrollment when the row has one, else every row of the program id. */
        fun wholeProgramEntryIds(
            entry: FitnessContent,
            enrollmentEntries: List<FitnessContent>,
            programEntries: List<FitnessContent>
        ): List<String> =
            (if (entry.programEnrollmentId.isNullOrEmpty()) programEntries else enrollmentEntries).map { it.id }
    }
}

/**
 * iOS `CalendarViewModel.refreshFromServer` pull guard: a row the user has
 * queued a delete or reschedule for must not be re-inserted / overwritten
 * by the server's copy until that mutation has landed.
 */
object CalendarPullGuard {
    fun filterPulled(remote: List<FitnessContent>, pendingMutationIds: Set<String>): List<FitnessContent> {
        if (pendingMutationIds.isEmpty()) return remote
        val pending = pendingMutationIds.map { it.lowercase(Locale.US) }.toSet()
        return remote.filter { it.id.lowercase(Locale.US) !in pending }
    }
}

/** iOS `CalendarViewModel.loadDayStatuses` — completed wins over scheduled on a day with both. */
data class CalendarDayStatuses(val scheduled: Set<Int>, val completed: Set<Int>, val program: Set<Int>) {
    fun statusFor(day: Int): String = when {
        completed.contains(day) -> "completed"
        scheduled.contains(day) -> "scheduled"
        else -> ""
    }

    companion object {
        fun from(entries: List<FitnessContent>, year: Int, month1: Int): CalendarDayStatuses {
            val scheduled = mutableSetOf<Int>()
            val completed = mutableSetOf<Int>()
            val program = mutableSetOf<Int>()
            val cal = Calendar.getInstance()
            for (entry in entries) {
                val date = entry.scheduledDate ?: continue
                cal.timeInMillis = date
                if (cal.get(Calendar.YEAR) != year || cal.get(Calendar.MONTH) + 1 != month1) continue
                val day = cal.get(Calendar.DAY_OF_MONTH)
                if (entry.isCompleted) completed.add(day) else scheduled.add(day)
                if (entry.programId.isNotEmpty()) program.add(day)
            }
            scheduled.removeAll(completed)
            return CalendarDayStatuses(scheduled, completed, program)
        }
    }
}
