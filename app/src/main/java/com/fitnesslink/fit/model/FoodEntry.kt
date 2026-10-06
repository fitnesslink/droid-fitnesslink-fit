package com.fitnesslink.fit.model

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Wire raw values are the iOS / backend names ("Breakfast", "Lunch", …).
 * The local DB stores the enum `name` and reads via `valueOf`.
 */
@JsonAdapter(MealType.Adapter::class)
enum class MealType(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    /** iOS `rawValue`. */
    val wireValue: String get() = displayName

    companion object {
        fun fromWire(raw: String?): MealType? =
            raw?.let { r -> entries.firstOrNull { it.displayName.equals(r, ignoreCase = true) || it.name.equals(r, ignoreCase = true) } }
    }

    class Adapter : TypeAdapter<MealType>() {
        override fun write(out: JsonWriter, value: MealType?) {
            if (value == null) out.nullValue() else out.value(value.wireValue)
        }

        override fun read(reader: JsonReader): MealType? {
            if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
            return fromWire(reader.nextString()) ?: BREAKFAST
        }
    }
}

@JsonAdapter(FoodEntry.Adapter::class)
data class FoodEntry(
    /** Lowercased to match the backend (SQLite TEXT comparison is case-sensitive). */
    val id: String = UUID.randomUUID().toString().lowercase(Locale.US),
    val name: String = "",
    val calories: Int = 0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val servingSize: Double = 1.0,
    val servingUnit: String = "serving",
    val mealType: MealType = MealType.BREAKFAST,
    val loggedAt: Date = Date(),
    val isCustomTemplate: Boolean = false,

    // FTNSSLNK-186/199: structured portion logging. All optional so legacy
    // rows, older payloads and queued sync payloads keep decoding.

    /** Source catalog food id; null for manual / free-form entries. */
    val foodId: String? = null,
    /** Number of [portionUnit] logged (e.g. 2 for "2 slices"). */
    val quantity: Double? = null,
    /** Structured portion unit label (e.g. "slice", "g", "cup"). */
    val portionUnit: String? = null,
    /** Grams for one [portionUnit] at log time; total grams = quantity × this. */
    val portionGramWeight: Double? = null,

    // FTNSSLNK-213: extended nutrients.
    val fiber: Double? = null,
    val sugars: Double? = null,
    val sodium: Double? = null,
    val saturatedFat: Double? = null,
    val cholesterol: Double? = null,

    // FTNSSLNK-449: provenance. `source` is one of [Source]; null = manual on legacy rows.
    val source: String? = null,
    val aiConfidence: String? = null,
    val aiGenerationId: String? = null
) {
    /** The backend's closed set for [source]. */
    object Source {
        const val manual = "manual"
        const val library = "library"
        const val barcode = "barcode"
        const val aiEstimate = "ai_estimate"
        /** Estimated from a meal photo (Snap a Meal). */
        const val aiPhoto = "ai_photo"
        const val copied = "copied"

        /** Map an estimate-macros `source` onto the entry's provenance. */
        fun fromEstimate(estimateSource: String): String = when (estimateSource) {
            "library" -> library
            "ai_estimate" -> aiEstimate
            else -> manual
        }
    }

    val isAiEstimate: Boolean get() = source == Source.aiEstimate

    /** "AI estimated · high confidence" for badges; null unless AI-estimated. */
    val aiBadgeText: String?
        get() {
            if (!isAiEstimate) return null
            if (aiConfidence.isNullOrEmpty()) return "AI estimated"
            return "AI estimated · $aiConfidence confidence"
        }

    class Adapter : TolerantAdapterFactory<FoodEntry>(FoodEntry::class.java)

    companion object {
        /**
         * Timestamp to stamp on a new entry being logged to [day]. Today keeps
         * the real clock time; a backfilled past day gets local noon so the
         * server's UTC day bucket and the local day-range query agree.
         */
        fun timestamp(loggingTo: Date?): Date {
            if (loggingTo == null) return Date()
            val cal = Calendar.getInstance()
            val today = Calendar.getInstance()
            cal.time = loggingTo
            val sameDay = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            if (sameDay) return Date()
            cal.set(Calendar.HOUR_OF_DAY, 12); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            return cal.time
        }
    }
}
