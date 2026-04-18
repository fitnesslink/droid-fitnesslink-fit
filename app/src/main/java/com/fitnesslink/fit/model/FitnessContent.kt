package com.fitnesslink.fit.model

data class FitnessContent(
    val id: String,
    val title: String,
    val programId: String,
    val workoutId: String,
    val mealPlanId: String,
    val status: String,
    val scheduledDate: Long? = null
)
