package com.fitnesslink.fit.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.White

/**
 * The four things the calendar's add menu can do (iOS `CalendarAddAction`).
 * Every entry point — header +, the selected cell's + badge, long-press on a
 * day, the inline add row — funnels into `CalendarViewModel.chooseAddAction`.
 */
enum class CalendarAddAction { SCHEDULE_WORKOUT, SCHEDULE_PROGRAM, CREATE_WORKOUT, CREATE_PROGRAM }

/** Which trigger the add menu is anchored to; null = closed (iOS `CalendarAddMenuAnchor`). */
sealed class CalendarAddMenuAnchor {
    data class Cell(val day: Int) : CalendarAddMenuAnchor()
    object InlineRow : CalendarAddMenuAnchor()
    object HeaderPlus : CalendarAddMenuAnchor()
}

/** iOS `CalendarAddMenuPalette`. Option text is brand FLPrimary (product decision, 2026-09-11). */
object CalendarAddMenuPalette {
    val secondaryText = Color(0xFF8A8A8E)
    val dashedBorder = Color(0xFF9ED9CB)
    val menuBackground = Color(0xFFFAFAFA)
    val separator = Color(0x333C3C43)
    val groupSeparator = Color(0x1F3C3C43)
}

/**
 * Anchored, branded add menu (iOS `CalendarAddMenuPopover`). Rendered as a
 * `DropdownMenu` so it drops from whatever triggered it; the host wraps the
 * trigger in a `Box` and places this inside it.
 *
 * @param title e.g. "Add to Thu, Sep 10"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarAddMenuPopover(
    expanded: Boolean,
    title: String,
    onChoose: (CalendarAddAction) -> Unit,
    onDismiss: () -> Unit,
    offset: DpOffset = DpOffset.Zero
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        shape = RoundedCornerShape(12.dp),
        containerColor = CalendarAddMenuPalette.menuBackground
    ) {
        Column(modifier = Modifier.width(250.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = CalendarAddMenuPalette.secondaryText,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 7.dp)
            )
            MenuSeparator()
            MenuRow("Schedule a Workout", Icons.Default.FitnessCenter) { onChoose(CalendarAddAction.SCHEDULE_WORKOUT) }
            MenuSeparator()
            MenuRow("Schedule a Program", Icons.Default.Layers) { onChoose(CalendarAddAction.SCHEDULE_PROGRAM) }
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(CalendarAddMenuPalette.groupSeparator))
            MenuRow("Create New Workout", Icons.Default.AddCircleOutline) { onChoose(CalendarAddAction.CREATE_WORKOUT) }
            MenuSeparator()
            MenuRow("Create New Program", Icons.Default.AddBox) { onChoose(CalendarAddAction.CREATE_PROGRAM) }
        }
    }
}

@Composable
private fun MenuSeparator() {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(CalendarAddMenuPalette.separator))
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 17.sp, color = FLPrimary, modifier = Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp))
    }
}

/**
 * Dashed "Add to <day>" row above the selected day's items; doubles as the
 * empty state's call to action (iOS `CalendarAddRow`). Tapping opens the add
 * menu anchored to the row.
 */
@Composable
fun CalendarAddRow(
    title: String,
    isMenuPresented: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onChoose: (CalendarAddAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val dashColor = CalendarAddMenuPalette.dashedBorder
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(FLPrimary.copy(alpha = 0.04f))
                .drawBehind {
                    drawRoundRect(
                        color = dashColor,
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                    )
                }
                .clickable(onClick = onOpenMenu)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
        }
        CalendarAddMenuPopover(expanded = isMenuPresented, title = title, onChoose = onChoose, onDismiss = onDismissMenu)
    }
}

/** 16dp green + badge with a 2dp white ring, pinned to the selected day's number ring (iOS `CalendarDayAddBadge`). */
@Composable
fun CalendarDayAddBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .background(White, CircleShape)
            .padding(2.dp)
            .background(FLPrimary, CircleShape)
            .border(0.dp, Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add", tint = White, modifier = Modifier.size(10.dp))
    }
}
