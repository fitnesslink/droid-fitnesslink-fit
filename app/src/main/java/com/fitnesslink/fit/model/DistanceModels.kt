package com.fitnesslink.fit.model

import com.google.gson.annotations.SerializedName
import java.util.Locale
import kotlin.math.roundToLong

// MARK: - Distance unit (FTNSSLNK-409)

/**
 * Unit for logged / prescribed distances. Stored per row next to the value
 * (`workout_session_history.distanceUnit`) — the wire contract is
 * `"km" | "mi"` lowercase.
 */
enum class DistanceUnit(val rawValue: String) {
    @SerializedName("km") KM("km"),
    @SerializedName("mi") MI("mi");

    /** Short suffix — "km" / "mi". */
    val label: String get() = rawValue
    /** "KM" / "MI" for the active screen's target line. */
    val uppercasedLabel: String get() = rawValue.uppercase(Locale.US)

    fun convert(value: Double, to: DistanceUnit): Double {
        if (this == to) return value
        return when (this to to) {
            KM to MI -> value / kmPerMile
            MI to KM -> value * kmPerMile
            else -> value
        }
    }

    companion object {
        const val kmPerMile = 1.609344

        /** Locale default: metric regions log kilometres, the rest miles. */
        val defaultUnit: DistanceUnit
            get() = if (Locale.getDefault().country in setOf("US", "LR", "MM")) MI else KM

        /**
         * The user's preference. Settings layer assigns it (iOS
         * `SettingsStore.distanceUnit`); defaults from the locale.
         */
        @Volatile var current: DistanceUnit = defaultUnit

        /** Wire / DB value → unit, case-insensitive; null for anything else. */
        fun fromWire(wire: String?): DistanceUnit? {
            val key = wire?.trim()?.lowercase(Locale.US) ?: return null
            return entries.firstOrNull { it.rawValue == key }
        }

        /** Two decimals, trailing zeros dropped — "5", "2.5", "1.25". */
        fun format(value: Double): String {
            val rounded = (value * 100).roundToLong() / 100.0
            if (rounded == Math.rint(rounded)) return rounded.toLong().toString()
            var s = String.format(Locale.US, "%.2f", rounded)
            while (s.endsWith("0")) s = s.dropLast(1)
            return s
        }

        /** "2.5 km" — the value rendered in its own unit. */
        fun display(value: Double, unit: DistanceUnit): String = "${format(value)} ${unit.label}"

        /** A stored distance rendered in the user's current unit. */
        fun displayInCurrent(value: Double, unit: DistanceUnit): String =
            display(unit.convert(value, current), current)
    }
}

/** A prescribed or logged distance with its unit. */
data class DistanceValue(
    val value: Double,
    val unit: DistanceUnit
) {
    /** `2 KM` — the active screen's target form. */
    val targetText: String get() = "${DistanceUnit.format(value)} ${unit.uppercasedLabel}"
    /** `2.5 km` */
    val text: String get() = DistanceUnit.display(value, unit)

    fun converted(to: DistanceUnit): DistanceValue = DistanceValue(unit.convert(value, to), to)
}

// MARK: - Distance-capable movements

/**
 * When the Log Set sheet offers a Distance field on its own: the task
 * prescribes a distance, the movement carries cardio fields, its anatomy tag
 * is Cardio, or its name reads like distance work.
 */
object DistanceRule {
    val nameKeywords: List<String> = listOf(
        "run", "jog", "sprint", "row", "bike", "cycle", "cycling", "ski", "swim",
        "walk", "hike", "ruck", "treadmill", "elliptical", "assault", "airdyne",
        "sled", "carry", "shuttle", "stair", "erg", "metres", "meters", "mile",
        "km", "kilomet"
    )

    fun isDistanceCapable(task: WorkoutTask, anatomyNames: List<String> = emptyList()): Boolean {
        if (!task.isMovement) return false
        if (task.prescribedDistance != null) return true
        if (task.paceSeconds != null || task.speed != null || task.incline != null) return true
        if (anatomyNames.any { it.contains("cardio", ignoreCase = true) }) return true
        return matchesName(task.name)
    }

    /** Whole-word keyword match on the movement name. */
    fun matchesName(name: String): Boolean {
        val words = name.lowercase(Locale.US).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return words.any { word ->
            nameKeywords.any { keyword ->
                word == keyword || word == keyword + "s" || word == keyword + "ing" ||
                    (keyword.length >= 5 && word.startsWith(keyword))
            }
        }
    }

    private val distancePattern = Regex(
        """(?<![\d.])(\d+(?:\.\d+)?)\s*(km|kilometers?|kilometres?|mi|miles?|m|meters?|metres?)\b""",
        RegexOption.IGNORE_CASE
    )

    /** `"2 km"`, `"1.5 mi"`, `"5 miles"`, `"400 m"` (metres → km) out of a free-form metric string. */
    fun parseDistance(text: String): DistanceValue? {
        val match = distancePattern.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val unit = match.groupValues[2].lowercase(Locale.US)
        if (unit.startsWith("k")) return DistanceValue(value, DistanceUnit.KM)
        if (unit.startsWith("mi")) return DistanceValue(value, DistanceUnit.MI)
        return DistanceValue(value / 1000, DistanceUnit.KM)
    }
}

/**
 * The distance the task prescribes: the explicit `distance` + `distanceUnit`
 * when the builder set one, else a distance parsed out of the legacy
 * free-form `metric` ("2 km"). Null = none.
 */
val WorkoutTask.prescribedDistance: DistanceValue?
    get() {
        val d = distance
        if (d != null && d > 0) return DistanceValue(d, DistanceUnit.fromWire(distanceUnit) ?: DistanceUnit.current)
        if (!isMovement || metric.isEmpty()) return null
        return DistanceRule.parseDistance(metric)
    }
