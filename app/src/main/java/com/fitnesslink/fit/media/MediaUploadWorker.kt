package com.fitnesslink.fit.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.network.ApiConfiguration
import com.fitnesslink.fit.network.ApiService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that performs one multipart `POST media` from a file
 * the coordinator staged on disk. Runs on its own OkHttp client (no body
 * logging, long write timeout) and streams the file through a progress-
 * reporting `RequestBody` so `setProgress` reflects bytes actually sent.
 *
 * Output: `KEY_MEDIA_ID` (+ echo of the request fields) so the chained
 * `MuxStatusWorker` can pick up polling. Port of the transfer half of iOS
 * `BackgroundUploadCoordinator`; persistence across relaunch is WorkManager's.
 */
class MediaUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uploadId = inputData.getString(KEY_UPLOAD_ID) ?: return@withContext fail("missing uploadId")
        val path = inputData.getString(KEY_FILE_PATH) ?: return@withContext fail("missing file path")
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "upload.bin"
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "application/octet-stream"
        val container = inputData.getString(KEY_CONTAINER) ?: "videoassets"

        val file = File(path)
        if (!file.exists()) return@withContext fail("staged file missing: $path")

        setProgress(workDataOf(KEY_PROGRESS to 0f, KEY_UPLOAD_ID to uploadId))

        val token = try { AuthManager.getIdToken() } catch (_: Exception) { null }
            ?: return@withContext if (runAttemptCount < 3) Result.retry() else fail("not signed in")

        val total = file.length()
        var lastReported = -1
        val body = ProgressFileRequestBody(file, mimeType.toMediaTypeOrNull()) { sent ->
            val pct = if (total > 0) ((sent * 100) / total).toInt().coerceIn(0, 100) else 0
            if (pct != lastReported) {
                lastReported = pct
                // setProgress is suspend; we're inside writeTo (blocking), so
                // hand off to the async variant.
                setProgressAsync(workDataOf(KEY_PROGRESS to pct / 100f, KEY_UPLOAD_ID to uploadId))
            }
        }

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("container", container)
            .addFormDataPart("file", fileName, body)
            .build()

        val request = Request.Builder()
            .url(ApiConfiguration.baseUrl(ApiService.CORE).trimEnd('/') + "/media")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(multipart)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // 5xx / 429 are worth a retry; 4xx are terminal.
                    return@withContext if (response.code >= 500 || response.code == 429) {
                        if (runAttemptCount < 3) Result.retry() else fail("HTTP ${response.code}: $text")
                    } else {
                        fail("HTTP ${response.code}: $text")
                    }
                }
                val parsed = try {
                    gson.fromJson(text, MediaUploadResponse::class.java)
                } catch (e: Exception) {
                    return@withContext fail("decode failed: ${e.message}")
                }
                if (parsed?.mediaId.isNullOrEmpty()) return@withContext fail("decode failed: no mediaId")

                file.delete()
                setProgress(workDataOf(KEY_PROGRESS to 1f, KEY_UPLOAD_ID to uploadId))
                Result.success(
                    workDataOf(
                        KEY_UPLOAD_ID to uploadId,
                        KEY_MEDIA_ID to parsed!!.mediaId,
                        KEY_FILE_NAME to fileName,
                        KEY_CONTAINER to container
                    )
                )
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else fail(e.message ?: "upload failed")
        }
    }

    private fun fail(message: String): Result =
        Result.failure(workDataOf(KEY_ERROR to message, KEY_UPLOAD_ID to inputData.getString(KEY_UPLOAD_ID)))

    /** Streams a file into the sink, reporting cumulative bytes written. */
    private class ProgressFileRequestBody(
        private val file: File,
        private val mediaType: MediaType?,
        private val onProgress: (Long) -> Unit
    ) : RequestBody() {
        override fun contentType(): MediaType? = mediaType
        override fun contentLength(): Long = file.length()
        /** One-shot so interceptors (e.g. body logging) never buffer the video in memory. */
        override fun isOneShot(): Boolean = true

        override fun writeTo(sink: BufferedSink) {
            file.source().use { source ->
                var sent = 0L
                val chunk = 256L * 1024
                while (true) {
                    val read = source.read(sink.buffer, chunk)
                    if (read == -1L) break
                    sent += read
                    sink.emitCompleteSegments()
                    onProgress(sent)
                }
                sink.flush()
            }
        }
    }

    companion object {
        const val TAG = "fl-media-upload"
        const val KEY_UPLOAD_ID = "uploadId"
        const val KEY_FILE_PATH = "filePath"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_MIME_TYPE = "mimeType"
        const val KEY_CONTAINER = "container"
        const val KEY_PROGRESS = "progress"
        const val KEY_MEDIA_ID = "mediaId"
        const val KEY_ERROR = "error"

        private val gson = Gson()
        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(600, TimeUnit.SECONDS)
                .build()
        }

        fun inputData(uploadId: String, filePath: String, fileName: String, mimeType: String, container: String): Data =
            workDataOf(
                KEY_UPLOAD_ID to uploadId,
                KEY_FILE_PATH to filePath,
                KEY_FILE_NAME to fileName,
                KEY_MIME_TYPE to mimeType,
                KEY_CONTAINER to container
            )
    }
}

/**
 * Second link in the upload chain: polls `GET media/{id}` through
 * `MuxStatusPoller` until Mux is terminal. Stays under WorkManager's 10-min
 * worker budget per run (≈20 attempts) and asks for a retry if still
 * non-terminal, so a slow ingest keeps being watched across runs — and
 * across app relaunches, since the chain is persisted.
 */
class MuxStatusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mediaId = inputData.getString(MediaUploadWorker.KEY_MEDIA_ID)
            ?: return Result.failure(workDataOf(MediaUploadWorker.KEY_ERROR to "missing mediaId"))
        val uploadId = inputData.getString(MediaUploadWorker.KEY_UPLOAD_ID)

        // Resume further up the backoff ladder on later runs.
        val start = if (runAttemptCount == 0) 0 else 6
        val outcome = MuxStatusPoller.poll(mediaId, maxAttempts = start + 20, startAttempt = start) { detail ->
            setProgress(
                workDataOf(
                    MediaUploadWorker.KEY_UPLOAD_ID to uploadId,
                    MediaUploadWorker.KEY_MEDIA_ID to mediaId,
                    KEY_MUX_STATUS to detail.muxStatusEnum.wire
                )
            )
        }

        return when (outcome) {
            is MuxPollOutcome.Ready -> Result.success(
                workDataOf(
                    MediaUploadWorker.KEY_UPLOAD_ID to uploadId,
                    MediaUploadWorker.KEY_MEDIA_ID to mediaId,
                    KEY_MUX_STATUS to MuxStatus.READY.wire,
                    KEY_PLAYBACK_ID to outcome.playbackId,
                    KEY_VIDEO_URL to outcome.detail.videoUrl
                )
            )
            is MuxPollOutcome.Failed -> Result.failure(
                workDataOf(
                    MediaUploadWorker.KEY_UPLOAD_ID to uploadId,
                    MediaUploadWorker.KEY_MEDIA_ID to mediaId,
                    KEY_MUX_STATUS to outcome.status.wire,
                    MediaUploadWorker.KEY_ERROR to outcome.message
                )
            )
            is MuxPollOutcome.TimedOut ->
                if (runAttemptCount < 2) Result.retry()
                else Result.failure(
                    workDataOf(
                        MediaUploadWorker.KEY_UPLOAD_ID to uploadId,
                        MediaUploadWorker.KEY_MEDIA_ID to mediaId,
                        KEY_MUX_STATUS to outcome.status.wire,
                        MediaUploadWorker.KEY_ERROR to "Still processing after ${MuxStatusPoller.DEFAULT_MAX_ATTEMPTS} checks"
                    )
                )
        }
    }

    companion object {
        const val TAG = "fl-mux-poll"
        const val KEY_MUX_STATUS = "muxStatus"
        const val KEY_PLAYBACK_ID = "playbackId"
        const val KEY_VIDEO_URL = "videoUrl"
    }
}
