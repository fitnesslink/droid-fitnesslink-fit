package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealSlot
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.nutrition.MealBuilderItem
import com.fitnesslink.fit.nutrition.MealPlanStore
import com.fitnesslink.fit.nutrition.PreviousMeal
import com.fitnesslink.fit.nutrition.PreviousMealSource
import com.fitnesslink.fit.nutrition.PreviousMealsAssembler
import com.fitnesslink.fit.nutrition.deepCopied
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * FTNSSLNK-206 — pick a previously logged or planned meal to quick-add into
 * the meal being built (agent C's builder). Selecting one hands DEEP COPIES
 * of its foods to [onPick] as `MealBuilderItem`s, so editing the quick-added
 * meal never touches the source. Logged meals come from the local diary;
 * planned meals from the itemized slots of the current planner week
 * (`MealPlanStore.week`), when available.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviousMealsPickerSheet(
    onPick: (List<MealBuilderItem>) -> Unit,
    onDismiss: () -> Unit,
    mealType: MealType? = null
) {
    var meals by remember { mutableStateOf<List<PreviousMeal>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val entries = withContext(Dispatchers.IO) { DatabaseManager.allFoodEntries() }
        val slots: List<MealSlot> = try {
            MealPlanStore.week(MealPlanDay.string(MealPlanDay.weekStart(Date())))
        } catch (_: Throwable) {
            emptyList()
        }
        meals = PreviousMealsAssembler.combined(entries = entries, slots = slots, preferredMealType = mealType)
        loaded = true
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BackgroundColor) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Text("Previous Meals", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.padding(horizontal = 20.dp))
            if (loaded && meals.isEmpty()) {
                FoodLogEmptyState(
                    icon = Icons.Default.History, title = "No previous meals yet",
                    message = "Meals you log or plan will show up here so you can reuse them in one tap."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(meals, key = { it.id }) { meal ->
                        PreviousMealCard(meal) {
                            onPick(meal.foods.deepCopied().map {
                                MealBuilderItem(
                                    foodId = it.foodId, name = it.name, servings = it.servings,
                                    servingSize = it.servingSize, servingUnit = it.servingUnit,
                                    calories = it.calories, protein = it.protein, fat = it.fat, carbs = it.carbs
                                )
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviousMealCard(meal: PreviousMeal, onAdd: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
        Row(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(meal.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodLogBadge(meal.source.label, color = if (meal.source == PreviousMealSource.PLANNED) PurpleTheme else BlueTheme)
                    Text(SimpleDateFormat("MMM d", Locale.US).format(meal.date), fontSize = 12.sp, color = TextSecondaryColor)
                    Text("·", color = TextSecondaryColor)
                    Text("${meal.itemCount} item${if (meal.itemCount == 1) "" else "s"}", fontSize = 12.sp, color = TextSecondaryColor)
                }
            }
            Text("${meal.totalCalories} cal", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            meal.foods.forEach { food ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(food.name, fontSize = 13.sp, color = TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(food.portionLabel, fontSize = 11.sp, color = TextSecondaryColor)
                    Spacer(Modifier.weight(1f))
                    Text("${food.totalCalories.roundToInt()} cal", fontSize = 12.sp, color = TextSecondaryColor)
                }
            }
        }
        Box(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 10.dp)) {
            FoodLogMacroSummary(protein = meal.totalProtein, carbs = meal.totalCarbs, fat = meal.totalFat)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).background(FLPrimary, RoundedCornerShape(10.dp)).clickable(onClick = onAdd).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AddCircle, null, tint = White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text("Add to Meal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White)
        }
    }
}
