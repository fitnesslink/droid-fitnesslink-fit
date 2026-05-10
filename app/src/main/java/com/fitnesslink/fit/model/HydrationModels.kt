package com.fitnesslink.fit.model

import java.util.Date
import java.util.Locale
import java.util.UUID

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
            entries.firstOrNull { it.rawValue == value || it.name == value } ?: defaultUnit
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
}

data class WaterIntakeEntry(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double = 0.0,
    val unit: WaterUnit = WaterUnit.defaultUnit,
    val loggedAt: Date = Date(),
    val notes: String = ""
)

data class HydrationGoal(
    val id: String = UUID.randomUUID().toString(),
    val dailyGoal: Double = 64.0,
    val unit: WaterUnit = WaterUnit.defaultUnit
)
