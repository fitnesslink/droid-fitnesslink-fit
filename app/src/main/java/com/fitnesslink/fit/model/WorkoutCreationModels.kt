package com.fitnesslink.fit.model

import java.util.UUID

enum class MuscleGroup(val label: String) {
    Chest("Chest"), Back("Back"), Shoulders("Shoulders"),
    Legs("Legs"), Arms("Arms"), Core("Core")
}

enum class EquipmentType(val label: String) {
    Barbell("Barbell"), Dumbbell("Dumbbell"), Cable("Cable"),
    Bodyweight("Bodyweight"), Machine("Machine"), Band("Band")
}

enum class BrowserTab(val label: String) {
    All("All"), Favorites("Favorites"), Recent("Recent")
}

enum class ExerciseGroupType(val label: String) {
    Superset("Superset"), Circuit("Circuit"), Interval("Interval")
}

data class TaskSetConfig(
    val id: String = UUID.randomUUID().toString(),
    val setNumber: Int = 1,
    val reps: Int = 10,
    val weightKg: Double? = null,
    val isDropSet: Boolean = false,
    val isWarmup: Boolean = false,
    val durationSeconds: Int? = null
)

data class SetPreset(
    val label: String,
    val sets: Int,
    val reps: Int
) {
    companion object {
        val presets = listOf(
            SetPreset("3x10", 3, 10),
            SetPreset("4x8", 4, 8),
            SetPreset("5x5", 5, 5),
            SetPreset("3x12", 3, 12)
        )
    }
}

data class MovementLibraryItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val muscleGroup: String = "",
    val equipment: String = "",
    val isFavorite: Boolean = false
)
