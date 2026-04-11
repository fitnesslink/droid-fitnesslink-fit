package com.fitnesslink.fit.media

import com.fitnesslink.fit.network.ApiClient
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

/**
 * In-memory cache of resolved SAS URLs keyed by MediaRef. Holds URLs only
 * for the lifetime of the process — no persistence. Batches lookups so a
 * list of N items results in one /media/resolve round trip.
 */
object MediaURLProvider {
    private data class CachedURL(val url: String, val expiresAt: Date)

    private val cache = mutableMapOf<MediaRef, CachedURL>()
    private val inflight = mutableMapOf<MediaRef, Deferred<String?>>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Refresh URLs this many seconds before they expire to avoid races. */
    private const val SAFETY_MARGIN_MS = 5 * 60 * 1000L

    /**
     * Returns a cached URL if one exists and isn't about to expire.
     * Used by views that want to render immediately without an async hop.
     */
    suspend fun cached(ref: MediaRef): String? = mutex.withLock {
        val entry = cache[ref] ?: return@withLock null
        if (entry.expiresAt.time - System.currentTimeMillis() <= SAFETY_MARGIN_MS) {
            return@withLock null
        }
        entry.url
    }

    /**
     * Resolves a single MediaRef to a URL, fetching from the backend if
     * necessary. Returns null if the user cannot see the asset or the
     * asset does not exist.
     */
    suspend fun url(ref: MediaRef): String? {
        cached(ref)?.let { return it }

        val deferred = mutex.withLock {
            inflight[ref]?.let { return@withLock it }
            val d = scope.async {
                try {
                    val response = ApiClient.mediaApi.resolve(
                        ResolveMediaRequest(items = listOf(MediaRefWire(ref.typeWireValue, ref.entityId)))
                    )
                    val item = response.items.firstOrNull() ?: return@async null
                    val url = item.url ?: return@async null
                    val expires = item.expiresAt ?: Date(System.currentTimeMillis() + 60 * 60 * 1000)
                    mutex.withLock {
                        cache[ref] = CachedURL(url, expires)
                    }
                    url
                } catch (_: Exception) {
                    null
                } finally {
                    mutex.withLock { inflight.remove(ref) }
                }
            }
            inflight[ref] = d
            d
        }

        return deferred.await()
    }

    /** Resolves multiple refs in a single round trip. */
    suspend fun resolve(refs: List<MediaRef>): Map<MediaRef, String> {
        val output = mutableMapOf<MediaRef, String>()
        val misses = mutableListOf<MediaRef>()

        for (ref in refs) {
            val cached = cached(ref)
            if (cached != null) {
                output[ref] = cached
            } else {
                misses.add(ref)
            }
        }

        if (misses.isEmpty()) return output

        try {
            val response = ApiClient.mediaApi.resolve(
                ResolveMediaRequest(items = misses.map { MediaRefWire(it.typeWireValue, it.entityId) })
            )
            mutex.withLock {
                for (item in response.items) {
                    val ref = MediaRef.fromWire(item.type, item.id) ?: continue
                    val url = item.url ?: continue
                    val expires = item.expiresAt ?: Date(System.currentTimeMillis() + 60 * 60 * 1000)
                    cache[ref] = CachedURL(url, expires)
                    output[ref] = url
                }
            }
        } catch (_: Exception) {
            // Leave misses unresolved — caller sees no URL and can retry.
        }

        return output
    }

    /**
     * Drops a cached URL. Call this when an image load returns 403 so the
     * next request fetches a fresh SAS.
     */
    suspend fun invalidate(ref: MediaRef) = mutex.withLock {
        cache.remove(ref)
        Unit
    }

    /**
     * Pre-populates the cache from a manifest fetch (or any other batch
     * source). Useful when the prefetcher has already gathered URLs.
     */
    suspend fun prime(items: List<ResolvedMediaItem>) = mutex.withLock {
        for (item in items) {
            val ref = MediaRef.fromWire(item.type, item.id) ?: continue
            val url = item.url ?: continue
            val expires = item.expiresAt ?: Date(System.currentTimeMillis() + 60 * 60 * 1000)
            cache[ref] = CachedURL(url, expires)
        }
    }
}
