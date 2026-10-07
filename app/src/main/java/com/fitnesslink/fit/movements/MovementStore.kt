package com.fitnesslink.fit.movements

import android.content.Context
import android.net.Uri
import android.util.Log
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.media.InlineMediaURL
import com.fitnesslink.fit.media.MediaPrefetcher
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.media.MediaURLProvider
import com.fitnesslink.fit.media.MovementVideoAttachmentCoordinator
import com.fitnesslink.fit.model.ModelGson
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.api.Movement
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.CreateMovementRequest
import com.fitnesslink.fit.network.dto.ShareStatus
import com.fitnesslink.fit.network.dto.ShareableContentKind
import com.fitnesslink.fit.network.dto.SharedContentDto
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.cacheMovementDetail
import com.fitnesslink.fit.persistence.cachedMovementDetail
import com.fitnesslink.fit.persistence.deleteMovementDetail
import com.fitnesslink.fit.persistence.enqueuePendingMovementCreate
import com.fitnesslink.fit.persistence.ensureMovementSchema
import com.fitnesslink.fit.persistence.pendingMovementCreates
import com.fitnesslink.fit.persistence.reconcileContributorMovements
import com.fitnesslink.fit.persistence.rekeyMovement
import com.fitnesslink.fit.persistence.removePendingMovementCreate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Movement library coordinator (iOS spreads this across `MovementService`,
 * `ExerciseBrowserViewModel`, `MovementsViewModel`, `MovementDetailView` and
 * `FormVideoUploadModel.commitCreate`). One place for:
 *
 * - server refresh of the catalog / browser slices (`GET /movements/list-view`)
 *   that upserts the local cache, primes inline media URLs, and stamps the
 *   server's catalog ranking;
 * - favorites + recents (`GET /movements/favorites|recents`, optimistic
 *   `POST|DELETE /movements/{id}/favorite`, fire-and-forget `POST /movements/{id}/recent`);
 * - detail hydration (`GET /movements/{id}`) with an offline JSON cache;
 * - share plumbing for shared-in movements (pending accept/decline, remove);
 * - the create pipeline (`POST /movements` → bare GUID → deferred video attach),
 *   with a best-effort retry ledger for creates that failed offline. iOS does
 *   not queue movement creates (no `SyncDomain`), so this stays inline rather
 *   than registering a `SyncManager` drain.
 *
 * [changes] fires after any local write so view models re-hydrate from SQLite.
 */
object MovementStore {
    private const val TAG = "MovementStore"
    const val STILL_IMAGE_CONTAINER = "photos"
    const val FORM_VIDEO_CONTAINER = "movementvideos" // iOS FormVideoSlot uses the movementvideos container for form videos

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson by lazy { ModelGson.instance }

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits after every local movement write (favorite flip, refresh, create, delete). */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    @Volatile private var registered = false
    private var retryJob: Job? = null

    /**
     * One-time wiring: creates the side tables and starts the offline-create
     * retry watcher. Idempotent; call from any entry point.
     */
    fun ensureRegistered() {
        if (registered) return
        synchronized(this) {
            if (registered) return
            registered = true
            runCatching { DatabaseManager.ensureMovementSchema() }
                .onFailure { Log.w(TAG, "schema init failed: ${it.message}") }
            retryJob = scope.launch {
                runCatching {
                    NetworkMonitor.isConnected.filter { it }.collect { retryPendingCreates() }
                }
            }
        }
    }

    private fun notifyChanged() { _changes.tryEmit(Unit) }

    private fun online(): Boolean = runCatching { NetworkMonitor.isConnected.value }.getOrDefault(false)

    // MARK: - List-view refresh

    /**
     * `GET /movements/list-view` for [query]; upserts every row into the local
     * cache (preserving local favorites / rank / createdAt), primes inline
     * media URLs. Returns null when offline or the request failed so callers
     * keep showing the cached slice.
     */
    suspend fun refreshListView(query: MovementListQuery): List<Movement>? {
        ensureRegistered()
        if (!online()) return null
        val p = query.toParams()
        return try {
            val remote = ApiClient.movementApi.listView(
                query = p.query,
                anatomyIds = p.anatomyIds,
                equipmentIds = p.equipmentIds,
                role = p.role,
                difficultyIds = p.difficultyIds,
                goalIds = p.goalIds,
                movementPatternIds = p.movementPatternIds,
                locationIds = p.locationIds,
                contributorId = p.contributorId,
                personalOnly = p.personalOnly,
                favoritesOnly = p.favoritesOnly
            )
            withContext(Dispatchers.IO) { remote.forEach { cacheListRow(it) } }
            primeInlineUrls(remote)
            notifyChanged()
            remote
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "list-view refresh failed: ${e.message}")
            null
        }
    }

    /**
     * Unfiltered catalog snapshot (iOS `MovementsViewModel.refreshFromServer`):
     * list-view + drop stale contributor rows + stamp the server ranking + snap
     * favorites to the server's list.
     */
    suspend fun refreshCatalog(): List<Movement>? {
        val remote = refreshListView(MovementListQuery()) ?: return null
        withContext(Dispatchers.IO) {
            val ids = remote.map { it.id.toString() }
            runCatching { DatabaseManager.reconcileContributorMovements(ids) }
            runCatching { DatabaseManager.setMovementSortOrder(ids) }
        }
        runCatching { ApiClient.movementApi.getFavorites() }
            .onSuccess { favs -> withContext(Dispatchers.IO) { DatabaseManager.replaceFavoriteMovements(favs.map { it.movementId }) } }
        notifyChanged()
        return remote
    }

    /** Pull the server-owned favorites + recents and snap local state to them (replace, not merge). */
    suspend fun refreshFavoritesAndRecents() {
        ensureRegistered()
        if (!online()) return
        coroutineScope {
            val favorites = async { runCatching { ApiClient.movementApi.getFavorites() }.getOrNull() }
            val recents = async { runCatching { ApiClient.movementApi.getRecents() }.getOrNull() }
            val f = favorites.await()
            val r = recents.await()
            withContext(Dispatchers.IO) {
                f?.let { DatabaseManager.replaceFavoriteMovements(it.map { row -> row.movementId }) }
                r?.let { DatabaseManager.replaceRecentMovements(it.map { row -> row.movementId to row.accessedAt.time }) }
            }
        }
        notifyChanged()
    }

    // MARK: - Favorites / recents

    /**
     * Optimistic favorite flip: local SQLite first, server call in the
     * background; a transient server failure is NOT rolled back (the next
     * refresh reconciles). Returns the new local state.
     */
    fun toggleFavorite(movementId: String, wasFavorited: Boolean): Boolean {
        ensureRegistered()
        val now = !wasFavorited
        runCatching { DatabaseManager.setMovementFavorite(movementId, now) }
        notifyChanged()
        scope.launch {
            try {
                if (now) ApiClient.movementApi.favorite(movementId) else ApiClient.movementApi.unfavorite(movementId)
            } catch (e: Exception) {
                Log.w(TAG, "favorite toggle sync failed for $movementId: ${e.message}")
            }
        }
        return now
    }

    /** "I just used this" — local recents row now, fire-and-forget `POST /movements/{id}/recent`. */
    fun recordRecent(movementId: String) {
        ensureRegistered()
        runCatching { DatabaseManager.logRecentMovement(movementId) }
        notifyChanged()
        if (!online()) return
        scope.launch {
            try { ApiClient.movementApi.recordRecent(movementId) } catch (e: Exception) {
                Log.d(TAG, "recent ping failed for $movementId: ${e.message}")
            }
        }
    }

    // MARK: - Detail

    /** Cached full payload (last `GET /movements/{id}`), or null when never fetched. */
    fun cachedDetail(movementId: String): Movement? =
        runCatching { DatabaseManager.cachedMovementDetail(movementId) }.getOrNull()

    /**
     * `GET /movements/{id}`; caches the payload (list row + detail JSON) unless
     * [cacheResult] is false (a shared-in movement still awaiting accept must
     * not enter the library cache). Returns null offline / on failure.
     */
    suspend fun hydrate(movementId: String, cacheResult: Boolean = true): Movement? {
        ensureRegistered()
        if (!online()) return null
        return try {
            val full = ApiClient.movementApi.get(movementId)
            if (cacheResult) cacheFull(full)
            primeInlineUrls(listOf(full))
            full
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "hydrate $movementId failed: ${e.message}")
            null
        }
    }

    /** Write a hydrated movement into the list cache + detail cache. */
    suspend fun cacheFull(full: Movement) = withContext(Dispatchers.IO) {
        DatabaseManager.insertMovement(full)
        DatabaseManager.cacheMovementDetail(full)
        notifyChanged()
    }

    /** Warm the on-disk video cache for the ids a detail/preview surface will play. */
    fun prefetchVideos(videoIds: Collection<String>) {
        val refs = videoIds.filter { it.isNotBlank() }.distinct().map { MediaRef.MovementVideo(it.lowercase(Locale.US)) }
        if (refs.isEmpty()) return
        scope.launch { runCatching { MediaPrefetcher.prefetch(refs) } }
    }

    // MARK: - Sharing

    /** The share of this movement to the current user that is still Pending, if any. */
    suspend fun pendingShare(movementId: String): SharedContentDto? {
        if (!online()) return null
        return runCatching { ApiClient.contentApi.getSharedContent() }.getOrNull()
            ?.firstOrNull {
                it.kind == ShareableContentKind.MOVEMENT &&
                    it.contentId.equals(movementId, ignoreCase = true) &&
                    it.status == ShareStatus.PENDING
            }
    }

    suspend fun acceptShare(shareId: String) {
        ApiClient.contentApi.acceptShare(shareId)
    }

    suspend fun declineShare(shareId: String, movementId: String) {
        ApiClient.contentApi.declineShare(shareId)
        withContext(Dispatchers.IO) { deleteLocal(movementId) }
    }

    /**
     * Recipient removes a movement shared *with* them: breaks the share on the
     * server (owner's copy untouched), then drops the local rows. Throws when
     * the server call fails so the UI can show "Couldn't remove".
     */
    suspend fun removeFromLibrary(movementId: String) {
        val shares = ApiClient.contentApi.getSharedContent()
        val share = shares.firstOrNull {
            it.kind == ShareableContentKind.MOVEMENT && it.contentId.equals(movementId, ignoreCase = true) &&
                it.status != ShareStatus.REMOVED
        } ?: throw IllegalStateException("No share found for movement $movementId")
        ApiClient.contentApi.removeShare(share.id)
        withContext(Dispatchers.IO) { deleteLocal(movementId) }
    }

    private fun deleteLocal(movementId: String) {
        runCatching { DatabaseManager.deleteMovement(movementId) }
        runCatching { DatabaseManager.deleteMovementDetail(movementId) }
        notifyChanged()
    }

    // MARK: - Create pipeline

    data class CreateInput(
        val name: String,
        val description: String,
        val anatomyIds: List<String>,
        val equipmentIds: List<String>,
        /** MediaAsset id of the still image; rides the create as both `imageId` and `thumbnailId`. */
        val imageId: String? = null,
        /** `MediaUploadCoordinator` upload id of the form video, attached once Ready. */
        val uploadId: String? = null
    )

    data class CreateResult(val movementId: String, val createdOnServer: Boolean, val item: MovementLibraryItem)

    /**
     * Upload the picked still image (`POST /media`, container `photos`) and
     * return the MediaAsset id. Small images go through the shared Retrofit
     * client synchronously at Create time (iOS `MediaService.upload`), so a
     * swipe-dismissed form never leaves an orphaned asset behind. Throws on failure.
     */
    suspend fun uploadStillImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Couldn't read the selected image")
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("heic") || mime.contains("heif") -> "heic"
            else -> "jpg"
        }
        val fileName = "movement-${UUID.randomUUID()}.$ext"
        val filePart = MultipartBody.Part.createFormData("file", fileName, bytes.toRequestBody(mime.toMediaType()))
        val containerPart = STILL_IMAGE_CONTAINER.toRequestBody("text/plain".toMediaType())
        val response = ApiClient.mediaApi.upload(containerPart, filePart)
        response.mediaId.trim().trim('"').lowercase(Locale.US)
    }

    /**
     * Shared create logic (iOS `FormVideoUploadModel.commitCreate`): persist on
     * the server when online so the row is owned by the user and comes back
     * from `list-view` everywhere; register the deferred form-video attach
     * (the create endpoint carries no video ref); write the local cache row.
     * Falls back to a local-only id when offline or the create fails and
     * records a retry so the row can be re-keyed later.
     */
    suspend fun create(input: CreateInput): CreateResult {
        ensureRegistered()
        var movementId = UUID.randomUUID().toString().lowercase(Locale.US)
        var createdOnServer = false
        val request = buildCreateRequest(input)

        if (online()) {
            try {
                movementId = normalizeGuid(ApiClient.movementApi.create(request))
                createdOnServer = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "create failed, keeping local-only: ${e.message}")
            }
        }

        if (createdOnServer && input.uploadId != null) {
            runCatching { MovementVideoAttachmentCoordinator.attach(movementId, input.uploadId) }
                .onFailure { Log.w(TAG, "video attach registration failed: ${it.message}") }
        }

        withContext(Dispatchers.IO) {
            DatabaseManager.insertMovementFull(
                id = movementId, name = input.name, description = input.description,
                anatomyIds = input.anatomyIds, equipmentIds = input.equipmentIds,
                imageId = input.imageId, thumbnailId = input.imageId, contributorId = null,
                createdAt = Date()
            )
            if (!createdOnServer) {
                DatabaseManager.enqueuePendingMovementCreate(movementId, gson.toJson(request), input.uploadId)
            }
        }
        notifyChanged()

        val item = MovementLibraryItem(
            id = movementId, name = input.name, description = input.description,
            anatomyIds = input.anatomyIds.map { it.lowercase(Locale.US) },
            equipmentIds = input.equipmentIds.map { it.lowercase(Locale.US) },
            isFavorite = false, imageId = input.imageId, thumbnailId = input.imageId,
            contributorId = null, createdAt = Date()
        )
        return CreateResult(movementId, createdOnServer, item)
    }

    internal fun buildCreateRequest(input: CreateInput): CreateMovementRequest = CreateMovementRequest(
        name = input.name.trim(),
        description = input.description.trim().ifEmpty { null },
        contributorId = null,
        statusId = ClassificationStore.contentStatusId("Active"),
        anatomyIds = input.anatomyIds.map { it.lowercase(Locale.US) },
        equipmentIds = input.equipmentIds.map { it.lowercase(Locale.US) },
        imageId = input.imageId?.lowercase(Locale.US),
        thumbnailId = input.imageId?.lowercase(Locale.US)
    )

    /**
     * Re-attempt creates that failed offline. On success the local row is
     * re-keyed to the server id and the video attach (if any) registered. A
     * definitive 4xx drops the ledger row; anything else leaves it for next time.
     */
    suspend fun retryPendingCreates() {
        if (!online()) return
        val pending = runCatching { DatabaseManager.pendingMovementCreates() }.getOrNull() ?: return
        for (row in pending) {
            val request = runCatching { gson.fromJson(row.payloadJson, CreateMovementRequest::class.java) }.getOrNull()
            if (request == null) {
                DatabaseManager.removePendingMovementCreate(row.localId)
                continue
            }
            try {
                val serverId = normalizeGuid(ApiClient.movementApi.create(request))
                withContext(Dispatchers.IO) {
                    DatabaseManager.rekeyMovement(row.localId, serverId)
                    DatabaseManager.removePendingMovementCreate(row.localId)
                }
                row.uploadId?.let { runCatching { MovementVideoAttachmentCoordinator.attach(serverId, it) } }
                notifyChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() in 400..499 && e.code() != 401) {
                    Log.w(TAG, "pending create ${row.localId} rejected (${e.code()}) — dropping")
                    DatabaseManager.removePendingMovementCreate(row.localId)
                } else {
                    return
                }
            } catch (e: Exception) {
                return
            }
        }
    }

    // MARK: - Helpers

    private fun normalizeGuid(raw: String): String = raw.trim().trim('"').lowercase(Locale.US)

    /** Upsert a list-view row without touching local favorites / rank (iOS `insertMovementFull`). */
    private fun cacheListRow(m: Movement) {
        DatabaseManager.insertMovementFull(
            id = m.id.toString(), name = m.name, description = m.description ?: "",
            anatomyIds = m.anatomyIds.map { it.toString() }, equipmentIds = m.equipmentIds.map { it.toString() },
            imageId = m.imageId?.toString(), thumbnailId = m.thumbnailId?.toString(), videoId = m.videoId?.toString(),
            contributorId = m.contributorId?.toString(), sharedByUserId = m.sharedByUserId?.toString(),
            phoneVideoId = m.phoneVideoId?.toString(), createdAt = m.audit?.timestamp, summary = m.summary,
            statusId = m.statusId?.toString()
        )
        if (m.isFavorite == true) DatabaseManager.setMovementFavorite(m.id.toString(), true)
    }

    /** Hand pre-resolved SAS / Mux URLs to the URL cache so thumbnails render without a resolve hop. */
    private fun primeInlineUrls(rows: List<Movement>) {
        val urls = ArrayList<InlineMediaURL>()
        for (m in rows) {
            m.thumbnailId?.let { id -> m.thumbnailUrl?.let { urls.add(InlineMediaURL(MediaRef.MovementThumbnail(id.toString()), it)) } }
            m.imageId?.let { id -> m.imageUrl?.let { urls.add(InlineMediaURL(MediaRef.MovementThumbnail(id.toString()), it)) } }
            m.videoId?.let { id -> m.videoUrl?.let { urls.add(InlineMediaURL(MediaRef.MovementVideo(id.toString()), it, videoStatus = m.videoStatus.toMediaStatus())) } }
            m.phoneVideoId?.let { id -> m.phoneVideoUrl?.let { urls.add(InlineMediaURL(MediaRef.MovementVideo(id.toString()), it, videoStatus = m.phoneVideoStatus.toMediaStatus())) } }
        }
        if (urls.isNotEmpty()) runCatching { MediaURLProvider.primeURLs(urls) }
    }

    /** Thumbnail ref for a library row — the asset id (`thumbnailId ?: imageId`), iOS semantics. */
    fun thumbnailRef(item: MovementLibraryItem): MediaRef? =
        (item.thumbnailId ?: item.imageId)?.let { MediaRef.MovementThumbnail(it) }

    fun thumbnailRef(movement: Movement?): MediaRef? =
        (movement?.thumbnailId ?: movement?.imageId)?.let { MediaRef.MovementThumbnail(it.toString()) }
}

/**
 * The API model (`model.api.MuxStatus`) and the media layer (`media.MuxStatus`)
 * each declare the backend enum; the player and URL cache take the media one.
 */
fun com.fitnesslink.fit.model.api.MuxStatus?.toMediaStatus(): com.fitnesslink.fit.media.MuxStatus? =
    this?.let { com.fitnesslink.fit.media.MuxStatus.fromWire(it.wire) }
