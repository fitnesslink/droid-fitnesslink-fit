package com.fitnesslink.fit.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.launch

/**
 * FA-97 — favorite + share row, slotted into Workout/Program detail
 * top bars. Share goes through the system chooser with a text payload
 * so it works without a dedicated recipient picker on Android. Favorite
 * toggles ContentApi.toggleFavorite optimistically and reads the
 * authoritative state from getFavorites on first composition.
 */
enum class ContentKind { Workout, Program }

@Composable
fun ContentActionsRow(
    contentId: String,
    contentKind: ContentKind,
    contentTitle: String,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isFavorite by remember(contentId) { mutableStateOf(false) }

    // Pull authoritative favorite state on first composition. Best-effort —
    // no spinner or error UI; on failure the toggle simply starts at false.
    LaunchedEffect(contentId) {
        try {
            val ids = ApiClient.contentApi.getFavorites().map { it.contentId.toString() }
            isFavorite = ids.contains(contentId)
        } catch (_: Exception) { /* leave default */ }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionCircle(
            onClick = {
                isFavorite = !isFavorite
                scope.launch {
                    try { ApiClient.contentApi.toggleFavorite(contentId) } catch (_: Exception) {}
                }
            }
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                tint = if (isFavorite) Color(0xFFEF4444) else White,
                modifier = Modifier.size(18.dp)
            )
        }
        ActionCircle(
            onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    val label = if (contentKind == ContentKind.Program) "program" else "workout"
                    putExtra(Intent.EXTRA_SUBJECT, "Check out this $label on FitnessLink")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "I've been training with \"$contentTitle\" on FitnessLink — give it a look."
                    )
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(Intent.createChooser(intent, "Share"))
                }
            }
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "Share",
                tint = White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ActionCircle(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(FLPrimary, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
