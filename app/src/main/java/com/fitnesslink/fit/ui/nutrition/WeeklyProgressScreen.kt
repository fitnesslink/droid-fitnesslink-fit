package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.DailyCalorieSummary
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

@Composable
fun WeeklyProgressScreen(onBack: () -> Unit) {
    var weeklyData by remember { mutableStateOf<List<DailyCalorieSummary>>(emptyList()) }

    LaunchedEffect(Unit) {
        weeklyData = MockDataProvider.weeklyCalories
    }

    val weeklyAvgCalories = if (weeklyData.isNotEmpty()) weeklyData.sumOf { it.calories } / weeklyData.size else 0
    val weeklyAvgProtein = if (weeklyData.isNotEmpty()) (weeklyData.sumOf { it.protein } / weeklyData.size).toInt() else 0
    val weeklyAvgFat = if (weeklyData.isNotEmpty()) (weeklyData.sumOf { it.fat } / weeklyData.size).toInt() else 0
    val weeklyAvgCarbs = if (weeklyData.isNotEmpty()) (weeklyData.sumOf { it.carbs } / weeklyData.size).toInt() else 0
    val maxCalories = weeklyData.maxOfOrNull { it.calories } ?: 1

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
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Weekly Progress",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
            )

            // Bar chart card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Daily Calories",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryColor
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    weeklyData.forEach { day ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = "${day.calories}",
                                fontSize = 10.sp,
                                color = TextSecondaryColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(barHeight(day.calories, maxCalories))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (day.calories >= day.goal) FLPrimary
                                        else FLPrimary.copy(alpha = 0.5f)
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = day.dayLabel,
                                fontSize = 11.sp,
                                color = TextSecondaryColor
                            )
                        }
                    }
                }

                // Legend
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp, 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(FLPrimary)
                    )
                    Text("At/above goal", fontSize = 11.sp, color = TextSecondaryColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(12.dp, 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(FLPrimary.copy(alpha = 0.5f))
                    )
                    Text("Below goal", fontSize = 11.sp, color = TextSecondaryColor)
                }
            }

            // Weekly averages card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Weekly Averages",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryColor
                )
                AverageRow("Calories", "$weeklyAvgCalories cal", FLPrimary)
                SeparatorView()
                AverageRow("Protein", "${weeklyAvgProtein}g", BlueTheme)
                SeparatorView()
                AverageRow("Fat", "${weeklyAvgFat}g", OrangeTheme)
                SeparatorView()
                AverageRow("Carbs", "${weeklyAvgCarbs}g", PurpleTheme)
            }
        }
    }
}

@Composable
private fun AverageRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        }
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
    }
}

private fun barHeight(calories: Int, maxCalories: Int): Dp {
    val maxH = 120f
    return if (maxCalories > 0) (maxH * calories / maxCalories).dp else 0.dp
}
