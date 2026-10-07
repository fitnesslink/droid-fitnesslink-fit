package com.fitnesslink.fit.persistence

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.fitnesslink.fit.model.GroceryItem
import com.fitnesslink.fit.model.GroceryList
import com.fitnesslink.fit.model.MealSlot
import com.fitnesslink.fit.model.MealSlotFood
import java.util.Locale

// Meal-plan / grocery DB helpers that `DatabaseManager` lacks (CONTRACTS:
// domain DAOs are extension functions over `rawDatabase()`).
//
// One new table, `meal_slot_foods`: the itemized foods of a planned meal
// (FTNSSLNK-205). iOS keeps these in memory + the sync queue only; caching
// them locally lets the builder re-open a meal pre-loaded after a process
// death and lets the offline copy-week replay carry foods along. Created on
// first use by [ensureMealPlanSchema], guarded by `sqlite_master`, so the
// integrator can fold it into the versioned migration later.

private fun lc(s: String) = s.lowercase(Locale.US)

@Volatile private var mealPlanSchemaReady = false

/** Create `meal_slot_foods` if this install predates it. Idempotent and cheap after the first call. */
fun DatabaseManager.ensureMealPlanSchema() {
    if (mealPlanSchemaReady) return
    val db = rawDatabase()
    val exists = db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='meal_slot_foods'", null
    ).use { it.moveToFirst() }
    if (!exists) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS meal_slot_foods (
                id TEXT PRIMARY KEY,
                slotId TEXT NOT NULL,
                foodId TEXT,
                name TEXT NOT NULL DEFAULT '',
                servings REAL NOT NULL DEFAULT 1,
                servingSize REAL,
                servingUnit TEXT,
                calories REAL NOT NULL DEFAULT 0,
                protein REAL NOT NULL DEFAULT 0,
                fat REAL NOT NULL DEFAULT 0,
                carbs REAL NOT NULL DEFAULT 0,
                sortOrder INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_slot_foods_slot ON meal_slot_foods(slotId)")
    }
    mealPlanSchemaReady = true
}

// MARK: - Itemized foods

private fun Cursor.readFood(): MealSlotFood = MealSlotFood(
    id = getString(0),
    foodId = if (isNull(1)) null else getString(1),
    name = getString(2) ?: "",
    servings = getDouble(3),
    servingSize = if (isNull(4)) null else getDouble(4),
    servingUnit = if (isNull(5)) null else getString(5),
    calories = getDouble(6),
    protein = getDouble(7),
    fat = getDouble(8),
    carbs = getDouble(9)
)

private const val FOOD_COLUMNS = "id, foodId, name, servings, servingSize, servingUnit, calories, protein, fat, carbs"

/** The itemized foods cached for [slotId], in add order. */
fun DatabaseManager.mealSlotFoods(slotId: String): List<MealSlotFood> {
    ensureMealPlanSchema()
    return rawDatabase().rawQuery(
        "SELECT $FOOD_COLUMNS FROM meal_slot_foods WHERE slotId=? ORDER BY sortOrder", arrayOf(lc(slotId))
    ).use { c -> buildList { while (c.moveToNext()) add(c.readFood()) } }
}

/** Replace-set the cached foods for [slotId] (index becomes `sortOrder`). */
fun DatabaseManager.saveMealSlotFoods(slotId: String, foods: List<MealSlotFood>) {
    ensureMealPlanSchema()
    val db = rawDatabase()
    db.beginTransaction()
    try {
        db.delete("meal_slot_foods", "slotId=?", arrayOf(lc(slotId)))
        foods.forEachIndexed { index, food ->
            db.insertWithOnConflict("meal_slot_foods", null, ContentValues().apply {
                put("id", lc(food.id)); put("slotId", lc(slotId))
                if (food.foodId == null) putNull("foodId") else put("foodId", lc(food.foodId))
                put("name", food.name); put("servings", food.servings)
                if (food.servingSize == null) putNull("servingSize") else put("servingSize", food.servingSize)
                if (food.servingUnit == null) putNull("servingUnit") else put("servingUnit", food.servingUnit)
                put("calories", food.calories); put("protein", food.protein)
                put("fat", food.fat); put("carbs", food.carbs); put("sortOrder", index)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

fun DatabaseManager.deleteMealSlotFoods(slotId: String) {
    ensureMealPlanSchema()
    rawDatabase().delete("meal_slot_foods", "slotId=?", arrayOf(lc(slotId)))
}

// MARK: - Slots with foods

/** Cache one slot: the flat `meal_slots` row plus its itemized foods. */
fun DatabaseManager.saveMealSlotWithFoods(slot: MealSlot) {
    insertMealSlot(slot)
    // An empty list on a flat (legacy / AI / copy) slot must not wipe foods
    // that an earlier itemized save cached — only replace when we have some.
    if (slot.foods.isNotEmpty()) saveMealSlotFoods(slot.id, slot.foods)
}

/** Delete a slot row and its cached foods (non-suspending twin of `DatabaseManager.deleteMealSlot`). */
fun DatabaseManager.deleteMealSlotLocal(id: String) {
    rawDatabase().delete("meal_slots", "LOWER(id)=?", arrayOf(lc(id)))
    deleteMealSlotFoods(id)
}

/** Slots in an inclusive `yyyy-MM-dd` range with their cached foods attached. */
fun DatabaseManager.mealSlotsWithFoods(fromIso: String, toIso: String): List<MealSlot> =
    mealSlots(fromIso, toIso).map { it.copy(foods = mealSlotFoods(it.id)) }

/** One cached slot (with foods) by id, or null. */
fun DatabaseManager.mealSlotWithFoods(id: String): MealSlot? {
    val wanted = lc(id)
    val slot = allMealSlots().firstOrNull { lc(it.id) == wanted } ?: return null
    return slot.copy(foods = mealSlotFoods(slot.id))
}

/**
 * Reconcile the local cache of one date range with the server's slots: rows
 * the server no longer has are dropped, the rest upserted with foods.
 */
fun DatabaseManager.reconcileMealSlots(fromIso: String, toIso: String, remote: List<MealSlot>) {
    val remoteIds = remote.map { lc(it.id) }.toSet()
    mealSlots(fromIso, toIso).filter { lc(it.id) !in remoteIds }.forEach { deleteMealSlotLocal(it.id) }
    remote.forEach { slot ->
        insertMealSlot(slot)
        saveMealSlotFoods(slot.id, slot.foods)
    }
}

// MARK: - Grocery lists / items

/** Replace every cached item of one list with the server's rows (other lists untouched). */
fun DatabaseManager.replaceGroceryItemsForList(listId: String, items: List<GroceryItem>) {
    val db = rawDatabase()
    db.beginTransaction()
    try {
        db.delete("grocery_items", "LOWER(groceryListId)=?", arrayOf(lc(listId)))
        items.forEach { insertGroceryItem(it.copy(groceryListId = it.groceryListId ?: listId)) }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

/** Replace the cached list index (`grocery_lists`) with the server's. */
fun DatabaseManager.replaceGroceryLists(lists: List<GroceryList>) {
    val db = rawDatabase()
    db.beginTransaction()
    try {
        db.delete("grocery_lists", null, null)
        lists.forEach { upsertGroceryList(it) }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

fun DatabaseManager.groceryListById(id: String): GroceryList? {
    val wanted = lc(id)
    return groceryLists().firstOrNull { lc(it.id) == wanted }
}

/** The cached default list, if the index has been fetched. */
fun DatabaseManager.defaultGroceryList(): GroceryList? = groceryLists().firstOrNull { it.isDefault }

/** Rewrite `sortOrder` to match [orderedIds] (unknown ids ignored). */
fun DatabaseManager.reorderGroceryItemsLocal(orderedIds: List<String>) {
    val db = rawDatabase()
    db.beginTransaction()
    try {
        orderedIds.forEachIndexed { index, id ->
            db.execSQL("UPDATE grocery_items SET sortOrder=? WHERE LOWER(id)=?", arrayOf(index, lc(id)))
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

/** Set the checked flag explicitly (the `toggleGroceryItem` helper flips blindly). */
fun DatabaseManager.setGroceryItemChecked(id: String, checked: Boolean) {
    rawDatabase().execSQL("UPDATE grocery_items SET isChecked=? WHERE LOWER(id)=?", arrayOf(if (checked) 1 else 0, lc(id)))
}
