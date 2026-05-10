package com.fitnesslink.fit.model

data class WorkoutList(
    val id: String,
    val name: String,
    val time: String,
    val isFavorite: Boolean,
    val trainingLevel: String = ""
)
