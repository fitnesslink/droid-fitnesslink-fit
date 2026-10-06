package com.fitnesslink.fit.media

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Observable lifecycle of one media upload. */
sealed class UploadState {
    /** Work enqueued; no bytes sent yet (also while waiting for network). */
    data object Queued : UploadState()

    /** Bytes flowing. `fraction` is 0…1 of upload bytes only. */
    data class Uploading(val fraction: Float) : UploadState()

    /** Server accepted the file; Mux ingest / transcode in progress. */
    data class Processing(val mediaId: String, val muxStatus: MuxStatus) : UploadState()

    /** Mux finished — the asset is playable. */
    data class Ready(val mediaId: String, val playbackId: String?, val videoUrl: String?) : UploadState()

    /** Upload or Mux ingest errored, or the record is gone. */
    data class Failed(val message: String, val mediaId: String? = null) : UploadState()

    val isTerminal: Boolean get() = this is Ready || this is Failed
}

/** What the coordinator remembers about an upload between launches. */
data class UploadRecord(
    val uploadId: String,
    val fileName: String,
    val container: String,
    val createdAtMs: Long
)

/**
 * Facade over the WorkManager upload chain (`MediaUploadWorker` →
 * `MuxStatusWorker`). Stages the source into app-private storage so the
 * transfer no longer depends on a content-URI grant, enqueues the chain as
 * unique work keyed by uploadId, and exposes live state as a Flow built
 * from WorkInfo (progress + output data). WorkManager persists the chain, so
 * an app kill mid-upload resumes on next launch.
 *
 * Port of iOS `BackgroundUploadCoordinator` (+ its `MuxStatusPoller` hand-off).
 */
object MediaUploadCoordinator {
    private const val PREFS = "fl_media_uploads"
    private const val KEY_RECORDS = "media.background-uploads.v1"
    private const val STAGING_DIR = "media-upload"

    @Volatile
    private var appContext: Context? = null
    private val gson = Gson()
    private val lock = Any()

    internal fun attach(context: Context) {
        appContext = context.applicationContext
    }

    private fun ctx(): Context = appContext ?: MediaLayer.requireContext()
    private fun workManager() = WorkManager.getInstance(ctx())

    // MARK: - Public API

    /**
     * Begin an upload. The source is copied into app-private storage first
     * (so the picker grant can lapse), then the chain is enqueued. Returns
     * the uploadId to pass to `observe`. Throws only for staging errors.
     */
    suspend fun enqueue(
        fileUri: Uri,
        container: String = "videoassets",
        fileName: String? = null,
        mimeType: String? = null
    ): String = withContext(Dispatchers.IO) {
        val context = ctx()
        val uploadId = UUID.randomUUID().toString()
        val resolvedMime = mimeType
            ?: context.contentResolver.getType(fileUri)
            ?: guessMime(fileName ?: fileUri.lastPathSegment.orEmpty())
            ?: "application/octet-stream"
        val resolvedName = fileName
            ?: fileUri.lastPathSegment?.substringAfterLast('/')?.ifEmpty { null }
            ?: "$uploadId.${MimeTypeMap.getSingleton().getExtensionFromMimeType(resolvedMime) ?: "bin"}"

        val staged = stage(context, fileUri, uploadId)
        enqueueStaged(staged, uploadId, resolvedName, resolvedMime, container)
        uploadId
    }

    /** Variant for a file already on disk that we own (e.g. camera capture). */
    suspend fun enqueue(
        file: File,
        container: String = "videoassets",
        fileName: String = file.name,
        mimeType: String = guessMime(file.name) ?: "application/octet-stream"
    ): String = withContext(Dispatchers.IO) {
        val uploadId = UUID.randomUUID().toString()
        val staged = stagingFile(ctx(), uploadId)
        file.copyTo(staged, overwrite = true)
        enqueueStaged(staged, uploadId, fileName, mimeType, container)
        uploadId
    }

    /**
     * Live state for an upload. Emits `Failed` for an unknown id so a
     * watcher resumed after relaunch doesn't hang on a record WorkManager
     * has already pruned.
     */
    fun observe(uploadId: String): Flow<UploadState> =
        workManager().getWorkInfosForUniqueWorkFlow(uploadId)
            .map { infos -> stateFrom(infos) }
            .distinctUntilChanged()

    /** Current snapshot, or null if WorkManager has no record. */
    suspend fun current(uploadId: String): UploadState? {
        val infos = try {
            withContext(Dispatchers.IO) { workManager().getWorkInfosForUniqueWork(uploadId).get() }
        } catch (_: Exception) {
            return null
        }
        if (infos.isNullOrEmpty()) return null
        return stateFrom(infos)
    }

    /** Uploads this install has started, newest first. */
    fun records(): List<UploadRecord> = loadRecords().sortedByDescending { it.createdAtMs }

    /**
     * Drop a record (and cancel its chain if still running) so a "My
     * Videos"-style list doesn't grow forever.
     */
    fun remove(uploadId: String) {
        try { workManager().cancelUniqueWork(uploadId) } catch (_: Exception) {}
        stagingFile(ctx(), uploadId).delete()
        saveRecords(loadRecords().filterNot { it.uploadId == uploadId })
    }

    // MARK: - Internals

    private fun enqueueStaged(staged: File, uploadId: String, fileName: String, mimeType: String, container: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val upload = OneTimeWorkRequestBuilder<MediaUploadWorker>()
            .setInputData(MediaUploadWorker.inputData(uploadId, staged.absolutePath, fileName, mimeType, container))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(MediaUploadWorker.TAG)
            .addTag(uploadTag(uploadId))
            .build()

        val poll = OneTimeWorkRequestBuilder<MuxStatusWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(MuxStatusWorker.TAG)
            .addTag(uploadTag(uploadId))
            .build()

        workManager()
            .beginUniqueWork(uploadId, ExistingWorkPolicy.KEEP, upload)
            .then(poll)
            .enqueue()

        saveRecords(loadRecords() + UploadRecord(uploadId, fileName, container, System.currentTimeMillis()))
    }

    private fun stateFrom(infos: List<WorkInfo>): UploadState {
        if (infos.isEmpty()) return UploadState.Failed("upload record not found")
        val upload = infos.firstOrNull { it.tags.contains(MediaUploadWorker.TAG) }
        val poll = infos.firstOrNull { it.tags.contains(MuxStatusWorker.TAG) }

        upload?.let { u ->
            when (u.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> return UploadState.Queued
                WorkInfo.State.RUNNING -> {
                    val pct = u.progress.getFloat(MediaUploadWorker.KEY_PROGRESS, 0f)
                    return UploadState.Uploading(pct)
                }
                WorkInfo.State.FAILED -> return UploadState.Failed(
                    u.outputData.getString(MediaUploadWorker.KEY_ERROR) ?: "upload failed"
                )
                WorkInfo.State.CANCELLED -> return UploadState.Failed("upload cancelled")
                WorkInfo.State.SUCCEEDED -> Unit // fall through to the poll link
            }
        }

        val mediaId = poll?.outputData?.getString(MediaUploadWorker.KEY_MEDIA_ID)
            ?: poll?.progress?.getString(MediaUploadWorker.KEY_MEDIA_ID)
            ?: upload?.outputData?.getString(MediaUploadWorker.KEY_MEDIA_ID)

        if (poll == null) {
            return if (mediaId != null) UploadState.Processing(mediaId, MuxStatus.PREPARING) else UploadState.Queued
        }
        return when (poll.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> {
                val status = MuxStatus.fromWire(poll.progress.getString(MuxStatusWorker.KEY_MUX_STATUS))
                UploadState.Processing(
                    mediaId ?: "",
                    if (status == MuxStatus.UNKNOWN) MuxStatus.PREPARING else status
                )
            }
            WorkInfo.State.SUCCEEDED -> UploadState.Ready(
                mediaId = mediaId ?: "",
                playbackId = poll.outputData.getString(MuxStatusWorker.KEY_PLAYBACK_ID),
                videoUrl = poll.outputData.getString(MuxStatusWorker.KEY_VIDEO_URL)
            )
            WorkInfo.State.FAILED -> UploadState.Failed(
                poll.outputData.getString(MediaUploadWorker.KEY_ERROR) ?: "Mux ingest failed",
                mediaId
            )
            WorkInfo.State.CANCELLED -> UploadState.Failed("upload cancelled", mediaId)
        }
    }

    private fun uploadTag(uploadId: String) = "upload:$uploadId"

    private fun stagingFile(context: Context, uploadId: String): File {
        val dir = File(context.filesDir, STAGING_DIR).apply { mkdirs() }
        return File(dir, "$uploadId.bin")
    }

    private fun stage(context: Context, uri: Uri, uploadId: String): File {
        val dest = stagingFile(context, uploadId)
        if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).copyTo(dest, overwrite = true)
            return dest
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open $uri")
        input.use { source -> dest.outputStream().use { out -> source.copyTo(out) } }
        return dest
    }

    private fun guessMime(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    // MARK: - Record persistence

    private fun prefs() = ctx().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadRecords(): List<UploadRecord> = synchronized(lock) {
        val raw = prefs().getString(KEY_RECORDS, null) ?: return emptyList()
        try {
            val type = object : TypeToken<List<UploadRecord>>() {}.type
            gson.fromJson<List<UploadRecord>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveRecords(records: List<UploadRecord>) = synchronized(lock) {
        prefs().edit().putString(KEY_RECORDS, gson.toJson(records)).apply()
    }
}
