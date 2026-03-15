package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.BarcodeProduct
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    val mockBarcodes = listOf(
        "Coca-Cola Classic" to "049000006346",
        "Cheerios" to "016000275287",
        "Chobani Greek Yogurt" to "818290014306",
        "Skippy Peanut Butter" to "038000138416"
    )

    fun handleBarcode(barcode: String) {
        phase = ScanPhase.Loading(barcode)
        scope.launch {
            delay(500)
            val product = MockDataProvider.barcodeProduct(barcode)
            if (product != null) {
                phase = ScanPhase.Result(product)
            } else {
                phase = ScanPhase.Error(
                    "Product Not Found",
                    "No product found for barcode $barcode. You can try scanning again or enter the food manually."
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
                ScannerView(
                    mockBarcodes = mockBarcodes,
                    onBarcodeScanned = { handleBarcode(it) }
                )
            }
            is ScanPhase.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = FLPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Looking up ${currentPhase.barcode}...",
                            fontSize = 14.sp,
                            color = TextSecondaryColor
                        )
                    }
                }
            }
            is ScanPhase.Result -> {
                ScannedFoodReviewContent(
                    product = currentPhase.product,
                    mealType = mealType,
                    onSave = onBack
                )
            }
            is ScanPhase.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MediumGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = currentPhase.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentPhase.message,
                            fontSize = 14.sp,
                            color = TextSecondaryColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FLPrimary, RoundedCornerShape(12.dp))
                                .clickable { phase = ScanPhase.Scanning }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Scan Again", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(White, RoundedCornerShape(12.dp))
                                .clickable(onClick = onBack)
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Enter Manually", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerView(
    mockBarcodes: List<Pair<String, String>>,
    onBarcodeScanned: (String) -> Unit
) {
    var manualBarcode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Barcode Scanner",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryColor,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Text(
            text = "Tap a product or enter a barcode manually",
            fontSize = 14.sp,
            color = TextSecondaryColor,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        // Mock barcode list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(White, RoundedCornerShape(12.dp))
        ) {
            mockBarcodes.forEachIndexed { index, (label, barcode) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBarcodeScanned(barcode) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.search),
                        contentDescription = "Scan",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimaryColor
                        )
                        Text(
                            text = barcode,
                            fontSize = 12.sp,
                            color = TextSecondaryColor
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.rightarrow),
                        contentDescription = "Go",
                        modifier = Modifier.size(8.dp, 14.dp)
                    )
                }
                if (index < mockBarcodes.size - 1) {
                    SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        // Manual entry
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Manual Entry",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimaryColor
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(White, RoundedCornerShape(8.dp))
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
                            if (manualBarcode.isEmpty()) {
                                Text("Enter barcode", fontSize = 16.sp, color = MediumGray)
                            }
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
                            if (manualBarcode.isNotEmpty()) Modifier.clickable { onBarcodeScanned(manualBarcode) }
                            else Modifier
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Look Up",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = White
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannedFoodReviewContent(
    product: BarcodeProduct,
    mealType: MealType,
    onSave: () -> Unit
) {
    var servingMultiplier by remember { mutableDoubleStateOf(1.0) }
    var selectedMeal by remember { mutableStateOf(mealType) }

    val factor = (product.servingSizeGrams * servingMultiplier) / 100.0
    val adjustedCalories = (product.caloriesPer100g * factor).toInt()
    val adjustedProtein = (product.proteinPer100g * factor).toInt()
    val adjustedFat = (product.fatPer100g * factor).toInt()
    val adjustedCarbs = (product.carbsPer100g * factor).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Product header
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp)
        ) {
            Text(
                text = product.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor
            )
            if (product.brand.isNotEmpty()) {
                Text(
                    text = product.brand,
                    fontSize = 14.sp,
                    color = TextSecondaryColor
                )
            }
            Text(
                text = "Barcode: ${product.barcode}",
                fontSize = 12.sp,
                color = MediumGray
            )
        }

        // Nutrition card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(White, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NutritionRow("Calories", "$adjustedCalories cal")
            SeparatorView()
            NutritionRow("Protein", "${adjustedProtein}g")
            SeparatorView()
            NutritionRow("Fat", "${adjustedFat}g")
            SeparatorView()
            NutritionRow("Carbs", "${adjustedCarbs}g")
        }

        // Serving adjuster
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(White, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Serving", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(product.servingSizeGrams * servingMultiplier).toInt()} ${product.servingUnit}",
                    fontSize = 16.sp,
                    color = TextPrimaryColor
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveCircle,
                        contentDescription = "Decrease",
                        tint = FLPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { if (servingMultiplier > 0.5) servingMultiplier -= 0.5 }
                    )
                    Text(
                        text = "${String.format("%.1f", servingMultiplier)}x",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryColor,
                        modifier = Modifier.width(40.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Increase",
                        tint = FLPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { servingMultiplier += 0.5 }
                    )
                }
            }
        }

        // Meal type selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(White, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Meal", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.entries.forEach { meal ->
                    Box(
                        modifier = Modifier
                            .background(
                                if (selectedMeal == meal) FLPrimary else BackgroundColor,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedMeal = meal }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = meal.displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedMeal == meal) White else TextPrimaryColor
                        )
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
                .clickable(onClick = onSave)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Save Entry", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
        }
    }
}
