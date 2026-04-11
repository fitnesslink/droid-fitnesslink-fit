package com.fitnesslink.fit.media

import android.content.Context
import java.io.File

/**
 * Local on-disk store for prefetched media bytes (today's workout).
 * Files are keyed by the stable entity ID, never the SAS URL, so a SAS
 * rotation never invalidates a cached byte.
 */
object MediaCacheStore {
    private lateinit var rootDir: File

    fun initialize(context: Context) {
        rootDir = File(context.cacheDir, "MediaCache").apply { mkdirs() }
    }

    /** Returns the local file if a prefetched copy exists, otherwise null. */
    fun localFile(ref: MediaRef): File? {
        val f = pathFor(ref)
        return if (f.exists()) f else null
    }

    /** Persistent file path the prefetcher writes to. */
    fun pathFor(ref: MediaRef): File {
        val folder = File(rootDir, folderName(ref)).apply { mkdirs() }
        return File(folder, "${ref.entityId}.${ref.fileExtensionHint}")
    }

    /**
     * Removes any cached file outside the supplied set that's older than
     * the grace period. Called by the prefetcher after the manifest fetch.
     */
    fun sweep(keeping: Set<MediaRef>, gracePeriodMs: Long = 7L * 24 * 60 * 60 * 1000) {
        if (!::rootDir.isInitialized) return
        val cutoff = System.currentTimeMillis() - gracePeriodMs
        val currentPaths = keeping.map { pathFor(it).absolutePath }.toSet()

        rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
            if (currentPaths.contains(file.absolutePath)) return@forEach
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun folderName(ref: MediaRef): String = when (ref) {
        is MediaRef.MovementThumbnail -> "movement-thumbnail"
        is MediaRef.MovementVideo -> "movement-video"
        is MediaRef.WorkoutThumbnail -> "workout-thumbnail"
        is MediaRef.ProgramThumbnail -> "program-thumbnail"
        is MediaRef.UserPhoto -> "user-photo"
        is MediaRef.ProgressPhoto -> "progress-photo"
    }
}
