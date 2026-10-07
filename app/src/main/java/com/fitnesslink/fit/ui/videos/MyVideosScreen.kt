package com.fitnesslink.fit.ui.videos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.media.MuxStatus
import com.fitnesslink.fit.media.UploadRecord
import com.fitnesslink.fit.media.UploadState
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.session.MuxVideoPlayer
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.MyVideosViewModel

/**
 * FB-89 port of iOS `MyVideosView` + `VideoUploadRow` (parity 4.7). Pick a
 * video, hand it to `MediaUploadCoordinator` (container `videoassets`), and
 * watch each row climb Queued → Uploading % → Processing → Ready. Ready rows
 * play through `MuxVideoPlayer`; Failed (and stuck Processing) rows can be
 * removed. Upload state survives relaunch (WorkManager + prefs records).
 */
@Composable
fun MyVideosScreen(onBack: () -> Unit) {
    val viewModel: MyVideosViewModel = viewModel()
    LaunchedEffect(Unit) { viewModel.loadData() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.upload(it) }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Text("My Videos", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(FLPrimary)
                .clickable(enabled = !viewModel.isImporting) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewModel.isImporting) {
                CircularProgressIndicator(color = White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.AddCircle, contentDescription = null, tint = White, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (viewModel.isImporting) "Importing..." else "Upload Video", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
        }

        if (viewModel.records.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.VideocamOff, contentDescription = null, tint = MediumGray, modifier = Modifier.size(44.dp))
                Text("No videos yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Text(
                    "Pick a video from your library to upload.",
                    fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.records, key = { it.uploadId }) { record ->
                    val state = viewModel.states[record.uploadId] ?: UploadState.Queued
                    VideoUploadRow(
                        record = record,
                        state = state,
                        onPlay = { viewModel.play(state) },
                        onRemove = { viewModel.remove(record.uploadId) }
                    )
                }
            }
        }
    }

    viewModel.pickError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.pickError = null },
            title = { Text("Couldn't import video") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { viewModel.pickError = null }) { Text("OK") } }
        )
    }

    viewModel.playingUrl?.let { url ->
        Dialog(onDismissRequest = { viewModel.playingUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                MuxVideoPlayer(
                    ref = null,
                    url = url,
                    loop = false,
                    fill = false,
                    muted = false,
                    handleAudioFocus = true,
                    showControls = true,
                    videoStatus = MuxStatus.READY,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).align(Alignment.Center)
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(28.dp)
                        .clickable { viewModel.playingUrl = null }
                )
            }
        }
    }
}

@Composable
fun VideoUploadRow(record: UploadRecord, state: UploadState, onPlay: () -> Unit, onRemove: () -> Unit) {
    val (icon, tint) = when (state) {
        is UploadState.Queued, is UploadState.Uploading -> Icons.Default.Upload to FLPrimary
        is UploadState.Processing -> Icons.Default.HourglassBottom to OrangeTheme
        is UploadState.Ready -> Icons.Default.CheckCircle to Color(0xFF2E9E6B)
        is UploadState.Failed -> Icons.Default.Warning to Color.Red
    }
    val statusLabel = when (state) {
        is UploadState.Queued -> "Queued"
        is UploadState.Uploading -> "Uploading ${(state.fraction * 100).toInt()}%"
        is UploadState.Processing -> "Processing video..."
        is UploadState.Ready -> "Ready to play"
        is UploadState.Failed -> "Failed"
    }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.fileName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(statusLabel, fontSize = 13.sp, color = TextSecondaryColor)
            }
            when (state) {
                is UploadState.Ready -> Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(FLPrimary).clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = White) }
                is UploadState.Failed, is UploadState.Processing -> Icon(
                    Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red,
                    modifier = Modifier.size(32.dp).clickable(onClick = onRemove).padding(6.dp)
                )
                else -> Unit
            }
        }
        when (state) {
            is UploadState.Queued -> LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth(), color = FLPrimary)
            is UploadState.Uploading -> LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth(), color = FLPrimary)
            is UploadState.Processing -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp), color = OrangeTheme)
            is UploadState.Failed -> Text(state.message, fontSize = 12.sp, color = Color.Red, maxLines = 3)
            is UploadState.Ready -> Unit
        }
    }
}
