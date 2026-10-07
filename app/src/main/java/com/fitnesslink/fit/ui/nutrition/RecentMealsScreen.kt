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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.RecentMeal
import com.fitnesslink.fit.network.dto.RecentMealItem
import com.fitnesslink.fit.network.dto.RecentMealTypeGroup
import com.fitnesslink.fit.nutrition.NavPayloadHandoff
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.navigation.FoodLoggingRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * FTNSSLNK-214 — browse the server's meal-grouped diary history
 * (`GET /meals/recent`) and pick a meal to re-log; selecting one opens the
 * edit-before-log screen (`RecentMealLogScreen`), which batch-logs the items.
 */
@Composable
fun RecentMealsScreen(
    mealType: MealType,
    date: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val logDate = parseLogDate(date)
    var groups by remember { mutableStateOf<List<RecentMealTypeGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        isLoading = true
        errorMessage = null
        try {
            groups = ApiClient.nutritionApi.getRecentMeals(tz = TimeZone.getDefault().id).mealTypes
        } catch (_: ApiError.Offline) {
            errorMessage = "You're offline — recent meals need a connection."
        } catch (_: Exception) {
            errorMessage = "Couldn't load recent meals. Try again."
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    // Groups with meals, the target meal type's group first, then the server's order.
    val ordered = groups.filter { it.meals.isNotEmpty() }
        .sortedByDescending { MealType.fromWire(it.mealType) == mealType }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recent Meals", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                Text("Adding to ${mealType.displayName}${backfillSuffix(logDate)}", fontSize = 12.sp, color = TextSecondaryColor)
            }

            when {
                isLoading && groups.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FLPrimary)
                }
                errorMessage != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 40.dp, end = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = OrangeTheme, modifier = Modifier.size(24.dp))
                    Text(errorMessage ?: "", fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center)
                    Text("Retry", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary, modifier = Modifier.clickable { scope.launch { load() } })
                }
                ordered.isEmpty() -> FoodLogEmptyState(
                    icon = Icons.Default.History, title = "No recent meals yet",
                    message = "Meals you log will show up here so you can re-log them in a couple of taps."
                )
                else -> ordered.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FoodLogSectionTitle(group.mealType)
                        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            group.meals.forEach { meal ->
                                RecentMealCard(meal) {
                                    val token = NavPayloadHandoff.put(meal)
                                    onNavigate(FoodLoggingRoutes.recentMealLog(token, mealType, date))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Foods, servings and totals at a glance — what the re-log will stage. */
@Composable
private fun RecentMealCard(meal: RecentMeal, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(MealPlanDay.date(meal.date)?.let { formatMediumDay(it) } ?: meal.date, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Text("${meal.entryCount} item${if (meal.entryCount == 1) "" else "s"}", fontSize = 12.sp, color = TextSecondaryColor)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${meal.calories.roundToInt()} cal", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
                Icon(Icons.Default.KeyboardArrowRight, null, tint = MediumGray, modifier = Modifier.size(16.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            meal.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.name, fontSize = 13.sp, color = TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    portionLabel(item)?.let { Text(it, fontSize = 11.sp, color = TextSecondaryColor) }
                    Spacer(Modifier.weight(1f))
                    Text("${item.calories.roundToInt()} cal", fontSize = 12.sp, color = TextSecondaryColor)
                }
            }
        }
        FoodLogMacroSummary(protein = meal.protein, carbs = meal.carbs, fat = meal.fat)
        Spacer(Modifier.height(0.dp))
    }
}

private fun portionLabel(item: RecentMealItem): String? {
    val qty = item.quantity ?: item.servingSize ?: return null
    val unit = item.portionUnit ?: item.servingUnit ?: "serving"
    val qtyText = if (qty % 1.0 == 0.0) qty.toInt().toString() else String.format(Locale.US, "%.2g", qty)
    return "$qtyText $unit"
}
