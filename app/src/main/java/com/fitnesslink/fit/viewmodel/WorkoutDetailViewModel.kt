package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.media.MediaPrefetcher
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.api.ShareStatus
import com.fitnesslink.fit.model.api.ShareableContentKind
import com.fitnesslink.fit.model.api.SharedContent
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.SharedContentDto
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.session.ActiveWorkoutSession
import com.fitnesslink.fit.session.InProgressSessionEntry
import com.fitnesslink.fit.workouts.WorkoutPhaseSummary
import com.fitnesslink.fit.workouts.WorkoutRowOps
import com.fitnesslink.fit.workouts.WorkoutSaveEvent
import com.fitnesslink.fit.workouts.WorkoutStore
import com.fitnesslink.fit.workouts.WorkoutStructure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Port of iOS `WorkoutDetailViewModel`. */
class WorkoutDetailViewModel : ViewModel() {
    var workout by mutableStateOf(Workout())
        private set
    /** True while the workout isn't cached and the network fetch is still out. */
    var isLoading by mutableStateOf(false)
        private set
    /** The unfinished session for this workout, for the resume card; null → Start Now. */
    var inProgressSession by mutableStateOf<InProgressSessionEntry?>(null)
        private set
    val hasSession: Boolean get() = inProgressSession != null
    /** Share awaiting the user's accept / decline. While set, the workout is a network-only preview. */
    var pendingShare by mutableStateOf<SharedContent?>(null)
        private set
    var isRespondingToShare by mutableStateOf(false)
        private set

    private var hasLoaded = false
    private var currentWorkoutId: String? = null

    init {
        WorkoutStore.ensureRegistered()
        viewModelScope.launch {
            WorkoutStore.events.collect { event ->
                val id = currentWorkoutId ?: return@collect
                when (event) {
                    is WorkoutSaveEvent.SaveDidCommit -> if (event.workoutId.equals(id, true)) loadData(id)
                    is WorkoutSaveEvent.CreateDidApply -> if (event.serverId.equals(id, true) || event.localId.equals(id, true)) loadData(event.serverId)
                    is WorkoutSaveEvent.Deleted -> Unit
                }
            }
        }
    }

    /** "Equipment Needed" names from the effective ids, falling back to the server-computed names. */
    val equipmentNames: List<String>
        get() {
            val fromIds = workout.equipmentIds.mapNotNull { ClassificationStore.equipmentName(it.toString()) }.sorted()
            return fromIds.ifEmpty { workout.equipment }
        }

    val phaseSummaries: List<WorkoutPhaseSummary> get() = WorkoutStructure.summaries(workout.phases)

    val exerciseCount: Int get() = workout.allTasks.count { it.isMovement }

    /** Owner actions (edit / delete / share) only for user-authored, non-shared workouts. */
    val isEditable: Boolean get() = WorkoutRowOps.isEditable(workout)

    fun loadData(workoutId: String) {
        currentWorkoutId = workoutId
        val cached = DatabaseManager.workout(workoutId) ?: Workout()
        workout = cached
        hasLoaded = true
        isLoading = cached.name.isEmpty()
        refreshHasSession(workoutId)
        primeMedia(cached)
        viewModelScope.launch {
            try {
                if (!NetworkMonitor.isConnected.value) return@launch
                val remote = withContext(Dispatchers.IO) { runCatching { ApiClient.workoutApi.get(workoutId) }.getOrNull() }
                    ?.hydrateGroupRounds() ?: return@launch
                if (remote.isSharedWithMe) {
                    val pending = withContext(Dispatchers.IO) { findPendingShare(workoutId) }
                    if (pending != null) {
                        pendingShare = pending
                        workout = remote
                        primeMedia(remote)
                        return@launch
                    }
                }
                pendingShare = null
                val merged = WorkoutRowOps.mergePreservingPhases(
                    local = cached, server = remote,
                    preservingLocalPhaseOrder = WorkoutRowOps.isEditable(remote)
                )
                withContext(Dispatchers.IO) { DatabaseManager.insertWorkout(merged) }
                val refreshed = (DatabaseManager.workout(workoutId) ?: workout).copy(
                    // Live-only fields the cache doesn't hold.
                    contributor = remote.contributor,
                    equipmentIds = remote.equipmentIds,
                    anatomyIds = remote.anatomyIds,
                    hasExplicitEquipment = remote.hasExplicitEquipment,
                    hasExplicitAnatomy = remote.hasExplicitAnatomy,
                    isSharedWithMe = remote.isSharedWithMe,
                    sharedByUserId = remote.sharedByUserId ?: workout.sharedByUserId
                )
                workout = refreshed
                primeMedia(refreshed)
            } finally {
                isLoading = false
            }
        }
    }

    /** Re-read the cache (keeping live-only fields) when the detail re-appears after the editor pops. */
    fun reloadFromCache(workoutId: String) {
        if (!hasLoaded || pendingShare != null) return
        val cached = DatabaseManager.workout(workoutId) ?: return
        workout = cached.copy(
            contributor = workout.contributor,
            equipmentIds = workout.equipmentIds,
            anatomyIds = workout.anatomyIds,
            hasExplicitEquipment = workout.hasExplicitEquipment,
            hasExplicitAnatomy = workout.hasExplicitAnatomy
        )
        primeMedia(cached)
    }

    fun refreshHasSession(workoutId: String) {
        inProgressSession = runCatching { ActiveWorkoutSession.inProgressEntry(workoutId) }.getOrNull()
    }

    /** "Restart from beginning": abandon the unfinished session so the next Start mints a fresh one. */
    fun restartFromBeginning(workoutId: String, onDone: () -> Unit = {}) {
        val entry = inProgressSession ?: return onDone()
        viewModelScope.launch {
            runCatching { ActiveWorkoutSession.discard(entry.sessionId) }
            refreshHasSession(workoutId)
            onDone()
        }
    }

    /** "Discard session": the unfinished session is removed; the workout is untouched. */
    fun discardSession(workoutId: String) {
        val entry = inProgressSession ?: return
        viewModelScope.launch {
            runCatching { ActiveWorkoutSession.discard(entry.sessionId) }
            refreshHasSession(workoutId)
        }
    }

    private fun primeMedia(w: Workout) {
        val refs = mutableListOf<MediaRef>()
        (w.thumbnailId ?: w.imageId)?.let { refs.add(MediaRef.WorkoutThumbnail(it.toString())) }
        for (task in w.allTasks) {
            (task.movementThumbnailId ?: task.movementImageId)?.let { refs.add(MediaRef.MovementThumbnail(it)) }
            task.effectiveMovementVideoId?.let { refs.add(MediaRef.MovementVideo(it)) }
        }
        if (refs.isEmpty()) return
        viewModelScope.launch { runCatching { MediaPrefetcher.prefetch(refs) } }
    }

    private suspend fun findPendingShare(workoutId: String): SharedContent? {
        val rows = runCatching { ApiClient.contentApi.getSharedContent() }.getOrNull() ?: return null
        return rows.firstOrNull { it.contentId.equals(workoutId, true) && it.isPending }?.toModel()
    }

    private fun SharedContentDto.toModel(): SharedContent = SharedContent(
        id = uuidOrZero(id),
        sharedByUserId = uuidOrZero(sharedByUserId),
        sharedByUserName = sharedByUserName,
        sharedWithUserId = uuidOrZero(sharedWithUserId),
        kind = ShareableContentKind.fromWire(kind?.serverName) ?: ShareableContentKind.WORKOUT,
        contentId = uuidOrZero(contentId),
        contentTitle = contentTitle,
        imageId = imageId?.let { runCatching { UUID.fromString(it) }.getOrNull() },
        sharedAtUtc = sharedAtUtc ?: Date(),
        status = ShareStatus.PENDING
    )

    private fun uuidOrZero(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrDefault(UUID(0L, 0L))

    /** Recipient's decision on a pending share. Returns false on a server failure so the banner stays. */
    suspend fun respondToShare(workoutId: String, accept: Boolean): Boolean {
        val share = pendingShare ?: return true
        isRespondingToShare = true
        try {
            withContext(Dispatchers.IO) {
                if (accept) ApiClient.contentApi.acceptShare(share.id.toString())
                else ApiClient.contentApi.declineShare(share.id.toString())
            }
            pendingShare = null
            if (accept) loadData(workoutId) else DatabaseManager.deleteWorkout(workoutId)
            return true
        } catch (_: Exception) {
            return false
        } finally {
            isRespondingToShare = false
        }
    }

    /** Recipient removes a shared-in workout from their library. Local-only delete — no workout DELETE is queued. */
    suspend fun removeFromLibrary(workoutId: String): Boolean {
        val shareId = withContext(Dispatchers.IO) {
            runCatching { ApiClient.contentApi.getSharedContent() }.getOrNull()
                ?.firstOrNull { it.contentId.equals(workoutId, true) && it.status != com.fitnesslink.fit.network.dto.ShareStatus.REMOVED }
                ?.id
        }
        try {
            withContext(Dispatchers.IO) { ApiClient.contentApi.removeShare(shareId ?: workoutId) }
        } catch (_: Exception) {
            return false
        }
        DatabaseManager.deleteWorkout(workoutId)
        return true
    }

    fun deleteWorkout(workoutId: String) {
        viewModelScope.launch { WorkoutStore.deleteWorkout(workoutId) }
    }

    /** Customize → fork-to-edit. Throws so the caller can surface `WorkoutStore.forkFailureMessage`. */
    suspend fun customize(workoutId: String): Workout = withContext(Dispatchers.IO) { WorkoutStore.customizeFork(workoutId) }

    /** Lower-cased id helper for callers comparing routes. */
    fun matches(id: String): Boolean = workout.id.lowercase(Locale.US) == id.lowercase(Locale.US)
}
