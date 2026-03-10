package com.fitnesslink.fit.model

data class WorkoutTask(
    val id: String = "",
    val name: String = "",
    val metric: String = "",
    val iconUrl: String = "",
    val videoUrl: String = "",
    val nextImageUrl: String = "",
    val isMovement: Boolean = false,
    val isRest: Boolean = false,
    val isAdvanced: Boolean = false,
    val isSuperset: Boolean = false,
    val isCircuit: Boolean = false,
    val isInterval: Boolean = false,
    val reps: Int = 0,
    val sets: Int = 0,
    val restSeconds: Int = 0,
    val rest: String = "",
    val round: String = "",
    val totalRounds: Int = 0,
    val phaseName: String = "",
    val phaseId: String = "",
    val advancedMovement: String = "",
    val currentSet: Int = 0,
    val order: Int = 0,
    val groupId: String = ""
)
