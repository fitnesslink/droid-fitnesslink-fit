package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fitnesslink.fit.media.MediaCacheStore
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.media.MediaURLProvider
import com.fitnesslink.fit.ui.theme.ImagePlaceholder

/**
 * Renders an image referenced by a stable MediaRef. Resolves the URL via
 * MediaURLProvider, prefers a local prefetched file when present, and on
 * a failed load invalidates the cached URL and retries once with a fresh
 * SAS.
 */
@Composable
fun FLImageView(
    ref: MediaRef?,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var resolvedUrl by remember(ref) { mutableStateOf<String?>(null) }
    var localPath by remember(ref) { mutableStateOf<String?>(null) }
    var retryCount by remember(ref) { mutableStateOf(0) }
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
            val local = MediaCacheStore.localFile(ref)?.absolutePath
            if (local != null) {
                localPath = local
                return@LaunchedEffect
            }
            val cached = MediaURLProvider.cached(ref)
            if (cached != null) {
                resolvedUrl = cached
                return@LaunchedEffect
            }
        } else {
            // Retry path: previous URL was stale, force a refresh.
            MediaURLProvider.invalidate(ref)
        }

        val fresh = MediaURLProvider.url(ref)
        if (fresh != null) {
            resolvedUrl = fresh
        } else {
            loadFailed = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(ImagePlaceholder),
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
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(height)
                )
            }
            resolvedUrl != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(resolvedUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(height),
                    onError = {
                        if (retryCount == 0) {
                            retryCount = 1
                        } else {
                            loadFailed = true
                        }
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
