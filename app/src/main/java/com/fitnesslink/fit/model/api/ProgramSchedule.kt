package com.fitnesslink.fit.model.api

data class ProgramSchedule(
    val id: EntityID,
    val programId: EntityID,
    val workoutId: EntityID,
    val weekNumber: Int,
    val dayNumber: Int,
    val audit: AuditFields = AuditFields()
)
