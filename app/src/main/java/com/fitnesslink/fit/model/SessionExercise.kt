package com.fitnesslink.fit.model

data class SessionExercise(
    val id: String,
    val name: String,
    val iconUrl: String,
    val metrics: String,
    val completed: Boolean
)
