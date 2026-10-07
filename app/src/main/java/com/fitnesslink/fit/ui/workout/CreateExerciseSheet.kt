package com.fitnesslink.fit.ui.workout

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.media.MediaUploadCoordinator
import com.fitnesslink.fit.media.UploadState
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.movements.MovementStore
import com.fitnesslink.fit.ui.movements.ClassificationGroup
import com.fitnesslink.fit.ui.movements.ClassificationPickerView
import com.fitnesslink.fit.ui.session.MuxVideoPlayer
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Sheet wrapper for the workout editor's create-exercise flow (iOS
 * `CreateExerciseSheet`). The form itself is [MovementCreateForm], shared with
 * the catalog's `MovementEditorScreen` so the two create surfaces can't drift.
 */
@Composable
fun CreateExerciseSheet(
    onCreated: (MovementLibraryItem) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(BackgroundColor)
        ) {
            MovementCreateForm(
                title = "New Exercise",
                onCancel = onDismiss,
                onCreated = onCreated
            )
        }
    }
}

/**
 * The shared create-movement form (iOS `MovementCreateForm`): name,
 * description, still image (uploaded at Create time → `imageId` =
 * `thumbnailId`), multi-select anatomy + equipment from the server taxonomy,
 * and the form video slot. Owns the Create action; calls [onCreated] with the
 * new library item and expects the host to dismiss.
 */
@Composable
fun MovementCreateForm(
    title: String,
    onCancel: () -> Unit,
    onCreated: (MovementLibraryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedAnatomyIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedEquipmentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<FormPicker?>(null) }

    var thumbUri by remember { mutableStateOf<Uri?>(null) }
    var thumbError by remember { mutableStateOf<String?>(null) }
    val thumbPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { thumbUri = uri; thumbError = null }
    }

    val formVideo = remember { FormVideoUploadModel() }

    LaunchedEffect(Unit) {
        // Warm the taxonomy so the pickers aren't empty on first open.
        runCatching { com.fitnesslink.fit.classification.ClassificationStore.refresh() }
    }

    fun create() {
        if (name.isBlank() || isSaving) return
        isSaving = true
        saveError = null
        scope.launch {
            try {
                var uploadedImageId: String? = null
                thumbUri?.let { uri ->
                    thumbError = null
                    try {
                        uploadedImageId = MovementStore.uploadStillImage(context, uri)
                    } catch (e: Exception) {
                        thumbError = "Couldn't upload the image — try again, or remove it to create without one."
                        return@launch
                    }
                }
                val result = MovementStore.create(
                    MovementStore.CreateInput(
                        name = name.trim(),
                        description = description.trim(),
                        anatomyIds = selectedAnatomyIds.toList(),
                        equipmentIds = selectedEquipmentIds.toList(),
                        imageId = uploadedImageId,
                        uploadId = formVideo.uploadId
                    )
                )
                onCreated(result.item)
            } catch (e: Exception) {
                saveError = "Couldn't create the exercise. Please try again."
            } finally {
                isSaving = false
            }
        }
    }

    when (picker) {
        FormPicker.ANATOMY -> {
            ClassificationPickerView(
                title = "Anatomy",
                groups = remember { ClassificationGroup.anatomyGroups() },
                selectedIds = selectedAnatomyIds,
                onToggle = { id -> selectedAnatomyIds = selectedAnatomyIds.toggled(id) },
                onDone = { picker = null },
                modifier = modifier
            )
            return
        }
        FormPicker.EQUIPMENT -> {
            ClassificationPickerView(
                title = "Equipment",
                groups = remember { ClassificationGroup.equipmentGroups() },
                selectedIds = selectedEquipmentIds,
                onToggle = { id -> selectedEquipmentIds = selectedEquipmentIds.toggled(id) },
                onDone = { picker = null },
                modifier = modifier
            )
            return
        }
        null -> Unit
    }

    Column(modifier = modifier.fillMaxSize().background(BackgroundColor)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel, enabled = !isSaving) { Text("Cancel", color = TextPrimaryColor) }
            Text(
                title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            TextButton(onClick = { create() }, enabled = name.isNotBlank() && !isSaving) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = FLPrimary)
                } else {
                    Text("Create", fontWeight = FontWeight.SemiBold, color = if (name.isNotBlank()) FLPrimary else MediumGray)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FormSectionHeader("Exercise Details")
            FormCard {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Exercise Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description (optional)") }, minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                )
            }

            FormSectionHeader("Thumbnail Image")
            FormCard {
                val uri = thumbUri
                if (uri != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(10.dp))) {
                        AsyncImage(
                            model = uri, contentDescription = "Selected still image",
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                .clickable { thumbPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        )
                        Icon(
                            Icons.Default.Edit, contentDescription = "Change image", tint = White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp)
                        )
                    }
                    TextButton(onClick = { thumbUri = null; thumbError = null }) {
                        Text("Remove Image", fontSize = 13.sp, color = Color.Red)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { thumbPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Add a still image", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                    }
                    Text("Optional. Shown in the editor and exercise browser.", fontSize = 12.sp, color = TextSecondaryColor)
                }
                thumbError?.let { Text(it, fontSize = 12.sp, color = Color.Red) }
            }

            FormSectionHeader("Anatomy")
            FormCard(padding = 0.dp) {
                NavigationRow("Select muscles", if (selectedAnatomyIds.isEmpty()) "None" else "${selectedAnatomyIds.size} selected") { picker = FormPicker.ANATOMY }
            }

            FormSectionHeader("Equipment")
            FormCard(padding = 0.dp) {
                NavigationRow("Select equipment", if (selectedEquipmentIds.isEmpty()) "None" else "${selectedEquipmentIds.size} selected") { picker = FormPicker.EQUIPMENT }
            }

            FormSectionHeader("Form Video")
            FormCard { FormVideoSlot(model = formVideo) }

            saveError?.let { Text(it, fontSize = 12.sp, color = Color.Red, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
    }
}

private enum class FormPicker { ANATOMY, EQUIPMENT }

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FLPrimary, cursorColor = FLPrimary, focusedLabelColor = FLPrimary,
    unfocusedContainerColor = White, focusedContainerColor = White
)

@Composable
private fun FormSectionHeader(title: String) {
    Text(
        title.uppercase(Locale.US), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
    )
}

@Composable
private fun FormCard(padding: androidx.compose.ui.unit.Dp = 14.dp, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(padding)
    ) { content() }
}

@Composable
private fun NavigationRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = TextSecondaryColor)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray)
    }
}

private fun Set<String>.toggled(id: String): Set<String> =
    if (any { it.equals(id, ignoreCase = true) }) filterNot { it.equals(id, ignoreCase = true) }.toSet() else this + id

// MARK: - Form video

/**
 * Owns the pending form-video upload for a create screen (iOS
 * `FormVideoUploadModel`). Validates type / size / duration, hands the file to
 * `MediaUploadCoordinator` (container `movementvideos`, as iOS), and exposes the live
 * [state]. The host reads [uploadId] on Create for the deferred attach.
 */
class FormVideoUploadModel {
    companion object {
        const val MAX_DURATION_SECONDS = 60
        const val MAX_FILE_SIZE_BYTES: Long = 200L * 1024 * 1024
    }

    var uploadId by mutableStateOf<String?>(null)
        private set
    var state by mutableStateOf<UploadState?>(null)
        private set
    var validationError by mutableStateOf<String?>(null)
    var isPreparing by mutableStateOf(false)
        private set

    val hasPendingVideo: Boolean get() = uploadId != null

    suspend fun handlePicked(context: Context, uri: Uri) {
        validationError = null
        isPreparing = true
        try {
            val mime = context.contentResolver.getType(uri) ?: guessMime(uri)
            if (!mime.startsWith("video/")) {
                validationError = "That file isn't a video. Please choose a video clip."
                return
            }
            validateSize(context, uri)?.let { validationError = it; return }
            validateDuration(context, uri)?.let { validationError = it; return }

            uploadId?.let { runCatching { MediaUploadCoordinator.remove(it) } }
            uploadId = null
            state = null
            try {
                val id = MediaUploadCoordinator.enqueue(fileUri = uri, container = MovementStore.FORM_VIDEO_CONTAINER, mimeType = mime)
                uploadId = id
                state = UploadState.Queued
            } catch (e: Exception) {
                validationError = "Couldn't start the upload: ${e.message ?: "unknown error"}"
            }
        } finally {
            isPreparing = false
        }
    }

    /** Observe the coordinator for the current upload; call from a LaunchedEffect keyed on [uploadId]. */
    suspend fun observe() {
        val id = uploadId ?: return
        MediaUploadCoordinator.observe(id).collectLatest { state = it }
    }

    fun remove() {
        uploadId?.let { runCatching { MediaUploadCoordinator.remove(it) } }
        uploadId = null
        state = null
        validationError = null
    }

    private suspend fun validateSize(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val size = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: return@withContext null
        if (size <= 0 || size <= MAX_FILE_SIZE_BYTES) null
        else "That video is too large. Please keep form videos under ${MAX_FILE_SIZE_BYTES / (1024 * 1024)} MB."
    }

    private suspend fun validateDuration(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val ms = runCatching {
            MediaMetadataRetriever().use { r ->
                r.setDataSource(context, uri)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            }
        }.getOrNull() ?: return@withContext null
        if (ms <= 0 || ms <= MAX_DURATION_SECONDS * 1000L) null
        else "That video is too long. Please keep form videos under $MAX_DURATION_SECONDS seconds."
    }

    private fun guessMime(uri: Uri): String = when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.US)) {
        "mp4", "m4v" -> "video/mp4"
        "mov", "qt" -> "video/quicktime"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        else -> "video/mp4"
    }
}

/**
 * The Form Video section body (iOS `FormVideoSlot`): Record / Choose entry
 * points, a progress + status readout while uploading / ingesting, an inline
 * preview once Ready, and remove / retry throughout.
 *
 * Record uses the system camera (`MediaStore.ACTION_VIDEO_CAPTURE`, 60 s
 * limit) — CameraX `VideoCapture` lives in `camera-video`, which is not among
 * the app's CameraX artifacts, and the dependency file is off-limits here.
 */
@Composable
fun FormVideoSlot(model: FormVideoUploadModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { model.handlePicked(context, uri) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            scope.launch { model.handlePicked(context, uri) }
        }
    }
    val cameraAvailable = remember {
        Intent(MediaStore.ACTION_VIDEO_CAPTURE).resolveActivity(context.packageManager) != null
    }

    LaunchedEffect(model.uploadId) { model.observe() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val state = model.state
        when {
            state != null -> FormVideoStatus(state = state, onRemove = { model.remove() }, onRetry = {
                model.remove()
            })
            model.isPreparing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = FLPrimary)
                Text("Preparing video…", fontSize = 14.sp, color = TextSecondaryColor)
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (cameraAvailable) {
                        CaptureButton(
                            label = "Record", icon = Icons.Default.Videocam, filled = true,
                            modifier = Modifier.weight(1f)
                        ) {
                            cameraLauncher.launch(
                                Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                                    .putExtra(MediaStore.EXTRA_DURATION_LIMIT, FormVideoUploadModel.MAX_DURATION_SECONDS)
                                    .putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                            )
                        }
                    }
                    CaptureButton(
                        label = "Choose", icon = Icons.Default.PhotoLibrary, filled = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                }
                Text("Optional. Record or choose a short clip demonstrating the movement.", fontSize = 12.sp, color = TextSecondaryColor)
            }
        }
        model.validationError?.let { Text(it, fontSize = 12.sp, color = Color.Red) }
    }
}

@Composable
private fun CaptureButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) FLPrimary else FLPrimary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) White else FLPrimary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (filled) White else FLPrimary)
    }
}

@Composable
private fun FormVideoStatus(state: UploadState, onRemove: () -> Unit, onRetry: () -> Unit) {
    when (state) {
        UploadState.Queued -> StatusRow(text = "Waiting to upload…", trailing = "Cancel", onTrailing = onRemove) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = FLPrimary, trackColor = BackgroundColor)
        }
        is UploadState.Uploading -> StatusRow(text = "Uploading ${(state.fraction * 100).toInt()}%", trailing = "Cancel", onTrailing = onRemove) {
            LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth(), color = FLPrimary, trackColor = BackgroundColor)
        }
        is UploadState.Processing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = FLPrimary)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Processing video…", fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.weight(1f))
            TextButton(onClick = onRemove) { Text("Remove", fontSize = 13.sp, color = Color.Red) }
        }
        is UploadState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.mediaId.isNotEmpty()) {
                MuxVideoPlayer(
                    ref = MediaRef.MovementVideo(state.mediaId),
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(10.dp)),
                    url = state.videoUrl,
                    loop = true, fill = false, muted = false, showControls = true,
                    videoStatus = com.fitnesslink.fit.media.MuxStatus.READY
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Video ready", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text("Remove", fontSize = 13.sp, color = Color.Red) }
            }
        }
        is UploadState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload failed", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Red, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("Try again", fontSize = 13.sp, color = FLPrimary) }
                TextButton(onClick = onRemove) { Text("Remove", fontSize = 13.sp, color = Color.Red) }
            }
            Text(state.message, fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun StatusRow(text: String, trailing: String, onTrailing: () -> Unit, progress: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor, modifier = Modifier.weight(1f))
            TextButton(onClick = onTrailing) { Text(trailing, fontSize = 13.sp, color = Color.Red) }
        }
        progress()
    }
}
