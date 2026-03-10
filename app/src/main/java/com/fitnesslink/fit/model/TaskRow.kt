package com.fitnesslink.fit.model

import java.util.UUID

data class TaskRow(
    val task: WorkoutTask? = null,
    val advanced: String = "",
    val rounds: String = "",
    val totalRounds: Int = 0,
    val isSuperset: Boolean = false,
    val isCircuit: Boolean = false,
    val advancedTasks: List<WorkoutTask> = emptyList()
) {
    val id: String get() = task?.id ?: UUID.randomUUID().toString()
}
