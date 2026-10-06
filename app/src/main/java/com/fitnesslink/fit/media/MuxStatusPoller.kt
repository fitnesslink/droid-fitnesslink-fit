package com.fitnesslink.fit.media

import com.fitnesslink.fit.network.ApiClient
import kotlinx.coroutines.delay

/** Terminal outcome of a Mux status poll. */
sealed class MuxPollOutcome {
    abstract val status: MuxStatus

    /** Mux finished; the asset is playable. */
    data class Ready(val detail: MediaAssetDetail) : MuxPollOutcome() {
        override val status: MuxStatus get() = MuxStatus.READY
        val playbackId: String? get() = detail.muxPlaybackId
    }

    /** Mux choked on the file (or the server never handed off to Mux). */
    data class Failed(
        override val status: MuxStatus,
        val message: String,
        val detail: MediaAssetDetail?
    ) : MuxPollOutcome()

    /** Attempt budget exhausted while still non-terminal. Caller may re-poll later. */
    data class TimedOut(val lastDetail: MediaAssetDetail?) : MuxPollOutcome() {
        override val status: MuxStatus get() = lastDetail?.muxStatusEnum ?: MuxStatus.UNKNOWN
    }
}

/**
 * FB-89: after the multipart upload completes, the server is still waiting
 * on Mux to ingest + transcode. Polls `GET media/{id}` until MuxStatus is
 * Ready (playable) or Errored (Mux choked on the file).
 *
 * Backoff is intentionally gentle — 2, 3, 5, 8, 13, 21, 30, 30, … seconds.
 * Mux ingest for short user clips typically wraps in ~10-30 s but can be a
 * couple of minutes for longer videos; the 30 s cap keeps a slow ingest
 * from spamming the API while the user still sees the flip within half a
 * minute of Mux finishing. Bounded attempts so a stuck ingest doesn't poll
 * forever. Port of iOS `MuxStatusPoller`.
 */
object MuxStatusPoller {
    private val delaysSeconds = longArrayOf(2, 3, 5, 8, 13, 21, 30)
    const val DEFAULT_MAX_ATTEMPTS = 40

    /**
     * After the server's upload handler returns, its Mux pull trigger should
     * have set MuxStatus=Preparing. If we still see None after this many
     * consecutive polls the server's Mux create failed silently — no point
     * waiting ten minutes.
     */
    private const val NONE_THRESHOLD = 3

    /**
     * Polls until terminal. `onUpdate` fires after every successful fetch so
     * callers can mirror intermediate status; `startAttempt` lets a resumed
     * worker continue the backoff ladder rather than restarting at 2 s.
     */
    suspend fun poll(
        mediaAssetId: String,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        startAttempt: Int = 0,
        onUpdate: (suspend (MediaAssetDetail) -> Unit)? = null
    ): MuxPollOutcome {
        var attempt = startAttempt
        var consecutiveNone = 0
        var lastDetail: MediaAssetDetail? = null

        while (attempt < maxAttempts) {
            val wait = delaysSeconds[minOf(attempt, delaysSeconds.size - 1)]
            delay(wait * 1000)
            attempt++

            val detail = try {
                ApiClient.mediaApi.get(mediaAssetId)
            } catch (_: Exception) {
                // Transient API errors don't end the poll — Mux is the slow
                // path; network blips during a 10-minute window are expected.
                continue
            }
            lastDetail = detail
            onUpdate?.invoke(detail)

            when (detail.muxStatusEnum) {
                MuxStatus.READY -> return MuxPollOutcome.Ready(detail)
                MuxStatus.ERRORED -> return MuxPollOutcome.Failed(
                    MuxStatus.ERRORED,
                    detail.muxErrors?.firstOrNull() ?: "Mux ingest failed",
                    detail
                )
                MuxStatus.NONE -> {
                    consecutiveNone++
                    if (consecutiveNone >= NONE_THRESHOLD) {
                        return MuxPollOutcome.Failed(
                            MuxStatus.NONE,
                            "Server failed to hand off to Mux. Retry from admin: mux-backfill-one.",
                            detail
                        )
                    }
                }
                MuxStatus.UPLOADING, MuxStatus.PREPARING, MuxStatus.UNKNOWN -> consecutiveNone = 0
            }
        }
        return MuxPollOutcome.TimedOut(lastDetail)
    }

    /** One-shot status read (no waiting) — handy for a "check now" affordance. */
    suspend fun check(mediaAssetId: String): MediaAssetDetail? =
        try { ApiClient.mediaApi.get(mediaAssetId) } catch (_: Exception) { null }
}
