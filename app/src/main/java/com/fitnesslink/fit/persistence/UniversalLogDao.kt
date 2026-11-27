package com.fitnesslink.fit.persistence

import android.database.Cursor
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.session.ExerciseHistorySession
import com.fitnesslink.fit.session.HistoricalSet
import java.util.Locale

// Universal Workout Logging persistence (PRD §4, §9): the performance cards and Exercise History
// read completed sets of one exercise across finished sessions, plus that exercise's Exercise RPE
// and Training Notes. Extension functions over `rawDatabase()` like QuickStartDao; the Set RPE /
// speed / incline columns live in `DatabaseManager.migrateToV13`. Port of the iOS
// `exerciseHistorySets` / `exerciseNotes` queries.

private fun lc(s: String) = s.lowercase(Locale.US)
private fun Cursor.strOrNullAt(i: Int): String? = if (isNull(i)) null else getString(i)?.ifEmpty { null }
private fun Cursor.intOrNullAt(i: Int): Int? = if (isNull(i)) null else getInt(i)

/** Completed sets of an exercise plus each session's workout name (by session id as stored). */
data class ExerciseHistorySets(val sets: List<HistoricalSet>, val names: Map<String, String>)

/**
 * Every completed set of an exercise in finished sessions, with its session and date. Identity is
 * `movementId`; rows that predate it match through `taskName`. [excludingSessionId] keeps the
 * live session out.
 */
fun DatabaseManager.exerciseHistorySets(
    movementId: String?, taskName: String?, userId: String, excludingSessionId: String? = null
): ExerciseHistorySets {
    val m = movementId?.takeIf { it.isNotEmpty() }
    val n = taskName?.takeIf { it.isNotEmpty() }
    val identity: String
    val args = mutableListOf(userId)
    when {
        m != null && n != null -> { identity = "(h.movementId = ? OR (h.movementId IS NULL AND TRIM(h.taskName) = TRIM(?)))"; args += lc(m); args += n }
        m != null -> { identity = "h.movementId = ?"; args += lc(m) }
        n != null -> { identity = "TRIM(h.taskName) = TRIM(?)"; args += n }
        else -> return ExerciseHistorySets(emptyList(), emptyMap())
    }
    val sql = StringBuilder("""
        SELECT h.workoutSessionId, COALESCE(s.startDate, h.logDate), COALESCE(h.setNumber, 1),
               COALESCE(h.reps, 0), COALESCE(h.weightLifted, 0), h.intervalSeconds,
               h.distance, h.distanceUnit, h.setRpe, COALESCE(s.workoutName, '')
        FROM workout_session_history h
        JOIN workout_sessions s ON LOWER(s.id) = LOWER(h.workoutSessionId)
        WHERE h.userId = ? AND $identity AND s.isCompleted = 1
    """.trimIndent())
    if (excludingSessionId != null) { sql.append(" AND LOWER(h.workoutSessionId) != ?"); args += lc(excludingSessionId) }
    sql.append(" ORDER BY s.startDate DESC, h.setNumber ASC LIMIT 600")
    val sets = mutableListOf<HistoricalSet>()
    val names = mutableMapOf<String, String>()
    rawDatabase().rawQuery(sql.toString(), args.toTypedArray()).use { c ->
        while (c.moveToNext()) {
            val sessionId = c.getString(0) ?: continue
            val duration = c.intOrNullAt(5)?.takeIf { it > 0 }
            val distance = if (c.isNull(6)) null else {
                val value = c.getDouble(6)
                val unit = DistanceUnit.fromWire(c.strOrNullAt(7))
                if (value > 0 && unit != null) DistanceValue(value, unit) else null
            }
            sets += HistoricalSet(
                sessionId = sessionId, dateMs = c.getLong(1), setNumber = c.getInt(2), reps = c.getInt(3),
                weightLbs = c.getDouble(4), durationSeconds = duration, distance = distance, rpe = c.intOrNullAt(8)
            )
            val name = c.getString(9).orEmpty()
            if (name.isNotEmpty()) names[sessionId] = name
        }
    }
    return ExerciseHistorySets(sets, names)
}

/** Exercise RPE + Training Notes of one exercise across sessions (Exercise History, PRD §9.3), keyed by lowercased session id. */
fun DatabaseManager.exerciseNotes(movementId: String?, taskName: String?, sessionIds: List<String>): Map<String, ExerciseHistorySession.Note> {
    if (sessionIds.isEmpty()) return emptyMap()
    val out = mutableMapOf<String, ExerciseHistorySession.Note>()
    for (id in sessionIds) {
        rawDatabase().rawQuery(
            "SELECT movementId, taskName, rpe, trainingLog FROM session_exercise_notes WHERE sessionId = ?", arrayOf(lc(id))
        ).use { c ->
            while (c.moveToNext()) {
                val mid = c.strOrNullAt(0)
                val name = c.getString(1).orEmpty()
                val matches = (movementId != null && mid != null && mid.equals(movementId, ignoreCase = true)) ||
                    (taskName != null && taskName.trim() == name.trim())
                if (!matches) continue
                out[lc(id)] = ExerciseHistorySession.Note(c.intOrNullAt(2), c.strOrNullAt(3))
            }
        }
    }
    return out
}
