package com.fitnesslink.fit.model.api

import java.util.Date

data class WorkoutSessionHistory(
    val id: EntityID,
    val workoutSessionId: EntityID,
    val workoutTaskId: EntityID,
    val workoutId: EntityID,
    val programId: EntityID? = null,
    val userId: EntityID,
    val logDate: Date,
    val reps: Int? = null,
    val set: Int? = null,
    val intervalSeconds: Int? = null,
    val weightLifted: Double? = null,
    val audit: AuditFields = AuditFields()
)
