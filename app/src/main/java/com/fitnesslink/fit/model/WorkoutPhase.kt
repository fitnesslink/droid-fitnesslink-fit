package com.fitnesslink.fit.model

import com.google.gson.annotations.JsonAdapter

@JsonAdapter(WorkoutPhase.Adapter::class)
data class WorkoutPhase(
    val id: String = "",
    val name: String = "",
    val order: Int = 0,
    val isSystem: Boolean = false,
    val taskRows: List<TaskRow> = emptyList()
) {
    /**
     * Every task in playback order — each row's lead followed by its group
     * members — each stamped with this phase's name.
     *
     * The server's task DTO carries `workoutPhaseId` but no phase name, so a
     * task decoded from the API or the cache arrives with an empty
     * `phaseName`. Filling the blank here fixes every consumer at once; a
     * task that already names its phase keeps what it has. Only the name is
     * filled — ids are left as decoded so phase-boundary detection
     * (`phaseKey`) is unchanged.
     */
    val allTasks: List<WorkoutTask>
        get() = taskRows.flatMap { row ->
            (listOfNotNull(row.task) + row.advancedTasks).map(::named)
        }

    private fun named(task: WorkoutTask): WorkoutTask =
        if (task.phaseName.isNotBlank()) task else task.copy(phaseName = name)

    class Adapter : TolerantAdapterFactory<WorkoutPhase>(WorkoutPhase::class.java)
}
