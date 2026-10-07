package com.fitnesslink.fit.model

import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Wire raw values are "fl oz" / "ml" / "cups" (iOS `rawValue`); the DB stores the enum `name`. */
@JsonAdapter(WaterUnit.Adapter::class)
enum class WaterUnit(val rawValue: String) {
    OZ("fl oz"),
    ML("ml"),
    CUPS("cups");

    companion object {
        // Three countries don't use the metric system: US, Liberia, Myanmar.
        private val IMPERIAL_COUNTRIES = setOf("US", "LR", "MM")

        val defaultUnit: WaterUnit
            get() = if (Locale.getDefault().country in IMPERIAL_COUNTRIES) OZ else ML

        fun fromRaw(value: String?): WaterUnit =
            fromRawOrNull(value) ?: defaultUnit

        /** Strict: null for a unit this build doesn't know. */
        fun fromRawOrNull(value: String?): WaterUnit? =
            value?.let { v -> entries.firstOrNull { it.rawValue.equals(v, true) || it.name.equals(v, true) } }
    }

    fun convert(value: Double, target: WaterUnit): Double {
        if (this == target) return value
        val inMl = when (this) {
            ML -> value
            OZ -> value * 29.5735
            CUPS -> value * 236.588
        }
        return when (target) {
            ML -> inMl
            OZ -> inMl / 29.5735
            CUPS -> inMl / 236.588
        }
    }

    class Adapter : TypeAdapter<WaterUnit>() {
        override fun write(out: JsonWriter, value: WaterUnit?) {
            if (value == null) out.nullValue() else out.value(value.rawValue)
        }

        override fun read(reader: JsonReader): WaterUnit? {
            if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
            return fromRawOrNull(reader.nextString())
        }
    }
}

@JsonAdapter(WaterIntakeEntry.Adapter::class)
data class WaterIntakeEntry(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double = 0.0,
    val unit: WaterUnit = WaterUnit.defaultUnit,
    val loggedAt: Date = Date(),
    val notes: String = ""
) {
    class Adapter : TolerantAdapterFactory<WaterIntakeEntry>(WaterIntakeEntry::class.java) {
        override fun preprocess(raw: JsonObject) {
            // An unrecognized unit falls back to the locale default instead of a null field.
            val unit = raw.get("unit")?.takeIf { it.isJsonPrimitive }?.asString
            if (unit != null && WaterUnit.fromRawOrNull(unit) == null) raw.remove("unit")
        }
    }
}

@JsonAdapter(HydrationGoal.Adapter::class)
data class HydrationGoal(
    val id: String = UUID.randomUUID().toString(),
    val dailyGoal: Double = 64.0,
    val unit: WaterUnit = WaterUnit.defaultUnit,
    /**
     * FTNSSLNK-221: the user's preferred DISPLAY unit. Null = fall back to
     * the goal's own [unit]. Server-side so it follows the user across devices.
     */
    val preferredUnit: WaterUnit? = null
) {
    /** The unit every water surface displays in. */
    val displayUnit: WaterUnit get() = preferredUnit ?: unit

    class Adapter : TolerantAdapterFactory<HydrationGoal>(HydrationGoal::class.java) {
        override fun preprocess(raw: JsonObject) {
            val unit = raw.get("unit")?.takeIf { it.isJsonPrimitive }?.asString
            if (unit != null && WaterUnit.fromRawOrNull(unit) == null) raw.remove("unit")
            // An unrecognized preferredUnit degrades to null (fall back to `unit`).
            val preferred = raw.get("preferredUnit")?.takeIf { it.isJsonPrimitive }?.asString
            if (preferred != null && WaterUnit.fromRawOrNull(preferred) == null) raw.remove("preferredUnit")
        }
    }
}

/**
 * One row of `GET /water-entries/recent-amounts` (FTNSSLNK-221): a distinct
 * recently-logged custom (amount, unit), newest first.
 */
data class RecentWaterAmount(
    val amount: Double = 0.0,
    /** Canonical unit string ("fl oz" / "ml" / "cups"). */
    val unit: String = "",
    val lastLoggedAt: Date = Date()
) {
    val id: String get() = "$amount|$unit"

    /** Typed unit; null if the server adds a unit this build doesn't know (chip hidden). */
    val waterUnit: WaterUnit? get() = WaterUnit.fromRawOrNull(unit)
}

// MARK: - Dashboard preset taps (FTNSSLNK-195 / 221)

/**
 * The hydration card's +8 / +16 / +24 oz presets. A tap is converted into
 * the goal's unit so the server-side raw sum stays unit-consistent for
 * metric users (iOS `NutritionViewModel.logWaterPreset`).
 */
object HydrationPresets {
    val presetOunces: List<Double> = listOf(8.0, 16.0, 24.0)

    /** [ounces] expressed in [unit]; millilitres are rounded to a whole number. */
    fun amount(ounces: Double, unit: WaterUnit): Double {
        val converted = WaterUnit.OZ.convert(ounces, unit)
        return if (unit == WaterUnit.ML) Math.rint(converted) else converted
    }
}

/**
 * Undo stack for preset taps (iOS `WaterTapUndoStack`): only taps made this
 * session on the currently-viewed day are undoable; switching days clears
 * the stack. Pure value semantics so it is unit-testable.
 */
class WaterTapUndoStack {
    var dayKey: String = ""
        private set
    private val entryIds = ArrayDeque<String>()

    val canUndo: Boolean get() = entryIds.isNotEmpty()

    fun canUndo(dayKey: String): Boolean = canUndo && this.dayKey == dayKey

    fun recordTap(entryId: String, dayKey: String) {
        if (dayKey != this.dayKey) {
            entryIds.clear()
            this.dayKey = dayKey
        }
        entryIds.addLast(entryId)
    }

    /** The most recent tap's entry id for [dayKey], or null (and a reset) when the day changed. */
    fun popLastTap(dayKey: String): String? {
        if (dayKey != this.dayKey) {
            entryIds.clear()
            this.dayKey = dayKey
            return null
        }
        return entryIds.removeLastOrNull()
    }

    fun clear() {
        entryIds.clear()
        dayKey = ""
    }
}
