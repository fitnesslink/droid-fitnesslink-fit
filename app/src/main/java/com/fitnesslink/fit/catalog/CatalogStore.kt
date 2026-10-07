package com.fitnesslink.fit.catalog

import android.util.Log
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.media.InlineMediaURL
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.media.MediaURLProvider
import com.fitnesslink.fit.model.CatalogItem
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.model.api.ContentTypeIds
import com.fitnesslink.fit.model.sortedByCreatedDescending
import com.fitnesslink.fit.movements.MovementStore
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.contributorProgramIdsWithSchedules
import com.fitnesslink.fit.persistence.isTemplate
import com.fitnesslink.fit.persistence.reconcileContributorPrograms
import com.fitnesslink.fit.persistence.reconcileContributorWorkouts
import com.fitnesslink.fit.persistence.reconcileStaleUserPrograms
import com.fitnesslink.fit.persistence.toCatalogItem
import com.fitnesslink.fit.persistence.toListRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The three catalog rails' local slice (iOS `CatalogViewModel` state).
 * Template rails carry rows with a contributor; the My-sections carry
 * user-authored rows (`contributorId == null`), newest first.
 */
data class CatalogSnapshot(
    val programs: List<CatalogItem> = emptyList(),
    val workouts: List<CatalogItem> = emptyList(),
    val movements: List<CatalogItem> = emptyList(),
    val myWorkouts: List<CatalogItem> = emptyList(),
    val myPrograms: List<CatalogItem> = emptyList(),
    val myMovements: List<CatalogItem> = emptyList()
) {
    /** Combined user-authored feed, newest-first across kinds (iOS `myContent`). */
    val myContent: List<CatalogItem> get() = (myWorkouts + myPrograms + myMovements).sortedByCreatedDescending()

    val isEmpty: Boolean get() = programs.isEmpty() && workouts.isEmpty() && movements.isEmpty() && myContent.isEmpty()

    val all: List<CatalogItem> get() = programs + workouts + movements + myWorkouts + myPrograms + myMovements
}

/**
 * Catalog hub data layer (CONTRACTS "Home + Catalog hub"): one refresh that
 * pulls `GET workouts/list-view`, `programs/list-view` and
 * `movements/list-view` in parallel, upserts into SQLite, drops stale
 * contributor / deleted rows, stamps the server ranking, snaps favorites to
 * the server, primes media URLs, and backfills contributor-program
 * schedules so workouts inside a template program stay hidden from the
 * Workouts rail (iOS `CatalogViewModel.refreshFromServer` +
 * `ProgramService.backfillContributorProgramSchedules`).
 *
 * Reads go through [snapshot] so local-only rows (an offline create queued
 * for sync) stay visible — the in-memory rails are never set straight from
 * the server response.
 */
object CatalogStore {
    private const val TAG = "CatalogStore"
    private const val SCHEDULE_BACKFILL_CONCURRENCY = 6

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits after every refresh that touched the local cache. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Workout / program equipment + anatomy tags aren't persisted to SQLite;
     * keep id → lowercased-tag-id maps from the last list-view fetch so the
     * My Content screen's chip strips work (iOS `MyContentViewModel`).
     * Empty until the first online refresh, like the Workouts screen.
     */
    @Volatile var equipmentByContentId: Map<String, Set<String>> = emptyMap()
        private set
    @Volatile var anatomyByContentId: Map<String, Set<String>> = emptyMap()
        private set

    private fun lc(s: String) = s.lowercase(Locale.US)

    private fun online(): Boolean = runCatching { NetworkMonitor.isConnected.value }.getOrDefault(false)

    // MARK: - Refresh

    /**
     * Full catalog refresh. Returns normally when offline (callers keep the
     * cached slice). Each endpoint is independent — one failing leaves the
     * other two applied.
     */
    suspend fun refresh() {
        if (!online()) return
        _isRefreshing.value = true
        try {
            val (remoteWorkouts, remotePrograms) = coroutineScope {
                val w = async { runCatching { ApiClient.workoutApi.listView() }.getOrNull() }
                val p = async { runCatching { ApiClient.programApi.listView() }.getOrNull() }
                // Movements: the movements agent's store already does list-view +
                // reconcile + rank + favorites + inline-URL priming.
                val m = async { runCatching { MovementStore.refreshCatalog() }.getOrNull() }
                m.await()
                w.await() to p.await()
            }

            withContext(Dispatchers.IO) {
                if (remoteWorkouts != null) applyWorkouts(remoteWorkouts)
                if (remotePrograms != null) applyPrograms(remotePrograms)
            }
            hydrateFavorites()
            _changes.tryEmit(Unit)

            // Paint first, then backfill schedules (slow: one call per
            // contributor program) and notify again so the rails re-filter.
            if (remotePrograms != null && backfillContributorProgramSchedules(remotePrograms)) {
                _changes.tryEmit(Unit)
            }
            primeMedia(snapshot().all)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed: ${e.message}")
        } finally {
            _isRefreshing.value = false
        }
    }

    private fun applyWorkouts(remote: List<Workout>) {
        // List-view payloads omit `phases`; the list-row upsert preserves cached exercises.
        remote.forEachIndexed { index, w -> DatabaseManager.upsertWorkoutListRow(w.toListRow(sortIndex = index)) }
        // A successful (even empty) snapshot is authoritative — drop rows the server removed.
        DatabaseManager.reconcileContributorWorkouts(remote.map { it.id })
        DatabaseManager.setWorkoutSortOrder(remote.map { it.id })
        val eq = HashMap(equipmentByContentId); val an = HashMap(anatomyByContentId)
        remote.forEach { w ->
            eq[lc(w.id)] = w.equipmentIds.map { lc(it.toString()) }.toSet()
            an[lc(w.id)] = w.anatomyIds.map { lc(it.toString()) }.toSet()
        }
        equipmentByContentId = eq; anatomyByContentId = an
        primeInline(remote.map { InlineCandidate(it.thumbnailId?.toString(), it.thumbnailUrl, it.imageId?.toString(), it.imageUrl) }, ::workoutRef)
    }

    private fun applyPrograms(remote: List<Program>) {
        remote.forEachIndexed { index, p -> DatabaseManager.upsertProgramListRow(p.toListRow(sortIndex = index)) }
        DatabaseManager.reconcileContributorPrograms(remote.map { it.id })
        DatabaseManager.reconcileStaleUserPrograms(remote.map { it.id })
        DatabaseManager.setProgramSortOrder(remote.map { it.id })
        val eq = HashMap(equipmentByContentId); val an = HashMap(anatomyByContentId)
        remote.forEach { p ->
            eq[lc(p.id)] = p.equipmentIds.map { lc(it.toString()) }.toSet()
            an[lc(p.id)] = p.anatomyIds.map { lc(it.toString()) }.toSet()
        }
        equipmentByContentId = eq; anatomyByContentId = an
        primeInline(remote.map { InlineCandidate(it.thumbnailId?.toString(), it.thumbnailUrl, it.imageId?.toString(), it.imageUrl) }, ::programRef)
    }

    /** Server is authoritative for workout / program favorites — replace, don't merge (movements: `MovementStore`). */
    private suspend fun hydrateFavorites() {
        val favorites = withContext(Dispatchers.IO) { runCatching { ApiClient.contentApi.getFavorites() }.getOrNull() } ?: return
        val workouts = favorites.filter { it.contentTypeId.toString().equals(ContentTypeIds.workout, ignoreCase = true) }.map { it.contentId.toString() }
        val programs = favorites.filter { it.contentTypeId.toString().equals(ContentTypeIds.program, ignoreCase = true) }.map { it.contentId.toString() }
        withContext(Dispatchers.IO) {
            DatabaseManager.replaceFavoriteWorkouts(workouts)
            DatabaseManager.replaceFavoritePrograms(programs)
        }
    }

    /**
     * `program_schedules` is not a sync domain and is otherwise only filled
     * when a program detail opens, so on a fresh install the "hide workouts
     * inside a contributor program" filter has nothing to read. Pull the
     * schedules for contributor programs that have none cached, at most
     * [SCHEDULE_BACKFILL_CONCURRENCY] in flight. Returns true when anything
     * was written.
     */
    private suspend fun backfillContributorProgramSchedules(programs: List<Program>): Boolean {
        val cached = withContext(Dispatchers.IO) { runCatching { DatabaseManager.contributorProgramIdsWithSchedules() }.getOrDefault(emptySet()) }
        val targets = programs.filter { it.contributorId != null && lc(it.id) !in cached }
        if (targets.isEmpty()) return false
        val gate = Semaphore(SCHEDULE_BACKFILL_CONCURRENCY)
        var wrote = false
        coroutineScope {
            targets.map { program ->
                async {
                    gate.withPermit {
                        val schedules = runCatching { ApiClient.programApi.weeklyWorkouts(program.id) }.getOrNull() ?: return@withPermit
                        withContext(Dispatchers.IO) { DatabaseManager.replaceSchedulesForProgram(program.id, schedules) }
                        wrote = true
                    }
                }
            }.forEach { it.await() }
        }
        return wrote
    }

    // MARK: - Local read

    /** Local-DB slice of every rail (iOS `hydrateFromLocal`). Cheap; safe to call on every appear. */
    fun snapshot(): CatalogSnapshot {
        // Workouts inside a contributor (template) program surface through
        // that program, never as standalone rail entries (FTNSSLNK-342).
        val localWorkouts = runCatching { DatabaseManager.catalogVisibleWorkouts() }.getOrDefault(emptyList())
        val localPrograms = runCatching { DatabaseManager.allPrograms() }.getOrDefault(emptyList())
        val localMovements = runCatching { DatabaseManager.allMovements() }.getOrDefault(emptyList())

        val workouts = localWorkouts.filter { it.isTemplate }.map { it.toCatalogItem(anatomyFor(it.id), equipmentFor(it.id)) }
        val programs = localPrograms.filter { it.isTemplate }.map { it.toCatalogItem(anatomyFor(it.id), equipmentFor(it.id)) }
        val movements = localMovements.filter { !it.contributorId.isNullOrEmpty() }.map(::movementItem)

        // Strict split (FTNSSLNK-343): no "treat everything as templates"
        // fallback — an empty templates rail is a legitimate state.
        val myWorkouts = localWorkouts.filter { !it.isTemplate }.map { it.toCatalogItem(anatomyFor(it.id), equipmentFor(it.id)) }.sortedByCreatedDescending()
        val myPrograms = localPrograms.filter { !it.isTemplate }.map { it.toCatalogItem(anatomyFor(it.id), equipmentFor(it.id)) }.sortedByCreatedDescending()
        val myMovements = localMovements.filter { it.contributorId.isNullOrEmpty() }.map(::movementItem).sortedByCreatedDescending()

        return CatalogSnapshot(programs, workouts, movements, myWorkouts, myPrograms, myMovements)
    }

    private fun anatomyFor(id: String): Set<String> = anatomyByContentId[lc(id)].orEmpty()
    private fun equipmentFor(id: String): Set<String> = equipmentByContentId[lc(id)].orEmpty()

    /** "Anatomy • Equipment" caption from the first tag of each (iOS `catalogItem(movement:)`). */
    private fun movementItem(m: MovementLibraryItem): CatalogItem {
        val anatomyName = m.anatomyIds.firstOrNull()?.let { ClassificationStore.anatomyName(it) }.orEmpty()
        val equipmentName = m.equipmentIds.firstOrNull()?.let { ClassificationStore.equipmentName(it) }.orEmpty()
        return CatalogItem(
            id = m.id, title = m.name, kind = CatalogItem.Kind.MOVEMENT,
            caption = listOf(anatomyName, equipmentName).filter { it.isNotEmpty() }.joinToString(" • "),
            imageId = m.imageId, thumbnailId = m.thumbnailId, imageUrl = m.imageUrl, thumbnailUrl = m.thumbnailUrl,
            sharedByUserId = m.sharedByUserId, createdAt = m.createdAt, isFavorite = m.isFavorite,
            anatomyIds = m.anatomyIds.map(::lc).toSet(), equipmentIds = m.equipmentIds.map(::lc).toSet()
        )
    }

    // MARK: - Media

    /**
     * Thumbnail ref for a catalog card — keyed by the media ASSET id
     * (`thumbnailId ?: imageId`), never the entity id (iOS `catalogRef`).
     */
    fun mediaRef(item: CatalogItem): MediaRef? {
        val assetId = item.mediaAssetId ?: return null
        return when (item.kind) {
            CatalogItem.Kind.PROGRAM -> programRef(assetId)
            CatalogItem.Kind.WORKOUT -> workoutRef(assetId)
            CatalogItem.Kind.MOVEMENT -> MediaRef.MovementThumbnail(assetId)
        }
    }

    private fun workoutRef(assetId: String): MediaRef = MediaRef.WorkoutThumbnail(lc(assetId))
    private fun programRef(assetId: String): MediaRef = MediaRef.ProgramThumbnail(lc(assetId))

    private data class InlineCandidate(val thumbnailId: String?, val thumbnailUrl: String?, val imageId: String?, val imageUrl: String?)

    /** Hand the pre-resolved SAS URLs on a list-view payload to the URL cache so cards skip the resolve hop. */
    private fun primeInline(rows: List<InlineCandidate>, ref: (String) -> MediaRef) {
        val urls = ArrayList<InlineMediaURL>()
        for (r in rows) {
            if (r.thumbnailId != null && !r.thumbnailUrl.isNullOrEmpty()) urls.add(InlineMediaURL(ref(r.thumbnailId), r.thumbnailUrl))
            if (r.imageId != null && !r.imageUrl.isNullOrEmpty()) urls.add(InlineMediaURL(ref(r.imageId), r.imageUrl))
        }
        if (urls.isNotEmpty()) runCatching { MediaURLProvider.primeURLs(urls) }
    }

    /**
     * Batch-resolve every catalog thumbnail that isn't already cached in one
     * `/media/resolve` round-trip per 200 refs (iOS `primeMediaURLs`).
     * Without this each card does its own one-ref resolve on first appear.
     */
    suspend fun primeMedia(items: List<CatalogItem>) {
        val refs = items.mapNotNull(::mediaRef).distinct().filter { runCatching { MediaURLProvider.cached(it) }.getOrNull() == null }
        if (refs.isEmpty()) return
        for (chunk in refs.chunked(200)) {
            runCatching { MediaURLProvider.resolve(chunk) }.onFailure { Log.w(TAG, "media prime failed: ${it.message}") }
        }
    }
}
