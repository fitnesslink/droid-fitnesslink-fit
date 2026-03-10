package com.fitnesslink.fit.model

data class WorkoutList(
    val id: String,
    val imageUrl: String,
    val name: String,
    val time: String,
    val isFavorite: Boolean
)
