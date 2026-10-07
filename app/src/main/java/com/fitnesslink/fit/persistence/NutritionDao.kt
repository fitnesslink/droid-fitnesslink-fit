package com.fitnesslink.fit.persistence

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.NutritionDailySummary
import com.fitnesslink.fit.network.dto.NutritionSummaryRange
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nutrition-core persistence layered over `DatabaseManager` (Wave 3, agent A).
 *
 * Adds ONE table, `nutrition_cache` — a small key/value JSON cache used for
 * the daily-summary snapshots the dashboard renders offline
 * (`summary:yyyy-MM-dd`), the last summary range per week
 * (`range:from:to`), the nutrition profile / targets (`profile`, `targets`,
 * `profileState`) and the displayed-micros preference (`displayedMicros`).
 * Created lazily by [ensureNutritionSchema] (guarded by `PRAGMA table_info`)
 * per CONTRACTS.md; the integrator folds it into the versioned migration.
 */

private const val CACHE_TABLE = "nutrition_cache"
private val schemaEnsured = AtomicBoolean(false)

fun DatabaseManager.ensureNutritionSchema() {
    if (schemaEnsured.get()) return
    val db = rawDatabase()
    val exists = db.rawQuery("PRAGMA table_info($CACHE_TABLE)", null).use { it.count > 0 }
    if (!exists) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS $CACHE_TABLE (
                key TEXT PRIMARY KEY,
                json TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )"""
        )
    }
    schemaEnsured.set(true)
}

// MARK: - Generic JSON cache

fun DatabaseManager.nutritionCacheGet(key: String): String? {
    ensureNutritionSchema()
    return rawDatabase().rawQuery("SELECT json FROM $CACHE_TABLE WHERE key=?", arrayOf(key)).use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}

fun DatabaseManager.nutritionCacheUpdatedAt(key: String): Long? {
    ensureNutritionSchema()
    return rawDatabase().rawQuery("SELECT updatedAt FROM $CACHE_TABLE WHERE key=?", arrayOf(key)).use { c ->
        if (c.moveToFirst()) c.getLong(0) else null
    }
}

fun DatabaseManager.nutritionCachePut(key: String, json: String) {
    ensureNutritionSchema()
    rawDatabase().insertWithOnConflict(
        CACHE_TABLE, null,
        ContentValues().apply { put("key", key); put("json", json); put("updatedAt", System.currentTimeMillis()) },
        SQLiteDatabase.CONFLICT_REPLACE
    )
}

fun DatabaseManager.nutritionCacheDelete(key: String) {
    ensureNutritionSchema()
    rawDatabase().delete(CACHE_TABLE, "key=?", arrayOf(key))
}

fun DatabaseManager.nutritionCacheDeletePrefix(prefix: String) {
    ensureNutritionSchema()
    rawDatabase().delete(CACHE_TABLE, "key LIKE ?", arrayOf("$prefix%"))
}

fun DatabaseManager.clearNutritionCache() {
    ensureNutritionSchema()
    rawDatabase().delete(CACHE_TABLE, null, null)
}

inline fun <reified T> DatabaseManager.nutritionCacheObject(key: String): T? {
    val json = nutritionCacheGet(key) ?: return null
    return try { ApiClient.gson.fromJson(json, T::class.java) } catch (_: Exception) { null }
}

fun DatabaseManager.nutritionCachePutObject(key: String, value: Any) {
    nutritionCachePut(key, ApiClient.gson.toJson(value))
}

// MARK: - Daily summary cache

fun summaryCacheKey(date: String) = "summary:$date"
fun rangeCacheKey(from: String, to: String) = "range:$from:$to"

fun DatabaseManager.cachedDailySummary(date: String): NutritionDailySummary? = nutritionCacheObject(summaryCacheKey(date))

fun DatabaseManager.cacheDailySummary(summary: NutritionDailySummary) {
    if (summary.date.isEmpty()) return
    nutritionCachePutObject(summaryCacheKey(summary.date), summary)
}

fun DatabaseManager.invalidateDailySummary(date: String) = nutritionCacheDelete(summaryCacheKey(date))

fun DatabaseManager.cachedSummaryRange(from: String, to: String): NutritionSummaryRange? = nutritionCacheObject(rangeCacheKey(from, to))

fun DatabaseManager.cacheSummaryRange(range: NutritionSummaryRange) {
    if (range.from.isEmpty() || range.to.isEmpty()) return
    nutritionCachePutObject(rangeCacheKey(range.from, range.to), range)
}

fun DatabaseManager.invalidateAllSummaries() {
    nutritionCacheDeletePrefix("summary:")
    nutritionCacheDeletePrefix("range:")
}

// MARK: - Food-entry reads the dashboard needs

/** Entries logged to the local calendar day containing [date] (fixes the all-time "today" bug). */
fun DatabaseManager.foodEntries(forDate: Date): List<FoodEntry> = foodEntries(forDate.time)

/** Entries for the `yyyy-MM-dd` day key. */
fun DatabaseManager.foodEntries(dayKey: String): List<FoodEntry> =
    MealPlanDay.date(dayKey)?.let { foodEntries(it) } ?: emptyList()

/** Entries whose `loggedAt` falls inside the inclusive local-day range. */
fun DatabaseManager.foodEntriesInRange(from: Date, to: Date): List<FoodEntry> {
    val start = MealPlanDay.dayStart(from).time
    val end = MealPlanDay.addDays(MealPlanDay.dayStart(to), 1).time
    return rawDatabase().rawQuery(
        "SELECT id,name,calories,protein,fat,carbs,servingSize,servingUnit,mealType,loggedAt,isCustomTemplate," +
            "foodId,quantity,portionUnit,portionGramWeight,fiber,sugars,sodium,saturatedFat,cholesterol,source,aiConfidence,aiGenerationId " +
            "FROM food_entries WHERE loggedAt>=? AND loggedAt<? AND isCustomTemplate=0 ORDER BY loggedAt",
        arrayOf(start.toString(), end.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    FoodEntry(
                        id = c.getString(0) ?: "", name = c.getString(1) ?: "", calories = c.getInt(2),
                        protein = c.getDouble(3), fat = c.getDouble(4), carbs = c.getDouble(5),
                        servingSize = c.getDouble(6), servingUnit = c.getString(7) ?: "serving",
                        mealType = com.fitnesslink.fit.model.MealType.fromWire(c.getString(8)) ?: com.fitnesslink.fit.model.MealType.BREAKFAST,
                        loggedAt = Date(c.getLong(9)), isCustomTemplate = c.getInt(10) != 0,
                        foodId = if (c.isNull(11)) null else c.getString(11),
                        quantity = if (c.isNull(12)) null else c.getDouble(12),
                        portionUnit = if (c.isNull(13)) null else c.getString(13),
                        portionGramWeight = if (c.isNull(14)) null else c.getDouble(14),
                        fiber = if (c.isNull(15)) null else c.getDouble(15),
                        sugars = if (c.isNull(16)) null else c.getDouble(16),
                        sodium = if (c.isNull(17)) null else c.getDouble(17),
                        saturatedFat = if (c.isNull(18)) null else c.getDouble(18),
                        cholesterol = if (c.isNull(19)) null else c.getDouble(19),
                        source = if (c.isNull(20)) null else c.getString(20),
                        aiConfidence = if (c.isNull(21)) null else c.getString(21),
                        aiGenerationId = if (c.isNull(22)) null else c.getString(22)
                    )
                )
            }
        }
    }
}

/** Day keys (`yyyy-MM-dd`) with at least one logged entry in the range — offline completeness dots. */
fun DatabaseManager.loggedDayKeys(from: Date, to: Date): Set<String> =
    foodEntriesInRange(from, to).map { MealPlanDay.string(it.loggedAt) }.toSet()
