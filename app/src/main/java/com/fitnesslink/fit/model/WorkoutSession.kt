package com.fitnesslink.fit.model

import com.fitnesslink.fit.model.api.EntityID
import java.util.Date

data class WorkoutSession(
    val id: String,
    val workoutId: String,
    val name: String,
    val description: String,
    val estimatedTime: String,
    val trainingLevel: String,
    val workoutTasks: List<WorkoutTask> = emptyList(),

    // API-aligned fields
    val userId: EntityID? = null,
    val programId: EntityID? = null,
    val rpeId: EntityID? = null,
    val currentIndex: Int? = null,
    val startDate: Date? = null,
    val completionDate: Date? = null,
    val nextMovementId: EntityID? = null,
    val workoutPhaseId: EntityID? = null
)
