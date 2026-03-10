package com.fitnesslink.fit.model

data class WorkoutSession(
    val id: String,
    val workoutId: String,
    val name: String,
    val description: String,
    val estimatedTime: String,
    val trainingLevel: String,
    val workoutTasks: List<WorkoutTask> = emptyList()
)
