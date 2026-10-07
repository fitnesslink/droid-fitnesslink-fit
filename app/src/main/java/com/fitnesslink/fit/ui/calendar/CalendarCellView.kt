package com.fitnesslink.fit.ui.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.CalendarCell
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor

/**
 * One day of the month grid (iOS `CalendarCellView`). A thin FLPrimary ring
 * marks the selected day; the status indicator slot is always reserved so
 * the number never shifts. Completed wins over scheduled (status comes from
 * the entries' real status, never the date).
 */
object CalendarCellMetrics {
    val cellHeight = 70.dp
    val ringSize = 32.dp
    val indicatorSize = 10.dp
    val indicatorSpacing = 7.dp
}

@Composable
fun CalendarCellView(cell: CalendarCell, modifier: Modifier = Modifier) {
    val ringColor by animateColorAsState(if (cell.selected) FLPrimary else Color.Transparent, label = "ring")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        modifier = modifier.height(CalendarCellMetrics.cellHeight)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(CalendarCellMetrics.ringSize)
                .border(1.5.dp, ringColor, CircleShape)
        ) {
            if (cell.dayNumber > 0) {
                Text(text = cell.name, fontSize = 18.sp, color = TextPrimaryColor)
            }
        }
        Spacer(modifier = Modifier.height(CalendarCellMetrics.indicatorSpacing))
        Box(modifier = Modifier.size(CalendarCellMetrics.indicatorSize)) {
            val asset = when (cell.status) {
                "completed" -> R.drawable.completedstate
                "scheduled" -> R.drawable.scheduledstate
                else -> null
            }
            if (asset != null) {
                Image(painter = painterResource(asset), contentDescription = cell.status, modifier = Modifier.size(CalendarCellMetrics.indicatorSize))
            }
        }
    }
}
