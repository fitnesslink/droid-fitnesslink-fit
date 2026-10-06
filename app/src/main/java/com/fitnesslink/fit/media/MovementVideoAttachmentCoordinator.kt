package com.fitnesslink.fit.media

import android.content.Context
import com.fitnesslink.fit.network.ApiClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * FTNSSLNK-225: bridges a background form-video upload to the movement it
 * belongs to. When a user attaches a video while creating a movement they
 * may hit "Create" before Mux finishes ingesting — the movement row is
 * written first (create carries no video ref), then the video is stitched
 * on with a PUT once the upload reaches `Ready`.
 *
 * Owns that deferred stitch so it survives the create sheet being dismissed.
 * Each job is (movementId, uploadId); we watch the upload until it flips to
 * `Ready` (attach) or `Failed` (drop — we NEVER attach a half-ingested /
 * broken asset). Jobs persist in SharedPreferences so a relaunch mid-ingest
 * resumes the stitch instead of silently dropping the video.
 *
 * Port of iOS `MovementVideoAttachmentCoordinator`.
 */
object MovementVideoAttachmentCoordinator {
    private const val PREFS = "fl_movement_video_attachments"
    private const val KEY_JOBS = "movement.video.attachments.v1"

    /** ~15 min, matching the iOS tick budget (450 × 2 s). */
    private const val WATCH_TIMEOUT_MS = 15L * 60 * 1000

    data class Job(val movementId: String, val uploadId: String)

    @Volatile
    private var appContext: Context? = null
    private val gson = Gson()
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watching = HashMap<String, kotlinx.coroutines.Job>()

    internal fun attach(context: Context) {
        appContext = context.applicationContext
        resumeAll()
    }

    private fun ctx(): Context = appContext ?: MediaLayer.requireContext()

    /** Pending stitch jobs keyed by uploadId. */
    fun jobs(): Map<String, Job> = loadJobs()

    /**
     * Register a movement ⇄ upload pairing. If the upload is already `Ready`
     * the stitch runs immediately; otherwise it's deferred until ingest
     * finishes. Safe to call more than once for the same upload.
     */
    fun attach(movementId: String, uploadId: String) {
        val job = Job(movementId, uploadId)
        saveJobs(loadJobs() + (uploadId to job))
        watch(job)
    }

    // MARK: - Watching

    private fun resumeAll() {
        for (job in loadJobs().values) watch(job)
    }

    private fun watch(job: Job) {
        synchronized(lock) {
            if (watching[job.uploadId]?.isActive == true) return
            watching[job.uploadId] = scope.launch { run(job) }
        }
    }

    private suspend fun run(job: Job) {
        try {
            val terminal = withTimeoutOrNull(WATCH_TIMEOUT_MS) {
                MediaUploadCoordinator.observe(job.uploadId).first { it.isTerminal }
            }
            when (terminal) {
                is UploadState.Ready -> {
                    if (terminal.mediaId.isNotEmpty()) attachVideo(job.movementId, terminal.mediaId)
                }
                is UploadState.Failed, null -> {
                    // Ingest/upload failed, record evicted, or the watch
                    // budget ran out — leave the movement with no video rather
                    // than pointing it at a broken asset.
                }
                else -> Unit
            }
        } finally {
            finish(job)
        }
    }

    /**
     * PUT the movement with the resolved video ref. Re-fetch first so we
     * don't clobber server-side fields written since create.
     */
    private suspend fun attachVideo(movementId: String, mediaAssetId: String) {
        val videoId = try { UUID.fromString(mediaAssetId) } catch (_: Exception) { return }
        try {
            val movement = ApiClient.movementApi.get(movementId)
            ApiClient.movementApi.update(movementId, movement.copy(videoId = videoId))
        } catch (_: Exception) {
            // Job is dropped; the video simply isn't attached. An edit-movement
            // flow can re-attach later.
        }
    }

    private fun finish(job: Job) {
        synchronized(lock) { watching.remove(job.uploadId) }
        saveJobs(loadJobs() - job.uploadId)
    }

    // MARK: - Persistence

    private fun prefs() = ctx().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadJobs(): Map<String, Job> = synchronized(lock) {
        val raw = prefs().getString(KEY_JOBS, null) ?: return emptyMap()
        try {
            val type = object : TypeToken<List<Job>>() {}.type
            (gson.fromJson<List<Job>>(raw, type) ?: emptyList()).associateBy { it.uploadId }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveJobs(jobs: Map<String, Job>) = synchronized(lock) {
        prefs().edit().putString(KEY_JOBS, gson.toJson(jobs.values.toList())).apply()
    }
}
