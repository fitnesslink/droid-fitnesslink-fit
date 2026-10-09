package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.dto.QuickLogMealSource
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.nutrition.NutritionFeatureFlags
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.util.Locale

/**
 * One logged-meal section on the dashboard (iOS `MealSectionView`): header
 * with calories + scan/add affordances (hidden on future days), a per-slot
 * macro roll-up, the entry rows, and the Quick Log "Same as yesterday" chip
 * for an EMPTY meal that has a copy source (FTNSSLNK-213).
 */
@Composable
fun MealSectionView(
    mealType: MealType,
    entries: List<FoodEntry>,
    totalCalories: Int,
    allowsLogging: Boolean,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onEntryTap: (FoodEntry) -> Unit,
    modifier: Modifier = Modifier,
    /** Snap a Meal shortcut: photo → foods → macros for this meal. Null hides the icon. */
    onSnap: (() -> Unit)? = null,
    quickLogSource: QuickLogMealSource? = null,
    quickLogLabel: String = "",
    isQuickLogging: Boolean = false,
    onQuickLog: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(mealType.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Spacer(modifier = Modifier.weight(1f))
            Text("$totalCalories cal", fontSize = 14.sp, color = TextSecondaryColor)
            if (allowsLogging) {
                if (onSnap != null && NutritionFeatureFlags.snapMealEnabled) {
                    Icon(
                        Icons.Default.PhotoCamera, contentDescription = "Snap a photo of your ${mealType.displayName.lowercase(Locale.US)}", tint = FLPrimary,
                        modifier = Modifier.size(20.dp).clickable(onClick = onSnap)
                    )
                }
                Icon(
                    Icons.Default.QrCodeScanner, contentDescription = "Scan barcode", tint = FLPrimary,
                    modifier = Modifier.size(20.dp).clickable(onClick = onScan)
                )
                Image(
                    painter = painterResource(R.drawable.greenplus), contentDescription = "Add food",
                    modifier = Modifier.size(20.dp).clickable(onClick = onAdd)
                )
            }
        }

        if (entries.isNotEmpty()) {
            MealMacroSummary(
                protein = entries.sumOf { it.protein },
                carbs = entries.sumOf { it.carbs },
                fat = entries.sumOf { it.fat }
            )
        }

        if (entries.isEmpty()) {
            Text("No entries yet", fontSize = 14.sp, color = MediumGray, modifier = Modifier.padding(vertical = 4.dp))
            if (allowsLogging && quickLogSource != null && onQuickLog != null) {
                QuickLogChip(source = quickLogSource, label = quickLogLabel, isLogging = isQuickLogging, onTap = onQuickLog)
            }
        } else {
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEntryTap(entry) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.name, fontSize = 14.sp, color = TextPrimaryColor)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(portionLabel(entry), fontSize = 12.sp, color = TextSecondaryColor)
                            entry.aiBadgeText?.let { badge ->
                                Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = PurpleTheme)
                            }
                        }
                    }
                    Text("${entry.calories} cal", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
                }
                if (index < entries.size - 1) SeparatorView()
            }
        }
    }
}

private fun portionLabel(entry: FoodEntry): String {
    val qty = entry.quantity
    val unit = entry.portionUnit
    if (qty != null && !unit.isNullOrEmpty()) return "${trim(qty)} $unit"
    return "${trim(entry.servingSize)} ${entry.servingUnit}"
}

private fun trim(value: Double): String =
    if (value == Math.rint(value)) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

/** "Same as yesterday · 3 items · 520 cal" one-tap copy affordance. */
@Composable
private fun QuickLogChip(source: QuickLogMealSource, label: String, isLogging: Boolean, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FLPrimary.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .clickable(enabled = !isLogging, onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isLogging) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = FLPrimary)
        } else {
            Icon(Icons.Default.Replay, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
            val items = if (source.entryCount == 1) "1 item" else "${source.entryCount} items"
            Text("$items · ${Math.round(source.calories)} cal", fontSize = 11.sp, color = TextSecondaryColor)
        }
        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
    }
}

/**
 * Compact protein / carbs / fat roll-up for a meal slot (iOS
 * `MealMacroSummary`): protein = blue, carbs = purple, fat = orange.
 */
@Composable
fun MealMacroSummary(protein: Double, carbs: Double, fat: Double, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        MacroDot("P", protein, BlueTheme)
        MacroDot("C", carbs, PurpleTheme)
        MacroDot("F", fat, OrangeTheme)
    }
}

@Composable
private fun MacroDot(label: String, grams: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Text("$label ${Math.round(grams)}g", fontSize = 13.sp, color = TextSecondaryColor)
    }
}
