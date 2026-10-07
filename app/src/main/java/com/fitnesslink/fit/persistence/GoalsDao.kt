package com.fitnesslink.fit.persistence

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.fitnesslink.fit.model.CompletionType
import com.fitnesslink.fit.model.FrequencyType
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.model.GoalType
import com.fitnesslink.fit.model.Habit
import com.fitnesslink.fit.model.HabitLog
import com.fitnesslink.fit.model.HabitTier
import com.fitnesslink.fit.model.HabitType
import com.fitnesslink.fit.model.Milestone
import com.fitnesslink.fit.model.Streak
import com.fitnesslink.fit.model.TrajectoryStatus
import org.json.JSONArray
import java.util.Locale

// Goals-domain DB helpers `DatabaseManager` lacks: by-id reads, cascading
// deletes, habit-log history and the milestone cache. Extension functions
// over `rawDatabase()`. `ensureGoalsSchema()` adds the two columns the goal
// models gained in Wave 4 (`habits.autoCompleteSource`, streak freezes)
// guarded by `PRAGMA table_info`, for the integrator to fold into the
// versioned migration. Home keeps using `HomeDao` for its own reads.

private fun goalsDb(): SQLiteDatabase = DatabaseManager.rawDatabase()

private fun lcGoalKey(s: String) = s.lowercase(Locale.US)

private fun Cursor.gText(col: Int): String = getString(col) ?: ""
private fun Cursor.gTextOrNull(col: Int): String? = if (isNull(col)) null else getString(col)?.ifEmpty { null }
private fun Cursor.gLongOrNull(col: Int): Long? = if (isNull(col)) null else getLong(col)
private fun Cursor.gDoubleOrNull(col: Int): Double? = if (isNull(col)) null else getDouble(col)

private inline fun <reified E : Enum<E>> goalEnum(raw: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default

// MARK: - Schema

@Volatile private var goalsSchemaEnsured = false
private val goalsSchemaLock = Any()

private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
    db.rawQuery("PRAGMA table_info($table)", null).use { c ->
        val nameIdx = c.getColumnIndex("name")
        while (c.moveToNext()) if (c.getString(nameIdx).equals(column, ignoreCase = true)) return true
        false
    }

private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, declaration: String) {
    if (hasColumn(db, table, column)) return
    try { db.execSQL("ALTER TABLE $table ADD COLUMN $column $declaration") } catch (_: Exception) {}
}

/** Idempotent; called at first use by `GoalStore.ensureRegistered()`. */
fun DatabaseManager.ensureGoalsSchema() {
    if (goalsSchemaEnsured) return
    synchronized(goalsSchemaLock) {
        if (goalsSchemaEnsured) return
        val db = goalsDb()
        addColumnIfMissing(db, "habits", "autoCompleteSource", "TEXT")
        addColumnIfMissing(db, "streaks", "freezesAvailable", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(db, "streaks", "freezesUsed", "INTEGER NOT NULL DEFAULT 0")
        goalsSchemaEnsured = true
    }
}

// MARK: - Goals

private const val GOAL_COLUMNS = "id, userId, goalType, title, description, targetValue, targetUnit, currentValue, startDate, " +
    "targetDate, completedDate, status, identityStatement, trajectoryStatus, createdAt, updatedAt"

private fun readGoal(c: Cursor) = Goal(
    id = c.gText(0), userId = c.gText(1), goalType = goalEnum(c.gText(2), GoalType.Performance), title = c.gText(3),
    description = c.gText(4), targetValue = c.gDoubleOrNull(5), targetUnit = c.gTextOrNull(6), currentValue = c.getDouble(7),
    startDate = c.getLong(8), targetDate = c.gLongOrNull(9), completedDate = c.gLongOrNull(10),
    status = goalEnum(c.gText(11), GoalStatus.Active), identityStatement = c.gTextOrNull(12),
    trajectoryStatus = goalEnum(c.gText(13), TrajectoryStatus.OnTrack), createdAt = c.getLong(14), updatedAt = c.getLong(15)
)

/** Every cached goal, newest first. */
fun DatabaseManager.goalsCached(): List<Goal> = goalsDb().rawQuery(
    "SELECT $GOAL_COLUMNS FROM goals ORDER BY createdAt DESC", null
).use { c -> buildList { while (c.moveToNext()) add(readGoal(c)) } }

fun DatabaseManager.goalById(id: String): Goal? = goalsDb().rawQuery(
    "SELECT $GOAL_COLUMNS FROM goals WHERE LOWER(id)=? LIMIT 1", arrayOf(lcGoalKey(id))
).use { c -> if (c.moveToFirst()) readGoal(c) else null }

fun DatabaseManager.deleteGoalRow(id: String) {
    goalsDb().delete("goals", "LOWER(id)=?", arrayOf(lcGoalKey(id)))
}

/**
 * iOS `GoalDetailViewModel.deleteGoal` cascade: habits with their streak and
 * log rows, the milestone cache, then the goal. Returns the habits that were
 * removed so the caller can queue their server deletes.
 */
fun DatabaseManager.deleteGoalCascade(goalId: String): List<Habit> {
    val habits = habitsCached(goalId)
    val db = goalsDb()
    db.beginTransaction()
    try {
        for (habit in habits) deleteHabitCascade(habit.id)
        db.delete("milestones", "LOWER(goalId)=?", arrayOf(lcGoalKey(goalId)))
        db.delete("goals", "LOWER(id)=?", arrayOf(lcGoalKey(goalId)))
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
    return habits
}

// MARK: - Habits

private const val HABIT_COLUMNS = "id, goalId, userId, title, description, habitType, anchorBehavior, frequencyType, frequencyDaysJson, " +
    "targetValue, targetUnit, tier, estimatedMinutes, isActive, sortOrder, createdAt, updatedAt, autoCompleteSource"

private fun readHabit(c: Cursor): Habit {
    val days = c.gTextOrNull(8)?.let { json ->
        runCatching { JSONArray(json).let { arr -> List(arr.length()) { i -> arr.getInt(i) } } }.getOrNull()
    }
    return Habit(
        id = c.gText(0), goalId = c.gTextOrNull(1), userId = c.gText(2), title = c.gText(3), description = c.gText(4),
        habitType = goalEnum(c.gText(5), HabitType.ActionBased), anchorBehavior = c.gTextOrNull(6),
        frequencyType = goalEnum(c.gText(7), FrequencyType.Daily), frequencyDays = days,
        targetValue = c.gDoubleOrNull(9), targetUnit = c.gTextOrNull(10), tier = goalEnum(c.gText(11), HabitTier.Seedling),
        estimatedMinutes = c.getInt(12), isActive = c.getInt(13) != 0, sortOrder = c.getInt(14),
        createdAt = c.getLong(15), updatedAt = c.getLong(16), autoCompleteSource = c.gTextOrNull(17)
    )
}

/** Cached habits for one goal, or every habit when [goalId] is null, in `sortOrder`. */
fun DatabaseManager.habitsCached(goalId: String?): List<Habit> {
    ensureGoalsSchema()
    val sql = StringBuilder("SELECT $HABIT_COLUMNS FROM habits")
    val args = mutableListOf<String>()
    if (goalId != null) { sql.append(" WHERE LOWER(goalId)=?"); args.add(lcGoalKey(goalId)) }
    sql.append(" ORDER BY sortOrder, createdAt")
    return goalsDb().rawQuery(sql.toString(), args.toTypedArray()).use { c ->
        buildList { while (c.moveToNext()) add(readHabit(c)) }
    }
}

fun DatabaseManager.habitById(id: String): Habit? {
    ensureGoalsSchema()
    return goalsDb().rawQuery(
        "SELECT $HABIT_COLUMNS FROM habits WHERE LOWER(id)=? LIMIT 1", arrayOf(lcGoalKey(id))
    ).use { c -> if (c.moveToFirst()) readHabit(c) else null }
}

/** `insertHabit` plus the Wave 4 `autoCompleteSource` column `DatabaseManager` doesn't know about. */
fun DatabaseManager.upsertHabitFull(h: Habit) {
    ensureGoalsSchema()
    insertHabit(h)
    goalsDb().update("habits", ContentValues().apply {
        if (h.autoCompleteSource == null) putNull("autoCompleteSource") else put("autoCompleteSource", h.autoCompleteSource)
    }, "LOWER(id)=?", arrayOf(lcGoalKey(h.id)))
}

/** Habit row + streak + logs (iOS `deleteHabit` / `deleteStreak` / `deleteHabitLogs`). */
fun DatabaseManager.deleteHabitCascade(habitId: String) {
    val db = goalsDb()
    val id = lcGoalKey(habitId)
    db.delete("habits", "LOWER(id)=?", arrayOf(id))
    db.delete("streaks", "LOWER(habitId)=?", arrayOf(id))
    db.delete("habit_logs", "LOWER(habitId)=?", arrayOf(id))
}

// MARK: - Streaks

/** `habitStreak` plus the freeze counters. */
fun DatabaseManager.streakFull(habitId: String): Streak? {
    ensureGoalsSchema()
    return goalsDb().rawQuery(
        "SELECT id, habitId, userId, currentCount, longestCount, lastCompletedDate, streakStartDate, " +
            "COALESCE(freezesAvailable,0), COALESCE(freezesUsed,0) FROM streaks WHERE LOWER(habitId)=? LIMIT 1",
        arrayOf(lcGoalKey(habitId))
    ).use { c ->
        if (!c.moveToFirst()) return null
        Streak(
            id = c.gText(0), habitId = c.gText(1), userId = c.gText(2), currentCount = c.getInt(3), longestCount = c.getInt(4),
            lastCompletedDate = c.gLongOrNull(5), streakStartDate = c.gLongOrNull(6),
            freezesAvailable = c.getInt(7), freezesUsed = c.getInt(8)
        )
    }
}

/** `upsertHabitStreak` plus the freeze counters. */
fun DatabaseManager.upsertStreakFull(s: Streak) {
    ensureGoalsSchema()
    upsertHabitStreak(s)
    goalsDb().update("streaks", ContentValues().apply {
        put("freezesAvailable", s.freezesAvailable); put("freezesUsed", s.freezesUsed)
    }, "LOWER(habitId)=?", arrayOf(lcGoalKey(s.habitId)))
}

// MARK: - Habit logs

/** Most recent logs for a habit (newest first), optionally since [sinceMillis]. */
fun DatabaseManager.habitLogsFor(habitId: String, sinceMillis: Long? = null, limit: Int = 50): List<HabitLog> {
    val sql = StringBuilder("SELECT id, habitId, userId, completedAt, completionType, actualValue, notes FROM habit_logs WHERE LOWER(habitId)=?")
    val args = mutableListOf(lcGoalKey(habitId))
    if (sinceMillis != null) { sql.append(" AND completedAt>=?"); args.add(sinceMillis.toString()) }
    sql.append(" ORDER BY completedAt DESC LIMIT ").append(limit)
    return goalsDb().rawQuery(sql.toString(), args.toTypedArray()).use { c ->
        buildList {
            while (c.moveToNext()) add(
                HabitLog(
                    id = c.gText(0), habitId = c.gText(1), userId = c.gText(2), completedAt = c.getLong(3),
                    completionType = goalEnum(c.gText(4), CompletionType.Manual), actualValue = c.gDoubleOrNull(5), notes = c.gTextOrNull(6)
                )
            )
        }
    }
}

/** Earliest log timestamp for a habit — the start of its tracked window when older than `createdAt`. */
fun DatabaseManager.firstHabitLogAt(habitId: String): Long? = goalsDb().rawQuery(
    "SELECT MIN(completedAt) FROM habit_logs WHERE LOWER(habitId)=?", arrayOf(lcGoalKey(habitId))
).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

// MARK: - Milestones (display cache; the server is authoritative)

fun DatabaseManager.upsertMilestone(m: Milestone) {
    goalsDb().insertWithOnConflict("milestones", null, ContentValues().apply {
        put("id", lcGoalKey(m.id)); put("goalId", lcGoalKey(m.goalId)); put("title", m.title)
        put("targetValue", m.targetValue)
        if (m.achievedAt == null) putNull("achievedAt") else put("achievedAt", m.achievedAt)
    }, SQLiteDatabase.CONFLICT_REPLACE)
}

fun DatabaseManager.milestonesForGoal(goalId: String): List<Milestone> = goalsDb().rawQuery(
    "SELECT id, goalId, title, targetValue, achievedAt FROM milestones WHERE LOWER(goalId)=? ORDER BY targetValue, title",
    arrayOf(lcGoalKey(goalId))
).use { c ->
    buildList {
        while (c.moveToNext()) add(Milestone(id = c.gText(0), goalId = c.gText(1), title = c.gText(2), targetValue = c.getDouble(3), achievedAt = c.gLongOrNull(4)))
    }
}

/** Replace the cached milestones for a goal with an authoritative server list. */
fun DatabaseManager.replaceMilestones(goalId: String, milestones: List<Milestone>) {
    val db = goalsDb()
    db.beginTransaction()
    try {
        db.delete("milestones", "LOWER(goalId)=?", arrayOf(lcGoalKey(goalId)))
        milestones.forEach { upsertMilestone(it.copy(goalId = goalId)) }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

// MARK: - Sync-queue helpers

/** Lower-cased entity ids with an unsynced queue row under [entityType] (any alias spelling). */
fun DatabaseManager.pendingEntityIds(entityType: String): Set<String> =
    runCatching { pendingSyncEntries(entityType) }.getOrDefault(emptyList())
        .mapTo(HashSet()) { lcGoalKey(it.entityId) }

/** Mark every unsynced queue row for one entity under [entityType] as synced (cancels a not-yet-shipped write). */
fun DatabaseManager.cancelPendingEntries(entityType: String, entityId: String) {
    runCatching { pendingSyncEntries(entityType) }.getOrDefault(emptyList())
        .filter { lcGoalKey(it.entityId) == lcGoalKey(entityId) }
        .forEach { runCatching { markSyncEntrySynced(it.id) } }
}
