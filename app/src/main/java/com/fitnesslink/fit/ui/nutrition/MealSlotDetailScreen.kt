package com.fitnesslink.fit.ui.nutrition

import androidx.compose.runtime.Composable
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.nutrition.MealBuilderStore

/**
 * Legacy entry point kept for the inline `mealSlotDetail/{day}/{mealType}`
 * route in `AppNavigation`. Slots are date-anchored now, so [dayName] is a
 * `yyyy-MM-dd` (a pre-Wave-3 weekday name maps onto the current week) and
 * the screen is the meal builder. The slot to edit comes from [slotId] or,
 * when the legacy route can't carry one, from `MealBuilderStore.activeSlotId`
 * which the planner sets before navigating.
 */
@Composable
fun MealSlotDetailScreen(
    dayName: String,
    mealTypeName: String,
    onBack: () -> Unit,
    slotId: String? = null,
    onNavigate: ((String) -> Unit)? = null
) {
    val date = MealPlanDay.normalize(dayName)
    val mealType = MealType.fromWire(mealTypeName) ?: MealType.BREAKFAST
    MealBuilderScreen(
        date = date,
        mealType = mealType,
        slotId = slotId ?: MealBuilderStore.activeSlotId,
        onBack = onBack,
        onNavigate = onNavigate ?: {}
    )
}
