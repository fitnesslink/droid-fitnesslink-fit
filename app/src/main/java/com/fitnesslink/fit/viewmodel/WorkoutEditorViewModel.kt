package com.fitnesslink.fit.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.media.MediaUploadCoordinator
import com.fitnesslink.fit.media.UploadState
import com.fitnesslink.fit.model.ContentTagSelection
import com.fitnesslink.fit.model.ExerciseGroupType
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.SetPreset
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.model.api.AvailableWorkoutPhase
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.CreateWorkoutPhaseTemplateRequest
import com.fitnesslink.fit.network.dto.WorkoutPhaseTemplate
import com.fitnesslink.fit.network.dto.WorkoutPhaseTemplateTaskInput
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import com.fitnesslink.fit.workouts.WorkoutPhaseStore
import com.fitnesslink.fit.workouts.WorkoutRowOps
import com.fitnesslink.fit.workouts.WorkoutSaveEvent
import com.fitnesslink.fit.workouts.WorkoutStore
import com.fitnesslink.fit.workouts.WorkoutTaskInputs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * Workout builder state + mutations — port of iOS `WorkoutEditorViewModel`.
 * Row / task transformations live in [WorkoutRowOps]; the save pipeline in
 * [WorkoutStore]; the phase catalog in [WorkoutPhaseStore]. This class only
 * holds the editing snapshot and the sheet / flow flags the screen binds to.
 */
class WorkoutEditorViewModel : ViewModel() {
    var workout by mutableStateOf(Workout())
        private set
    var isNewWorkout by mutableStateOf(true)
        private set
    var isDirty by mutableStateOf(false)
    var isMultiSelectMode by mutableStateOf(false)
    var selectedTaskIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var showExerciseBrowser by mutableStateOf(false)
    var showCreateExercise by mutableStateOf(false)
    /** Rest task whose active-recovery movement is being picked (single-select browser). */
    var restMovementPickerTaskId by mutableStateOf<String?>(null)
    /** Movement task whose exercise is being replaced (single-select browser). */
    var replaceExerciseTaskId by mutableStateOf<String?>(null)
    var activePhaseIndex by mutableIntStateOf(0)
    var expandedTaskId by mutableStateOf<String?>(null)
    var pendingGroupType by mutableStateOf<ExerciseGroupType?>(null)
    var pendingGroupTaskIds by mutableStateOf<List<String>>(emptyList())
        private set

    // Guided interval setup flow
    var showIntervalModePicker by mutableStateOf(false)
    var pendingIntervalMode by mutableStateOf<IntervalMode?>(null)
        private set
    var isGroupingExistingSelection by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set
    var saveErrorMessage by mutableStateOf<String?>(null)
    var saveErrorIsFatal by mutableStateOf(false)
        private set
    var phaseRenameError by mutableStateOf<String?>(null)

    // Explicit equipment / anatomy tags (override-with-fallback). Mutable
    // classes — `tagsRevision` bumps so composables recompose.
    var equipmentTags = ContentTagSelection()
        private set
    var anatomyTags = ContentTagSelection()
        private set
    var tagsRevision by mutableIntStateOf(0)
        private set

    // Workout image upload (photo picker → MediaUploadCoordinator "photos")
    var localImageUri by mutableStateOf<Uri?>(null)
        private set
    var isUploadingImage by mutableStateOf(false)
        private set
    private var imageUploadId: String? = null

    /** Contributor templates and shared-in workouts are read-only; the screen offers the fork prompt. */
    val isReadOnlySource: Boolean get() = !isNewWorkout && !WorkoutRowOps.isEditable(workout)

    init {
        WorkoutStore.ensureRegistered()
        viewModelScope.launch {
            WorkoutStore.events.collect { event ->
                if (event is WorkoutSaveEvent.CreateDidApply && workout.id.equals(event.localId, true)) {
                    workout = workout.copy(id = event.serverId)
                    isNewWorkout = false
                }
            }
        }
    }

    // MARK: - Load

    fun createNewWorkout() {
        viewModelScope.launch { WorkoutPhaseStore.refresh() }
        val systemPhases = WorkoutPhaseStore.systemPhases.map {
            WorkoutPhase(id = it.id.lowercase(Locale.US), name = it.name, order = it.sortOrder, isSystem = true)
        }
        workout = Workout(id = UUID.randomUUID().toString().lowercase(Locale.US), name = "", phases = systemPhases)
        equipmentTags = ContentTagSelection(isLoaded = true)
        anatomyTags = ContentTagSelection(isLoaded = true)
        tagsRevision++
        isNewWorkout = true
        isDirty = false
        localImageUri = null
        imageUploadId = null
    }

    fun loadExistingWorkout(id: String) {
        var loaded = DatabaseManager.workout(id) ?: Workout(id = id)
        loaded = WorkoutRowOps.normalizeMovementFlags(loaded.hydrateGroupRounds()).recoverEstimatedTimeFromDisplayString()
        workout = loaded
        isNewWorkout = false
        isDirty = false
        localImageUri = null
        imageUploadId = null
        equipmentTags = ContentTagSelection()
        anatomyTags = ContentTagSelection()
        tagsRevision++
        viewModelScope.launch { loadTags(id) }
        viewModelScope.launch { WorkoutPhaseStore.refresh() }
    }

    private suspend fun loadTags(workoutId: String) {
        if (!NetworkMonitor.isConnected.value) return
        val remote = withContext(Dispatchers.IO) { runCatching { ApiClient.workoutApi.get(workoutId) }.getOrNull() } ?: return
        if (!workout.id.equals(workoutId, true)) return
        equipmentTags.seed(remote.equipmentIds.map { it.toString() }, remote.hasExplicitEquipment)
        anatomyTags.seed(remote.anatomyIds.map { it.toString() }, remote.hasExplicitAnatomy)
        tagsRevision++
    }

    /** "Build a Workout" entry: drop the movement into the main phase (local DB first, then server). */
    suspend fun prefillInitialMovement(movementId: String) {
        var item = DatabaseManager.movement(movementId)
        if (item == null && NetworkMonitor.isConnected.value) {
            val full = withContext(Dispatchers.IO) { runCatching { ApiClient.movementApi.get(movementId) }.getOrNull() }
            if (full != null) {
                item = MovementLibraryItem(
                    id = full.id.toString(), name = full.name, description = full.description ?: "",
                    anatomyIds = full.anatomyIds.map { it.toString() }, equipmentIds = full.equipmentIds.map { it.toString() },
                    isFavorite = full.isFavorite ?: false, imageId = full.imageId?.toString(), thumbnailId = full.thumbnailId?.toString(),
                    videoId = full.videoId?.toString(), phoneVideoId = full.phoneVideoId?.toString(),
                    imageUrl = full.imageUrl, thumbnailUrl = full.thumbnailUrl, videoUrl = full.videoUrl, phoneVideoUrl = full.phoneVideoUrl,
                    contributorId = full.contributorId?.toString()
                )
            }
        }
        val movement = item ?: return
        addExercise(movement, phaseIndexForInitialMovement())
        isDirty = false
    }

    private fun phaseIndexForInitialMovement(): Int {
        val phases = workout.phases
        phases.indexOfFirst { val n = it.name.lowercase(Locale.US); n.contains("main") || n.contains("exercis") }.takeIf { it >= 0 }?.let { return it }
        phases.indexOfFirst { val n = it.name.lowercase(Locale.US); !n.contains("warm") && !n.contains("cool") }.takeIf { it >= 0 }?.let { return it }
        return 0
    }

    // MARK: - Header fields

    fun updateName(name: String) { workout = workout.copy(name = name); isDirty = true }
    fun updateDescription(text: String) { workout = workout.copy(description = text); isDirty = true }

    /** Estimated time in MINUTES (null clears). Stored as `estimatedTime` SECONDS + the "<n> min" display string. */
    fun setEstimatedMinutes(minutes: Int?) {
        workout = if (minutes == null || minutes <= 0) workout.copy(time = "", estimatedTime = null)
        else {
            val clamped = minOf(minutes, 600)
            workout.copy(time = "$clamped min", estimatedTime = clamped * 60)
        }
        isDirty = true
    }

    fun toggleTrainingLevel(level: String) {
        val selected = workout.trainingLevels.toMutableSet()
        if (!selected.remove(level)) selected.add(level)
        val ordered = WorkoutTaskInputs.trainingLevelOptions.filter { selected.contains(it) }
        workout = workout.copy(trainingLevels = ordered, trainingLevel = ordered.firstOrNull() ?: "")
        isDirty = true
    }

    /** Tapping the selected chip clears back to "no location". */
    fun setLocation(location: String?) {
        workout = workout.copy(location = location ?: "")
        isDirty = true
    }

    fun selectEquipmentTags(ids: Set<String>) { equipmentTags.select(ids); tagsRevision++; isDirty = true }
    fun resetEquipmentTags() { if (!equipmentTags.isPendingReset) { equipmentTags.resetToDerived(); tagsRevision++; isDirty = true } }
    fun selectAnatomyTags(ids: Set<String>) { anatomyTags.select(ids); tagsRevision++; isDirty = true }
    fun resetAnatomyTags() { if (!anatomyTags.isPendingReset) { anatomyTags.resetToDerived(); tagsRevision++; isDirty = true } }

    /** Photo picked: start the upload now so Save doesn't sit on it. */
    fun pickImage(uri: Uri) {
        localImageUri = uri
        isDirty = true
        viewModelScope.launch {
            isUploadingImage = true
            try {
                imageUploadId = withContext(Dispatchers.IO) { MediaUploadCoordinator.enqueue(uri, container = "photos") }
            } catch (e: Exception) {
                saveErrorMessage = "Failed to upload image: ${e.message ?: "unknown error"}"
                imageUploadId = null
                isUploadingImage = false
            }
        }
    }

    /** Wait for a pending image upload and attach its media id. Returns false when it failed. */
    private suspend fun awaitImageUpload(): Boolean {
        val uploadId = imageUploadId ?: return true
        val state = withTimeoutOrNull(120_000L) {
            MediaUploadCoordinator.observe(uploadId).first { it is UploadState.Ready || it is UploadState.Processing || it is UploadState.Failed }
        }
        isUploadingImage = false
        val mediaId = when (state) {
            is UploadState.Ready -> state.mediaId
            is UploadState.Processing -> state.mediaId
            else -> null
        }
        if (mediaId == null) {
            saveErrorMessage = "Failed to upload image: " + ((state as? UploadState.Failed)?.message ?: "the upload didn't finish")
            return false
        }
        runCatching { UUID.fromString(mediaId) }.getOrNull()?.let { workout = workout.copy(imageId = it) }
        imageUploadId = null
        localImageUri = null
        return true
    }

    // MARK: - Save

    /**
     * Local-first save: writes the cache synchronously, then pushes in the
     * returned job (create → `POST /workouts`; edit → shell PUT + replace-tasks),
     * draining pending phases first. Save & Close joins the job.
     */
    fun save(): Job {
        if (workout.name.isBlank()) workout = workout.copy(name = "My Workout")
        val wasNew = isNewWorkout
        DatabaseManager.insertWorkout(workout)
        isDirty = false
        saveErrorMessage = null
        saveErrorIsFatal = false
        val equipmentWire = equipmentTags.wireValue
        val anatomyWire = anatomyTags.wireValue

        return viewModelScope.launch {
            isSaving = true
            try {
                if (!awaitImageUpload()) { saveErrorIsFatal = true; return@launch }
                val snapshot = workout
                DatabaseManager.insertWorkout(snapshot)
                if (WorkoutPhaseStore.hasUnsyncedPhase(snapshot.phases.map { it.id })) SyncManager.drain(SyncDomain.WORKOUT_PHASES)
                val forPush = (DatabaseManager.workout(snapshot.id) ?: snapshot)
                val inputs = WorkoutTaskInputs.taskInputs(forPush) { ClassificationStore.workoutTaskTypeId(it) }
                try {
                    if (wasNew) {
                        val request = WorkoutTaskInputs.createRequest(
                            forPush, inputs, statusId = ClassificationStore.contentStatusId("Active"),
                            equipmentIds = equipmentWire, anatomyIds = anatomyWire
                        )
                        val saved = withContext(Dispatchers.IO) { WorkoutStore.commitCreate(forPush.id, request) }
                        if (workout.id.equals(snapshot.id, true)) workout = workout.copy(id = saved.id.lowercase(Locale.US))
                        isNewWorkout = false
                        equipmentTags.markSaved(); anatomyTags.markSaved(); tagsRevision++
                    } else {
                        val updateReq = WorkoutTaskInputs.updateRequest(forPush, equipmentWire, anatomyWire)
                        var transient: Exception? = null
                        try {
                            withContext(Dispatchers.IO) { WorkoutStore.commitUpdateShell(forPush.id, updateReq) }
                        } catch (e: Exception) {
                            if (WorkoutStore.isFatalCommitError(e)) throw e
                            transient = e
                        }
                        equipmentTags.markSaved(); anatomyTags.markSaved(); tagsRevision++
                        try {
                            withContext(Dispatchers.IO) { WorkoutStore.commitReplaceTasks(forPush.id, inputs) }
                        } catch (e: Exception) {
                            if (WorkoutStore.isFatalCommitError(e)) throw e
                            if (transient == null) transient = e
                        }
                        transient?.let { throw it }
                        WorkoutStore.notifySaveDidCommit(forPush.id)
                    }
                } catch (e: ApiError.ServerError) {
                    if (e.statusCode == 400) {
                        saveErrorMessage = e.envelope?.displayMessage ?: "Workout couldn't be saved. Please review and try again."
                        saveErrorIsFatal = true
                    } else {
                        saveErrorMessage = "Couldn't reach the server — your workout is saved on this device and will retry automatically."
                    }
                } catch (e: ApiError.Forbidden) {
                    saveErrorMessage = "You can't edit this workout — it belongs to another user."
                    saveErrorIsFatal = true
                } catch (e: ApiError.NotFound) {
                    saveErrorMessage = "This workout no longer exists on the server."
                    saveErrorIsFatal = true
                } catch (e: ApiError.Unauthorized) {
                    saveErrorMessage = "Your session expired — your work is saved on this device and will sync once you're signed in again."
                } catch (e: ApiError.Offline) {
                    saveErrorMessage = "No connection — your workout is saved on this device and will sync automatically when you're back online."
                } catch (e: ApiError.NetworkError) {
                    saveErrorMessage = "No connection — your workout is saved on this device and will sync automatically when you're back online."
                } catch (e: Exception) {
                    saveErrorMessage = "Couldn't reach the server — your workout is saved on this device and will retry automatically."
                }
            } finally {
                isSaving = false
            }
        }
    }

    // MARK: - Phases

    fun addPhase(phase: AvailableWorkoutPhase) {
        if (workout.phases.any { it.id.equals(phase.id, true) }) return
        val nextOrder = (workout.phases.maxOfOrNull { it.order } ?: 0) + 1
        workout = workout.copy(phases = workout.phases + WorkoutPhase(id = phase.id.lowercase(Locale.US), name = phase.name, order = nextOrder, isSystem = phase.isSystem))
        isDirty = true
    }

    /** Rename a user phase through the catalog API, then mirror onto the model. System phases are refused. */
    suspend fun renamePhase(index: Int, name: String) {
        val phase = workout.phases.getOrNull(index) ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == phase.name) return
        if (phase.isSystem) { phaseRenameError = "System phases can't be renamed. Add a custom phase instead."; return }
        try {
            WorkoutPhaseStore.rename(phase.id, trimmed)
        } catch (e: Exception) {
            phaseRenameError = e.message ?: "Couldn't rename the phase."
            return
        }
        updatePhase(index) { p ->
            p.copy(name = trimmed, taskRows = p.taskRows.map { row ->
                row.copy(task = row.task?.copy(phaseName = trimmed), advancedTasks = row.advancedTasks.map { it.copy(phaseName = trimmed) })
            })
        }
        isDirty = true
    }

    fun deletePhase(index: Int) {
        val phase = workout.phases.getOrNull(index) ?: return
        if (phase.taskRows.isNotEmpty()) return
        workout = workout.copy(phases = WorkoutRowOps.restampPhaseOrders(workout.phases.filterIndexed { i, _ -> i != index }))
        isDirty = true
    }

    fun movePhase(index: Int, up: Boolean) {
        val target = if (up) index - 1 else index + 1
        if (index !in workout.phases.indices || target !in workout.phases.indices) return
        val phases = workout.phases.toMutableList()
        val p = phases.removeAt(index)
        phases.add(target, p)
        workout = workout.copy(phases = WorkoutRowOps.restampPhaseOrders(phases))
        isDirty = true
    }

    /** Duplicate a phase and all its tasks into a NEW catalog phase ("Name (copy)") inserted after the source. */
    suspend fun duplicatePhase(index: Int) {
        val source = workout.phases.getOrNull(index) ?: return
        val taken = WorkoutPhaseStore.available.value.map { it.name.lowercase(Locale.US) }.toSet()
        val copyName = WorkoutRowOps.duplicateName(source.name, taken)
        val newPhaseId = try {
            WorkoutPhaseStore.create(copyName)
        } catch (e: Exception) {
            saveErrorMessage = e.message ?: "Couldn't duplicate the phase. Please try again."
            return
        }
        val sourceIdx = workout.phases.indexOfFirst { it.id == source.id }.takeIf { it >= 0 } ?: minOf(index, workout.phases.size - 1)
        val copy = source.copy(id = newPhaseId, name = copyName, isSystem = false,
            taskRows = WorkoutRowOps.copiedTaskRows(source.taskRows, newPhaseId, copyName))
        val phases = workout.phases.toMutableList()
        phases.add(sourceIdx + 1, copy)
        workout = workout.copy(phases = WorkoutRowOps.restampPhaseOrders(phases))
        isDirty = true
    }

    /** Save one phase's task list as a reusable server-side template (409 = duplicate name). */
    suspend fun savePhaseAsTemplate(index: Int, name: String): WorkoutPhaseTemplate {
        val phase = workout.phases.getOrNull(index) ?: throw ApiError.NotFound
        if (!NetworkMonitor.isConnected.value) throw ApiError.Offline
        val tasks = WorkoutTaskInputs.taskInputs(phase) { ClassificationStore.workoutTaskTypeId(it) }
            .map { WorkoutPhaseTemplateTaskInput.from(it) }
        return withContext(Dispatchers.IO) { ApiClient.workoutPhaseApi.createTemplate(CreateWorkoutPhaseTemplateRequest(name, tasks)) }
    }

    /** Insert a saved template as a NEW phase (fresh catalog phase, copied tasks). */
    suspend fun insertPhaseTemplate(template: WorkoutPhaseTemplate) {
        val taken = WorkoutPhaseStore.available.value.map { it.name.lowercase(Locale.US) }.toSet()
        val phaseName = if (taken.contains(template.name.lowercase(Locale.US))) WorkoutRowOps.duplicateName(template.name, taken) else template.name
        val newPhaseId = try {
            WorkoutPhaseStore.create(phaseName)
        } catch (e: Exception) {
            saveErrorMessage = e.message ?: "Couldn't add the template. Please try again."
            return
        }
        val modelTasks = template.tasks.map { t ->
            com.fitnesslink.fit.model.api.WorkoutPhaseTemplateTask(
                id = t.id, workoutTaskTypeId = t.workoutTaskTypeId, order = t.order, groupId = t.groupId, movementId = t.movementId,
                advancedMovementId = t.advancedMovementId, reps = t.reps, sets = t.sets, restSeconds = t.restSeconds, rounds = t.rounds,
                intervalSeconds = t.intervalSeconds, instructions = t.instructions, weightKg = t.weightKg, incline = t.incline,
                paceSeconds = t.paceSeconds, speed = t.speed, caloriesBurned = t.caloriesBurned, heartRate = t.heartRate,
                roundRestSeconds = t.roundRestSeconds, intervalMode = t.intervalMode, intervalWorkSeconds = t.intervalWorkSeconds,
                intervalRestSeconds = t.intervalRestSeconds, intervalWindowSeconds = t.intervalWindowSeconds, intervalSets = t.intervalSets,
                intervalSetRestSeconds = t.intervalSetRestSeconds, restTypeId = t.restTypeId, restMovementId = t.restMovementId,
                sameAcrossSets = t.sameAcrossSets,
                setConfigs = t.setConfigs.map { com.fitnesslink.fit.model.api.WorkoutPhaseTemplateSetConfig(it.setNumber, it.reps, it.weightKg, it.durationSeconds, it.isDropSet, it.isWarmup) },
                movementName = t.movementName, movementThumbnailUrl = t.movementThumbnailUrl
            )
        }
        val rows = WorkoutRowOps.taskRowsFromTemplate(modelTasks, ClassificationStore.workoutTaskTypeId("Rest"))
        val phase = WorkoutPhase(
            id = newPhaseId, name = phaseName, order = (workout.phases.maxOfOrNull { it.order } ?: 0) + 1, isSystem = false,
            taskRows = WorkoutRowOps.copiedTaskRows(rows, newPhaseId, phaseName)
        )
        workout = workout.copy(phases = WorkoutRowOps.restampPhaseOrders(workout.phases + phase))
        isDirty = true
    }

    /** Merge an AI-generated draft into the editor (fills empty header fields; appends rows into matching phases). */
    fun applyGenerated(generated: Workout) {
        var w = workout
        if (w.name.isEmpty()) w = w.copy(name = generated.name)
        if (w.description.isEmpty()) w = w.copy(description = generated.description)
        if (w.trainingLevel.isEmpty()) w = w.copy(trainingLevel = generated.trainingLevel)
        if (w.trainingLevels.isEmpty()) {
            w = w.copy(trainingLevels = generated.trainingLevels.ifEmpty { listOfNotNull(generated.trainingLevel.takeIf { it.isNotEmpty() }) })
        }
        if (w.time.isEmpty()) w = w.copy(time = generated.time)
        if (w.estimatedTime == null) w = w.copy(estimatedTime = generated.estimatedTime)

        val phases = w.phases.toMutableList()
        for (genPhase in generated.phases) {
            val idx = phases.indexOfFirst { it.id.equals(genPhase.id, true) || it.name.equals(genPhase.name, true) }
            if (idx >= 0) {
                val target = phases[idx]
                val base = target.taskRows.size
                val appended = genPhase.taskRows.mapIndexed { offset, row ->
                    row.copy(
                        task = row.task?.copy(phaseId = target.id, phaseName = target.name, workoutPhaseId = target.id, order = base + offset),
                        advancedTasks = row.advancedTasks.map { it.copy(phaseId = target.id, phaseName = target.name, workoutPhaseId = target.id) }
                    )
                }
                phases[idx] = target.copy(taskRows = target.taskRows + appended)
            } else {
                phases.add(genPhase.copy(order = (phases.maxOfOrNull { it.order } ?: 0) + 1))
            }
        }
        workout = w.copy(phases = phases)
        isDirty = true
    }

    // MARK: - Exercises

    fun addExercise(movement: MovementLibraryItem, phaseIndex: Int) {
        val phase = workout.phases.getOrNull(phaseIndex) ?: return
        val fresh = DatabaseManager.movement(movement.id) ?: movement
        val task = WorkoutRowOps.makeMovementTask(fresh).copy(order = phase.taskRows.size, phaseId = phase.id, phaseName = phase.name, workoutPhaseId = phase.id)

        val groupType = pendingGroupType
        if (groupType != null) {
            pendingGroupTaskIds = pendingGroupTaskIds + task.id
            updatePhase(phaseIndex) { it.copy(taskRows = it.taskRows + TaskRow(task = task)) }
            DatabaseManager.logRecentMovement(fresh.id)
            isDirty = true
            if (groupType == ExerciseGroupType.Superset && pendingGroupTaskIds.size >= 2) {
                finalizePendingGroup()
                showExerciseBrowser = false
            }
            return
        }

        val rows = phase.taskRows.toMutableList()
        val phaseName = phase.name.lowercase(Locale.US)
        val existingMovements = rows.mapNotNull { it.task }.filter { it.isMovement }
        if (existingMovements.isNotEmpty() && (phaseName.contains("main") || phaseName.contains("exercis"))) {
            rows.add(TaskRow(task = WorkoutRowOps.makeRestTask(60, ClassificationStore.workoutTaskTypeId("Rest"))))
        }
        rows.add(TaskRow(task = task))
        updatePhase(phaseIndex) { it.copy(taskRows = rows) }
        DatabaseManager.logRecentMovement(fresh.id)
        isDirty = true
    }

    fun finalizePendingGroup() {
        val type = pendingGroupType
        val minCount = type?.let(WorkoutRowOps::minimumGroupCount) ?: 2
        if (type == null || pendingGroupTaskIds.size < minCount || (type == ExerciseGroupType.Interval && pendingIntervalMode == null)) {
            pendingGroupType = null
            pendingGroupTaskIds = emptyList()
            clearIntervalSetupState()
            return
        }
        selectedTaskIds = pendingGroupTaskIds.toSet()
        createGroup(type, activePhaseIndex)
        pendingGroupType = null
        pendingGroupTaskIds = emptyList()
    }

    fun cancelPendingGroup() {
        pendingGroupType = null
        pendingGroupTaskIds = emptyList()
        clearIntervalSetupState()
    }

    fun beginIntervalSetup(phaseIndex: Int) {
        activePhaseIndex = phaseIndex
        isGroupingExistingSelection = false
        pendingGroupType = ExerciseGroupType.Interval
        pendingGroupTaskIds = emptyList()
        pendingIntervalMode = null
        showIntervalModePicker = true
    }

    fun completeIntervalModePick(mode: IntervalMode) {
        pendingIntervalMode = mode
        if (isGroupingExistingSelection) {
            showIntervalModePicker = false
            groupSelection(ExerciseGroupType.Interval)
            pendingGroupType = null
            pendingGroupTaskIds = emptyList()
            clearIntervalSetupState()
        } else {
            showExerciseBrowser = true
            showIntervalModePicker = false
        }
    }

    private fun phaseIndexForSelection(): Int? = workout.phases.indexOfFirst { phase ->
        phase.taskRows.any { row -> row.task?.id?.let { selectedTaskIds.contains(it) } == true }
    }.takeIf { it >= 0 }

    fun groupSelection(type: ExerciseGroupType) {
        val phaseIndex = phaseIndexForSelection() ?: return
        createGroup(type, phaseIndex)
    }

    fun beginIntervalFromSelection() {
        val phaseIndex = phaseIndexForSelection() ?: return
        activePhaseIndex = phaseIndex
        isGroupingExistingSelection = true
        pendingGroupType = ExerciseGroupType.Interval
        pendingIntervalMode = null
        showIntervalModePicker = true
    }

    private fun clearIntervalSetupState() {
        showIntervalModePicker = false
        pendingIntervalMode = null
        isGroupingExistingSelection = false
    }

    fun replaceMovement(taskId: String, movement: MovementLibraryItem) {
        val fresh = DatabaseManager.movement(movement.id) ?: movement
        forTask(taskId) { WorkoutRowOps.replaced(it, fresh) }
        DatabaseManager.logRecentMovement(fresh.id)
        isDirty = true
    }

    fun removeExercise(taskId: String, phaseIndex: Int) {
        val phase = workout.phases.getOrNull(phaseIndex) ?: return
        val rows = phase.taskRows.toMutableList()
        val idx = rows.indexOfFirst { it.task?.id == taskId }
        if (idx < 0) return
        if (idx > 0 && rows[idx - 1].task?.isRest == true) { rows.removeAt(idx - 1); rows.removeAt(idx - 1) } else rows.removeAt(idx)
        updatePhase(phaseIndex) { it.copy(taskRows = rows) }
        isDirty = true
    }

    fun moveExerciseUp(taskId: String, phaseIndex: Int) = swapWithNeighbor(taskId, phaseIndex, -1)
    fun moveExerciseDown(taskId: String, phaseIndex: Int) = swapWithNeighbor(taskId, phaseIndex, +1)

    private fun swapWithNeighbor(taskId: String, phaseIndex: Int, step: Int) {
        val phase = workout.phases.getOrNull(phaseIndex) ?: return
        val rows = phase.taskRows.toMutableList()
        val idx = rows.indexOfFirst { it.task?.id == taskId }
        if (idx < 0) return
        var target = idx + step
        while (target in rows.indices) {
            val t = rows[target].task
            if (t != null && (t.isMovement || !t.isRest)) break
            target += step
        }
        if (target !in rows.indices) return
        val tmp = rows[idx]; rows[idx] = rows[target]; rows[target] = tmp
        updatePhase(phaseIndex) { it.copy(taskRows = rows) }
        isDirty = true
    }

    /** Move one row (loose exercise, rest, or whole group) to another phase, at [rowIndex] or appended. */
    fun moveRow(taskId: String, toPhaseIndex: Int, rowIndex: Int? = null) {
        val destination = workout.phases.getOrNull(toPhaseIndex) ?: return
        val from = locateRow(taskId) ?: return
        if (from.first == toPhaseIndex) return
        val phases = workout.phases.toMutableList()
        val sourceRows = phases[from.first].taskRows.toMutableList()
        val row = WorkoutRowOps.rehomed(sourceRows.removeAt(from.second), destination)
        phases[from.first] = phases[from.first].copy(taskRows = sourceRows)
        val destRows = phases[toPhaseIndex].taskRows.toMutableList()
        destRows.add((rowIndex ?: destRows.size).coerceIn(0, destRows.size), row)
        phases[toPhaseIndex] = phases[toPhaseIndex].copy(taskRows = destRows)
        workout = workout.copy(phases = phases)
        isDirty = true
    }

    // MARK: - Set configuration

    fun updateTaskSets(taskId: String, sets: Int, reps: Int, weightLbs: Double?) {
        forTask(taskId) { WorkoutRowOps.updatedSets(it, sets, reps, weightLbs) }
        isDirty = true
    }

    fun updateInstructions(taskId: String, instructions: String) {
        forTask(taskId) { it.copy(instructions = instructions.trim().ifEmpty { null }?.let { instructions }) }
        isDirty = true
    }

    fun updateTaskDuration(taskId: String, sets: Int, durationSeconds: Int, weightLbs: Double?) {
        forTask(taskId) { WorkoutRowOps.updatedDuration(it, sets, durationSeconds, weightLbs) }
        isDirty = true
    }

    fun setUsesDuration(taskId: String, usesDuration: Boolean) {
        forTask(taskId) { WorkoutRowOps.withUsesDuration(it, usesDuration) }
        isDirty = true
    }

    fun updateSetConfig(taskId: String, setNumber: Int, reps: Int, weightLbs: Double?, isDropSet: Boolean) {
        forTask(taskId) { task ->
            task.copy(setConfigs = task.setConfigs.map { if (it.setNumber == setNumber) it.copy(reps = reps, weightKg = weightLbs, isDropSet = isDropSet) else it })
        }
        isDirty = true
    }

    fun updateSetConfigDuration(taskId: String, setNumber: Int, durationSeconds: Int, weightLbs: Double?) {
        forTask(taskId) { task ->
            task.copy(setConfigs = task.setConfigs.map { if (it.setNumber == setNumber) it.copy(durationSeconds = durationSeconds, weightKg = weightLbs) else it })
        }
        isDirty = true
    }

    fun toggleSameAcrossSets(taskId: String) { forTask(taskId, WorkoutRowOps::toggledSameAcrossSets); isDirty = true }
    fun togglePerRound(taskId: String) { forTask(taskId, WorkoutRowOps::toggledPerRound); isDirty = true }
    fun applyPreset(taskId: String, preset: SetPreset) = updateTaskSets(taskId, preset.sets, preset.reps, null)

    // MARK: - Rests

    fun addRest(afterTaskId: String, phaseIndex: Int, seconds: Int = 60) {
        val phase = workout.phases.getOrNull(phaseIndex) ?: return
        val idx = phase.taskRows.indexOfFirst { it.task?.id == afterTaskId }
        if (idx < 0) return
        val rows = phase.taskRows.toMutableList()
        rows.add(idx + 1, TaskRow(task = WorkoutRowOps.makeRestTask(seconds, ClassificationStore.workoutTaskTypeId("Rest"))))
        updatePhase(phaseIndex) { it.copy(taskRows = rows) }
        isDirty = true
    }

    fun addRestToPhase(phaseIndex: Int, seconds: Int = 60) {
        if (phaseIndex !in workout.phases.indices) return
        updatePhase(phaseIndex) { it.copy(taskRows = it.taskRows + TaskRow(task = WorkoutRowOps.makeRestTask(seconds, ClassificationStore.workoutTaskTypeId("Rest")))) }
        isDirty = true
    }

    fun updateRest(taskId: String, seconds: Int) {
        forTask(taskId) { it.copy(restSeconds = seconds, rest = WorkoutRowOps.formatRest(seconds)) }
        isDirty = true
    }

    /** Rest between sets on a straight-set movement; 0 = none. */
    fun updateExerciseRest(taskId: String, seconds: Int) {
        val clamped = maxOf(0, seconds)
        forTask(taskId) { it.copy(restSeconds = clamped, rest = if (clamped > 0) WorkoutRowOps.formatRest(clamped) else "") }
        isDirty = true
    }

    fun updateRestType(taskId: String, type: RestType) {
        forTask(taskId) { it.withRestType(type) }
        isDirty = true
    }

    fun updateRestMovement(taskId: String, movement: MovementLibraryItem) {
        val fresh = DatabaseManager.movement(movement.id) ?: movement
        forTask(taskId) { WorkoutRowOps.withRestMovement(it, fresh) }
        isDirty = true
    }

    fun updateRestMovementFromInGroupTask(taskId: String, source: WorkoutTask) {
        forTask(taskId) { WorkoutRowOps.withRestMovementFrom(it, source) }
        isDirty = true
    }

    fun clearRestMovement(taskId: String) {
        forTask(taskId, WorkoutRowOps::clearedRestMovement)
        isDirty = true
    }

    fun removeRest(taskId: String, phaseIndex: Int) {
        if (phaseIndex !in workout.phases.indices) return
        updatePhase(phaseIndex) { it.copy(taskRows = it.taskRows.filter { row -> row.task?.id != taskId }) }
        isDirty = true
    }

    // MARK: - Groups

    fun createGroup(type: ExerciseGroupType, phaseIndex: Int) {
        val phase = workout.phases.getOrNull(phaseIndex) ?: return
        val newRows = WorkoutRowOps.groupRows(
            rows = phase.taskRows, selectedIds = selectedTaskIds, type = type, mode = pendingIntervalMode,
            passiveRestTypeId = ClassificationStore.restTypeId("Passive")
        )
        if (newRows === phase.taskRows) return
        updatePhase(phaseIndex) { it.copy(taskRows = newRows) }
        selectedTaskIds = emptySet()
        isMultiSelectMode = false
        isDirty = true
        clearIntervalSetupState()
    }

    fun canAddNextMovement(groupTaskId: String, phaseIndex: Int): Boolean =
        workout.phases.getOrNull(phaseIndex)?.let { WorkoutRowOps.canAddNextMovement(it.taskRows, groupTaskId) } ?: false

    fun addNextMovementToGroup(groupTaskId: String, phaseIndex: Int) {
        if (phaseIndex !in workout.phases.indices) return
        updatePhase(phaseIndex) { it.copy(taskRows = WorkoutRowOps.addNextMovementToGroup(it.taskRows, groupTaskId)) }
        isDirty = true
    }

    fun ungroupExercise(taskId: String, phaseIndex: Int) {
        if (phaseIndex !in workout.phases.indices) return
        updatePhase(phaseIndex) { it.copy(taskRows = WorkoutRowOps.ungroupRows(it.taskRows, taskId)) }
        isDirty = true
    }

    fun removeGroup(taskId: String, phaseIndex: Int) {
        if (phaseIndex !in workout.phases.indices) return
        updatePhase(phaseIndex) { it.copy(taskRows = it.taskRows.filter { row -> row.task?.id != taskId }) }
        isDirty = true
    }

    fun removeGroupMember(taskId: String, phaseIndex: Int) {
        if (phaseIndex !in workout.phases.indices) return
        updatePhase(phaseIndex) { it.copy(taskRows = WorkoutRowOps.removedGroupMember(it.taskRows, taskId)) }
        isDirty = true
    }

    fun moveGroupMember(taskId: String, up: Boolean, phaseIndex: Int) {
        if (phaseIndex !in workout.phases.indices) return
        updatePhase(phaseIndex) { phase ->
            phase.copy(taskRows = phase.taskRows.map { row ->
                if (row.task?.id == taskId || row.advancedTasks.any { it.id == taskId }) WorkoutRowOps.movedGroupMember(row, taskId, up) else row
            })
        }
        isDirty = true
    }

    fun updateGroupRounds(taskId: String, rounds: Int) = forRow(taskId) { WorkoutRowOps.withRounds(it, maxOf(1, rounds)) }

    /** Between-rounds rest for a superset / circuit; null clears. */
    fun updateRoundRest(taskId: String, seconds: Int?) = forRow(taskId) { it.copy(task = it.task?.copy(roundRestSeconds = seconds)) }

    /** Rest after a superset / circuit member, before the next member in the same round. */
    fun updateGroupMemberRest(taskId: String, seconds: Int?) {
        val clamped = maxOf(0, seconds ?: 0)
        forTask(taskId) { it.copy(restSeconds = clamped, rest = if (clamped > 0) WorkoutRowOps.formatRest(clamped) else "") }
        isDirty = true
    }

    // MARK: - Interval params

    fun updateIntervalMode(taskId: String, mode: IntervalMode) = forRow(taskId) { WorkoutRowOps.withIntervalMode(it, mode) }
    fun updateIntervalWork(taskId: String, seconds: Int) = forRow(taskId) { it.copy(task = it.task?.copy(intervalWorkSeconds = maxOf(1, seconds))) }
    fun updateIntervalRest(taskId: String, seconds: Int) = forRow(taskId) { it.copy(task = it.task?.copy(intervalRestSeconds = maxOf(0, seconds))) }
    fun updateIntervalSets(taskId: String, sets: Int) = forRow(taskId) { it.copy(task = it.task?.copy(intervalSets = maxOf(1, sets))) }
    fun updateIntervalSetRest(taskId: String, seconds: Int?) = forRow(taskId) {
        it.copy(task = it.task?.copy(intervalSetRestSeconds = seconds?.let { s -> maxOf(0, s) }, roundRestSeconds = null))
    }
    fun updateIntervalWindow(taskId: String, seconds: Int) = forRow(taskId) { WorkoutRowOps.withIntervalWindow(it, seconds) }

    // MARK: - Multi-select

    fun toggleTaskSelection(taskId: String) {
        selectedTaskIds = if (selectedTaskIds.contains(taskId)) selectedTaskIds - taskId else selectedTaskIds + taskId
        if (selectedTaskIds.isEmpty()) isMultiSelectMode = false
    }

    fun enterMultiSelect(taskId: String) {
        isMultiSelectMode = true
        toggleTaskSelection(taskId)
    }

    fun cancelMultiSelect() {
        selectedTaskIds = emptySet()
        isMultiSelectMode = false
    }

    // MARK: - Helpers

    private fun locateRow(taskId: String): Pair<Int, Int>? {
        workout.phases.forEachIndexed { pi, phase ->
            val ri = phase.taskRows.indexOfFirst { it.task?.id == taskId }
            if (ri >= 0) return pi to ri
        }
        return null
    }

    private fun updatePhase(index: Int, transform: (WorkoutPhase) -> WorkoutPhase) {
        val phases = workout.phases.toMutableList()
        phases[index] = transform(phases[index])
        workout = workout.copy(phases = phases)
    }

    /** Apply [transform] to the row whose LEAD task is [taskId]. */
    private fun forRow(taskId: String, transform: (TaskRow) -> TaskRow) {
        val (pi, ri) = locateRow(taskId) ?: return
        updatePhase(pi) { phase ->
            val rows = phase.taskRows.toMutableList()
            rows[ri] = transform(rows[ri])
            phase.copy(taskRows = rows)
        }
        isDirty = true
    }

    /** Apply [modify] to the task (lead or group member) with [taskId]. */
    private fun forTask(taskId: String, modify: (WorkoutTask) -> WorkoutTask) {
        workout.phases.forEachIndexed { pi, phase ->
            phase.taskRows.forEachIndexed { ri, row ->
                if (row.task?.id == taskId) {
                    updatePhase(pi) { p ->
                        val rows = p.taskRows.toMutableList()
                        rows[ri] = row.copy(task = modify(row.task))
                        p.copy(taskRows = rows)
                    }
                    return
                }
                val ai = row.advancedTasks.indexOfFirst { it.id == taskId }
                if (ai >= 0) {
                    updatePhase(pi) { p ->
                        val rows = p.taskRows.toMutableList()
                        val adv = row.advancedTasks.toMutableList()
                        adv[ai] = modify(adv[ai])
                        rows[ri] = row.copy(advancedTasks = adv)
                        p.copy(taskRows = rows)
                    }
                    return
                }
            }
        }
    }

    /** Lower-cased phase ids in the workout, for the phase picker's "Added" badges. */
    val attachedPhaseIds: Set<String> get() = workout.phases.map { it.id.lowercase(Locale.US) }.toSet()

    companion object {
        val trainingLevelOptions: List<String> get() = WorkoutTaskInputs.trainingLevelOptions
        val locationOptions: List<String> get() = WorkoutTaskInputs.locationOptions
        val estimatedTimePresets = listOf(15, 30, 45, 60, 90)
    }
}
