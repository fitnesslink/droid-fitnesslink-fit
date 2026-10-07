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
    FOREARMS("Forearms", "Arms");

    /**
     * iOS `BodyPart.anatomyNameCandidates`: the server taxonomy is a MUSCLE
     * list (Glutes, Core, Thighs…), not tape-measure landmarks, so several
     * landmarks fall back to the nearest muscle group. Most-specific first.
     */
    val anatomyNameCandidates: List<String>
        get() = when (this) {
            CHEST -> listOf("Chest", "Pectoralis Major")
            WAIST -> listOf("Waist", "Core", "Rectus abdominis", "Obliques")
            HIPS -> listOf("Hips", "Glutes", "Gluteus Maximus")
            LEFT_BICEP -> listOf("Left Bicep", "Bicep", "Biceps", "Biceps Brachii")
            RIGHT_BICEP -> listOf("Right Bicep", "Bicep", "Biceps", "Biceps Brachii")
            LEFT_THIGH -> listOf("Left Thigh", "Thigh", "Thighs", "Quadriceps")
            RIGHT_THIGH -> listOf("Right Thigh", "Thigh", "Thighs", "Quadriceps")
            NECK -> listOf("Neck")
            CALVES -> listOf("Calves", "Calf", "Gastrocnemius")
            FOREARMS -> listOf("Forearms", "Forearm", "Forearm Flexors")
        }

    companion object {
        val categoryOrder: List<String> = listOf("Core", "Arms", "Legs", "Other")

        /**
         * iOS `ClassificationStore.bodyPart(for:)`: reverse-map an anatomy
         * display name to the first part whose candidates contain it.
         * Laterality is lost when the taxonomy only has the group (both
         * biceps resolve "Biceps" → LEFT_BICEP), exactly as on iOS.
         */
        fun fromAnatomyName(name: String?): BodyPart? {
            val lc = name?.trim()?.lowercase(Locale.US) ?: return null
            if (lc.isEmpty()) return null
            return entries.firstOrNull { part -> part.anatomyNameCandidates.any { it.lowercase(Locale.US) == lc } }
        }
    }
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
