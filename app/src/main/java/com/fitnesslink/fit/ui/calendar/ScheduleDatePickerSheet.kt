package com.fitnesslink.fit.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnesslink.fit.calendar.CalendarDurationChips
import com.fitnesslink.fit.ui.components.PrimaryButtonView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * iOS `ScheduleDatePickerSheet` (FTNSSLNK-329): date (today onward), time,
 * optional duration chips (15/30/45/60/75/90 merged with the workout's own
 * estimate, pre-selected), custom title + CTA.
 *
 * @param initialDate seeds date + time; a past date clamps to now (the picker only allows today onward)
 * @param workoutEstimateMinutes the workout's estimate — merged into and pre-selected among the chips
 * @param showDurationChips false for flows that schedule without a duration (move, program start)
 * @param onConfirm the chosen instant and the chosen duration (null = no explicit choice)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDatePickerSheet(
    initialDate: Date?,
    workoutEstimateMinutes: Int?,
    title: String,
    cta: String,
    onConfirm: (date: Date, durationMinutes: Int?) -> Unit,
    onDismiss: () -> Unit,
    showDurationChips: Boolean = true
) {
    val now = remember { System.currentTimeMillis() }
    val seed = remember(initialDate) { maxOf(initialDate?.time ?: now, now) }
    val seedCal = remember(seed) { Calendar.getInstance().apply { timeInMillis = seed } }
    val todayUtc = remember(now) { localDayToUtcMidnight(now) }

    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = localDayToUtcMidnight(seed),
        selectableDates = remember(todayUtc) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtc
                override fun isSelectableYear(year: Int): Boolean = year >= Calendar.getInstance().get(Calendar.YEAR)
            }
        }
    )
    val timeState = rememberTimePickerState(
        initialHour = seedCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = seedCal.get(Calendar.MINUTE)
    )
    var selectedDuration by remember { mutableStateOf(workoutEstimateMinutes?.takeIf { it > 0 }) }
    val durationOptions = remember(workoutEstimateMinutes) { CalendarDurationChips.options(workoutEstimateMinutes) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(White)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = FLPrimary) }
                    Spacer(modifier = Modifier.weight(1f))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    DatePicker(
                        state = dateState,
                        title = null,
                        headline = null,
                        showModeToggle = false,
                        colors = DatePickerDefaults.colors(
                            containerColor = White,
                            selectedDayContainerColor = FLPrimary,
                            todayDateBorderColor = FLPrimary,
                            todayContentColor = FLPrimary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Time", fontSize = 15.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                        TimeInput(state = timeState)
                    }
                    if (showDurationChips) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Duration", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                durationOptions.forEach { minutes ->
                                    val isSelected = selectedDuration == minutes
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) FLPrimary else BackgroundColor)
                                            .border(1.dp, if (isSelected) FLPrimary else MediumGray.copy(alpha = 0.4f), CircleShape)
                                            .clickable { selectedDuration = if (isSelected) null else minutes }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            CalendarDurationChips.label(minutes, workoutEstimateMinutes),
                                            fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                            color = if (isSelected) White else TextPrimaryColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    PrimaryButtonView(
                        text = cta,
                        modifier = Modifier.clickable {
                            val dayUtc = dateState.selectedDateMillis ?: localDayToUtcMidnight(seed)
                            val result = utcMidnightToLocal(dayUtc).apply {
                                set(Calendar.HOUR_OF_DAY, timeState.hour)
                                set(Calendar.MINUTE, timeState.minute)
                                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            }
                            onConfirm(result.time, if (showDurationChips) selectedDuration else null)
                        }
                    )
                }
            }
        }
    }
}

/** Material's DatePicker works in UTC-midnight millis; map a local instant's calendar day onto that. */
internal fun localDayToUtcMidnight(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

internal fun utcMidnightToLocal(utcMillis: Long): Calendar {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }
}
