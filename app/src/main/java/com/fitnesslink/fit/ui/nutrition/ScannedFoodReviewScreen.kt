package com.fitnesslink.fit.ui.nutrition

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.BarcodeProduct
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

@Composable
fun ScannedFoodReviewScreen(
    product: BarcodeProduct,
    initialMealType: MealType,
    onSave: (MealType, Double) -> Unit,
    onBack: () -> Unit
) {
    var servingMultiplier by remember { mutableDoubleStateOf(1.0) }
    var selectedMeal by remember { mutableStateOf(initialMealType) }

    val factor = (product.servingSizeGrams * servingMultiplier) / 100.0
    val adjustedCalories = (product.caloriesPer100g * factor).toInt()
    val adjustedProtein = (product.proteinPer100g * factor).toInt()
    val adjustedFat = (product.fatPer100g * factor).toInt()
    val adjustedCarbs = (product.carbsPer100g * factor).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp)
        ) {
            // Product header
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            // Nutrition card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ScannedNutritionRow("Calories", "$adjustedCalories cal")
                SeparatorView()
                ScannedNutritionRow("Protein", "${adjustedProtein}g")
                SeparatorView()
                ScannedNutritionRow("Fat", "${adjustedFat}g")
                SeparatorView()
                ScannedNutritionRow("Carbs", "${adjustedCarbs}g")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Serving adjuster
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Serving",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryColor
                )
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
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease",
                            tint = FLPrimary,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    if (servingMultiplier > 0.5) servingMultiplier -= 0.5
                                }
                        )
                        Text(
                            text = "${String.format("%.1f", servingMultiplier)}x",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryColor,
                            modifier = Modifier.width(40.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase",
                            tint = FLPrimary,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    servingMultiplier += 0.5
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Meal type selector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Meal",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimaryColor
                )
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

            Spacer(modifier = Modifier.height(16.dp))

            // Save button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(FLPrimary, RoundedCornerShape(12.dp))
                    .clickable { onSave(selectedMeal, servingMultiplier) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save Entry",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = White
                )
            }
        }
    }
}

@Composable
private fun ScannedNutritionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = TextSecondaryColor
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryColor
        )
    }
}
