package com.fitnesslink.fit.persistence

import com.fitnesslink.fit.model.FitnessContent
import java.util.Calendar
import java.util.Locale

// Calendar-domain DB helpers that `DatabaseManager` lacks (CONTRACTS: domain
// DAOs are extension functions over `rawDatabase()`). No new tables or
// columns — `calendar_content` already carries every FitnessContent field.

private fun lc(s: String) = s.lowercase(Locale.US)

/** Local-midnight start of the month containing [millis]. */
fun monthStartMillis(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Last instant (23:59:59.999 local) of the month containing [millis]. */
fun monthEndMillis(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = monthStartMillis(millis)
    add(Calendar.MONTH, 1)
    add(Calendar.MILLISECOND, -1)
}.timeInMillis

/** Every entry whose `scheduledDate` falls in the month containing [monthMillis]. */
fun DatabaseManager.calendarContentForMonth(monthMillis: Long): List<FitnessContent> =
    calendarContent(monthStartMillis(monthMillis), monthEndMillis(monthMillis))

/** Rows that carry a device-calendar mirror id (iOS `calendarContentWithEkEvents`). */
fun DatabaseManager.calendarContentWithDeviceEvents(): List<FitnessContent> =
    calendarContent().filter { !it.deviceEventId.isNullOrEmpty() }

/** Rows scheduled but not yet mirrored to the device calendar. */
fun DatabaseManager.calendarContentWithoutDeviceEvents(): List<FitnessContent> =
    calendarContent().filter { it.deviceEventId.isNullOrEmpty() && it.scheduledDate != null }

/** Entries of one program run, or — for legacy rows without an enrollment — every entry of the program id. */
fun DatabaseManager.calendarContentForProgramScope(entry: FitnessContent): List<FitnessContent> {
    val enrollmentId = entry.programEnrollmentId ?: ""
    return if (enrollmentId.isEmpty()) calendarContentForProgram(entry.programId) else calendarContentForEnrollment(enrollmentId)
}

/** In-progress sessions for [workoutId] whose start falls on the same local day as [dayMillis] (null day = all). */
fun DatabaseManager.inProgressSessionsForWorkoutOnDay(workoutId: String, dayMillis: Long?): List<DatabaseManager.InProgressSession> {
    if (workoutId.isEmpty()) return emptyList()
    val wanted = lc(workoutId)
    return allInProgressSessions().filter { session ->
        if (lc(session.workoutId) != wanted) return@filter false
        if (dayMillis == null) true else sameLocalDay(session.startDate, dayMillis)
    }
}

private fun sameLocalDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
