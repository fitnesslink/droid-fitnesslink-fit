package com.fitnesslink.fit.ui.catalog

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.ProgramWeek
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.ui.components.BackCircleButton
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.PrimaryButtonView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.components.TimeInfoView
import com.fitnesslink.fit.ui.components.TrainingLevelInfoView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.ProgramDetailViewModel

@Composable
fun ProgramDetailScreen(
    programId: String,
    onBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit = {}
) {
    val viewModel: ProgramDetailViewModel = viewModel()
    var showDeleteAlert by remember { mutableStateOf(false) }

    LaunchedEffect(programId) { viewModel.loadData(programId) }

    // Delete confirmation
    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text("Delete Program?") },
            text = { Text("This will permanently delete this program and its schedule.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAlert = false
                    viewModel.deleteProgram(programId)
                    onBack()
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlert = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Box {
            FLImageView(ref = MediaRef.ProgramThumbnail(viewModel.program.id), height = 250.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp, start = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BackCircleButton(onClick = onBack)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { onNavigateToEditor(programId) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { showDeleteAlert = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = viewModel.program.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                TrainingLevelInfoView(level = viewModel.program.trainingLevel)
                Spacer(modifier = Modifier.width(16.dp))
                TimeInfoView(time = viewModel.program.time)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = viewModel.program.description,
                fontSize = 14.sp,
                color = TextSecondaryColor
            )

            // Schedule overview
            if (viewModel.scheduleWeeks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Schedule", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                viewModel.scheduleWeeks.forEach { week ->
                    ProgramDetailWeekView(week = week)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
        ) {
            SeparatorView()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .clickable { }
            ) {
                PrimaryButtonView(text = "Start Program")
            }
        }
    }
}

@Composable
private fun ProgramDetailWeekView(week: ProgramWeek) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .padding(10.dp)
    ) {
        Text("Week ${week.weekNumber}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            week.days.forEach { day ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(day.dayLabel, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                    Spacer(modifier = Modifier.height(2.dp))
                    if (day.workout != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(FLPrimary.copy(alpha = 0.1f))
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day.workout.name,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(BackgroundColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Rest", fontSize = 9.sp, color = MediumGray)
                        }
                    }
                }
            }
        }
    }
}
