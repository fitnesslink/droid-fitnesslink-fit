package com.fitnesslink.fit.model

import java.util.Date
import java.util.Locale
import java.util.UUID

enum class MeasurementUnit(val label: String) {
    INCHES("in"),
    CM("cm");

    companion object {
        val defaultUnit: MeasurementUnit
            get() {
                val country = Locale.getDefault().country
                return if (country in listOf("US", "LR", "MM")) INCHES else CM
            }
    }

    fun convert(value: Double, to: MeasurementUnit): Double {
        if (this == to) return value
        return when (this to to) {
            INCHES to CM -> value * 2.54
            CM to INCHES -> value / 2.54
            else -> value
        }
    }
}

enum class BodyPart(val displayName: String, val category: String) {
    CHEST("Chest", "Core"),
    WAIST("Waist", "Core"),
    HIPS("Hips", "Core"),
    LEFT_BICEP("Left Bicep", "Arms"),
    RIGHT_BICEP("Right Bicep", "Arms"),
    LEFT_THIGH("Left Thigh", "Legs"),
    RIGHT_THIGH("Right Thigh", "Legs"),
    NECK("Neck", "Other"),
    CALVES("Calves", "Legs"),
    FOREARMS("Forearms", "Arms")
}

data class BodyMeasurementValue(
    val id: String = UUID.randomUUID().toString(),
    val bodyPart: BodyPart = BodyPart.CHEST,
    val value: Double = 0.0,
    val unit: MeasurementUnit = MeasurementUnit.defaultUnit,
    val createdAt: Date = Date()
)

data class MeasurementEntry(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val date: Date = Date(),
    val measurements: List<BodyMeasurementValue> = emptyList(),
    val notes: String = "",
    val createdAt: Date = Date()
) {
    fun measurement(part: BodyPart): BodyMeasurementValue? =
        measurements.firstOrNull { it.bodyPart == part }
}
