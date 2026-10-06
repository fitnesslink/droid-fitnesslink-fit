package com.fitnesslink.fit.model

import com.google.gson.annotations.JsonAdapter
import java.util.UUID

/**
 * One editor / detail row: a lead task plus the secondary members of a
 * grouped block (superset / circuit / interval). The server-side row `id`
 * is intentionally not stored — rows key off the lead task's id.
 */
@JsonAdapter(TaskRow.Adapter::class)
data class TaskRow(
    val task: WorkoutTask? = null,
    val advanced: String = "",
    val rounds: String = "",
    val totalRounds: Int = 0,
    val isSuperset: Boolean = false,
    val isCircuit: Boolean = false,
    val isInterval: Boolean = false,
    /** Row-level interval protocol; survives sync/merge even when the task's doesn't. */
    val intervalMode: IntervalMode? = null,
    val advancedTasks: List<WorkoutTask> = emptyList()
) {
    val id: String get() = task?.id ?: UUID.randomUUID().toString()

    class Adapter : TolerantAdapterFactory<TaskRow>(TaskRow::class.java)
}
