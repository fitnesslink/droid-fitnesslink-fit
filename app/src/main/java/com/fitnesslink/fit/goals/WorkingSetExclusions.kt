package com.fitnesslink.fit.goals

import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.SessionTransitionEngine
import java.util.Locale

/**
 * Port of iOS `WorkingSetVolume.Exclusions` (Services/Session/WorkoutSummary.swift)
 * for strength goals: persisted history rows carry only a task id / name
 * and a set number, so the cached workout's tasks say which sets are
 * warm-ups or sit in the Warm-Up / Cool Down phases.
 */
class WorkingSetExclusions(tasks: List<WorkoutTask>) {
    private val excludedTasks = HashSet<String>()
    private val warmupSets = HashSet<String>()
    /** name → (every task of that name sits in an excluded phase, warm-up set numbers shared by all of them). */
    private val byName = HashMap<String, Pair<Boolean, Set<Int>>>()

    init {
        for (task in tasks) {
            if (!task.isMovement) continue
            val id = task.id.lowercase(Locale.US)
            val excludedPhase = isExcludedPhase(task.phaseName)
            if (excludedPhase) excludedTasks.add(id)
            val warmups = task.setConfigs.filter { it.isWarmup }.map { it.setNumber }.toSet()
            warmups.forEach { warmupSets.add("$id|$it") }
            val key = task.name.lowercase(Locale.US)
            val prior = byName[key]
            byName[key] = if (prior != null) (prior.first && excludedPhase) to prior.second.intersect(warmups) else excludedPhase to warmups
        }
    }

    fun excludes(taskId: String, setNumber: Int): Boolean {
        val id = taskId.lowercase(Locale.US)
        return id in excludedTasks || "$id|$setNumber" in warmupSets
    }

    /** Name-based fallback for rows that carry no task id: excluded only if every occurrence would be. */
    fun excludesByName(taskName: String, setNumber: Int): Boolean {
        val entry = byName[taskName.lowercase(Locale.US)] ?: return false
        return entry.first || setNumber in entry.second
    }

    companion object {
        val NONE = WorkingSetExclusions(emptyList())

        /** True for the Warm-Up / Cool Down phases, whatever the spelling. */
        fun isExcludedPhase(phaseName: String): Boolean =
            when (SessionTransitionEngine.phaseDisplayName(phaseName)) {
                "Warm-Up", "Cool Down" -> true
                else -> false
            }
    }
}
