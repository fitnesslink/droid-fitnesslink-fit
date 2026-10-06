package com.fitnesslink.fit.model

import java.util.Locale

/**
 * Session-only edits to a workout the user doesn't own (a coach's, program,
 * or shared-in workout). The interactive session applies them on top of the
 * flat task list every time it (re)loads, they persist with the in-progress
 * session row (`workout_sessions.sessionOverrides`) so a relaunch keeps
 * them, and they are dropped when the session ends. "Save as my workout"
 * bakes them into a personal copy. Exact port of iOS `SessionOverrides`.
 */
data class SessionMovementOverride(
    val movementId: String = "",
    val name: String = "",
    val imageId: String? = null,
    val thumbnailId: String? = null,
    val videoId: String? = null,
    val phoneVideoId: String? = null,
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    val videoUrl: String? = null,
    val phoneVideoUrl: String? = null
) {
    constructor(movement: MovementLibraryItem) : this(
        movementId = movement.id,
        name = movement.name,
        imageId = movement.imageId,
        thumbnailId = movement.thumbnailId,
        videoId = movement.videoId,
        phoneVideoId = movement.phoneVideoId,
        imageUrl = movement.imageUrl,
        thumbnailUrl = movement.thumbnailUrl,
        videoUrl = movement.videoUrl,
        phoneVideoUrl = movement.phoneVideoUrl
    )

    /** Swap the movement identity + media, clear cached video readiness so it re-resolves. */
    fun apply(task: WorkoutTask): WorkoutTask = task.copy(
        movementId = movementId,
        name = name,
        movementImageId = imageId,
        movementThumbnailId = thumbnailId,
        movementVideoId = videoId,
        movementPhoneVideoId = phoneVideoId,
        movementImageUrl = imageUrl,
        movementThumbnailUrl = thumbnailUrl,
        movementVideoUrl = videoUrl,
        movementPhoneVideoUrl = phoneVideoUrl,
        movementVideoStatus = null,
        movementVideoAspectRatio = null,
        movementVideoDurationMs = null,
        movementPhoneVideoStatus = null,
        movementPhoneVideoAspectRatio = null,
        movementPhoneVideoDurationMs = null
    )
}

data class SessionSetOverride(
    val sets: Int = 1,
    val reps: Int = 0
) {
    fun apply(task: WorkoutTask): WorkoutTask = SessionOverrides.applySets(task, sets, reps)
}

data class SessionAddedTask(
    val task: WorkoutTask = WorkoutTask(),
    /** Id of the task this one follows. Null appends at the end of the workout — or of [phaseId]'s phase. */
    val afterTaskId: String? = null,
    /** Phase to append to when there is no anchor. Older persisted overrides omit it. */
    val phaseId: String? = null
)

data class SessionOverrides(
    val swaps: Map<String, SessionMovementOverride> = emptyMap(),
    val removed: Set<String> = emptySet(),
    val setChanges: Map<String, SessionSetOverride> = emptyMap(),
    val added: List<SessionAddedTask> = emptyList()
) {
    val isEmpty: Boolean
        get() = swaps.isEmpty() && removed.isEmpty() && setChanges.isEmpty() && added.isEmpty()

    /** Flat-list form used by the interactive session. */
    fun apply(tasks: List<WorkoutTask>): List<WorkoutTask> = apply(listOf(tasks)).flatten()

    /**
     * Per-phase form used when baking the edits into a personal copy: each
     * inner list is one phase's tasks in playback order. Added tasks land in
     * the phase that holds their anchor; an unanchored add with a `phaseId`
     * lands at the end of that phase — matched by position through
     * [phaseIds] when the caller has them, else by the phase stamp on the
     * tasks already in the list — and everything else goes to the last phase.
     */
    fun apply(phases: List<List<WorkoutTask>>, phaseIds: List<String>? = null): List<List<WorkoutTask>> {
        var out: MutableList<MutableList<WorkoutTask>> = phases.map { phase ->
            phase.mapNotNull { task ->
                if (removed.contains(task.id)) return@mapNotNull null
                var t = task
                swaps[t.id]?.let { t = it.apply(t) }
                setChanges[t.id]?.let { t = it.apply(t) }
                t
            }.toMutableList()
        }.toMutableList()
        if (out.isEmpty()) out = mutableListOf(mutableListOf())

        // Tasks already chained behind each anchor, so a second "add after X"
        // lands after the first one rather than directly after X.
        val chainedAfter = mutableMapOf<String, Int>()
        for (entry in added) {
            if (removed.contains(entry.task.id)) continue
            var t = entry.task
            swaps[t.id]?.let { t = it.apply(t) }
            setChanges[t.id]?.let { t = it.apply(t) }
            val anchor = entry.afterTaskId
            val anchorPhase = anchor?.let { a -> out.indexOfFirst { list -> list.any { it.id == a } }.takeIf { it >= 0 } }
            val target = entry.phaseId?.lowercase(Locale.US)
            if (anchor != null && anchorPhase != null) {
                val ti = out[anchorPhase].indexOfFirst { it.id == anchor }
                val at = ti + 1 + (chainedAfter[anchor] ?: 0)
                out[anchorPhase].add(minOf(at, out[anchorPhase].size), t)
                chainedAfter[anchor] = (chainedAfter[anchor] ?: 0) + 1
            } else if (target != null && phaseIds != null &&
                phaseIds.indexOfFirst { it.lowercase(Locale.US) == target }.let { it >= 0 && it < out.size }
            ) {
                val pi = phaseIds.indexOfFirst { it.lowercase(Locale.US) == target }
                out[pi].add(t)
            } else if (target != null && lastSlot(target, out) != null) {
                val (pi, index) = lastSlot(target, out)!!
                out[pi].add(index + 1, t)
            } else {
                out[out.size - 1].add(t)
            }
        }
        return out
    }

    companion object {
        /** The phase a task is stamped with: the server's `workoutPhaseId`, else the editor's `phaseId`. Lowercased. */
        fun phaseKey(task: WorkoutTask): String? {
            val raw = task.workoutPhaseId ?: task.phaseId.takeIf { it.isNotEmpty() }
            return raw?.lowercase(Locale.US)
        }

        /** Position of the last task stamped with [phase] (lowercased), scanning every list. */
        private fun lastSlot(phase: String, lists: List<List<WorkoutTask>>): Pair<Int, Int>? {
            for (pi in lists.indices.reversed()) {
                val ti = lists[pi].indexOfLast { phaseKey(it) == phase }
                if (ti >= 0) return pi to ti
            }
            return null
        }

        /** Mirrors the editor's `updateTaskSets` for rep-based work. */
        fun applySets(task: WorkoutTask, sets: Int, reps: Int): WorkoutTask {
            val s = maxOf(sets, 1)
            val r = maxOf(reps, 0)
            val weight = task.weightKg?.toDouble()
            val configs: List<TaskSetConfig> = if (task.sameAcrossSets || task.setConfigs.isEmpty()) {
                (1..s).map { TaskSetConfig(setNumber = it, reps = r, weightKg = weight) }
            } else {
                val list = task.setConfigs.toMutableList()
                while (list.size < s) list.add(TaskSetConfig(setNumber = list.size + 1, reps = r, weightKg = weight))
                while (list.size > s) list.removeAt(list.size - 1)
                list
            }
            return task.copy(sets = s, reps = r, metric = "$s x $r", setConfigs = configs)
        }

        /**
         * Group a flat, phase-local task list back into editor rows:
         * consecutive tasks sharing a `groupId` become one row led by the
         * first member.
         */
        fun rows(tasks: List<WorkoutTask>): List<TaskRow> {
            val rows = mutableListOf<TaskRow>()
            var i = 0
            while (i < tasks.size) {
                val lead = tasks[i]
                if (lead.groupId.isEmpty()) {
                    rows.add(TaskRow(task = lead))
                    i += 1
                    continue
                }
                var j = i + 1
                while (j < tasks.size && tasks[j].groupId == lead.groupId) j += 1
                val totalRounds = maxOf(lead.rounds ?: lead.totalRounds, 0)
                rows.add(
                    TaskRow(
                        task = lead,
                        advancedTasks = tasks.subList(i + 1, j).toList(),
                        isSuperset = lead.isSuperset,
                        isCircuit = lead.isCircuit,
                        isInterval = lead.isInterval,
                        intervalMode = lead.intervalMode,
                        totalRounds = totalRounds,
                        rounds = if (totalRounds > 0) "$totalRounds rounds" else ""
                    )
                )
                i = j
            }
            return rows
        }
    }
}
