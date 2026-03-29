package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.ui.components.HeaderView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.MealPlanViewModel

@Composable
fun NutritionHomeScreen(
    onNavigateToCalorieTracking: () -> Unit = {},
    onNavigateToMealPlan: () -> Unit = {},
    onNavigateToNutritionSummary: () -> Unit = {},
    onNavigateToGroceryList: () -> Unit = {},
    onNavigateToGoalSettings: () -> Unit = {}
) {
    val viewModel: MealPlanViewModel = viewModel()

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderView()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title
            Column(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Nutrition",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )
                Text(
                    text = "Track calories and plan your meals",
                    fontSize = 14.sp,
                    color = TextSecondaryColor
                )
            }

            // Calorie Tracking Card
            FeatureCard(
                iconRes = R.drawable.exercises,
                iconTint = OrangeTheme,
                title = "Calorie Tracking",
                subtitle = "Log meals, scan barcodes, track daily macros",
                onClick = onNavigateToCalorieTracking,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // Meal Plans Card
            FeatureCard(
                iconRes = R.drawable.calendaricon,
                iconTint = FLPrimary,
                title = "Meal Plans",
                subtitle = "Weekly meal planning with recipes & grocery lists",
                onClick = onNavigateToMealPlan,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // Quick Stats
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "This Week",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryColor
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        value = "${viewModel.weeklyAverageCalories}",
                        label = "Avg Calories",
                        color = FLPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = "${viewModel.weekSlots.size}",
                        label = "Meals Planned",
                        color = BlueTheme,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = "${viewModel.uncheckedGroceryCount}",
                        label = "Grocery Items",
                        color = PurpleTheme,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Actions
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Quick Actions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryColor,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = "Summary",
                        color = BlueTheme,
                        onClick = onNavigateToNutritionSummary,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        icon = Icons.Default.ShoppingCart,
                        title = "Groceries",
                        color = FLPrimary,
                        onClick = onNavigateToGroceryList,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        icon = Icons.Default.DateRange,
                        title = "Goals",
                        color = OrangeTheme,
                        onClick = onNavigateToGoalSettings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    iconRes: Int,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier
                .size(48.dp)
                .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondaryColor,
                maxLines = 2
            )
        }
        Icon(
            painter = painterResource(R.drawable.back),
            contentDescription = "Go",
            tint = MediumGray,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(White, RoundedCornerShape(12.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondaryColor
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(White, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimaryColor
        )
    }
}
