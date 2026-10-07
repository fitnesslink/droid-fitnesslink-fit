package com.fitnesslink.fit.persistence

import android.database.Cursor
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.session.MovementPreviousWeight
import com.fitnesslink.fit.session.MovementWeightMax
import com.fitnesslink.fit.session.SavedSessionRow
import com.fitnesslink.fit.sync.SyncDomain
import java.util.Locale
import kotlin.math.abs

// Session-engine helpers `DatabaseManager` lacks (CONTRACTS: extension
// functions over `rawDatabase()`). Everything here is read-only or touches
// only the session / history / calendar tables the engine owns.

private fun lc(s: String) = s.lowercase(Locale.US)
private fun Cursor.strOrNullAt(i: Int): String? = if (isNull(i)) null else getString(i)
private fun Cursor.intOrNullAt(i: Int): Int? = if (isNull(i)) null else getInt(i)
private fun Cursor.longOrNullAt(i: Int): Long? = if (isNull(i)) null else getLong(i)
private fun Cursor.doubleOrNullAt(i: Int): Double? = if (isNull(i)) null else getDouble(i)

/** The full session row as the summary / RPE edit path needs it; null when unknown. */
fun DatabaseManager.sessionDetailRow(sessionId: String): SavedSessionRow? = rawDatabase().rawQuery(
    """SELECT id, workoutId, programId, workoutName, startDate, completionDate, durationSeconds, isCompleted,
              exerciseCount, totalSets, totalReps, totalWeightLifted, totalCaloriesBurned, rpeValue, rpeEditedAt,
              completionPercent, progressJson
       FROM workout_sessions WHERE LOWER(id)=?""",
    arrayOf(lc(sessionId))
).use { c ->
    if (!c.moveToFirst()) return null
    SavedSessionRow(
        id = c.getString(0), workoutId = c.getString(1), programId = c.strOrNullAt(2), workoutName = c.getString(3) ?: "",
        startDateMs = c.getLong(4), completionDateMs = c.longOrNullAt(5), durationSeconds = c.getInt(6),
        isCompleted = c.getInt(7) != 0, exerciseCount = c.getInt(8), totalSets = c.getInt(9), totalReps = c.getInt(10),
        totalWeightLifted = c.getDouble(11), totalCaloriesBurned = c.getDouble(12), rpeValue = c.doubleOrNullAt(13),
        rpeEditedAtMs = c.longOrNullAt(14), completionPercent = c.intOrNullAt(15), progressJson = c.strOrNullAt(16)
    )
}

/**
 * Last session's logged weight for a movement (FTNSSLNK-408): identity by `movementId`, with
 * `taskName` matching rows written before the column existed. [excludingSessionId] keeps the
 * active session out of the figure so it always means "last time".
 */
fun DatabaseManager.previousLoggedWeight(
    movementId: String, userId: String?, excludingSessionId: String?, taskName: String?
): MovementPreviousWeight? {
    val sql = StringBuilder("SELECT weightLifted, COALESCE(reps,0), workoutSessionId, logDate FROM workout_session_history WHERE weightLifted>0")
    val args = mutableListOf<String>()
    if (userId != null) { sql.append(" AND userId=?"); args.add(userId) }
    if (taskName != null) { sql.append(" AND (movementId=? OR (movementId IS NULL AND TRIM(taskName)=TRIM(?)))"); args.add(lc(movementId)); args.add(taskName) }
    else { sql.append(" AND movementId=?"); args.add(lc(movementId)) }
    if (excludingSessionId != null) { sql.append(" AND LOWER(workoutSessionId)!=?"); args.add(lc(excludingSessionId)) }
    sql.append(" ORDER BY logDate DESC LIMIT 1")
    return rawDatabase().rawQuery(sql.toString(), args.toTypedArray()).use { c ->
        if (!c.moveToFirst()) return null
        MovementPreviousWeight(weightLbs = c.getDouble(0), reps = c.getInt(1), isTopSet = false, sessionId = c.getString(2), loggedAtMs = c.getLong(3))
    }
}

/** Heaviest logged set for a movement (by id, falling back to the task name), optionally excluding the active session. */
fun DatabaseManager.maxLoggedWeight(
    movementId: String?, taskName: String?, userId: String?, excludingSessionId: String?
): MovementWeightMax? {
    if (movementId.isNullOrEmpty() && taskName.isNullOrEmpty()) return null
    val sql = StringBuilder("SELECT weightLifted, COALESCE(reps,0) FROM workout_session_history WHERE weightLifted>0")
    val args = mutableListOf<String>()
    if (userId != null) { sql.append(" AND userId=?"); args.add(userId) }
    if (!movementId.isNullOrEmpty() && taskName != null) {
        sql.append(" AND (movementId=? OR (movementId IS NULL AND TRIM(taskName)=TRIM(?)))"); args.add(lc(movementId)); args.add(taskName)
    } else if (!movementId.isNullOrEmpty()) {
        sql.append(" AND movementId=?"); args.add(lc(movementId))
    } else {
        sql.append(" AND TRIM(taskName)=TRIM(?)"); args.add(taskName!!)
    }
    if (excludingSessionId != null) { sql.append(" AND LOWER(workoutSessionId)!=?"); args.add(lc(excludingSessionId)) }
    return rawDatabase().rawQuery(sql.toString(), args.toTypedArray()).use { c ->
        var max: MovementWeightMax? = null
        var count = 0
        while (c.moveToNext()) {
            count += 1
            val w = c.getDouble(0)
            val current = max
            if (current == null || w > current.maxWeight) max = MovementWeightMax(maxWeight = w, reps = c.getInt(1), setCount = 0)
        }
        max?.copy(setCount = count)
    }
}

/** Movement ids (lowercased) among [movementIds] that already have a weighted local history row. */
fun DatabaseManager.movementIdsWithWeightHistory(movementIds: Collection<String>, userId: String?): Set<String> {
    val ids = movementIds.map { lc(it) }.distinct()
    if (ids.isEmpty()) return emptySet()
    val out = mutableSetOf<String>()
    for (chunk in ids.chunked(400)) {
        val sql = StringBuilder("SELECT DISTINCT movementId FROM workout_session_history WHERE weightLifted>0 AND movementId IN (${chunk.joinToString(",") { "?" }})")
        val args = chunk.toMutableList()
        if (userId != null) { sql.append(" AND userId=?"); args.add(userId) }
        rawDatabase().rawQuery(sql.toString(), args.toTypedArray()).use { c -> while (c.moveToNext()) c.strOrNullAt(0)?.let { out.add(lc(it)) } }
    }
    return out
}

/** Sum of every logged distance on a session's history rows, converted to [unit]; null when none. */
fun DatabaseManager.sessionTotalDistance(sessionId: String, unit: DistanceUnit): Double? = rawDatabase().rawQuery(
    "SELECT distance, distanceUnit FROM workout_session_history WHERE LOWER(workoutSessionId)=? AND distance>0",
    arrayOf(lc(sessionId))
).use { c ->
    var total = 0.0
    var any = false
    while (c.moveToNext()) {
        val from = DistanceUnit.fromWire(c.strOrNullAt(1)) ?: continue
        total += from.convert(c.getDouble(0), unit)
        any = true
    }
    if (any) total else null
}

/**
 * A program-calendar entry for [workoutId] that is still open — the occurrence a program workout
 * done on another day completes (iOS `UserProgramStore.openProgramEntry`). Nearest to now wins.
 */
fun DatabaseManager.openProgramEntry(workoutId: String, nowMs: Long): FitnessContent? {
    val candidates = calendarContent().filter {
        it.programEnrollmentId != null && it.workoutId.equals(workoutId, ignoreCase = true) && !it.isCompleted && it.scheduledDate != null
    }
    return candidates.minByOrNull { abs((it.scheduledDate ?: nowMs) - nowMs) }
}

/** Session ids (lowercased) whose `start` is still queued — their progress / history must wait. */
fun DatabaseManager.pendingSessionStartIds(): Set<String> =
    pendingSyncEntries().filter { it.entityType in SyncDomain.SESSIONS.entityTypeAliases && it.action == "start" }
        .map { lc(it.entityId) }.toSet()

/** Rename a session row (and its history + queued entries) to the server-canonical id. */
fun DatabaseManager.renameWorkoutSession(from: String, to: String) {
    updateEntityId(SyncDomain.SESSIONS.wireName, from, to)
    // Progress / history queue rows carry the session id under their own domain.
    rawDatabase().execSQL(
        "UPDATE sync_queue SET entityId=? WHERE LOWER(entityId)=? AND entityType IN ('session-progress','session_progress','workout_session_history')",
        arrayOf(lc(to), lc(from))
    )
}

/** Completed-session start dates (epoch millis) since [sinceMs], for the streak. */
fun DatabaseManager.completedSessionStartDates(userId: String, sinceMs: Long): List<Long> = rawDatabase().rawQuery(
    "SELECT startDate FROM workout_sessions WHERE userId=? AND isCompleted=1 AND startDate>=? ORDER BY startDate",
    arrayOf(userId, sinceMs.toString())
).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
