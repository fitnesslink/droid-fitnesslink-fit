package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.MealPlanDay
import com.fitnesslink.fit.model.NutritionCalendarMonth
import com.fitnesslink.fit.model.NutritionDayDotState
import com.fitnesslink.fit.network.dto.NutritionDayRollup
import com.fitnesslink.fit.nutrition.NutritionSummaryStore
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Month calendar for jumping the dashboard to any day (iOS
 * `NutritionCalendarSheet`, FTNSSLNK-196). Each day carries a completeness
 * dot from the RANGE summary: filled = food or water logged. Future days are
 * muted but selectable. Picking a day applies and dismisses in one tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionCalendarSheet(
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    var monthAnchor by remember { mutableStateOf(NutritionCalendarMonth.firstOfMonth(selectedDate)) }
    val rollupsByDay = remember { mutableStateMapOf<String, NutritionDayRollup>() }
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("d", Locale.getDefault()) }

    LaunchedEffect(monthAnchor) {
        val first = monthAnchor
        val last = NutritionCalendarMonth.lastOfMonth(first)
        val range = NutritionSummaryStore.range(MealPlanDay.string(first), MealPlanDay.string(last))
        range?.days?.forEach { rollupsByDay[it.date] = it }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BackgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Select Day", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = FLPrimary,
                    modifier = Modifier.size(32.dp).clickable { monthAnchor = NutritionCalendarMonth.firstOfMonth(monthAnchor, -1) }
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(monthFormat.format(monthAnchor), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.ChevronRight, contentDescription = "Next month", tint = FLPrimary,
                    modifier = Modifier.size(32.dp).clickable { monthAnchor = NutritionCalendarMonth.firstOfMonth(monthAnchor, 1) }
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { symbol ->
                    Text(
                        symbol, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            val cells = NutritionCalendarMonth.gridDays(monthAnchor)
            val selected = MealPlanDay.dayStart(selectedDate)
            val today = MealPlanDay.dayStart(Date())
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            Box(modifier = Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    val isSelected = date == selected
                                    val isToday = date == today
                                    val dot = NutritionDayDotState.state(date, rollupsByDay[MealPlanDay.string(date)]?.hasData)
                                    val isFuture = dot == NutritionDayDotState.FUTURE
                                    Column(
                                        modifier = Modifier.clickable { onDateSelected(date); onDismiss() },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(if (isSelected) FLPrimary else Color.Transparent, CircleShape)
                                                .then(if (isToday && !isSelected) Modifier.border(1.5.dp, FLPrimary, CircleShape) else Modifier),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                dayFormat.format(date), fontSize = 15.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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
                    }
                }
            }
        }
    }
}
