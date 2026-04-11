package com.fitnesslink.fit.media

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.fitnesslink.fit.network.ApiClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Downloads today's workout media to disk so it's available offline during
 * the workout. Triggered on app launch, on foreground, and after the user's
 * schedule changes. Sweeps stale files from prior days.
 */
object MediaPrefetcher {
    private var appContext: Context? = null
    private val runMutex = Mutex()
    private var lastRunDay: Long? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

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
                if (last != null && isSameDay(last, System.currentTimeMillis())) return
            }

            val offsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
            val manifest = try {
                ApiClient.mediaApi.todayManifest(offsetMinutes)
            } catch (_: Exception) {
                return
            }

            MediaURLProvider.prime(manifest.items)

            val refs = manifest.items.mapNotNull { MediaRef.fromWire(it.type, it.id) }
            val refByItemId = manifest.items
                .mapIndexedNotNull { idx, item ->
                    MediaRef.fromWire(item.type, item.id)?.let { it to item }
                }
                .toMap()

            downloadMissing(context, refByItemId)
            MediaCacheStore.sweep(refs.toSet())
            lastRunDay = System.currentTimeMillis()
        } finally {
            runMutex.unlock()
        }
    }

    private suspend fun downloadMissing(
        context: Context,
        items: Map<MediaRef, ResolvedMediaItem>
    ) = coroutineScope {
        val onMetered = isOnMetered(context)
        for ((ref, item) in items) {
            val url = item.url ?: continue
            if (MediaCacheStore.localFile(ref) != null) continue

            // Defer videos on metered connections to avoid burning user data.
            if (onMetered && ref is MediaRef.MovementVideo) continue

            launch { download(url, ref) }
        }
    }

    private suspend fun download(url: String, ref: MediaRef) {
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 403) MediaURLProvider.invalidate(ref)
                    return
                }
                val body = response.body ?: return
                val dest = MediaCacheStore.pathFor(ref)
                val tmp = File(dest.parentFile, "${dest.name}.tmp")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (dest.exists()) dest.delete()
                tmp.renameTo(dest)
            }
        } catch (_: Exception) {
            // Best effort — leave the file missing.
        }
    }

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
