package com.fitnesslink.fit.ui.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.session.MuxVideoPlayer
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.MovementPreview

/**
 * "Sneak Peek" strip on Program Detail (iOS `ProgramDetailView`): vertical
 * thumbnails of 2–5 movements with demo videos. Tapping one opens the
 * full-screen [SneakPeekReel].
 */
@Composable
fun SneakPeekStrip(previews: List<MovementPreview>, onOpen: (MovementPreview) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sneak Peek", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 20.dp)) {
            itemsIndexed(previews, key = { _, p -> p.id }) { _, preview ->
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(195.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .clickable { onOpen(preview) },
                    contentAlignment = Alignment.Center
                ) {
                    FLImageView(ref = preview.displayId?.let { MediaRef.MovementThumbnail(it) }, modifier = Modifier.fillMaxSize())
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Play preview",
                        tint = White.copy(alpha = 0.95f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

/**
 * Full-screen, swipeable horizontal reel of demo videos (iOS
 * `ProgramSneakPeekReelView`). Only the visible page mounts a player;
 * everything else shows its thumbnail.
 */
@Composable
fun SneakPeekReel(previews: List<MovementPreview>, startId: String, onDismiss: () -> Unit) {
    val start = previews.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = start) { previews.size }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val preview = previews[page]
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FLImageView(ref = preview.displayId?.let { MediaRef.MovementThumbnail(it) }, modifier = Modifier.fillMaxSize())
                    if (page == pagerState.currentPage) {
                        MuxVideoPlayer(
                            ref = MediaRef.MovementVideo(preview.videoId),
                            url = preview.videoUrl,
                            modifier = Modifier.fillMaxSize(),
                            loop = true,
                            fill = true,
                            muted = false,
                            handleAudioFocus = true,
                            posterRef = preview.displayId?.let { MediaRef.MovementThumbnail(it) }
                        )
                    }
                }
            }

            // Page dots
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(previews.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (index == pagerState.currentPage) White else White.copy(alpha = 0.4f))
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 50.dp, end = 16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(White.copy(alpha = 0.9f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        }
    }
}
