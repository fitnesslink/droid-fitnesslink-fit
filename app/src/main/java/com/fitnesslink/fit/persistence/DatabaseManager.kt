package com.fitnesslink.fit.persistence

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.model.api.ProgramSchedule
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID

object DatabaseManager {
    private lateinit var database: SQLiteDatabase

    fun initialize(context: Context) {
        database = DatabaseHelper(context).writableDatabase
        DatabaseSeeder.seedIfNeeded()
    }

    fun initializeForTesting() {
        database = SQLiteDatabase.create(null)
        createAllTables(database)
        migrateToV2(database)
    }

    private fun db(): SQLiteDatabase = database

    // MARK: - Schema

    private const val DB_VERSION = 10

    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, "fitnesslink.db", null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            createAllTables(db)
            migrateToV2(db)
            migrateToV3(db)
            migrateToV4(db)
            migrateToV5(db)
            migrateToV6(db)
            // V7 schema is already applied by createAllTables (no imageUrl columns).
            migrateToV9(db)
            migrateToV10(db)
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
        }
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

    private fun migrateToV2(db: SQLiteDatabase) {
        // Expand users table
        val userColumns = listOf(
            "ALTER TABLE users ADD COLUMN firstName TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE users ADD COLUMN lastName TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE users ADD COLUMN alias TEXT",
            "ALTER TABLE users ADD COLUMN phone TEXT",
            "ALTER TABLE users ADD COLUMN firebaseId TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE users ADD COLUMN username TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE users ADD COLUMN country TEXT",
            "ALTER TABLE users ADD COLUMN profileImageId TEXT",
            "ALTER TABLE users ADD COLUMN companyId TEXT",
            "ALTER TABLE users ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE users ADD COLUMN requirePersonalization INTEGER NOT NULL DEFAULT 1",
        )
        userColumns.forEach { try { db.execSQL(it) } catch (_: Exception) { } }

        // New tables
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
        try { db.execSQL("ALTER TABLE workout_session_history ADD COLUMN taskName TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
    }

    private fun migrateToV4(db: SQLiteDatabase) {
        val cols = listOf(
            "ALTER TABLE movements ADD COLUMN muscleGroup TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE movements ADD COLUMN equipment TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE movements ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0"
        )
        cols.forEach { try { db.execSQL(it) } catch (_: Exception) {} }
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
        val tablesToAlter = listOf("workouts", "programs", "food_entries", "movements",
            "workout_sessions", "meal_slots", "grocery_items", "notifications")
        for (table in tablesToAlter) {
            try { db.execSQL("ALTER TABLE $table ADD COLUMN lastSyncedAt INTEGER") } catch (_: Exception) {}
        }
    }

    private fun migrateToV7(db: SQLiteDatabase) {
        try { db.execSQL("ALTER TABLE programs DROP COLUMN imageUrl") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE workouts DROP COLUMN imageUrl") } catch (_: Exception) {}
    }

    private fun migrateToV8(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_content ADD COLUMN scheduledDate INTEGER")
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
        try { db.execSQL("ALTER TABLE programs ADD COLUMN weeks INTEGER") } catch (_: Exception) {}
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
            MealTypeAggregate(r.mt, MealType.valueOf(r.mt), r.avgC, r.avgP, r.avgF, r.avgCb,
                if (total > 0) r.sumC / total * 100 else 0.0, r.count)
        }.sortedByDescending { it.percentOfDailyCalories }
    }

    fun foodAggregates(since: Long): List<FoodAggregate> {
        return db().rawQuery("""
            SELECT name, COUNT(*), AVG(calories), SUM(calories), SUM(protein), AVG(protein)
            FROM food_entries WHERE loggedAt>=? GROUP BY name ORDER BY COUNT(*) DESC
        """, arrayOf(since.toString())).use { c ->
            buildList { while (c.moveToNext()) add(FoodAggregate(c.str(0), c.str(0), c.getInt(1), c.getDouble(2), c.getInt(3), c.getDouble(4), c.getDouble(5))) }
        }
    }

    fun foodEntriesForName(name: String, since: Long): List<FoodEntry> {
        return db().rawQuery("SELECT id,name,calories,protein,fat,carbs,servingSize,servingUnit,mealType,loggedAt,isCustomTemplate FROM food_entries WHERE name=? AND loggedAt>=? ORDER BY loggedAt DESC",
            arrayOf(name, since.toString())).use { c -> buildList { while (c.moveToNext()) add(readFoodEntry(c)) } }
    }

    fun foodEntriesForMealType(mealType: String, since: Long): List<FoodEntry> {
        return db().rawQuery("SELECT id,name,calories,protein,fat,carbs,servingSize,servingUnit,mealType,loggedAt,isCustomTemplate FROM food_entries WHERE mealType=? AND loggedAt>=? ORDER BY loggedAt DESC",
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

    fun sessionRows(userId: String, since: Long): List<SessionRow> {
        return db().rawQuery("""
            SELECT id, workoutName, startDate, durationSeconds, isCompleted,
                   exerciseCount, totalWeightLifted, totalCaloriesBurned, rpeValue
            FROM workout_sessions WHERE userId=? AND startDate>=? ORDER BY startDate DESC
        """, arrayOf(userId, since.toString())).use { c ->
            buildList {
                while (c.moveToNext()) add(SessionRow(c.str(0), c.str(1), Date(c.getLong(2)),
                    c.getInt(3), c.getInt(4) != 0, c.getInt(5), c.getDouble(6), c.getDouble(7),
                    if (c.isNull(8)) null else c.getDouble(8)))
            }
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
        return db().rawQuery("""
            SELECT id, workoutName, startDate, durationSeconds, isCompleted,
                   exerciseCount, totalWeightLifted, totalCaloriesBurned, rpeValue
            FROM workout_sessions WHERE id=?
        """, arrayOf(id)).use { c ->
            if (!c.moveToFirst()) return null
            SessionRow(c.str(0), c.str(1), Date(c.getLong(2)), c.getInt(3),
                c.getInt(4) != 0, c.getInt(5), c.getDouble(6), c.getDouble(7),
                if (c.isNull(8)) null else c.getDouble(8))
        }
    }

    fun sessionExercises(sessionId: String): List<SessionExerciseRow> {
        return db().rawQuery("""
            SELECT taskName, reps, setNumber, COALESCE(weightLifted,0), logDate
            FROM workout_session_history WHERE workoutSessionId=? ORDER BY logDate, taskName, setNumber
        """, arrayOf(sessionId)).use { c ->
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

    // MARK: - Workout Session Write Methods

    fun insertWorkoutSession(
        id: String, workoutId: String, userId: String, programId: String?,
        workoutName: String, startDate: Long, completionDate: Long?,
        durationSeconds: Int, isCompleted: Boolean, exerciseCount: Int,
        totalSets: Int, totalReps: Int, totalWeightLifted: Double,
        totalCaloriesBurned: Double, rpeValue: Double?
    ) {
        db().insertWithOnConflict("workout_sessions", null, ContentValues().apply {
            put("id", id); put("workoutId", workoutId); put("userId", userId)
            programId?.let { put("programId", it) }
            put("workoutName", workoutName); put("startDate", startDate)
            completionDate?.let { put("completionDate", it) }
            put("durationSeconds", durationSeconds); put("isCompleted", if (isCompleted) 1 else 0)
            put("exerciseCount", exerciseCount); put("totalSets", totalSets)
            put("totalReps", totalReps); put("totalWeightLifted", totalWeightLifted)
            put("totalCaloriesBurned", totalCaloriesBurned)
            rpeValue?.let { put("rpeValue", it) }
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertSessionHistory(
        id: String, workoutSessionId: String, workoutTaskId: String,
        workoutId: String, programId: String?, userId: String,
        logDate: Long, reps: Int?, setNumber: Int?,
        intervalSeconds: Int?, weightLifted: Double?, taskName: String
    ) {
        db().insertWithOnConflict("workout_session_history", null, ContentValues().apply {
            put("id", id); put("workoutSessionId", workoutSessionId)
            put("workoutTaskId", workoutTaskId); put("workoutId", workoutId)
            programId?.let { put("programId", it) }
            put("userId", userId); put("logDate", logDate)
            reps?.let { put("reps", it) }; setNumber?.let { put("setNumber", it) }
            intervalSeconds?.let { put("intervalSeconds", it) }
            weightLifted?.let { put("weightLifted", it) }
            put("taskName", taskName)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // MARK: - Read Queries

    fun allWorkouts(): List<WorkoutList> = db().rawQuery(
        "SELECT id, name, time, trainingLevel FROM workouts", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    WorkoutList(
                        id = c.str(0),
                        name = c.str(1),
                        time = c.str(2),
                        isFavorite = false,
                        trainingLevel = c.str(3)
                    )
                )
            }
        }
    }

    fun workout(id: String): Workout? = db().rawQuery("SELECT id, name, time, location, trainingLevel, description, phasesJson FROM workouts WHERE id=?", arrayOf(id)).use { c ->
        if (!c.moveToFirst()) return null
        Workout(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.str(5), phasesFromJson(c.str(6)))
    }

    fun allPrograms(): List<ProgramList> = db().rawQuery(
        "SELECT id, name, time, trainingLevel, weeks FROM programs", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val weeks = if (c.isNull(4)) null else c.getInt(4)
                add(
                    ProgramList(
                        id = c.str(0),
                        name = c.str(1),
                        time = c.str(2),
                        isFavorite = false,
                        weeks = weeks,
                        trainingLevel = c.str(3)
                    )
                )
            }
        }
    }

    fun program(id: String): Program? = db().rawQuery("SELECT id, name, time, location, trainingLevel, description FROM programs WHERE id=?", arrayOf(id)).use { c ->
        if (!c.moveToFirst()) return null
        Program(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.str(5))
    }

    fun allFoodEntries(): List<FoodEntry> = db().rawQuery("SELECT id, name, calories, protein, fat, carbs, servingSize, servingUnit, mealType, loggedAt, isCustomTemplate FROM food_entries", null).use { c ->
        buildList { while (c.moveToNext()) add(readFoodEntry(c)) }
    }

    private fun readFoodEntry(c: Cursor): FoodEntry = FoodEntry(
        c.str(0), c.str(1), c.getInt(2), c.getDouble(3), c.getDouble(4), c.getDouble(5),
        c.getDouble(6), c.str(7), MealType.valueOf(c.str(8)), Date(c.getLong(9)), c.getInt(10) != 0
    )

    fun nutritionGoal(): NutritionGoal = db().rawQuery("SELECT id, calorieGoal, proteinTarget, fatTarget, carbsTarget FROM nutrition_goals LIMIT 1", null).use { c ->
        if (!c.moveToFirst()) return NutritionGoal()
        NutritionGoal(c.str(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4))
    }

    fun weeklyMealSlots(): List<MealSlot> = db().rawQuery("SELECT id, day, mealType, recipeName, calories, protein, fat, carbs, isAISuggestion, ingredientsJson FROM meal_slots", null).use { c ->
        buildList {
            while (c.moveToNext()) add(MealSlot(c.str(0), DayOfWeek.valueOf(c.str(1)), MealType.valueOf(c.str(2)),
                c.str(3), c.getInt(4), c.getDouble(5), c.getDouble(6), c.getDouble(7),
                c.getInt(8) != 0, groceryItemsFromJson(c.str(9))))
        }
    }

    fun groceryItems(): List<GroceryItem> = db().rawQuery("SELECT id, name, quantity, unit, category, isChecked FROM grocery_items", null).use { c ->
        buildList {
            while (c.moveToNext()) add(GroceryItem(c.str(0), c.str(1), c.str(2), c.str(3),
                GroceryCategory.valueOf(c.str(4)), c.getInt(5) != 0))
        }
    }

    fun barcodeProduct(barcode: String): BarcodeProduct? = db().rawQuery("SELECT id, barcode, name, brand, imageUrl, caloriesPer100g, proteinPer100g, fatPer100g, carbsPer100g, servingSizeGrams, servingUnit FROM barcode_products WHERE barcode=?", arrayOf(barcode)).use { c ->
        if (!c.moveToFirst()) return null
        BarcodeProduct(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.getDouble(5), c.getDouble(6), c.getDouble(7), c.getDouble(8), c.getDouble(9), c.str(10))
    }

    fun dashboards(): List<HomeDashboard> = db().rawQuery("SELECT id, name, progress, unit, goals FROM home_dashboards", null).use { c ->
        buildList { while (c.moveToNext()) add(HomeDashboard(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4))) }
    }

    fun calendarContent(): List<FitnessContent> = db().rawQuery("SELECT id, title, programId, workoutId, mealPlanId, status, scheduledDate FROM calendar_content ORDER BY scheduledDate", null).use { c ->
        buildList {
            while (c.moveToNext()) {
                val dateIdx = 6
                val scheduledDate = if (c.isNull(dateIdx)) null else c.getLong(dateIdx)
                add(FitnessContent(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.str(5), scheduledDate))
            }
        }
    }

    fun calendarContent(forDate: Long): List<FitnessContent> {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = forDate
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val endOfDay = cal.timeInMillis
        return db().rawQuery(
            "SELECT id, title, programId, workoutId, mealPlanId, status, scheduledDate FROM calendar_content WHERE scheduledDate >= ? AND scheduledDate < ? ORDER BY scheduledDate",
            arrayOf(startOfDay.toString(), endOfDay.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val scheduledDate = if (c.isNull(6)) null else c.getLong(6)
                    add(FitnessContent(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.str(5), scheduledDate))
                }
            }
        }
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

    fun deleteCalendarContent(id: String) {
        db().delete("calendar_content", "id=?", arrayOf(id))
    }

    fun personalizations(): List<Personalization> = db().rawQuery("SELECT id, name, singleSelection, optionsJson FROM personalizations", null).use { c ->
        buildList {
            while (c.moveToNext()) add(Personalization(c.str(0), c.str(1), c.getInt(2) != 0,
                personalizationItemsFromJson(c.str(3))))
        }
    }

    fun profileMenuItems(): List<ProfileMenu> = db().rawQuery("SELECT menuId, text FROM profile_menus ORDER BY menuId", null).use { c ->
        buildList { while (c.moveToNext()) add(ProfileMenu(c.getInt(0), c.str(1))) }
    }

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

    fun catalogPrograms(): List<CatalogItem> = db().rawQuery("SELECT id, name, time FROM programs", null).use { c ->
        buildList { while (c.moveToNext()) add(CatalogItem(c.str(0), c.str(1), CatalogItem.Kind.PROGRAM, c.str(2))) }
    }

    fun catalogWorkouts(): List<CatalogItem> = db().rawQuery("SELECT id, name, time FROM workouts", null).use { c ->
        buildList { while (c.moveToNext()) add(CatalogItem(c.str(0), c.str(1), CatalogItem.Kind.WORKOUT, c.str(2))) }
    }

    // MARK: - Write Methods

    suspend fun saveFoodEntry(entry: FoodEntry) {
        db().insertWithOnConflict("food_entries", null, ContentValues().apply {
            put("id", entry.id); put("name", entry.name); put("calories", entry.calories)
            put("protein", entry.protein); put("fat", entry.fat); put("carbs", entry.carbs)
            put("servingSize", entry.servingSize); put("servingUnit", entry.servingUnit)
            put("mealType", entry.mealType.name); put("loggedAt", entry.loggedAt.time)
            put("isCustomTemplate", if (entry.isCustomTemplate) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun deleteFoodEntry(id: String) {
        db().delete("food_entries", "id=?", arrayOf(id))
    }

    fun waterEntries(forDate: Date): List<WaterIntakeEntry> {
        val cal = java.util.Calendar.getInstance().apply {
            time = forDate
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val endOfDay = cal.timeInMillis
        return db().rawQuery(
            "SELECT id, amount, unit, loggedAt, notes FROM water_entries WHERE loggedAt >= ? AND loggedAt < ? ORDER BY loggedAt DESC",
            arrayOf(startOfDay.toString(), endOfDay.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        WaterIntakeEntry(
                            id = c.str(0),
                            amount = c.getDouble(1),
                            unit = WaterUnit.fromRaw(c.str(2)),
                            loggedAt = Date(c.getLong(3)),
                            notes = c.str(4)
                        )
                    )
                }
            }
        }
    }

    fun saveWaterEntry(entry: WaterIntakeEntry) {
        db().insertWithOnConflict("water_entries", null, ContentValues().apply {
            put("id", entry.id); put("amount", entry.amount)
            put("unit", entry.unit.name); put("loggedAt", entry.loggedAt.time)
            put("notes", entry.notes)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteWaterEntry(id: String) {
        db().delete("water_entries", "id=?", arrayOf(id))
    }

    fun hydrationGoal(): HydrationGoal = db().rawQuery(
        "SELECT id, dailyGoal, unit FROM hydration_goals LIMIT 1", null
    ).use { c ->
        if (!c.moveToFirst()) return HydrationGoal()
        HydrationGoal(c.str(0), c.getDouble(1), WaterUnit.fromRaw(c.str(2)))
    }

    fun saveHydrationGoal(goal: HydrationGoal) {
        db().insertWithOnConflict("hydration_goals", null, ContentValues().apply {
            put("id", goal.id); put("dailyGoal", goal.dailyGoal); put("unit", goal.unit.name)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun saveMealSlot(slot: MealSlot) {
        db().insertWithOnConflict("meal_slots", null, ContentValues().apply {
            put("id", slot.id); put("day", slot.day.name); put("mealType", slot.mealType.name)
            put("recipeName", slot.recipeName); put("calories", slot.calories)
            put("protein", slot.protein); put("fat", slot.fat); put("carbs", slot.carbs)
            put("isAISuggestion", if (slot.isAISuggestion) 1 else 0)
            put("ingredientsJson", groceryItemsToJson(slot.ingredients))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun deleteMealSlot(id: String) {
        db().delete("meal_slots", "id=?", arrayOf(id))
    }

    suspend fun saveGroceryItems(items: List<GroceryItem>) {
        db().beginTransaction()
        try {
            db().delete("grocery_items", null, null)
            items.forEach { item ->
                db().insert("grocery_items", null, ContentValues().apply {
                    put("id", item.id); put("name", item.name); put("quantity", item.quantity)
                    put("unit", item.unit); put("category", item.category.name)
                    put("isChecked", if (item.isChecked) 1 else 0)
                })
            }
            db().setTransactionSuccessful()
        } finally {
            db().endTransaction()
        }
    }

    suspend fun toggleGroceryItem(id: String) {
        db().execSQL("UPDATE grocery_items SET isChecked = NOT isChecked WHERE id=?", arrayOf(id))
    }

    suspend fun savePersonalizations(personalizations: List<Personalization>) {
        personalizations.forEach { p ->
            db().insertWithOnConflict("personalizations", null, ContentValues().apply {
                put("id", p.id); put("name", p.name)
                put("singleSelection", if (p.singleSelection) 1 else 0)
                put("optionsJson", personalizationItemsToJson(p.options))
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    suspend fun setUserPersonalized(userId: String) {
        db().execSQL("UPDATE users SET isPersonalized=1 WHERE id=?", arrayOf(userId))
    }

    suspend fun saveUser(id: String, name: String, email: String) {
        db().execSQL("INSERT INTO users (id, name, email, isPersonalized) VALUES (?, ?, ?, 0) ON CONFLICT(id) DO UPDATE SET name=excluded.name, email=excluded.email", arrayOf(id, name, email))
    }

    // MARK: - Seed Insert Methods

    fun insertDashboard(d: HomeDashboard) {
        db().insertWithOnConflict("home_dashboards", null, ContentValues().apply {
            put("id", d.id); put("name", d.name); put("progress", d.progress)
            put("unit", d.unit); put("goals", d.goals)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertProgram(p: Program) {
        db().insertWithOnConflict("programs", null, ContentValues().apply {
            put("id", p.id); put("name", p.name)
            put("time", p.time); put("location", p.location)
            put("trainingLevel", p.trainingLevel); put("description", p.description)
            if (p.weeks != null) put("weeks", p.weeks) else putNull("weeks")
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // Program Schedules

    fun schedulesForProgram(id: String): List<ProgramSchedule> =
        db().rawQuery(
            "SELECT id, programId, workoutId, weekNumber, dayNumber FROM program_schedules WHERE programId=? ORDER BY weekNumber, dayNumber",
            arrayOf(id)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    ProgramSchedule(
                        id = UUID.fromString(c.str(0)),
                        programId = UUID.fromString(c.str(1)),
                        workoutId = UUID.fromString(c.str(2)),
                        weekNumber = c.getInt(3),
                        dayNumber = c.getInt(4)
                    )
                )
            }
        }

    fun insertProgramSchedule(s: ProgramSchedule) {
        db().insertWithOnConflict("program_schedules", null, ContentValues().apply {
            put("id", s.id.toString()); put("programId", s.programId.toString())
            put("workoutId", s.workoutId.toString()); put("weekNumber", s.weekNumber)
            put("dayNumber", s.dayNumber)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteSchedulesForProgram(id: String) {
        db().delete("program_schedules", "programId=?", arrayOf(id))
    }

    fun replaceSchedulesForProgram(programId: String, schedules: List<ProgramSchedule>) {
        val database = db()
        database.beginTransaction()
        try {
            database.delete("program_schedules", "programId=?", arrayOf(programId))
            schedules.forEach { insertProgramSchedule(it) }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun deleteProgram(id: String) {
        deleteSchedulesForProgram(id)
        db().delete("programs", "id=?", arrayOf(id))
    }

    fun insertWorkout(w: Workout) {
        db().insertWithOnConflict("workouts", null, ContentValues().apply {
            put("id", w.id); put("name", w.name)
            put("time", w.time); put("location", w.location)
            put("trainingLevel", w.trainingLevel); put("description", w.description)
            put("phasesJson", phasesToJson(w.phases))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertNutritionGoal(g: NutritionGoal) {
        db().insertWithOnConflict("nutrition_goals", null, ContentValues().apply {
            put("id", g.id); put("calorieGoal", g.calorieGoal)
            put("proteinTarget", g.proteinTarget); put("fatTarget", g.fatTarget)
            put("carbsTarget", g.carbsTarget)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertFoodEntry(e: FoodEntry) {
        db().insertWithOnConflict("food_entries", null, ContentValues().apply {
            put("id", e.id); put("name", e.name); put("calories", e.calories)
            put("protein", e.protein); put("fat", e.fat); put("carbs", e.carbs)
            put("servingSize", e.servingSize); put("servingUnit", e.servingUnit)
            put("mealType", e.mealType.name); put("loggedAt", e.loggedAt.time)
            put("isCustomTemplate", if (e.isCustomTemplate) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertMealSlot(s: MealSlot) {
        db().insertWithOnConflict("meal_slots", null, ContentValues().apply {
            put("id", s.id); put("day", s.day.name); put("mealType", s.mealType.name)
            put("recipeName", s.recipeName); put("calories", s.calories)
            put("protein", s.protein); put("fat", s.fat); put("carbs", s.carbs)
            put("isAISuggestion", if (s.isAISuggestion) 1 else 0)
            put("ingredientsJson", groceryItemsToJson(s.ingredients))
        }, SQLiteDatabase.CONFLICT_REPLACE)
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

    fun insertPersonalization(p: Personalization) {
        db().insertWithOnConflict("personalizations", null, ContentValues().apply {
            put("id", p.id); put("name", p.name)
            put("singleSelection", if (p.singleSelection) 1 else 0)
            put("optionsJson", personalizationItemsToJson(p.options))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertCalendarContent(c: FitnessContent) {
        db().insertWithOnConflict("calendar_content", null, ContentValues().apply {
            put("id", c.id); put("title", c.title); put("programId", c.programId)
            put("workoutId", c.workoutId); put("mealPlanId", c.mealPlanId); put("status", c.status)
            if (c.scheduledDate != null) put("scheduledDate", c.scheduledDate) else putNull("scheduledDate")
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertProfileMenu(m: ProfileMenu) {
        db().insertWithOnConflict("profile_menus", null, ContentValues().apply {
            put("menuId", m.id); put("text", m.text)
        }, SQLiteDatabase.CONFLICT_REPLACE)
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

    // MARK: - Cursor Helpers

    private fun Cursor.str(col: Int): String = getString(col) ?: ""

    // MARK: - JSON Serialization

    private fun phasesToJson(phases: List<WorkoutPhase>): String {
        val arr = JSONArray()
        phases.forEach { phase ->
            arr.put(JSONObject().apply {
                put("id", phase.id); put("name", phase.name)
                put("taskRows", JSONArray().apply {
                    phase.taskRows.forEach { row ->
                        put(JSONObject().apply {
                            put("advanced", row.advanced); put("rounds", row.rounds)
                            put("totalRounds", row.totalRounds); put("isSuperset", row.isSuperset)
                            put("isCircuit", row.isCircuit)
                            row.task?.let { put("task", taskToJson(it)) }
                            put("advancedTasks", JSONArray().apply {
                                row.advancedTasks.forEach { put(taskToJson(it)) }
                            })
                        })
                    }
                })
            })
        }
        return arr.toString()
    }

    private fun taskToJson(t: WorkoutTask): JSONObject = JSONObject().apply {
        put("id", t.id); put("name", t.name); put("metric", t.metric)
        put("isMovement", t.isMovement); put("isRest", t.isRest); put("isAdvanced", t.isAdvanced)
        put("isSuperset", t.isSuperset); put("isCircuit", t.isCircuit); put("isInterval", t.isInterval)
        put("reps", t.reps); put("sets", t.sets); put("restSeconds", t.restSeconds)
        put("rest", t.rest); put("round", t.round); put("totalRounds", t.totalRounds)
        put("phaseName", t.phaseName); put("phaseId", t.phaseId)
        put("advancedMovement", t.advancedMovement); put("currentSet", t.currentSet)
        put("order", t.order); put("groupId", t.groupId)
    }

    private fun phasesFromJson(json: String): List<WorkoutPhase> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val rows = obj.getJSONArray("taskRows")
            WorkoutPhase(
                id = obj.getString("id"),
                name = obj.getString("name"),
                taskRows = (0 until rows.length()).map { j ->
                    val r = rows.getJSONObject(j)
                    val task = if (r.has("task") && !r.isNull("task")) taskFromJson(r.getJSONObject("task")) else null
                    val adv = r.optJSONArray("advancedTasks") ?: JSONArray()
                    TaskRow(task = task, advanced = r.optString("advanced", ""),
                        rounds = r.optString("rounds", ""), totalRounds = r.optInt("totalRounds", 0),
                        isSuperset = r.optBoolean("isSuperset", false),
                        isCircuit = r.optBoolean("isCircuit", false),
                        advancedTasks = (0 until adv.length()).map { k -> taskFromJson(adv.getJSONObject(k)) })
                }
            )
        }
    }

    private fun taskFromJson(o: JSONObject): WorkoutTask = WorkoutTask(
        id = o.optString("id", ""), name = o.optString("name", ""),
        metric = o.optString("metric", ""),
        isMovement = o.optBoolean("isMovement"), isRest = o.optBoolean("isRest"),
        isAdvanced = o.optBoolean("isAdvanced"), isSuperset = o.optBoolean("isSuperset"),
        isCircuit = o.optBoolean("isCircuit"), isInterval = o.optBoolean("isInterval"),
        reps = o.optInt("reps"), sets = o.optInt("sets"), restSeconds = o.optInt("restSeconds"),
        rest = o.optString("rest", ""), round = o.optString("round", ""),
        totalRounds = o.optInt("totalRounds"), phaseName = o.optString("phaseName", ""),
        phaseId = o.optString("phaseId", ""), advancedMovement = o.optString("advancedMovement", ""),
        currentSet = o.optInt("currentSet"), order = o.optInt("order"),
        groupId = o.optString("groupId", "")
    )

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

    private fun groceryItemsToJson(items: List<GroceryItem>): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("quantity", it.quantity)
                put("unit", it.unit); put("category", it.category.name)
                put("isChecked", it.isChecked)
            })
        }
        return arr.toString()
    }

    private fun groceryItemsFromJson(json: String): List<GroceryItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GroceryItem(o.getString("id"), o.getString("name"), o.optString("quantity", ""),
                o.optString("unit", ""), GroceryCategory.valueOf(o.optString("category", "OTHER")),
                o.optBoolean("isChecked"))
        }
    }

    // MARK: - Movement Queries

    fun allMovements(): List<MovementLibraryItem> = db().rawQuery(
        "SELECT id, name, COALESCE(description,''), muscleGroup, equipment, isFavorite FROM movements WHERE isDeleted=0 ORDER BY name", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(MovementLibraryItem(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.getInt(5) != 0))
        }
    }

    fun searchMovements(query: String, muscleGroup: String?, equipment: String?): List<MovementLibraryItem> {
        val sb = StringBuilder("SELECT id, name, COALESCE(description,''), muscleGroup, equipment, isFavorite FROM movements WHERE isDeleted=0")
        val args = mutableListOf<String>()
        if (query.isNotEmpty()) { sb.append(" AND name LIKE ?"); args.add("%$query%") }
        if (!muscleGroup.isNullOrEmpty()) { sb.append(" AND muscleGroup=?"); args.add(muscleGroup) }
        if (!equipment.isNullOrEmpty()) { sb.append(" AND equipment=?"); args.add(equipment) }
        sb.append(" ORDER BY name")
        return db().rawQuery(sb.toString(), args.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) add(MovementLibraryItem(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.getInt(5) != 0))
            }
        }
    }

    fun favoriteMovements(): List<MovementLibraryItem> = db().rawQuery(
        "SELECT id, name, COALESCE(description,''), muscleGroup, equipment, isFavorite FROM movements WHERE isFavorite=1 AND isDeleted=0 ORDER BY name", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(MovementLibraryItem(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), true))
        }
    }

    fun recentMovements(limit: Int = 20): List<MovementLibraryItem> = db().rawQuery(
        """SELECT m.id, m.name, COALESCE(m.description,''), m.muscleGroup, m.equipment, m.isFavorite
           FROM recent_movements r JOIN movements m ON m.id=r.movementId
           WHERE m.isDeleted=0 ORDER BY r.addedAt DESC LIMIT ?""", arrayOf(limit.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(MovementLibraryItem(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.getInt(5) != 0))
        }
    }

    fun toggleMovementFavorite(id: String) {
        db().execSQL("UPDATE movements SET isFavorite = CASE WHEN isFavorite=1 THEN 0 ELSE 1 END WHERE id=?", arrayOf(id))
    }

    fun logRecentMovement(movementId: String) {
        db().insertWithOnConflict("recent_movements", null, ContentValues().apply {
            put("movementId", movementId); put("addedAt", System.currentTimeMillis().toDouble() / 1000.0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertMovementFull(id: String, name: String, description: String, muscleGroup: String, equipment: String) {
        db().insertWithOnConflict("movements", null, ContentValues().apply {
            put("id", id); put("name", name); put("description", description)
            put("muscleGroup", muscleGroup); put("equipment", equipment)
            put("isDeleted", 0); put("isFavorite", 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteWorkout(id: String) {
        db().delete("workouts", "id=?", arrayOf(id))
    }

    // MARK: - Notification Queries

    fun allNotifications(type: String? = null): List<NotificationItem> {
        val sb = StringBuilder("SELECT id, type, title, body, isRead, createdAt, deepLink FROM notifications")
        val args = mutableListOf<String>()
        if (type != null) { sb.append(" WHERE type=?"); args.add(type) }
        sb.append(" ORDER BY createdAt DESC")
        return db().rawQuery(sb.toString(), args.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) add(NotificationItem(
                    id = c.str(0),
                    type = NotificationType.fromString(c.str(1)),
                    title = c.str(2),
                    body = c.str(3),
                    isRead = c.getInt(4) != 0,
                    createdAt = Date(c.getLong(5)),
                    deepLink = c.str(6).ifEmpty { null }
                ))
            }
        }
    }

    fun unreadNotificationCount(): Int = db().rawQuery(
        "SELECT COUNT(*) FROM notifications WHERE isRead=0", null
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun markNotificationRead(id: String) {
        db().execSQL("UPDATE notifications SET isRead=1 WHERE id=?", arrayOf(id))
    }

    fun markAllNotificationsRead() {
        db().execSQL("UPDATE notifications SET isRead=1")
    }

    fun deleteNotification(id: String) {
        db().delete("notifications", "id=?", arrayOf(id))
    }

    fun insertNotification(id: String, type: String, title: String, body: String,
                           isRead: Boolean, createdAt: Long, deepLink: String?) {
        db().insertWithOnConflict("notifications", null, ContentValues().apply {
            put("id", id); put("type", type); put("title", title); put("body", body)
            put("isRead", if (isRead) 1 else 0); put("createdAt", createdAt)
            deepLink?.let { put("deepLink", it) }
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // MARK: - Server Sync Support

    fun saveUserFromApi(user: com.fitnesslink.fit.model.api.FLUser) {
        db().execSQL("""
            INSERT INTO users (id, name, email, isPersonalized, firstName, lastName, phone, username, country, profileImageId, isActive)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET name=excluded.name, email=excluded.email,
                isPersonalized=excluded.isPersonalized, firstName=excluded.firstName,
                lastName=excluded.lastName, phone=excluded.phone, username=excluded.username,
                country=excluded.country, profileImageId=excluded.profileImageId, isActive=excluded.isActive
        """.trimIndent(), arrayOf(
            user.id, "${user.firstName} ${user.lastName}", user.email,
            if (user.requirePersonalization) 0 else 1,
            user.firstName, user.lastName, user.phone ?: "",
            user.username, user.country ?: "",
            user.profileImageId ?: "", if (user.isActive) 1 else 0
        ))
    }

    fun clearUserData() {
        val tables = listOf("users", "home_dashboards", "programs", "workouts", "food_entries",
            "nutrition_goals", "meal_slots", "grocery_items", "barcode_products",
            "personalizations", "calendar_content", "profile_menus",
            "workout_sessions", "workout_session_history", "movements",
            "user_preferences", "user_personalizations", "program_schedules",
            "recent_movements", "notifications",
            "goals", "habits", "habit_logs", "streaks", "milestones",
            "achievements", "weight_entries", "measurement_entries",
            "progress_photo_entries", "notification_preferences", "sync_queue",
            "water_entries", "hydration_goals")
        for (table in tables) {
            try { db().execSQL("DELETE FROM $table") } catch (_: Exception) {}
        }
    }

    // MARK: - Sync Queue

    fun enqueueSyncEntry(entityType: String, entityId: String, action: String, payload: String) {
        db().insertWithOnConflict("sync_queue", null, ContentValues().apply {
            put("id", java.util.UUID.randomUUID().toString())
            put("entityType", entityType); put("entityId", entityId)
            put("action", action); put("payload", payload)
            put("createdAt", System.currentTimeMillis()); put("synced", 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    data class SyncQueueEntry(
        val id: String, val entityType: String, val entityId: String,
        val action: String, val payload: String, val createdAt: Long
    )

    fun pendingSyncEntries(): List<SyncQueueEntry> {
        val cursor = db().rawQuery(
            "SELECT id, entityType, entityId, action, payload, createdAt FROM sync_queue WHERE synced=0 ORDER BY createdAt ASC",
            null
        )
        val results = mutableListOf<SyncQueueEntry>()
        while (cursor.moveToNext()) {
            results.add(SyncQueueEntry(
                id = cursor.getString(0), entityType = cursor.getString(1),
                entityId = cursor.getString(2), action = cursor.getString(3),
                payload = cursor.getString(4), createdAt = cursor.getLong(5)
            ))
        }
        cursor.close()
        return results
    }

    fun markSyncEntrySynced(id: String) {
        db().execSQL("UPDATE sync_queue SET synced=1 WHERE id=?", arrayOf(id))
    }

    fun pruneSyncedEntries(olderThanDays: Int = 7) {
        val cutoff = System.currentTimeMillis() - olderThanDays * 86_400_000L
        db().execSQL("DELETE FROM sync_queue WHERE synced=1 AND createdAt<?", arrayOf(cutoff))
    }

    fun deleteSyncEntity(entityType: String, entityId: String) {
        try { db().execSQL("DELETE FROM $entityType WHERE id=?", arrayOf(entityId)) } catch (_: Exception) {}
    }

    fun applySyncPayload(entityType: String, entityId: String, data: Map<String, Any?>?) {
        // Placeholder for per-entity-type sync application.
        // Will be expanded as each domain is fully integrated.
        if (data == null) return
    }
}
