package com.fitnesslink.fit.model.api

data class ProgramSchedule(
    val id: EntityID,
    val programId: EntityID,
    val workoutId: EntityID,
    val weekNumber: Int,
    /** 1 = Sunday … 7 = Saturday (backend seeder convention). */
    val dayNumber: Int,
    /** Pre-resolved SAS URLs for the scheduled workout's media (`/programs/{id}/weekly-workouts`). */
    val workoutImageUrl: String? = null,
    val workoutThumbnailUrl: String? = null,
    val audit: AuditFields? = null
)
