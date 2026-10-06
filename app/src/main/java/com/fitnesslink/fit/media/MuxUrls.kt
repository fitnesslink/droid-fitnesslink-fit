package com.fitnesslink.fit.media

import android.net.Uri
import android.util.Base64
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** The MP4 rendition of a signed Mux HLS stream plus its playback id. */
data class MuxMp4Rendition(val url: String, val playbackId: String)

/**
 * Pure helpers over signed Mux URLs — shared by the prefetcher (MP4 rendition
 * for offline copies) and the player (JWT expiry for proactive refresh).
 */
object MuxUrls {
    private const val MUX_STREAM_HOST = "stream.mux.com"

    fun isMuxStream(url: String): Boolean {
        val http = url.toHttpUrlOrNull() ?: return false
        return http.host.equals(MUX_STREAM_HOST, ignoreCase = true)
    }

    /** Mux playback id for `https://stream.mux.com/{playbackId}.m3u8?token=…`, else null. */
    fun playbackId(url: String): String? {
        val http = url.toHttpUrlOrNull() ?: return null
        if (!http.host.equals(MUX_STREAM_HOST, ignoreCase = true)) return null
        val last = http.pathSegments.lastOrNull { it.isNotEmpty() } ?: return null
        return when {
            last.endsWith(".m3u8") -> last.removeSuffix(".m3u8").ifEmpty { null }
            // Already an MP4 rendition: `/{playbackId}/highest.mp4`
            last.endsWith(".mp4") && http.pathSegments.size >= 2 ->
                http.pathSegments[http.pathSegments.size - 2].ifEmpty { null }
            else -> null
        }
    }

    /**
     * The MP4 rendition URL for a signed Mux stream URL
     * (`https://stream.mux.com/{playbackId}.m3u8?token=…` →
     * `…/{playbackId}/highest.mp4?token=…` — the same playback token covers
     * both). Null for anything that isn't a Mux HLS stream. The playback id
     * is stable across token rotations but replaced whenever the asset is
     * re-ingested, which makes it the staleness marker for offline copies.
     */
    fun mp4Rendition(streamUrl: String): MuxMp4Rendition? {
        val http: HttpUrl = streamUrl.toHttpUrlOrNull() ?: return null
        if (!http.host.equals(MUX_STREAM_HOST, ignoreCase = true)) return null
        val last = http.pathSegments.lastOrNull { it.isNotEmpty() } ?: return null
        if (!last.endsWith(".m3u8")) return null
        val playbackId = last.removeSuffix(".m3u8")
        if (playbackId.isEmpty()) return null
        val mp4 = http.newBuilder()
            .encodedPath("/$playbackId/highest.mp4")
            .build()
        return MuxMp4Rendition(mp4.toString(), playbackId)
    }

    /**
     * Expiry (epoch millis) of the signed `token` JWT on a Mux URL, parsed
     * from the payload's `exp` claim. Null when the URL has no token or the
     * token isn't a parseable JWT — callers fall back to the resolve expiry.
     */
    fun tokenExpiryMs(url: String): Long? {
        val token = try {
            Uri.parse(url).getQueryParameter("token")
        } catch (_: Exception) {
            null
        } ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        return try {
            val payload = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val exp = JSONObject(String(payload, Charsets.UTF_8)).optLong("exp", 0L)
            if (exp > 0) exp * 1000 else null
        } catch (_: Exception) {
            null
        }
    }
}
