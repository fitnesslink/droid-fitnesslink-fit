package com.fitnesslink.fit.model.api

data class UserPreference(
    val id: EntityID,
    val userId: EntityID,
    val language: String? = null,
    val timezone: String? = null,
    val darkMode: Boolean = false,
    val workoutSessionType: WorkoutSessionType = WorkoutSessionType.STANDARD
)
