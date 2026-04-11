package com.fitnesslink.fit.ui.nutrition

import android.Manifest
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.fitnesslink.fit.model.BarcodeProduct
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private sealed class ScanPhase {
    data object Scanning : ScanPhase()
    data class Loading(val barcode: String) : ScanPhase()
    data class Result(val product: BarcodeProduct) : ScanPhase()
    data class Error(val title: String, val message: String) : ScanPhase()
}

@Composable
fun BarcodeScannerScreen(
    mealTypeName: String,
    onBack: () -> Unit
) {
    val mealType = MealType.entries.firstOrNull { it.name == mealTypeName } ?: MealType.BREAKFAST
    var phase by remember { mutableStateOf<ScanPhase>(ScanPhase.Scanning) }
    val scope = rememberCoroutineScope()
    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun handleBarcode(barcode: String) {
        phase = ScanPhase.Loading(barcode)
        scope.launch {
            try {
                // Try server API first
                val product = if (NetworkMonitor.isConnected.value) {
                    try {
                        ApiClient.nutritionApi.lookupBarcode(barcode)
                    } catch (_: Exception) {
                        null
                    }
                } else null

                // Check local cache
                val result = product
                    ?: DatabaseManager.barcodeProduct(barcode)
                    ?: com.fitnesslink.fit.data.MockDataProvider.barcodeProduct(barcode)

                if (result != null) {
                    // Cache locally
                    DatabaseManager.insertBarcodeProduct(result)
                    phase = ScanPhase.Result(result)
                } else {
                    phase = ScanPhase.Error(
                        "Product Not Found",
                        "No product found for barcode $barcode. Try scanning again or enter the food manually."
                    )
                }
            } catch (_: Exception) {
                phase = ScanPhase.Error(
                    "Lookup Failed",
                    "Could not look up barcode $barcode. Check your connection and try again."
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        when (val currentPhase = phase) {
            is ScanPhase.Scanning -> {
                if (hasCameraPermission) {
                    CameraScannerView(
                        onBarcodeScanned = { handleBarcode(it) }
                    )
                } else {
                    ManualEntryFallback(
                        onBarcodeScanned = { handleBarcode(it) }
                    )
                }
            }
            is ScanPhase.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = FLPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Looking up ${currentPhase.barcode}...", fontSize = 14.sp, color = TextSecondaryColor)
                    }
                }
            }
            is ScanPhase.Result -> {
                ScannedFoodReviewContent(
                    product = currentPhase.product,
                    mealType = mealType,
                    onSave = { entry ->
                        scope.launch {
                            DatabaseManager.saveFoodEntry(entry)
                            try { ApiClient.nutritionApi.addFoodEntry(entry) } catch (_: Exception) {}
                        }
                        onBack()
                    }
                )
            }
            is ScanPhase.Error -> {
                ErrorView(
                    title = currentPhase.title,
                    message = currentPhase.message,
                    onScanAgain = { phase = ScanPhase.Scanning },
                    onBack = onBack
                )
            }
        }
    }
}

// MARK: - Real Camera Scanner with ML Kit

@Composable
private fun CameraScannerView(onBarcodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasScanned by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var manualBarcode by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                val analysisExecutor = Executors.newSingleThreadExecutor()

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                            Barcode.FORMAT_EAN_13,
                            Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A,
                            Barcode.FORMAT_UPC_E
                        )
                        .build()
                    val scanner = BarcodeScanning.getClient(options)

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && !hasScanned) {
                                    val inputImage = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    scanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            val barcode = barcodes.firstOrNull()?.rawValue
                                            if (barcode != null && !hasScanned) {
                                                hasScanned = true
                                                onBarcodeScanned(barcode)
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                        cameraControl = camera
                    } catch (e: Exception) {
                        Log.e("BarcodeScanner", "Camera bind failed", e)
                    }
                }, executor)

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Scan overlay
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(260.dp, 160.dp)
                    .border(2.dp, FLPrimary, RoundedCornerShape(12.dp))
            )
        }

        // Flash toggle
        IconButton(
            onClick = {
                flashEnabled = !flashEnabled
                cameraControl?.cameraControl?.enableTorch(flashEnabled)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Toggle flash",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Manual entry at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Or enter barcode manually", fontSize = 13.sp, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = manualBarcode,
                        onValueChange = { manualBarcode = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = TextPrimaryColor),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            if (manualBarcode.isEmpty()) Text("Enter barcode", fontSize = 16.sp, color = MediumGray)
                            innerTextField()
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .background(
                            if (manualBarcode.isEmpty()) DisabledButton else FLPrimary,
                            RoundedCornerShape(8.dp)
                        )
                        .then(
                            if (manualBarcode.isNotEmpty()) Modifier.clickable {
                                hasScanned = true
                                onBarcodeScanned(manualBarcode)
                            } else Modifier
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("Look Up", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White)
                }
            }
        }
    }
}

// MARK: - Manual Entry Fallback (no camera permission)

@Composable
private fun ManualEntryFallback(onBarcodeScanned: (String) -> Unit) {
    var manualBarcode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Barcode Scanner", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        Text("Camera permission is required for scanning. You can enter a barcode manually below.", fontSize = 14.sp, color = TextSecondaryColor)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.weight(1f).background(White, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = manualBarcode,
                    onValueChange = { manualBarcode = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = TextPrimaryColor),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (manualBarcode.isEmpty()) Text("Enter barcode", fontSize = 16.sp, color = MediumGray)
                        innerTextField()
                    }
                )
            }
            Box(
                modifier = Modifier
                    .background(if (manualBarcode.isEmpty()) DisabledButton else FLPrimary, RoundedCornerShape(8.dp))
                    .then(if (manualBarcode.isNotEmpty()) Modifier.clickable { onBarcodeScanned(manualBarcode) } else Modifier)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Look Up", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White)
            }
        }
    }
}

// MARK: - Review Content

@Composable
private fun ScannedFoodReviewContent(
    product: BarcodeProduct,
    mealType: MealType,
    onSave: (FoodEntry) -> Unit
) {
    var servingMultiplier by remember { mutableDoubleStateOf(1.0) }
    var selectedMeal by remember { mutableStateOf(mealType) }

    val factor = (product.servingSizeGrams * servingMultiplier) / 100.0
    val adjustedCalories = (product.caloriesPer100g * factor).toInt()
    val adjustedProtein = (product.proteinPer100g * factor).toInt()
    val adjustedFat = (product.fatPer100g * factor).toInt()
    val adjustedCarbs = (product.carbsPer100g * factor).toInt()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Product header
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp)) {
            Text(product.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            if (product.brand.isNotEmpty()) {
                Text(product.brand, fontSize = 14.sp, color = TextSecondaryColor)
            }
            Text("Barcode: ${product.barcode}", fontSize = 12.sp, color = MediumGray)
        }

        // Nutrition card
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(White, RoundedCornerShape(12.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReviewNutritionRow("Calories", "$adjustedCalories cal")
            SeparatorView()
            ReviewNutritionRow("Protein", "${adjustedProtein}g")
            SeparatorView()
            ReviewNutritionRow("Fat", "${adjustedFat}g")
            SeparatorView()
            ReviewNutritionRow("Carbs", "${adjustedCarbs}g")
        }

        // Serving adjuster
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(White, RoundedCornerShape(12.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Serving", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${(product.servingSizeGrams * servingMultiplier).toInt()} ${product.servingUnit}", fontSize = 16.sp, color = TextPrimaryColor)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.RemoveCircle, "Decrease", tint = FLPrimary, modifier = Modifier.size(24.dp).clickable { if (servingMultiplier > 0.5) servingMultiplier -= 0.5 })
                    Text("${String.format("%.1f", servingMultiplier)}x", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.width(40.dp))
                    Icon(Icons.Default.AddCircle, "Increase", tint = FLPrimary, modifier = Modifier.size(24.dp).clickable { servingMultiplier += 0.5 })
                }
            }
        }

        // Meal type selector
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(White, RoundedCornerShape(12.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Meal", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.entries.forEach { meal ->
                    Box(
                        modifier = Modifier
                            .background(if (selectedMeal == meal) FLPrimary else BackgroundColor, RoundedCornerShape(8.dp))
                            .clickable { selectedMeal = meal }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(meal.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selectedMeal == meal) White else TextPrimaryColor)
                    }
                }
            }
        }

        // Save button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(FLPrimary, RoundedCornerShape(12.dp))
                .clickable {
                    onSave(product.toFoodEntry(selectedMeal, servingMultiplier))
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Save Entry", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
        }
    }
}

// MARK: - Error View

@Composable
private fun ErrorView(title: String, message: String, onScanAgain: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
            Icon(Icons.Default.Warning, "Error", tint = MediumGray, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier.fillMaxWidth().background(FLPrimary, RoundedCornerShape(12.dp)).clickable(onClick = onScanAgain).padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Scan Again", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).clickable(onClick = onBack).padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Enter Manually", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
            }
        }
    }
}

@Composable
private fun ReviewNutritionRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
    }
}
