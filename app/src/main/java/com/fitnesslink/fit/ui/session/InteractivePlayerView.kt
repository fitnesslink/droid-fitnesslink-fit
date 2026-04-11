package com.fitnesslink.fit.ui.session

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fitnesslink.fit.media.MediaCacheStore
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.media.MediaURLProvider
import com.fitnesslink.fit.ui.theme.ImagePlaceholder

/**
 * Plays a movement video referenced by a MediaRef. Prefers a locally
 * prefetched file (via MediaCacheStore), falls back to a fresh SAS URL
 * from MediaURLProvider on cache miss. Auto-releases the ExoPlayer when
 * the composable leaves composition.
 */
@Composable
fun InteractivePlayerView(
    ref: MediaRef?,
    height: Dp,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false
) {
    val context = LocalContext.current
    var sourceUri by remember(ref) { mutableStateOf<String?>(null) }
    var isLoading by remember(ref) { mutableStateOf(true) }

    LaunchedEffect(ref) {
        if (ref == null) {
            sourceUri = null
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        val local = MediaCacheStore.localFile(ref)?.absolutePath
        sourceUri = local ?: MediaURLProvider.url(ref)
        isLoading = false
    }

    val player = remember(sourceUri) {
        if (sourceUri != null) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(sourceUri!!))
                prepare()
                playWhenReady = !isPaused
            }
        } else null
    }

    LaunchedEffect(player, isPaused) {
        player?.playWhenReady = !isPaused
    }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(ImagePlaceholder),
        contentAlignment = Alignment.Center
    ) {
        when {
            player != null -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            isLoading -> CircularProgressIndicator()
            else -> Unit
        }
    }
}
