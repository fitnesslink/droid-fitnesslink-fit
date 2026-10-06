package com.fitnesslink.fit.ui.session

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fitnesslink.fit.media.MediaCacheStore
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.media.MediaURLProvider
import com.fitnesslink.fit.media.MuxStatus
import com.fitnesslink.fit.media.MuxUrls
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.ImagePlaceholder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Lightweight config for Mux HLS playback. Mirrors iOS `MuxPlaybackConfig`.
 */
object MuxPlaybackConfig {
    /**
     * How long before a signed URL's expiry we proactively swap in a fresh
     * one. The Mux JWT defaults to a 1 h lifetime; refreshing ~90 s early
     * keeps a long-running session player from ever hitting a 403 mid-stream.
     */
    const val TOKEN_REFRESH_LEAD_MS = 90_000L

    /**
     * Cap on consecutive 403/failure recoveries before we stop re-resolving.
     * Prevents an unrecoverable asset (or a backend outage) from hammering
     * `/media/resolve` in a tight loop. Reset once playback advances again.
     */
    const val MAX_RECOVERY_ATTEMPTS = 3

    /** Master kill-switch: when false, players render the poster only. */
    @Volatile
    var isHLSPlaybackEnabled: Boolean = true
}

/**
 * Owns the ExoPlayer for a single movement video and its Mux token
 * lifecycle: resolves the signed HLS URL (or plays the offline MP4 when
 * `MediaCacheStore` has a current copy), loops, and keeps playback alive
 * across token expiry — proactively (swap before the JWT lapses) and
 * reactively (recover from a 401/403/410 by re-resolving and resuming at
 * the same position, capped). Port of iOS `MuxPlaybackController`.
 */
@OptIn(UnstableApi::class)
class MuxPlaybackController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val handleAudioFocus: Boolean
) {
    var player: ExoPlayer? by mutableStateOf(null)
        private set
    var isLoading: Boolean by mutableStateOf(true)
        private set
    var isBuffering: Boolean by mutableStateOf(false)
        private set
    var hasRenderedFrame: Boolean by mutableStateOf(false)
        private set

    /** True once the player is actually painting frames (loaded, not buffering). */
    val isShowingFrames: Boolean get() = player != null && !isLoading && !isBuffering && hasRenderedFrame

    // Inputs captured on load, reused when swapping in a fresh token.
    private var ref: MediaRef? = null
    private var prerenderedUrl: String? = null
    private var muted = true
    private var loop = true
    private var paused = false

    private var refreshJob: Job? = null
    private var isRecovering = false
    private var recoveryAttempts = 0
    private var currentUrl: String? = null

    private enum class RefreshReason { PROACTIVE, ERROR }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> isBuffering = true
                Player.STATE_READY -> {
                    isBuffering = false
                    // Healthy playback refills the recovery budget.
                    recoveryAttempts = 0
                }
                Player.STATE_ENDED -> {
                    // REPEAT_MODE_ONE handles looping; ENDED only arrives when
                    // loop is off.
                    isBuffering = false
                }
                else -> Unit
            }
        }

        override fun onRenderedFirstFrame() {
            hasRenderedFrame = true
            isBuffering = false
        }

        override fun onPlayerError(error: PlaybackException) {
            // A Mux token that expired mid-stream surfaces as a 401/403 on a
            // segment/playlist fetch — drop the cached URL so the re-resolve
            // is forced. Any other failure also gets a capped recovery,
            // matching iOS (AVPlayerItemFailedToPlayToEndTime).
            if (isAuthFailure(error)) ref?.let { MediaURLProvider.invalidate(it) }
            recover()
        }
    }

    // MARK: - Lifecycle

    suspend fun load(ref: MediaRef?, prerenderedUrl: String?, muted: Boolean, loop: Boolean, startPaused: Boolean) {
        this.ref = ref
        this.prerenderedUrl = prerenderedUrl
        this.muted = muted
        this.loop = loop
        this.paused = startPaused
        isLoading = true
        hasRenderedFrame = false
        recoveryAttempts = 0

        val url = resolveUrl()
        if (url == null) {
            isLoading = false
            return
        }
        attach(url, resumeAtMs = null)
        isLoading = false
    }

    fun setPaused(value: Boolean) {
        paused = value
        player?.playWhenReady = !value
    }

    fun setMuted(value: Boolean) {
        muted = value
        player?.volume = if (value) 0f else 1f
    }

    fun teardown() {
        refreshJob?.cancel()
        refreshJob = null
        player?.let {
            it.removeListener(listener)
            it.stop()
            it.release()
        }
        player = null
        currentUrl = null
        isBuffering = false
        isLoading = false
        hasRenderedFrame = false
        isRecovering = false
        recoveryAttempts = 0
    }

    // MARK: - URL resolution

    private suspend fun resolveUrl(): String? {
        val r = ref
        if (r != null) {
            MediaCacheStore.localFile(r)?.let { return Uri.fromFile(it).toString() }
        }
        prerenderedUrl?.let { url ->
            if (r != null) MediaURLProvider.primeURL(r, url)
            return url
        }
        return r?.let { MediaURLProvider.url(it) }
    }

    // MARK: - Player wiring

    private fun attach(url: String, resumeAtMs: Long?) {
        val exo = player ?: ExoPlayer.Builder(context).build().also { built ->
            // Exercise demo videos are silent; by default don't take audio
            // focus so Spotify / YouTube Music keep playing during a workout.
            built.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ handleAudioFocus
            )
            built.addListener(listener)
        }

        exo.volume = if (muted) 0f else 1f
        exo.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

        val isLocal = url.startsWith("file:")
        val item = makeItem(url, isLocal)
        if (resumeAtMs != null && resumeAtMs > 0) exo.setMediaItem(item, resumeAtMs) else exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = !paused

        player = exo
        currentUrl = url
        // Streaming items show the spinner until the first frame paints; a
        // resumed swap also re-buffers briefly. Local files are instant.
        isBuffering = !isLocal
        if (!isLocal) hasRenderedFrame = false

        scheduleProactiveRefresh(url, isLocal)
    }

    private fun makeItem(url: String, isLocal: Boolean): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        if (!isLocal && (url.contains(".m3u8") || MuxUrls.isMuxStream(url))) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        return builder.build()
    }

    // MARK: - Token refresh / recovery

    private fun isAuthFailure(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 8) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode in setOf(401, 403, 410)
            }
            cause = cause.cause
            depth++
        }
        return error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    }

    private fun recover() {
        if (isRecovering || player == null || ref == null) return
        scope.launch { swapToFreshUrl(RefreshReason.ERROR) }
    }

    private fun scheduleProactiveRefresh(url: String, isLocal: Boolean) {
        refreshJob?.cancel()
        refreshJob = null
        if (isLocal) return
        val r = ref ?: return

        // Prefer the JWT's own `exp`; fall back to the resolve expiry.
        val expiryMs = MuxUrls.tokenExpiryMs(url)
            ?: MediaURLProvider.cachedExpiry(r)?.time
            ?: return
        val delayMs = expiryMs - System.currentTimeMillis() - MuxPlaybackConfig.TOKEN_REFRESH_LEAD_MS
        refreshJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            swapToFreshUrl(RefreshReason.PROACTIVE)
        }
    }

    /**
     * Re-resolve a fresh signed URL and swap it in, resuming where we were.
     * `PROACTIVE` runs ahead of expiry (no attempt budget spent); `ERROR` is
     * the 403/failure path and is capped.
     */
    private suspend fun swapToFreshUrl(reason: RefreshReason) {
        if (isRecovering) return
        val r = ref ?: return
        val exo = player ?: return

        if (reason == RefreshReason.ERROR) {
            recoveryAttempts++
            if (recoveryAttempts > MuxPlaybackConfig.MAX_RECOVERY_ATTEMPTS) return
        }

        isRecovering = true
        try {
            val position = exo.currentPosition
            // A local copy may have landed since we started streaming.
            val fresh = MediaCacheStore.localFile(r)?.let { Uri.fromFile(it).toString() }
                ?: MediaURLProvider.refresh(r)
                ?: return
            if (player == null) return
            attach(fresh, resumeAtMs = if (position > 0) position else null)
        } finally {
            isRecovering = false
        }
    }
}

/**
 * Reusable Mux video player. Streams Mux HLS (adaptive bitrate) for remote
 * sources and plays instantly from disk when `MediaCacheStore` holds a
 * current MP4 for the ref. Shows `posterRef` (thumbnail) until the first
 * frame paints, when the asset isn't Ready yet, or when HLS playback is
 * disabled. The caller owns `keepScreenOn`.
 *
 * @param ref       stable media reference; resolved through `MediaURLProvider`
 * @param url       optional prerendered signed URL (skips the resolve hop; primed for `ref`)
 * @param loop      replay on end (default true — demo loops)
 * @param fill      crop to cover (`true`, immersive session) or letterbox (`false`, detail/preview)
 * @param muted     start muted (default true — form videos have no soundtrack)
 * @param paused    hold playback
 * @param handleAudioFocus whether ExoPlayer should request audio focus (default false: don't duck the user's music)
 * @param videoStatus Mux ingest state; anything other than READY (or null) shows the poster only
 */
@OptIn(UnstableApi::class)
@Composable
fun MuxVideoPlayer(
    ref: MediaRef?,
    modifier: Modifier = Modifier,
    url: String? = null,
    loop: Boolean = true,
    fill: Boolean = true,
    muted: Boolean = true,
    paused: Boolean = false,
    handleAudioFocus: Boolean = false,
    showControls: Boolean = false,
    videoStatus: MuxStatus? = null,
    posterRef: MediaRef? = null,
    posterHeight: Dp? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(handleAudioFocus) { MuxPlaybackController(context, scope, handleAudioFocus) }

    // Play only when HLS is enabled and Mux reports the asset Ready. Unknown
    // status (null) is treated as Ready so pre-status payloads behave as before.
    val shouldAttemptPlayback = MuxPlaybackConfig.isHLSPlaybackEnabled &&
        (videoStatus ?: MuxStatus.READY) == MuxStatus.READY &&
        (ref != null || url != null)

    LaunchedEffect(ref, url, shouldAttemptPlayback) {
        if (shouldAttemptPlayback) {
            controller.load(ref = ref, prerenderedUrl = url, muted = muted, loop = loop, startPaused = paused)
        } else {
            controller.teardown()
        }
    }
    LaunchedEffect(paused) { controller.setPaused(paused) }
    LaunchedEffect(muted) { controller.setMuted(muted) }

    DisposableEffect(controller) {
        onDispose { controller.teardown() }
    }

    val showingFrames = shouldAttemptPlayback && controller.isShowingFrames

    Box(
        modifier = modifier.background(if (fill) ImagePlaceholder else Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Poster beneath the player until frames paint — first paint is the
        // thumbnail, not a black frame.
        if (!showingFrames && posterRef != null) {
            if (posterHeight != null) {
                FLImageView(ref = posterRef, height = posterHeight, modifier = Modifier.fillMaxWidth())
            } else {
                FLImageView(ref = posterRef, modifier = Modifier.fillMaxSize())
            }
        }

        val exo = controller.player
        if (shouldAttemptPlayback && exo != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = showControls
                        resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        player = exo
                    }
                },
                update = { view ->
                    if (view.player !== exo) view.player = exo
                    view.useController = showControls
                    view.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (shouldAttemptPlayback && (controller.isLoading || controller.isBuffering)) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/**
 * Plays a movement video referenced by a MediaRef at a fixed height. Kept
 * for existing call sites; delegates to `MuxVideoPlayer`.
 */
@Composable
fun InteractivePlayerView(
    ref: MediaRef?,
    height: Dp,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false,
    posterRef: MediaRef? = null,
    videoStatus: MuxStatus? = null,
    fill: Boolean = true
) {
    MuxVideoPlayer(
        ref = ref,
        modifier = modifier.fillMaxWidth().height(height),
        loop = true,
        fill = fill,
        muted = true,
        paused = isPaused,
        videoStatus = videoStatus,
        posterRef = posterRef,
        posterHeight = height
    )
}

/** Local-file helper for callers that want to know whether playback will be offline. */
fun MediaRef.offlineVideoFile(): File? = MediaCacheStore.localFile(this)
