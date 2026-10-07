package com.fitnesslink.fit.progress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.AnglePhoto
import com.fitnesslink.fit.model.BodyPart
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.MeasurementUnit
import com.fitnesslink.fit.model.ModelGson
import com.fitnesslink.fit.model.PhotoAngle
import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.model.ProgressPhotoEntry
import com.fitnesslink.fit.model.WeightEntry
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.CreateMeasurementRequest
import com.fitnesslink.fit.network.dto.CreateProgressPhotoRequest
import com.fitnesslink.fit.network.dto.toMeasurementEntries
import com.fitnesslink.fit.network.dto.toProgressPhotoEntries
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.closestMeasurementEntry
import com.fitnesslink.fit.persistence.closestWeightEntry
import com.fitnesslink.fit.persistence.deleteMeasurementEntryRow
import com.fitnesslink.fit.persistence.deleteProgressPhotoEntryRow
import com.fitnesslink.fit.persistence.deleteWeightEntryRow
import com.fitnesslink.fit.persistence.measurementEntriesCached
import com.fitnesslink.fit.persistence.measurementEntryById
import com.fitnesslink.fit.persistence.pendingEntityIds
import com.fitnesslink.fit.persistence.progressPhotoEntriesCached
import com.fitnesslink.fit.persistence.progressPhotoEntryById
import com.fitnesslink.fit.persistence.weightEntriesCached
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import com.fitnesslink.fit.sync.TransientSyncException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Weight / measurements / progress-photo pipeline — port of iOS
 * `BodyTrackingService` plus the `WeightLogViewModel` / `MeasurementsViewModel`
 * write paths and the `SyncManager.drainWeight` / `drainMeasurements` drains.
 *
 * Wire shapes (see `BodyTrackingApi`): weight is the model itself; the
 * server stores ONE measurement row per (anatomy, value) in centimetres, so a
 * multi-part entry fans out N `POST /measurements` (client-supplied ids make
 * a replay upsert) and reads are grouped back by day; progress photos are
 * one row per angle, uploaded in two hops (`POST /media` → `POST
 * /progress-photos`) and grouped by day on read.
 *
 * Every write is local row first → `sync_queue` row → inline attempt, with
 * the registered drains replaying what didn't land. Photo uploads are online
 * only (bytes aren't queued), as on iOS.
 */
object BodyTrackingStore {
    private const val TAG = "BodyTrackingStore"

    private const val ACTION_CREATE = "create"
    private const val ACTION_DELETE = "delete"
    private const val PHOTO_CONTAINER = "photos"
    private const val MAX_PHOTO_EDGE = 1600

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** Fires after any local weight / measurement / photo change — screens re-read the cache. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    @Volatile private var registered = false
    @Volatile private var appContext: Context? = null

    private val gson get() = ModelGson.instance

    private val userId: String
        get() = runCatching { AuthManager.currentUser.value?.id?.toString() }.getOrNull() ?: ""

    /** Hand the store an application context so [uploadPhotos] can read picker / camera URIs. */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    /** Register the drains once per process. Safe to call repeatedly. */
    fun ensureRegistered() {
        if (registered) return
        registered = true
        SyncManager.registerDrain(SyncDomain.WEIGHT) { drainWeight() }
        SyncManager.registerDrain(SyncDomain.MEASUREMENTS) { drainMeasurements() }
        SyncManager.registerDrain(SyncDomain.PROGRESS_PHOTOS) { drainPhotos() }
    }

    // MARK: - Weight

    fun weightEntries(): List<WeightEntry> = runCatching { DatabaseManager.weightEntriesCached() }.getOrDefault(emptyList())

    /** Drain queued weigh-ins, then reconcile the cache with `GET /weight/me` (pending rows untouched). */
    suspend fun refreshWeight() {
        ensureRegistered()
        if (!NetworkMonitor.isConnected.value) return
        runCatching { SyncManager.drain(SyncDomain.WEIGHT) }
        val remote = try {
            ApiClient.bodyTrackingApi.getWeightEntries()
        } catch (e: Exception) {
            Log.i(TAG, "weight refresh failed — using cache (${e.javaClass.simpleName})"); return
        }
        withContext(Dispatchers.IO) {
            val pending = DatabaseManager.pendingEntityIds(SyncDomain.WEIGHT.wireName)
            val remoteIds = remote.mapTo(HashSet()) { it.id.lowercase(Locale.US) }
            remote.forEach { entry ->
                if (entry.id.lowercase(Locale.US) !in pending) runCatching { DatabaseManager.insertWeightEntry(entry.copy(id = entry.id.lowercase(Locale.US))) }
            }
            DatabaseManager.weightEntriesCached()
                .filter { it.id.lowercase(Locale.US) !in remoteIds && it.id.lowercase(Locale.US) !in pending }
                .forEach { runCatching { DatabaseManager.deleteWeightEntryRow(it.id) } }
        }
        notifyChanged()
    }

    /** iOS `WeightLogViewModel.addEntry`: local insert → queued `POST /weight` with an inline attempt. */
    suspend fun addWeight(entry: WeightEntry) {
        ensureRegistered()
        val stored = entry.copy(id = entry.id.lowercase(Locale.US), userId = entry.userId.ifBlank { userId })
        withContext(Dispatchers.IO) { DatabaseManager.insertWeightEntry(stored) }
        notifyChanged()
        commit(SyncDomain.WEIGHT, ACTION_CREATE, stored.id, gson.toJson(stored)) { ApiClient.bodyTrackingApi.addWeightEntry(stored) }
    }

    /** iOS `WeightLogViewModel.deleteEntry` (no UI there; Android adds one). */
    suspend fun deleteWeight(id: String) {
        ensureRegistered()
        val key = id.lowercase(Locale.US)
        withContext(Dispatchers.IO) { DatabaseManager.deleteWeightEntryRow(key) }
        notifyChanged()
        commit(SyncDomain.WEIGHT, ACTION_DELETE, key, "") { ApiClient.bodyTrackingApi.deleteWeightEntry(key) }
    }

    // MARK: - Measurements

    fun measurementEntries(): List<MeasurementEntry> = runCatching { DatabaseManager.measurementEntriesCached() }.getOrDefault(emptyList())

    fun measurementEntry(id: String): MeasurementEntry? = runCatching { DatabaseManager.measurementEntryById(id) }.getOrNull()

    /**
     * Drain queued entries, warm the anatomy taxonomy, then group
     * `GET /measurements/me` rows by day into entries. A cached entry for the
     * same day that has no pending write is replaced by the server's grouping
     * (its id becomes the first server row id) but keeps its local notes —
     * the server stores none.
     */
    suspend fun refreshMeasurements() {
        ensureRegistered()
        if (!NetworkMonitor.isConnected.value) return
        runCatching { SyncManager.drain(SyncDomain.MEASUREMENTS) }
        warmAnatomy()
        val remoteRows = try {
            ApiClient.bodyTrackingApi.getMeasurements()
        } catch (e: Exception) {
            Log.i(TAG, "measurements refresh failed — using cache (${e.javaClass.simpleName})"); return
        }
        val remote = remoteRows.toMeasurementEntries(::resolvePart)
            .map { entry -> entry.copy(measurements = entry.measurements.map { it.copy(unit = MeasurementUnit.CM) }) }
        withContext(Dispatchers.IO) {
            val pending = DatabaseManager.pendingEntityIds(SyncDomain.MEASUREMENTS.wireName)
            val local = DatabaseManager.measurementEntriesCached()
            val keptDays = HashSet<Long>()
            remote.forEach { entry ->
                val day = ProgramDates.startOfDay(entry.date.time)
                val sameDay = local.filter { ProgramDates.isSameDay(it.date.time, entry.date.time) }
                if (sameDay.any { it.id.lowercase(Locale.US) in pending }) { keptDays.add(day); return@forEach }
                val notes = sameDay.firstOrNull { it.notes.isNotBlank() }?.notes ?: ""
                sameDay.filter { !it.id.equals(entry.id, ignoreCase = true) }.forEach { runCatching { DatabaseManager.deleteMeasurementEntryRow(it.id) } }
                runCatching { DatabaseManager.insertMeasurementEntry(entry.copy(userId = entry.userId.ifBlank { userId }, notes = notes)) }
                keptDays.add(day)
            }
            if (remote.isNotEmpty()) {
                local.filter { ProgramDates.startOfDay(it.date.time) !in keptDays && it.id.lowercase(Locale.US) !in pending }
                    .forEach { runCatching { DatabaseManager.deleteMeasurementEntryRow(it.id) } }
            }
        }
        notifyChanged()
    }

    /**
     * iOS `MeasurementsViewModel.addEntry` + `BodyTrackingService.addMeasurement`:
     * local insert, queued full entry, then one `POST /measurements` per part
     * (anatomy id via the part's candidate names, value in cm, the part's own
     * id as the row id). No resolvable part → transient, stays queued.
     */
    suspend fun addMeasurements(entry: MeasurementEntry) {
        ensureRegistered()
        val stored = entry.copy(
            id = entry.id.lowercase(Locale.US),
            userId = entry.userId.ifBlank { userId },
            measurements = entry.measurements.map { it.copy(id = it.id.lowercase(Locale.US)) }
        )
        withContext(Dispatchers.IO) { DatabaseManager.insertMeasurementEntry(stored) }
        notifyChanged()
        commit(SyncDomain.MEASUREMENTS, ACTION_CREATE, stored.id, gson.toJson(stored)) { postMeasurementRows(stored) }
    }

    /** Delete fans out one `DELETE /measurements/{rowId}` per part; the queue row carries the whole entry. */
    suspend fun deleteMeasurements(entryId: String) {
        ensureRegistered()
        val entry = measurementEntry(entryId) ?: return
        withContext(Dispatchers.IO) { DatabaseManager.deleteMeasurementEntryRow(entry.id) }
        notifyChanged()
        commit(SyncDomain.MEASUREMENTS, ACTION_DELETE, entry.id, gson.toJson(entry)) { deleteMeasurementRows(entry) }
    }

    private suspend fun postMeasurementRows(entry: MeasurementEntry) {
        warmAnatomy()
        val requests = entry.measurements.mapNotNull { m ->
            val anatomyId = anatomyIdFor(m.bodyPart)
            if (anatomyId == null) {
                Log.w(TAG, "no anatomy match for ${m.bodyPart} (candidates ${m.bodyPart.anatomyNameCandidates}) — skipping part"); return@mapNotNull null
            }
            CreateMeasurementRequest(anatomyId = anatomyId, value = m.unit.convert(m.value, MeasurementUnit.CM), logDate = entry.date, id = m.id)
        }
        if (requests.isEmpty()) throw TransientSyncException("anatomy taxonomy cold — no part resolved")
        for (request in requests) ApiClient.bodyTrackingApi.addMeasurement(request)
    }

    private suspend fun deleteMeasurementRows(entry: MeasurementEntry) {
        for (m in entry.measurements) {
            try {
                ApiClient.bodyTrackingApi.deleteMeasurement(m.id)
            } catch (e: Exception) {
                if (!isFinalFailure(e)) throw e
            }
        }
    }

    private suspend fun warmAnatomy() {
        if (ClassificationStore.anatomy.value.isEmpty()) runCatching { ClassificationStore.refresh() }
    }

    /** iOS `ClassificationStore.anatomyId(for:)` over the Android store, with the cached anatomy table as fallback. */
    fun anatomyIdFor(part: BodyPart): String? {
        MeasurementMath.anatomyIdFor(part) { name -> ClassificationStore.anatomyId(name) }?.let { return it }
        val cached = runCatching { DatabaseManager.allAnatomy() }.getOrDefault(emptyList())
        return MeasurementMath.anatomyIdFor(part) { name -> cached.firstOrNull { it.name.equals(name, true) }?.id?.toString() }
    }

    /** iOS `ClassificationStore.bodyPart(for:)`. */
    fun resolvePart(anatomyId: String): BodyPart? {
        val name = ClassificationStore.anatomyName(anatomyId)
            ?: runCatching { DatabaseManager.allAnatomy() }.getOrDefault(emptyList())
                .firstOrNull { it.id.toString().equals(anatomyId, true) }?.name
        return MeasurementMath.bodyPartFor(name)
    }

    // MARK: - Progress photos

    fun photoEntries(): List<ProgressPhotoEntry> = runCatching { DatabaseManager.progressPhotoEntriesCached() }.getOrDefault(emptyList())

    fun photoEntry(id: String): ProgressPhotoEntry? = runCatching { DatabaseManager.progressPhotoEntryById(id) }.getOrNull()

    /**
     * `GET /progress-photos/me` grouped by day. Per-angle and entry notes are
     * local-only (the server keeps notes per row but doesn't return them), so
     * they're carried over by photo id when the day is replaced.
     */
    suspend fun refreshPhotos() {
        ensureRegistered()
        if (!NetworkMonitor.isConnected.value) return
        runCatching { SyncManager.drain(SyncDomain.PROGRESS_PHOTOS) }
        val remote = try {
            ApiClient.bodyTrackingApi.getProgressPhotos().toProgressPhotoEntries()
        } catch (e: Exception) {
            Log.i(TAG, "photos refresh failed — using cache (${e.javaClass.simpleName})"); return
        }
        withContext(Dispatchers.IO) {
            val pending = DatabaseManager.pendingEntityIds(SyncDomain.PROGRESS_PHOTOS.wireName)
            val local = DatabaseManager.progressPhotoEntriesCached()
            val keptDays = HashSet<Long>()
            remote.forEach { entry ->
                val day = ProgramDates.startOfDay(entry.date.time)
                val sameDay = local.filter { ProgramDates.isSameDay(it.date.time, entry.date.time) }
                if (sameDay.any { it.id.lowercase(Locale.US) in pending }) { keptDays.add(day); return@forEach }
                val localPhotos = sameDay.flatMap { it.photos }.associateBy { it.id.lowercase(Locale.US) }
                val merged = entry.copy(
                    userId = entry.userId.ifBlank { userId },
                    notes = sameDay.firstOrNull { it.notes.isNotBlank() }?.notes ?: "",
                    photos = entry.photos.map { p -> p.copy(notes = localPhotos[p.id.lowercase(Locale.US)]?.notes ?: p.notes) }
                )
                sameDay.filter { !it.id.equals(entry.id, ignoreCase = true) }.forEach { runCatching { DatabaseManager.deleteProgressPhotoEntryRow(it.id) } }
                runCatching { DatabaseManager.insertProgressPhotoEntry(merged) }
                keptDays.add(day)
            }
            local.filter { ProgramDates.startOfDay(it.date.time) !in keptDays && it.id.lowercase(Locale.US) !in pending }
                .forEach { runCatching { DatabaseManager.deleteProgressPhotoEntryRow(it.id) } }
        }
        notifyChanged()
    }

    /**
     * iOS `PhotoCaptureViewModel.uploadAndBuildEntry` + `BodyTrackingService.uploadProgressPhoto`:
     * for each captured angle, downscale → `POST /media` (container `photos`)
     * → `POST /progress-photos { imageId, takenAt, notes, angle }`. The entry
     * id is the first created row id so the next `refreshPhotos` matches it.
     * Throws on the first failure (the screen shows the error).
     */
    suspend fun uploadPhotos(
        photos: List<Pair<PhotoAngle, Uri>>,
        takenAt: Date,
        notes: String,
        perAngleNotes: Map<PhotoAngle, String>
    ): ProgressPhotoEntry {
        ensureRegistered()
        val context = appContext ?: throw IllegalStateException("BodyTrackingStore.attach(context) must run before uploading")
        if (!NetworkMonitor.isConnected.value) throw ApiError.Offline
        val ordered = photos.sortedBy { it.first.ordinal }
        val anglePhotos = mutableListOf<AnglePhoto>()
        for ((angle, uri) in ordered) {
            val bytes = withContext(Dispatchers.IO) { readScaledJpeg(context, uri) }
            val fileName = "progress-${angle.displayName.lowercase(Locale.US)}-${UUID.randomUUID()}.jpg"
            val filePart = MultipartBody.Part.createFormData("file", fileName, bytes.toRequestBody("image/jpeg".toMediaType()))
            val containerPart = PHOTO_CONTAINER.toRequestBody("text/plain".toMediaType())
            val mediaId = ApiClient.mediaApi.upload(containerPart, filePart).mediaId.trim().trim('"').lowercase(Locale.US)
            Log.i(TAG, "STEP1 /media ok — mediaId=$mediaId angle=${angle.displayName}")
            val angleNotes = perAngleNotes[angle] ?: ""
            val rowId = ApiClient.bodyTrackingApi.createProgressPhoto(
                CreateProgressPhotoRequest(imageId = mediaId, takenAt = takenAt, notes = angleNotes, angle = angle.displayName)
            ).trim().trim('"').lowercase(Locale.US)
            Log.i(TAG, "STEP2 /progress-photos ok — rowId=$rowId")
            anglePhotos.add(AnglePhoto(id = rowId.ifBlank { UUID.randomUUID().toString() }, angle = angle, imageId = mediaId, notes = angleNotes, createdAt = Date()))
        }
        val entry = ProgressPhotoEntry(
            id = anglePhotos.firstOrNull()?.id ?: UUID.randomUUID().toString().lowercase(Locale.US),
            userId = userId,
            date = takenAt,
            photos = anglePhotos,
            notes = notes,
            createdAt = Date()
        )
        // One card per day: fold any photos already cached for this day into
        // the new entry (the server groups by day too) and drop the old rows.
        val merged = withContext(Dispatchers.IO) {
            val sameDay = DatabaseManager.progressPhotoEntriesCached()
                .filter { ProgramDates.isSameDay(it.date.time, takenAt.time) && !it.id.equals(entry.id, true) }
            sameDay.forEach { stale -> runCatching { DatabaseManager.deleteProgressPhotoEntryRow(stale.id) } }
            val combined = entry.copy(
                photos = (sameDay.flatMap { it.photos } + entry.photos).distinctBy { it.id.lowercase(Locale.US) }.sortedBy { it.angle.ordinal },
                notes = entry.notes.ifBlank { sameDay.firstOrNull { it.notes.isNotBlank() }?.notes ?: "" }
            )
            DatabaseManager.insertProgressPhotoEntry(combined)
            combined
        }
        notifyChanged()
        return merged
    }

    /** Android extra (neither platform had delete UI): one `DELETE /progress-photos/{rowId}` per angle, queued. */
    suspend fun deletePhotoEntry(id: String) {
        ensureRegistered()
        val entry = photoEntry(id) ?: return
        withContext(Dispatchers.IO) { DatabaseManager.deleteProgressPhotoEntryRow(entry.id) }
        notifyChanged()
        commit(SyncDomain.PROGRESS_PHOTOS, ACTION_DELETE, entry.id, gson.toJson(entry.copy(photos = entry.photos.map { it.copy(imageData = null) }))) {
            deletePhotoRows(entry)
        }
    }

    private suspend fun deletePhotoRows(entry: ProgressPhotoEntry) {
        for (photo in entry.photos) {
            try {
                ApiClient.bodyTrackingApi.deleteProgressPhoto(photo.id)
            } catch (e: Exception) {
                if (!isFinalFailure(e)) throw e
            }
        }
    }

    // MARK: - Linked data

    fun closestWeight(date: Date): WeightEntry? = runCatching { DatabaseManager.closestWeightEntry(date) }.getOrNull()

    fun closestMeasurements(date: Date): MeasurementEntry? = runCatching { DatabaseManager.closestMeasurementEntry(date) }.getOrNull()

    // MARK: - Image bytes

    /**
     * Decode a picker / camera URI to a JPEG no larger than [MAX_PHOTO_EDGE]
     * on its long side. `ImageDecoder` (API 28+) applies EXIF orientation;
     * the `BitmapFactory` fallback on 26/27 does not.
     */
    internal fun readScaledJpeg(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                var sample = 1
                while (longest / sample > MAX_PHOTO_EDGE) sample *= 2
                decoder.setTargetSampleSize(sample)
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / sample > MAX_PHOTO_EDGE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IllegalStateException("Couldn't read the selected image")
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    // MARK: - Queue

    /** Enqueue, then try inline (see `CalendarService.commit`). */
    private fun commit(domain: SyncDomain, action: String, entityId: String, payload: String, call: suspend () -> Unit) {
        val queueId = runCatching { DatabaseManager.enqueueSyncEntry(domain.wireName, entityId, action, payload) }.getOrNull() ?: return
        if (!NetworkMonitor.isConnected.value) return
        scope.launch {
            try {
                call()
                DatabaseManager.markSyncEntrySynced(queueId)
            } catch (e: Exception) {
                if (isFinalFailure(e)) {
                    Log.w(TAG, "$action ${domain.wireName} $entityId: final ${e.javaClass.simpleName} — dropping")
                    DatabaseManager.markSyncEntrySynced(queueId)
                } else {
                    Log.i(TAG, "$action ${domain.wireName} $entityId: transient ${e.javaClass.simpleName} — left queued")
                }
            }
        }
    }

    /** iOS semantics: 404 / 403 / other 4xx are final; network, 5xx, 401 and a cold taxonomy are transient. */
    internal fun isFinalFailure(e: Throwable): Boolean = when (e) {
        is TransientSyncException -> false
        is HttpException -> e.code() in 400..499 && e.code() != 401
        is ApiError.Forbidden, is ApiError.NotFound -> true
        is ApiError.ServerError -> e.statusCode in 400..499 && e.statusCode != 401
        else -> false
    }

    /** iOS `drainWeight`: create carries the entry, delete only needs `entityId`. */
    private suspend fun drainWeight() {
        SyncManager.drainQueue(SyncDomain.WEIGHT) { entry ->
            when (entry.action) {
                ACTION_CREATE -> ApiClient.bodyTrackingApi.addWeightEntry(SyncManager.decodePayload<WeightEntry>(entry.payload) ?: throw ApiError.NotFound)
                ACTION_DELETE -> ApiClient.bodyTrackingApi.deleteWeightEntry(entry.entityId)
                else -> Log.w(TAG, "unknown weight queue action '${entry.action}' — dropping")
            }
        }
    }

    /** iOS `drainMeasurements`: warms the taxonomy first; an unresolved part stays queued. */
    private suspend fun drainMeasurements() {
        warmAnatomy()
        if (ClassificationStore.anatomy.value.isEmpty() && runCatching { DatabaseManager.allAnatomy() }.getOrDefault(emptyList()).isEmpty()) return
        SyncManager.drainQueue(SyncDomain.MEASUREMENTS) { entry ->
            val measurementEntry = SyncManager.decodePayload<MeasurementEntry>(entry.payload) ?: throw ApiError.NotFound
            when (entry.action) {
                ACTION_CREATE -> postMeasurementRows(measurementEntry)
                ACTION_DELETE -> deleteMeasurementRows(measurementEntry)
                else -> Log.w(TAG, "unknown measurements queue action '${entry.action}' — dropping")
            }
        }
    }

    private suspend fun drainPhotos() {
        SyncManager.drainQueue(SyncDomain.PROGRESS_PHOTOS) { entry ->
            when (entry.action) {
                ACTION_DELETE -> deletePhotoRows(SyncManager.decodePayload<ProgressPhotoEntry>(entry.payload) ?: throw ApiError.NotFound)
                else -> Log.w(TAG, "unknown progress-photo queue action '${entry.action}' — dropping")
            }
        }
    }

    private fun notifyChanged() { _changes.tryEmit(Unit) }
}
