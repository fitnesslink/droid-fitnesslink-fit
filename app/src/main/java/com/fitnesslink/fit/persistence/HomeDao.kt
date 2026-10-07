package com.fitnesslink.fit.persistence

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.fitnesslink.fit.model.CompletionType
import com.fitnesslink.fit.model.DashboardSession
import com.fitnesslink.fit.model.FrequencyType
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.model.GoalType
import com.fitnesslink.fit.model.Habit
import com.fitnesslink.fit.model.HabitLog
import com.fitnesslink.fit.model.HabitTier
import com.fitnesslink.fit.model.HabitType
import com.fitnesslink.fit.model.ModelGson
import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.model.Streak
import com.fitnesslink.fit.model.TrajectoryStatus
import com.fitnesslink.fit.sync.SyncDomain
import org.json.JSONArray
import java.util.Locale

// Home-tab DB helpers `DatabaseManager` lacks: goal / habit / streak / habit-
// log reads for "Today's Habits", plus session projections for the dashboard
// tiles. Extension functions over `rawDatabase()`; the tables (`goals`,
// `habits`, `habit_logs`, `streaks`, `workout_sessions`) already exist in the
// versioned schema (V6), so no `ensureSchema` is needed. Names are prefixed
// with `home` where a goals-domain DAO is likely to add its own reader of the
// same table, so the two files never collide at integration.

private fun homeDb(): SQLiteDatabase = DatabaseManager.rawDatabase()

private fun lcKey(s: String) = s.lowercase(Locale.US)

private fun Cursor.text(col: Int): String = getString(col) ?: ""
private fun Cursor.textOrNull(col: Int): String? = if (isNull(col)) null else getString(col)?.ifEmpty { null }
private fun Cursor.longOrNullAt(col: Int): Long? = if (isNull(col)) null else getLong(col)
private fun Cursor.doubleOrNullAt(col: Int): Double? = if (isNull(col)) null else getDouble(col)

private inline fun <reified E : Enum<E>> enumOrDefault(raw: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default

// MARK: - Goals

private const val HOME_GOAL_COLUMNS = "id, userId, goalType, title, description, targetValue, targetUnit, currentValue, startDate, " +
    "targetDate, completedDate, status, identityStatement, trajectoryStatus, createdAt, updatedAt"

private fun readHomeGoal(c: Cursor) = Goal(
    id = c.text(0), userId = c.text(1), goalType = enumOrDefault(c.text(2), GoalType.Performance), title = c.text(3),
    description = c.text(4), targetValue = c.doubleOrNullAt(5), targetUnit = c.textOrNull(6), currentValue = c.getDouble(7),
    startDate = c.getLong(8), targetDate = c.longOrNullAt(9), completedDate = c.longOrNullAt(10),
    status = enumOrDefault(c.text(11), GoalStatus.Active), identityStatement = c.textOrNull(12),
    trajectoryStatus = enumOrDefault(c.text(13), TrajectoryStatus.OnTrack), createdAt = c.getLong(14), updatedAt = c.getLong(15)
)

/** Every cached goal, newest first (iOS `DatabaseManager.goals()`). */
fun DatabaseManager.homeGoals(): List<Goal> = homeDb().rawQuery(
    "SELECT $HOME_GOAL_COLUMNS FROM goals ORDER BY createdAt DESC", null
).use { c -> buildList { while (c.moveToNext()) add(readHomeGoal(c)) } }

// MARK: - Habits

private const val HOME_HABIT_COLUMNS = "id, goalId, userId, title, description, habitType, anchorBehavior, frequencyType, frequencyDaysJson, " +
    "targetValue, targetUnit, tier, estimatedMinutes, isActive, sortOrder, createdAt, updatedAt"

private fun readHomeHabit(c: Cursor): Habit {
    val days = c.textOrNull(8)?.let { json ->
        runCatching { JSONArray(json).let { arr -> List(arr.length()) { i -> arr.getInt(i) } } }.getOrNull()
    }
    return Habit(
        id = c.text(0), goalId = c.textOrNull(1), userId = c.text(2), title = c.text(3), description = c.text(4),
        habitType = enumOrDefault(c.text(5), HabitType.ActionBased), anchorBehavior = c.textOrNull(6),
        frequencyType = enumOrDefault(c.text(7), FrequencyType.Daily), frequencyDays = days,
        targetValue = c.doubleOrNullAt(9), targetUnit = c.textOrNull(10), tier = enumOrDefault(c.text(11), HabitTier.Seedling),
        estimatedMinutes = c.getInt(12), isActive = c.getInt(13) != 0, sortOrder = c.getInt(14),
        createdAt = c.getLong(15), updatedAt = c.getLong(16)
    )
}

/** Every cached habit in `sortOrder` (iOS `DatabaseManager.habits()`). */
fun DatabaseManager.homeHabits(): List<Habit> = homeDb().rawQuery(
    "SELECT $HOME_HABIT_COLUMNS FROM habits ORDER BY sortOrder, createdAt", null
).use { c -> buildList { while (c.moveToNext()) add(readHomeHabit(c)) } }

/**
 * Habit ids (lowercased) with at least one log row stamped inside the local
 * calendar day containing [dayMillis] (iOS `habitsCompletedToday`). Keeps
 * the checkmarks across tab switches and relaunches.
 */
fun DatabaseManager.habitIdsCompletedOn(dayMillis: Long = System.currentTimeMillis()): Set<String> {
    val start = ProgramDates.startOfDay(dayMillis)
    val end = ProgramDates.addDays(start, 1)
    return homeDb().rawQuery(
        "SELECT DISTINCT habitId FROM habit_logs WHERE completedAt >= ? AND completedAt < ?",
        arrayOf(start.toString(), end.toString())
    ).use { c -> buildSet { while (c.moveToNext()) add(lcKey(c.text(0))) } }
}

/** The most recent log for [habitId] inside the day containing [dayMillis], or null (iOS `todayHabitLog`). */
fun DatabaseManager.habitLogOn(habitId: String, dayMillis: Long = System.currentTimeMillis()): HabitLog? {
    val start = ProgramDates.startOfDay(dayMillis)
    val end = ProgramDates.addDays(start, 1)
    return homeDb().rawQuery(
        "SELECT id, habitId, userId, completedAt, completionType, actualValue, notes FROM habit_logs " +
            "WHERE LOWER(habitId)=? AND completedAt >= ? AND completedAt < ? ORDER BY completedAt DESC LIMIT 1",
        arrayOf(lcKey(habitId), start.toString(), end.toString())
    ).use { c ->
        if (!c.moveToFirst()) return null
        HabitLog(
            id = c.text(0), habitId = c.text(1), userId = c.text(2), completedAt = c.getLong(3),
            completionType = enumOrDefault(c.text(4), CompletionType.Manual), actualValue = c.doubleOrNullAt(5), notes = c.textOrNull(6)
        )
    }
}

fun DatabaseManager.deleteHabitLogById(id: String) {
    homeDb().delete("habit_logs", "LOWER(id)=?", arrayOf(lcKey(id)))
}

// MARK: - Streaks

/** The persisted streak for a habit, or null when none exists yet (iOS `streak(habitId:)`). */
fun DatabaseManager.habitStreak(habitId: String): Streak? = homeDb().rawQuery(
    "SELECT id, habitId, userId, currentCount, longestCount, lastCompletedDate, streakStartDate FROM streaks WHERE LOWER(habitId)=? LIMIT 1",
    arrayOf(lcKey(habitId))
).use { c ->
    if (!c.moveToFirst()) return null
    Streak(
        id = c.text(0), habitId = c.text(1), userId = c.text(2), currentCount = c.getInt(3), longestCount = c.getInt(4),
        lastCompletedDate = c.longOrNullAt(5), streakStartDate = c.longOrNullAt(6)
    )
}

/** Insert-or-replace one habit's streak row (iOS `insertStreak`). */
fun DatabaseManager.upsertHabitStreak(s: Streak) {
    // One row per habit: drop any prior row keyed by a different id first.
    homeDb().delete("streaks", "LOWER(habitId)=? AND LOWER(id)<>?", arrayOf(lcKey(s.habitId), lcKey(s.id)))
    homeDb().insertWithOnConflict("streaks", null, ContentValues().apply {
        put("id", lcKey(s.id)); put("habitId", lcKey(s.habitId)); put("userId", s.userId)
        put("currentCount", s.currentCount); put("longestCount", s.longestCount)
        if (s.lastCompletedDate == null) putNull("lastCompletedDate") else put("lastCompletedDate", s.lastCompletedDate)
        if (s.streakStartDate == null) putNull("streakStartDate") else put("streakStartDate", s.streakStartDate)
    }, SQLiteDatabase.CONFLICT_REPLACE)
}

/**
 * Sweep habits whose goal no longer exists locally (iOS `purgeOrphanedHabits`).
 * Only call after a successful, authoritative goals pull. Each orphan is
 * deleted with its streak and logs, and a server DELETE is queued under the
 * habits sync domain so the drain clears it remotely too.
 */
fun DatabaseManager.purgeOrphanedHabits() {
    val liveGoalIds = homeGoals().mapTo(HashSet()) { lcKey(it.id) }
    val orphans = homeHabits().filter { h -> !h.goalId.isNullOrEmpty() && lcKey(h.goalId) !in liveGoalIds }
    if (orphans.isEmpty()) return
    val db = homeDb()
    db.beginTransaction()
    try {
        for (habit in orphans) {
            val id = lcKey(habit.id)
            db.delete("habits", "LOWER(id)=?", arrayOf(id))
            db.delete("streaks", "LOWER(habitId)=?", arrayOf(id))
            db.delete("habit_logs", "LOWER(habitId)=?", arrayOf(id))
            val payload = runCatching { ModelGson.instance.toJson(habit) }.getOrDefault("")
            enqueueSyncEntry(SyncDomain.HABITS.wireName, habit.id, "delete", payload)
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

// MARK: - Sessions (dashboard tiles)

/**
 * Sessions since [sinceMillis] for [userId], reduced to the dashboard's
 * needs (date, completed flag, stored-lbs volume).
 */
fun DatabaseManager.dashboardSessions(userId: String, sinceMillis: Long): List<DashboardSession> = homeDb().rawQuery(
    "SELECT startDate, isCompleted, totalWeightLifted FROM workout_sessions WHERE userId=? AND startDate>=?",
    arrayOf(userId, sinceMillis.toString())
).use { c ->
    buildList { while (c.moveToNext()) add(DashboardSession(c.getLong(0), c.getInt(1) != 0, c.getDouble(2))) }
}
