package com.fitnesslink.fit.model

import com.google.gson.annotations.SerializedName
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

enum class WeightUnit(val label: String) {
    @SerializedName("lbs") LBS("lbs"),
    @SerializedName("kg") KG("kg");

    companion object {
        val defaultUnit: WeightUnit
            get() {
                val country = Locale.getDefault().country
                return if (country in listOf("US", "LR", "MM")) LBS else KG
            }

        // MARK: - Lifting weight display (FTNSSLNK-325)
        //
        // The canonical stored unit app-wide is POUNDS — every `weightKg`
        // field, the local DB, and the server wire all carry lbs despite the
        // legacy field name. kg is purely a rendering / input conversion
        // applied at the UI boundary.

        /** 1 kg = 2.20462 lbs. */
        const val lbsPerKg = 2.20462

        /**
         * The user's chosen display unit for lifting weights. Defaults to
         * lbs so existing users see no change. Settings layer assigns it.
         */
        @Volatile var current: WeightUnit = LBS

        /** A stored-lbs value converted into the current display unit. */
        fun displayValue(fromLbs: Double): Double = if (current == KG) fromLbs / lbsPerKg else fromLbs

        /**
         * A user-entered display-unit value converted back to storage lbs.
         * kg entries snap to quarter-pounds so values round-trip without drift.
         */
        fun storageLbs(fromDisplay: Double): Double {
            if (current != KG) return fromDisplay
            return (fromDisplay * lbsPerKg * 4).roundToLong() / 4.0
        }

        /** Number-only string in the display unit — "75", "34.5". */
        fun displayNumber(fromLbs: Double): String = format(displayValue(fromLbs))

        /** Whole-number string in the display unit — for large aggregates. */
        fun displayWholeNumber(fromLbs: Double): String = displayValue(fromLbs).roundToLong().toString()

        /** "75 lbs" / "34.5 kg". */
        fun displayString(fromLbs: Double): String = "${displayNumber(fromLbs)} ${current.label}"

        /** One decimal, trailing ".0" dropped. */
        fun format(value: Double): String {
            val rounded = (value * 10).roundToLong() / 10.0
            return if (rounded == Math.rint(rounded)) rounded.toLong().toString()
            else String.format(Locale.US, "%.1f", rounded)
        }
    }

    fun convert(value: Double, to: WeightUnit): Double {
        if (this == to) return value
        return when (this to to) {
            LBS to KG -> value * 0.453592
            KG to LBS -> value / 0.453592
            else -> value
        }
    }
}

data class WeightEntry(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val weight: Double = 0.0,
    val unit: WeightUnit = WeightUnit.defaultUnit,
    val date: Date = Date(),
    val notes: String = "",
    val createdAt: Date = Date()
)

data class WeightChartPoint(
    val id: String = UUID.randomUUID().toString(),
    val date: Date = Date(),
    val weight: Double = 0.0
)
