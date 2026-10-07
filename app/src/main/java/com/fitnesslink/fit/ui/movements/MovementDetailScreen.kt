package com.fitnesslink.fit.ui.movements

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.fitnesslink.fit.model.api.SharedContent
import com.fitnesslink.fit.model.api.ShareableContentKind as ModelShareKind
import com.fitnesslink.fit.model.api.ShareStatus as ModelShareStatus
import com.fitnesslink.fit.movements.MovementStore
import com.fitnesslink.fit.movements.toMediaStatus
import com.fitnesslink.fit.network.dto.ShareStatus
import com.fitnesslink.fit.network.dto.ShareableContentKind
import com.fitnesslink.fit.network.dto.SharedContentDto
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.components.BackCircleButton
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.PendingShareBanner
import com.fitnesslink.fit.ui.components.PrimaryButtonView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.components.ShareSheet
import com.fitnesslink.fit.ui.components.SharedBadge
import com.fitnesslink.fit.ui.session.MuxVideoPlayer
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.ui.workout.NoPreviewPlaceholder
import com.fitnesslink.fit.ui.workout.WatchDemoButton
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

/**
 * Movement detail (iOS `MovementDetailView`): inline looping video hero
 * (landscape form video first, "Watch Demo" mounts it) with a fullscreen
 * player and poster; anatomy / equipment chips; summary + typed sections;
 * share-to-user for own movements, remove-from-library for shared-in ones,
 * pending-share accept / decline; records a recent on open; "Build a Workout".
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MovementDetailScreen(
    movementId: String,
    onBack: () -> Unit,
    onBuildWorkout: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var movement by remember(movementId) { mutableStateOf<Movement?>(MovementStore.cachedDetail(movementId)) }
    var fallback by remember(movementId) { mutableStateOf<MovementLibraryItem?>(null) }
    var isLoading by remember(movementId) { mutableStateOf(true) }
    var showVideo by remember(movementId) { mutableStateOf(false) }
    var showFullscreen by remember(movementId) { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showRemoveAlert by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingShare by remember(movementId) { mutableStateOf<SharedContentDto?>(null) }
    var isRespondingToShare by remember { mutableStateOf(false) }

    val anatomy by ClassificationStore.anatomy.collectAsState()
    val equipment by ClassificationStore.equipment.collectAsState()

    val displayName = movement?.name ?: fallback?.name ?: "Movement"
    val anatomyIds = movement?.anatomyIds?.takeIf { it.isNotEmpty() }?.map { it.toString() } ?: fallback?.anatomyIds ?: emptyList()
    val equipmentIds = movement?.equipmentIds?.takeIf { it.isNotEmpty() }?.map { it.toString() } ?: fallback?.equipmentIds ?: emptyList()
    val anatomyNames = remember(anatomyIds, anatomy) { anatomyIds.mapNotNull { ClassificationStore.anatomyName(it) } }
    val equipmentNames = remember(equipmentIds, equipment) { equipmentIds.mapNotNull { ClassificationStore.equipmentName(it) } }

    val videoId: String? = movement?.inlineVideoId?.toString() ?: fallback?.inlineVideoId
    val fullscreenVideoId: String? = movement?.effectiveVideoId?.toString() ?: fallback?.effectiveVideoId
    val thumbnailId: String? = movement?.thumbnailId?.toString() ?: fallback?.thumbnailId
    val imageId: String? = movement?.imageId?.toString() ?: fallback?.imageId
    val posterRef = (thumbnailId ?: imageId)?.let { MediaRef.MovementThumbnail(it) }
    val isPortraitVideo = remember(movement) { isPortrait(movement?.inlineVideoAspectRatio) }
    val heroHeight = if (isPortraitVideo) 440.dp else 250.dp

    val isOwn = movement?.let { it.contributorId == null && it.sharedByUserId == null } ?: false
    val isSharedIn = movement?.let { it.contributorId == null && it.sharedByUserId != null } ?: (fallback?.isSharedWithMe == true)

    suspend fun load() {
        fallback = runCatching { DatabaseManager.movement(movementId) }.getOrNull()
        MovementStore.recordRecent(movementId)
        MovementStore.prefetchVideos(listOfNotNull(videoId, fullscreenVideoId))
        // Fetch without caching first — a shared-in movement awaiting the user's
        // decision is previewed from the network only.
        val full = MovementStore.hydrate(movementId, cacheResult = false)
        if (full != null) {
            movement = full
            if (full.isSharedWithMe == true) {
                val pending = MovementStore.pendingShare(movementId)
                if (pending != null) { pendingShare = pending; isLoading = false; return }
            }
            pendingShare = null
            MovementStore.cacheFull(full)
            MovementStore.prefetchVideos(listOfNotNull(full.inlineVideoId?.toString(), full.effectiveVideoId?.toString()))
        }
        isLoading = false
    }

    LaunchedEffect(movementId) { load() }

    if (showFullscreen && fullscreenVideoId != null) {
        FullscreenVideoPlayer(videoId = fullscreenVideoId, url = movement?.effectiveVideoUrl, onClose = { showFullscreen = false })
    }

    if (showShareSheet) {
        ShareSheet(contentId = movementId, kind = ModelShareKind.MOVEMENT, onDismiss = { showShareSheet = false })
    }

    if (showRemoveAlert) {
        AlertDialog(
            onDismissRequest = { showRemoveAlert = false },
            title = { Text("Remove from your library?") },
            text = { Text("This removes the shared movement from your library. It won't delete the original or affect who shared it.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveAlert = false
                    scope.launch {
                        try { MovementStore.removeFromLibrary(movementId); onBack() } catch (e: Exception) {
                            errorMessage = "Something went wrong removing this movement. Please try again."
                        }
                    }
                }) { Text("Remove", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showRemoveAlert = false }) { Text("Cancel") } }
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Couldn't update") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        // Hero
        Box(modifier = Modifier.fillMaxWidth().height(if (showVideo && videoId != null) heroHeight else 250.dp)) {
            when {
                showVideo && videoId != null -> {
                    MuxVideoPlayer(
                        ref = MediaRef.MovementVideo(videoId),
                        modifier = Modifier.fillMaxSize(),
                        url = movement?.inlineVideoUrl,
                        loop = true,
                        fill = isPortraitVideo,
                        muted = true,
                        paused = showFullscreen,
                        videoStatus = movement?.inlineVideoStatus.toMediaStatus(),
                        posterRef = posterRef
                    )
                    if (fullscreenVideoId != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 56.dp, end = 12.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { showFullscreen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                videoId != null || posterRef != null -> {
                    if (posterRef != null) {
                        FLImageView(ref = posterRef, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)))
                    }
                    if (videoId != null) {
                        WatchDemoButton(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), onClick = { showVideo = true })
                    }
                }
                else -> NoPreviewPlaceholder()
            }

            // Top gradient + controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
            )
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackCircleButton(onClick = onBack)
                Spacer(modifier = Modifier.weight(1f))
                when {
                    isOwn -> HeroIconButton(Icons.Default.Group, "Share") { showShareSheet = true }
                    isSharedIn && pendingShare == null -> HeroIconButton(Icons.Default.PersonRemove, "Remove from library") { showRemoveAlert = true }
                }
            }
            if (isLoading && movement == null && fallback == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = White)
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(displayName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)

            if (movement?.isSharedWithMe == true || fallback?.isSharedWithMe == true) SharedBadge()

            pendingShare?.let { share ->
                PendingShareBanner(
                    share = share.toModel(),
                    kindLabel = "movement",
                    onAccept = {
                        if (isRespondingToShare) return@PendingShareBanner
                        isRespondingToShare = true
                        scope.launch {
                            try {
                                MovementStore.acceptShare(share.id)
                                pendingShare = null
                                load()
                            } catch (e: Exception) {
                                errorMessage = "Something went wrong responding to this share. Please try again."
                            } finally { isRespondingToShare = false }
                        }
                    },
                    onDecline = {
                        if (isRespondingToShare) return@PendingShareBanner
                        isRespondingToShare = true
                        scope.launch {
                            try {
                                MovementStore.declineShare(share.id, movementId)
                                pendingShare = null
                                onBack()
                            } catch (e: Exception) {
                                errorMessage = "Something went wrong responding to this share. Please try again."
                            } finally { isRespondingToShare = false }
                        }
                    }
                )
            }

            if (anatomyNames.isNotEmpty() || equipmentNames.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    anatomyNames.forEach { MovementTagChip(it, Icons.Default.SportsGymnastics, FLPrimary) }
                    equipmentNames.forEach { MovementTagChip(it, Icons.Default.FitnessCenter, BlueTheme) }
                }
            }

            MovementContentView(summary = movement?.summary, sections = movement?.sections ?: emptyList())

            if (movement?.summary.isNullOrBlank() && movement?.sections.isNullOrEmpty()) {
                val description = movement?.description ?: fallback?.description
                if (!description.isNullOrBlank()) Text(description, fontSize = 15.sp, color = TextPrimaryColor)
            }
        }

        // Action bar
        Column(modifier = Modifier.fillMaxWidth().background(White)) {
            SeparatorView()
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).clickable { onBuildWorkout(movementId) }) {
                PrimaryButtonView(text = "Build a Workout")
            }
        }
    }
}

@Composable
private fun HeroIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = White, modifier = Modifier.size(16.dp))
    }
}

/** Fullscreen modal hosting a single player with controls + audio (iOS `FullscreenVideoPlayer`). */
@Composable
private fun FullscreenVideoPlayer(videoId: String, url: String?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            MuxVideoPlayer(
                ref = MediaRef.MovementVideo(videoId),
                modifier = Modifier.fillMaxSize(),
                url = url,
                loop = true, fill = false, muted = false,
                handleAudioFocus = true, showControls = true
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** "W:H" → true when taller than wide (a 9:16 phone variant); false when unknown. */
internal fun isPortrait(aspectRatio: String?): Boolean {
    val parts = aspectRatio?.split(":")?.mapNotNull { it.trim().toDoubleOrNull() } ?: return false
    if (parts.size != 2 || parts[0] <= 0 || parts[1] <= 0) return false
    return parts[0] < parts[1]
}

/** Bridge the wire DTO to the model the shared banner renders. */
internal fun SharedContentDto.toModel(): SharedContent = SharedContent(
    id = uuidOrZero(id),
    sharedByUserId = uuidOrZero(sharedByUserId),
    sharedByUserName = sharedByUserName,
    sharedWithUserId = uuidOrZero(sharedWithUserId),
    kind = when (kind) {
        ShareableContentKind.PROGRAM -> ModelShareKind.PROGRAM
        ShareableContentKind.WORKOUT -> ModelShareKind.WORKOUT
        ShareableContentKind.MEAL_PLAN -> ModelShareKind.MEAL_PLAN
        ShareableContentKind.MOVEMENT, null -> ModelShareKind.MOVEMENT
    },
    contentId = uuidOrZero(contentId),
    contentTitle = contentTitle,
    imageId = imageId?.let { runCatching { UUID.fromString(it) }.getOrNull() },
    sharedAtUtc = sharedAtUtc ?: Date(),
    status = when (status) {
        ShareStatus.PENDING -> ModelShareStatus.PENDING
        ShareStatus.REJECTED -> ModelShareStatus.REJECTED
        ShareStatus.REMOVED -> ModelShareStatus.REMOVED
        ShareStatus.ACCEPTED, null -> ModelShareStatus.ACCEPTED
    }
)

private fun uuidOrZero(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrDefault(UUID(0L, 0L))
