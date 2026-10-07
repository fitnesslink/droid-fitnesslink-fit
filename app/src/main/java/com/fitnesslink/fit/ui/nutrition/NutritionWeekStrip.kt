package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.NutritionDayDotState
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Seven-day strip (Sun…Sat, meal-plan week convention) for the dashboard
 * (iOS `NutritionWeekStripView`, FTNSSLNK-193/196). Each cell shows the
 * weekday, day number and a completeness dot from the RANGE summary:
 * filled = something logged, none = empty. Future days are muted but still
 * tappable (planning context).
 */
@Composable
fun NutritionWeekStrip(
    weekDates: List<Date>,
    selectedDate: Date,
    dotState: (Date) -> NutritionDayDotState,
    onSelect: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekdayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    val dayFormat = SimpleDateFormat("d", Locale.getDefault())
    val today = MealPlanDay.dayStart(Date())
    val selected = MealPlanDay.dayStart(selectedDate)

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weekDates.forEach { date ->
            val day = MealPlanDay.dayStart(date)
            val isSelected = day == selected
            val isToday = day == today
            val dot = dotState(date)
            val isFuture = dot == NutritionDayDotState.FUTURE
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    weekdayFormat.format(date).take(3),
                    fontSize = 11.sp,
                    color = if (isFuture) MediumGray else TextSecondaryColor
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (isSelected) FLPrimary else Color.Transparent, CircleShape)
                        .then(if (isToday && !isSelected) Modifier.border(1.5.dp, FLPrimary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dayFormat.format(date),
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isSelected -> White
                            isFuture -> MediumGray
                            else -> TextPrimaryColor
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(if (dot == NutritionDayDotState.LOGGED) FLPrimary else Color.Transparent, CircleShape)
                )
            }
        }
    }
}
