package com.fitnesslink.fit.persistence

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.model.api.Anatomy
import com.fitnesslink.fit.model.api.AvailableWorkoutPhase
import com.fitnesslink.fit.model.api.Equipment
import com.fitnesslink.fit.model.api.Movement
import com.fitnesslink.fit.model.api.ProgramSchedule
import com.fitnesslink.fit.model.api.WorkoutSessionStatusId
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Local SQLite cache + offline queue. Schema mirrors iOS
 * `Persistence/DatabaseManager.swift` (V46) collapsed into Android V11.
 *
 * Conventions:
 * - Server ids are stored LOWERCASED (SQLite TEXT comparison is case-sensitive;
 *   the backend returns lowercase GUIDs). Lookups lowercase the key too.
 * - Dates are epoch millis (INTEGER). Only `meal_slots.date` is a
 *   `yyyy-MM-dd` string (the server's `MealSlot.Day` contract).
 * - Rich payloads (`phasesJson`, `sessionOverrides`, `progressJson`,
 *   `trainingLevelsJson`, …) round-trip the FULL model through [ModelGson].
 * - List-view / detail refreshes never wipe locally-owned columns
 *   (`isFavorite`, `sortIndex`, `createdAt`, cached `phasesJson`).
 */
object DatabaseManager {
    private lateinit var database: SQLiteDatabase

    fun initialize(context: Context) {
        database = DatabaseHelper(context).writableDatabase
        DatabaseSeeder.seedIfNeeded()
    }

    fun initializeForTesting() {
        database = SQLiteDatabase.create(null)
        createAllTables(database)
        runAllMigrations(database)
    }

    private fun db(): SQLiteDatabase = database

    /** Raw handle for domain DAO extension files under persistence (the Dao.kt files). Prefer the typed helpers. */
    internal fun rawDatabase(): SQLiteDatabase = database

    private val gson get() = ModelGson.instance

    // MARK: - Schema

    private const val DB_VERSION = 11

    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, "fitnesslink.db", null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            createAllTables(db)
            runAllMigrations(db)
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            if (old < 2) migrateToV2(db)
            if (old < 3) migrateToV3(db)
            if (old < 4) migrateToV4(db)
            if (old < 5) migrateToV5(db)
            if (old < 6) migrateToV6(db)
            if (old < 7) migrateToV7(db)
            if (old < 8) migrateToV8(db)
            if (old < 9) migrateToV9(db)
            if (old < 10) migrateToV10(db)
            if (old < 11) migrateToV11(db)
        }
    }

    private fun runAllMigrations(db: SQLiteDatabase) {
        migrateToV2(db)
        migrateToV3(db)
        migrateToV4(db)
        migrateToV5(db)
        migrateToV6(db)
        // V7 schema is already applied by createAllTables (no imageUrl columns).
        migrateToV8(db)
        migrateToV9(db)
        migrateToV10(db)
        migrateToV11(db)
    }

    private fun createAllTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL,
            isPersonalized INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS home_dashboards (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, progress TEXT NOT NULL,
            unit TEXT NOT NULL, goals TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS programs (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL, time TEXT NOT NULL DEFAULT '',
            location TEXT NOT NULL DEFAULT '', trainingLevel TEXT NOT NULL DEFAULT '',
            description TEXT NOT NULL DEFAULT '')""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS workouts (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL, time TEXT NOT NULL DEFAULT '',
            location TEXT NOT NULL DEFAULT '', trainingLevel TEXT NOT NULL DEFAULT '',
            description TEXT NOT NULL DEFAULT '', phasesJson TEXT NOT NULL DEFAULT '[]')""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS food_entries (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, calories INTEGER NOT NULL DEFAULT 0,
            protein REAL NOT NULL DEFAULT 0, fat REAL NOT NULL DEFAULT 0,
            carbs REAL NOT NULL DEFAULT 0, servingSize REAL NOT NULL DEFAULT 1,
            servingUnit TEXT NOT NULL DEFAULT 'serving',
            mealType TEXT NOT NULL DEFAULT 'BREAKFAST',
            loggedAt INTEGER NOT NULL, isCustomTemplate INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS nutrition_goals (
            id TEXT PRIMARY KEY, calorieGoal INTEGER NOT NULL DEFAULT 2000,
            proteinTarget INTEGER NOT NULL DEFAULT 150, fatTarget INTEGER NOT NULL DEFAULT 65,
            carbsTarget INTEGER NOT NULL DEFAULT 250)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS meal_slots (
            id TEXT PRIMARY KEY, day TEXT NOT NULL, mealType TEXT NOT NULL,
            recipeName TEXT NOT NULL DEFAULT '', calories INTEGER NOT NULL DEFAULT 0,
            protein REAL NOT NULL DEFAULT 0, fat REAL NOT NULL DEFAULT 0,
            carbs REAL NOT NULL DEFAULT 0, isAISuggestion INTEGER NOT NULL DEFAULT 0,
            ingredientsJson TEXT NOT NULL DEFAULT '[]')""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS grocery_items (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, quantity TEXT NOT NULL DEFAULT '',
            unit TEXT NOT NULL DEFAULT '', category TEXT NOT NULL DEFAULT 'OTHER',
            isChecked INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS barcode_products (
            id TEXT PRIMARY KEY, barcode TEXT NOT NULL, name TEXT NOT NULL,
            brand TEXT NOT NULL DEFAULT '', imageUrl TEXT NOT NULL DEFAULT '',
            caloriesPer100g REAL NOT NULL DEFAULT 0, proteinPer100g REAL NOT NULL DEFAULT 0,
            fatPer100g REAL NOT NULL DEFAULT 0, carbsPer100g REAL NOT NULL DEFAULT 0,
            servingSizeGrams REAL NOT NULL DEFAULT 100, servingUnit TEXT NOT NULL DEFAULT 'g')""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS personalizations (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, singleSelection INTEGER NOT NULL DEFAULT 0,
            optionsJson TEXT NOT NULL DEFAULT '[]')""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS calendar_content (
            id TEXT PRIMARY KEY, title TEXT NOT NULL, programId TEXT NOT NULL DEFAULT '',
            workoutId TEXT NOT NULL DEFAULT '', mealPlanId TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT '')""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS profile_menus (
            menuId INTEGER PRIMARY KEY, text TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_barcode ON barcode_products(barcode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_food_logged ON food_entries(loggedAt)")
    }

    // MARK: - Schema Migration

    /** `ALTER TABLE … ADD COLUMN`, silently skipped when the column already exists. */
    private fun addColumn(db: SQLiteDatabase, table: String, columnDef: String) {
        val column = columnDef.trim().split(" ")[0]
        if (columnExists(db, table, column)) return
        try { db.execSQL("ALTER TABLE $table ADD COLUMN $columnDef") } catch (_: Exception) { }
    }

    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) if (c.getString(nameIdx).equals(column, ignoreCase = true)) return true
            false
        }

    private fun migrateToV2(db: SQLiteDatabase) {
        listOf(
            "firstName TEXT NOT NULL DEFAULT ''", "lastName TEXT NOT NULL DEFAULT ''", "alias TEXT", "phone TEXT",
            "firebaseId TEXT NOT NULL DEFAULT ''", "username TEXT NOT NULL DEFAULT ''", "country TEXT",
            "profileImageId TEXT", "companyId TEXT", "isActive INTEGER NOT NULL DEFAULT 1",
            "requirePersonalization INTEGER NOT NULL DEFAULT 1"
        ).forEach { addColumn(db, "users", it) }

        db.execSQL("""CREATE TABLE IF NOT EXISTS movements (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT,
            videoId TEXT, statusId TEXT, contributorId TEXT,
            imageId TEXT, relatedToId TEXT, thumbnailId TEXT,
            preBuiltTemplateId TEXT, timestamp INTEGER,
            lastUpdateDate INTEGER, isDeleted INTEGER NOT NULL DEFAULT 0,
            createdBy TEXT)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS user_preferences (
            id TEXT PRIMARY KEY, userId TEXT NOT NULL,
            language TEXT, timezone TEXT,
            darkMode INTEGER NOT NULL DEFAULT 0,
            workoutSessionType INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS user_personalizations (
            id TEXT PRIMARY KEY, personalizationId TEXT NOT NULL,
            personalizationOptionId TEXT NOT NULL,
            userId TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS program_schedules (
            id TEXT PRIMARY KEY, programId TEXT NOT NULL,
            workoutId TEXT NOT NULL, weekNumber INTEGER NOT NULL,
            dayNumber INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS workout_session_history (
            id TEXT PRIMARY KEY, workoutSessionId TEXT NOT NULL,
            workoutTaskId TEXT NOT NULL, workoutId TEXT NOT NULL,
            programId TEXT, userId TEXT NOT NULL,
            logDate INTEGER NOT NULL, reps INTEGER,
            setNumber INTEGER, intervalSeconds INTEGER,
            weightLifted REAL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_session_history_session ON workout_session_history(workoutSessionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_session_history_user ON workout_session_history(userId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_program_schedules_program ON program_schedules(programId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_user_personalizations_user ON user_personalizations(userId)")
    }

    private fun migrateToV3(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS workout_sessions (
            id TEXT PRIMARY KEY, workoutId TEXT NOT NULL, userId TEXT NOT NULL,
            programId TEXT, workoutName TEXT NOT NULL DEFAULT '',
            startDate INTEGER NOT NULL, completionDate INTEGER,
            durationSeconds INTEGER NOT NULL DEFAULT 0,
            isCompleted INTEGER NOT NULL DEFAULT 0,
            exerciseCount INTEGER NOT NULL DEFAULT 0,
            totalSets INTEGER NOT NULL DEFAULT 0,
            totalReps INTEGER NOT NULL DEFAULT 0,
            totalWeightLifted REAL NOT NULL DEFAULT 0,
            totalCaloriesBurned REAL NOT NULL DEFAULT 0,
            rpeValue REAL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_sessions_user ON workout_sessions(userId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_sessions_date ON workout_sessions(startDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_session_history_logdate ON workout_session_history(logDate)")
        addColumn(db, "workout_session_history", "taskName TEXT NOT NULL DEFAULT ''")
    }

    private fun migrateToV4(db: SQLiteDatabase) {
        addColumn(db, "movements", "muscleGroup TEXT NOT NULL DEFAULT ''")
        addColumn(db, "movements", "equipment TEXT NOT NULL DEFAULT ''")
        addColumn(db, "movements", "isFavorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""CREATE TABLE IF NOT EXISTS recent_movements (
            movementId TEXT PRIMARY KEY,
            addedAt REAL NOT NULL)""")
    }

    private fun migrateToV5(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS notifications (
            id TEXT PRIMARY KEY,
            type TEXT NOT NULL,
            title TEXT NOT NULL,
            body TEXT NOT NULL DEFAULT '',
            isRead INTEGER NOT NULL DEFAULT 0,
            createdAt INTEGER NOT NULL,
            deepLink TEXT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notifications_date ON notifications(createdAt)")
    }

    private fun migrateToV6(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS goals (
            id TEXT PRIMARY KEY, userId TEXT NOT NULL, goalType TEXT NOT NULL,
            title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
            targetValue REAL, targetUnit TEXT, currentValue REAL NOT NULL DEFAULT 0,
            startDate INTEGER NOT NULL, targetDate INTEGER, completedDate INTEGER,
            status TEXT NOT NULL DEFAULT 'Active', identityStatement TEXT,
            trajectoryStatus TEXT NOT NULL DEFAULT 'OnTrack',
            createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, lastSyncedAt INTEGER)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS habits (
            id TEXT PRIMARY KEY, goalId TEXT, userId TEXT NOT NULL,
            title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
            habitType TEXT NOT NULL, anchorBehavior TEXT,
            frequencyType TEXT NOT NULL DEFAULT 'Daily', frequencyDaysJson TEXT,
            targetValue REAL, targetUnit TEXT, tier TEXT NOT NULL DEFAULT 'Seedling',
            estimatedMinutes INTEGER NOT NULL DEFAULT 2, isActive INTEGER NOT NULL DEFAULT 1,
            sortOrder INTEGER NOT NULL DEFAULT 0,
            createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, lastSyncedAt INTEGER)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS habit_logs (
            id TEXT PRIMARY KEY, habitId TEXT NOT NULL, userId TEXT NOT NULL,
            completedAt INTEGER NOT NULL, completionType TEXT NOT NULL DEFAULT 'Manual',
            actualValue REAL, notes TEXT)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS streaks (
            id TEXT PRIMARY KEY, habitId TEXT NOT NULL, userId TEXT NOT NULL,
            currentCount INTEGER NOT NULL DEFAULT 0, longestCount INTEGER NOT NULL DEFAULT 0,
            lastCompletedDate INTEGER, streakStartDate INTEGER)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS milestones (
            id TEXT PRIMARY KEY, goalId TEXT NOT NULL, title TEXT NOT NULL,
            targetValue REAL NOT NULL, achievedAt INTEGER)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS achievements (
            id TEXT PRIMARY KEY, userId TEXT NOT NULL, achievementType TEXT NOT NULL,
            title TEXT NOT NULL, earnedAt INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS weight_entries (
            id TEXT PRIMARY KEY, userId TEXT NOT NULL, weight REAL NOT NULL,
            unit TEXT NOT NULL, date INTEGER NOT NULL, notes TEXT NOT NULL DEFAULT '',
            createdAt INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS measurement_entries (
            id TEXT PRIMARY KEY, userId TEXT NOT NULL, date INTEGER NOT NULL,
            measurementsJson TEXT NOT NULL DEFAULT '[]', notes TEXT NOT NULL DEFAULT '',
            createdAt INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS progress_photo_entries (
            id TEXT PRIMARY KEY, userId TEXT NOT NULL, date INTEGER NOT NULL,
            photosJson TEXT NOT NULL DEFAULT '[]', notes TEXT NOT NULL DEFAULT '',
            createdAt INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS notification_preferences (
            id TEXT PRIMARY KEY, userId TEXT NOT NULL,
            coachingTone TEXT NOT NULL DEFAULT 'EncouragingCoach',
            maxDailyNotifications INTEGER NOT NULL DEFAULT 5,
            enableHabitReminders INTEGER NOT NULL DEFAULT 1,
            enableStreakAlerts INTEGER NOT NULL DEFAULT 1,
            enableMilestones INTEGER NOT NULL DEFAULT 1,
            enableAiCoaching INTEGER NOT NULL DEFAULT 1,
            enableReengagement INTEGER NOT NULL DEFAULT 1,
            enableGoalCheckIns INTEGER NOT NULL DEFAULT 1)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_queue (
            id TEXT PRIMARY KEY, entityType TEXT NOT NULL, entityId TEXT NOT NULL,
            action TEXT NOT NULL, payload TEXT NOT NULL, createdAt INTEGER NOT NULL,
            synced INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_queue_unsynced ON sync_queue(synced) WHERE synced = 0")
        listOf("workouts", "programs", "food_entries", "movements",
            "workout_sessions", "meal_slots", "grocery_items", "notifications")
            .forEach { addColumn(db, it, "lastSyncedAt INTEGER") }
    }

    private fun migrateToV7(db: SQLiteDatabase) {
        try { db.execSQL("ALTER TABLE programs DROP COLUMN imageUrl") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE workouts DROP COLUMN imageUrl") } catch (_: Exception) {}
    }

    private fun migrateToV8(db: SQLiteDatabase) {
        addColumn(db, "calendar_content", "scheduledDate INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_calendar_content_date ON calendar_content(scheduledDate)")
    }

    private fun migrateToV9(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS water_entries (
            id TEXT PRIMARY KEY, amount REAL NOT NULL DEFAULT 0,
            unit TEXT NOT NULL DEFAULT 'OZ', loggedAt INTEGER NOT NULL,
            notes TEXT NOT NULL DEFAULT '')""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_water_entries_logged ON water_entries(loggedAt)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS hydration_goals (
            id TEXT PRIMARY KEY, dailyGoal REAL NOT NULL DEFAULT 64,
            unit TEXT NOT NULL DEFAULT 'OZ')""")
    }

    /** FA-74: surface program week count for the library list + filters. */
    private fun migrateToV10(db: SQLiteDatabase) {
        addColumn(db, "programs", "weeks INTEGER")
    }

    /**
     * iOS-parity schema (iOS V9…V46 collapsed): full task JSON persistence,
     * session / history columns, programs + calendar columns, `user_programs`,
     * movement tag joins, favorites + sort order, notification-pref fields.
     */
    private fun migrateToV11(db: SQLiteDatabase) {
        // workouts
        listOf(
            "imageId TEXT", "thumbnailId TEXT", "contributorId TEXT", "sharedByUserId TEXT",
            "isFavorite INTEGER NOT NULL DEFAULT 0", "createdAt INTEGER", "libraryAddedAt INTEGER",
            "trainingLevelsJson TEXT NOT NULL DEFAULT '[]'", "equipmentJson TEXT NOT NULL DEFAULT '[]'",
            "sortIndex INTEGER", "partOfContributorProgram INTEGER", "estimatedTime INTEGER",
            "statusId TEXT", "preBuiltTemplateId TEXT", "showInLibrary INTEGER NOT NULL DEFAULT 1"
        ).forEach { addColumn(db, "workouts", it) }

        // programs
        listOf(
            "imageId TEXT", "thumbnailId TEXT", "contributorId TEXT", "sharedByUserId TEXT",
            "isFavorite INTEGER NOT NULL DEFAULT 0", "createdAt INTEGER", "sortIndex INTEGER",
            "whoIsFor TEXT", "bestFor TEXT", "weekPhases TEXT",
            "trainingLevelsJson TEXT NOT NULL DEFAULT '[]'",
            "hasExplicitLocation INTEGER NOT NULL DEFAULT 0",
            "hasExplicitTrainingLevels INTEGER NOT NULL DEFAULT 0",
            "estimatedTime INTEGER", "statusId TEXT", "splitId TEXT", "preBuiltTemplateId TEXT",
            "showInLibrary INTEGER NOT NULL DEFAULT 1"
        ).forEach { addColumn(db, "programs", it) }

        // movements (tags live in the join tables below; the legacy free-text
        // muscleGroup / equipment columns stay but are no longer written)
        listOf("sharedByUserId TEXT", "phoneVideoId TEXT", "sortIndex INTEGER", "createdAt INTEGER",
            "summary TEXT").forEach { addColumn(db, "movements", it) }
        db.execSQL("""CREATE TABLE IF NOT EXISTS movement_anatomy (
            movementId TEXT NOT NULL, anatomyId TEXT NOT NULL,
            PRIMARY KEY (movementId, anatomyId))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS movement_equipment (
            movementId TEXT NOT NULL, equipmentId TEXT NOT NULL,
            PRIMARY KEY (movementId, equipmentId))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_movement_anatomy_anatomy ON movement_anatomy(anatomyId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_movement_equipment_equipment ON movement_equipment(equipmentId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS anatomy (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, sortOrder INTEGER)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS equipment (
            id TEXT PRIMARY KEY, name TEXT NOT NULL)""")

        // calendar_content
        listOf(
            "workoutImageId TEXT", "workoutThumbnailId TEXT", "programImageId TEXT", "programThumbnailId TEXT",
            "programEnrollmentId TEXT", "durationMinutes INTEGER",
            "programWeekNumber INTEGER", "programDayNumber INTEGER", "deviceEventId TEXT"
        ).forEach { addColumn(db, "calendar_content", it) }
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_calendar_content_enrollment ON calendar_content(programEnrollmentId)")

        // workout_sessions (live-session breadcrumbs + resume state)
        listOf(
            "statusId TEXT", "workoutPhaseId TEXT", "currentIndex INTEGER", "nextMovementId TEXT",
            "currentRound INTEGER", "completedTaskIds TEXT NOT NULL DEFAULT ''",
            "progressJson TEXT", "completionPercent INTEGER", "lastMode TEXT",
            "sessionOverrides TEXT", "rpeEditedAt INTEGER"
        ).forEach { addColumn(db, "workout_sessions", it) }

        // workout_session_history
        listOf("movementId TEXT", "distance REAL", "distanceUnit TEXT")
            .forEach { addColumn(db, "workout_session_history", it) }
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_session_history_movement ON workout_session_history(movementId)")

        // food_entries
        listOf(
            "foodId TEXT", "quantity REAL", "portionUnit TEXT", "portionGramWeight REAL",
            "fiber REAL", "sugars REAL", "sodium REAL", "saturatedFat REAL", "cholesterol REAL",
            "source TEXT", "aiConfidence TEXT", "aiGenerationId TEXT"
        ).forEach { addColumn(db, "food_entries", it) }

        // meal_slots: date-anchored (yyyy-MM-dd). Legacy `day` rows map onto the current week on read.
        addColumn(db, "meal_slots", "date TEXT NOT NULL DEFAULT ''")
        addColumn(db, "meal_slots", "rowVersion TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_slots_date ON meal_slots(date)")

        // grocery
        listOf("rowVersion TEXT NOT NULL DEFAULT ''", "groceryListId TEXT", "sortOrder INTEGER")
            .forEach { addColumn(db, "grocery_items", it) }
        db.execSQL("""CREATE TABLE IF NOT EXISTS grocery_lists (
            id TEXT PRIMARY KEY, name TEXT NOT NULL DEFAULT '',
            isArchived INTEGER NOT NULL DEFAULT 0, isDefault INTEGER NOT NULL DEFAULT 0,
            itemCount INTEGER NOT NULL DEFAULT 0, createdAt INTEGER, updatedAt INTEGER,
            rowVersion TEXT NOT NULL DEFAULT '')""")

        // hydration
        addColumn(db, "hydration_goals", "preferredUnit TEXT")

        // notification preferences (FTNSSLNK-233)
        listOf(
            "quietHoursStart TEXT", "quietHoursEnd TEXT",
            "enableSystem INTEGER NOT NULL DEFAULT 1", "enableGoals INTEGER NOT NULL DEFAULT 1",
            "enableContent INTEGER NOT NULL DEFAULT 1", "enableCalendar INTEGER NOT NULL DEFAULT 1"
        ).forEach { addColumn(db, "notification_preferences", it) }

        // achievements
        listOf("description TEXT NOT NULL DEFAULT ''", "iconName TEXT NOT NULL DEFAULT ''",
            "sharedToFeed INTEGER NOT NULL DEFAULT 0").forEach { addColumn(db, "achievements", it) }

        // user programs (My Program instances)
        db.execSQL("""CREATE TABLE IF NOT EXISTS user_programs (
            enrollmentId TEXT PRIMARY KEY,
            programId TEXT NOT NULL,
            programName TEXT NOT NULL DEFAULT '',
            startDate INTEGER NOT NULL,
            weekOneStart INTEGER NOT NULL,
            weeks INTEGER NOT NULL DEFAULT 1,
            createdAt INTEGER NOT NULL)""")

        // local catalog of available workout phases (system + user-owned)
        db.execSQL("""CREATE TABLE IF NOT EXISTS workout_phases (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            sortOrder INTEGER NOT NULL DEFAULT 0,
            isSystem INTEGER NOT NULL DEFAULT 0,
            syncState TEXT NOT NULL DEFAULT 'synced',
            lastUpdatedAt INTEGER,
            deletedLocally INTEGER NOT NULL DEFAULT 0)""")
    }

    // MARK: - Cursor Helpers

    private fun Cursor.str(col: Int): String = getString(col) ?: ""
    private fun Cursor.strOrNull(col: Int): String? = if (isNull(col)) null else getString(col)?.ifEmpty { null }
    private fun Cursor.intOrNull(col: Int): Int? = if (isNull(col)) null else getInt(col)
    private fun Cursor.longOrNull(col: Int): Long? = if (isNull(col)) null else getLong(col)
    private fun Cursor.doubleOrNull(col: Int): Double? = if (isNull(col)) null else getDouble(col)
    private fun Cursor.dateOrNull(col: Int): Date? = longOrNull(col)?.let { Date(it) }
    private fun Cursor.bool(col: Int): Boolean = !isNull(col) && getInt(col) != 0
    private fun Cursor.boolOrNull(col: Int): Boolean? = if (isNull(col)) null else getInt(col) != 0

    private fun lc(id: String): String = id.lowercase(Locale.US)
    private fun lcOrNull(id: String?): String? = id?.takeIf { it.isNotEmpty() }?.lowercase(Locale.US)

    private fun ContentValues.putOpt(key: String, value: String?) { if (value == null) putNull(key) else put(key, value) }
    private fun ContentValues.putOpt(key: String, value: Int?) { if (value == null) putNull(key) else put(key, value) }
    private fun ContentValues.putOpt(key: String, value: Long?) { if (value == null) putNull(key) else put(key, value) }
    private fun ContentValues.putOpt(key: String, value: Double?) { if (value == null) putNull(key) else put(key, value) }
    private fun ContentValues.putBool(key: String, value: Boolean) { put(key, if (value) 1 else 0) }

    private inline fun <T> transaction(block: () -> T): T {
        val d = db()
        d.beginTransaction()
        try {
            val r = block()
            d.setTransactionSuccessful()
            return r
        } finally {
            d.endTransaction()
        }
    }

    /** UPDATE, falling back to INSERT when no row matched (API 26 SQLite has no UPSERT). */
    private fun upsert(table: String, idColumn: String, id: String, values: ContentValues) {
        val updated = db().update(table, values, "$idColumn=?", arrayOf(id))
        if (updated == 0) {
            values.put(idColumn, id)
            db().insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun encodeStringList(list: List<String>): String = JSONArray(list).toString()
    private fun decodeStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    // MARK: - Nutrition Report Queries

    fun dailyNutritionRows(since: Long): List<DailyNutritionRow> {
        val goal = nutritionGoal()
        val lower = goal.calorieGoal * 0.9; val upper = goal.calorieGoal * 1.1
        return db().rawQuery("""
            SELECT date(loggedAt/1000,'unixepoch','localtime'), SUM(calories), SUM(protein), SUM(fat), SUM(carbs), MIN(loggedAt)
            FROM food_entries WHERE loggedAt>=? GROUP BY date(loggedAt/1000,'unixepoch','localtime') ORDER BY MIN(loggedAt) DESC
        """, arrayOf(since.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val cals = c.getInt(1)
                    add(DailyNutritionRow(c.str(0), Date(c.getLong(5)), cals, c.getDouble(2),
                        c.getDouble(3), c.getDouble(4), goal.calorieGoal,
                        cals.toDouble() in lower..upper))
                }
            }
        }
    }

    fun mealTypeAggregates(since: Long): List<MealTypeAggregate> {
        data class Row(val mt: String, val count: Int, val avgC: Double, val avgP: Double, val avgF: Double, val avgCb: Double, val sumC: Double)
        val rows = db().rawQuery("""
            SELECT mealType, COUNT(*), AVG(calories), AVG(protein), AVG(fat), AVG(carbs), SUM(calories)
            FROM food_entries WHERE loggedAt>=? GROUP BY mealType
        """, arrayOf(since.toString())).use { c ->
            buildList { while (c.moveToNext()) add(Row(c.str(0), c.getInt(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5), c.getDouble(6))) }
        }
        val total = rows.sumOf { it.sumC }
        return rows.map { r ->
            MealTypeAggregate(r.mt, mealType(r.mt), r.avgC, r.avgP, r.avgF, r.avgCb,
                if (total > 0) r.sumC / total * 100 else 0.0, r.count)
        }.sortedByDescending { it.percentOfDailyCalories }
    }

    private fun mealType(raw: String): MealType = MealType.fromWire(raw) ?: MealType.BREAKFAST

    fun foodAggregates(since: Long): List<FoodAggregate> {
        return db().rawQuery("""
            SELECT name, COUNT(*), AVG(calories), SUM(calories), SUM(protein), AVG(protein)
            FROM food_entries WHERE loggedAt>=? GROUP BY name ORDER BY COUNT(*) DESC
        """, arrayOf(since.toString())).use { c ->
            buildList { while (c.moveToNext()) add(FoodAggregate(c.str(0), c.str(0), c.getInt(1), c.getDouble(2), c.getInt(3), c.getDouble(4), c.getDouble(5))) }
        }
    }

    private const val FOOD_COLUMNS = "id,name,calories,protein,fat,carbs,servingSize,servingUnit,mealType,loggedAt,isCustomTemplate," +
        "foodId,quantity,portionUnit,portionGramWeight,fiber,sugars,sodium,saturatedFat,cholesterol,source,aiConfidence,aiGenerationId"

    fun foodEntriesForName(name: String, since: Long): List<FoodEntry> {
        return db().rawQuery("SELECT $FOOD_COLUMNS FROM food_entries WHERE name=? AND loggedAt>=? ORDER BY loggedAt DESC",
            arrayOf(name, since.toString())).use { c -> buildList { while (c.moveToNext()) add(readFoodEntry(c)) } }
    }

    fun foodEntriesForMealType(mealType: String, since: Long): List<FoodEntry> {
        return db().rawQuery("SELECT $FOOD_COLUMNS FROM food_entries WHERE mealType=? AND loggedAt>=? ORDER BY loggedAt DESC",
            arrayOf(mealType, since.toString())).use { c -> buildList { while (c.moveToNext()) add(readFoodEntry(c)) } }
    }

    fun nutritionLogDates(since: Long): List<Date> {
        return db().rawQuery("SELECT DISTINCT date(loggedAt/1000,'unixepoch','localtime'), MIN(loggedAt) FROM food_entries WHERE loggedAt>=? GROUP BY date(loggedAt/1000,'unixepoch','localtime') ORDER BY MIN(loggedAt)",
            arrayOf(since.toString())).use { c -> buildList { while (c.moveToNext()) add(Date(c.getLong(1))) } }
    }

    // MARK: - Workout Report Queries

    fun workoutReportData(userId: String, since: Long): WorkoutReportData {
        return db().rawQuery("""
            SELECT SUM(CASE WHEN isCompleted=1 THEN 1 ELSE 0 END),
                   SUM(durationSeconds),
                   SUM(CASE WHEN isCompleted=0 THEN 1 ELSE 0 END),
                   AVG(CASE WHEN rpeValue IS NOT NULL AND isCompleted=1 THEN rpeValue END),
                   SUM(exerciseCount), SUM(totalWeightLifted), SUM(totalCaloriesBurned)
            FROM workout_sessions WHERE userId=? AND startDate>=?
        """, arrayOf(userId, since.toString())).use { c ->
            if (!c.moveToFirst()) return WorkoutReportData()
            WorkoutReportData(c.getInt(0), c.getInt(1), c.getInt(2),
                if (c.isNull(3)) 0.0 else c.getDouble(3),
                c.getInt(4), c.getDouble(5), c.getDouble(6))
        }
    }

    private const val SESSION_ROW_COLUMNS = "id, workoutName, startDate, durationSeconds, isCompleted, exerciseCount, totalWeightLifted, totalCaloriesBurned, rpeValue"

    private fun readSessionRow(c: Cursor) = SessionRow(c.str(0), c.str(1), Date(c.getLong(2)), c.getInt(3),
        c.getInt(4) != 0, c.getInt(5), c.getDouble(6), c.getDouble(7), c.doubleOrNull(8))

    fun sessionRows(userId: String, since: Long): List<SessionRow> {
        return db().rawQuery("SELECT $SESSION_ROW_COLUMNS FROM workout_sessions WHERE userId=? AND startDate>=? ORDER BY startDate DESC",
            arrayOf(userId, since.toString())).use { c ->
            buildList { while (c.moveToNext()) add(readSessionRow(c)) }
        }
    }

    fun volumeEntries(userId: String, since: Long): List<VolumeEntry> {
        return db().rawQuery("""
            SELECT id, taskName, logDate, COALESCE(setNumber,1), COALESCE(reps,0), COALESCE(weightLifted,0)
            FROM workout_session_history WHERE userId=? AND logDate>=? AND weightLifted>0 ORDER BY logDate DESC
        """, arrayOf(userId, since.toString())).use { c ->
            buildList { while (c.moveToNext()) add(VolumeEntry(c.str(0), c.str(1), Date(c.getLong(2)), 1, c.getInt(3), c.getDouble(5))) }
        }
    }

    fun personalRecords(userId: String, since: Long): List<PersonalRecord> {
        return db().rawQuery("""
            SELECT taskName, MAX(weightLifted), MAX(reps), logDate
            FROM workout_session_history WHERE userId=? AND logDate>=? AND weightLifted>0
            GROUP BY taskName ORDER BY MAX(weightLifted) DESC
        """, arrayOf(userId, since.toString())).use { c ->
            buildList { while (c.moveToNext()) { val n = c.str(0); add(PersonalRecord(n, n, c.getDouble(1), c.getInt(2), Date(c.getLong(3)))) } }
        }
    }

    fun workoutDates(userId: String, since: Long): List<Date> {
        return db().rawQuery("SELECT startDate FROM workout_sessions WHERE userId=? AND isCompleted=1 AND startDate>=? ORDER BY startDate",
            arrayOf(userId, since.toString())).use { c ->
            buildList { while (c.moveToNext()) add(Date(c.getLong(0))) }
        }
    }

    fun sessionById(id: String): SessionRow? {
        return db().rawQuery("SELECT $SESSION_ROW_COLUMNS FROM workout_sessions WHERE LOWER(id)=?", arrayOf(lc(id))).use { c ->
            if (!c.moveToFirst()) return null
            readSessionRow(c)
        }
    }

    fun sessionExercises(sessionId: String): List<SessionExerciseRow> {
        return db().rawQuery("""
            SELECT taskName, reps, setNumber, COALESCE(weightLifted,0), logDate
            FROM workout_session_history WHERE LOWER(workoutSessionId)=? ORDER BY logDate, taskName, setNumber
        """, arrayOf(lc(sessionId))).use { c ->
            buildList { while (c.moveToNext()) add(SessionExerciseRow(c.str(0), c.getInt(1), c.getInt(2), c.getDouble(3), Date(c.getLong(4)))) }
        }
    }

    fun workoutAggregates(userId: String, since: Long): List<WorkoutAggregate> {
        val sessions = sessionRows(userId, since)
        return sessions.groupBy { it.workoutName }.map { (name, rows) ->
            val completed = rows.filter { it.isCompleted }
            val avgDur = if (completed.isEmpty()) 0 else completed.sumOf { it.durationSeconds } / completed.size
            val rpes = completed.mapNotNull { it.rpeValue }
            WorkoutAggregate(name, name, completed.size, avgDur,
                if (rpes.isEmpty()) 0.0 else rpes.average(),
                completed.sumOf { it.totalWeightLifted }, completed.sumOf { it.totalWeightLifted },
                completed.sumOf { it.totalCaloriesBurned }, rows.sortedByDescending { it.date })
        }.sortedByDescending { it.timesCompleted }
    }

    fun movementAggregates(userId: String, since: Long): List<MovementAggregate> {
        data class R(val tn: String, val reps: Int, val sn: Int, val w: Double, val d: Date, val wn: String, val sid: String)
        val raw = db().rawQuery("""
            SELECT h.taskName, h.reps, h.setNumber, COALESCE(h.weightLifted,0),
                   h.logDate, s.workoutName, h.workoutSessionId
            FROM workout_session_history h JOIN workout_sessions s ON s.id=h.workoutSessionId
            WHERE h.userId=? AND h.logDate>=? AND h.taskName!='' ORDER BY h.taskName, h.logDate DESC, h.setNumber
        """, arrayOf(userId, since.toString())).use { c ->
            buildList { while (c.moveToNext()) add(R(c.str(0), c.getInt(1), c.getInt(2), c.getDouble(3), Date(c.getLong(4)), c.str(5), c.str(6))) }
        }
        return raw.groupBy { it.tn }.map { (name, entries) ->
            val bySession = entries.groupBy { it.sid }
            val hist = bySession.map { (sid, sets) ->
                val s = sets.sortedBy { it.sn }
                MovementHistoryEntry(sid, s.first().d, s.first().wn, s.size, s.sumOf { it.reps },
                    s.maxOf { it.w }, s.map { MovementSetDetail(it.sn, it.reps, it.w) })
            }.sortedByDescending { it.sessionDate }
            val mw = entries.maxOf { it.w }; val pr = entries.first { it.w == mw }
            MovementAggregate(name, name, bySession.size, entries.size, entries.sumOf { it.reps },
                mw, entries.sumOf { it.reps.toDouble() * it.w }, mw, pr.reps, pr.d, hist)
        }.sortedByDescending { it.totalVolume }
    }

    // MARK: - Workout Sessions (write + live-session state)

    fun insertWorkoutSession(
        id: String, workoutId: String, userId: String, programId: String?,
        workoutName: String, startDate: Long, completionDate: Long?,
        durationSeconds: Int, isCompleted: Boolean, exerciseCount: Int,
        totalSets: Int, totalReps: Int, totalWeightLifted: Double,
        totalCaloriesBurned: Double, rpeValue: Double?,
        statusId: String? = null,
        workoutPhaseId: String? = null,
        currentIndex: Int? = null,
        nextMovementId: String? = null,
        currentRound: Int? = null,
        completionPercent: Int? = null,
        progressJson: String? = null,
        sessionOverridesJson: String? = null,
        lastMode: String? = null
    ) {
        db().insertWithOnConflict("workout_sessions", null, ContentValues().apply {
            put("id", lc(id)); put("workoutId", lc(workoutId)); put("userId", userId)
            putOpt("programId", lcOrNull(programId))
            put("workoutName", workoutName); put("startDate", startDate)
            putOpt("completionDate", completionDate)
            put("durationSeconds", durationSeconds); putBool("isCompleted", isCompleted)
            put("exerciseCount", exerciseCount); put("totalSets", totalSets)
            put("totalReps", totalReps); put("totalWeightLifted", totalWeightLifted)
            put("totalCaloriesBurned", totalCaloriesBurned)
            putOpt("rpeValue", rpeValue)
            putOpt("statusId", lcOrNull(statusId) ?: if (isCompleted) WorkoutSessionStatusId.completed else WorkoutSessionStatusId.inProgress)
            putOpt("workoutPhaseId", lcOrNull(workoutPhaseId))
            putOpt("currentIndex", currentIndex)
            putOpt("nextMovementId", lcOrNull(nextMovementId))
            putOpt("currentRound", currentRound)
            putOpt("completionPercent", completionPercent)
            putOpt("progressJson", progressJson)
            putOpt("sessionOverrides", sessionOverridesJson)
            putOpt("lastMode", lastMode)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Backfill a server session row (`/sessions/me`, sync pull). Only ever
     * creates rows for sessions the server considers finished — a purged
     * in-progress session must not resurrect under "Continue Where You Left
     * Off". Existing local rows keep their authoritative stats.
     */
    fun insertWorkoutSession(s: WorkoutSession) {
        if (!s.isCompletedOnServer && !sessionExists(s.id)) return
        if (sessionExists(s.id)) {
            db().update("workout_sessions", ContentValues().apply {
                putOpt("statusId", s.statusId?.toString()?.lowercase(Locale.US))
                putOpt("completionDate", s.completionDate?.time)
                if (s.isCompletedOnServer) putBool("isCompleted", true)
            }, "LOWER(id)=?", arrayOf(lc(s.id)))
            return
        }
        insertWorkoutSession(
            id = s.id, workoutId = s.workoutId, userId = s.userId?.toString() ?: "",
            programId = s.programId?.toString(), workoutName = s.name,
            startDate = s.startDate?.time ?: System.currentTimeMillis(),
            completionDate = s.completionDate?.time, durationSeconds = 0,
            isCompleted = s.isCompletedOnServer, exerciseCount = 0, totalSets = 0, totalReps = 0,
            totalWeightLifted = 0.0, totalCaloriesBurned = 0.0, rpeValue = null,
            statusId = s.statusId?.toString(), workoutPhaseId = s.workoutPhaseId?.toString(),
            currentIndex = s.currentIndex, nextMovementId = s.nextMovementId?.toString()
        )
    }

    fun sessionExists(id: String): Boolean = db().rawQuery(
        "SELECT 1 FROM workout_sessions WHERE LOWER(id)=? LIMIT 1", arrayOf(lc(id))
    ).use { it.moveToFirst() }

    data class SessionResumeBreadcrumbs(
        val workoutPhaseId: String?, val currentIndex: Int?, val nextMovementId: String?, val currentRound: Int?
    )

    fun sessionResumeBreadcrumbs(sessionId: String): SessionResumeBreadcrumbs? = db().rawQuery(
        "SELECT workoutPhaseId, currentIndex, nextMovementId, currentRound FROM workout_sessions WHERE LOWER(id)=?", arrayOf(lc(sessionId))
    ).use { c ->
        if (!c.moveToFirst()) return null
        SessionResumeBreadcrumbs(c.strOrNull(0), c.intOrNull(1), c.strOrNull(2), c.intOrNull(3))
    }

    fun updateSessionResumeBreadcrumbs(sessionId: String, workoutPhaseId: String?, currentIndex: Int?, nextMovementId: String?, currentRound: Int?) {
        db().update("workout_sessions", ContentValues().apply {
            putOpt("workoutPhaseId", lcOrNull(workoutPhaseId)); putOpt("currentIndex", currentIndex)
            putOpt("nextMovementId", lcOrNull(nextMovementId)); putOpt("currentRound", currentRound)
        }, "LOWER(id)=?", arrayOf(lc(sessionId)))
    }

    fun sessionOverridesJson(sessionId: String): String? = db().rawQuery(
        "SELECT sessionOverrides FROM workout_sessions WHERE LOWER(id)=?", arrayOf(lc(sessionId))
    ).use { c -> if (c.moveToFirst()) c.strOrNull(0) else null }

    fun sessionOverrides(sessionId: String): SessionOverrides? =
        sessionOverridesJson(sessionId)?.let { runCatching { gson.fromJson(it, SessionOverrides::class.java) }.getOrNull() }

    fun updateSessionOverrides(sessionId: String, json: String?) {
        db().update("workout_sessions", ContentValues().apply { putOpt("sessionOverrides", json) }, "LOWER(id)=?", arrayOf(lc(sessionId)))
    }

    fun updateSessionOverrides(sessionId: String, overrides: SessionOverrides?) =
        updateSessionOverrides(sessionId, overrides?.takeIf { !it.isEmpty }?.let { gson.toJson(it) })

    fun sessionProgressJson(sessionId: String): String? = db().rawQuery(
        "SELECT progressJson FROM workout_sessions WHERE LOWER(id)=?", arrayOf(lc(sessionId))
    ).use { c -> if (c.moveToFirst()) c.strOrNull(0) else null }

    fun updateSessionProgress(sessionId: String, json: String?, completionPercent: Int, completedTaskIds: Set<String>) {
        db().update("workout_sessions", ContentValues().apply {
            putOpt("progressJson", json); put("completionPercent", completionPercent)
            put("completedTaskIds", completedTaskIds.sorted().joinToString(","))
        }, "LOWER(id)=?", arrayOf(lc(sessionId)))
    }

    fun sessionCompletionPercent(sessionId: String): Int? = db().rawQuery(
        "SELECT completionPercent FROM workout_sessions WHERE LOWER(id)=?", arrayOf(lc(sessionId))
    ).use { c -> if (c.moveToFirst()) c.intOrNull(0) else null }

    fun sessionLastMode(sessionId: String): String? = db().rawQuery(
        "SELECT lastMode FROM workout_sessions WHERE LOWER(id)=?", arrayOf(lc(sessionId))
    ).use { c -> if (c.moveToFirst()) c.strOrNull(0) else null }

    fun updateSessionLastMode(sessionId: String, mode: String) {
        db().update("workout_sessions", ContentValues().apply { put("lastMode", mode) }, "LOWER(id)=?", arrayOf(lc(sessionId)))
    }

    /** Replace the comma-separated `completedTaskIds` column; an empty set clears it. */
    fun setCompletedTaskIds(sessionId: String, ids: Set<String>) {
        db().update("workout_sessions", ContentValues().apply { put("completedTaskIds", ids.sorted().joinToString(",")) },
            "LOWER(id)=?", arrayOf(lc(sessionId)))
    }

    fun updateWorkoutSessionWorkoutId(sessionId: String, workoutId: String) {
        db().update("workout_sessions", ContentValues().apply { put("workoutId", lc(workoutId)) }, "LOWER(id)=?", arrayOf(lc(sessionId)))
    }

    /** RPE edit (0–10, 24 h window enforced by the caller); stamps `rpeEditedAt`. */
    fun updateSessionRpe(sessionId: String, rpeValue: Double?, editedAt: Long = System.currentTimeMillis()) {
        db().update("workout_sessions", ContentValues().apply { putOpt("rpeValue", rpeValue); put("rpeEditedAt", editedAt) },
            "LOWER(id)=?", arrayOf(lc(sessionId)))
    }

    data class InProgressSession(
        val id: String, val workoutId: String, val workoutName: String, val startDate: Long,
        val programId: String?, val completionPercent: Int?, val lastMode: String?
    )

    fun allInProgressSessions(): List<InProgressSession> = db().rawQuery(
        "SELECT id, workoutId, workoutName, startDate, programId, completionPercent, lastMode FROM workout_sessions WHERE isCompleted=0 AND completionDate IS NULL ORDER BY startDate DESC", null
    ).use { c ->
        buildList { while (c.moveToNext()) add(InProgressSession(c.str(0), c.str(1), c.str(2), c.getLong(3), c.strOrNull(4), c.intOrNull(5), c.strOrNull(6))) }
    }

    fun inProgressSession(workoutId: String): InProgressSession? = db().rawQuery(
        "SELECT id, workoutId, workoutName, startDate, programId, completionPercent, lastMode FROM workout_sessions WHERE LOWER(workoutId)=? AND isCompleted=0 AND completionDate IS NULL ORDER BY startDate DESC LIMIT 1",
        arrayOf(lc(workoutId))
    ).use { c ->
        if (!c.moveToFirst()) return null
        InProgressSession(c.str(0), c.str(1), c.str(2), c.getLong(3), c.strOrNull(4), c.intOrNull(5), c.strOrNull(6))
    }

    /** Drop in-progress sessions older than [olderThanDays] (the 24 h resume window). Returns their ids. */
    fun purgeStaleInProgressSessions(olderThanDays: Int): List<String> {
        val cutoff = System.currentTimeMillis() - olderThanDays * 86_400_000L
        val ids = db().rawQuery("SELECT id FROM workout_sessions WHERE isCompleted=0 AND completionDate IS NULL AND startDate<?", arrayOf(cutoff.toString()))
            .use { c -> buildList { while (c.moveToNext()) add(c.str(0)) } }
        ids.forEach { discardInProgressSession(it) }
        return ids
    }

    /** Remove an in-progress session and its live history rows. */
    fun discardInProgressSession(id: String) = transaction {
        db().delete("workout_session_history", "LOWER(workoutSessionId)=?", arrayOf(lc(id)))
        db().delete("workout_sessions", "LOWER(id)=? AND isCompleted=0", arrayOf(lc(id)))
    }

    /** Soft-delete counterpart of `DELETE /sessions/{id}`: drops the session and its history locally. */
    fun deleteSession(id: String) = transaction {
        db().delete("workout_session_history", "LOWER(workoutSessionId)=?", arrayOf(lc(id)))
        db().delete("workout_sessions", "LOWER(id)=?", arrayOf(lc(id)))
    }

    /** The completed session of [workoutId] on the local day of [dayMillis], if any. */
    fun completedSession(workoutId: String, dayMillis: Long): SessionRow? {
        val start = ProgramDates.startOfDay(dayMillis)
        val end = ProgramDates.addDays(start, 1)
        return db().rawQuery(
            "SELECT $SESSION_ROW_COLUMNS FROM workout_sessions WHERE LOWER(workoutId)=? AND isCompleted=1 AND COALESCE(completionDate, startDate)>=? AND COALESCE(completionDate, startDate)<? ORDER BY startDate DESC LIMIT 1",
            arrayOf(lc(workoutId), start.toString(), end.toString())
        ).use { c -> if (c.moveToFirst()) readSessionRow(c) else null }
    }

    fun completedSessionCount(userId: String, excludingSessionId: String? = null): Int {
        val sql = StringBuilder("SELECT COUNT(*) FROM workout_sessions WHERE userId=? AND isCompleted=1")
        val args = mutableListOf(userId)
        if (excludingSessionId != null) { sql.append(" AND LOWER(id)!=?"); args.add(lc(excludingSessionId)) }
        return db().rawQuery(sql.toString(), args.toTypedArray()).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    // MARK: - Session history

    /** Deterministic id for a per-set history row so live logging never duplicates. */
    fun sessionHistoryRowId(sessionId: String, taskId: String, setNumber: Int): String = "${lc(sessionId)}-${lc(taskId)}-$setNumber"

    fun insertSessionHistory(
        id: String, workoutSessionId: String, workoutTaskId: String,
        workoutId: String, programId: String?, userId: String,
        logDate: Long, reps: Int?, setNumber: Int?,
        intervalSeconds: Int?, weightLifted: Double?, taskName: String,
        movementId: String? = null, distance: Double? = null, distanceUnit: String? = null
    ) {
        db().insertWithOnConflict("workout_session_history", null, ContentValues().apply {
            put("id", id); put("workoutSessionId", lc(workoutSessionId))
            put("workoutTaskId", workoutTaskId); put("workoutId", lc(workoutId))
            putOpt("programId", lcOrNull(programId))
            put("userId", userId); put("logDate", logDate)
            putOpt("reps", reps); putOpt("setNumber", setNumber)
            putOpt("intervalSeconds", intervalSeconds)
            putOpt("weightLifted", weightLifted)
            put("taskName", taskName)
            putOpt("movementId", lcOrNull(movementId))
            val unit = DistanceUnit.fromWire(distanceUnit)
            if (distance != null && distance > 0 && unit != null) { put("distance", distance); put("distanceUnit", unit.rawValue) }
            else { putNull("distance"); putNull("distanceUnit") }
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** INSERT OR REPLACE a single set's history row using the deterministic id. */
    fun upsertSessionHistoryEntry(
        sessionId: String, taskId: String, workoutId: String, userId: String, setNumber: Int,
        reps: Int, weightLbs: Double?, taskName: String, durationSeconds: Int? = null,
        movementId: String? = null, distance: Double? = null, distanceUnit: String? = null,
        logDate: Long = System.currentTimeMillis()
    ) {
        insertSessionHistory(
            id = sessionHistoryRowId(sessionId, taskId, setNumber), workoutSessionId = sessionId,
            workoutTaskId = taskId, workoutId = workoutId, programId = null, userId = userId,
            logDate = logDate, reps = reps, setNumber = setNumber, intervalSeconds = durationSeconds,
            weightLifted = weightLbs, taskName = taskName, movementId = movementId,
            distance = distance, distanceUnit = distanceUnit
        )
    }

    fun deleteSessionHistoryEntry(sessionId: String, taskId: String, setNumber: Int) {
        db().delete("workout_session_history", "id=?", arrayOf(sessionHistoryRowId(sessionId, taskId, setNumber)))
    }

    data class SessionLogs(val logs: Map<String, Map<Int, TaskSetConfig>>, val completed: Set<String>)

    /** Rehydrate live mid-workout state: logged sets keyed `[taskId][setNumber]` + completed task ids. */
    fun loadSessionLogs(sessionId: String): SessionLogs {
        val logs = mutableMapOf<String, MutableMap<Int, TaskSetConfig>>()
        db().rawQuery(
            "SELECT workoutTaskId, setNumber, reps, weightLifted, intervalSeconds, distance, distanceUnit FROM workout_session_history WHERE LOWER(workoutSessionId)=?",
            arrayOf(lc(sessionId))
        ).use { c ->
            while (c.moveToNext()) {
                val setNumber = c.intOrNull(1) ?: continue
                logs.getOrPut(c.str(0)) { mutableMapOf() }[setNumber] = TaskSetConfig(
                    setNumber = setNumber, reps = c.intOrNull(2) ?: 0, weightKg = c.doubleOrNull(3),
                    durationSeconds = c.intOrNull(4), distance = c.doubleOrNull(5), distanceUnit = c.strOrNull(6)
                )
            }
        }
        val completed = db().rawQuery("SELECT completedTaskIds FROM workout_sessions WHERE LOWER(id)=?", arrayOf(lc(sessionId))).use { c ->
            if (c.moveToFirst()) c.str(0).split(",").filter { it.isNotEmpty() }.toSet() else emptySet()
        }
        return SessionLogs(logs, completed)
    }

    /**
     * Every recorded set of a movement outside [excludingSessionId] (PR
     * detection). Identity is `movementId`; pre-tagged rows match through
     * `taskName` when given.
     */
    fun movementHistorySets(movementId: String, userId: String? = null, excludingSessionId: String? = null, taskName: String? = null): List<MovementSetRecord> {
        val sql = StringBuilder("SELECT COALESCE(weightLifted,0), COALESCE(reps,0), intervalSeconds, distance, distanceUnit FROM workout_session_history WHERE ")
        val args = mutableListOf<String>()
        if (userId != null) { sql.append("userId=? AND "); args.add(userId) }
        if (taskName != null) { sql.append("(movementId=? OR (movementId IS NULL AND TRIM(taskName)=TRIM(?)))"); args.add(lc(movementId)); args.add(taskName) }
        else { sql.append("movementId=?"); args.add(lc(movementId)) }
        if (excludingSessionId != null) { sql.append(" AND LOWER(workoutSessionId)!=?"); args.add(lc(excludingSessionId)) }
        return db().rawQuery(sql.toString(), args.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val duration = c.intOrNull(2)?.takeIf { it > 0 }
                    val distance = c.doubleOrNull(3)?.takeIf { it > 0 }?.let { v -> DistanceUnit.fromWire(c.strOrNull(4))?.let { DistanceValue(v, it) } }
                    add(MovementSetRecord(c.getDouble(0), c.getInt(1), duration, distance))
                }
            }
        }
    }

    /** Last logged load (lbs) for a movement, newest first. */
    fun lastLoggedWeight(movementId: String, userId: String? = null): Double? {
        val sql = StringBuilder("SELECT weightLifted FROM workout_session_history WHERE movementId=? AND weightLifted>0")
        val args = mutableListOf(lc(movementId))
        if (userId != null) { sql.append(" AND userId=?"); args.add(userId) }
        sql.append(" ORDER BY logDate DESC LIMIT 1")
        return db().rawQuery(sql.toString(), args.toTypedArray()).use { c -> if (c.moveToFirst()) c.getDouble(0) else null }
    }

    // MARK: - Workouts

    private const val WORKOUT_LIST_COLUMNS = "id, name, time, trainingLevel, isFavorite, imageId, thumbnailId, contributorId, sharedByUserId, createdAt, partOfContributorProgram, sortIndex"

    private fun readWorkoutList(c: Cursor) = WorkoutList(
        id = c.str(0), name = c.str(1), time = c.str(2), isFavorite = c.bool(4), trainingLevel = c.str(3),
        imageId = c.strOrNull(5), thumbnailId = c.strOrNull(6), contributorId = c.strOrNull(7),
        sharedByUserId = c.strOrNull(8), createdAt = c.dateOrNull(9), partOfContributorProgram = c.boolOrNull(10),
        sortIndex = c.intOrNull(11)
    )

    /** Every cached workout, in server (list-view) order; unranked rows sort last, then by name. */
    fun allWorkouts(): List<WorkoutList> = db().rawQuery(
        "SELECT $WORKOUT_LIST_COLUMNS FROM workouts ORDER BY CASE WHEN sortIndex IS NULL THEN 1 ELSE 0 END, sortIndex, name COLLATE NOCASE", null
    ).use { c -> buildList { while (c.moveToNext()) add(readWorkoutList(c)) } }

    private const val WORKOUT_COLUMNS = "id, name, time, location, trainingLevel, description, phasesJson, imageId, thumbnailId, contributorId, " +
        "sharedByUserId, isFavorite, createdAt, trainingLevelsJson, equipmentJson, sortIndex, partOfContributorProgram, estimatedTime, " +
        "statusId, preBuiltTemplateId, showInLibrary, libraryAddedAt"

    private fun readWorkout(c: Cursor): Workout {
        val levels = decodeStringList(c.strOrNull(13)).ifEmpty { listOfNotNull(c.strOrNull(4)) }
        val estimated = c.intOrNull(17)
        val time = c.str(2).ifEmpty { estimated?.takeIf { it > 0 }?.let { Workout.formatEstimatedTime(it) } ?: "" }
        return Workout(
            id = c.str(0), name = c.str(1), time = time, location = c.str(3),
            trainingLevel = c.strOrNull(4) ?: levels.firstOrNull() ?: "", trainingLevels = levels,
            description = c.str(5), phases = phasesFromJson(c.str(6)),
            imageId = uuidOrNull(c.strOrNull(7)), thumbnailId = uuidOrNull(c.strOrNull(8)),
            contributorId = uuidOrNull(c.strOrNull(9)), sharedByUserId = uuidOrNull(c.strOrNull(10)),
            isSharedWithMe = c.strOrNull(10) != null, isFavorite = c.bool(11),
            equipment = decodeStringList(c.strOrNull(14)), sortIndex = c.intOrNull(15),
            partOfContributorProgram = c.boolOrNull(16),
            estimatedTime = estimated ?: Workout.secondsFromDisplayTime(c.str(2)),
            statusId = uuidOrNull(c.strOrNull(18)), preBuiltTemplateId = uuidOrNull(c.strOrNull(19)),
            showInLibrary = if (c.isNull(20)) true else c.bool(20), libraryAddedAt = c.dateOrNull(21)
        ).hydrateGroupRounds()
    }

    private fun uuidOrNull(raw: String?): UUID? = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    fun workout(id: String): Workout? = db().rawQuery("SELECT $WORKOUT_COLUMNS FROM workouts WHERE LOWER(id)=?", arrayOf(lc(id))).use { c ->
        if (!c.moveToFirst()) return null
        readWorkout(c)
    }

    fun allWorkoutsFull(): List<Workout> = db().rawQuery("SELECT $WORKOUT_COLUMNS FROM workouts", null).use { c ->
        buildList { while (c.moveToNext()) add(readWorkout(c)) }
    }

    /**
     * Cache a workout (detail fetch, editor save, sync pull). Locally-owned
     * columns survive: `isFavorite`, `sortIndex`, cached `createdAt`,
     * `partOfContributorProgram`, and — when the incoming payload is a
     * shallow list row with no phases — the cached `phasesJson`.
     */
    fun insertWorkout(w: Workout) {
        if (pendingDeleteIds("workouts").contains(lc(w.id))) return
        val id = w.id
        val existing = db().rawQuery(
            "SELECT phasesJson, isFavorite, createdAt, sortIndex, partOfContributorProgram, equipmentJson, libraryAddedAt FROM workouts WHERE LOWER(id)=?", arrayOf(lc(id))
        ).use { c -> if (c.moveToFirst()) Cached(c.str(0), c.bool(1), c.longOrNull(2), c.intOrNull(3), c.boolOrNull(4), c.str(5), c.longOrNull(6)) else null }

        val levels = w.trainingLevels.ifEmpty { listOfNotNull(w.trainingLevel.takeIf { it.isNotEmpty() }) }
        val libraryAddedAt = w.libraryAddedAt?.time ?: existing?.libraryAddedAt
        // A shared workout dates from when it entered THIS user's library.
        val createdAt = libraryAddedAt ?: w.audit?.timestamp?.time ?: existing?.createdAt
        val phasesJson = if (w.phases.isEmpty() && existing != null && existing.phasesJson.isNotEmpty() && existing.phasesJson != "[]") existing.phasesJson else phasesToJson(w.phases)
        val values = ContentValues().apply {
            put("name", w.name); put("time", w.time.ifEmpty { w.estimatedTime?.takeIf { it > 0 }?.let { Workout.formatEstimatedTime(it) } ?: "" })
            put("location", w.location); put("trainingLevel", levels.firstOrNull() ?: w.trainingLevel)
            put("description", w.description); put("phasesJson", phasesJson)
            putOpt("imageId", lcOrNull(w.imageId?.toString())); putOpt("thumbnailId", lcOrNull(w.thumbnailId?.toString()))
            putOpt("contributorId", lcOrNull(w.contributorId?.toString()))
            putOpt("sharedByUserId", lcOrNull(w.sharedByUserId?.toString()))
            putBool("isFavorite", existing?.isFavorite ?: w.isFavorite)
            putOpt("createdAt", createdAt); putOpt("libraryAddedAt", libraryAddedAt)
            put("trainingLevelsJson", encodeStringList(levels))
            put("equipmentJson", if (w.equipment.isEmpty() && existing != null) existing.equipmentJson else encodeStringList(w.equipment))
            putOpt("sortIndex", w.sortIndex ?: existing?.sortIndex)
            val part = w.partOfContributorProgram ?: existing?.partOfContributorProgram
            if (part == null) putNull("partOfContributorProgram") else putBool("partOfContributorProgram", part)
            putOpt("estimatedTime", w.estimatedTime ?: Workout.secondsFromDisplayTime(w.time))
            putOpt("statusId", lcOrNull(w.statusId?.toString())); putOpt("preBuiltTemplateId", lcOrNull(w.preBuiltTemplateId?.toString()))
            putBool("showInLibrary", w.showInLibrary)
        }
        upsert("workouts", "id", if (existing != null) existingId("workouts", id) else lc(id), values)
    }

    private data class Cached(val phasesJson: String, val isFavorite: Boolean, val createdAt: Long?, val sortIndex: Int?,
                              val partOfContributorProgram: Boolean?, val equipmentJson: String, val libraryAddedAt: Long?)

    /** The stored spelling of an id (older rows may be uppercase). */
    private fun existingId(table: String, id: String): String =
        db().rawQuery("SELECT id FROM $table WHERE LOWER(id)=? LIMIT 1", arrayOf(lc(id))).use { c -> if (c.moveToFirst()) c.str(0) else lc(id) }

    /**
     * Targeted list-view upsert (`/workouts/list-view`): refreshes only the
     * list columns + server rank; never touches `phasesJson`, `isFavorite`
     * or a cached detail's description.
     */
    fun upsertWorkoutListRow(row: WorkoutList, sortIndex: Int? = row.sortIndex) {
        if (pendingDeleteIds("workouts").contains(lc(row.id))) return
        val values = ContentValues().apply {
            put("name", row.name)
            if (row.time.isNotEmpty()) { put("time", row.time); putOpt("estimatedTime", Workout.secondsFromDisplayTime(row.time)) }
            if (row.trainingLevel.isNotEmpty()) { put("trainingLevel", row.trainingLevel); put("trainingLevelsJson", encodeStringList(listOf(row.trainingLevel))) }
            putOpt("imageId", lcOrNull(row.imageId)); putOpt("thumbnailId", lcOrNull(row.thumbnailId))
            putOpt("contributorId", lcOrNull(row.contributorId)); putOpt("sharedByUserId", lcOrNull(row.sharedByUserId))
            row.createdAt?.let { put("createdAt", it.time) }
            row.partOfContributorProgram?.let { putBool("partOfContributorProgram", it) }
            sortIndex?.let { put("sortIndex", it) }
        }
        val updated = db().update("workouts", values, "LOWER(id)=?", arrayOf(lc(row.id)))
        if (updated == 0) {
            values.put("id", lc(row.id)); values.put("time", row.time); values.put("trainingLevel", row.trainingLevel)
            values.put("phasesJson", "[]"); values.putBool("isFavorite", row.isFavorite)
            db().insertWithOnConflict("workouts", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    /** Replace the server-order ranks from a list-view snapshot (ids in server order). Unlisted rows lose their rank. */
    fun setWorkoutSortOrder(orderedIds: List<String>) = transaction {
        db().execSQL("UPDATE workouts SET sortIndex=NULL")
        orderedIds.forEachIndexed { i, id -> db().execSQL("UPDATE workouts SET sortIndex=? WHERE LOWER(id)=?", arrayOf(i, lc(id))) }
    }

    fun setWorkoutFavorite(id: String, favorited: Boolean) {
        db().execSQL("UPDATE workouts SET isFavorite=? WHERE LOWER(id)=?", arrayOf(if (favorited) 1 else 0, lc(id)))
    }

    /** Server is the source of truth for favorites — replace the flags wholesale. */
    fun replaceFavoriteWorkouts(favoritedIds: Collection<String>) = transaction {
        db().execSQL("UPDATE workouts SET isFavorite=0")
        favoritedIds.forEach { db().execSQL("UPDATE workouts SET isFavorite=1 WHERE LOWER(id)=?", arrayOf(lc(it))) }
    }

    fun favoriteWorkoutIds(): Set<String> = db().rawQuery("SELECT id FROM workouts WHERE isFavorite=1", null).use { c ->
        buildSet { while (c.moveToNext()) add(lc(c.str(0))) }
    }

    fun deleteWorkout(id: String) {
        db().delete("workouts", "LOWER(id)=?", arrayOf(lc(id)))
    }

    /** Catalog-visible workouts: not flagged as part of a contributor program (server flag, else schedule inference). */
    fun catalogVisibleWorkouts(): List<WorkoutList> {
        val inferred = workoutIdsInContributorPrograms()
        return allWorkouts().filter { !(it.partOfContributorProgram ?: inferred.contains(lc(it.id))) }
    }

    // MARK: - Programs

    private const val PROGRAM_LIST_COLUMNS = "id, name, time, trainingLevel, weeks, isFavorite, imageId, thumbnailId, contributorId, sharedByUserId, createdAt, sortIndex"

    private fun readProgramList(c: Cursor) = ProgramList(
        id = c.str(0), name = c.str(1), time = c.str(2), isFavorite = c.bool(5), weeks = c.intOrNull(4), trainingLevel = c.str(3),
        imageId = c.strOrNull(6), thumbnailId = c.strOrNull(7), contributorId = c.strOrNull(8), sharedByUserId = c.strOrNull(9),
        createdAt = c.dateOrNull(10), sortIndex = c.intOrNull(11)
    )

    fun allPrograms(): List<ProgramList> = db().rawQuery(
        "SELECT $PROGRAM_LIST_COLUMNS FROM programs ORDER BY CASE WHEN sortIndex IS NULL THEN 1 ELSE 0 END, sortIndex, name COLLATE NOCASE", null
    ).use { c -> buildList { while (c.moveToNext()) add(readProgramList(c)) } }

    private const val PROGRAM_COLUMNS = "id, name, time, location, trainingLevel, description, imageId, thumbnailId, contributorId, sharedByUserId, " +
        "isFavorite, createdAt, sortIndex, weeks, whoIsFor, bestFor, weekPhases, trainingLevelsJson, hasExplicitLocation, hasExplicitTrainingLevels, " +
        "estimatedTime, statusId, splitId, preBuiltTemplateId, showInLibrary"

    private fun readProgram(c: Cursor): Program {
        val levels = decodeStringList(c.strOrNull(17)).ifEmpty { c.str(4).split(", ").filter { it.isNotEmpty() } }
        return Program(
            id = c.str(0), name = c.str(1), time = c.str(2), location = c.str(3),
            trainingLevel = levels.firstOrNull() ?: c.str(4), trainingLevels = levels,
            hasExplicitLocation = c.bool(18), hasExplicitTrainingLevels = c.bool(19),
            description = c.str(5), imageId = uuidOrNull(c.strOrNull(6)), thumbnailId = uuidOrNull(c.strOrNull(7)),
            contributorId = uuidOrNull(c.strOrNull(8)), sharedByUserId = uuidOrNull(c.strOrNull(9)),
            isSharedWithMe = c.strOrNull(9) != null, isFavorite = c.bool(10), sortIndex = c.intOrNull(12),
            weeks = c.intOrNull(13), whoIsFor = c.str(14), bestFor = decodeStringList(c.strOrNull(15)),
            weekPhases = decodeStringList(c.strOrNull(16)), estimatedTime = c.intOrNull(20),
            statusId = uuidOrNull(c.strOrNull(21)), splitId = uuidOrNull(c.strOrNull(22)),
            preBuiltTemplateId = uuidOrNull(c.strOrNull(23)), showInLibrary = if (c.isNull(24)) true else c.bool(24)
        )
    }

    fun program(id: String): Program? = db().rawQuery("SELECT $PROGRAM_COLUMNS FROM programs WHERE LOWER(id)=?", arrayOf(lc(id))).use { c ->
        if (!c.moveToFirst()) return null
        readProgram(c)
    }

    /**
     * Cache a program. Preserves the local favorite flag, cached `createdAt`,
     * server rank, and the detail-only fields (weeks / whoIsFor / bestFor /
     * weekPhases) when a shallow list payload omits them.
     */
    fun insertProgram(p: Program) {
        if (pendingDeleteIds("programs").contains(lc(p.id))) return
        val cached = program(p.id)
        val levels = p.trainingLevels.ifEmpty { listOfNotNull(p.trainingLevel.takeIf { it.isNotEmpty() }) }
        val values = ContentValues().apply {
            put("name", p.name); put("time", p.time); put("location", p.location)
            // The single column carries every level, ", "-joined, so chips survive offline.
            put("trainingLevel", levels.joinToString(", ")); put("trainingLevelsJson", encodeStringList(levels))
            putBool("hasExplicitLocation", p.hasExplicitLocation); putBool("hasExplicitTrainingLevels", p.hasExplicitTrainingLevels)
            put("description", p.description)
            putOpt("imageId", lcOrNull(p.imageId?.toString())); putOpt("thumbnailId", lcOrNull(p.thumbnailId?.toString()))
            putOpt("contributorId", lcOrNull(p.contributorId?.toString())); putOpt("sharedByUserId", lcOrNull(p.sharedByUserId?.toString()))
            putBool("isFavorite", cached?.isFavorite ?: p.isFavorite)
            putOpt("createdAt", p.audit?.timestamp?.time ?: cached?.createdAt?.time)
            putOpt("sortIndex", p.sortIndex ?: cached?.sortIndex)
            putOpt("weeks", p.weeks ?: cached?.weeks)
            put("whoIsFor", p.whoIsFor.ifEmpty { cached?.whoIsFor ?: "" })
            put("bestFor", encodeStringList(p.bestFor.ifEmpty { cached?.bestFor ?: emptyList() }))
            put("weekPhases", encodeStringList(p.weekPhases.ifEmpty { cached?.weekPhases ?: emptyList() }))
            putOpt("estimatedTime", p.estimatedTime ?: cached?.estimatedTime)
            putOpt("statusId", lcOrNull(p.statusId?.toString())); putOpt("splitId", lcOrNull(p.splitId?.toString()))
            putOpt("preBuiltTemplateId", lcOrNull(p.preBuiltTemplateId?.toString()))
            putBool("showInLibrary", p.showInLibrary)
        }
        upsert("programs", "id", if (cached != null) existingId("programs", p.id) else lc(p.id), values)
    }

    /** Targeted list-view upsert for `/programs/list-view`. */
    fun upsertProgramListRow(row: ProgramList, sortIndex: Int? = row.sortIndex) {
        if (pendingDeleteIds("programs").contains(lc(row.id))) return
        val values = ContentValues().apply {
            put("name", row.name)
            if (row.time.isNotEmpty()) put("time", row.time)
            if (row.trainingLevel.isNotEmpty()) put("trainingLevel", row.trainingLevel)
            row.weeks?.let { put("weeks", it) }
            putOpt("imageId", lcOrNull(row.imageId)); putOpt("thumbnailId", lcOrNull(row.thumbnailId))
            putOpt("contributorId", lcOrNull(row.contributorId)); putOpt("sharedByUserId", lcOrNull(row.sharedByUserId))
            row.createdAt?.let { put("createdAt", it.time) }
            sortIndex?.let { put("sortIndex", it) }
        }
        val updated = db().update("programs", values, "LOWER(id)=?", arrayOf(lc(row.id)))
        if (updated == 0) {
            values.put("id", lc(row.id)); values.put("time", row.time); values.put("trainingLevel", row.trainingLevel)
            values.putBool("isFavorite", row.isFavorite)
            db().insertWithOnConflict("programs", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun setProgramSortOrder(orderedIds: List<String>) = transaction {
        db().execSQL("UPDATE programs SET sortIndex=NULL")
        orderedIds.forEachIndexed { i, id -> db().execSQL("UPDATE programs SET sortIndex=? WHERE LOWER(id)=?", arrayOf(i, lc(id))) }
    }

    fun setProgramFavorite(id: String, favorited: Boolean) {
        db().execSQL("UPDATE programs SET isFavorite=? WHERE LOWER(id)=?", arrayOf(if (favorited) 1 else 0, lc(id)))
    }

    fun replaceFavoritePrograms(favoritedIds: Collection<String>) = transaction {
        db().execSQL("UPDATE programs SET isFavorite=0")
        favoritedIds.forEach { db().execSQL("UPDATE programs SET isFavorite=1 WHERE LOWER(id)=?", arrayOf(lc(it))) }
    }

    fun favoriteProgramIds(): Set<String> = db().rawQuery("SELECT id FROM programs WHERE isFavorite=1", null).use { c ->
        buildSet { while (c.moveToNext()) add(lc(c.str(0))) }
    }

    fun deleteProgram(id: String) {
        deleteSchedulesForProgram(id)
        db().delete("programs", "LOWER(id)=?", arrayOf(lc(id)))
    }

    // MARK: - Program Schedules

    fun schedulesForProgram(id: String): List<ProgramSchedule> =
        db().rawQuery(
            "SELECT id, programId, workoutId, weekNumber, dayNumber FROM program_schedules WHERE LOWER(programId)=? ORDER BY weekNumber, dayNumber",
            arrayOf(lc(id))
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val sid = uuidOrNull(c.str(0)) ?: continue
                    val pid = uuidOrNull(c.str(1)) ?: continue
                    val wid = uuidOrNull(c.str(2)) ?: continue
                    add(ProgramSchedule(id = sid, programId = pid, workoutId = wid, weekNumber = c.getInt(3), dayNumber = c.getInt(4)))
                }
            }
        }

    fun insertProgramSchedule(s: ProgramSchedule) {
        db().insertWithOnConflict("program_schedules", null, ContentValues().apply {
            put("id", lc(s.id.toString())); put("programId", lc(s.programId.toString()))
            put("workoutId", lc(s.workoutId.toString())); put("weekNumber", s.weekNumber)
            put("dayNumber", s.dayNumber)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteSchedulesForProgram(id: String) {
        db().delete("program_schedules", "LOWER(programId)=?", arrayOf(lc(id)))
    }

    fun replaceSchedulesForProgram(programId: String, schedules: List<ProgramSchedule>) = transaction {
        db().delete("program_schedules", "LOWER(programId)=?", arrayOf(lc(programId)))
        schedules.forEach { insertProgramSchedule(it) }
    }

    /** Lowercased ids of every workout scheduled inside a contributor (template) program. */
    fun workoutIdsInContributorPrograms(): Set<String> = db().rawQuery(
        "SELECT DISTINCT s.workoutId FROM program_schedules s JOIN programs p ON LOWER(p.id)=LOWER(s.programId) WHERE p.contributorId IS NOT NULL AND p.contributorId!=''", null
    ).use { c -> buildSet { while (c.moveToNext()) add(lc(c.str(0))) } }

    /** Lowercased ids of every workout scheduled inside *any* program (the calendar's workout picker excludes these). */
    fun workoutIdsInAnyProgram(): Set<String> = db().rawQuery("SELECT DISTINCT workoutId FROM program_schedules", null).use { c ->
        buildSet { while (c.moveToNext()) add(lc(c.str(0))) }
    }

    /** Lowercased ids of every program with at least one schedule row. */
    fun programIdsWithSchedules(): Set<String> = db().rawQuery("SELECT DISTINCT programId FROM program_schedules", null).use { c ->
        buildSet { while (c.moveToNext()) add(lc(c.str(0))) }
    }

    // MARK: - User Programs (My Program instances)

    fun upsertUserProgram(p: UserProgram) {
        db().insertWithOnConflict("user_programs", null, ContentValues().apply {
            put("enrollmentId", lc(p.enrollmentId)); put("programId", lc(p.programId))
            put("programName", p.programName); put("startDate", p.startDate)
            put("weekOneStart", p.weekOneStart); put("weeks", p.weeks); put("createdAt", p.createdAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun userPrograms(): List<UserProgram> = db().rawQuery(
        "SELECT enrollmentId, programId, programName, startDate, weekOneStart, weeks, createdAt FROM user_programs ORDER BY createdAt", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(UserProgram(c.str(0), c.str(1), c.str(2), c.getLong(3), c.getLong(4), c.getInt(5), c.getLong(6)))
        }
    }

    fun userProgram(enrollmentId: String): UserProgram? = userPrograms().firstOrNull { it.enrollmentId == lc(enrollmentId) }

    fun deleteUserProgram(enrollmentId: String) {
        db().delete("user_programs", "enrollmentId=?", arrayOf(lc(enrollmentId)))
    }

    // MARK: - Food / Nutrition

    private fun readFoodEntry(c: Cursor): FoodEntry = FoodEntry(
        id = c.str(0), name = c.str(1), calories = c.getInt(2), protein = c.getDouble(3), fat = c.getDouble(4), carbs = c.getDouble(5),
        servingSize = c.getDouble(6), servingUnit = c.str(7), mealType = mealType(c.str(8)), loggedAt = Date(c.getLong(9)),
        isCustomTemplate = c.getInt(10) != 0,
        foodId = c.strOrNull(11), quantity = c.doubleOrNull(12), portionUnit = c.strOrNull(13), portionGramWeight = c.doubleOrNull(14),
        fiber = c.doubleOrNull(15), sugars = c.doubleOrNull(16), sodium = c.doubleOrNull(17), saturatedFat = c.doubleOrNull(18),
        cholesterol = c.doubleOrNull(19), source = c.strOrNull(20), aiConfidence = c.strOrNull(21), aiGenerationId = c.strOrNull(22)
    )

    fun allFoodEntries(): List<FoodEntry> = db().rawQuery("SELECT $FOOD_COLUMNS FROM food_entries", null).use { c ->
        buildList { while (c.moveToNext()) add(readFoodEntry(c)) }
    }

    fun foodEntries(forDate: Long): List<FoodEntry> {
        val start = ProgramDates.startOfDay(forDate)
        val end = ProgramDates.addDays(start, 1)
        return db().rawQuery("SELECT $FOOD_COLUMNS FROM food_entries WHERE loggedAt>=? AND loggedAt<? ORDER BY loggedAt",
            arrayOf(start.toString(), end.toString())).use { c -> buildList { while (c.moveToNext()) add(readFoodEntry(c)) } }
    }

    private fun foodValues(e: FoodEntry) = ContentValues().apply {
        put("id", lc(e.id)); put("name", e.name); put("calories", e.calories)
        put("protein", e.protein); put("fat", e.fat); put("carbs", e.carbs)
        put("servingSize", e.servingSize); put("servingUnit", e.servingUnit)
        put("mealType", e.mealType.name); put("loggedAt", e.loggedAt.time)
        putBool("isCustomTemplate", e.isCustomTemplate)
        putOpt("foodId", lcOrNull(e.foodId)); putOpt("quantity", e.quantity); putOpt("portionUnit", e.portionUnit)
        putOpt("portionGramWeight", e.portionGramWeight)
        putOpt("fiber", e.fiber); putOpt("sugars", e.sugars); putOpt("sodium", e.sodium)
        putOpt("saturatedFat", e.saturatedFat); putOpt("cholesterol", e.cholesterol)
        putOpt("source", e.source); putOpt("aiConfidence", e.aiConfidence); putOpt("aiGenerationId", e.aiGenerationId)
    }

    suspend fun saveFoodEntry(entry: FoodEntry) = insertFoodEntry(entry)

    fun insertFoodEntry(e: FoodEntry) {
        // Normalize the key: an uppercase local row + lowercase server pull must land as ONE row.
        db().delete("food_entries", "LOWER(id)=? AND id!=?", arrayOf(lc(e.id), lc(e.id)))
        db().insertWithOnConflict("food_entries", null, foodValues(e), SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun deleteFoodEntry(id: String) {
        db().delete("food_entries", "LOWER(id)=?", arrayOf(lc(id)))
    }

    fun nutritionGoal(): NutritionGoal = db().rawQuery("SELECT id, calorieGoal, proteinTarget, fatTarget, carbsTarget FROM nutrition_goals LIMIT 1", null).use { c ->
        if (!c.moveToFirst()) return NutritionGoal()
        NutritionGoal(c.str(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4))
    }

    fun insertNutritionGoal(g: NutritionGoal) = transaction {
        // Single-row table: the latest goal replaces whatever was there.
        db().delete("nutrition_goals", null, null)
        db().insertWithOnConflict("nutrition_goals", null, ContentValues().apply {
            put("id", g.id); put("calorieGoal", g.calorieGoal)
            put("proteinTarget", g.proteinTarget); put("fatTarget", g.fatTarget)
            put("carbsTarget", g.carbsTarget)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // MARK: - Meal slots (date-anchored)

    private const val MEAL_SLOT_COLUMNS = "id, COALESCE(NULLIF(date,''), day), mealType, recipeName, calories, protein, fat, carbs, isAISuggestion, ingredientsJson, rowVersion"

    private fun readMealSlot(c: Cursor) = MealSlot(
        id = c.str(0), date = MealPlanDay.normalize(c.str(1)), mealType = mealType(c.str(2)),
        recipeName = c.str(3), calories = c.getInt(4), protein = c.getDouble(5), fat = c.getDouble(6), carbs = c.getDouble(7),
        isAISuggestion = c.getInt(8) != 0, ingredients = groceryItemsFromJson(c.str(9)), rowVersion = c.str(10)
    )

    /** Slots of the Sunday-start week containing today (legacy day-of-week rows map onto this week). */
    fun weeklyMealSlots(): List<MealSlot> {
        val dates = MealPlanDay.weekDates(Date()).map { MealPlanDay.string(it) }
        return allMealSlots().filter { it.date in dates }
    }

    fun allMealSlots(): List<MealSlot> = db().rawQuery("SELECT $MEAL_SLOT_COLUMNS FROM meal_slots", null).use { c ->
        buildList { while (c.moveToNext()) add(readMealSlot(c)) }
    }

    /** Slots in an inclusive `yyyy-MM-dd` range. */
    fun mealSlots(fromIso: String, toIso: String): List<MealSlot> =
        allMealSlots().filter { it.date >= fromIso && it.date <= toIso }.sortedBy { it.date }

    private fun mealSlotValues(s: MealSlot) = ContentValues().apply {
        put("id", lc(s.id)); put("date", s.date); put("day", s.date); put("mealType", s.mealType.name)
        put("recipeName", s.recipeName); put("calories", s.calories)
        put("protein", s.protein); put("fat", s.fat); put("carbs", s.carbs)
        putBool("isAISuggestion", s.isAISuggestion)
        put("ingredientsJson", groceryItemsToJson(s.ingredients))
        put("rowVersion", s.rowVersion)
    }

    suspend fun saveMealSlot(slot: MealSlot) = insertMealSlot(slot)

    fun insertMealSlot(s: MealSlot) {
        db().delete("meal_slots", "LOWER(id)=? AND id!=?", arrayOf(lc(s.id), lc(s.id)))
        db().insertWithOnConflict("meal_slots", null, mealSlotValues(s), SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun deleteMealSlot(id: String) {
        db().delete("meal_slots", "LOWER(id)=?", arrayOf(lc(id)))
    }

    /** Replace every cached slot in an inclusive date range with the server's week (ids outside the range untouched). */
    fun replaceMealSlots(fromIso: String, toIso: String, slots: List<MealSlot>) = transaction {
        mealSlots(fromIso, toIso).forEach { db().delete("meal_slots", "id=?", arrayOf(it.id)) }
        slots.forEach { insertMealSlot(it) }
    }

    // MARK: - Grocery

    private const val GROCERY_COLUMNS = "id, name, quantity, unit, category, isChecked, rowVersion, groceryListId, sortOrder"

    private fun readGroceryItem(c: Cursor) = GroceryItem(
        id = c.str(0), name = c.str(1), quantity = c.str(2), unit = c.str(3),
        category = GroceryCategory.fromWire(c.str(4)), isChecked = c.getInt(5) != 0,
        rowVersion = c.str(6), groceryListId = c.strOrNull(7), sortOrder = c.intOrNull(8)
    )

    fun groceryItems(listId: String? = null): List<GroceryItem> {
        val sql = StringBuilder("SELECT $GROCERY_COLUMNS FROM grocery_items")
        val args = mutableListOf<String>()
        if (listId != null) { sql.append(" WHERE LOWER(groceryListId)=?"); args.add(lc(listId)) }
        sql.append(" ORDER BY CASE WHEN sortOrder IS NULL THEN 1 ELSE 0 END, sortOrder, name COLLATE NOCASE")
        return db().rawQuery(sql.toString(), args.toTypedArray()).use { c -> buildList { while (c.moveToNext()) add(readGroceryItem(c)) } }
    }

    private fun groceryValues(item: GroceryItem) = ContentValues().apply {
        put("id", lc(item.id)); put("name", item.name); put("quantity", item.quantity)
        put("unit", item.unit); put("category", item.category.name)
        putBool("isChecked", item.isChecked); put("rowVersion", item.rowVersion)
        putOpt("groceryListId", lcOrNull(item.groceryListId)); putOpt("sortOrder", item.sortOrder)
    }

    fun insertGroceryItem(item: GroceryItem) {
        db().insertWithOnConflict("grocery_items", null, groceryValues(item), SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Replace the whole cached grocery list (legacy single-list semantics). */
    suspend fun saveGroceryItems(items: List<GroceryItem>) = transaction {
        db().delete("grocery_items", null, null)
        items.forEach { db().insert("grocery_items", null, groceryValues(it)) }
    }

    fun deleteGroceryItem(id: String) {
        db().delete("grocery_items", "LOWER(id)=?", arrayOf(lc(id)))
    }

    suspend fun toggleGroceryItem(id: String) {
        db().execSQL("UPDATE grocery_items SET isChecked = CASE WHEN isChecked=1 THEN 0 ELSE 1 END WHERE LOWER(id)=?", arrayOf(lc(id)))
    }

    fun groceryLists(): List<GroceryList> = db().rawQuery(
        "SELECT id, name, isArchived, isDefault, itemCount, createdAt, updatedAt, rowVersion FROM grocery_lists ORDER BY isDefault DESC, createdAt", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(GroceryList(c.str(0), c.str(1), c.bool(2), c.bool(3), c.getInt(4),
                c.dateOrNull(5) ?: Date(0), c.dateOrNull(6), c.str(7)))
        }
    }

    fun upsertGroceryList(list: GroceryList) {
        db().insertWithOnConflict("grocery_lists", null, ContentValues().apply {
            put("id", lc(list.id)); put("name", list.name); putBool("isArchived", list.isArchived); putBool("isDefault", list.isDefault)
            put("itemCount", list.itemCount); put("createdAt", list.createdAt.time); putOpt("updatedAt", list.updatedAt?.time)
            put("rowVersion", list.rowVersion)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteGroceryList(id: String) = transaction {
        db().delete("grocery_items", "LOWER(groceryListId)=?", arrayOf(lc(id)))
        db().delete("grocery_lists", "LOWER(id)=?", arrayOf(lc(id)))
    }

    // MARK: - Hydration

    fun waterEntries(forDate: Date): List<WaterIntakeEntry> {
        val start = ProgramDates.startOfDay(forDate.time)
        val end = ProgramDates.addDays(start, 1)
        return db().rawQuery(
            "SELECT id, amount, unit, loggedAt, notes FROM water_entries WHERE loggedAt >= ? AND loggedAt < ? ORDER BY loggedAt DESC",
            arrayOf(start.toString(), end.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(WaterIntakeEntry(id = c.str(0), amount = c.getDouble(1), unit = WaterUnit.fromRaw(c.str(2)),
                    loggedAt = Date(c.getLong(3)), notes = c.str(4)))
            }
        }
    }

    fun saveWaterEntry(entry: WaterIntakeEntry) {
        db().insertWithOnConflict("water_entries", null, ContentValues().apply {
            put("id", lc(entry.id)); put("amount", entry.amount)
            put("unit", entry.unit.name); put("loggedAt", entry.loggedAt.time)
            put("notes", entry.notes)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteWaterEntry(id: String) {
        db().delete("water_entries", "LOWER(id)=?", arrayOf(lc(id)))
    }

    fun hydrationGoal(): HydrationGoal = db().rawQuery("SELECT id, dailyGoal, unit, preferredUnit FROM hydration_goals LIMIT 1", null).use { c ->
        if (!c.moveToFirst()) return HydrationGoal()
        HydrationGoal(c.str(0), c.getDouble(1), WaterUnit.fromRaw(c.str(2)), WaterUnit.fromRawOrNull(c.strOrNull(3)))
    }

    fun saveHydrationGoal(goal: HydrationGoal) = transaction {
        db().delete("hydration_goals", null, null)
        db().insertWithOnConflict("hydration_goals", null, ContentValues().apply {
            put("id", goal.id); put("dailyGoal", goal.dailyGoal); put("unit", goal.unit.name)
            putOpt("preferredUnit", goal.preferredUnit?.name)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // MARK: - Barcode / dashboards / personalization / profile

    fun barcodeProduct(barcode: String): BarcodeProduct? = db().rawQuery("SELECT id, barcode, name, brand, imageUrl, caloriesPer100g, proteinPer100g, fatPer100g, carbsPer100g, servingSizeGrams, servingUnit FROM barcode_products WHERE barcode=?", arrayOf(barcode)).use { c ->
        if (!c.moveToFirst()) return null
        BarcodeProduct(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.getDouble(5), c.getDouble(6), c.getDouble(7), c.getDouble(8), c.getDouble(9), c.str(10))
    }

    fun insertBarcodeProduct(p: BarcodeProduct) {
        db().insertWithOnConflict("barcode_products", null, ContentValues().apply {
            put("id", p.id); put("barcode", p.barcode); put("name", p.name)
            put("brand", p.brand); put("imageUrl", p.imageUrl)
            put("caloriesPer100g", p.caloriesPer100g); put("proteinPer100g", p.proteinPer100g)
            put("fatPer100g", p.fatPer100g); put("carbsPer100g", p.carbsPer100g)
            put("servingSizeGrams", p.servingSizeGrams); put("servingUnit", p.servingUnit)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun dashboards(): List<HomeDashboard> = db().rawQuery("SELECT id, name, progress, unit, goals FROM home_dashboards", null).use { c ->
        buildList { while (c.moveToNext()) add(HomeDashboard(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4))) }
    }

    fun insertDashboard(d: HomeDashboard) {
        db().insertWithOnConflict("home_dashboards", null, ContentValues().apply {
            put("id", d.id); put("name", d.name); put("progress", d.progress)
            put("unit", d.unit); put("goals", d.goals)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun personalizations(): List<Personalization> = db().rawQuery("SELECT id, name, singleSelection, optionsJson FROM personalizations", null).use { c ->
        buildList {
            while (c.moveToNext()) add(Personalization(c.str(0), c.str(1), c.getInt(2) != 0, personalizationItemsFromJson(c.str(3))))
        }
    }

    suspend fun savePersonalizations(personalizations: List<Personalization>) {
        personalizations.forEach { insertPersonalization(it) }
    }

    fun insertPersonalization(p: Personalization) {
        db().insertWithOnConflict("personalizations", null, ContentValues().apply {
            put("id", p.id); put("name", p.name)
            putBool("singleSelection", p.singleSelection)
            put("optionsJson", personalizationItemsToJson(p.options))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Static profile navigation. Falls back to the built-in menu when the
     * table is empty (nothing seeds it any more) so the Profile screen
     * never renders blank.
     */
    fun profileMenuItems(): List<ProfileMenu> {
        val stored = db().rawQuery("SELECT menuId, text FROM profile_menus ORDER BY menuId", null).use { c ->
            buildList { while (c.moveToNext()) add(ProfileMenu(c.getInt(0), c.str(1))) }
        }
        return stored.ifEmpty { com.fitnesslink.fit.data.MockDataProvider.profileMenuItems }
    }

    fun insertProfileMenu(m: ProfileMenu) {
        db().insertWithOnConflict("profile_menus", null, ContentValues().apply {
            put("menuId", m.id); put("text", m.text)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // MARK: - Calendar

    private const val CALENDAR_COLUMNS = "id, title, programId, workoutId, mealPlanId, status, scheduledDate, workoutImageId, workoutThumbnailId, " +
        "programImageId, programThumbnailId, programEnrollmentId, durationMinutes, programWeekNumber, programDayNumber, deviceEventId"

    private fun readCalendarContent(c: Cursor) = FitnessContent(
        id = c.str(0), title = c.str(1), programId = c.str(2), workoutId = c.str(3), mealPlanId = c.str(4), status = c.str(5),
        scheduledDate = c.longOrNull(6), workoutImageId = c.strOrNull(7), workoutThumbnailId = c.strOrNull(8),
        programImageId = c.strOrNull(9), programThumbnailId = c.strOrNull(10), programEnrollmentId = c.strOrNull(11),
        durationMinutes = c.intOrNull(12), programWeekNumber = c.intOrNull(13), programDayNumber = c.intOrNull(14),
        deviceEventId = c.strOrNull(15)
    )

    fun calendarContent(): List<FitnessContent> = db().rawQuery("SELECT $CALENDAR_COLUMNS FROM calendar_content ORDER BY scheduledDate", null).use { c ->
        buildList { while (c.moveToNext()) add(readCalendarContent(c)) }
    }

    fun calendarContent(forDate: Long): List<FitnessContent> {
        val start = ProgramDates.startOfDay(forDate)
        val end = ProgramDates.addDays(start, 1)
        return db().rawQuery(
            "SELECT $CALENDAR_COLUMNS FROM calendar_content WHERE scheduledDate >= ? AND scheduledDate < ? ORDER BY scheduledDate",
            arrayOf(start.toString(), end.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(readCalendarContent(c)) } }
    }

    fun calendarContentById(id: String): FitnessContent? = db().rawQuery("SELECT $CALENDAR_COLUMNS FROM calendar_content WHERE LOWER(id)=?", arrayOf(lc(id))).use { c ->
        if (c.moveToFirst()) readCalendarContent(c) else null
    }

    fun calendarContentForEnrollment(enrollmentId: String): List<FitnessContent> = db().rawQuery(
        "SELECT $CALENDAR_COLUMNS FROM calendar_content WHERE LOWER(programEnrollmentId)=? ORDER BY scheduledDate", arrayOf(lc(enrollmentId))
    ).use { c -> buildList { while (c.moveToNext()) add(readCalendarContent(c)) } }

    fun calendarContentForProgram(programId: String): List<FitnessContent> = db().rawQuery(
        "SELECT $CALENDAR_COLUMNS FROM calendar_content WHERE LOWER(programId)=? ORDER BY scheduledDate", arrayOf(lc(programId))
    ).use { c -> buildList { while (c.moveToNext()) add(readCalendarContent(c)) } }

    /** Entries in an inclusive millis range (sync pulls, month views). */
    fun calendarContent(fromMillis: Long, toMillis: Long): List<FitnessContent> = db().rawQuery(
        "SELECT $CALENDAR_COLUMNS FROM calendar_content WHERE scheduledDate >= ? AND scheduledDate <= ? ORDER BY scheduledDate",
        arrayOf(fromMillis.toString(), toMillis.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(readCalendarContent(c)) } }

    /**
     * Span of every program entry on the calendar — tells a genuine program
     * rest day from a plain empty day. Reads `calendar_content` (synced)
     * rather than `program_schedules` so it works on a fresh install.
     */
    fun programCalendarSpan(): Pair<Long, Long>? = db().rawQuery(
        "SELECT MIN(scheduledDate), MAX(scheduledDate) FROM calendar_content WHERE (COALESCE(programEnrollmentId,'')!='' OR COALESCE(programId,'')!='') AND scheduledDate IS NOT NULL", null
    ).use { c ->
        if (!c.moveToFirst() || c.isNull(0) || c.isNull(1)) return null
        val min = c.getLong(0); val max = c.getLong(1)
        if (min <= 0 || max <= 0) null else min to max
    }

    fun calendarScheduledDays(year: Int, month: Int): Set<Int> {
        val cal = java.util.Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfMonth = cal.timeInMillis
        cal.add(java.util.Calendar.MONTH, 1)
        val endOfMonth = cal.timeInMillis
        return db().rawQuery(
            "SELECT scheduledDate FROM calendar_content WHERE scheduledDate >= ? AND scheduledDate < ? AND scheduledDate IS NOT NULL",
            arrayOf(startOfMonth.toString(), endOfMonth.toString())
        ).use { c ->
            val days = mutableSetOf<Int>()
            val dayCal = java.util.Calendar.getInstance()
            while (c.moveToNext()) {
                dayCal.timeInMillis = c.getLong(0)
                days.add(dayCal.get(java.util.Calendar.DAY_OF_MONTH))
            }
            days
        }
    }

    /** Insert / update a calendar entry. The device-local `deviceEventId` survives a server refresh that omits it. */
    fun insertCalendarContent(c: FitnessContent) {
        val existingDevice = if (c.deviceEventId == null) db().rawQuery("SELECT deviceEventId FROM calendar_content WHERE LOWER(id)=?", arrayOf(lc(c.id)))
            .use { cur -> if (cur.moveToFirst()) cur.strOrNull(0) else null } else null
        db().delete("calendar_content", "LOWER(id)=? AND id!=?", arrayOf(lc(c.id), lc(c.id)))
        db().insertWithOnConflict("calendar_content", null, ContentValues().apply {
            put("id", lc(c.id)); put("title", c.title); put("programId", lc(c.programId))
            put("workoutId", lc(c.workoutId)); put("mealPlanId", lc(c.mealPlanId)); put("status", c.status)
            putOpt("scheduledDate", c.scheduledDate)
            putOpt("workoutImageId", lcOrNull(c.workoutImageId)); putOpt("workoutThumbnailId", lcOrNull(c.workoutThumbnailId))
            putOpt("programImageId", lcOrNull(c.programImageId)); putOpt("programThumbnailId", lcOrNull(c.programThumbnailId))
            putOpt("programEnrollmentId", lcOrNull(c.programEnrollmentId)); putOpt("durationMinutes", c.durationMinutes)
            putOpt("programWeekNumber", c.programWeekNumber); putOpt("programDayNumber", c.programDayNumber)
            putOpt("deviceEventId", c.deviceEventId ?: existingDevice)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateCalendarStatus(id: String, status: String) {
        db().execSQL("UPDATE calendar_content SET status=? WHERE LOWER(id)=?", arrayOf(status, lc(id)))
    }

    fun updateCalendarScheduledDate(id: String, scheduledDate: Long) {
        db().execSQL("UPDATE calendar_content SET scheduledDate=? WHERE LOWER(id)=?", arrayOf(scheduledDate, lc(id)))
    }

    fun updateCalendarDeviceEventId(id: String, deviceEventId: String?) {
        db().update("calendar_content", ContentValues().apply { putOpt("deviceEventId", deviceEventId) }, "LOWER(id)=?", arrayOf(lc(id)))
    }

    fun deleteCalendarContent(id: String) {
        db().delete("calendar_content", "LOWER(id)=?", arrayOf(lc(id)))
    }

    fun deleteCalendarContentForEnrollment(enrollmentId: String) {
        db().delete("calendar_content", "LOWER(programEnrollmentId)=?", arrayOf(lc(enrollmentId)))
    }

    // MARK: - Users

    data class UserInfo(
        val id: String, val name: String, val email: String, val isPersonalized: Boolean,
        val firstName: String = "", val lastName: String = "", val phone: String = "",
        val username: String = "", val country: String = "", val profileImageId: String = "",
        val isActive: Boolean = true
    )

    fun user(): UserInfo? = db().rawQuery("""
        SELECT id, name, email, isPersonalized, firstName, lastName,
               COALESCE(phone,''), username, COALESCE(country,''),
               COALESCE(profileImageId,''), isActive
        FROM users LIMIT 1
    """, null).use { c ->
        if (!c.moveToFirst()) return null
        UserInfo(c.str(0), c.str(1), c.str(2), c.getInt(3) != 0,
            c.str(4), c.str(5), c.str(6), c.str(7), c.str(8), c.str(9), c.getInt(10) != 0)
    }

    fun updateUserProfile(id: String, firstName: String, lastName: String, email: String,
                          phone: String, username: String, country: String) {
        db().execSQL("UPDATE users SET firstName=?, lastName=?, name=?, email=?, phone=?, username=?, country=? WHERE id=?",
            arrayOf(firstName, lastName, "$firstName $lastName", email, phone, username, country, id))
    }

    fun updateProfileImage(userId: String, imageId: String) {
        db().execSQL("UPDATE users SET profileImageId=? WHERE id=?", arrayOf(imageId, userId))
    }

    suspend fun setUserPersonalized(userId: String) {
        db().execSQL("UPDATE users SET isPersonalized=1 WHERE id=?", arrayOf(userId))
    }

    suspend fun saveUser(id: String, name: String, email: String) {
        upsert("users", "id", id, ContentValues().apply { put("name", name); put("email", email) })
        db().execSQL("UPDATE users SET isPersonalized=COALESCE(isPersonalized,0) WHERE id=?", arrayOf(id))
    }

    fun insertUser(id: String, name: String, email: String) {
        db().insertWithOnConflict("users", null, ContentValues().apply {
            put("id", id); put("name", name); put("email", email); put("isPersonalized", 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertFullUser(id: String, firstName: String, lastName: String, email: String,
                       phone: String, username: String, country: String) {
        db().insertWithOnConflict("users", null, ContentValues().apply {
            put("id", id); put("name", "$firstName $lastName"); put("email", email)
            put("isPersonalized", 0); put("firstName", firstName); put("lastName", lastName)
            put("phone", phone); put("username", username); put("country", country)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveUserFromApi(user: com.fitnesslink.fit.model.api.FLUser) {
        val id = user.id.toString()
        upsert("users", "id", id, ContentValues().apply {
            put("name", "${user.firstName} ${user.lastName}"); put("email", user.email)
            put("isPersonalized", if (user.requirePersonalization) 0 else 1)
            put("firstName", user.firstName); put("lastName", user.lastName)
            put("phone", user.phone ?: ""); put("username", user.username)
            put("country", user.country ?: ""); put("profileImageId", user.profileImageId?.toString() ?: "")
            putBool("isActive", user.isActive)
        })
    }

    fun catalogPrograms(): List<CatalogItem> = allPrograms().map {
        CatalogItem(it.id, it.name, CatalogItem.Kind.PROGRAM, it.time, imageId = it.imageId, thumbnailId = it.thumbnailId,
            sharedByUserId = it.sharedByUserId, createdAt = it.createdAt, isFavorite = it.isFavorite)
    }

    fun catalogWorkouts(): List<CatalogItem> = allWorkouts().map {
        CatalogItem(it.id, it.name, CatalogItem.Kind.WORKOUT, it.time, imageId = it.imageId, thumbnailId = it.thumbnailId,
            sharedByUserId = it.sharedByUserId, createdAt = it.createdAt, isFavorite = it.isFavorite)
    }

    fun catalogMovements(): List<CatalogItem> = allMovements().map {
        CatalogItem(it.id, it.name, CatalogItem.Kind.MOVEMENT, it.description, imageId = it.imageId, thumbnailId = it.thumbnailId,
            imageUrl = it.imageUrl, thumbnailUrl = it.thumbnailUrl, sharedByUserId = it.sharedByUserId, createdAt = it.createdAt,
            isFavorite = it.isFavorite, anatomyIds = it.anatomyIds.toSet(), equipmentIds = it.equipmentIds.toSet())
    }

    // MARK: - JSON Serialization

    private val phasesType = object : TypeToken<List<WorkoutPhase>>() {}.type

    /** Full-fidelity phase cache: every [WorkoutTask] field round-trips through Gson. */
    private fun phasesToJson(phases: List<WorkoutPhase>): String = gson.toJson(phases, phasesType)

    private fun phasesFromJson(json: String): List<WorkoutPhase> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try { gson.fromJson<List<WorkoutPhase>>(json, phasesType) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    private fun personalizationItemsToJson(items: List<PersonalizationItem>): String {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().apply { put("id", it.id); put("text", it.text); put("selected", it.selected) }) }
        return arr.toString()
    }

    private fun personalizationItemsFromJson(json: String): List<PersonalizationItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PersonalizationItem(o.getString("id"), o.getString("text"), o.optBoolean("selected"))
        }
    }

    private val groceryListType = object : TypeToken<List<GroceryItem>>() {}.type

    private fun groceryItemsToJson(items: List<GroceryItem>): String = gson.toJson(items, groceryListType)

    private fun groceryItemsFromJson(json: String): List<GroceryItem> {
        if (json.isBlank()) return emptyList()
        return try { gson.fromJson<List<GroceryItem>>(json, groceryListType) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    // MARK: - Movements

    private const val MOVEMENT_COLUMNS = "m.id, m.name, COALESCE(m.description,''), m.isFavorite, m.imageId, m.thumbnailId, m.videoId, m.phoneVideoId, " +
        "m.contributorId, m.sharedByUserId, m.createdAt, m.sortIndex"

    private fun readMovement(c: Cursor): MovementLibraryItem {
        val id = c.str(0)
        return MovementLibraryItem(
            id = id, name = c.str(1), description = c.str(2), anatomyIds = anatomyIds(id), equipmentIds = equipmentIds(id),
            isFavorite = c.bool(3), imageId = c.strOrNull(4), thumbnailId = c.strOrNull(5), videoId = c.strOrNull(6),
            phoneVideoId = c.strOrNull(7), contributorId = c.strOrNull(8), sharedByUserId = c.strOrNull(9),
            createdAt = c.dateOrNull(10), sortIndex = c.intOrNull(11)
        )
    }

    private const val MOVEMENT_ORDER = " ORDER BY CASE WHEN m.sortIndex IS NULL THEN 1 ELSE 0 END, m.sortIndex, m.name COLLATE NOCASE"

    fun allMovements(): List<MovementLibraryItem> = db().rawQuery(
        "SELECT $MOVEMENT_COLUMNS FROM movements m WHERE m.isDeleted=0$MOVEMENT_ORDER", null
    ).use { c -> buildList { while (c.moveToNext()) add(readMovement(c)) } }

    fun movement(id: String): MovementLibraryItem? = db().rawQuery(
        "SELECT $MOVEMENT_COLUMNS FROM movements m WHERE LOWER(m.id)=? AND m.isDeleted=0", arrayOf(lc(id))
    ).use { c -> if (c.moveToFirst()) readMovement(c) else null }

    /**
     * Name search with optional tag filters. [anatomyIds] / [equipmentIds]
     * are lowercased tag GUIDs (any match). The legacy free-text
     * `muscleGroup` / `equipment` strings are accepted for compile
     * compatibility and matched against the legacy columns only.
     */
    fun searchMovements(
        query: String, muscleGroup: String? = null, equipment: String? = null,
        anatomyIds: Collection<String> = emptyList(), equipmentIds: Collection<String> = emptyList(),
        mineOnly: Boolean = false
    ): List<MovementLibraryItem> {
        val sb = StringBuilder("SELECT $MOVEMENT_COLUMNS FROM movements m WHERE m.isDeleted=0")
        val args = mutableListOf<String>()
        if (query.isNotEmpty()) { sb.append(" AND m.name LIKE ?"); args.add("%$query%") }
        if (!muscleGroup.isNullOrEmpty()) { sb.append(" AND m.muscleGroup=?"); args.add(muscleGroup) }
        if (!equipment.isNullOrEmpty()) { sb.append(" AND m.equipment=?"); args.add(equipment) }
        if (anatomyIds.isNotEmpty()) {
            sb.append(" AND LOWER(m.id) IN (SELECT movementId FROM movement_anatomy WHERE anatomyId IN (${anatomyIds.joinToString(",") { "?" }}))")
            args.addAll(anatomyIds.map { lc(it) })
        }
        if (equipmentIds.isNotEmpty()) {
            sb.append(" AND LOWER(m.id) IN (SELECT movementId FROM movement_equipment WHERE equipmentId IN (${equipmentIds.joinToString(",") { "?" }}))")
            args.addAll(equipmentIds.map { lc(it) })
        }
        if (mineOnly) sb.append(" AND (m.contributorId IS NULL OR m.contributorId='') AND (m.sharedByUserId IS NULL OR m.sharedByUserId='')")
        sb.append(MOVEMENT_ORDER)
        return db().rawQuery(sb.toString(), args.toTypedArray()).use { c -> buildList { while (c.moveToNext()) add(readMovement(c)) } }
    }

    fun favoriteMovements(): List<MovementLibraryItem> = db().rawQuery(
        "SELECT $MOVEMENT_COLUMNS FROM movements m WHERE m.isFavorite=1 AND m.isDeleted=0$MOVEMENT_ORDER", null
    ).use { c -> buildList { while (c.moveToNext()) add(readMovement(c)) } }

    fun recentMovements(limit: Int = 20): List<MovementLibraryItem> = db().rawQuery(
        """SELECT $MOVEMENT_COLUMNS FROM recent_movements r JOIN movements m ON LOWER(m.id)=LOWER(r.movementId)
           WHERE m.isDeleted=0 ORDER BY r.addedAt DESC LIMIT ?""", arrayOf(limit.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(readMovement(c)) } }

    fun toggleMovementFavorite(id: String) {
        db().execSQL("UPDATE movements SET isFavorite = CASE WHEN isFavorite=1 THEN 0 ELSE 1 END WHERE LOWER(id)=?", arrayOf(lc(id)))
    }

    fun setMovementFavorite(id: String, favorited: Boolean) {
        db().execSQL("UPDATE movements SET isFavorite=? WHERE LOWER(id)=?", arrayOf(if (favorited) 1 else 0, lc(id)))
    }

    fun replaceFavoriteMovements(favoritedIds: Collection<String>) = transaction {
        db().execSQL("UPDATE movements SET isFavorite=0")
        favoritedIds.forEach { db().execSQL("UPDATE movements SET isFavorite=1 WHERE LOWER(id)=?", arrayOf(lc(it))) }
    }

    fun setMovementSortOrder(orderedIds: List<String>) = transaction {
        db().execSQL("UPDATE movements SET sortIndex=NULL")
        orderedIds.forEachIndexed { i, id -> db().execSQL("UPDATE movements SET sortIndex=? WHERE LOWER(id)=?", arrayOf(i, lc(id))) }
    }

    fun logRecentMovement(movementId: String) {
        db().insertWithOnConflict("recent_movements", null, ContentValues().apply {
            put("movementId", lc(movementId)); put("addedAt", System.currentTimeMillis().toDouble() / 1000.0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Replace the recents list from the server (`/movements/recent`). */
    fun replaceRecentMovements(rows: List<Pair<String, Long>>) = transaction {
        db().delete("recent_movements", null, null)
        rows.forEach { (id, accessedAt) ->
            db().insert("recent_movements", null, ContentValues().apply { put("movementId", lc(id)); put("addedAt", accessedAt / 1000.0) })
        }
    }

    /** Legacy 5-string signature kept for the pre-parity browser / create-exercise flow. */
    fun insertMovementFull(id: String, name: String, description: String, muscleGroup: String, equipment: String) {
        insertMovementFull(id = id, name = name, description = description)
        if (muscleGroup.isNotEmpty() || equipment.isNotEmpty()) {
            db().execSQL("UPDATE movements SET muscleGroup=?, equipment=? WHERE LOWER(id)=?", arrayOf(muscleGroup, equipment, lc(id)))
        }
    }

    /**
     * Cache a movement with its tag joins. Preserves the local favorite flag,
     * server rank and cached `createdAt` across the write (iOS parity).
     */
    fun insertMovementFull(
        id: String, name: String, description: String,
        anatomyIds: List<String> = emptyList(), equipmentIds: List<String> = emptyList(),
        imageId: String? = null, thumbnailId: String? = null, videoId: String? = null,
        contributorId: String? = null, sharedByUserId: String? = null, phoneVideoId: String? = null,
        createdAt: Date? = null, summary: String? = null, statusId: String? = null
    ) {
        val cached = db().rawQuery("SELECT isFavorite, sortIndex, createdAt, id FROM movements WHERE LOWER(id)=?", arrayOf(lc(id)))
            .use { c -> if (c.moveToFirst()) Triple(c.bool(0), c.intOrNull(1), c.longOrNull(2)) else null }
        val values = ContentValues().apply {
            put("name", name); put("description", description)
            putOpt("imageId", lcOrNull(imageId)); putOpt("thumbnailId", lcOrNull(thumbnailId)); putOpt("videoId", lcOrNull(videoId))
            putOpt("contributorId", lcOrNull(contributorId)); putOpt("sharedByUserId", lcOrNull(sharedByUserId))
            putOpt("phoneVideoId", lcOrNull(phoneVideoId)); putOpt("statusId", lcOrNull(statusId))
            put("isDeleted", 0); putBool("isFavorite", cached?.first ?: false)
            putOpt("sortIndex", cached?.second); putOpt("createdAt", createdAt?.time ?: cached?.third)
            putOpt("summary", summary)
        }
        upsert("movements", "id", if (cached != null) existingId("movements", id) else lc(id), values)
        replaceMovementAnatomy(id, anatomyIds)
        replaceMovementEquipment(id, equipmentIds)
    }

    fun insertMovement(m: Movement) = insertMovementFull(
        id = m.id.toString(), name = m.name, description = m.description ?: "",
        anatomyIds = m.anatomyIds.map { it.toString() }, equipmentIds = m.equipmentIds.map { it.toString() },
        imageId = m.imageId?.toString(), thumbnailId = m.thumbnailId?.toString(), videoId = m.videoId?.toString(),
        contributorId = m.contributorId?.toString(), sharedByUserId = m.sharedByUserId?.toString(),
        phoneVideoId = m.phoneVideoId?.toString(), createdAt = m.audit?.timestamp, summary = m.summary,
        statusId = m.statusId?.toString()
    ).also { if (m.isFavorite == true) setMovementFavorite(m.id.toString(), true) }

    fun deleteMovement(id: String) = transaction {
        db().delete("movement_anatomy", "movementId=?", arrayOf(lc(id)))
        db().delete("movement_equipment", "movementId=?", arrayOf(lc(id)))
        db().delete("movements", "LOWER(id)=?", arrayOf(lc(id)))
    }

    // Movement <-> Anatomy / Equipment joins (ids lowercased)

    fun anatomyIds(movementId: String): List<String> = db().rawQuery(
        "SELECT anatomyId FROM movement_anatomy WHERE movementId=?", arrayOf(lc(movementId))
    ).use { c -> buildList { while (c.moveToNext()) add(c.str(0)) } }

    fun equipmentIds(movementId: String): List<String> = db().rawQuery(
        "SELECT equipmentId FROM movement_equipment WHERE movementId=?", arrayOf(lc(movementId))
    ).use { c -> buildList { while (c.moveToNext()) add(c.str(0)) } }

    private fun replaceMovementAnatomy(movementId: String, anatomyIds: List<String>) {
        db().delete("movement_anatomy", "movementId=?", arrayOf(lc(movementId)))
        anatomyIds.forEach {
            db().insertWithOnConflict("movement_anatomy", null, ContentValues().apply { put("movementId", lc(movementId)); put("anatomyId", lc(it)) }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    private fun replaceMovementEquipment(movementId: String, equipmentIds: List<String>) {
        db().delete("movement_equipment", "movementId=?", arrayOf(lc(movementId)))
        equipmentIds.forEach {
            db().insertWithOnConflict("movement_equipment", null, ContentValues().apply { put("movementId", lc(movementId)); put("equipmentId", lc(it)) }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun allAnatomy(): List<Anatomy> = db().rawQuery("SELECT id, name, sortOrder FROM anatomy ORDER BY CASE WHEN sortOrder IS NULL THEN 1 ELSE 0 END, sortOrder, name", null).use { c ->
        buildList { while (c.moveToNext()) { uuidOrNull(c.str(0))?.let { add(Anatomy(it, c.str(1), c.intOrNull(2))) } } }
    }

    fun allEquipment(): List<Equipment> = db().rawQuery("SELECT id, name FROM equipment ORDER BY name", null).use { c ->
        buildList { while (c.moveToNext()) { uuidOrNull(c.str(0))?.let { add(Equipment(it, c.str(1))) } } }
    }

    fun replaceAnatomy(rows: List<Anatomy>) = transaction {
        db().delete("anatomy", null, null)
        rows.forEach { db().insert("anatomy", null, ContentValues().apply { put("id", lc(it.id.toString())); put("name", it.name); putOpt("sortOrder", it.sortOrder) }) }
    }

    fun replaceEquipment(rows: List<Equipment>) = transaction {
        db().delete("equipment", null, null)
        rows.forEach { db().insert("equipment", null, ContentValues().apply { put("id", lc(it.id.toString())); put("name", it.name) }) }
    }

    // MARK: - Workout phases catalog

    fun availableWorkoutPhases(): List<AvailableWorkoutPhase> = db().rawQuery(
        "SELECT id, name, sortOrder, isSystem FROM workout_phases WHERE deletedLocally=0 ORDER BY isSystem DESC, sortOrder, name", null
    ).use { c -> buildList { while (c.moveToNext()) add(AvailableWorkoutPhase(c.str(0), c.str(1), c.getInt(2), c.bool(3))) } }

    fun upsertWorkoutPhase(phase: AvailableWorkoutPhase, syncState: String = "synced") {
        db().insertWithOnConflict("workout_phases", null, ContentValues().apply {
            put("id", lc(phase.id)); put("name", phase.name); put("sortOrder", phase.sortOrder); putBool("isSystem", phase.isSystem)
            put("syncState", syncState); put("lastUpdatedAt", System.currentTimeMillis()); put("deletedLocally", 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun replaceWorkoutPhases(phases: List<AvailableWorkoutPhase>) = transaction {
        db().delete("workout_phases", "syncState='synced'", null)
        phases.forEach { upsertWorkoutPhase(it) }
    }

    fun deleteWorkoutPhase(id: String) {
        db().delete("workout_phases", "LOWER(id)=?", arrayOf(lc(id)))
    }

    // MARK: - Notifications

    fun allNotifications(type: String? = null): List<NotificationItem> {
        val sb = StringBuilder("SELECT id, type, title, body, isRead, createdAt, deepLink FROM notifications")
        val args = mutableListOf<String>()
        if (type != null) { sb.append(" WHERE type=?"); args.add(type) }
        sb.append(" ORDER BY createdAt DESC")
        return db().rawQuery(sb.toString(), args.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) add(NotificationItem(
                    id = c.str(0), type = NotificationType.fromString(c.str(1)), title = c.str(2), body = c.str(3),
                    isRead = c.getInt(4) != 0, createdAt = Date(c.getLong(5)), deepLink = c.str(6).ifEmpty { null }
                ))
            }
        }
    }

    fun unreadNotificationCount(): Int = db().rawQuery(
        "SELECT COUNT(*) FROM notifications WHERE isRead=0", null
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun markNotificationRead(id: String) {
        db().execSQL("UPDATE notifications SET isRead=1 WHERE LOWER(id)=?", arrayOf(lc(id)))
    }

    fun markAllNotificationsRead() {
        db().execSQL("UPDATE notifications SET isRead=1")
    }

    fun deleteNotification(id: String) {
        db().delete("notifications", "LOWER(id)=?", arrayOf(lc(id)))
    }

    fun insertNotification(id: String, type: String, title: String, body: String,
                           isRead: Boolean, createdAt: Long, deepLink: String?) {
        db().insertWithOnConflict("notifications", null, ContentValues().apply {
            put("id", lc(id)); put("type", type); put("title", title); put("body", body)
            putBool("isRead", isRead); put("createdAt", createdAt)
            deepLink?.let { put("deepLink", it) }
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // MARK: - Notification preferences

    fun notificationPreference(): GoalNotificationPreference? = db().rawQuery(
        "SELECT id, userId, coachingTone, maxDailyNotifications, quietHoursStart, quietHoursEnd, enableHabitReminders, enableStreakAlerts, enableMilestones, enableAiCoaching, enableReengagement, enableGoalCheckIns, enableSystem, enableGoals, enableContent, enableCalendar FROM notification_preferences LIMIT 1", null
    ).use { c ->
        if (!c.moveToFirst()) return null
        GoalNotificationPreference(
            id = c.str(0), userId = c.str(1),
            coachingTone = runCatching { CoachingTone.valueOf(c.str(2)) }.getOrDefault(CoachingTone.EncouragingCoach),
            maxDailyNotifications = c.getInt(3), quietHoursStart = c.strOrNull(4), quietHoursEnd = c.strOrNull(5),
            enableHabitReminders = c.bool(6), enableStreakAlerts = c.bool(7), enableMilestones = c.bool(8),
            enableAiCoaching = c.bool(9), enableReengagement = c.bool(10), enableGoalCheckIns = c.bool(11),
            enableSystem = if (c.isNull(12)) true else c.bool(12), enableGoals = if (c.isNull(13)) true else c.bool(13),
            enableContent = if (c.isNull(14)) true else c.bool(14), enableCalendar = if (c.isNull(15)) true else c.bool(15)
        )
    }

    fun saveNotificationPreference(p: GoalNotificationPreference) = transaction {
        db().delete("notification_preferences", null, null)
        db().insertWithOnConflict("notification_preferences", null, ContentValues().apply {
            put("id", p.id); put("userId", p.userId); put("coachingTone", p.coachingTone.name)
            put("maxDailyNotifications", p.maxDailyNotifications)
            putOpt("quietHoursStart", p.quietHoursStart); putOpt("quietHoursEnd", p.quietHoursEnd)
            putBool("enableHabitReminders", p.enableHabitReminders); putBool("enableStreakAlerts", p.enableStreakAlerts)
            putBool("enableMilestones", p.enableMilestones); putBool("enableAiCoaching", p.enableAiCoaching)
            putBool("enableReengagement", p.enableReengagement); putBool("enableGoalCheckIns", p.enableGoalCheckIns)
            putBool("enableSystem", p.enableSystem); putBool("enableGoals", p.enableGoals)
            putBool("enableContent", p.enableContent); putBool("enableCalendar", p.enableCalendar)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // MARK: - Goals / habits / achievements / body tracking (sync-apply targets)

    fun insertGoal(g: Goal) {
        db().insertWithOnConflict("goals", null, ContentValues().apply {
            put("id", lc(g.id)); put("userId", g.userId); put("goalType", g.goalType.name); put("title", g.title)
            put("description", g.description); putOpt("targetValue", g.targetValue); putOpt("targetUnit", g.targetUnit)
            put("currentValue", g.currentValue); put("startDate", g.startDate); putOpt("targetDate", g.targetDate)
            putOpt("completedDate", g.completedDate); put("status", g.status.name); putOpt("identityStatement", g.identityStatement)
            put("trajectoryStatus", g.trajectoryStatus.name); put("createdAt", g.createdAt); put("updatedAt", g.updatedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertHabit(h: Habit) {
        db().insertWithOnConflict("habits", null, ContentValues().apply {
            put("id", lc(h.id)); putOpt("goalId", lcOrNull(h.goalId)); put("userId", h.userId); put("title", h.title)
            put("description", h.description); put("habitType", h.habitType.name); putOpt("anchorBehavior", h.anchorBehavior)
            put("frequencyType", h.frequencyType.name); putOpt("frequencyDaysJson", h.frequencyDays?.let { JSONArray(it).toString() })
            putOpt("targetValue", h.targetValue); putOpt("targetUnit", h.targetUnit); put("tier", h.tier.name)
            put("estimatedMinutes", h.estimatedMinutes); putBool("isActive", h.isActive); put("sortOrder", h.sortOrder)
            put("createdAt", h.createdAt); put("updatedAt", h.updatedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertHabitLog(l: HabitLog) {
        db().insertWithOnConflict("habit_logs", null, ContentValues().apply {
            put("id", lc(l.id)); put("habitId", lc(l.habitId)); put("userId", l.userId); put("completedAt", l.completedAt)
            put("completionType", l.completionType.name); putOpt("actualValue", l.actualValue); putOpt("notes", l.notes)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertAchievement(a: Achievement) {
        db().insertWithOnConflict("achievements", null, ContentValues().apply {
            put("id", lc(a.id)); put("userId", a.userId); put("achievementType", a.achievementType?.name ?: "")
            put("title", a.title); put("description", a.description); put("iconName", a.iconName)
            put("earnedAt", a.earnedAt); putBool("sharedToFeed", a.sharedToFeed)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun achievements(): List<Achievement> = db().rawQuery(
        "SELECT id, userId, achievementType, title, COALESCE(description,''), COALESCE(iconName,''), earnedAt, COALESCE(sharedToFeed,0) FROM achievements ORDER BY earnedAt DESC", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(Achievement(c.str(0), c.str(1), AchievementType.fromWire(c.str(2)), c.str(3), c.str(4), c.str(5), c.getLong(6), c.bool(7)))
        }
    }

    fun insertWeightEntry(w: WeightEntry) {
        db().insertWithOnConflict("weight_entries", null, ContentValues().apply {
            put("id", lc(w.id)); put("userId", w.userId); put("weight", w.weight); put("unit", w.unit.name)
            put("date", w.date.time); put("notes", w.notes); put("createdAt", w.createdAt.time)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertMeasurementEntry(m: MeasurementEntry) {
        db().insertWithOnConflict("measurement_entries", null, ContentValues().apply {
            put("id", lc(m.id)); put("userId", m.userId); put("date", m.date.time)
            put("measurementsJson", gson.toJson(m.measurements)); put("notes", m.notes); put("createdAt", m.createdAt.time)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertProgressPhotoEntry(p: ProgressPhotoEntry) {
        db().insertWithOnConflict("progress_photo_entries", null, ContentValues().apply {
            put("id", lc(p.id)); put("userId", p.userId); put("date", p.date.time)
            put("photosJson", gson.toJson(p.photos.map { it.copy(imageData = null) })); put("notes", p.notes); put("createdAt", p.createdAt.time)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Distinct local-day completion timestamps for a habit, as start-of-day
     * millis (habit detail heatmap).
     */
    fun habitLogDates(habitId: String, since: Long): Set<Long> {
        return db().rawQuery(
            "SELECT completedAt FROM habit_logs WHERE habitId=? AND completedAt>=?",
            arrayOf(habitId, since.toString())
        ).use { c ->
            val days = mutableSetOf<Long>()
            while (c.moveToNext()) days.add(ProgramDates.startOfDay(c.getLong(0)))
            days
        }
    }

    // MARK: - Reset

    fun clearUserData() {
        val tables = listOf("users", "home_dashboards", "programs", "workouts", "food_entries",
            "nutrition_goals", "meal_slots", "grocery_items", "grocery_lists", "barcode_products",
            "personalizations", "calendar_content", "profile_menus",
            "workout_sessions", "workout_session_history", "movements",
            "movement_anatomy", "movement_equipment", "anatomy", "equipment",
            "user_preferences", "user_personalizations", "program_schedules",
            "recent_movements", "notifications",
            "goals", "habits", "habit_logs", "streaks", "milestones",
            "achievements", "weight_entries", "measurement_entries",
            "progress_photo_entries", "notification_preferences", "sync_queue",
            "water_entries", "hydration_goals", "user_programs", "workout_phases")
        for (table in tables) {
            try { db().execSQL("DELETE FROM $table") } catch (_: Exception) {}
        }
    }

    // MARK: - Sync Queue

    /** Enqueue a durable mutation; returns the queue row id (the push idempotency key). */
    fun enqueueSyncEntry(entityType: String, entityId: String, action: String, payload: String): String {
        val id = UUID.randomUUID().toString()
        db().insertWithOnConflict("sync_queue", null, ContentValues().apply {
            put("id", id)
            put("entityType", entityType); put("entityId", lc(entityId))
            put("action", action); put("payload", payload)
            put("createdAt", System.currentTimeMillis()); put("synced", 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        return id
    }

    data class SyncQueueEntry(
        val id: String, val entityType: String, val entityId: String,
        val action: String, val payload: String, val createdAt: Long
    )

    fun pendingSyncEntries(): List<SyncQueueEntry> = pendingSyncEntries(null)

    /** Unsynced queue rows, oldest first; [entityType] matches both hyphen / underscore spellings of a domain. */
    fun pendingSyncEntries(entityType: String?): List<SyncQueueEntry> {
        val sb = StringBuilder("SELECT id, entityType, entityId, action, payload, createdAt FROM sync_queue WHERE synced=0")
        val args = mutableListOf<String>()
        if (entityType != null) {
            val spellings = entityTypeSpellings(entityType)
            sb.append(" AND entityType IN (${spellings.joinToString(",") { "?" }})")
            args.addAll(spellings)
        }
        sb.append(" ORDER BY createdAt ASC")
        return db().rawQuery(sb.toString(), args.toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) add(SyncQueueEntry(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.getLong(5))) }
        }
    }

    fun markSyncEntrySynced(id: String) {
        db().execSQL("UPDATE sync_queue SET synced=1 WHERE id=?", arrayOf(id))
    }

    fun deleteSyncEntry(id: String) {
        db().delete("sync_queue", "id=?", arrayOf(id))
    }

    fun pruneSyncedEntries(olderThanDays: Int = 7) {
        val cutoff = System.currentTimeMillis() - olderThanDays * 86_400_000L
        db().execSQL("DELETE FROM sync_queue WHERE synced=1 AND createdAt<?", arrayOf(cutoff))
    }

    /** Lowercased ids with an unsynced `delete` queued for [entityType]. */
    fun pendingDeleteIds(entityType: String): Set<String> =
        pendingSyncEntries(entityType).filter { it.action == "delete" }.map { lc(it.entityId) }.toSet()

    /** Lowercased ids with an unsynced `update` queued for [entityType]. */
    fun pendingUpdateIds(entityType: String): Set<String> =
        pendingSyncEntries(entityType).filter { it.action == "update" }.map { lc(it.entityId) }.toSet()

    /**
     * Everything a calendar pull must leave alone: queued deletes and
     * queued reschedules (the server would otherwise overwrite the row with
     * the old date / resurrect a deleted entry).
     */
    fun pendingCalendarMutationIds(): Set<String> =
        pendingDeleteIds("calendar") + pendingUpdateIds("calendar")

    // MARK: - Sync apply (allow-listed entity type -> table map)

    /**
     * Sync entity type -> local table. Accepts the hyphenated `SyncDomain`
     * raw values, their underscore spellings, and the table names themselves
     * (port of iOS `tableName(forSyncEntityType:)`). Returns null for anything
     * else — the raw entity type is NEVER interpolated into SQL.
     */
    fun tableNameForSyncEntityType(entityType: String): String? = when (entityType.trim().lowercase(Locale.US)) {
        "workouts", "workout" -> "workouts"
        "programs", "program" -> "programs"
        "movements", "movement" -> "movements"
        "goals", "goal" -> "goals"
        "habits", "habit" -> "habits"
        "sessions", "session", "workout-sessions", "workout_sessions" -> "workout_sessions"
        "session-progress", "session_progress", "session-history", "session_history",
        "workout-session-history", "workout_session_history" -> "workout_session_history"
        "food-entries", "food_entries", "food-entry", "food_entry" -> "food_entries"
        "nutrition-goals", "nutrition_goals", "nutrition-goal", "nutrition_goal" -> "nutrition_goals"
        "meal-slots", "meal_slots", "meal-slot", "meal_slot" -> "meal_slots"
        "grocery-items", "grocery_items", "grocery-item", "grocery_item", "grocery" -> "grocery_items"
        "grocery-lists", "grocery_lists" -> "grocery_lists"
        "habit-logs", "habit_logs", "habit-log", "habit_log" -> "habit_logs"
        "water-entries", "water_entries", "water-entry", "water_entry", "water" -> "water_entries"
        "hydration-goals", "hydration_goals", "hydration-goal", "hydration_goal", "hydration" -> "hydration_goals"
        "weight", "weight-entries", "weight_entries", "weight-entry", "weight_entry" -> "weight_entries"
        "measurements", "measurement-entries", "measurement_entries", "measurement" -> "measurement_entries"
        "progress-photos", "progress_photos", "progress-photo-entries", "progress_photo_entries", "progress_photo" -> "progress_photo_entries"
        "calendar", "calendar-content", "calendar_content", "calendar-entries", "calendar_entries" -> "calendar_content"
        "notifications", "notification" -> "notifications"
        "achievements", "achievement" -> "achievements"
        "program-enrollments", "program_enrollments", "user-programs", "user_programs" -> "user_programs"
        "notification-preferences", "notification_preferences" -> "notification_preferences"
        else -> null
    }

    /** Every spelling that maps to the same table as [entityType] (for queue lookups). */
    private fun entityTypeSpellings(entityType: String): List<String> {
        val table = tableNameForSyncEntityType(entityType) ?: return listOf(entityType)
        val known = listOf(
            "workouts", "programs", "movements", "goals", "habits", "sessions", "workout-sessions", "workout_sessions",
            "session-progress", "session_progress", "workout_session_history", "food-entries", "food_entries",
            "nutrition-goals", "nutrition_goals", "meal-slots", "meal_slots", "grocery-items", "grocery_items", "grocery",
            "grocery-lists", "grocery_lists", "habit-logs", "habit_logs", "water-entries", "water_entries",
            "hydration-goals", "hydration_goals", "weight", "weight-entries", "weight_entries", "measurements",
            "measurement_entries", "progress-photos", "progress_photo_entries", "calendar", "calendar-content",
            "calendar_content", "notifications", "achievements", "program-enrollments", "program_enrollments",
            "user-programs", "user_programs", "notification-preferences", "notification_preferences"
        )
        return (known.filter { tableNameForSyncEntityType(it) == table } + entityType).distinct()
    }

    private fun idColumn(table: String): String = if (table == "user_programs") "enrollmentId" else "id"

    /** Remove a row that a `/sync/pull` delete change names. Unknown types are ignored. */
    fun deleteSyncEntity(entityType: String, entityId: String) {
        val table = tableNameForSyncEntityType(entityType) ?: return
        when (table) {
            "workouts" -> deleteWorkout(entityId)
            "programs" -> deleteProgram(entityId)
            "movements" -> deleteMovement(entityId)
            "workout_sessions" -> deleteSession(entityId)
            "user_programs" -> deleteUserProgram(entityId)
            "grocery_lists" -> deleteGroceryList(entityId)
            else -> db().delete(table, "LOWER(${idColumn(table)})=?", arrayOf(lc(entityId)))
        }
    }

    /**
     * Apply one row from a `/sync/pull` response. [payload] is the entity's
     * JSON. Decode failures are silent (the cursor advances regardless);
     * unknown entity types are dropped.
     */
    fun applySyncPayload(entityType: String, entityId: String, payload: String) {
        val table = tableNameForSyncEntityType(entityType) ?: return
        if (payload.isBlank()) return
        try {
            when (table) {
                "workouts" -> decode<Workout>(payload)?.let { insertWorkout(it.copy(id = it.id.ifEmpty { entityId })) }
                "programs" -> decode<Program>(payload)?.let { insertProgram(it.copy(id = it.id.ifEmpty { entityId })) }
                "movements" -> decode<Movement>(payload)?.let { insertMovement(it) }
                "goals" -> decode<Goal>(payload)?.let { insertGoal(it) }
                "habits" -> decode<Habit>(payload)?.let { insertHabit(it) }
                "habit_logs" -> decode<HabitLog>(payload)?.let { insertHabitLog(it) }
                "food_entries" -> decode<FoodEntry>(payload)?.let { insertFoodEntry(it) }
                "nutrition_goals" -> decode<NutritionGoal>(payload)?.let { insertNutritionGoal(it) }
                "meal_slots" -> decode<MealSlot>(payload)?.let { insertMealSlot(it) }
                "grocery_items" -> decode<GroceryItem>(payload)?.let { insertGroceryItem(it) }
                "grocery_lists" -> decode<GroceryList>(payload)?.let { upsertGroceryList(it) }
                "water_entries" -> decode<WaterIntakeEntry>(payload)?.let { saveWaterEntry(it) }
                "hydration_goals" -> decode<HydrationGoal>(payload)?.let { saveHydrationGoal(it) }
                "calendar_content" -> decode<FitnessContent>(payload)?.let {
                    val entry = if (it.id.isEmpty()) it.copy(id = entityId) else it
                    if (!pendingCalendarMutationIds().contains(lc(entry.id))) insertCalendarContent(entry)
                }
                "notifications" -> decode<NotificationItem>(payload)?.let {
                    insertNotification(it.id.ifEmpty { entityId }, it.type.name.lowercase(Locale.US), it.title, it.body, it.isRead, it.createdAt.time, it.deepLink)
                }
                "workout_sessions" -> decode<WorkoutSession>(payload)?.let { insertWorkoutSession(it) }
                "weight_entries" -> decode<WeightEntry>(payload)?.let { insertWeightEntry(it) }
                "measurement_entries" -> decode<MeasurementEntry>(payload)?.let { insertMeasurementEntry(it) }
                "progress_photo_entries" -> decode<ProgressPhotoEntry>(payload)?.let { insertProgressPhotoEntry(it) }
                "achievements" -> decode<Achievement>(payload)?.let { insertAchievement(it) }
                "notification_preferences" -> decode<GoalNotificationPreference>(payload)?.let { saveNotificationPreference(it) }
                "user_programs" -> decode<ProgramEnrollmentWire>(payload)?.let { wire ->
                    if (wire.status.equals("active", true) && wire.enrollmentId.isNotEmpty()) {
                        val start = ProgramDates.startOfDay(wire.startDate.time)
                        val existing = userProgram(wire.enrollmentId)
                        upsertUserProgram(UserProgram(
                            enrollmentId = lc(wire.enrollmentId), programId = lc(wire.programId),
                            programName = wire.programName?.takeIf { it.isNotEmpty() } ?: existing?.programName ?: "Program",
                            startDate = start, weekOneStart = existing?.weekOneStart ?: start,
                            weeks = wire.weeks ?: existing?.weeks ?: 1, createdAt = existing?.createdAt ?: start
                        ))
                    } else if (wire.enrollmentId.isNotEmpty()) deleteUserProgram(wire.enrollmentId)
                }
                else -> {}
            }
        } catch (_: Exception) {
            // Decode / apply failures never stall the pull cursor.
        }
    }

    /** Map-shaped overload for callers that already parsed the change body. */
    fun applySyncPayload(entityType: String, entityId: String, data: Map<String, Any?>?) {
        if (data == null) return
        applySyncPayload(entityType, entityId, gson.toJson(data))
    }

    private inline fun <reified T> decode(json: String): T? = try { gson.fromJson(json, T::class.java) } catch (_: Exception) { null }

    /**
     * Apply a push `idMappings` entry: the server replaced a client-minted id
     * with its own. Renames the row's id in the mapped table AND every
     * `sync_queue` row for that entity so later queued mutations target the
     * server id. Both ids are compared case-insensitively.
     */
    fun updateEntityId(entityType: String, localId: String, serverId: String) {
        val table = tableNameForSyncEntityType(entityType) ?: return
        val from = lc(localId); val to = lc(serverId)
        if (from == to) return
        transaction {
            val col = idColumn(table)
            // Drop a pre-existing server row so the rename can't collide.
            db().delete(table, "LOWER($col)=?", arrayOf(to))
            db().execSQL("UPDATE $table SET $col=? WHERE LOWER($col)=?", arrayOf(to, from))
            when (table) {
                "workouts" -> {
                    db().execSQL("UPDATE program_schedules SET workoutId=? WHERE LOWER(workoutId)=?", arrayOf(to, from))
                    db().execSQL("UPDATE calendar_content SET workoutId=? WHERE LOWER(workoutId)=?", arrayOf(to, from))
                    db().execSQL("UPDATE workout_sessions SET workoutId=? WHERE LOWER(workoutId)=?", arrayOf(to, from))
                    db().execSQL("UPDATE workout_session_history SET workoutId=? WHERE LOWER(workoutId)=?", arrayOf(to, from))
                }
                "programs" -> {
                    db().execSQL("UPDATE program_schedules SET programId=? WHERE LOWER(programId)=?", arrayOf(to, from))
                    db().execSQL("UPDATE calendar_content SET programId=? WHERE LOWER(programId)=?", arrayOf(to, from))
                    db().execSQL("UPDATE user_programs SET programId=? WHERE LOWER(programId)=?", arrayOf(to, from))
                }
                "movements" -> {
                    db().execSQL("UPDATE movement_anatomy SET movementId=? WHERE movementId=?", arrayOf(to, from))
                    db().execSQL("UPDATE movement_equipment SET movementId=? WHERE movementId=?", arrayOf(to, from))
                    db().execSQL("UPDATE recent_movements SET movementId=? WHERE LOWER(movementId)=?", arrayOf(to, from))
                    db().execSQL("UPDATE workout_session_history SET movementId=? WHERE movementId=?", arrayOf(to, from))
                }
                "workout_sessions" -> {
                    db().execSQL("UPDATE workout_session_history SET workoutSessionId=? WHERE LOWER(workoutSessionId)=?", arrayOf(to, from))
                }
                "habits" -> db().execSQL("UPDATE habit_logs SET habitId=? WHERE LOWER(habitId)=?", arrayOf(to, from))
                "goals" -> db().execSQL("UPDATE habits SET goalId=? WHERE LOWER(goalId)=?", arrayOf(to, from))
                "grocery_lists" -> db().execSQL("UPDATE grocery_items SET groceryListId=? WHERE LOWER(groceryListId)=?", arrayOf(to, from))
                "user_programs" -> db().execSQL("UPDATE calendar_content SET programEnrollmentId=? WHERE LOWER(programEnrollmentId)=?", arrayOf(to, from))
            }
            val spellings = entityTypeSpellings(entityType)
            val queueArgs: List<Any> = listOf(to, from) + spellings
            db().execSQL(
                "UPDATE sync_queue SET entityId=? WHERE LOWER(entityId)=? AND entityType IN (${spellings.joinToString(",") { "?" }})",
                queueArgs.toTypedArray()
            )
        }
    }
}
