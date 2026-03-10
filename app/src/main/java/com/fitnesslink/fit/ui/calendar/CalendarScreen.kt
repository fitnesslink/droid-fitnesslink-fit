package com.fitnesslink.fit.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.CalendarCell
import com.fitnesslink.fit.model.CalendarHeaderCell
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.ui.components.HeaderView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.CalendarLine
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.CalendarViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CalendarScreen(
    onNavigateToWorkoutDetail: (String) -> Unit = {}
) {
    val viewModel: CalendarViewModel = viewModel()

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderView()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            MonthCalendarView(cells = viewModel.calendarCells)
            Spacer(modifier = Modifier.height(16.dp))
            ScheduledContentView(
                content = viewModel.content,
                onNavigateToWorkoutDetail = onNavigateToWorkoutDetail
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MonthCalendarView(cells: List<CalendarCell>) {
    val headerItems = listOf(
        CalendarHeaderCell(1, "S"), CalendarHeaderCell(2, "M"),
        CalendarHeaderCell(3, "T"), CalendarHeaderCell(4, "W"),
        CalendarHeaderCell(5, "T"), CalendarHeaderCell(6, "F"),
        CalendarHeaderCell(7, "S")
    )

    val monthYear = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date())

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { }
            ) {
                Text(
                    text = monthYear,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FLPrimary
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select Month",
                    tint = FLPrimary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = FLPrimary,
                modifier = Modifier.clickable { }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            headerItems.forEach { day ->
                Text(
                    text = day.name,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .height(1.dp)
                .background(CalendarLine)
        )

        // Calendar grid - using simple rows instead of LazyVerticalGrid to avoid nested scroll issues
        val rows = cells.chunked(7)
        rows.forEach { rowCells ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                rowCells.forEach { cell ->
                    Box(modifier = Modifier.weight(1f)) {
                        CalendarCellView(cell = cell)
                    }
                }
                // Fill remaining columns if row is incomplete
                repeat(7 - rowCells.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun CalendarCellView(cell: CalendarCell) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .height(70.dp)
            .padding(top = 15.dp)
    ) {
        Text(
            text = cell.name,
            fontSize = 18.sp
        )
        if (cell.status == "scheduled" || cell.status == "completed") {
            Spacer(modifier = Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (cell.status == "completed") FLPrimary else FLPrimary.copy(alpha = 0.5f),
                        CircleShape
                    )
            )
        }
    }
}

@Composable
fun ScheduledContentView(
    content: List<FitnessContent>,
    onNavigateToWorkoutDetail: (String) -> Unit
) {
    Column {
        Text(
            text = "Scheduled",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        content.forEach { item ->
            DateContentView(
                content = item,
                onNavigateToWorkoutDetail = onNavigateToWorkoutDetail
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun DateContentView(
    content: FitnessContent,
    onNavigateToWorkoutDetail: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    if (content.status == "completed") FLPrimary else FLPrimary.copy(alpha = 0.5f),
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = content.title,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (content.workoutId.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View",
                tint = TextSecondaryColor,
                modifier = Modifier.clickable {
                    onNavigateToWorkoutDetail(content.workoutId)
                }
            )
        }
    }
}
