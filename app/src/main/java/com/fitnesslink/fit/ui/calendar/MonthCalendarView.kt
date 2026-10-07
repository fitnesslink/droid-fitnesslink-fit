package com.fitnesslink.fit.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.calendar.CalendarDayKeys
import com.fitnesslink.fit.model.CalendarCell
import com.fitnesslink.fit.model.CalendarHeaderCell
import com.fitnesslink.fit.ui.theme.CalendarLine
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Month grid (iOS `MonthCalendarView`): "MMM yyyy" label opening the wheel
 * month/year picker, prev / next chevrons, a header + that opens the add
 * menu for the selected day, Sunday-first weekday row, and 7-column day
 * cells. Tap selects; long-press selects and opens the add menu on the cell;
 * the selected cell grows a + badge that opens the same menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthCalendarView(
    cells: List<CalendarCell>,
    monthMillis: Long,
    selectedDateMillis: Long,
    addMenuAnchor: CalendarAddMenuAnchor?,
    onAddMenuAnchorChange: (CalendarAddMenuAnchor?) -> Unit,
    onDayTapped: (Int) -> Unit,
    onChooseAdd: (CalendarAddAction) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMonthSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMonthPicker by remember { mutableStateOf(false) }
    val headerItems = remember {
        listOf("S", "M", "T", "W", "T", "F", "S").mapIndexed { i, n -> CalendarHeaderCell(i + 1, n) }
    }
    val monthLabel = remember(monthMillis) { SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(monthMillis)) }

    if (showMonthPicker) {
        MonthYearPickerSheet(
            initialMonthMillis = monthMillis,
            onDismiss = { showMonthPicker = false },
            onConfirm = { millis -> showMonthPicker = false; onMonthSelected(millis) }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(White)
            .padding(16.dp)
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { showMonthPicker = true }
            ) {
                Text(monthLabel, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select Month", tint = FLPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = FLPrimary,
                modifier = Modifier.size(28.dp).clickable(onClick = onPreviousMonth).padding(2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight, contentDescription = "Next month", tint = FLPrimary,
                modifier = Modifier.size(28.dp).clickable(onClick = onNextMonth).padding(2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                Image(
                    painter = painterResource(R.drawable.greenplus),
                    contentDescription = "Add",
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onAddMenuAnchorChange(CalendarAddMenuAnchor.HeaderPlus) }
                        .padding(9.dp)
                )
                CalendarAddMenuPopover(
                    expanded = addMenuAnchor == CalendarAddMenuAnchor.HeaderPlus,
                    title = "Add to ${CalendarDayKeys.dayLabel(selectedDateMillis)}",
                    onChoose = onChooseAdd,
                    onDismiss = { if (addMenuAnchor == CalendarAddMenuAnchor.HeaderPlus) onAddMenuAnchorChange(null) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            headerItems.forEach { day ->
                Text(day.name, fontSize = 10.sp, textAlign = TextAlign.Center, color = TextPrimaryColor, modifier = Modifier.weight(1f))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(1.dp).background(CalendarLine))

        cells.chunked(7).forEach { rowCells ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowCells.forEach { cell ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (cell.dayNumber > 0) {
                            DayCell(
                                cell = cell,
                                dayMillis = dayMillis(monthMillis, cell.dayNumber),
                                menuOpen = (addMenuAnchor as? CalendarAddMenuAnchor.Cell)?.day == cell.dayNumber,
                                onTap = { onDayTapped(cell.dayNumber) },
                                onLongPress = { onDayTapped(cell.dayNumber); onAddMenuAnchorChange(CalendarAddMenuAnchor.Cell(cell.dayNumber)) },
                                onBadgeTap = { onAddMenuAnchorChange(CalendarAddMenuAnchor.Cell(cell.dayNumber)) },
                                onChooseAdd = onChooseAdd,
                                onDismissMenu = { if ((addMenuAnchor as? CalendarAddMenuAnchor.Cell)?.day == cell.dayNumber) onAddMenuAnchorChange(null) }
                            )
                        } else {
                            CalendarCellView(cell = cell)
                        }
                    }
                }
                repeat(7 - rowCells.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    cell: CalendarCell,
    dayMillis: Long,
    menuOpen: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onBadgeTap: () -> Unit,
    onChooseAdd: (CalendarAddAction) -> Unit,
    onDismissMenu: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        CalendarCellView(
            cell = cell,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap,
                    onLongClick = onLongPress
                )
        )
        if (cell.selected) {
            // Top-right of the number ring: ring is centred; badge box sits at (+7, -5) from its corner.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = CalendarCellMetrics.ringSize / 2 + 7.dp - 10.dp, y = (CalendarCellMetrics.cellHeight - (CalendarCellMetrics.ringSize + CalendarCellMetrics.indicatorSpacing + CalendarCellMetrics.indicatorSize)) / 2 - 5.dp)
                    .size(28.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBadgeTap),
                contentAlignment = Alignment.Center
            ) {
                CalendarDayAddBadge()
            }
        }
        CalendarAddMenuPopover(
            expanded = menuOpen,
            title = "Add to ${CalendarDayKeys.dayLabel(dayMillis)}",
            onChoose = onChooseAdd,
            onDismiss = onDismissMenu
        )
    }
}

private fun dayMillis(monthMillis: Long, day: Int): Long = Calendar.getInstance().apply {
    timeInMillis = monthMillis
    set(Calendar.DAY_OF_MONTH, day.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
}.timeInMillis
