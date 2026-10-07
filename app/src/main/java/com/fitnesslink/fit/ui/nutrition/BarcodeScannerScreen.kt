package com.fitnesslink.fit.ui.nutrition

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fitnesslink.fit.model.BarcodeScanError
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.dto.FoodSearchResult
import com.fitnesslink.fit.nutrition.BarcodeLookupCascade
import com.fitnesslink.fit.nutrition.BarcodeLookupService
import com.fitnesslink.fit.nutrition.BarcodeScanDestination
import com.fitnesslink.fit.nutrition.FoodDetailHandoff
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.navigation.FoodLoggingRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private sealed class ScanPhase {
    data object Scanning : ScanPhase()
    data class Loading(val barcode: String) : ScanPhase()
    /** Backend catalog match — full food, catalog path (portions + /convert). */
    data class CatalogHit(val food: FoodSearchResult) : ScanPhase()
    /** Cache / OpenFoodFacts product — template path, no catalog id. */
    data class ProductHit(val entry: FoodEntry) : ScanPhase()
    /** Everything missed: the code just isn't in any database. */
    data class Miss(val barcode: String) : ScanPhase()
    /** Lookup or camera failure; [barcode] kept for one-tap retry. */
    data class Error(val error: BarcodeScanError, val barcode: String?) : ScanPhase()
}

/**
 * FTNSSLNK-198: the barcode scan flow (iOS `BarcodeScannerContainerView`).
 * Scan (ML Kit, with manual entry) → look the code up (backend catalog →
 * device cache → OpenFoodFacts) → land on the Food Detail screen inline —
 * nothing is logged until the user saves there. A miss offers database
 * search and manual entry with the barcode carried along so it is
 * recognised next time; failures are always recoverable.
 *
 * The legacy `(mealTypeName, onBack)` signature from `AppNavigation.kt` is
 * kept; [onNavigate] powers the miss / error links.
 */
@Composable
fun BarcodeScannerScreen(
    mealTypeName: String,
    onBack: () -> Unit,
    date: String? = null,
    mode: String = "log",
    onNavigate: (String) -> Unit = {}
) {
    val mealType = parseMealTypeArg(mealTypeName)
    val dateArg = date ?: FoodLoggingRoutes.today()
    val logDate = parseLogDate(dateArg)
    val flow = FoodLogFlow.parse(mode)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<ScanPhase>(ScanPhase.Scanning) }
    var cameraAuthorized by remember { mutableStateOf<Boolean?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraAuthorized = granted
        if (!granted) phase = ScanPhase.Error(BarcodeScanError.PermissionDenied, null)
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraAuthorized = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun handleBarcode(barcode: String) {
        phase = ScanPhase.Loading(barcode)
        scope.launch {
            try {
                phase = when (val destination = BarcodeLookupCascade.destination(BarcodeLookupService.lookup(barcode), mealType)) {
                    is BarcodeScanDestination.CatalogDetail -> {
                        FoodDetailHandoff.putFood(destination.food)
                        ScanPhase.CatalogHit(destination.food)
                    }
                    is BarcodeScanDestination.TemplateDetail -> ScanPhase.ProductHit(destination.entry)
                }
            } catch (_: BarcodeScanError.ProductNotFound) {
                phase = ScanPhase.Miss(barcode)
            } catch (e: BarcodeScanError) {
                phase = ScanPhase.Error(e, barcode)
            } catch (e: Exception) {
                phase = ScanPhase.Error(BarcodeScanError.NetworkError(e.message ?: "Lookup failed"), barcode)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        when (val current = phase) {
            is ScanPhase.Scanning -> when (cameraAuthorized) {
                true -> CameraScannerView(
                    onBarcodeScanned = ::handleBarcode,
                    onCameraUnavailable = { phase = ScanPhase.Error(BarcodeScanError.CameraUnavailable, null) }
                )
                false -> ManualEntryFallback(onBarcodeScanned = ::handleBarcode)
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FLPrimary) }
            }
            is ScanPhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = FLPrimary)
                    Spacer(Modifier.height(16.dp))
                    Text("Looking up ${current.barcode}…", fontSize = 14.sp, color = TextSecondaryColor)
                }
            }
            // Same screen a search selection lands on; its Save pops this container.
            is ScanPhase.CatalogHit -> FoodDetailContent(
                mode = FoodDetailMode.LogFood(current.food.id), flow = flow, mealType = mealType, logDate = logDate, onDone = onBack
            )
            is ScanPhase.ProductHit -> {
                val token = remember(current.entry.id) { FoodDetailHandoff.putEntry(current.entry) }
                FoodDetailContent(mode = FoodDetailMode.Handoff(token), flow = flow, mealType = mealType, logDate = logDate, onDone = onBack)
            }
            is ScanPhase.Miss -> MissView(
                barcode = current.barcode,
                onSearch = { onNavigate(FoodLoggingRoutes.foodSearch(mealType, dateArg, barcode = current.barcode, mode = flow.wire)) },
                onManual = { onNavigate(FoodLoggingRoutes.quickAdd(mealType, dateArg, barcode = current.barcode, mode = flow.wire)) },
                onScanAgain = { phase = ScanPhase.Scanning }
            )
            is ScanPhase.Error -> ErrorView(
                error = current.error,
                barcode = current.barcode,
                onRetry = { current.barcode?.let { handleBarcode(it) } },
                onScanAgain = { phase = ScanPhase.Scanning },
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                },
                onManual = { onNavigate(FoodLoggingRoutes.quickAdd(mealType, dateArg, barcode = current.barcode, mode = flow.wire)) }
            )
        }
    }
}

// MARK: - Real Camera Scanner with ML Kit

@Composable
private fun CameraScannerView(onBarcodeScanned: (String) -> Unit, onCameraUnavailable: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasScanned by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var manualBarcode by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                val analysisExecutor = Executors.newSingleThreadExecutor()

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                        .build()
                    val scanner = BarcodeScanning.getClient(options)
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && !hasScanned) {
                                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            val barcode = barcodes.firstOrNull()?.rawValue
                                            if (barcode != null && !hasScanned) {
                                                hasScanned = true
                                                onBarcodeScanned(barcode)
                                            }
                                        }
                                        .addOnCompleteListener { imageProxy.close() }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }
                    try {
                        cameraProvider.unbindAll()
                        cameraControl = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    } catch (e: Exception) {
                        Log.e("BarcodeScanner", "Camera bind failed", e)
                        onCameraUnavailable()
                    }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(260.dp, 160.dp).border(2.dp, FLPrimary, RoundedCornerShape(12.dp)))
        }

        IconButton(
            onClick = { flashEnabled = !flashEnabled; cameraControl?.cameraControl?.enableTorch(flashEnabled) },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff, "Toggle flash", tint = Color.White, modifier = Modifier.size(28.dp))
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Or enter barcode manually", fontSize = 13.sp, color = Color.White)
            ManualBarcodeRow(value = manualBarcode, onValueChange = { manualBarcode = it }) {
                hasScanned = true
                onBarcodeScanned(manualBarcode)
            }
        }
    }
}

// MARK: - Manual Entry Fallback (no camera permission)

@Composable
private fun ManualEntryFallback(onBarcodeScanned: (String) -> Unit) {
    var manualBarcode by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Barcode Scanner", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        Text("Camera permission is required for scanning. You can enter a barcode manually below.", fontSize = 14.sp, color = TextSecondaryColor)
        ManualBarcodeRow(value = manualBarcode, onValueChange = { manualBarcode = it }) { onBarcodeScanned(manualBarcode) }
    }
}

@Composable
private fun ManualBarcodeRow(value: String, onValueChange: (String) -> Unit, onLookUp: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).background(White, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = TextPrimaryColor),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> if (value.isEmpty()) Text("Enter barcode", fontSize = 16.sp, color = MediumGray); inner() }
            )
        }
        Box(
            modifier = Modifier.background(if (value.isEmpty()) DisabledButton else FLPrimary, RoundedCornerShape(8.dp))
                .then(if (value.isNotEmpty()) Modifier.clickable(onClick = onLookUp) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text("Look Up", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White)
        }
    }
}

// MARK: - Miss (no match anywhere)

@Composable
private fun MissView(barcode: String, onSearch: () -> Unit, onManual: () -> Unit, onScanAgain: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.QrCodeScanner, null, tint = MediumGray, modifier = Modifier.size(48.dp))
            Text("No Match Found", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Text(
                "Barcode $barcode isn't in the food database yet. Search for the food by name, or add it manually — the barcode stays attached so it's recognized next time.",
                fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                FoodLogPrimaryButton(text = "Search the Database", onClick = onSearch)
                FoodLogSecondaryButton(text = "Add Food Manually", onClick = onManual)
                FoodLogSecondaryButton(text = "Scan Again", onClick = onScanAgain)
            }
        }
    }
}

// MARK: - Errors (always recoverable)

@Composable
private fun ErrorView(
    error: BarcodeScanError, barcode: String?,
    onRetry: () -> Unit, onScanAgain: () -> Unit, onOpenSettings: () -> Unit, onManual: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(if (error == BarcodeScanError.PermissionDenied) Icons.Default.PhotoCamera else Icons.Default.Warning, null, tint = MediumGray, modifier = Modifier.size(48.dp))
            Text(error.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Text(error.userMessage, fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (error == BarcodeScanError.PermissionDenied) {
                    FoodLogPrimaryButton(text = "Open Settings", onClick = onOpenSettings)
                } else {
                    // Retry the lookup without making the user re-aim the camera (typical offline → online case).
                    if (barcode != null) FoodLogPrimaryButton(text = "Try Again", onClick = onRetry)
                    if (error != BarcodeScanError.CameraUnavailable) FoodLogSecondaryButton(text = "Scan Again", onClick = onScanAgain)
                }
                // Manual entry is reachable from every failure state — no dead ends.
                FoodLogSecondaryButton(text = "Enter Manually", onClick = onManual)
            }
        }
    }
}
