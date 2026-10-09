package com.fitnesslink.fit.persistence

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.fitnesslink.fit.session.SessionExerciseNote
import java.util.Locale

// Quick Start persistence (PRD §34–36, §45): the training focus + quick-start
// flag on the session row, and the per-exercise RPE / Training Log table.
// Extension functions over `rawDatabase()` per CONTRACTS; the schema lives
// in `DatabaseManager.migrateToV12`.

private fun lc(s: String) = s.lowercase(Locale.US)
private fun Cursor.strOrNullAt(i: Int): String? = if (isNull(i)) null else getString(i)?.ifEmpty { null }
private fun Cursor.intOrNullAt(i: Int): Int? = if (isNull(i)) null else getInt(i)

data class QuickStartSessionMeta(val trainingFocus: String?, val isQuickStart: Boolean)

/** Stamp the Quick Start columns on a session row (after `begin`, and again after a full-row rewrite). */
fun DatabaseManager.updateSessionQuickStartMeta(sessionId: String, trainingFocus: String?, isQuickStart: Boolean) {
    if (sessionId.isEmpty()) return
    rawDatabase().update("workout_sessions", ContentValues().apply {
        if (trainingFocus.isNullOrEmpty()) putNull("trainingFocus") else put("trainingFocus", trainingFocus)
        put("isQuickStart", if (isQuickStart) 1 else 0)
    }, "LOWER(id)=?", arrayOf(lc(sessionId)))
}

/** The Quick Start columns of a session row; null when no row exists. */
fun DatabaseManager.sessionQuickStartMeta(sessionId: String): QuickStartSessionMeta? = rawDatabase().rawQuery(
    "SELECT trainingFocus, isQuickStart FROM workout_sessions WHERE LOWER(id)=? LIMIT 1", arrayOf(lc(sessionId))
).use { c ->
    if (!c.moveToFirst()) return null
    QuickStartSessionMeta(c.strOrNullAt(0), (c.intOrNullAt(1) ?: 0) != 0)
}

/** Exercise RPE + Training Log for one exercise of a session. An empty note deletes the row. */
fun DatabaseManager.upsertSessionExerciseNote(note: SessionExerciseNote) {
    if (note.sessionId.isEmpty() || note.taskId.isEmpty()) return
    if (note.isEmpty) { deleteSessionExerciseNote(note.sessionId, note.taskId); return }
    rawDatabase().insertWithOnConflict("session_exercise_notes", null, ContentValues().apply {
        put("sessionId", lc(note.sessionId)); put("taskId", note.taskId)
        if (note.movementId.isNullOrEmpty()) putNull("movementId") else put("movementId", lc(note.movementId))
        put("taskName", note.taskName)
        if (note.rpe == null) putNull("rpe") else put("rpe", note.rpe)
        val log = note.trainingLog?.trim()
        if (log.isNullOrEmpty()) putNull("trainingLog") else put("trainingLog", log)
        put("updatedAt", note.updatedAtMs)
    }, SQLiteDatabase.CONFLICT_REPLACE)
}

fun DatabaseManager.deleteSessionExerciseNote(sessionId: String, taskId: String) {
    rawDatabase().delete("session_exercise_notes", "sessionId=? AND taskId=?", arrayOf(lc(sessionId), taskId))
}

fun DatabaseManager.deleteSessionExerciseNotes(sessionId: String) {
    rawDatabase().delete("session_exercise_notes", "sessionId=?", arrayOf(lc(sessionId)))
}

/** Every exercise note of a session, in task-name order. */
fun DatabaseManager.sessionExerciseNotes(sessionId: String): List<SessionExerciseNote> = rawDatabase().rawQuery(
    "SELECT sessionId, taskId, movementId, taskName, rpe, trainingLog, updatedAt FROM session_exercise_notes WHERE sessionId=? ORDER BY taskName",
    arrayOf(lc(sessionId))
).use { c ->
    buildList {
        while (c.moveToNext()) add(SessionExerciseNote(
            sessionId = c.getString(0), taskId = c.getString(1), movementId = c.strOrNullAt(2), taskName = c.getString(3) ?: "",
            rpe = c.intOrNullAt(4), trainingLog = c.strOrNullAt(5), updatedAtMs = c.getLong(6)
        ))
    }
}
