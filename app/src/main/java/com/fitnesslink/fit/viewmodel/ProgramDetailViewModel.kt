package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.media.MediaPrefetcher
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.model.ProgramWeek
import com.fitnesslink.fit.model.api.ProgramSchedule
import com.fitnesslink.fit.model.api.SharedContent
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.programs.ProgramCTA
import com.fitnesslink.fit.programs.ProgramStore
import com.fitnesslink.fit.programs.ScheduledWorkoutInfo
import com.fitnesslink.fit.ui.workout.ContentTagNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** One movement sampled for the Sneak Peek reel (iOS `MovementPreview`). */
data class MovementPreview(
    val id: String,
    val videoId: String,
    val thumbnailId: String?,
    val imageId: String?,
    val thumbnailUrl: String?,
    val imageUrl: String?,
    val videoUrl: String?
) {
    /** Best-available media id for thumbnail rendering. */
    val displayId: String? get() = thumbnailId ?: imageId
    val displayUrl: String? get() = thumbnailUrl ?: imageUrl
}

/**
 * Program Detail — port of iOS `ProgramDetailViewModel`. Cache first, then
 * `GET /programs/{id}` (live-only fields grafted on), then
 * `GET /programs/{id}/weekly-workouts` with the scheduled workouts hydrated
 * (≤ 4 in flight) so the schedule cards and the Sneak Peek reel have
 * exercise names / movement videos to work with.
 */
class ProgramDetailViewModel : ViewModel() {
    var program by mutableStateOf(Program())
        private set
    /** Share still awaiting accept / decline. While set the program is preview-only (not cached, CTA hidden). */
    var pendingShare by mutableStateOf<SharedContent?>(null)
        private set
    var isRespondingToShare by mutableStateOf(false)
        private set
    var scheduleWeeks by mutableStateOf<List<ProgramWeek>>(emptyList())
        private set
    /** Exercise names + focus area per scheduled workout, keyed by lowercased workout id. */
    var scheduleWorkoutInfo by mutableStateOf<Map<String, ScheduledWorkoutInfo>>(emptyMap())
        private set
    /** 2–5 movements from the program's workouts that ship a demo video. Empty hides the reel. */
    var previewMovements by mutableStateOf<List<MovementPreview>>(emptyList())
        private set
    var cta by mutableStateOf<ProgramCTA>(ProgramCTA.Start)
        private set
    var isLoading by mutableStateOf(false)
        private set

    /** Only the user's own, non-shared programs can be edited / deleted / shared (templates are read-only). */
    val isOwner: Boolean get() = program.contributorId == null && program.sharedByUserId == null
    val isSharedIn: Boolean get() = program.contributorId == null && program.sharedByUserId != null

    val equipmentNames: List<String> get() = ContentTagNames.equipment(program.equipmentIds.map { it.toString() })
    val anatomyNames: List<String> get() = ContentTagNames.anatomy(program.anatomyIds.map { it.toString() })

    private var loadedId: String? = null

    fun loadData(programId: String) {
        loadedId = programId
        program = DatabaseManager.program(programId) ?: Program()
        isLoading = program.name.isEmpty()
        rebuildScheduleWeeks(programId)
        refreshCta(programId)
        publishPreviews()
        viewModelScope.launch {
            try {
                val detail = ProgramStore.fetchDetail(programId) ?: return@launch
                if (loadedId != programId) return@launch
                if (detail.pendingShare != null) {
                    pendingShare = detail.pendingShare
                    program = detail.program
                    return@launch
                }
                pendingShare = null
                program = detail.program
                primeMedia()
                val remote = ProgramStore.fetchSchedules(programId) ?: return@launch
                ProgramStore.hydrateScheduledWorkouts(remote.map { it.workoutId.toString() })
                if (loadedId != programId) return@launch
                withContext(Dispatchers.IO) { rebuildScheduleWeeks(programId) }
                publishPreviews()
                refreshCta(programId)
            } finally {
                if (loadedId == programId) isLoading = false
            }
        }
    }

    /** Re-read the state-dependent bits on every appearance (starting / removing happens on other screens). */
    fun refreshCta(programId: String) {
        cta = ProgramCTA.current(programId)
    }

    /** Re-read the cache when the detail re-appears after the editor pops (keeps live-only fields). */
    fun reloadFromCache(programId: String) {
        if (pendingShare != null) return
        val cached = DatabaseManager.program(programId) ?: return
        program = cached.copy(
            contributor = program.contributor,
            equipmentIds = program.equipmentIds,
            anatomyIds = program.anatomyIds,
            hasExplicitEquipment = program.hasExplicitEquipment,
            hasExplicitAnatomy = program.hasExplicitAnatomy
        )
        rebuildScheduleWeeks(programId)
        publishPreviews()
        refreshCta(programId)
    }

    suspend fun respondToShare(programId: String, accept: Boolean): Boolean {
        val share = pendingShare ?: return true
        isRespondingToShare = true
        try {
            val ok = ProgramStore.respondToShare(programId, share, accept)
            if (!ok) return false
            pendingShare = null
            if (accept) loadData(programId)
            return true
        } finally {
            isRespondingToShare = false
        }
    }

    suspend fun removeFromLibrary(programId: String): Boolean = ProgramStore.removeFromLibrary(programId)

    fun deleteProgram(id: String) {
        viewModelScope.launch { ProgramStore.deleteProgram(id) }
    }

    // MARK: - Schedule

    private fun rebuildScheduleWeeks(programId: String) {
        val schedules: List<ProgramSchedule> = DatabaseManager.schedulesForProgram(programId)
        if (schedules.isEmpty()) {
            scheduleWeeks = emptyList()
            scheduleWorkoutInfo = emptyMap()
            return
        }
        val lookup = DatabaseManager.allWorkouts().associateBy { it.id.lowercase(Locale.US) }
        val maxWeek = schedules.maxOf { it.weekNumber }
        scheduleWeeks = (1..maxWeek).map { w ->
            val week = ProgramWeek.empty(w)
            week.copy(days = week.days.map { day ->
                val schedule = schedules.firstOrNull { it.weekNumber == w && it.dayNumber == day.dayNumber } ?: return@map day
                val key = schedule.workoutId.toString().lowercase(Locale.US)
                day.copy(workoutId = key, workout = lookup[key])
            })
        }
        scheduleWorkoutInfo = ProgramStore.insights(scheduleWeeks.flatMap { it.days }.mapNotNull { it.workoutId })
    }

    // MARK: - Sneak peek

    private fun publishPreviews() {
        previewMovements = computePreviewMovements()
    }

    /**
     * Walk every scheduled workout's tasks for movements with both a video
     * and a thumbnail, dedupe by movement, sample up to five. Hidden under two.
     */
    private fun computePreviewMovements(): List<MovementPreview> {
        val workoutIds = scheduleWeeks.flatMap { it.days }.mapNotNull { it.workoutId }.toSet()
        if (workoutIds.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val candidates = mutableListOf<MovementPreview>()
        for (workoutId in workoutIds) {
            val workout = DatabaseManager.workout(workoutId) ?: continue
            for (task in workout.allTasks) {
                if (!task.isMovement) continue
                val videoId = task.effectiveMovementVideoId?.takeIf { it.isNotEmpty() } ?: continue
                val movementId = task.movementId?.takeIf { it.isNotEmpty() } ?: continue
                if (!seen.add(movementId)) continue
                val thumb = task.movementThumbnailId ?: task.movementImageId
                if (thumb.isNullOrEmpty()) { seen.remove(movementId); continue }
                candidates.add(
                    MovementPreview(
                        id = movementId,
                        videoId = videoId,
                        thumbnailId = task.movementThumbnailId,
                        imageId = task.movementImageId,
                        thumbnailUrl = task.movementThumbnailUrl,
                        imageUrl = task.movementImageUrl,
                        videoUrl = task.effectiveMovementVideoUrl
                    )
                )
            }
        }
        return if (candidates.size >= 2) candidates.shuffled().take(5) else emptyList()
    }

    private fun primeMedia() {
        val refs = buildList<MediaRef> {
            (program.thumbnailId ?: program.imageId)?.let { add(MediaRef.ProgramThumbnail(it.toString())) }
            previewMovements.forEach { p -> p.displayId?.let { add(MediaRef.MovementThumbnail(it)) } }
        }
        if (refs.isEmpty()) return
        viewModelScope.launch { runCatching { MediaPrefetcher.prefetch(refs) } }
    }
}
