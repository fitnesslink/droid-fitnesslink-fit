package com.fitnesslink.fit.media

import android.content.Context
import java.io.File

/**
 * Local on-disk store for prefetched media bytes. Files are keyed by the
 * stable entity ID, never the SAS URL, so a SAS rotation never invalidates
 * a cached byte. Port of iOS `MediaCacheStore` (minus the retired AVAsset
 * HLS-bundle registry, which never existed on Android).
 *
 * Layout under `cacheDir/MediaCache/`:
 *  - `{folder}/{entityId}.img`                      single-file image cache
 *  - `movement-video-mp4/{entityId}.{playbackId}.mp4` offline movement videos
 *
 * The Mux playback id in the video file name is the staleness marker: a
 * re-ingest (e.g. a source re-upload) issues a new playback id for the same
 * entity id, so `hasCurrentVideoFile` fails and the prefetcher re-downloads.
 */
object MediaCacheStore {
    private const val VIDEO_FOLDER = "movement-video-mp4"
    private const val RETIRED_VIDEO_FOLDER = "movement-video"
    private const val DEFAULT_GRACE_MS = 7L * 24 * 60 * 60 * 1000

    @Volatile
    private var rootDirOrNull: File? = null

    private val rootDir: File
        get() = rootDirOrNull ?: error("MediaCacheStore.initialize(context) has not been called")

    /** Kept for `FitnessLinkApp`; routes through the shared media-layer init. */
    fun initialize(context: Context) = MediaLayer.initialize(context)

    internal fun attach(context: Context) {
        rootDirOrNull = File(context.cacheDir, "MediaCache").apply { mkdirs() }
    }

    val isInitialized: Boolean get() = rootDirOrNull != null

    // MARK: - Lookup

    /**
     * Returns the local file if a prefetched copy exists for this ref, else
     * null. Movement videos resolve to their downloaded MP4 (whatever
     * playback id it was stamped with); everything else to its single file.
     */
    fun localFile(ref: MediaRef): File? {
        if (!isInitialized) return null
        if (ref is MediaRef.MovementVideo) return localVideoFile(ref)
        val f = pathFor(ref)
        return if (f.exists()) f else null
    }

    /**
     * Single-file path the prefetcher writes images to. Not used for movement
     * videos — see `storeVideoFile`.
     */
    fun pathFor(ref: MediaRef): File {
        require(ref !is MediaRef.MovementVideo) { "Movement videos use the playback-id-stamped video cache" }
        val folder = File(rootDir, folderName(ref)).apply { mkdirs() }
        return File(folder, "${ref.entityId}.${ref.fileExtensionHint}")
    }

    // MARK: - Offline movement videos

    private val videoFolder: File
        get() = File(rootDir, VIDEO_FOLDER).apply { mkdirs() }

    private fun videoFiles(entityId: String): List<File> {
        val prefix = "$entityId."
        return videoFolder.listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".mp4") }
            ?.toList() ?: emptyList()
    }

    private fun videoFile(entityId: String, playbackId: String): File =
        File(videoFolder, "$entityId.$playbackId.mp4")

    /** The downloaded MP4 for this movement video, if any playback id is on disk. */
    fun localVideoFile(ref: MediaRef): File? = localVideoFile(ref.entityId)

    fun localVideoFile(entityId: String): File? {
        if (!isInitialized) return null
        return videoFiles(entityId).firstOrNull()
    }

    /** Playback id the on-disk MP4 was downloaded from, if one exists. */
    fun cachedVideoPlaybackId(entityId: String): String? {
        val file = localVideoFile(entityId) ?: return null
        val parts = file.name.removeSuffix(".mp4").split('.')
        return parts.getOrNull(1)?.ifEmpty { null }
    }

    /** True when the MP4 on disk for this ref was downloaded from the same Mux playback id. */
    fun hasCurrentVideoFile(ref: MediaRef, playbackId: String): Boolean =
        hasCurrentVideoFile(ref.entityId, playbackId)

    fun hasCurrentVideoFile(entityId: String, playbackId: String): Boolean {
        if (!isInitialized) return false
        return videoFile(entityId, playbackId).exists()
    }

    /**
     * Moves a finished download into the cache, replacing any copy from an
     * earlier playback id. `tempFile` must be on the same filesystem (the
     * prefetcher writes its `.tmp` beside the destination).
     */
    @Throws(java.io.IOException::class)
    fun storeVideoFile(tempFile: File, ref: MediaRef, playbackId: String) =
        storeVideoFile(tempFile, ref.entityId, playbackId)

    @Throws(java.io.IOException::class)
    fun storeVideoFile(tempFile: File, entityId: String, playbackId: String) {
        val dest = videoFile(entityId, playbackId)
        val stale = videoFiles(entityId)
        if (dest.exists()) dest.delete()
        if (!tempFile.renameTo(dest)) {
            tempFile.copyTo(dest, overwrite = true)
            tempFile.delete()
        }
        for (f in stale) if (f != dest) f.delete()
    }

    /** Temp file beside the video destination so the final rename is atomic. */
    internal fun videoTempFile(entityId: String, playbackId: String): File =
        File(videoFolder, "$entityId.$playbackId.mp4.tmp")

    // MARK: - Sweeps

    /**
     * Removes any cached file outside the supplied set that's older than
     * the grace period. Called by the prefetcher after the manifest fetch.
     * Movement videos are kept by entity id regardless of playback id.
     */
    fun sweep(keeping: Set<MediaRef>, gracePeriodMs: Long = DEFAULT_GRACE_MS) {
        if (!isInitialized) return
        val cutoff = System.currentTimeMillis() - gracePeriodMs
        val currentPaths = keeping
            .filter { it !is MediaRef.MovementVideo }
            .map { pathFor(it).absolutePath }
            .toSet()
        val currentVideoIds = keeping
            .filterIsInstance<MediaRef.MovementVideo>()
            .map { it.id }
            .toSet()

        rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
            if (file.name == MediaURLProvider.DISK_CACHE_FILE) return@forEach
            if (currentPaths.contains(file.absolutePath)) return@forEach
            if (file.parentFile?.name == VIDEO_FOLDER) {
                // Orphaned partial downloads die regardless of age.
                if (file.name.endsWith(".tmp")) {
                    if (file.lastModified() < System.currentTimeMillis() - 60 * 60 * 1000) file.delete()
                    return@forEach
                }
                val id = file.name.substringBefore('.')
                if (currentVideoIds.contains(id)) return@forEach
            }
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    /**
     * Reclaims leftover files from the retired `movement-video/{id}.mp4`
     * scheme (movement videos live in `movement-video-mp4` now) — dead bytes,
     * deleted regardless of age. Cheap; runs alongside the daily sweep.
     */
    fun purgeRetiredVideoFiles() {
        if (!isInitialized) return
        val folder = File(rootDir, RETIRED_VIDEO_FOLDER)
        folder.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }

    private fun folderName(ref: MediaRef): String = when (ref) {
        is MediaRef.MovementThumbnail -> "movement-thumbnail"
        is MediaRef.MovementVideo -> VIDEO_FOLDER
        is MediaRef.WorkoutThumbnail -> "workout-thumbnail"
        is MediaRef.ProgramThumbnail -> "program-thumbnail"
        is MediaRef.UserPhoto -> "user-photo"
        is MediaRef.ProgressPhoto -> "progress-photo"
    }
}
