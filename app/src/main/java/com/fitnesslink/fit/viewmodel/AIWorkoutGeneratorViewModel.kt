package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.ExerciseGroupType
import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.IntervalPreset
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.CreateMovementRequest
import com.fitnesslink.fit.network.dto.DraftWorkout
import com.fitnesslink.fit.network.dto.DraftWorkoutTask
import com.fitnesslink.fit.network.dto.GenerateWorkoutContext
import com.fitnesslink.fit.network.dto.GenerateWorkoutRequest
import com.fitnesslink.fit.network.dto.GenerateWorkoutResponse
import com.fitnesslink.fit.network.dto.MovementResolution
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.workouts.WorkoutPhaseStore
import com.fitnesslink.fit.workouts.WorkoutRowOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Drives the "Generate with AI" flow (iOS `AIWorkoutGeneratorViewModel`):
 * collects the prompt + optional context, calls the long-running
 * generator, and on approval resolves movements (auto-creating personal
 * ones for unmatched suggestions) into an editable `Workout` the editor
 * merges unsaved.
 */
class AIWorkoutGeneratorViewModel : ViewModel() {
    var prompt by mutableStateOf("")
    var durationMinutes by mutableStateOf<Int?>(null)
    var level by mutableStateOf<String?>(null)
    var equipment by mutableStateOf<List<String>>(emptyList())

    var isGenerating by mutableStateOf(false)
        private set
    var isApplying by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<GenerateWorkoutResponse?>(null)
        private set

    val draft: DraftWorkout? get() = result?.draftWorkout

    val resolutionsByIndex: Map<Int, MovementResolution>
        get() = (result?.movementResolutions ?: emptyList()).associateBy { it.taskIndex }

    fun discard() { result = null }

    // MARK: - Generate

    suspend fun generate() {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return
        if (!NetworkMonitor.isConnected.value) {
            errorMessage = "You need an active internet connection to generate a workout."
            return
        }
        isGenerating = true
        errorMessage = null
        result = null
        try {
            val response = withContext(Dispatchers.IO) {
                ApiClient.aiWorkoutApi.generate(GenerateWorkoutRequest(prompt = trimmed, context = buildContext()))
            }
            if (response.success && response.draftWorkout != null) result = response
            else errorMessage = friendlyMessage(response.errorReasonCode, response.errorDetail)
        } catch (e: ApiError.ServerError) {
            // 400 safety reject / 502 / 504 carry the same envelope in the error body.
            val envelope = e.decodeBody(ApiClient.gson, GenerateWorkoutResponse::class.java)
            errorMessage = if (envelope != null) friendlyMessage(envelope.errorReasonCode ?: e.errorReasonCode, envelope.errorDetail ?: e.errorDetail)
            else if (e.isGatewayTimeout) friendlyMessage("timeout", null)
            else if (e.isBadGateway) friendlyMessage("gemini_unavailable", null)
            else "Something went wrong generating your workout. Please try again."
        } catch (e: ApiError.Offline) {
            errorMessage = "You need an active internet connection to generate a workout."
        } catch (e: ApiError.Timeout) {
            errorMessage = friendlyMessage("timeout", null)
        } catch (e: Exception) {
            errorMessage = "Something went wrong generating your workout. Please try again."
        } finally {
            isGenerating = false
        }
    }

    private fun buildContext(): GenerateWorkoutContext? {
        val eq = equipment.ifEmpty { null }
        val lvl = level?.takeIf { it.isNotEmpty() }
        if (eq == null && durationMinutes == null && lvl == null) return null
        return GenerateWorkoutContext(equipment = eq, durationMinutes = durationMinutes, level = lvl)
    }

    // MARK: - Approval → editable Workout

    suspend fun buildWorkout(): Workout? {
        val draft = result?.draftWorkout ?: return null
        isApplying = true
        try {
            val resByIndex = resolutionsByIndex
            val systemPhases = WorkoutPhaseStore.systemPhases
            val requested = durationMinutes
            val minutes = if (requested != null && requested > 0) requested else draft.durationMinutes

            val phaseOrder = mutableListOf<String>()
            val tasksByPhase = LinkedHashMap<String, MutableList<DraftWorkoutTask>>()
            for (t in draft.tasks) {
                val key = t.phase.ifEmpty { "Exercises" }
                if (!tasksByPhase.containsKey(key)) { phaseOrder.add(key); tasksByPhase[key] = mutableListOf() }
                tasksByPhase[key]!!.add(t)
            }

            val phases = mutableListOf<WorkoutPhase>()
            phaseOrder.forEachIndexed { phaseIdx, phaseName ->
                val canonical = systemPhases.firstOrNull { it.name.equals(phaseName, true) }
                val phaseId = canonical?.id?.lowercase(Locale.US) ?: WorkoutRowOps.lowercaseUuid()
                val displayName = canonical?.name ?: phaseName
                val built = mutableListOf<Pair<DraftWorkoutTask, WorkoutTask>>()
                (tasksByPhase[phaseName] ?: emptyList()).forEachIndexed { i, draftTask ->
                    if (isRestTask(draftTask)) built.add(draftTask to makeRestTask(draftTask, phaseId, displayName, i))
                    else {
                        val resolved = resolveMovement(draftTask, resByIndex[draftTask.taskIndex])
                        built.add(draftTask to makeTask(draftTask, resolved, phaseId, displayName, i))
                    }
                }
                phases.add(WorkoutPhase(
                    id = phaseId, name = displayName,
                    order = canonical?.sortOrder ?: (1000 + phaseIdx),
                    isSystem = canonical?.isSystem ?: false,
                    taskRows = rows(built)
                ))
            }

            return Workout(
                id = WorkoutRowOps.lowercaseUuid(),
                name = draft.name,
                description = draft.description ?: "",
                trainingLevel = draft.level ?: "",
                trainingLevels = listOfNotNull(draft.level?.takeIf { it.isNotEmpty() }),
                estimatedTime = minutes * 60,
                time = Workout.formatEstimatedTime(minutes * 60),
                contributorId = null,
                phases = phases.sortedBy { it.order }
            )
        } finally {
            isApplying = false
        }
    }

    // MARK: - Movement resolution

    private data class ResolvedMovement(val id: String?, val name: String, val imageId: String?, val thumbnailId: String?, val videoId: String?)

    private suspend fun resolveMovement(draftTask: DraftWorkoutTask, resolution: MovementResolution?): ResolvedMovement {
        if (resolution != null) {
            when (resolution.status.lowercase(Locale.US)) {
                "matched" -> resolution.existingMovementId?.let { return hydratedMovement(it, resolution.suggestedMovementName) }
                "ambiguous" -> resolution.candidates.maxByOrNull { it.score }?.let { return hydratedMovement(it.movementId, it.name) }
            }
        }
        val newId = createPersonalMovement(draftTask.movementName, draftTask.movementDescription)
        return ResolvedMovement(newId, draftTask.movementName, null, null, null)
    }

    private suspend fun hydratedMovement(id: String, fallbackName: String): ResolvedMovement {
        DatabaseManager.movement(id)?.let { return ResolvedMovement(id, it.name, it.imageId, it.thumbnailId, it.videoId) }
        val fetched = withContext(Dispatchers.IO) { runCatching { ApiClient.movementApi.get(id) }.getOrNull() }
        if (fetched != null) {
            return ResolvedMovement(id, fetched.name, fetched.imageId?.toString(), fetched.thumbnailId?.toString(), fetched.videoId?.toString())
        }
        return ResolvedMovement(id, fallbackName, null, null, null)
    }

    /** Create a personal movement (server first so the saved workout's movementId validates; local id on failure). */
    private suspend fun createPersonalMovement(name: String, description: String?): String {
        val serverId = withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.movementApi.create(CreateMovementRequest(
                    name = name, description = description, contributorId = null,
                    statusId = ClassificationStore.contentStatusId("Active")
                ))
            }.getOrNull()
        }?.trim('"')?.lowercase(Locale.US)
        val id = serverId ?: WorkoutRowOps.lowercaseUuid()
        runCatching { DatabaseManager.insertMovementFull(id = id, name = name, description = description ?: "", contributorId = null) }
        return id
    }

    // MARK: - Task construction

    private fun makeRestTask(draftTask: DraftWorkoutTask, phaseId: String, phaseName: String, order: Int): WorkoutTask {
        val seconds = draftTask.restSeconds?.takeIf { it > 0 } ?: 60
        return WorkoutTask(
            id = WorkoutRowOps.lowercaseUuid(), isRest = true, isMovement = false, name = "Rest",
            restSeconds = seconds, rest = WorkoutRowOps.formatRest(seconds), instructions = draftTask.notes,
            workoutTaskTypeId = ClassificationStore.workoutTaskTypeId("Rest"),
            phaseId = phaseId, phaseName = phaseName, workoutPhaseId = phaseId, order = order
        )
    }

    private fun makeTask(draftTask: DraftWorkoutTask, resolved: ResolvedMovement, phaseId: String, phaseName: String, order: Int): WorkoutTask {
        val base = WorkoutTask(
            id = WorkoutRowOps.lowercaseUuid(), isMovement = true, movementId = resolved.id, name = resolved.name,
            movementImageId = resolved.imageId, movementThumbnailId = resolved.thumbnailId, movementVideoId = resolved.videoId,
            instructions = draftTask.notes,
            workoutTaskTypeId = ClassificationStore.workoutTaskTypeId(draftTask.taskType.ifEmpty { "Movement" }),
            phaseId = phaseId, phaseName = phaseName, workoutPhaseId = phaseId, order = order
        )
        return applyPrescription(draftTask, base)
    }

    // MARK: - Blocks (FTNSSLNK-388)

    enum class DraftBlockKind { SUPERSET, CIRCUIT, INTERVAL }

    /** One contiguous run of draft tasks sharing a `groupKey` inside a phase. */
    data class DraftBlockRun(val key: String, val kind: DraftBlockKind, val taskIndexes: List<Int>, val lead: DraftWorkoutTask) {
        val groupType: ExerciseGroupType
            get() = when (kind) { DraftBlockKind.SUPERSET -> ExerciseGroupType.Superset; DraftBlockKind.CIRCUIT -> ExerciseGroupType.Circuit; DraftBlockKind.INTERVAL -> ExerciseGroupType.Interval }

        /** Resolved protocol for an interval run (mode inferred from timing when the draft omits it). */
        val intervalMode: IntervalMode?
            get() {
                if (kind != DraftBlockKind.INTERVAL) return null
                IntervalMode.fromRaw(lead.intervalMode)?.let { return it }
                if (lead.intervalWindowSeconds != null && lead.intervalWorkSeconds == null) return IntervalMode.AMRAP
                if (lead.intervalWorkSeconds == 20 && lead.intervalRestSeconds == 10) return IntervalMode.TABATA
                return IntervalMode.HIIT
            }

        /** Chip text, e.g. "Superset A · 3 rounds · 60s rest", "Tabata B · 20s/10s × 8", "AMRAP C · 10 min". */
        val title: String
            get() = when (kind) {
                DraftBlockKind.SUPERSET, DraftBlockKind.CIRCUIT -> {
                    "${if (kind == DraftBlockKind.SUPERSET) "Superset" else "Circuit"} $key · ${lead.rounds ?: 3} rounds · ${lead.roundRestSeconds ?: 60}s rest"
                }
                DraftBlockKind.INTERVAL -> {
                    val mode = intervalMode ?: IntervalMode.HIIT
                    val preset = WorkoutRowOps.preset(mode)
                    when (mode) {
                        IntervalMode.AMRAP -> "${mode.displayName} $key · ${(lead.intervalWindowSeconds ?: preset.totalSeconds) / 60} min"
                        IntervalMode.EMOM -> "${mode.displayName} $key · ${lead.intervalWorkSeconds ?: preset.workSeconds}s work · ${lead.rounds ?: preset.rounds} min"
                        IntervalMode.TABATA, IntervalMode.HIIT -> {
                            val sets = lead.intervalSets ?: 1
                            "${mode.displayName} $key · ${lead.intervalWorkSeconds ?: preset.workSeconds}s/${lead.intervalRestSeconds ?: preset.restSeconds}s × ${lead.rounds ?: preset.rounds}" +
                                (if (sets > 1) " · $sets sets" else "")
                        }
                    }
                }
            }
    }

    companion object {
        /** A draft task is a rest period when tagged with the Rest task type (or named "Rest" with no type). */
        fun isRestTask(t: DraftWorkoutTask): Boolean {
            val type = t.taskType.trim().lowercase(Locale.US)
            if (type == "rest") return true
            return type.isEmpty() && t.movementName.trim().lowercase(Locale.US) == "rest"
        }

        /** Whether a resolution reuses an existing library movement (drives the review badges). */
        fun isFromLibrary(resolution: MovementResolution?): Boolean {
            if (resolution == null) return false
            return when (resolution.status.lowercase(Locale.US)) {
                "matched" -> resolution.existingMovementId != null
                "ambiguous" -> resolution.candidates.isNotEmpty()
                else -> false
            }
        }

        /** Sets / reps-or-duration / rest / weight from the draft; timed work becomes a duration-mode task. */
        fun applyPrescription(draftTask: DraftWorkoutTask, task: WorkoutTask): WorkoutTask {
            val sets = maxOf(draftTask.sets ?: 3, 1)
            val weight = draftTask.weightKg?.toDouble()
            var t = task.copy(sets = sets, sameAcrossSets = true, weightKg = draftTask.weightKg)
            val duration = draftTask.durationSeconds
            t = if (duration != null && duration > 0) {
                t.copy(usesDuration = true, durationSeconds = duration, reps = 0, metric = "$sets x ${duration}s",
                    setConfigs = (1..sets).map { TaskSetConfig(setNumber = it, reps = 0, weightKg = weight, durationSeconds = duration) })
            } else {
                val reps = maxOf(draftTask.reps ?: 10, 0)
                t.copy(usesDuration = false, reps = reps, metric = "$sets x $reps",
                    setConfigs = (1..sets).map { TaskSetConfig(setNumber = it, reps = reps, weightKg = weight) })
            }
            val rest = draftTask.restSeconds
            if (rest != null && rest > 0) t = t.copy(restSeconds = rest, rest = WorkoutRowOps.formatRest(rest))
            return t
        }

        /** Splits a phase's draft tasks into block runs; breaks on a different / missing groupKey or a Rest task. */
        fun blockRuns(tasks: List<DraftWorkoutTask>): List<DraftBlockRun> {
            val runs = mutableListOf<DraftBlockRun>()
            val current = mutableListOf<DraftWorkoutTask>()
            fun flush() {
                val lead = current.firstOrNull()
                if (lead != null) {
                    val interval = current.any { it.groupType?.lowercase(Locale.US) == "interval" || IntervalMode.fromRaw(it.intervalMode) != null }
                    if (interval || current.size >= 2) {
                        val kind = if (interval) DraftBlockKind.INTERVAL else if (current.size == 2) DraftBlockKind.SUPERSET else DraftBlockKind.CIRCUIT
                        runs.add(DraftBlockRun(lead.groupKey!!.trim(), kind, current.map { it.taskIndex }, lead))
                    }
                }
                current.clear()
            }
            for (t in tasks) {
                val key = t.groupKey?.trim() ?: ""
                if (key.isEmpty() || isRestTask(t)) { flush(); continue }
                val prev = current.lastOrNull()
                if (prev != null && ((prev.groupKey?.trim() ?: "") != key || prev.phase != t.phase)) flush()
                current.add(t)
            }
            flush()
            return runs
        }

        /** Folds built tasks into editor rows: block members collapse into one grouped row. */
        fun rows(built: List<Pair<DraftWorkoutTask, WorkoutTask>>): List<TaskRow> {
            val runs = blockRuns(built.map { it.first })
            val runByIndex = HashMap<Int, Pair<DraftBlockRun, Int>>()
            for (run in runs) run.taskIndexes.forEachIndexed { position, idx -> runByIndex[idx] = run to position }
            val taskByIndex = built.associate { it.first.taskIndex to it.second }
            val rows = mutableListOf<TaskRow>()
            for ((draft, task) in built) {
                val membership = runByIndex[draft.taskIndex]
                if (membership == null) { rows.add(TaskRow(task = task)); continue }
                if (membership.second != 0) continue
                val members = membership.first.taskIndexes.mapNotNull { taskByIndex[it] }
                rows.add(groupRow(membership.first, members))
            }
            return rows
        }

        private fun groupRow(run: DraftBlockRun, members: List<WorkoutTask>): TaskRow {
            val type = run.groupType
            val groupId = WorkoutRowOps.lowercaseUuid()
            var lead = members[0]
            var mode: IntervalMode? = null
            val rounds: Int

            fun clearInterval(t: WorkoutTask) = t.copy(intervalMode = null, intervalWorkSeconds = null, intervalRestSeconds = null,
                intervalWindowSeconds = null, intervalSets = null, intervalSetRestSeconds = null)
            fun singleSet(t: WorkoutTask): WorkoutTask = t.copy(
                sets = 1,
                setConfigs = t.setConfigs.take(1),
                metric = if (t.usesDuration) "1 x ${t.durationSeconds ?: 0}s" else "1 x ${t.reps}"
            )

            when (run.kind) {
                DraftBlockKind.SUPERSET, DraftBlockKind.CIRCUIT -> {
                    rounds = maxOf(run.lead.rounds ?: 3, 1)
                    lead = clearInterval(lead).copy(roundRestSeconds = run.lead.roundRestSeconds ?: 60)
                }
                DraftBlockKind.INTERVAL -> {
                    val m = run.intervalMode ?: IntervalMode.HIIT
                    mode = m
                    val preset: IntervalPreset = WorkoutRowOps.preset(m)
                    val work = run.lead.intervalWorkSeconds ?: preset.workSeconds
                    val rest = run.lead.intervalRestSeconds ?: preset.restSeconds
                    when (m) {
                        IntervalMode.TABATA, IntervalMode.HIIT -> {
                            rounds = (run.lead.rounds ?: preset.rounds).coerceIn(1, 30)
                            lead = lead.copy(intervalWorkSeconds = work, intervalRestSeconds = rest, intervalWindowSeconds = (work + rest) * rounds)
                        }
                        IntervalMode.EMOM -> {
                            rounds = maxOf(run.lead.rounds ?: ((run.lead.intervalWindowSeconds ?: preset.totalSeconds) / 60), 1)
                            lead = lead.copy(intervalWorkSeconds = work.coerceIn(5, 55), intervalRestSeconds = null, intervalWindowSeconds = rounds * 60)
                        }
                        IntervalMode.AMRAP -> {
                            rounds = 0
                            lead = lead.copy(intervalWorkSeconds = null, intervalRestSeconds = null,
                                intervalWindowSeconds = maxOf(run.lead.intervalWindowSeconds ?: preset.totalSeconds, 60))
                        }
                    }
                    lead = lead.copy(
                        intervalMode = m,
                        intervalSets = if (m == IntervalMode.AMRAP) 1 else maxOf(run.lead.intervalSets ?: 1, 1),
                        intervalSetRestSeconds = maxOf(run.lead.intervalSetRestSeconds ?: IntervalMath.defaultSetRestSeconds, 0),
                        roundRestSeconds = null, restSeconds = 0, rest = ""
                    ).withRestType(RestType.PASSIVE)
                }
            }

            fun stamp(t: WorkoutTask): WorkoutTask {
                var s = t.copy(groupId = groupId, isSuperset = type == ExerciseGroupType.Superset, isCircuit = type == ExerciseGroupType.Circuit,
                    isInterval = type == ExerciseGroupType.Interval, rounds = rounds, totalRounds = rounds)
                s = singleSet(s)
                if (type == ExerciseGroupType.Interval) s = s.copy(restSeconds = 0, rest = "")
                return s
            }
            lead = stamp(lead)
            val siblings = members.drop(1).map { stamp(clearInterval(it).copy(roundRestSeconds = null)) }

            return TaskRow(
                task = lead,
                advanced = type.label,
                rounds = if (rounds > 0) "$rounds rounds" else "",
                totalRounds = rounds,
                isSuperset = type == ExerciseGroupType.Superset,
                isCircuit = type == ExerciseGroupType.Circuit,
                isInterval = type == ExerciseGroupType.Interval,
                intervalMode = mode,
                advancedTasks = siblings
            )
        }

        fun friendlyMessage(reasonCode: String?, detail: String?): String = when {
            reasonCode == "gemini_unavailable" -> "The AI service is temporarily unavailable. Please try again shortly."
            reasonCode == "timeout" -> "Generation timed out. Please try again."
            reasonCode == "schema_violation" -> "The AI returned an unexpected response. Please try again."
            reasonCode != null && (reasonCode.lowercase(Locale.US).contains("unsafe") || reasonCode.lowercase(Locale.US).contains("safety")) ->
                "That request couldn't be processed. Try describing a standard workout."
            else -> detail ?: "Couldn't generate a workout. Please try a different prompt."
        }
    }
}
