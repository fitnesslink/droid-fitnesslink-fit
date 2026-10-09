package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.persistence.DatabaseManager
import java.util.Locale

/**
 * Contextual exercise suggestions for the Quick Start browser (PRD §17).
 * MVP rule: phase + the user's own history. Movements the user has put in
 * a same-named phase of their own workouts rank first (by how often), then
 * recently performed movements fill the list. Pure ranking in [rank] for
 * tests; [suggest] reads the local cache.
 */
object QuickStartSuggestions {
    const val DEFAULT_LIMIT = 6

    /** Movement ids in suggestion order. [recents] is the recent-movement list newest first. */
    fun rank(phaseName: String, workouts: List<Workout>, recents: List<String>, limit: Int = DEFAULT_LIMIT): List<String> {
        val wanted = normalizedPhaseName(phaseName)
        val counts = LinkedHashMap<String, Int>()
        for (workout in workouts) {
            for (phase in workout.phases) {
                if (normalizedPhaseName(phase.name) != wanted) continue
                for (task in phase.allTasks) {
                    if (!task.isMovement) continue
                    val id = task.movementId?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: continue
                    counts[id] = (counts[id] ?: 0) + 1
                }
            }
        }
        // Stable sort keeps first-seen order among equal counts.
        val ranked = counts.entries.sortedByDescending { it.value }.map { it.key }.toMutableList()
        for (id in recents.map { it.lowercase(Locale.US) }) if (!ranked.contains(id)) ranked.add(id)
        return ranked.take(maxOf(0, limit))
    }

    /** "Warm-Up", "Warmup" and "warm up" are the same phase. */
    fun normalizedPhaseName(name: String): String = name.lowercase(Locale.US).filter { it.isLetterOrDigit() }

    /** Suggestions for [phaseName] from the local cache, as library items. */
    fun suggest(phaseName: String, limit: Int = DEFAULT_LIMIT): List<MovementLibraryItem> {
        val own = DatabaseManager.allWorkouts().filter { it.contributorId == null }
        val workouts = own.mapNotNull { DatabaseManager.workout(it.id) }
        val recents = DatabaseManager.recentMovements(20).map { it.id }
        val ids = rank(phaseName, workouts, recents, limit)
        if (ids.isEmpty()) return emptyList()
        val byId = DatabaseManager.allMovements().associateBy { it.id.lowercase(Locale.US) }
        return ids.mapNotNull { byId[it] }
    }
}
