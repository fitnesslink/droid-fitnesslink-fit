package com.fitnesslink.fit.persistence

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.fitnesslink.fit.model.AnglePhoto
import com.fitnesslink.fit.model.BodyMeasurementValue
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.ModelGson
import com.fitnesslink.fit.model.ProgressPhotoEntry
import com.fitnesslink.fit.model.WeightEntry
import com.fitnesslink.fit.model.WeightUnit
import com.google.gson.reflect.TypeToken
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// Progress-tracking DB helpers `DatabaseManager` lacks: reads and deletes for
// `weight_entries`, `measurement_entries` and `progress_photo_entries` (the
// insert helpers already exist as sync-apply targets), plus the "closest
// weigh-in / measurement to a date" lookups the photo screens link to.
// Extension functions over `rawDatabase()`; no schema changes.

private fun progressDb(): SQLiteDatabase = DatabaseManager.rawDatabase()

private fun lcProgressKey(s: String) = s.lowercase(Locale.US)

private fun Cursor.pText(col: Int): String = getString(col) ?: ""

private val measurementListType = object : TypeToken<List<BodyMeasurementValue>>() {}.type
private val anglePhotoListType = object : TypeToken<List<AnglePhoto>>() {}.type

// MARK: - Weight

private fun readWeight(c: Cursor) = WeightEntry(
    id = c.pText(0), userId = c.pText(1), weight = c.getDouble(2),
    unit = WeightUnit.entries.firstOrNull { it.name.equals(c.pText(3), true) || it.label.equals(c.pText(3), true) } ?: WeightUnit.defaultUnit,
    date = Date(c.getLong(4)), notes = c.pText(5), createdAt = Date(c.getLong(6))
)

/** Every cached weigh-in, newest first (iOS `DatabaseManager.weightEntries()`). */
fun DatabaseManager.weightEntriesCached(): List<WeightEntry> = progressDb().rawQuery(
    "SELECT id, userId, weight, unit, date, COALESCE(notes,''), createdAt FROM weight_entries ORDER BY date DESC, createdAt DESC", null
).use { c -> buildList { while (c.moveToNext()) add(readWeight(c)) } }

fun DatabaseManager.deleteWeightEntryRow(id: String) {
    progressDb().delete("weight_entries", "LOWER(id)=?", arrayOf(lcProgressKey(id)))
}

// MARK: - Measurements

private fun readMeasurementEntry(c: Cursor): MeasurementEntry {
    val parts: List<BodyMeasurementValue> = runCatching {
        ModelGson.instance.fromJson<List<BodyMeasurementValue>>(c.pText(3), measurementListType)
    }.getOrNull() ?: emptyList()
    return MeasurementEntry(
        id = c.pText(0), userId = c.pText(1), date = Date(c.getLong(2)),
        measurements = parts, notes = c.pText(4), createdAt = Date(c.getLong(5))
    )
}

/** Every cached measurement entry, newest first (iOS `DatabaseManager.measurementEntries()`). */
fun DatabaseManager.measurementEntriesCached(): List<MeasurementEntry> = progressDb().rawQuery(
    "SELECT id, userId, date, measurementsJson, COALESCE(notes,''), createdAt FROM measurement_entries ORDER BY date DESC, createdAt DESC", null
).use { c -> buildList { while (c.moveToNext()) add(readMeasurementEntry(c)) } }

fun DatabaseManager.measurementEntryById(id: String): MeasurementEntry? = progressDb().rawQuery(
    "SELECT id, userId, date, measurementsJson, COALESCE(notes,''), createdAt FROM measurement_entries WHERE LOWER(id)=? LIMIT 1",
    arrayOf(lcProgressKey(id))
).use { c -> if (c.moveToFirst()) readMeasurementEntry(c) else null }

fun DatabaseManager.deleteMeasurementEntryRow(id: String) {
    progressDb().delete("measurement_entries", "LOWER(id)=?", arrayOf(lcProgressKey(id)))
}

// MARK: - Progress photos

private fun readPhotoEntry(c: Cursor): ProgressPhotoEntry {
    val photos: List<AnglePhoto> = runCatching {
        ModelGson.instance.fromJson<List<AnglePhoto>>(c.pText(3), anglePhotoListType)
    }.getOrNull() ?: emptyList()
    return ProgressPhotoEntry(
        id = c.pText(0), userId = c.pText(1), date = Date(c.getLong(2)),
        photos = photos, notes = c.pText(4), createdAt = Date(c.getLong(5))
    )
}

/** Every cached photo entry (one per day, multi-angle), newest first. */
fun DatabaseManager.progressPhotoEntriesCached(): List<ProgressPhotoEntry> = progressDb().rawQuery(
    "SELECT id, userId, date, photosJson, COALESCE(notes,''), createdAt FROM progress_photo_entries ORDER BY date DESC, createdAt DESC", null
).use { c -> buildList { while (c.moveToNext()) add(readPhotoEntry(c)) } }

fun DatabaseManager.progressPhotoEntryById(id: String): ProgressPhotoEntry? = progressDb().rawQuery(
    "SELECT id, userId, date, photosJson, COALESCE(notes,''), createdAt FROM progress_photo_entries WHERE LOWER(id)=? LIMIT 1",
    arrayOf(lcProgressKey(id))
).use { c -> if (c.moveToFirst()) readPhotoEntry(c) else null }

fun DatabaseManager.deleteProgressPhotoEntryRow(id: String) {
    progressDb().delete("progress_photo_entries", "LOWER(id)=?", arrayOf(lcProgressKey(id)))
}

// MARK: - Nearest-to-date lookups (iOS `MockDataProvider.closestWeight(to:)` replacement)

/** The weigh-in nearest to [date] within [withinDays] (default 14), or null. */
fun DatabaseManager.closestWeightEntry(date: Date, withinDays: Int = 14): WeightEntry? =
    weightEntriesCached().nearest(date, withinDays) { it.date }

/** The measurement entry nearest to [date] within [withinDays] (default 30), or null. */
fun DatabaseManager.closestMeasurementEntry(date: Date, withinDays: Int = 30): MeasurementEntry? =
    measurementEntriesCached().nearest(date, withinDays) { it.date }

private fun <T> List<T>.nearest(date: Date, withinDays: Int, dateOf: (T) -> Date): T? {
    val window = withinDays.toLong() * 86_400_000L
    return filter { abs(dateOf(it).time - date.time) <= window }
        .minByOrNull { abs(dateOf(it).time - date.time) }
}
