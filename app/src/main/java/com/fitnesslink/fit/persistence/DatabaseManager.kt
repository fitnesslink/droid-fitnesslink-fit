package com.fitnesslink.fit.persistence

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.fitnesslink.fit.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

object DatabaseManager {
    private lateinit var database: SQLiteDatabase

    fun initialize(context: Context) {
        database = DatabaseHelper(context).writableDatabase
        DatabaseSeeder.seedIfNeeded()
    }

    fun initializeForTesting() {
        database = SQLiteDatabase.create(null)
        createAllTables(database)
    }

    private fun db(): SQLiteDatabase = database

    // MARK: - Schema

    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, "fitnesslink.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) = createAllTables(db)
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
    }

    private fun createAllTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL,
            isPersonalized INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS home_dashboards (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, progress TEXT NOT NULL,
            unit TEXT NOT NULL, goals TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS programs (
            id TEXT PRIMARY KEY, imageUrl TEXT NOT NULL DEFAULT '',
            name TEXT NOT NULL, time TEXT NOT NULL DEFAULT '',
            location TEXT NOT NULL DEFAULT '', trainingLevel TEXT NOT NULL DEFAULT '',
            description TEXT NOT NULL DEFAULT '')""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS workouts (
            id TEXT PRIMARY KEY, imageUrl TEXT NOT NULL DEFAULT '',
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

    // MARK: - Read Queries

    fun allWorkouts(): List<WorkoutList> = db().rawQuery("SELECT id, imageUrl, name, time FROM workouts", null).use { c ->
        buildList { while (c.moveToNext()) add(WorkoutList(c.str(0), c.str(1), c.str(2), c.str(3), false)) }
    }

    fun workout(id: String): Workout? = db().rawQuery("SELECT id, imageUrl, name, time, location, trainingLevel, description, phasesJson FROM workouts WHERE id=?", arrayOf(id)).use { c ->
        if (!c.moveToFirst()) return null
        Workout(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.str(5), c.str(6), phasesFromJson(c.str(7)))
    }

    fun allPrograms(): List<ProgramList> = db().rawQuery("SELECT id, imageUrl, name, time FROM programs", null).use { c ->
        buildList { while (c.moveToNext()) add(ProgramList(c.str(0), c.str(1), c.str(2), c.str(3), false)) }
    }

    fun program(id: String): Program? = db().rawQuery("SELECT id, imageUrl, name, time, location, trainingLevel, description FROM programs WHERE id=?", arrayOf(id)).use { c ->
        if (!c.moveToFirst()) return null
        Program(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.str(5), c.str(6))
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

    fun calendarContent(): List<FitnessContent> = db().rawQuery("SELECT id, title, programId, workoutId, mealPlanId, status FROM calendar_content", null).use { c ->
        buildList { while (c.moveToNext()) add(FitnessContent(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), c.str(5))) }
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

    data class UserInfo(val id: String, val name: String, val email: String, val isPersonalized: Boolean)

    fun user(): UserInfo? = db().rawQuery("SELECT id, name, email, isPersonalized FROM users LIMIT 1", null).use { c ->
        if (!c.moveToFirst()) return null
        UserInfo(c.str(0), c.str(1), c.str(2), c.getInt(3) != 0)
    }

    fun catalogPrograms(): List<CatalogItem> = db().rawQuery("SELECT id, name, imageUrl, time FROM programs", null).use { c ->
        buildList { while (c.moveToNext()) add(CatalogItem(c.str(0), c.str(1), c.str(2), c.str(3))) }
    }

    fun catalogWorkouts(): List<CatalogItem> = db().rawQuery("SELECT id, name, imageUrl, time FROM workouts", null).use { c ->
        buildList { while (c.moveToNext()) add(CatalogItem(c.str(0), c.str(1), c.str(2), c.str(3))) }
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
            put("id", p.id); put("imageUrl", p.imageUrl); put("name", p.name)
            put("time", p.time); put("location", p.location)
            put("trainingLevel", p.trainingLevel); put("description", p.description)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertWorkout(w: Workout) {
        db().insertWithOnConflict("workouts", null, ContentValues().apply {
            put("id", w.id); put("imageUrl", w.imageUrl); put("name", w.name)
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
        put("iconUrl", t.iconUrl); put("videoUrl", t.videoUrl); put("nextImageUrl", t.nextImageUrl)
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
        metric = o.optString("metric", ""), iconUrl = o.optString("iconUrl", ""),
        videoUrl = o.optString("videoUrl", ""), nextImageUrl = o.optString("nextImageUrl", ""),
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
}
