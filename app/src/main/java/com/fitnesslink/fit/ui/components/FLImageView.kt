package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.network.HttpException
import coil.request.ImageRequest
import com.fitnesslink.fit.media.MediaCacheStore
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.media.MediaURLProvider
import com.fitnesslink.fit.ui.theme.ImagePlaceholder

/**
 * Renders an image referenced by a stable MediaRef. Prefers a local
 * prefetched file, then a cached SAS URL (synchronous — no async hop), then
 * resolves through MediaURLProvider. On a failed remote load it forces a
 * fresh SAS (`refresh`) and retries once; a 401/403/410 is the expired-SAS
 * case, anything else gets the same single retry.
 */
@Composable
fun FLImageView(
    ref: MediaRef?,
    height: Dp,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    FLImageView(
        ref = ref,
        modifier = modifier.fillMaxWidth().height(height),
        contentScale = contentScale
    )
}

/** Size-agnostic variant — the caller's modifier decides the frame. */
@Composable
fun FLImageView(
    ref: MediaRef?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var resolvedUrl by remember(ref) { mutableStateOf<String?>(null) }
    var localPath by remember(ref) { mutableStateOf<String?>(null) }
    var retryCount by remember(ref) { mutableIntStateOf(0) }
    var loadFailed by remember(ref) { mutableStateOf(false) }

    LaunchedEffect(ref, retryCount) {
        if (ref == null) {
            resolvedUrl = null
            localPath = null
            loadFailed = false
            return@LaunchedEffect
        }
        loadFailed = false

        if (retryCount == 0) {
            MediaCacheStore.localFile(ref)?.absolutePath?.let {
                localPath = it
                return@LaunchedEffect
            }
            MediaURLProvider.cached(ref)?.let {
                resolvedUrl = it
                return@LaunchedEffect
            }
            val fresh = MediaURLProvider.url(ref)
            if (fresh != null) resolvedUrl = fresh else loadFailed = true
        } else {
            // Retry path: previous URL was stale (or the load failed) —
            // force a fresh SAS.
            val fresh = MediaURLProvider.refresh(ref)
            if (fresh != null) resolvedUrl = fresh else loadFailed = true
        }
    }

    Box(
        modifier = modifier.background(ImagePlaceholder),
        contentAlignment = Alignment.Center
    ) {
        when {
            ref == null || loadFailed -> Unit
            localPath != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(localPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                    onError = {
                        // Corrupt/partial local file — fall through to the
                        // network path instead of leaving a grey box.
                        localPath = null
                        retryCount = 1
                    }
                )
            }
            resolvedUrl != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(resolvedUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                    onError = { state ->
                        val code = (state.result.throwable as? HttpException)?.response?.code
                        if (code == 401 || code == 403 || code == 410) MediaURLProvider.invalidate(ref)
                        if (retryCount == 0) retryCount = 1 else loadFailed = true
                    }
                )
            }
            else -> CircularProgressIndicator()
        }
    }
}

/** Convenience overload for Compose previews / placeholders. */
@Composable
fun FLImageView(
    height: Dp,
    modifier: Modifier = Modifier
) = FLImageView(ref = null, height = height, modifier = modifier)
