package com.fitnesslink.fit.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.media.MediaUploadCoordinator
import com.fitnesslink.fit.media.UploadState
import com.fitnesslink.fit.model.ContentTagSelection
import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.model.ProgramDaySlot
import com.fitnesslink.fit.model.ProgramWeek
import com.fitnesslink.fit.model.ProgramWriteRequest
import com.fitnesslink.fit.model.WorkoutList
import com.fitnesslink.fit.model.api.ProgramSchedule
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.api.AddWeeklyWorkoutRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.toListRow
import com.fitnesslink.fit.programs.ProgramStore
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * Program editor — port of iOS `ProgramEditorViewModel`. Local-first: Save
 * writes the program + schedule rows to SQLite, then `POST`/`PUT /programs`
 * and one `POST /programs/{id}/weekly-workouts` per slot. When offline (or
 * the request fails) the write is queued under the `programs` sync domain
 * so the generic push replays it.
 */
class ProgramEditorViewModel : ViewModel() {
    var program by mutableStateOf(Program())
    var weeks by mutableStateOf<List<ProgramWeek>>(emptyList())
    var isNewProgram by mutableStateOf(true)
    var isDirty by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
        private set
    var saveErrorMessage by mutableStateOf<String?>(null)

    // Workout picker state
    var showWorkoutPicker by mutableStateOf(false)
    var targetWeekNumber by mutableStateOf(1)
    var targetDayNumber by mutableStateOf(1)
    var availableWorkouts by mutableStateOf<List<WorkoutList>>(emptyList())
    var workoutSearchText by mutableStateOf("")

    // Program image (photo picker → MediaUploadCoordinator "photos"; attached on save).
    var localImageUri by mutableStateOf<Uri?>(null)
        private set
    var isUploadingImage by mutableStateOf(false)
        private set
    private var imageUploadId: String? = null

    /**
     * Explicit equipment / anatomy tag state (2026-09-10 tags contract).
     * Seeded from the live `GET /programs/{id}` read and sent as the
     * optional `equipmentIds` / `anatomyIds` via `ContentTagSelection.wireValue`
     * (untouched → null, edited → list, reset → []). Non-observable
     * classes — `tagsRevision` bumps so composables recompose.
     */
    var equipmentTags = ContentTagSelection()
        private set
    var anatomyTags = ContentTagSelection()
        private set
    var tagsRevision by mutableIntStateOf(0)
        private set

    val filteredWorkouts: List<WorkoutList>
        get() = if (workoutSearchText.isEmpty()) availableWorkouts
        else availableWorkouts.filter { it.name.contains(workoutSearchText, ignoreCase = true) }

    val assignedWorkoutCount: Int get() = weeks.flatMap { it.days }.count { it.workout != null }

    companion object {
        val trainingLevelOptions = listOf("Beginner", "Intermediate", "Specialized", "Advanced")
        val locationOptions = listOf("Gym", "Home", "Outdoors", "Track", "Studio", "Pool")
        val timePresets = listOf(15, 30, 45, 60, 90)
    }

    // MARK: - Init

    fun createNewProgram() {
        program = Program(id = UUID.randomUUID().toString().lowercase(Locale.US))
        weeks = listOf(ProgramWeek.empty(1))
        equipmentTags = ContentTagSelection(isLoaded = true)
        anatomyTags = ContentTagSelection(isLoaded = true)
        tagsRevision++
        localImageUri = null
        imageUploadId = null
        isNewProgram = true
        isDirty = false
    }

    fun loadExistingProgram(id: String) {
        program = DatabaseManager.program(id) ?: Program()
        val schedules = DatabaseManager.schedulesForProgram(id)
        val lookup = DatabaseManager.allWorkouts().associateBy { it.id.lowercase(Locale.US) }
        val maxWeek = maxOf(schedules.maxOfOrNull { it.weekNumber } ?: 1, 1)
        weeks = (1..maxWeek).map { w ->
            val week = ProgramWeek.empty(w)
            week.copy(days = week.days.map { day ->
                val schedule = schedules.firstOrNull { it.weekNumber == w && it.dayNumber == day.dayNumber } ?: return@map day
                val key = schedule.workoutId.toString().lowercase(Locale.US)
                day.copy(workoutId = key, workout = lookup[key])
            })
        }
        isNewProgram = false
        isDirty = false
        localImageUri = null
        imageUploadId = null
        // Tag pickers stay inert until the live read lands (offline: the save sends null, server sets untouched).
        equipmentTags = ContentTagSelection()
        anatomyTags = ContentTagSelection()
        tagsRevision++
        viewModelScope.launch { loadTags(id) }
    }

    private suspend fun loadTags(programId: String) {
        val detail = ProgramStore.fetchDetail(programId) ?: return
        if (program.id != programId) return
        val remote = detail.program
        // The cache row we just refreshed may carry a fuller description / levels than the one loaded before.
        program = remote.copy(contributor = null)
        equipmentTags.seed(remote.equipmentIds.map { it.toString() }, remote.hasExplicitEquipment)
        anatomyTags.seed(remote.anatomyIds.map { it.toString() }, remote.hasExplicitAnatomy)
        tagsRevision++
    }

    // MARK: - Metadata edits

    fun setName(name: String) { program = program.copy(name = name); isDirty = true }
    fun setDescription(text: String) { program = program.copy(description = text); isDirty = true }
    fun setTime(minutes: Int) { program = program.copy(time = "$minutes min", estimatedTime = minutes * 60); isDirty = true }

    /** Multi-select level chip: canonical order, legacy single `trainingLevel` points at the first pick. */
    fun toggleTrainingLevel(level: String) {
        val selected = program.trainingLevels.toMutableSet()
        if (!selected.remove(level)) selected.add(level)
        val ordered = trainingLevelOptions.filter { it in selected }
        program = program.copy(
            trainingLevels = ordered,
            trainingLevel = ordered.firstOrNull() ?: "",
            hasExplicitTrainingLevels = ordered.isNotEmpty()
        )
        isDirty = true
    }

    /** Single-select location chip; tapping the selected one clears it. */
    fun toggleLocation(location: String) {
        val next = if (program.location == location) "" else location
        program = program.copy(location = next, hasExplicitLocation = next.isNotEmpty())
        isDirty = true
    }

    fun selectEquipmentTags(ids: Set<String>) { equipmentTags.select(ids); tagsRevision++; isDirty = true }
    fun resetEquipmentTags() { if (!equipmentTags.isPendingReset) { equipmentTags.resetToDerived(); tagsRevision++; isDirty = true } }
    fun selectAnatomyTags(ids: Set<String>) { anatomyTags.select(ids); tagsRevision++; isDirty = true }
    fun resetAnatomyTags() { if (!anatomyTags.isPendingReset) { anatomyTags.resetToDerived(); tagsRevision++; isDirty = true } }

    // MARK: - Image

    fun pickImage(uri: Uri) {
        localImageUri = uri
        isDirty = true
        isUploadingImage = true
        viewModelScope.launch {
            imageUploadId = try {
                withContext(Dispatchers.IO) { MediaUploadCoordinator.enqueue(uri, container = "photos") }
            } catch (_: Exception) {
                null
            }
            if (imageUploadId == null) {
                isUploadingImage = false
                saveErrorMessage = "Couldn't read the selected image."
                localImageUri = null
            }
        }
    }

    /** Wait for a pending image upload and attach its media id. False when it failed. */
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
        runCatching { UUID.fromString(mediaId.trim().trim('"')) }.getOrNull()?.let {
            program = program.copy(imageId = it, thumbnailId = it)
        }
        imageUploadId = null
        return true
    }

    // MARK: - Save

    /**
     * Local write first, then the server. Invokes [onSaved] with the program
     * id once the local write has landed (iOS fires `onSaved` before dismiss).
     */
    fun save(onSaved: (String) -> Unit) {
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            try {
                if (!awaitImageUpload()) return@launch
                if (program.name.isBlank()) program = program.copy(name = "My Program")
                program = program.copy(weeks = weeks.size)
                val schedules = scheduleRows()
                withContext(Dispatchers.IO) {
                    DatabaseManager.insertProgram(program)
                    DatabaseManager.replaceSchedulesForProgram(program.id, schedules)
                }
                val wasNew = isNewProgram
                isDirty = false
                isNewProgram = false
                val equipmentWire = equipmentTags.wireValue
                val anatomyWire = anatomyTags.wireValue
                val saved = program
                onSaved(saved.id)
                viewModelScope.launch { syncToServer(saved, schedules, wasNew, equipmentWire, anatomyWire) }
            } finally {
                isSaving = false
            }
        }
    }

    private fun scheduleRows(): List<ProgramSchedule> {
        val programUUID = runCatching { UUID.fromString(program.id) }.getOrNull() ?: return emptyList()
        return weeks.flatMap { week ->
            week.days.mapNotNull { day ->
                val workout = day.workout ?: return@mapNotNull null
                val workoutUUID = runCatching { UUID.fromString(workout.id) }.getOrNull() ?: return@mapNotNull null
                ProgramSchedule(id = UUID.randomUUID(), programId = programUUID, workoutId = workoutUUID, weekNumber = day.weekNumber, dayNumber = day.dayNumber)
            }
        }
    }

    private suspend fun syncToServer(
        saved: Program,
        schedules: List<ProgramSchedule>,
        isNew: Boolean,
        equipmentIds: List<String>?,
        anatomyIds: List<String>?
    ) {
        val request = ProgramWriteRequest(saved, equipmentIds = equipmentIds, anatomyIds = anatomyIds)
        if (!NetworkMonitor.isConnected.value) { enqueue(saved, request, isNew); return }
        try {
            withContext(Dispatchers.IO) {
                if (isNew) ApiClient.programApi.create(request) else ApiClient.programApi.update(saved.id, request)
                for (s in schedules) {
                    ApiClient.programApi.addWeeklyWorkout(saved.id, AddWeeklyWorkoutRequest(s.workoutId.toString(), s.weekNumber, s.dayNumber))
                }
            }
            equipmentTags.markSaved(); anatomyTags.markSaved(); tagsRevision++
        } catch (_: Exception) {
            enqueue(saved, request, isNew)
        }
    }

    /** Durable fallback: the generic `/sync/push` replays `programs` rows (the schedule follows on the next editor save). */
    private fun enqueue(saved: Program, request: ProgramWriteRequest, isNew: Boolean) {
        DatabaseManager.enqueueSyncEntry(
            SyncDomain.PROGRAMS.wireName, saved.id, if (isNew) "create" else "update",
            com.fitnesslink.fit.model.ModelGson.instance.toJson(request)
        )
        viewModelScope.launch { runCatching { SyncManager.push() } }
    }

    // MARK: - Week Management

    fun addWeek() {
        val nextNumber = (weeks.maxOfOrNull { it.weekNumber } ?: 0) + 1
        weeks = weeks + ProgramWeek.empty(nextNumber)
        isDirty = true
    }

    fun removeWeek(weekNumber: Int) {
        if (weeks.size <= 1) return
        weeks = weeks.filter { it.weekNumber != weekNumber }.mapIndexed { index, week ->
            val newNumber = index + 1
            week.copy(weekNumber = newNumber, days = week.days.map { it.copy(weekNumber = newNumber) })
        }
        isDirty = true
    }

    fun duplicateWeek(weekNumber: Int) {
        val source = weeks.firstOrNull { it.weekNumber == weekNumber } ?: return
        val nextNumber = (weeks.maxOfOrNull { it.weekNumber } ?: 0) + 1
        weeks = weeks + ProgramWeek(
            weekNumber = nextNumber,
            days = source.days.map { ProgramDaySlot(weekNumber = nextNumber, dayNumber = it.dayNumber, workout = it.workout, workoutId = it.workoutId) }
        )
        isDirty = true
    }

    // MARK: - Workout Assignment

    fun openWorkoutPicker(week: Int, day: Int) {
        targetWeekNumber = week
        targetDayNumber = day
        workoutSearchText = ""
        loadAvailableWorkouts()
        showWorkoutPicker = true
    }

    fun assignWorkout(workout: WorkoutList, weekNumber: Int, dayNumber: Int) {
        weeks = weeks.map { week ->
            if (week.weekNumber != weekNumber) week
            else week.copy(days = week.days.map { day ->
                if (day.dayNumber != dayNumber) day else day.copy(workout = workout, workoutId = workout.id.lowercase(Locale.US))
            })
        }
        isDirty = true
    }

    /** "Create New Workout" in the picker: the editor's `onSaved` hands back the id; assign the cached row to the slot. */
    fun assignWorkoutById(workoutId: String, weekNumber: Int, dayNumber: Int) {
        val row = DatabaseManager.workout(workoutId)?.toListRow()
            ?: DatabaseManager.allWorkouts().firstOrNull { it.id.equals(workoutId, ignoreCase = true) }
            ?: WorkoutList(id = workoutId, name = "Workout", time = "", isFavorite = false)
        assignWorkout(row, weekNumber, dayNumber)
    }

    fun removeWorkout(weekNumber: Int, dayNumber: Int) {
        weeks = weeks.map { week ->
            if (week.weekNumber != weekNumber) week
            else week.copy(days = week.days.map { day -> if (day.dayNumber != dayNumber) day else day.copy(workout = null, workoutId = null) })
        }
        isDirty = true
    }

    // MARK: - Move Workout (swap two slots)

    fun moveWorkout(fromWeek: Int, fromDay: Int, toWeek: Int, toDay: Int) {
        if (fromWeek == toWeek && fromDay == toDay) return
        val from = weeks.firstOrNull { it.weekNumber == fromWeek }?.days?.firstOrNull { it.dayNumber == fromDay }
        val to = weeks.firstOrNull { it.weekNumber == toWeek }?.days?.firstOrNull { it.dayNumber == toDay }
        weeks = weeks.map { week ->
            week.copy(days = week.days.map { day ->
                when {
                    week.weekNumber == fromWeek && day.dayNumber == fromDay -> day.copy(workout = to?.workout, workoutId = to?.workoutId)
                    week.weekNumber == toWeek && day.dayNumber == toDay -> day.copy(workout = from?.workout, workoutId = from?.workoutId)
                    else -> day
                }
            })
        }
        isDirty = true
    }

    // MARK: - Helpers

    fun loadAvailableWorkouts() {
        availableWorkouts = DatabaseManager.allWorkouts()
    }
}
