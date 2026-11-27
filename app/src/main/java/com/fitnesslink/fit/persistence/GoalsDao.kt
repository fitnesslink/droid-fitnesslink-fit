package com.fitnesslink.fit.persistence

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.fitnesslink.fit.goals.GoalSessionRecord
import com.fitnesslink.fit.goals.GoalSetRecord
import com.fitnesslink.fit.goals.WorkingSetExclusions
import com.fitnesslink.fit.model.CompletionType
import com.fitnesslink.fit.model.GoalEntry
import com.fitnesslink.fit.model.GoalFrequency
import com.fitnesslink.fit.model.GoalSubtype
import com.fitnesslink.fit.model.GoalTrackingSource
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
        // Goals PRD (iOS migrateToV48). iOS adds `goals.definition`; here the
        // typed definition lives in a side table keyed by goal id because
        // `DatabaseManager.insertGoal` is INSERT OR REPLACE and would wipe an
        // extra goals column on every server refresh. `goal_entries` holds
        // manual check-ins / logged amounts, synced against /goals/entries.
        db.execSQL("CREATE TABLE IF NOT EXISTS goal_definitions (id TEXT PRIMARY KEY, definition TEXT)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS goal_entries (
                id TEXT PRIMARY KEY, goalId TEXT NOT NULL, entryDate TEXT NOT NULL,
                activityKey TEXT, value REAL, source TEXT NOT NULL DEFAULT 'Manual',
                note TEXT, createdAt INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_goal_entries_goal ON goal_entries (goalId, entryDate)")
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

/** Every cached goal (with its typed definition), newest first. */
fun DatabaseManager.goalsCached(): List<Goal> {
    ensureGoalsSchema()
    val goals = goalsDb().rawQuery("SELECT $GOAL_COLUMNS FROM goals ORDER BY createdAt DESC", null)
        .use { c -> buildList { while (c.moveToNext()) add(readGoal(c)) } }
    val definitions = goalDefinitions()
    return goals.map { GoalDefinitionColumn.apply(definitions[lcGoalKey(it.id)], it) }
}

fun DatabaseManager.goalById(id: String): Goal? {
    ensureGoalsSchema()
    val goal = goalsDb().rawQuery(
        "SELECT $GOAL_COLUMNS FROM goals WHERE LOWER(id)=? LIMIT 1", arrayOf(lcGoalKey(id))
    ).use { c -> if (c.moveToFirst()) readGoal(c) else null } ?: return null
    return GoalDefinitionColumn.apply(goalDefinition(goal.id), goal)
}

fun DatabaseManager.deleteGoalRow(id: String) {
    goalsDb().delete("goals", "LOWER(id)=?", arrayOf(lcGoalKey(id)))
    goalsDb().delete("goal_definitions", "LOWER(id)=?", arrayOf(lcGoalKey(id)))
}

private fun goalDefinitions(): Map<String, String?> =
    goalsDb().rawQuery("SELECT id, definition FROM goal_definitions", null).use { c ->
        buildMap { while (c.moveToNext()) put(lcGoalKey(c.gText(0)), c.gTextOrNull(1)) }
    }

private fun goalDefinition(id: String): String? =
    goalsDb().rawQuery("SELECT definition FROM goal_definitions WHERE LOWER(id)=? LIMIT 1", arrayOf(lcGoalKey(id)))
        .use { c -> if (c.moveToFirst()) c.gTextOrNull(0) else null }

/**
 * `insertGoal` plus the typed definition. A payload without a definition
 * (legacy goal, or an API that predates the definition columns) keeps the
 * stored one (iOS `insertGoal` v48 behaviour).
 */
fun DatabaseManager.upsertGoalFull(g: Goal) {
    ensureGoalsSchema()
    val stored = goalDefinition(g.id)
    insertGoal(g)
    val definition = if (g.subtype == null) (stored ?: GoalDefinitionColumn.encode(g)) else GoalDefinitionColumn.encode(g)
    goalsDb().insertWithOnConflict("goal_definitions", null, ContentValues().apply {
        put("id", lcGoalKey(g.id)); put("definition", definition)
    }, SQLiteDatabase.CONFLICT_REPLACE)
}

/** Goals PRD 8.3: one pinned goal — pinning clears every other pin locally. */
fun DatabaseManager.setGoalPinnedLocal(id: String, pinned: Boolean) {
    for (goal in goalsCached()) {
        val isTarget = lcGoalKey(goal.id) == lcGoalKey(id)
        val newValue = if (isTarget) pinned else (if (pinned) false else goal.isPinned)
        if (goal.isPinned == newValue) continue
        upsertGoalFull(goal.copy(isPinned = newValue))
    }
}

// MARK: - Goal entries (manual check-ins)

fun DatabaseManager.goalEntriesFor(goalId: String): List<GoalEntry> {
    ensureGoalsSchema()
    return goalsDb().rawQuery(
        "SELECT id, goalId, entryDate, activityKey, value, source, note, createdAt FROM goal_entries " +
            "WHERE LOWER(goalId)=? ORDER BY entryDate DESC, createdAt DESC",
        arrayOf(lcGoalKey(goalId))
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                GoalEntry(
                    id = c.gText(0), goalId = c.gText(1), entryDate = c.gText(2), activityKey = c.gTextOrNull(3),
                    value = c.gDoubleOrNull(4), source = c.gText(5).ifEmpty { "Manual" }, note = c.gTextOrNull(6), createdAt = c.getLong(7)
                )
            )
        }
    }
}

/** Skips an entry whose DELETE is still queued, so a server pull can't resurrect it. */
fun DatabaseManager.insertGoalEntry(e: GoalEntry) {
    ensureGoalsSchema()
    val id = lcGoalKey(e.id)
    val pendingDeletes = runCatching { pendingSyncEntries(GOAL_ENTRIES_ENTITY_TYPE) }.getOrDefault(emptyList())
        .filter { it.action == "delete" }.map { lcGoalKey(it.entityId) }
    if (id in pendingDeletes) return
    goalsDb().insertWithOnConflict("goal_entries", null, ContentValues().apply {
        put("id", id); put("goalId", lcGoalKey(e.goalId)); put("entryDate", e.entryDate.take(10))
        if (e.activityKey == null) putNull("activityKey") else put("activityKey", e.activityKey)
        if (e.value == null) putNull("value") else put("value", e.value)
        put("source", e.source)
        if (e.note == null) putNull("note") else put("note", e.note)
        put("createdAt", e.createdAt)
    }, SQLiteDatabase.CONFLICT_REPLACE)
}

fun DatabaseManager.deleteGoalEntryRow(id: String) {
    ensureGoalsSchema()
    goalsDb().delete("goal_entries", "LOWER(id)=?", arrayOf(lcGoalKey(id)))
}

/** Server pull: replace every entry with the server's copy, keeping local rows whose create is still queued. */
fun DatabaseManager.replaceGoalEntries(entries: List<GoalEntry>) {
    ensureGoalsSchema()
    val pendingCreates = runCatching { pendingSyncEntries(GOAL_ENTRIES_ENTITY_TYPE) }.getOrDefault(emptyList())
        .filter { it.action == "create" }.map { lcGoalKey(it.entityId) }.toSet()
    val db = goalsDb()
    db.beginTransaction()
    try {
        if (pendingCreates.isEmpty()) db.delete("goal_entries", null, null)
        else db.delete("goal_entries", "LOWER(id) NOT IN (${pendingCreates.joinToString(",") { "?" }})", pendingCreates.toTypedArray())
        entries.forEach { insertGoalEntry(it) }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

/** Queue entity type for goal check-ins (iOS `SyncManager.goalEntriesType`). */
const val GOAL_ENTRIES_ENTITY_TYPE = "goal-entries"

// MARK: - Goal progress sources

/**
 * Working sets for strength goals (Goals PRD §3, provisional rules in
 * `StrengthMetrics`): every recorded set with weight or reps, warm-up sets
 * and Warm-Up / Cool Down phase tasks excluded like Session Total Volume.
 * Matched to an exercise by trimmed, case-insensitive task name — the
 * identity PRs use, since history rows carry no reliable movement id.
 */
fun DatabaseManager.goalSetRecords(userId: String, movementName: String?): List<GoalSetRecord> {
    val sql = StringBuilder(
        "SELECT COALESCE(workoutSessionId,''), taskName, logDate, COALESCE(setNumber,1), COALESCE(reps,0), " +
            "COALESCE(weightLifted,0), COALESCE(workoutTaskId,''), COALESCE(workoutId,'') FROM workout_session_history " +
            "WHERE userId=? AND (COALESCE(reps,0)>0 OR COALESCE(weightLifted,0)>0)"
    )
    val args = mutableListOf(userId)
    if (movementName != null) { sql.append(" AND LOWER(TRIM(taskName))=LOWER(TRIM(?))"); args.add(movementName) }
    sql.append(" ORDER BY logDate ASC")
    val exclusions = HashMap<String, WorkingSetExclusions>()
    return goalsDb().rawQuery(sql.toString(), args.toTypedArray()).use { c ->
        buildList {
            while (c.moveToNext()) {
                val setNumber = c.getInt(3)
                val taskId = c.gText(6)
                val workoutId = c.gText(7)
                if (workoutId.isNotEmpty() && taskId.isNotEmpty()) {
                    val ex = exclusions.getOrPut(lcGoalKey(workoutId)) {
                        runCatching { workout(workoutId) }.getOrNull()?.let { WorkingSetExclusions(it.allTasks) } ?: WorkingSetExclusions.NONE
                    }
                    if (ex.excludes(taskId, setNumber)) continue
                }
                add(GoalSetRecord(c.gText(0), c.gText(1), c.getLong(2), c.getInt(4), c.getDouble(5)))
            }
        }
    }
}

/** Completed sessions for routine goals (completion date, falling back to start), on/after [sinceMillis]. */
fun DatabaseManager.goalCompletedSessions(userId: String, sinceMillis: Long): List<GoalSessionRecord> = goalsDb().rawQuery(
    "SELECT id, workoutName, COALESCE(completionDate, startDate), programId FROM workout_sessions " +
        "WHERE userId=? AND isCompleted=1 AND COALESCE(completionDate, startDate)>=? ORDER BY COALESCE(completionDate, startDate) DESC",
    arrayOf(userId, sinceMillis.toString())
).use { c -> buildList { while (c.moveToNext()) add(GoalSessionRecord(c.gText(0), c.gText(1), c.getLong(2), c.gTextOrNull(3))) } }

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
        ensureGoalsSchema()
        db.delete("goal_definitions", "LOWER(id)=?", arrayOf(lcGoalKey(goalId)))
        db.delete("goal_entries", "LOWER(goalId)=?", arrayOf(lcGoalKey(goalId)))
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

// MARK: - Definition column

/**
 * The local typed-goal definition: one JSON blob per goal (iOS
 * `GoalDefinitionColumn`), so adding a definition field needs no migration.
 */
object GoalDefinitionColumn {
    private data class Row(
        val subtype: String? = null,
        val metricKey: String? = null,
        val baselineValue: Double? = null,
        val baselineDate: Long? = null,
        val frequency: String? = null,
        val frequencyTarget: Int? = null,
        val trackingSource: String? = null,
        val movementId: String? = null,
        val config: String? = null,
        val isPinned: Boolean? = null
    )

    private val gson = com.google.gson.Gson()

    fun encode(goal: Goal): String = gson.toJson(
        Row(goal.subtype?.name, goal.metricKey, goal.baselineValue, goal.baselineDate, goal.frequency?.name,
            goal.frequencyTarget, goal.trackingSource?.name, goal.movementId, goal.config, goal.isPinned)
    )

    fun apply(json: String?, goal: Goal): Goal {
        if (json.isNullOrBlank()) return goal
        val row = runCatching { gson.fromJson(json, Row::class.java) }.getOrNull() ?: return goal
        return goal.copy(
            subtype = GoalSubtype.fromWire(row.subtype), metricKey = row.metricKey, baselineValue = row.baselineValue,
            baselineDate = row.baselineDate, frequency = GoalFrequency.fromWire(row.frequency), frequencyTarget = row.frequencyTarget,
            trackingSource = GoalTrackingSource.fromWire(row.trackingSource), movementId = row.movementId, config = row.config,
            isPinned = row.isPinned ?: false
        )
    }
}
