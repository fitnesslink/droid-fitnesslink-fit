package com.fitnesslink.fit.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.DateStripStatus
import com.fitnesslink.fit.model.HomeDateItem
import com.fitnesslink.fit.ui.theme.HighlightState
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.flow.first

private val CELL_WIDTH = 44.dp
private val CELL_SPACING = 20.dp
private val STRIP_PADDING = 10.dp

/**
 * Horizontal month strip (iOS `DateScrollView`). Centres [scrollTo] on
 * first layout, whenever the target day changes (midnight rollover) and
 * whenever [recenter] is bumped (Home tab re-selected / app foregrounded).
 * Tapping a day only updates the selection — the strip never scrolls out
 * from under the user.
 */
@Composable
fun DateScrollView(
    items: List<HomeDateItem>,
    scrollTo: Int,
    recenter: Int = 0,
    onSelect: (Int) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    LaunchedEffect(scrollTo, recenter, items.isNotEmpty()) {
        if (scrollTo <= 0 || items.isEmpty()) return@LaunchedEffect
        val index = items.indexOfFirst { it.dayNumber == scrollTo }.takeIf { it >= 0 } ?: return@LaunchedEffect
        // The strip lays out lazily, so on first composition the viewport
        // hasn't been measured yet — wait for it, then centre the target
        // cell: offset by half the viewport minus half a cell.
        val viewport = snapshotFlow { listState.layoutInfo.viewportSize.width }.first { it > 0 }
        val cellPx = with(density) { CELL_WIDTH.roundToPx() }
        listState.animateScrollToItem(index, -(viewport / 2 - cellPx / 2))
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = STRIP_PADDING),
        horizontalArrangement = Arrangement.spacedBy(CELL_SPACING),
        modifier = Modifier
            .fillMaxWidth()
            .background(White)
            .padding(vertical = STRIP_PADDING)
    ) {
        items(items, key = { it.dayNumber }) { item ->
            DateCellView(item = item, onClick = { onSelect(item.dayNumber) })
        }
    }
}

/** One strip cell (iOS `DateCellView`): day number, weekday and a completion indicator. */
@Composable
fun DateCellView(item: HomeDateItem, onClick: () -> Unit = {}) {
    val label = item.accessibilityLabel
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(CELL_WIDTH)
            .height(78.dp)
            .background(if (item.selected) HighlightState else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(vertical = 8.dp)
    ) {
        Text(text = item.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = item.weekDay, fontSize = 11.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.height(3.dp))
        StatusDot(status = item.status)
    }
}

@Composable
private fun StatusDot(status: String) {
    when (status) {
        DateStripStatus.COMPLETED -> Image(
            painter = painterResource(R.drawable.completedstate),
            contentDescription = null,
            modifier = Modifier.size(13.dp)
        )
        DateStripStatus.SCHEDULED -> Image(
            painter = painterResource(R.drawable.scheduledstate),
            contentDescription = null,
            modifier = Modifier.size(13.dp)
        )
        // Rest days (inside a program span) and empty days show no dot, like iOS.
        else -> Spacer(modifier = Modifier.size(13.dp))
    }
}
