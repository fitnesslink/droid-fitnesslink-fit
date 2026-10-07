package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Shared chrome for the food-logging screens (iOS card / button / chip idioms).

/** How a picker screen hands its result on (iOS `mealBuilderCapture` environment, extended). */
enum class FoodLogFlow(val wire: String) {
    /** Log a diary entry (default). */
    LOG("log"),
    /** Stage into agent C's `MealBuilderStore` (planner meal builder). */
    ADD_TO_MEAL("addToMeal"),
    /** Push onto `FoodCaptureBus` for a locally-assembled meal (recent-meal re-log editor). */
    CAPTURE("capture");

    val stagesIntoMeal: Boolean get() = this != LOG

    companion object {
        fun parse(raw: String?): FoodLogFlow = entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: LOG
    }
}

/** `yyyy-MM-dd` → the log day, or null when it is today / blank / unparseable (today keeps the real clock time). */
fun parseLogDate(dateArg: String?): Date? {
    if (dateArg.isNullOrBlank()) return null
    val date = MealPlanDay.date(dateArg) ?: return null
    return if (MealPlanDay.string(date) == MealPlanDay.string(Date())) null else date
}

fun parseMealTypeArg(raw: String?): MealType = MealType.fromWire(raw) ?: MealType.BREAKFAST

/** " · Jul 2" style marker so it's visible the log is going to a past day. Empty when logging normally. */
fun backfillSuffix(logDate: Date?, prefix: String = " · "): String {
    if (logDate == null) return ""
    return prefix + SimpleDateFormat("MMM d", Locale.US).format(logDate)
}

fun formatMediumDay(date: Date): String = SimpleDateFormat("EEEE, MMM d", Locale.US).format(date)

fun isToday(date: Date): Boolean {
    val a = Calendar.getInstance().apply { time = date }
    val b = Calendar.getInstance()
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun FoodLogCard(
    modifier: Modifier = Modifier,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(White, RoundedCornerShape(12.dp))
            .padding(padding.dp),
        content = content
    )
}

@Composable
fun FoodLogSectionTitle(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
fun FoodLogPrimaryButton(text: String, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(if (enabled && !loading) FLPrimary else DisabledButton, RoundedCornerShape(12.dp))
            .then(if (enabled && !loading) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
        }
    }
}

@Composable
fun FoodLogSecondaryButton(text: String, color: Color = FLPrimary, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(White, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** A tappable row card with a leading icon, title, optional subtitle and the right arrow (iOS link rows). */
@Composable
fun FoodLogLinkRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = if (enabled) FLPrimary else TextSecondaryColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(White, RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = tint)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = TextSecondaryColor)
        }
        Image(painter = painterResource(R.drawable.rightarrow), contentDescription = null, modifier = Modifier.size(8.dp, 14.dp))
    }
}

/** Breakfast / Lunch / Dinner / Snack selector chips. */
@Composable
fun MealTypeChips(selected: MealType, onSelect: (MealType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealType.entries.forEach { meal ->
            Box(
                modifier = Modifier
                    .background(if (selected == meal) FLPrimary else BackgroundColor, RoundedCornerShape(8.dp))
                    .clickable { onSelect(meal) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(meal.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected == meal) White else TextPrimaryColor)
            }
        }
    }
}

/** "P 32g · C 40g · F 12g" line (iOS `MealMacroSummary`). */
@Composable
fun FoodLogMacroSummary(protein: Double, carbs: Double, fat: Double) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MacroPill("P", protein)
        MacroPill("C", carbs)
        MacroPill("F", fat)
    }
}

@Composable
private fun MacroPill(label: String, grams: Double) {
    Text("$label ${grams.toInt()}g", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
}

@Composable
fun FoodLogErrorText(message: String, modifier: Modifier = Modifier, centered: Boolean = false) {
    Text(
        message, fontSize = 12.sp, color = OrangeTheme,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = modifier
    )
}

@Composable
fun FoodLogEmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 60.dp, start = 40.dp, end = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MediumGray, modifier = Modifier.size(34.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        Text(message, fontSize = 13.sp, color = TextSecondaryColor, textAlign = TextAlign.Center)
    }
}

/** Labelled form field with inline validation copy (iOS QuickAdd `formField`). */
@Composable
fun ValidatedFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 12.sp, color = if (error == null) TextSecondaryColor else OrangeTheme)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = TextPrimaryColor),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(label, fontSize = 16.sp, color = MediumGray)
                inner()
            }
        )
        if (error != null) Text(error, fontSize = 11.sp, color = OrangeTheme)
    }
}

/** Small boxed numeric input used by the AI review rows and the detail quantity field. */
@Composable
fun BoxedNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "0",
    keyboardType: KeyboardType = KeyboardType.Decimal,
    fontSize: Int = 14,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Box(
        modifier = modifier
            .background(BackgroundColor, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = fontSize.sp, color = TextPrimaryColor, textAlign = TextAlign.Center, fontWeight = fontWeight),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) Text(placeholder, fontSize = fontSize.sp, color = MediumGray)
                    inner()
                }
            }
        )
    }
}

/** Small capsule badge ("My Food", "System", meal type). */
@Composable
fun FoodLogBadge(text: String, color: Color = FLPrimary) {
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
