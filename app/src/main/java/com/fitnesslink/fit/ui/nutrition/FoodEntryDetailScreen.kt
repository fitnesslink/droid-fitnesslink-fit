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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

@Composable
fun FoodEntryDetailScreen(
    entryId: String,
    onBack: () -> Unit
) {
    var entry by remember { mutableStateOf<FoodEntry?>(null) }
    var servingMultiplier by remember { mutableDoubleStateOf(1.0) }

    LaunchedEffect(entryId) {
        entry = MockDataProvider.foodEntry(entryId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        val currentEntry = entry
        if (currentEntry != null) {
            val adjustedCalories = (currentEntry.calories * servingMultiplier).toInt()
            val adjustedProtein = (currentEntry.protein * servingMultiplier).toInt()
            val adjustedFat = (currentEntry.fat * servingMultiplier).toInt()
            val adjustedCarbs = (currentEntry.carbs * servingMultiplier).toInt()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = currentEntry.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryColor
                    )
                    Text(
                        text = currentEntry.mealType.displayName,
                        fontSize = 14.sp,
                        color = TextSecondaryColor
                    )
                }

                // Nutrition info card
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
                            text = "${(currentEntry.servingSize * servingMultiplier).let { String.format("%.1f", it) }} ${currentEntry.servingUnit}",
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
                                    .height(24.dp)
                                    .width(24.dp)
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
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Increase",
                                tint = FLPrimary,
                                modifier = Modifier
                                    .height(24.dp)
                                    .width(24.dp)
                                    .clickable { servingMultiplier += 0.5 }
                            )
                        }
                    }
                }

                // Save button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(FLPrimary, RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save Changes",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = White
                    )
                }

                // Delete button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(White, RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Delete Entry",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Red
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Entry not found",
                    fontSize = 16.sp,
                    color = TextSecondaryColor
                )
            }
        }
    }
}

@Composable
fun NutritionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
    }
}
