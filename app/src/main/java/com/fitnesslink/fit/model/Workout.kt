package com.fitnesslink.fit.model

data class Workout(
    val id: String = "",
    val imageUrl: String = "",
    val name: String = "",
    val time: String = "",
    val location: String = "",
    val trainingLevel: String = "",
    val description: String = "",
    val phases: List<WorkoutPhase> = emptyList()
)
