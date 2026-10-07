package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.api.Movement
import com.fitnesslink.fit.movements.MovementStore
import com.fitnesslink.fit.movements.toMediaStatus
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.movements.MovementContentView
import com.fitnesslink.fit.ui.movements.MovementTagChip
import com.fitnesslink.fit.ui.session.MuxVideoPlayer
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * Modal preview for a movement before the user commits to adding it (iOS
 * `MovementPreviewSheet`). Hydrates the full record via `GET /movements/{id}`
 * (the list-view row has no summary / sections), prefers the landscape form
 * video for the inline 220dp player ("Watch Demo" mounts it), renders
 * taxonomy chips through `ClassificationStore`, and the structured
 * `MovementContentView`. Add flows through [onAdd] like the row tap.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MovementPreviewSheet(
    movement: MovementLibraryItem,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    addLabel: String = "Add to Workout"
) {
    var hydrated by remember(movement.id) { mutableStateOf<Movement?>(MovementStore.cachedDetail(movement.id)) }
    var showVideo by remember(movement.id) { mutableStateOf(false) }
    // Keep the store flows hot so chip names resolve once the taxonomy lands.
    val anatomy by ClassificationStore.anatomy.collectAsState()
    val equipment by ClassificationStore.equipment.collectAsState()

    val videoId: String? = hydrated?.inlineVideoId?.toString() ?: movement.inlineVideoId
    val thumbnailId: String? = hydrated?.thumbnailId?.toString() ?: movement.thumbnailId
    val imageId: String? = hydrated?.imageId?.toString() ?: movement.imageId
    val posterRef = (thumbnailId ?: imageId)?.let { MediaRef.MovementThumbnail(it) }

    LaunchedEffect(movement.id) {
        videoId?.let { MovementStore.prefetchVideos(listOf(it)) }
        MovementStore.hydrate(movement.id)?.let { full ->
            hydrated = full
            full.inlineVideoId?.let { MovementStore.prefetchVideos(listOf(it.toString())) }
        }
    }

    val anatomyIds = hydrated?.anatomyIds?.takeIf { it.isNotEmpty() }?.map { it.toString() } ?: movement.anatomyIds
    val equipmentIds = hydrated?.equipmentIds?.takeIf { it.isNotEmpty() }?.map { it.toString() } ?: movement.equipmentIds
    val anatomyNames = remember(anatomyIds, anatomy) { anatomyIds.mapNotNull { ClassificationStore.anatomyName(it) } }
    val equipmentNames = remember(equipmentIds, equipment) { equipmentIds.mapNotNull { ClassificationStore.equipmentName(it) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(BackgroundColor)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimaryColor) }
            }

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Video / hero
                Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    when {
                        showVideo && videoId != null -> MuxVideoPlayer(
                            ref = MediaRef.MovementVideo(videoId),
                            modifier = Modifier.fillMaxSize(),
                            url = hydrated?.inlineVideoUrl,
                            loop = true, fill = false, muted = true,
                            videoStatus = hydrated?.inlineVideoStatus.toMediaStatus(),
                            posterRef = posterRef, posterHeight = 220.dp
                        )
                        videoId != null || posterRef != null -> {
                            if (posterRef != null) {
                                FLImageView(ref = posterRef, height = 220.dp, modifier = Modifier.fillMaxWidth())
                            } else {
                                // Mux-only asset: no separate thumbnail exists, so a neutral backdrop
                                // keeps the Watch Demo button reachable.
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)))
                            }
                            if (videoId != null) {
                                WatchDemoButton(
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                                    onClick = { showVideo = true }
                                )
                            }
                        }
                        else -> NoPreviewPlaceholder()
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(movement.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    if (anatomyNames.isNotEmpty() || equipmentNames.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            anatomyNames.forEach { MovementTagChip(it, Icons.Default.SportsGymnastics, FLPrimary) }
                            equipmentNames.forEach { MovementTagChip(it, Icons.Default.FitnessCenter, BlueTheme) }
                        }
                    }
                }

                MovementContentView(
                    summary = hydrated?.summary,
                    sections = hydrated?.sections ?: emptyList(),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                // Legacy plain description when there is no structured content yet.
                if (hydrated?.summary.isNullOrBlank() && (hydrated?.sections.isNullOrEmpty())) {
                    val description = hydrated?.description ?: movement.description
                    if (description.isNotBlank()) {
                        Text(description, fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }

                if (anatomyNames.isNotEmpty() || equipmentNames.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                        if (anatomyNames.isNotEmpty()) DetailRow(Icons.Default.SportsGymnastics, "Muscle Group", anatomyNames.joinToString(", "))
                        if (equipmentNames.isNotEmpty()) DetailRow(Icons.Default.FitnessCenter, "Equipment", equipmentNames.joinToString(", "))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(FLPrimary)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(addLabel, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = White)
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
internal fun WatchDemoButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(FLPrimary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
        Text("Watch Demo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = White)
    }
}

@Composable
internal fun NoPreviewPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = White.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
            Text("No Preview Available", fontSize = 14.sp, color = White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
    }
}
