package com.fitnesslink.fit.media

import android.content.Context
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.persistence.DatabaseManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Date

/** A URL that arrived inline on an entity payload, ready to prime the cache. */
data class InlineMediaURL(
    val ref: MediaRef,
    val url: String,
    val expiresAt: Date? = null,
    /**
     * Mux ingest state for video refs. Only `READY` (or null, for payloads
     * that don't carry a status) primes a `MovementVideo` — a not-Ready
     * `videoUrl` is the SAS fallback to the source file and must never be
     * cached as the HLS stream.
     */
    val videoStatus: MuxStatus? = null
)

/**
 * Cache of resolved SAS / signed Mux URLs keyed by MediaRef. In memory for
 * speed, persisted to `cacheDir/MediaCache/url-cache.json` so resolves
 * survive app relaunches — without persistence every cold launch had to
 * re-resolve every visible thumbnail before the image loader could even
 * start fetching bytes. Batches lookups so a list of N items results in one
 * `/media/resolve` round trip, and de-dupes concurrent single-ref resolves.
 *
 * Port of iOS `MediaURLProvider`.
 */
object MediaURLProvider {
    internal const val DISK_CACHE_FILE = "url-cache.json"

    /** Refresh URLs this many ms before they expire to avoid races. */
    private const val SAFETY_MARGIN_MS = 5 * 60 * 1000L
    private const val DEFAULT_LIFETIME_MS = 60 * 60 * 1000L

    private data class CachedURL(val url: String, val expiresAtMs: Long)

    private data class PersistedEntry(
        val type: String,
        val id: String,
        val url: String,
        val expiresAtMs: Long
    )

    private val lock = Any()
    private val cache = HashMap<MediaRef, CachedURL>()

    private val inflightMutex = Mutex()
    private val inflight = HashMap<MediaRef, Deferred<String?>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    @Volatile
    private var diskCacheFile: File? = null
    private var saveJob: Job? = null

    /** Explicit init — `MediaLayer.initialize` also calls this. */
    fun initialize(context: Context) = MediaLayer.initialize(context)

    internal fun attach(context: Context) {
        val folder = File(context.cacheDir, "MediaCache").apply { mkdirs() }
        diskCacheFile = File(folder, DISK_CACHE_FILE)
        loadFromDisk()
    }

    // MARK: - Reads

    /**
     * Returns a cached URL if one exists and isn't about to expire. Safe to
     * call synchronously from a composable to render without an async hop.
     */
    fun cached(ref: MediaRef): String? = synchronized(lock) {
        val entry = cache[ref] ?: return null
        if (entry.expiresAtMs - System.currentTimeMillis() <= SAFETY_MARGIN_MS) return null
        entry.url
    }

    /**
     * Expiry of the currently cached URL for a ref, if any. The player reads
     * this after resolving so it can schedule a proactive refresh just before
     * the Mux JWT (or SAS) lapses — the value is the real token lifetime
     * threaded through from `/media/resolve`, not a guess.
     */
    fun cachedExpiry(ref: MediaRef): Date? = synchronized(lock) {
        cache[ref]?.let { Date(it.expiresAtMs) }
    }

    /**
     * Resolves a single MediaRef to a URL, fetching from the backend if
     * necessary. Returns null if the user cannot see the asset or the asset
     * does not exist. Concurrent callers for the same ref coalesce on one
     * request.
     */
    suspend fun url(ref: MediaRef): String? {
        cached(ref)?.let { return it }

        val deferred: Deferred<String?> = inflightMutex.withLock {
            inflight[ref]?.let { return@withLock it }
            val d = scope.async {
                try {
                    val resolved = ApiClient.mediaApi.resolve(
                        ResolveMediaRequest(items = listOf(MediaRefWire(ref.typeWireValue, ref.entityId)))
                    )
                    val item = resolved.firstOrNull() ?: return@async null
                    val url = item.url ?: return@async null
                    store(ref, url, item.expiresAt)
                    scheduleSave()
                    url
                } catch (_: Exception) {
                    null
                } finally {
                    inflightMutex.withLock { inflight.remove(ref) }
                }
            }
            inflight[ref] = d
            d
        }
        return deferred.await()
    }

    /**
     * Resolves multiple refs in a single round trip. Cached entries are
     * returned immediately; only the misses are sent to the backend.
     */
    suspend fun resolve(refs: Collection<MediaRef>): Map<MediaRef, String> {
        val output = LinkedHashMap<MediaRef, String>()
        val misses = ArrayList<MediaRef>()

        for (ref in refs.distinct()) {
            val hit = cached(ref)
            if (hit != null) output[ref] = hit else misses.add(ref)
        }
        if (misses.isEmpty()) return output

        try {
            val resolved = ApiClient.mediaApi.resolve(
                ResolveMediaRequest(items = misses.map { MediaRefWire(it.typeWireValue, it.entityId) })
            )
            for (item in resolved) {
                val ref = MediaRef.fromWire(item.type, item.id) ?: continue
                val url = item.url ?: continue
                store(ref, url, item.expiresAt)
                output[ref] = url
            }
            scheduleSave()
        } catch (_: Exception) {
            // Leave misses unresolved — caller sees no URL and can retry.
        }
        return output
    }

    // MARK: - Invalidation / refresh

    /**
     * Drops a cached URL. Call this when an image load returns 403 so the
     * next request fetches a fresh SAS.
     */
    fun invalidate(ref: MediaRef) {
        synchronized(lock) { cache.remove(ref) }
        scheduleSave()
    }

    /**
     * Forces a fresh resolve, bypassing the cached (possibly near-expiry)
     * URL. The Mux video player uses this to swap in a new signed HLS URL
     * before (proactive) or after (403 recovery) the JWT expires. Concurrent
     * callers coalesce on the inflight request in `url()`, so a burst of
     * segment 403s can't fan out into a storm of resolves.
     */
    suspend fun refresh(ref: MediaRef): String? {
        invalidate(ref)
        return url(ref)
    }

    // MARK: - Priming

    /** Pre-populates the cache from a manifest fetch (or any other batch source). */
    fun prime(items: List<ResolvedMediaItem>) {
        for (item in items) {
            val ref = MediaRef.fromWire(item.type, item.id) ?: continue
            val url = item.url ?: continue
            store(ref, url, item.expiresAt)
        }
        scheduleSave()
    }

    /**
     * Pre-populates the cache from a URL that arrived inline on an entity
     * payload. Pass the expiry from the payload if the server returns one;
     * the default matches the SAS lifetime `/media/resolve` mints today.
     */
    fun primeURL(ref: MediaRef, url: String, expiresAt: Date? = null) {
        store(ref, url, expiresAt)
        scheduleSave()
    }

    /** Batch variant of `primeURL` — one disk write for the whole list. */
    fun primeURLs(pairs: List<InlineMediaURL>) {
        if (pairs.isEmpty()) return
        for (p in pairs) store(p.ref, p.url, p.expiresAt)
        scheduleSave()
    }

    /**
     * Primes the cache from URLs arriving inline on API payloads. Applies the
     * iOS Ready-gate: a `MovementVideo` is only cached when `videoStatus` is
     * `READY` or absent, so the not-Ready SAS fallback never gets played as
     * HLS. Android entity models don't carry `*Url` fields yet — services
     * build `InlineMediaURL`s from whatever their payload exposes and hand
     * them here (see report: typed `ingest(workout)` overloads are deferred
     * until the models grow URL fields).
     */
    fun ingest(items: List<InlineMediaURL>) {
        val accepted = items.filter { item ->
            if (item.ref !is MediaRef.MovementVideo) true
            else (item.videoStatus ?: MuxStatus.READY) == MuxStatus.READY
        }
        primeURLs(accepted)
    }

    fun ingest(vararg items: InlineMediaURL) = ingest(items.toList())

    /**
     * Walks the local catalog cache (workouts, programs, movements) and
     * batch-resolves every thumbnail ref whose URL isn't already cached.
     * Idempotent — safe to call on every foreground; warm refs are skipped.
     * This is what makes catalog browsing feel instant on cold launch.
     */
    suspend fun primeCatalog() {
        val refs = collectCatalogRefs()
        val misses = refs.filter { cached(it) == null }
        if (misses.isEmpty()) return
        // Chunk so a very large catalog doesn't produce one giant request.
        for (chunk in misses.chunked(200)) resolve(chunk)
    }

    /**
     * Android's catalog screens key thumbnails by ENTITY id
     * (`MediaRef.WorkoutThumbnail(workout.id)`), and the local `workouts` /
     * `programs` tables carry no `thumbnailId` column, so that's what we
     * prime. iOS keys by `thumbnailId ?? imageId`; when the Android models
     * and DB grow those columns, switch this to the asset ids.
     */
    private fun collectCatalogRefs(): List<MediaRef> {
        val refs = ArrayList<MediaRef>()
        try {
            for (w in DatabaseManager.allWorkouts()) if (w.id.isNotEmpty()) refs.add(MediaRef.WorkoutThumbnail(w.id))
            for (p in DatabaseManager.allPrograms()) if (p.id.isNotEmpty()) refs.add(MediaRef.ProgramThumbnail(p.id))
            for (m in DatabaseManager.allMovements()) if (m.id.isNotEmpty()) refs.add(MediaRef.MovementThumbnail(m.id))
        } catch (_: Exception) {
            // DB not initialized (tests) — prime whatever we gathered.
        }
        return refs
    }

    // MARK: - Internals

    private fun store(ref: MediaRef, url: String, expiresAt: Date?) {
        val expiresMs = expiresAt?.time ?: (System.currentTimeMillis() + DEFAULT_LIFETIME_MS)
        synchronized(lock) { cache[ref] = CachedURL(url, expiresMs) }
    }

    // MARK: - Persistence

    private fun loadFromDisk() {
        val file = diskCacheFile ?: return
        if (!file.exists()) return
        val entries: List<PersistedEntry> = try {
            val type = object : TypeToken<List<PersistedEntry>>() {}.type
            gson.fromJson<List<PersistedEntry>>(file.readText(), type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val now = System.currentTimeMillis()
        synchronized(lock) {
            for (e in entries) {
                // Drop entries whose SAS has already passed the safety margin
                // so we don't hand stale URLs to the image loader on first call.
                if (e.expiresAtMs - now <= SAFETY_MARGIN_MS) continue
                val ref = MediaRef.fromWire(e.type, e.id) ?: continue
                cache[ref] = CachedURL(e.url, e.expiresAtMs)
            }
        }
    }

    /** Debounced, off-main, atomic write of the whole cache. */
    private fun scheduleSave() {
        if (diskCacheFile == null) return
        synchronized(lock) {
            if (saveJob?.isActive == true) return
            saveJob = scope.launch {
                delay(500)
                saveToDisk()
            }
        }
    }

    private fun saveToDisk() {
        val file = diskCacheFile ?: return
        val snapshot = synchronized(lock) {
            cache.map { (ref, c) -> PersistedEntry(ref.typeWireValue, ref.entityId, c.url, c.expiresAtMs) }
        }
        try {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(gson.toJson(snapshot))
            if (!tmp.renameTo(file)) {
                file.writeText(gson.toJson(snapshot))
                tmp.delete()
            }
        } catch (_: Exception) {
            // Best effort — the in-memory cache is still authoritative.
        }
    }
}
