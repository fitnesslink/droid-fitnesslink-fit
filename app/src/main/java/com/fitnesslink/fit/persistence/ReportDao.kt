package com.fitnesslink.fit.persistence

import android.database.Cursor
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.MovementAggregate
import com.fitnesslink.fit.model.MovementHistoryEntry
import com.fitnesslink.fit.model.MovementSetDetail
import com.fitnesslink.fit.model.SessionRow
import java.util.Date
import java.util.Locale

/**
 * Report-domain reads layered over the Wave 0 `DatabaseManager` queries.
 * Everything here is read-only against existing columns (no schema work):
 * the full `workout_sessions` column set (completionDate, totalSets,
 * totalReps, completionPercent, rpeEditedAt — FTNSSLNK-319/403/411), the
 * per-set time windows and distances behind the exercise breakdown, and a
 * movement aggregate that carries them.
 */

private const val FULL_SESSION_COLUMNS =
    "id, workoutName, startDate, durationSeconds, isCompleted, exerciseCount, totalWeightLifted, totalCaloriesBurned, rpeValue, " +
        "completionDate, totalSets, totalReps, completionPercent, rpeEditedAt, workoutId, programId"

private fun Cursor.text(col: Int): String = getString(col) ?: ""
private fun Cursor.textOrNull(col: Int): String? = if (isNull(col)) null else getString(col)?.ifEmpty { null }
private fun Cursor.intOrNullAt(col: Int): Int? = if (isNull(col)) null else getInt(col)
private fun Cursor.longOrNullAt(col: Int): Long? = if (isNull(col)) null else getLong(col)
private fun Cursor.doubleOrNullAt(col: Int): Double? = if (isNull(col)) null else getDouble(col)
private fun Cursor.dateOrNullAt(col: Int): Date? = longOrNullAt(col)?.let { Date(it) }
private fun lower(id: String) = id.lowercase(Locale.US)

private fun readFullSessionRow(c: Cursor) = SessionRow(
    id = c.text(0),
    workoutName = c.text(1),
    date = Date(c.getLong(2)),
    durationSeconds = c.getInt(3),
    isCompleted = c.getInt(4) != 0,
    exerciseCount = c.getInt(5),
    totalWeightLifted = c.getDouble(6),
    totalCaloriesBurned = c.getDouble(7),
    rpeValue = c.doubleOrNullAt(8),
    completionDate = c.dateOrNullAt(9),
    totalSets = c.intOrNullAt(10) ?: 0,
    totalReps = c.intOrNullAt(11) ?: 0,
    completionPercent = c.intOrNullAt(12),
    rpeEditedAt = c.dateOrNullAt(13),
    workoutId = c.text(14),
    programId = c.textOrNull(15)
)

/** One session with every report column populated (RPE edit window, completion %, totals). */
fun DatabaseManager.fullSessionRow(id: String): SessionRow? =
    rawDatabase().rawQuery("SELECT $FULL_SESSION_COLUMNS FROM workout_sessions WHERE LOWER(id)=?", arrayOf(lower(id))).use { c ->
        if (c.moveToFirst()) readFullSessionRow(c) else null
    }

/** `sessionRows` with the full column set, newest first. */
fun DatabaseManager.fullSessionRows(userId: String, since: Long): List<SessionRow> =
    rawDatabase().rawQuery(
        "SELECT $FULL_SESSION_COLUMNS FROM workout_sessions WHERE userId=? AND startDate>=? ORDER BY startDate DESC",
        arrayOf(userId, since.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(readFullSessionRow(c)) } }

/** Key used by [sessionSetDurations] / [sessionSetDistances]: `"taskName|setNumber"`. */
fun sessionSetKey(taskName: String, setNumber: Int): String = "$taskName|$setNumber"

/**
 * FTNSSLNK-326 — time windows for the session's timed sets, keyed
 * `"taskName|setNumber"`. Empty for all-rep sessions.
 */
fun DatabaseManager.sessionSetDurations(sessionId: String): Map<String, Int> =
    rawDatabase().rawQuery(
        "SELECT taskName, COALESCE(setNumber,1), intervalSeconds FROM workout_session_history WHERE LOWER(workoutSessionId)=? AND intervalSeconds>0",
        arrayOf(lower(sessionId))
    ).use { c ->
        buildMap { while (c.moveToNext()) put(sessionSetKey(c.text(0), c.getInt(1)), c.getInt(2)) }
    }

/** FTNSSLNK-409 — logged distances per set, keyed `"taskName|setNumber"`. */
fun DatabaseManager.sessionSetDistances(sessionId: String): Map<String, DistanceValue> =
    rawDatabase().rawQuery(
        "SELECT taskName, COALESCE(setNumber,1), distance, distanceUnit FROM workout_session_history WHERE LOWER(workoutSessionId)=? AND distance>0",
        arrayOf(lower(sessionId))
    ).use { c ->
        buildMap {
            while (c.moveToNext()) {
                val unit = DistanceUnit.fromWire(c.textOrNull(3)) ?: continue
                val value = c.doubleOrNullAt(2)?.takeIf { it > 0 } ?: continue
                put(sessionSetKey(c.text(0), c.getInt(1)), DistanceValue(value, unit))
            }
        }
    }

/**
 * The aggregate for one movement (matched by task name, case-insensitive)
 * with per-set durations and distances on the history entries — what the
 * movement detail's weight log renders. Null when nothing was logged in
 * the window.
 */
fun DatabaseManager.movementAggregateDetail(userId: String, since: Long, exerciseName: String): MovementAggregate? {
    data class Raw(
        val taskName: String, val reps: Int, val setNumber: Int, val weight: Double, val date: Date,
        val workoutName: String, val sessionId: String, val duration: Int?, val distance: DistanceValue?
    )
    val raw = rawDatabase().rawQuery(
        """
        SELECT h.taskName, COALESCE(h.reps,0), COALESCE(h.setNumber,1), COALESCE(h.weightLifted,0),
               h.logDate, s.workoutName, h.workoutSessionId, h.intervalSeconds, h.distance, h.distanceUnit
        FROM workout_session_history h JOIN workout_sessions s ON s.id=h.workoutSessionId
        WHERE h.userId=? AND h.logDate>=? AND LOWER(TRIM(h.taskName))=?
        ORDER BY h.logDate DESC, h.setNumber
        """.trimIndent(),
        arrayOf(userId, since.toString(), exerciseName.trim().lowercase(Locale.US))
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val duration = c.intOrNullAt(7)?.takeIf { it > 0 }
                val distance = c.doubleOrNullAt(8)?.takeIf { it > 0 }
                    ?.let { v -> DistanceUnit.fromWire(c.textOrNull(9))?.let { DistanceValue(v, it) } }
                add(Raw(c.text(0), c.getInt(1), c.getInt(2), c.getDouble(3), Date(c.getLong(4)), c.text(5), c.text(6), duration, distance))
            }
        }
    }
    if (raw.isEmpty()) return null

    val displayName = raw.first().taskName.ifBlank { exerciseName }
    val bySession = raw.groupBy { it.sessionId }
    val entries = bySession.map { (sessionId, sets) ->
        val sorted = sets.sortedBy { it.setNumber }
        MovementHistoryEntry(
            id = sessionId,
            sessionDate = sorted.first().date,
            workoutName = sorted.first().workoutName,
            sets = sorted.size,
            reps = sorted.sumOf { it.reps },
            weight = sorted.maxOf { it.weight },
            setDetails = sorted.map { MovementSetDetail(it.setNumber, it.reps, it.weight, it.duration, it.distance) }
        )
    }.sortedByDescending { it.sessionDate }

    val maxWeight = raw.maxOf { it.weight }
    val pr = raw.first { it.weight == maxWeight }
    return MovementAggregate(
        id = displayName,
        exerciseName = displayName,
        timesPerformed = bySession.size,
        totalSets = raw.size,
        totalReps = raw.sumOf { it.reps },
        maxWeight = maxWeight,
        totalVolume = raw.sumOf { it.reps.toDouble() * it.weight },
        prWeight = maxWeight,
        prReps = pr.reps,
        prDate = pr.date,
        entries = entries
    )
}
