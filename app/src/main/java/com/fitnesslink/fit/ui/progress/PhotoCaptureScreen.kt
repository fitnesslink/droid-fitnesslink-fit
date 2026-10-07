package com.fitnesslink.fit.ui.progress

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fitnesslink.fit.model.PhotoAngle
import com.fitnesslink.fit.model.ProgressPhotoEntry
import com.fitnesslink.fit.progress.BodyTrackingStore
import com.fitnesslink.fit.progress.CaptureSegment
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.PhotoCaptureViewModel
import java.io.File
import java.util.UUID

/**
 * iOS `PhotoCaptureFlowView` (route `photoCapture`): Front → Side → Back
 * with a 3-segment progress bar, per-angle header icon and copy, Camera
 * (CameraX `ImageCapture`, which iOS never wired) or Library
 * (`PickVisualMedia`), preview with Retake, per-angle notes, Next / Finish,
 * "Save N Photos Now", "Skip {angle}", an upload overlay and an error alert.
 * Finishing runs `BodyTrackingStore.uploadPhotos` (two hops per angle).
 */
@Composable
fun PhotoCaptureScreen(
    onClose: () -> Unit,
    onSaved: (ProgressPhotoEntry) -> Unit = { onClose() }
) {
    val viewModel: PhotoCaptureViewModel = viewModel()
    val context = LocalContext.current
    var showCamera by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { BodyTrackingStore.attach(context) }
    LaunchedEffect(viewModel.isComplete) { if (viewModel.isComplete) viewModel.upload() }
    LaunchedEffect(viewModel.savedEntry) { viewModel.savedEntry?.let(onSaved) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.setPhoto(uri)
    }

    val angle = viewModel.currentAngle

    Box(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: Cancel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "Cancel", fontSize = 14.sp, color = TextSecondaryColor,
                    modifier = Modifier
                        .clickable(onClick = onClose)
                        .padding(8.dp)
                )
            }

            // 3-segment progress
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.state.segments().forEach { segment ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                when (segment) {
                                    CaptureSegment.DONE -> FLPrimary
                                    CaptureSegment.CURRENT -> FLPrimary.copy(alpha = 0.4f)
                                    CaptureSegment.PENDING -> Color.Gray.copy(alpha = 0.2f)
                                },
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Angle header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(40.dp))
                    Text("${angle.displayName} Photo", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    Text(
                        "Take or upload a ${angle.displayName.lowercase()} angle photo",
                        fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center
                    )
                }

                // Preview / camera / placeholder
                val current = viewModel.currentUri
                when {
                    current != null -> {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.05f))
                        ) {
                            AsyncImage(
                                model = current,
                                contentDescription = "${angle.displayName} photo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { viewModel.retakeCurrentAngle() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
                                Text("Retake", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = White)
                            }
                        }
                    }
                    showCamera -> {
                        CameraCapturePane(
                            onCaptured = { uri -> viewModel.setPhoto(uri); showCamera = false },
                            onClose = { showCamera = false },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Gray.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MediumGray, modifier = Modifier.size(40.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    PillButton(label = "Camera", icon = Icons.Default.CameraAlt, filled = true) { showCamera = true }
                                    PillButton(label = "Library", icon = Icons.Default.PhotoLibrary, filled = false) {
                                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                }
                            }
                        }
                    }
                }

                // Notes for this angle
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Notes (optional)", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
                    OutlinedTextField(
                        value = viewModel.currentNotes,
                        onValueChange = { viewModel.setCurrentNotes(it) },
                        placeholder = { Text("Add notes for this angle...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Actions
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (viewModel.hasCurrentPhoto) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(FLPrimary)
                                .clickable { viewModel.moveToNextAngle() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(viewModel.state.advanceLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
                        }
                    }
                    if (viewModel.state.canFinishEarly) {
                        val n = viewModel.anglesCompleted
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(FLPrimary.copy(alpha = 0.1f))
                                .clickable { viewModel.finishNow() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Save $n Photo${if (n == 1) "" else "s"} Now", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                        }
                    }
                    Text(
                        "Skip ${angle.displayName}",
                        fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.skipAngle() }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }

        if (viewModel.isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .background(White, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = FLPrimary)
                    Text("Uploading…", fontSize = 14.sp, color = TextPrimaryColor)
                }
            }
        }
    }

    viewModel.uploadError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Couldn't Save Photos") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { viewModel.dismissError() }) { Text("OK") } }
        )
    }
}

@Composable
private fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) FLPrimary else FLPrimary.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) White else FLPrimary, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (filled) White else FLPrimary)
    }
}

/**
 * Inline CameraX viewfinder with a shutter button. Requests the camera
 * permission on first use; the capture is written to the app cache and
 * handed back as a `file://` URI.
 */
@Composable
private fun CameraCapturePane(
    onCaptured: (Uri) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var captureError by remember { mutableStateOf<String?>(null) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        if (hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                captureError = e.localizedMessage ?: "Camera unavailable"
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                update = { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(previewView.context)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.Builder().requireLensFacing(lensFacing).build(), preview, imageCapture)
                        }
                    }, ContextCompat.getMainExecutor(previewView.context))
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera permission is required to take a photo. Use Library instead, or grant access.", fontSize = 14.sp, color = White, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                PillButton(label = "Grant access", icon = Icons.Default.CameraAlt, filled = true) { permissionLauncher.launch(Manifest.permission.CAMERA) }
            }
        }

        // Close
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Close, contentDescription = "Close camera", tint = White, modifier = Modifier.size(16.dp)) }

        if (hasPermission) {
            // Flip
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = White, modifier = Modifier.size(16.dp)) }

            // Shutter
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(White)
                    .clickable {
                        val file = File(context.cacheDir, "progress-${UUID.randomUUID()}.jpg")
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                onCaptured(Uri.fromFile(file))
                            }

                            override fun onError(exception: ImageCaptureException) {
                                captureError = exception.localizedMessage ?: "Couldn't take the photo"
                            }
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(White).padding(2.dp).clip(CircleShape).background(FLPrimary))
            }
        }

        captureError?.let { error ->
            Text(
                error, fontSize = 12.sp, color = White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            )
        }
    }
}
