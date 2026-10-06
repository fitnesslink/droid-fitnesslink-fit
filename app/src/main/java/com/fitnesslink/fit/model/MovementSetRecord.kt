package com.fitnesslink.fit.model

/**
 * One recorded set of a movement (`workout_session_history` row) for PR
 * detection and history sheets: load, reps, time window and logged distance.
 * Port of iOS `MovementSetRecord` (SessionAchievements.swift).
 */
data class MovementSetRecord(
    /** Stored lbs (see `WorkoutTask.weightKg`). */
    val weightLbs: Double = 0.0,
    val reps: Int = 0,
    val durationSeconds: Int? = null,
    val distance: DistanceValue? = null
) {
    /** Anything measurable at all (a Next-only bodyweight set has none). */
    val hasMetric: Boolean
        get() = weightLbs > 0 || (durationSeconds ?: 0) > 0 || (distance?.value ?: 0.0) > 0
}
