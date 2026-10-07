package com.fitnesslink.fit.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.text.DateFormatSymbols
import java.util.Calendar

/**
 * Wheel month + year picker (iOS `MonthCalendarView` "Select Month" sheet):
 * two snapping columns, years current−5 … current+5, Cancel / Done.
 * [onConfirm] receives local-midnight millis of the 1st of the chosen month.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthYearPickerSheet(
    initialMonthMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initial = remember(initialMonthMillis) { Calendar.getInstance().apply { timeInMillis = initialMonthMillis } }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val years = remember(currentYear) { ((currentYear - 5)..(currentYear + 5)).toList() }
    val months = remember { DateFormatSymbols.getInstance().months.take(12) }

    val monthState = rememberLazyListState(initialFirstVisibleItemIndex = initial.get(Calendar.MONTH))
    val yearState = rememberLazyListState(
        initialFirstVisibleItemIndex = years.indexOf(initial.get(Calendar.YEAR)).coerceAtLeast(0)
    )
    val selectedMonthIndex by remember { derivedStateOf { centeredIndex(monthState) } }
    val selectedYearIndex by remember { derivedStateOf { centeredIndex(yearState) } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = White) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = FLPrimary) }
                Text(
                    "Select Month", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        set(years[selectedYearIndex.coerceIn(years.indices)], selectedMonthIndex.coerceIn(0, 11), 1, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onConfirm(cal.timeInMillis)
                }) { Text("Done", fontWeight = FontWeight.SemiBold, color = FLPrimary) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                // Selection band behind the centred row.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(WHEEL_ROW_HEIGHT)
                        .background(BackgroundColor, RoundedCornerShape(10.dp))
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    WheelColumn(items = months, state = monthState, selectedIndex = selectedMonthIndex, modifier = Modifier.weight(1.4f))
                    WheelColumn(items = years.map { it.toString() }, state = yearState, selectedIndex = selectedYearIndex, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private val WHEEL_ROW_HEIGHT = 40.dp
private const val WHEEL_VISIBLE_ROWS = 5

/** Index of the item resting in the centre slot (content is padded by two rows, snapping is centred). */
private fun centeredIndex(state: LazyListState): Int {
    val first = state.layoutInfo.visibleItemsInfo.firstOrNull() ?: return state.firstVisibleItemIndex
    return if (state.firstVisibleItemScrollOffset > first.size / 2) first.index + 1 else first.index
}

@Composable
private fun WheelColumn(items: List<String>, state: LazyListState, selectedIndex: Int, modifier: Modifier = Modifier) {
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    LazyColumn(
        state = state,
        flingBehavior = fling,
        modifier = modifier.height(WHEEL_ROW_HEIGHT * WHEEL_VISIBLE_ROWS),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = WHEEL_ROW_HEIGHT * 2)
    ) {
        items(items.size) { index ->
            val isSelected = index == selectedIndex
            Box(modifier = Modifier.fillMaxWidth().height(WHEEL_ROW_HEIGHT), contentAlignment = Alignment.Center) {
                Text(
                    items[index],
                    fontSize = if (isSelected) 20.sp else 17.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) TextPrimaryColor else TextSecondaryColor,
                    modifier = Modifier.alpha(if (isSelected) 1f else 0.6f)
                )
            }
        }
    }
}
