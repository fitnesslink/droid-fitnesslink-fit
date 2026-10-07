package com.fitnesslink.fit.programs

import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.model.ProgramList
import com.fitnesslink.fit.model.ProgramWorkoutMeta
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.api.ContentTypeIds
import com.fitnesslink.fit.model.api.ProgramSchedule
import com.fitnesslink.fit.model.api.ShareStatus
import com.fitnesslink.fit.model.api.ShareableContentKind
import com.fitnesslink.fit.model.api.SharedContent
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.SharedContentDto
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.reconcileContributorPrograms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Per-workout extras for schedule rows: ordered exercise names + a focus-area label (iOS `ScheduledWorkoutInfo`). */
data class ScheduledWorkoutInfo(
    val exercises: List<String>,
    val focusArea: String?
)

/**
 * Network + cache plumbing shared by the Programs list, Program Detail,
 * Start Program and My Program (iOS `ProgramService` + the refresh halves
 * of `ProgramsViewModel` / `ProgramDetailViewModel`). Every call is
 * best-effort: offline or on failure the caller keeps its cached rows.
 */
object ProgramStore {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    /** Emits after the catalog cache changes (list refresh, favorite flip, delete). */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private fun lc(s: String) = s.lowercase(Locale.US)

    /** Result of one `/programs/list-view` snapshot. Tag maps are keyed by lowercased program id (not persisted, as on iOS). */
    data class ListSnapshot(
        val programs: List<Program>,
        val equipmentByProgramId: Map<String, Set<String>>,
        val anatomyByProgramId: Map<String, Set<String>>
    )

    /** "See All" shows template content (has a contributor); own programs live in My Content (FTNSSLNK-343). */
    fun templates(rows: List<ProgramList>): List<ProgramList> = rows.filter { it.contributorId != null }

    /** The user's own programs (not templates, not shared-in). */
    fun own(rows: List<ProgramList>): List<ProgramList> = rows.filter { it.contributorId == null && it.sharedByUserId == null }

    /**
     * `GET /programs/list-view`: upsert every row (favorites preserved),
     * drop stale contributor rows, stamp the server ranking, then snap
     * favorites to the server's list. Null offline / on failure.
     */
    suspend fun refreshListView(): ListSnapshot? {
        if (!NetworkMonitor.isConnected.value) return null
        val remote = withContext(Dispatchers.IO) { runCatching { ApiClient.programApi.listView() }.getOrNull() } ?: return null
        withContext(Dispatchers.IO) {
            remote.forEachIndexed { index, p -> DatabaseManager.insertProgram(p.copy(sortIndex = index)) }
            DatabaseManager.reconcileContributorPrograms(remote.map { it.id })
            DatabaseManager.setProgramSortOrder(remote.map { it.id })
        }
        hydrateFavorites()
        _changes.tryEmit(Unit)
        return ListSnapshot(
            programs = remote,
            equipmentByProgramId = remote.associate { p -> lc(p.id) to p.equipmentIds.map { lc(it.toString()) }.toSet() }
                .filterValues { it.isNotEmpty() },
            anatomyByProgramId = remote.associate { p -> lc(p.id) to p.anatomyIds.map { lc(it.toString()) }.toSet() }
                .filterValues { it.isNotEmpty() }
        )
    }

    /** Server is authoritative for favorites — replace, don't merge. */
    suspend fun hydrateFavorites() {
        val favorites = withContext(Dispatchers.IO) { runCatching { ApiClient.contentApi.getFavorites() }.getOrNull() } ?: return
        val ids = favorites
            .filter { it.contentTypeId.toString().equals(ContentTypeIds.program, ignoreCase = true) }
            .map { it.contentId.toString() }
        withContext(Dispatchers.IO) { DatabaseManager.replaceFavoritePrograms(ids) }
    }

    /**
     * `POST /content/favorites/{id}?kind=Program` — returns the server's
     * authoritative state, or null on failure (caller reverts).
     */
    suspend fun toggleFavoriteOnServer(programId: String): Boolean? = withContext(Dispatchers.IO) {
        runCatching { ApiClient.contentApi.toggleFavorite(programId, ShareableContentKind.PROGRAM.serverName).isFavorited }.getOrNull()
    }

    /**
     * `GET /programs/{id}`. When the program is shared-in and still awaiting
     * the user's decision it is NOT cached (preview only) and the pending
     * share is returned alongside. Otherwise the cache row is refreshed and
     * the live-only fields (contributor, effective tag ids, explicit flags)
     * are grafted onto the cache-reconstructed program.
     */
    data class DetailResult(val program: Program, val pendingShare: SharedContent?)

    suspend fun fetchDetail(programId: String): DetailResult? {
        if (!NetworkMonitor.isConnected.value) return null
        val remote = withContext(Dispatchers.IO) { runCatching { ApiClient.programApi.get(programId) }.getOrNull() } ?: return null
        if (remote.isSharedWithMe) {
            val pending = findPendingShare(programId)
            if (pending != null) return DetailResult(remote, pending)
        }
        val merged = withContext(Dispatchers.IO) {
            DatabaseManager.insertProgram(remote)
            (DatabaseManager.program(programId) ?: remote).copy(
                contributor = remote.contributor,
                equipmentIds = remote.equipmentIds,
                anatomyIds = remote.anatomyIds,
                hasExplicitEquipment = remote.hasExplicitEquipment,
                hasExplicitAnatomy = remote.hasExplicitAnatomy,
                isSharedWithMe = remote.isSharedWithMe,
                sharedByUserId = remote.sharedByUserId,
                imageUrl = remote.imageUrl,
                thumbnailUrl = remote.thumbnailUrl
            )
        }
        return DetailResult(merged, null)
    }

    /** `GET /programs/{id}/weekly-workouts` → `program_schedules` (replace). Null offline / on failure. */
    suspend fun fetchSchedules(programId: String, persist: Boolean = true): List<ProgramSchedule>? {
        if (!NetworkMonitor.isConnected.value) return null
        val remote = withContext(Dispatchers.IO) { runCatching { ApiClient.programApi.weeklyWorkouts(programId) }.getOrNull() } ?: return null
        if (persist) withContext(Dispatchers.IO) { DatabaseManager.replaceSchedulesForProgram(programId, remote) }
        return remote
    }

    /**
     * Pull the full `Workout` for every scheduled id whose cached copy is
     * missing or shallow (no phases), at most [maxInflight] requests at a
     * time (iOS `hydrateScheduledWorkouts`).
     */
    suspend fun hydrateScheduledWorkouts(workoutIds: Collection<String>, maxInflight: Int = 4) {
        if (!NetworkMonitor.isConnected.value) return
        val needs = withContext(Dispatchers.IO) {
            workoutIds.map { lc(it) }.distinct().filter { id ->
                val cached = DatabaseManager.workout(id)
                cached == null || cached.phases.isEmpty()
            }
        }
        if (needs.isEmpty()) return
        val gate = Semaphore(maxInflight)
        coroutineScope {
            for (id in needs) {
                launch(Dispatchers.IO) {
                    gate.withPermit {
                        val workout = runCatching { ApiClient.workoutApi.get(id) }.getOrNull() ?: return@withPermit
                        DatabaseManager.insertWorkout(workout.hydrateGroupRounds())
                    }
                }
            }
        }
    }

    /** Fetch + cache one workout (My Program row metadata for workouts this device never opened). */
    suspend fun fetchWorkout(workoutId: String): Workout? {
        if (!NetworkMonitor.isConnected.value) return null
        return withContext(Dispatchers.IO) {
            runCatching { ApiClient.workoutApi.get(workoutId) }.getOrNull()?.hydrateGroupRounds()?.also { DatabaseManager.insertWorkout(it) }
        }
    }

    // MARK: Sharing

    suspend fun findPendingShare(programId: String): SharedContent? = withContext(Dispatchers.IO) {
        val rows = runCatching { ApiClient.contentApi.getSharedContent() }.getOrNull() ?: return@withContext null
        rows.firstOrNull { it.contentId.equals(programId, true) && it.isPending }?.toModel()
    }

    /** Recipient's decision. Accept → caller reloads like any library item; decline → stale local copy dropped. False on failure. */
    suspend fun respondToShare(programId: String, share: SharedContent, accept: Boolean): Boolean {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                if (accept) ApiClient.contentApi.acceptShare(share.id.toString())
                else ApiClient.contentApi.declineShare(share.id.toString())
            }.isSuccess
        }
        if (ok && !accept) withContext(Dispatchers.IO) { DatabaseManager.deleteProgram(programId) }
        return ok
    }

    /**
     * Recipient removes a program shared *with* them: break the share on
     * the server, then drop the local row. No program DELETE is queued —
     * this user doesn't own it. False on server failure.
     */
    suspend fun removeFromLibrary(programId: String): Boolean {
        val shareId = withContext(Dispatchers.IO) {
            runCatching { ApiClient.contentApi.getSharedContent() }.getOrNull()
                ?.firstOrNull { it.contentId.equals(programId, true) && it.status != com.fitnesslink.fit.network.dto.ShareStatus.REMOVED }
                ?.id
        }
        val ok = withContext(Dispatchers.IO) { runCatching { ApiClient.contentApi.removeShare(shareId ?: programId) }.isSuccess }
        if (!ok) return false
        withContext(Dispatchers.IO) { DatabaseManager.deleteProgram(programId) }
        _changes.tryEmit(Unit)
        return true
    }

    /** Owner delete: local row + schedule now, `DELETE /programs/{id}` best-effort. */
    suspend fun deleteProgram(programId: String) {
        withContext(Dispatchers.IO) { DatabaseManager.deleteProgram(programId) }
        _changes.tryEmit(Unit)
        if (!NetworkMonitor.isConnected.value) return
        withContext(Dispatchers.IO) { runCatching { ApiClient.programApi.delete(programId) } }
    }

    private fun SharedContentDto.toModel(): SharedContent = SharedContent(
        id = uuidOrZero(id),
        sharedByUserId = uuidOrZero(sharedByUserId),
        sharedByUserName = sharedByUserName,
        sharedWithUserId = uuidOrZero(sharedWithUserId),
        kind = ShareableContentKind.fromWire(kind?.serverName) ?: ShareableContentKind.PROGRAM,
        contentId = uuidOrZero(contentId),
        contentTitle = contentTitle,
        imageId = imageId?.let { runCatching { UUID.fromString(it) }.getOrNull() },
        sharedAtUtc = sharedAtUtc ?: Date(),
        status = ShareStatus.PENDING
    )

    private fun uuidOrZero(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrDefault(UUID(0L, 0L))

    // MARK: Workout insights (schedule cards + My Program rows)

    /**
     * Ordered unique exercise names + the most common anatomy across the
     * workout's movements (ties break alphabetically so the label is stable).
     */
    fun insights(workout: Workout): ScheduledWorkoutInfo {
        val seen = LinkedHashSet<String>()
        val anatomyCounts = HashMap<String, Int>()
        for (task in workout.allTasks) {
            if (!task.isMovement || task.name.isEmpty()) continue
            seen.add(task.name)
            val movementId = task.movementId?.takeIf { it.isNotEmpty() } ?: continue
            for (anatomyId in DatabaseManager.anatomyIds(movementId)) {
                val name = ClassificationStore.anatomyName(anatomyId) ?: continue
                anatomyCounts[name] = (anatomyCounts[name] ?: 0) + 1
            }
        }
        val focus = anatomyCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key
        return ScheduledWorkoutInfo(exercises = seen.toList(), focusArea = focus)
    }

    /** Insights for every cached workout in [workoutIds], keyed by lowercased id (uncached ids are omitted). */
    fun insights(workoutIds: Collection<String>): Map<String, ScheduledWorkoutInfo> =
        workoutIds.map { lc(it) }.distinct().mapNotNull { id ->
            DatabaseManager.workout(id)?.let { id to insights(it) }
        }.toMap()

    /** What a Week Plan / Home row shows about a workout beyond its title (iOS `ProgramWorkoutMeta.load`). */
    fun workoutMeta(workoutId: String): ProgramWorkoutMeta? {
        val workout = DatabaseManager.workout(lc(workoutId)) ?: return null
        val info = insights(workout)
        // EstimatedTime is seconds everywhere.
        val duration = workout.time.ifEmpty {
            workout.estimatedTime?.takeIf { it > 0 }?.let { "${maxOf(it / 60, 1)} min" } ?: ""
        }.ifEmpty { null }
        return ProgramWorkoutMeta(duration = duration, exerciseCount = info.exercises.size, focus = info.focusArea)
    }
}
