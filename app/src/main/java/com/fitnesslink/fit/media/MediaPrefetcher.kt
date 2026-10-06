package com.fitnesslink.fit.media

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.fitnesslink.fit.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Downloads workout media to disk so it's available offline during the
 * workout. Triggered on app launch, on foreground, and after the user's
 * schedule changes (today manifest), and per-content from workout /
 * movement detail (`prefetch(refs)`). Sweeps stale files from prior days.
 *
 * Movement videos download as the Mux MP4 rendition of their HLS stream
 * (`/{playbackId}/highest.mp4`), stamped with the playback id so a
 * re-ingested asset replaces its stale copy. All downloads share one
 * 2-wide queue so a 20-movement workout can't starve the active stream.
 *
 * Port of iOS `MediaPrefetcher`; keeps the Android-only metered-network
 * deferral and self-healing retry.
 */
object MediaPrefetcher {
    private const val MAX_CONCURRENT_DOWNLOADS = 2

    private var appContext: Context? = null
    private val runMutex = Mutex()
    private var lastRunDay: Long? = null

    /**
     * Did the last run actually pull every item in the manifest, or did we
     * skip videos because we were on a metered connection? If the run was
     * incomplete, every subsequent foreground/reconnect tick retries rather
     * than short-circuiting on `lastRunDay`. A user who started on cellular
     * and later switched to Wi-Fi picks up the missing videos automatically.
     */
    private var lastRunWasComplete: Boolean = false

    /** Global download queue: at most two transfers in flight at once. */
    private val downloadSlots = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    /**
     * Movement videos currently downloading, by entity id — the detail page,
     * preview sheet and daily manifest can all ask for the same video.
     */
    private val videosInFlight = HashSet<String>()
    private val inFlightLock = Any()

    /** Background scope for the slow video tail of a manifest run. */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Kept for `FitnessLinkApp`; routes through the shared media-layer init. */
    fun initialize(context: Context) = MediaLayer.initialize(context)

    internal fun attach(context: Context) {
        appContext = context.applicationContext
    }

    // MARK: - Public API

    /** Force a fresh prefetch run regardless of recency. */
    suspend fun runNow() = runIfNeeded(force = true)

    /**
     * Run a prefetch only if one hasn't completed for today's date yet.
     * Safe to call on every app launch and foregrounding.
     */
    suspend fun runIfNeeded(force: Boolean = false) {
        val context = appContext ?: return
        if (!runMutex.tryLock()) return
        try {
            if (!force) {
                val last = lastRunDay
                if (last != null && isSameDay(last, System.currentTimeMillis()) && lastRunWasComplete) return
            }

            val offsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
            val manifest = try {
                ApiClient.mediaApi.todayManifest(offsetMinutes)
            } catch (_: Exception) {
                return
            }

            MediaURLProvider.prime(manifest.items)

            val work = manifest.items.mapNotNull { item ->
                val ref = MediaRef.fromWire(item.type, item.id) ?: return@mapNotNull null
                val url = item.url ?: return@mapNotNull null
                ref to url
            }
            val allRefs = work.map { it.first }.toSet()

            val complete = downloadMissing(context, work)
            MediaCacheStore.sweep(allRefs)
            MediaCacheStore.purgeRetiredVideoFiles()
            lastRunDay = System.currentTimeMillis()
            lastRunWasComplete = complete
        } finally {
            runMutex.unlock()
        }
    }

    /**
     * Prefetch a specific list of refs to local disk. Resolves URLs via
     * MediaURLProvider for refs that aren't already URL-cached, then
     * downloads through the shared 2-wide queue. Idempotent — refs already
     * on disk are skipped; movement videos always pass through because
     * `downloadVideo` compares the resolved URL's playback id against the
     * cached file's, so a re-ingested asset replaces its stale copy instead
     * of being skipped on mere existence. Honors the metered deferral for
     * videos like the manifest run does.
     */
    suspend fun prefetch(refs: List<MediaRef>) {
        if (refs.isEmpty()) return
        val context = appContext
        val onMetered = context?.let { isOnMetered(it) } ?: false

        val missing = refs.distinct().filter { ref ->
            when {
                ref is MediaRef.MovementVideo -> !onMetered
                else -> MediaCacheStore.localFile(ref) == null
            }
        }
        if (missing.isEmpty()) return

        val urlMap = MediaURLProvider.resolve(missing)
        val work = missing.mapNotNull { ref -> urlMap[ref]?.let { ref to it } }
        downloadFiles(work)
    }

    /** True when the current network is metered (cellular / hotspot). */
    fun isOnMeteredNetwork(): Boolean = appContext?.let { isOnMetered(it) } ?: false

    // MARK: - Manifest run

    /**
     * Returns true if every manifest item that was missing got queued (or
     * was already on disk). Returns false if any video was skipped due to
     * metering — the next trigger should retry rather than treat the day as
     * done. Images are awaited; videos are the slow part and run on in the
     * background through the shared queue so login-time callers aren't held.
     */
    private suspend fun downloadMissing(
        context: Context,
        work: List<Pair<MediaRef, String>>
    ): Boolean {
        val onMetered = isOnMetered(context)
        var skippedAny = false
        val imageWork = ArrayList<Pair<MediaRef, String>>()
        val videoWork = ArrayList<Pair<MediaRef, String>>()

        for ((ref, url) in work) {
            if (ref is MediaRef.MovementVideo) {
                if (onMetered) {
                    skippedAny = true
                    continue
                }
                // `downloadVideo` decides whether the on-disk copy is current.
                videoWork.add(ref to url)
                continue
            }
            if (MediaCacheStore.localFile(ref) != null) continue
            imageWork.add(ref to url)
        }

        downloadFiles(imageWork)
        if (videoWork.isNotEmpty()) {
            backgroundScope.launch { downloadFiles(videoWork) }
        }
        return !skippedAny
    }

    // MARK: - Download queue

    private suspend fun downloadFiles(work: List<Pair<MediaRef, String>>) {
        if (work.isEmpty()) return
        coroutineScope {
            for ((ref, url) in work) {
                launch(Dispatchers.IO) {
                    downloadSlots.withPermit { download(url, ref) }
                }
            }
        }
    }

    private suspend fun download(url: String, ref: MediaRef) {
        if (ref is MediaRef.MovementVideo) {
            downloadVideo(url, ref)
            return
        }
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 403 || response.code == 401) MediaURLProvider.invalidate(ref)
                    return
                }
                val body = response.body ?: return
                val dest = MediaCacheStore.pathFor(ref)
                val tmp = File(dest.parentFile, "${dest.name}.tmp")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
            }
        } catch (_: Exception) {
            // Best effort — leave the file missing.
        }
    }

    /**
     * Downloads a movement video for offline playback as the Mux MP4
     * rendition of its HLS stream. Best-effort — an asset with no MP4
     * rendition (404) simply keeps streaming.
     */
    private fun downloadVideo(streamUrl: String, ref: MediaRef) {
        val mp4 = MuxUrls.mp4Rendition(streamUrl) ?: return
        if (MediaCacheStore.hasCurrentVideoFile(ref, mp4.playbackId)) return

        synchronized(inFlightLock) {
            if (!videosInFlight.add(ref.entityId)) return
        }
        val tmp = MediaCacheStore.videoTempFile(ref.entityId, mp4.playbackId)
        try {
            val request = Request.Builder().url(mp4.url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 403 || response.code == 401) MediaURLProvider.invalidate(ref)
                    return
                }
                val body = response.body ?: return
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                MediaCacheStore.storeVideoFile(tmp, ref, mp4.playbackId)
            }
        } catch (_: Exception) {
            // Best effort — the video streams instead.
        } finally {
            if (tmp.exists()) tmp.delete()
            synchronized(inFlightLock) { videosInFlight.remove(ref.entityId) }
        }
    }

    // MARK: - Helpers

    private fun isOnMetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}
