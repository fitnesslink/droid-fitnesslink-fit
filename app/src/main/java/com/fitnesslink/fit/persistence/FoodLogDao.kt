package com.fitnesslink.fit.persistence

import android.database.sqlite.SQLiteDatabase
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealPlanDay
import java.util.Locale

// Food-logging DB helpers that `DatabaseManager` lacks (CONTRACTS: domain
// DAOs are extension functions over `rawDatabase()`). The `food_entries`
// table already carries every FoodEntry column (foodId / quantity /
// portionUnit / portionGramWeight / micros / source / aiConfidence /
// aiGenerationId were added by the versioned migration); `ensureFoodLogSchema`
// re-guards them with `PRAGMA table_info` for databases that skipped it.

private fun lc(s: String) = s.lowercase(Locale.US)

private val FOOD_LOG_COLUMNS = listOf(
    "foodId TEXT", "quantity REAL", "portionUnit TEXT", "portionGramWeight REAL",
    "fiber REAL", "sugars REAL", "sodium REAL", "saturatedFat REAL", "cholesterol REAL",
    "source TEXT", "aiConfidence TEXT", "aiGenerationId TEXT"
)

/** Idempotent: adds any `food_entries` column the versioned migration has not yet added, plus the recent-foods index. */
fun DatabaseManager.ensureFoodLogSchema() {
    val db = rawDatabase()
    val existing = HashSet<String>()
    db.rawQuery("PRAGMA table_info(food_entries)", null).use { c ->
        val nameIdx = c.getColumnIndex("name")
        while (c.moveToNext()) existing.add(c.getString(nameIdx))
    }
    for (def in FOOD_LOG_COLUMNS) {
        val column = def.substringBefore(' ')
        if (column !in existing) {
            try { db.execSQL("ALTER TABLE food_entries ADD COLUMN $def") } catch (_: Exception) { }
        }
    }
    try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_food_template ON food_entries(isCustomTemplate, name)") } catch (_: Exception) { }
}

/** One row by id (case-insensitive). */
fun DatabaseManager.foodEntry(id: String): FoodEntry? {
    val key = lc(id)
    return allFoodEntries().firstOrNull { lc(it.id) == key }
}

/** Rows whose `loggedAt` falls on the local day [date] (`yyyy-MM-dd`), template rows included. */
fun DatabaseManager.foodEntriesForDay(date: String): List<FoodEntry> {
    val day = MealPlanDay.date(date) ?: return emptyList()
    return foodEntries(forDate = day.time)
}

/** The user's saved custom foods (`isCustomTemplate` rows). */
fun DatabaseManager.customFoodTemplates(): List<FoodEntry> = allFoodEntries().filter { it.isCustomTemplate }

/**
 * Lowercased ids of queued food-entry writes. [actions] null = every action;
 * otherwise only rows whose action is in the set (e.g. update + delete, the
 * writes a server read must not clobber).
 */
fun DatabaseManager.pendingFoodEntryIds(actions: Set<String>?): Set<String> =
    pendingSyncEntries("food-entries")
        .filter { actions == null || it.action in actions }
        .map { lc(it.entityId) }
        .toSet()

/** Replace every custom-template row with [templates] (server-sourced refresh of "My Foods"). */
fun DatabaseManager.replaceCustomFoodTemplates(templates: List<FoodEntry>) {
    val db: SQLiteDatabase = rawDatabase()
    val keep = templates.map { lc(it.id) }.toSet()
    for (existing in customFoodTemplates()) {
        if (lc(existing.id) !in keep) db.delete("food_entries", "LOWER(id)=?", arrayOf(lc(existing.id)))
    }
    templates.forEach { insertFoodEntry(it) }
}
