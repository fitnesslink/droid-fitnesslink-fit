package com.fitnesslink.fit.workouts

import com.fitnesslink.fit.model.ExerciseGroupType
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.IntervalPreset
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.WorkoutPhaseTemplateTask
import java.util.Locale
import java.util.UUID

/**
 * Pure, side-effect-free row/task transformations shared by the editor view
 * model, the AI generator and the save pipeline. Ports of the static /
 * value-type helpers in iOS `WorkoutEditorViewModel.swift`. Everything here
 * takes immutable models in and returns new copies, so the view model can
 * stay a thin state holder and the unit tests can hit the logic directly.
 */
object WorkoutRowOps {

    // MARK: - Formatting

    /** `m:ss` (90 → "1:30", 300 → "5:00"). */
    fun formatRest(seconds: Int): String = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    fun formatDuration(seconds: Int): String = formatRest(seconds)

    /**
     * The task's cached `metric` string: "3 x 10" / "3 x 5:00", with the load
     * suffixed in LBS (the canonical stored unit; `displayMetric` re-renders it
     * in the user's unit).
     */
    fun formatMetric(usesDuration: Boolean, sets: Int, reps: Int, durationSeconds: Int?, weightLbs: Double?): String {
        val valuePart = if (usesDuration) formatDuration(durationSeconds ?: 0) else "$reps"
        val core = "$sets x $valuePart"
        return if (weightLbs != null && weightLbs > 0) "$core @ ${weightLbs.toInt()} lbs" else core
    }

    fun lowercaseUuid(): String = UUID.randomUUID().toString().lowercase(Locale.US)

    // MARK: - Task factories

    /** A fresh 3 × 10 movement task with the movement's media ids copied on. */
    fun makeMovementTask(movement: MovementLibraryItem): WorkoutTask = WorkoutTask(
        id = lowercaseUuid(),
        name = movement.name,
        isMovement = true,
        movementId = movement.id,
        movementImageId = movement.imageId,
        movementThumbnailId = movement.thumbnailId,
        movementVideoId = movement.videoId,
        movementPhoneVideoId = movement.phoneVideoId,
        movementImageUrl = movement.imageUrl,
        movementThumbnailUrl = movement.thumbnailUrl,
        movementVideoUrl = movement.videoUrl,
        movementPhoneVideoUrl = movement.phoneVideoUrl,
        sets = 3,
        reps = 10,
        metric = "3 x 10",
        sameAcrossSets = true,
        setConfigs = (1..3).map { TaskSetConfig(setNumber = it, reps = 10) }
    )

    fun makeRestTask(seconds: Int, restTaskTypeId: String? = null): WorkoutTask = WorkoutTask(
        id = lowercaseUuid(),
        name = "Rest",
        isRest = true,
        restSeconds = seconds,
        rest = formatRest(seconds),
        workoutTaskTypeId = restTaskTypeId
    )

    /** Swap the movement behind a task, keeping its configuration and group membership. */
    fun replaced(task: WorkoutTask, movement: MovementLibraryItem): WorkoutTask = task.copy(
        movementId = movement.id,
        name = movement.name,
        movementImageId = movement.imageId,
        movementThumbnailId = movement.thumbnailId,
        movementVideoId = movement.videoId,
        movementPhoneVideoId = movement.phoneVideoId,
        movementImageUrl = movement.imageUrl,
        movementThumbnailUrl = movement.thumbnailUrl,
        movementVideoUrl = movement.videoUrl,
        movementPhoneVideoUrl = movement.phoneVideoUrl,
        movementVideoStatus = null,
        movementVideoAspectRatio = null,
        movementVideoDurationMs = null,
        movementPhoneVideoStatus = null,
        movementPhoneVideoAspectRatio = null,
        movementPhoneVideoDurationMs = null
    )

    /** Attach a catalog movement as a rest task's active-recovery target. */
    fun withRestMovement(task: WorkoutTask, movement: MovementLibraryItem): WorkoutTask =
        task.withRestType(RestType.ACTIVE_RECOVERY).copy(
            restMovementId = movement.id,
            restMovementName = movement.name,
            restMovementThumbnailId = movement.thumbnailId,
            restMovementThumbnailUrl = movement.thumbnailUrl,
            restMovementVideoId = movement.videoId,
            restMovementVideoUrl = movement.videoUrl,
            restMovementPhoneVideoId = movement.phoneVideoId,
            restMovementPhoneVideoUrl = movement.phoneVideoUrl
        )

    /** Same as [withRestMovement] but sourced from another task in the group. */
    fun withRestMovementFrom(task: WorkoutTask, source: WorkoutTask): WorkoutTask {
        val movementId = source.movementId?.takeIf { it.isNotEmpty() } ?: return task
        return task.withRestType(RestType.ACTIVE_RECOVERY).copy(
            restMovementId = movementId,
            restMovementName = source.name,
            restMovementThumbnailId = source.movementThumbnailId,
            restMovementThumbnailUrl = source.movementThumbnailUrl,
            restMovementVideoId = source.movementVideoId,
            restMovementVideoUrl = source.movementVideoUrl,
            restMovementVideoStatus = source.movementVideoStatus,
            restMovementPhoneVideoId = source.movementPhoneVideoId,
            restMovementPhoneVideoUrl = source.movementPhoneVideoUrl,
            restMovementPhoneVideoStatus = source.movementPhoneVideoStatus
        )
    }

    /** Detach the recovery movement while keeping the rest type on Active Recovery. */
    fun clearedRestMovement(task: WorkoutTask): WorkoutTask = task.copy(
        restMovementId = null, restMovementName = null,
        restMovementThumbnailId = null, restMovementThumbnailUrl = null,
        restMovementVideoId = null, restMovementVideoUrl = null, restMovementVideoStatus = null,
        restMovementPhoneVideoId = null, restMovementPhoneVideoUrl = null, restMovementPhoneVideoStatus = null
    )

    // MARK: - Load / flag normalisation

    /**
     * Any non-rest task is a movement. Saved / server tasks often decode
     * with `isMovement == false`; the grouping and auto-rest paths key off it.
     */
    fun normalizeMovementFlags(workout: Workout): Workout = workout.copy(phases = workout.phases.map { phase ->
        phase.copy(taskRows = phase.taskRows.map { row ->
            row.copy(
                task = row.task?.let { if (!it.isRest && !it.isMovement) it.copy(isMovement = true) else it },
                advancedTasks = row.advancedTasks.map { if (!it.isRest && !it.isMovement) it.copy(isMovement = true) else it }
            )
        })
    })

    // MARK: - Set configuration

    /** Resize `setConfigs` to exactly `count` entries, preserving existing numbers and seeding the rest. */
    fun seedConfigs(task: WorkoutTask, count: Int): WorkoutTask {
        val n = maxOf(count, 1)
        val seedWeight = task.weightKg?.toDouble()
        return task.copy(setConfigs = (1..n).map { i ->
            task.setConfigs.firstOrNull { it.setNumber == i } ?: TaskSetConfig(
                setNumber = i,
                reps = if (task.usesDuration) 0 else task.reps,
                weightKg = seedWeight,
                durationSeconds = if (task.usesDuration) task.durationSeconds else null
            )
        })
    }

    fun updatedSets(task: WorkoutTask, sets: Int, reps: Int, weightLbs: Double?): WorkoutTask {
        var t = task.copy(
            sets = sets, reps = reps, weightKg = weightLbs?.toInt(),
            metric = formatMetric(task.usesDuration, sets, reps, task.durationSeconds, weightLbs)
        )
        t = if (t.sameAcrossSets) {
            t.copy(setConfigs = (1..maxOf(sets, 1)).map {
                TaskSetConfig(setNumber = it, reps = if (t.usesDuration) 0 else reps, weightKg = weightLbs,
                    durationSeconds = if (t.usesDuration) t.durationSeconds else null)
            })
        } else {
            resized(t, sets, reps, weightLbs, t.durationSeconds)
        }
        return t
    }

    fun updatedDuration(task: WorkoutTask, sets: Int, durationSeconds: Int, weightLbs: Double?): WorkoutTask {
        var t = task.copy(
            sets = sets, durationSeconds = durationSeconds, weightKg = weightLbs?.toInt(),
            metric = formatMetric(task.usesDuration, sets, task.reps, durationSeconds, weightLbs)
        )
        t = if (t.sameAcrossSets) {
            t.copy(setConfigs = (1..maxOf(sets, 1)).map {
                TaskSetConfig(setNumber = it, reps = 0, weightKg = weightLbs, durationSeconds = durationSeconds)
            })
        } else {
            resized(t, sets, 0, weightLbs, durationSeconds)
        }
        return t
    }

    private fun resized(task: WorkoutTask, sets: Int, reps: Int, weightLbs: Double?, durationSeconds: Int?): WorkoutTask {
        val configs = task.setConfigs.toMutableList()
        while (configs.size < sets) {
            configs.add(TaskSetConfig(setNumber = configs.size + 1, reps = if (task.usesDuration) 0 else reps, weightKg = weightLbs,
                durationSeconds = if (task.usesDuration) durationSeconds else null))
        }
        while (configs.size > sets && configs.isNotEmpty()) configs.removeAt(configs.lastIndex)
        return task.copy(setConfigs = configs)
    }

    /** Reps ↔ Time toggle; seeds 60 s per set or 10 reps and rewrites `setConfigs`. */
    fun withUsesDuration(task: WorkoutTask, usesDuration: Boolean): WorkoutTask {
        if (task.usesDuration == usesDuration) return task
        val sets = maxOf(task.sets, 1)
        val weight = task.weightKg?.toDouble()
        return if (usesDuration) {
            val seed = task.durationSeconds ?: 60
            task.copy(
                usesDuration = true, durationSeconds = seed,
                metric = formatMetric(true, sets, task.reps, seed, weight),
                setConfigs = (1..sets).map { TaskSetConfig(setNumber = it, reps = 0, weightKg = weight, durationSeconds = seed) }
            )
        } else {
            val seed = if (task.reps > 0) task.reps else 10
            task.copy(
                usesDuration = false, reps = seed,
                metric = formatMetric(false, sets, seed, null, weight),
                setConfigs = (1..sets).map { TaskSetConfig(setNumber = it, reps = seed, weightKg = weight) }
            )
        }
    }

    fun toggledSameAcrossSets(task: WorkoutTask): WorkoutTask {
        val same = !task.sameAcrossSets
        return if (same) {
            val first = task.setConfigs.firstOrNull()
            task.copy(sameAcrossSets = true, setConfigs = task.setConfigs.map {
                it.copy(reps = first?.reps ?: task.reps, weightKg = first?.weightKg, isDropSet = false)
            })
        } else {
            seedConfigs(task.copy(sameAcrossSets = false), maxOf(task.sets, 1))
        }
    }

    /** Per-round loads for a grouped member — one `setConfigs` row per round. */
    fun toggledPerRound(task: WorkoutTask): WorkoutTask {
        val same = !task.sameAcrossSets
        return if (same) {
            val w = task.setConfigs.firstOrNull()?.weightKg
            task.copy(sameAcrossSets = true, weightKg = if (w != null && w > 0) w.toInt() else task.weightKg, setConfigs = emptyList())
        } else {
            val rounds = maxOf(maxOf(task.rounds ?: 0, task.totalRounds), 1)
            seedConfigs(task.copy(sameAcrossSets = false), rounds)
        }
    }

    // MARK: - Grouping

    /** Supersets / circuits need 2+; every interval style is valid with one exercise. */
    fun minimumGroupCount(type: ExerciseGroupType): Int = when (type) {
        ExerciseGroupType.Superset, ExerciseGroupType.Circuit -> 2
        ExerciseGroupType.Interval -> 1
    }

    fun preset(mode: IntervalMode): IntervalPreset = when (mode) {
        IntervalMode.TABATA -> IntervalPreset.tabata
        IntervalMode.HIIT -> IntervalPreset.hiitDefault
        IntervalMode.EMOM -> IntervalPreset.emomDefault
        IntervalMode.AMRAP -> IntervalPreset.amrapDefault
    }

    private fun defaultPreset(type: ExerciseGroupType, mode: IntervalMode?): IntervalPreset? {
        if (type != ExerciseGroupType.Interval) return null
        return preset(mode ?: IntervalMode.HIIT)
    }

    private fun isLooseMovement(row: TaskRow): Boolean {
        val t = row.task ?: return false
        if (t.isRest) return false
        val isGroup = row.advancedTasks.isNotEmpty() || row.isSuperset || row.isCircuit || row.isInterval
        return !isGroup
    }

    /**
     * Wrap the selected loose movements of a phase into one grouped row.
     * The group lands at the first selected row's slot; loose rests that sat
     * between the grouped movements are dropped. Returns the phase's new rows
     * (unchanged when the selection can't form a group).
     */
    fun groupRows(
        rows: List<TaskRow>,
        selectedIds: Set<String>,
        type: ExerciseGroupType,
        mode: IntervalMode?,
        passiveRestTypeId: String
    ): List<TaskRow> {
        val minCount = minimumGroupCount(type)
        if (selectedIds.size < minCount) return rows
        var selectedRows = rows.filter { row ->
            val id = row.task?.id ?: return@filter false
            selectedIds.contains(id) && row.task.isMovement
        }
        if (selectedRows.size < minCount) return rows
        if (type == ExerciseGroupType.Superset && selectedRows.size > 2) selectedRows = selectedRows.take(2)

        val usedIds = selectedRows.mapNotNull { it.task?.id }.toSet()
        val selectedIdxs = rows.indices.filter { i -> rows[i].task?.id?.let { usedIds.contains(it) } == true }
        val insertAt = selectedIdxs.firstOrNull() ?: rows.size
        val removeIdxs = selectedIdxs.toMutableSet()
        val lo = selectedIdxs.firstOrNull(); val hi = selectedIdxs.lastOrNull()
        if (lo != null && hi != null && lo < hi) {
            for (i in (lo + 1) until hi) if (rows[i].task?.isRest == true) removeIdxs.add(i)
        }
        val rebuilt = rows.filterIndexed { i, _ -> !removeIdxs.contains(i) }.toMutableList()

        val newGroupId = lowercaseUuid()
        val preset = defaultPreset(type, mode)
        val defaultRounds = preset?.rounds ?: 3
        val isSuperset = type == ExerciseGroupType.Superset
        val isCircuit = type == ExerciseGroupType.Circuit
        val isInterval = type == ExerciseGroupType.Interval

        var primary = selectedRows[0].task!!.copy(
            rounds = defaultRounds, totalRounds = defaultRounds, groupId = newGroupId,
            isSuperset = isSuperset, isCircuit = isCircuit, isInterval = isInterval
        )
        if (preset != null) {
            primary = primary.copy(
                intervalMode = preset.mode,
                intervalWorkSeconds = preset.workSeconds.takeIf { it > 0 },
                intervalRestSeconds = preset.restSeconds.takeIf { it > 0 },
                intervalWindowSeconds = preset.totalSeconds.takeIf { it > 0 }
            )
        }
        if (isSuperset || isCircuit) primary = primary.copy(roundRestSeconds = 60)
        if (isInterval) {
            primary = primary.copy(
                intervalSets = 1, intervalSetRestSeconds = IntervalMath.defaultSetRestSeconds,
                roundRestSeconds = null, restTypeId = passiveRestTypeId
            )
        }
        val secondary = selectedRows.drop(1).mapNotNull { row ->
            row.task?.copy(
                groupId = newGroupId, isSuperset = isSuperset, isCircuit = isCircuit, isInterval = isInterval,
                rounds = defaultRounds, totalRounds = defaultRounds
            )
        }
        val groupRow = TaskRow(
            task = primary,
            advanced = type.label,
            rounds = "$defaultRounds rounds",
            totalRounds = defaultRounds,
            isSuperset = isSuperset, isCircuit = isCircuit, isInterval = isInterval,
            intervalMode = preset?.mode,
            advancedTasks = secondary
        )
        rebuilt.add(minOf(insertAt, rebuilt.size), groupRow)
        return rebuilt
    }

    /** Re-insert a grouped row's members as standalone rows, clearing the group fields. */
    fun ungroupRows(rows: List<TaskRow>, leadTaskId: String): List<TaskRow> {
        val idx = rows.indexOfFirst { it.task?.id == leadTaskId }
        if (idx < 0) return rows
        val row = rows[idx]
        val out = rows.toMutableList()
        out.removeAt(idx)
        var insert = idx
        row.task?.let { main ->
            out.add(insert, TaskRow(task = main.copy(
                rounds = null, totalRounds = 0, groupId = "", isSuperset = false, isCircuit = false,
                roundRestSeconds = null, intervalSetRestSeconds = null
            )))
            insert += 1
        }
        row.advancedTasks.forEach { adv ->
            out.add(insert, TaskRow(task = adv.copy(groupId = "", isSuperset = false, isCircuit = false)))
            insert += 1
        }
        return out
    }

    /** Whether a circuit has a loose movement right after it (skipping a rest) that could be folded in. */
    fun canAddNextMovement(rows: List<TaskRow>, groupTaskId: String): Boolean {
        val gi = rows.indexOfFirst { it.task?.id == groupTaskId }
        if (gi < 0 || !rows[gi].isCircuit) return false
        var i = gi + 1
        while (i < rows.size) {
            if (rows[i].task?.isRest == true) { i += 1; continue }
            return isLooseMovement(rows[i])
        }
        return false
    }

    /** Fold the loose movement following a circuit (skipping a rest row) into the circuit. */
    fun addNextMovementToGroup(rows: List<TaskRow>, groupTaskId: String): List<TaskRow> {
        val gi = rows.indexOfFirst { it.task?.id == groupTaskId }
        if (gi < 0 || !rows[gi].isCircuit) return rows
        var targetIdx: Int? = null
        var i = gi + 1
        while (i < rows.size) {
            if (rows[i].task?.isRest == true) { i += 1; continue }
            if (isLooseMovement(rows[i])) targetIdx = i
            break
        }
        val mi = targetIdx ?: return rows
        val movedSource = rows[mi].task ?: return rows
        val group = rows[gi]
        val moved = movedSource.copy(
            groupId = group.task?.groupId ?: "", isSuperset = false, isCircuit = true, isInterval = false,
            rounds = group.totalRounds, totalRounds = group.totalRounds
        )
        val removeIdxs = mutableSetOf(mi)
        for (j in (gi + 1) until mi) if (rows[j].task?.isRest == true) removeIdxs.add(j)
        return rows.mapIndexed { idx, row -> if (idx == gi) row.copy(advancedTasks = row.advancedTasks + moved) else row }
            .filterIndexed { idx, _ -> !removeIdxs.contains(idx) }
    }

    /**
     * Move the strictly-lead-owned group fields from a demoted lead to the
     * promoted one. Returns (demoted, promoted).
     */
    fun migrateGroupLeadConfig(old: WorkoutTask, new: WorkoutTask): Pair<WorkoutTask, WorkoutTask> {
        val promoted = new.copy(
            roundRestSeconds = old.roundRestSeconds,
            intervalWorkSeconds = old.intervalWorkSeconds,
            intervalRestSeconds = old.intervalRestSeconds,
            intervalWindowSeconds = old.intervalWindowSeconds,
            intervalSets = old.intervalSets,
            intervalSetRestSeconds = old.intervalSetRestSeconds,
            intervalMode = new.intervalMode ?: old.intervalMode,
            restTypeId = old.restTypeId,
            restMovementId = old.restMovementId,
            restMovementName = old.restMovementName,
            restMovementThumbnailId = old.restMovementThumbnailId,
            restMovementThumbnailUrl = old.restMovementThumbnailUrl,
            restMovementVideoId = old.restMovementVideoId,
            restMovementVideoUrl = old.restMovementVideoUrl,
            restMovementVideoStatus = old.restMovementVideoStatus,
            restMovementPhoneVideoId = old.restMovementPhoneVideoId,
            restMovementPhoneVideoUrl = old.restMovementPhoneVideoUrl,
            restMovementPhoneVideoStatus = old.restMovementPhoneVideoStatus
        )
        val demoted = old.copy(
            roundRestSeconds = null, intervalWorkSeconds = null, intervalRestSeconds = null, intervalWindowSeconds = null,
            intervalSets = null, intervalSetRestSeconds = null, restTypeId = null,
            restMovementId = null, restMovementName = null, restMovementThumbnailId = null, restMovementThumbnailUrl = null,
            restMovementVideoId = null, restMovementVideoUrl = null, restMovementVideoStatus = null,
            restMovementPhoneVideoId = null, restMovementPhoneVideoUrl = null, restMovementPhoneVideoStatus = null
        )
        return demoted to promoted
    }

    /** Reorder a member inside its group; promotes a new lead when position 0 changes hands. */
    fun movedGroupMember(row: TaskRow, taskId: String, up: Boolean): TaskRow {
        val lead = row.task ?: return row
        val members = (listOf(lead) + row.advancedTasks).toMutableList()
        val idx = members.indexOfFirst { it.id == taskId }
        if (idx < 0) return row
        val target = if (up) idx - 1 else idx + 1
        if (target !in members.indices) return row
        val tmp = members[idx]; members[idx] = members[target]; members[target] = tmp
        if (members[0].id != lead.id) {
            val oldLeadIdx = members.indexOfFirst { it.id == lead.id }
            if (oldLeadIdx >= 0) {
                val (demoted, promoted) = migrateGroupLeadConfig(members[oldLeadIdx], members[0])
                members[oldLeadIdx] = demoted
                members[0] = promoted
            }
        }
        return row.copy(task = members[0], advancedTasks = members.drop(1))
    }

    /**
     * Delete one member of a group. Returns the new rows list: the row is
     * dropped when the last member leaves, ungrouped when fewer than the
     * minimum remain, else kept with the lead config migrated as needed.
     */
    fun removedGroupMember(rows: List<TaskRow>, taskId: String): List<TaskRow> {
        val ri = rows.indexOfFirst { row -> row.task?.id == taskId || row.advancedTasks.any { it.id == taskId } }
        if (ri < 0) return rows
        val row = rows[ri]
        val lead = row.task ?: return rows
        val members = (listOf(lead) + row.advancedTasks).toMutableList()
        val idx = members.indexOfFirst { it.id == taskId }
        if (idx < 0) return rows
        val removed = members.removeAt(idx)
        if (members.isEmpty()) return rows.filterIndexed { i, _ -> i != ri }
        if (idx == 0) {
            val (_, promoted) = migrateGroupLeadConfig(removed, members[0])
            members[0] = promoted
        }
        val newRow = row.copy(task = members[0], advancedTasks = members.drop(1))
        val out = rows.toMutableList()
        out[ri] = newRow
        val type = if (row.isInterval) ExerciseGroupType.Interval else if (row.isSuperset) ExerciseGroupType.Superset else ExerciseGroupType.Circuit
        return if (members.size < minimumGroupCount(type)) ungroupRows(out, members[0].id) else out
    }

    /** Rounds change mirrored onto the lead + siblings (and per-round configs; EMOM window). */
    fun withRounds(row: TaskRow, rounds: Int): TaskRow {
        var lead = row.task?.copy(rounds = rounds, totalRounds = rounds)
        if (lead != null && !lead.sameAcrossSets) lead = seedConfigs(lead, rounds)
        if (lead != null && row.intervalMode == IntervalMode.EMOM) lead = lead.copy(intervalWindowSeconds = rounds * 60)
        return row.copy(
            totalRounds = rounds, rounds = "$rounds rounds", task = lead,
            advancedTasks = row.advancedTasks.map {
                val t = it.copy(rounds = rounds, totalRounds = rounds)
                if (!t.sameAcrossSets) seedConfigs(t, rounds) else t
            }
        )
    }

    /** Apply a named interval preset to the row (work / rest / rounds / window). */
    fun withIntervalMode(row: TaskRow, mode: IntervalMode): TaskRow {
        val p = preset(mode)
        return row.copy(
            intervalMode = mode,
            totalRounds = p.rounds,
            rounds = if (p.rounds > 0) "${p.rounds} rounds" else "",
            task = row.task?.copy(
                intervalMode = mode,
                intervalWorkSeconds = p.workSeconds.takeIf { it > 0 },
                intervalRestSeconds = p.restSeconds.takeIf { it > 0 },
                intervalWindowSeconds = p.totalSeconds.takeIf { it > 0 },
                rounds = p.rounds, totalRounds = p.rounds
            )
        )
    }

    /** AMRAP / EMOM window; EMOM also re-derives the round count. */
    fun withIntervalWindow(row: TaskRow, seconds: Int): TaskRow {
        val s = maxOf(60, seconds)
        var out = row.copy(task = row.task?.copy(intervalWindowSeconds = s))
        if (row.intervalMode == IntervalMode.EMOM) {
            val rounds = s / 60
            out = out.copy(totalRounds = rounds, rounds = "$rounds rounds", task = out.task?.copy(rounds = rounds, totalRounds = rounds))
        }
        return out
    }

    // MARK: - Phases

    fun restampPhaseOrders(phases: List<WorkoutPhase>): List<WorkoutPhase> =
        phases.mapIndexed { i, p -> p.copy(order = i + 1) }

    /** "Name (copy)", then "Name (copy 2)", … — first variant not already in the catalog. */
    fun duplicateName(base: String, taken: Set<String>): String {
        val first = "$base (copy)"
        if (!taken.contains(first.lowercase(Locale.US))) return first
        var n = 2
        while (taken.contains("$base (copy $n)".lowercase(Locale.US))) n += 1
        return "$base (copy $n)"
    }

    /**
     * Deep-copy rows for a duplicated / template phase: fresh task ids,
     * re-homed to the new phase, group ids remapped per source group.
     */
    fun copiedTaskRows(rows: List<TaskRow>, phaseId: String, phaseName: String): List<TaskRow> {
        val groupIdMap = HashMap<String, String>()
        fun remapped(old: String): String = groupIdMap.getOrPut(old) { lowercaseUuid() }
        fun copied(task: WorkoutTask): WorkoutTask = task.copy(
            id = lowercaseUuid(), phaseId = phaseId, phaseName = phaseName, workoutPhaseId = phaseId,
            groupId = if (task.groupId.isNotEmpty()) remapped(task.groupId) else ""
        )
        return rows.map { row -> row.copy(task = row.task?.let(::copied), advancedTasks = row.advancedTasks.map(::copied)) }
    }

    /** Re-home a row (lead + members) to a destination phase. */
    fun rehomed(row: TaskRow, phase: WorkoutPhase): TaskRow {
        fun re(t: WorkoutTask) = t.copy(phaseId = phase.id, phaseName = phase.name, workoutPhaseId = phase.id)
        return row.copy(task = row.task?.let(::re), advancedTasks = row.advancedTasks.map(::re))
    }

    /**
     * Rebuild a template's flat task list into grouped rows. Tasks sharing a
     * groupId collapse into one row; `intervalMode` on the lead marks an
     * interval, else 2 members = superset, 3+ = circuit.
     */
    fun taskRowsFromTemplate(tasks: List<WorkoutPhaseTemplateTask>, restTaskTypeId: String): List<TaskRow> {
        val restLc = restTaskTypeId.lowercase(Locale.US)
        val ordered = tasks.sortedBy { it.order }

        fun makeTask(t: WorkoutPhaseTemplateTask): WorkoutTask {
            val isRest = t.workoutTaskTypeId.lowercase(Locale.US) == restLc
            val mode = IntervalMode.fromRaw(t.intervalMode)
            val usesDuration = t.intervalSeconds != null && !isRest
            return WorkoutTask(
                id = t.id.lowercase(Locale.US),
                workoutTaskTypeId = t.workoutTaskTypeId,
                isRest = isRest,
                isMovement = !isRest,
                name = if (isRest) "Rest" else t.movementName,
                order = t.order,
                groupId = t.groupId?.lowercase(Locale.US) ?: "",
                movementId = t.movementId,
                movementThumbnailUrl = t.movementThumbnailUrl,
                advancedMovementId = t.advancedMovementId,
                reps = t.reps ?: 0,
                sets = t.sets ?: 0,
                restSeconds = t.restSeconds ?: 0,
                rest = t.restSeconds?.takeIf { it > 0 }?.let(::formatRest) ?: "",
                rounds = t.rounds,
                totalRounds = t.rounds ?: 0,
                usesDuration = usesDuration,
                durationSeconds = if (usesDuration) t.intervalSeconds else null,
                instructions = t.instructions,
                weightKg = t.weightKg,
                incline = t.incline,
                paceSeconds = t.paceSeconds,
                speed = t.speed,
                caloriesBurned = t.caloriesBurned,
                heartRate = t.heartRate,
                intervalMode = mode,
                intervalWorkSeconds = t.intervalWorkSeconds,
                intervalRestSeconds = t.intervalRestSeconds,
                intervalWindowSeconds = t.intervalWindowSeconds,
                intervalSets = t.intervalSets,
                intervalSetRestSeconds = if (mode != null) (t.intervalSetRestSeconds ?: t.roundRestSeconds) else null,
                roundRestSeconds = if (mode != null) null else t.roundRestSeconds,
                restTypeId = t.restTypeId,
                restMovementId = t.restMovementId,
                sameAcrossSets = t.sameAcrossSets,
                setConfigs = t.setConfigs.map {
                    TaskSetConfig(setNumber = it.setNumber, reps = it.reps, weightKg = it.weightKg?.toDouble(),
                        isDropSet = it.isDropSet, isWarmup = it.isWarmup, durationSeconds = it.durationSeconds)
                }
            )
        }

        val rows = mutableListOf<TaskRow>()
        val consumed = HashSet<String>()
        for (t in ordered) {
            val gid = t.groupId?.lowercase(Locale.US)
            if (gid.isNullOrEmpty()) { rows.add(TaskRow(task = makeTask(t))); continue }
            if (!consumed.add(gid)) continue
            val members = ordered.filter { it.groupId?.lowercase(Locale.US) == gid }.map(::makeTask)
            var lead = members.firstOrNull() ?: continue
            val isInterval = lead.intervalMode != null
            val isSuperset = !isInterval && members.size == 2
            val isCircuit = !isInterval && members.size > 2
            if (!isInterval && members.size == 1) {
                rows.add(TaskRow(task = lead.copy(groupId = "")))
                continue
            }
            val totalRounds = lead.rounds ?: 0
            lead = lead.copy(isSuperset = isSuperset, isCircuit = isCircuit, isInterval = isInterval)
            val siblings = members.drop(1).map {
                it.copy(isSuperset = isSuperset, isCircuit = isCircuit, isInterval = isInterval, rounds = lead.rounds, totalRounds = totalRounds)
            }
            rows.add(TaskRow(
                task = lead,
                advanced = when { isSuperset -> ExerciseGroupType.Superset.label; isCircuit -> ExerciseGroupType.Circuit.label; else -> ExerciseGroupType.Interval.label },
                rounds = if (totalRounds > 0) "$totalRounds rounds" else "",
                totalRounds = totalRounds,
                isSuperset = isSuperset, isCircuit = isCircuit, isInterval = isInterval,
                intervalMode = lead.intervalMode,
                advancedTasks = siblings
            ))
        }
        return rows
    }

    // MARK: - Server merge

    /**
     * If the server echo has no tasks while the local snapshot does, keep the
     * local copy (adopting the server id). Otherwise take the server as the
     * baseline and restore the device-side / rollout-window fields from the
     * local tasks; optionally keep the local phase arrangement.
     */
    fun mergePreservingPhases(local: Workout, server: Workout, preservingLocalPhaseOrder: Boolean = true): Workout {
        val localTaskCount = local.phases.sumOf { it.taskRows.size }
        val serverTaskCount = server.phases.sumOf { it.taskRows.size }
        if (serverTaskCount == 0 && localTaskCount > 0) return local.copy(id = server.id)

        val localTasksById = HashMap<String, WorkoutTask>()
        val localRowsByLeadId = HashMap<String, TaskRow>()
        for (phase in local.phases) for (row in phase.taskRows) {
            row.task?.let { localTasksById[it.id] = it; if (it.id.isNotEmpty()) localRowsByLeadId[it.id] = row }
            row.advancedTasks.forEach { localTasksById[it.id] = it }
        }

        fun restoreLead(task: WorkoutTask, localTask: WorkoutTask): WorkoutTask {
            var t = task.copy(usesDuration = localTask.usesDuration, durationSeconds = localTask.durationSeconds)
            if (localTask.isInterval) t = t.copy(isInterval = true)
            t = t.copy(
                intervalMode = t.intervalMode ?: localTask.intervalMode,
                intervalSets = t.intervalSets ?: localTask.intervalSets,
                intervalWorkSeconds = t.intervalWorkSeconds ?: localTask.intervalWorkSeconds,
                intervalRestSeconds = t.intervalRestSeconds ?: localTask.intervalRestSeconds,
                intervalWindowSeconds = t.intervalWindowSeconds ?: localTask.intervalWindowSeconds,
                intervalSetRestSeconds = t.intervalSetRestSeconds ?: localTask.intervalSetRestSeconds,
                roundRestSeconds = t.roundRestSeconds ?: localTask.roundRestSeconds,
                restTypeId = t.restTypeId ?: localTask.restTypeId,
                restMovementId = t.restMovementId ?: localTask.restMovementId,
                restMovementName = t.restMovementName ?: localTask.restMovementName,
                restMovementThumbnailId = t.restMovementThumbnailId ?: localTask.restMovementThumbnailId,
                restMovementThumbnailUrl = t.restMovementThumbnailUrl ?: localTask.restMovementThumbnailUrl,
                restMovementVideoId = t.restMovementVideoId ?: localTask.restMovementVideoId,
                restMovementVideoUrl = t.restMovementVideoUrl ?: localTask.restMovementVideoUrl,
                restMovementVideoStatus = t.restMovementVideoStatus ?: localTask.restMovementVideoStatus,
                restMovementPhoneVideoId = t.restMovementPhoneVideoId ?: localTask.restMovementPhoneVideoId,
                restMovementPhoneVideoUrl = t.restMovementPhoneVideoUrl ?: localTask.restMovementPhoneVideoUrl,
                restMovementPhoneVideoStatus = t.restMovementPhoneVideoStatus ?: localTask.restMovementPhoneVideoStatus,
                weightKg = t.weightKg ?: localTask.weightKg
            )
            if (!localTask.sameAcrossSets && t.setConfigs.isEmpty()) t = t.copy(sameAcrossSets = false, setConfigs = localTask.setConfigs)
            return t
        }

        fun restoreMember(task: WorkoutTask, localTask: WorkoutTask): WorkoutTask {
            var t = task.copy(usesDuration = localTask.usesDuration, durationSeconds = localTask.durationSeconds,
                weightKg = task.weightKg ?: localTask.weightKg)
            if (!localTask.sameAcrossSets && t.setConfigs.isEmpty()) t = t.copy(sameAcrossSets = false, setConfigs = localTask.setConfigs)
            if (localTask.isInterval) t = t.copy(isInterval = true)
            t = t.copy(intervalMode = t.intervalMode ?: localTask.intervalMode)
            return t
        }

        var mergedPhases = server.phases.map { phase ->
            phase.copy(taskRows = phase.taskRows.map { row ->
                var r = row
                val leadId = row.task?.id
                val localRow = leadId?.let { localRowsByLeadId[it] }
                if (localRow != null) {
                    if (localRow.isInterval) r = r.copy(isInterval = true)
                    if (r.intervalMode == null && localRow.intervalMode != null) r = r.copy(intervalMode = localRow.intervalMode)
                }
                val lead = r.task
                if (lead != null) localTasksById[lead.id]?.let { r = r.copy(task = restoreLead(lead, it)) }
                r = r.copy(advancedTasks = r.advancedTasks.map { adv -> localTasksById[adv.id]?.let { restoreMember(adv, it) } ?: adv })
                r
            })
        }
        if (preservingLocalPhaseOrder) {
            val localIndex = local.phases.mapIndexed { i, p -> p.id.lowercase(Locale.US) to i }.toMap()
            mergedPhases = mergedPhases.withIndex().sortedWith { a, b ->
                val x = localIndex[a.value.id.lowercase(Locale.US)]
                val y = localIndex[b.value.id.lowercase(Locale.US)]
                when {
                    x != null && y != null -> x.compareTo(y)
                    x != null -> -1
                    y != null -> 1
                    else -> a.index.compareTo(b.index)
                }
            }.map { it.value }
        }
        return server.copy(phases = restampPhaseOrders(mergedPhases))
    }

    /** A workout the current user can edit directly (not a template, not shared in). */
    fun isEditable(workout: Workout): Boolean = workout.contributorId == null && workout.sharedByUserId == null
}
