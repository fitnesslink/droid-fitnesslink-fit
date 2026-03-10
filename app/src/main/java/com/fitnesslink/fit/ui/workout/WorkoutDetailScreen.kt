package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.ui.components.BackCircleButton
import com.fitnesslink.fit.ui.components.ExercisesInfoView
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.PrimaryButtonView
import com.fitnesslink.fit.ui.components.SecondaryButtonView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.components.TimeInfoView
import com.fitnesslink.fit.ui.components.TrainingLevelInfoView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.ImagePlaceholder
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.WorkoutDetailViewModel

@Composable
fun WorkoutDetailScreen(
    workoutId: String,
    onBack: () -> Unit,
    onStartPlaylist: (String) -> Unit = {},
    onStartInteractive: (String) -> Unit = {}
) {
    val viewModel: WorkoutDetailViewModel = viewModel()

    LaunchedEffect(workoutId) { viewModel.loadData(workoutId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Box {
            FLImageView(url = viewModel.workout.imageUrl, height = 250.dp)
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
            BackCircleButton(
                onClick = onBack,
                modifier = Modifier.padding(top = 60.dp, start = 20.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = viewModel.workout.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                TrainingLevelInfoView(level = viewModel.workout.trainingLevel)
                Spacer(modifier = Modifier.width(16.dp))
                TimeInfoView(time = viewModel.workout.time)
                Spacer(modifier = Modifier.width(16.dp))
                ExercisesInfoView(count = viewModel.exerciseCount)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = viewModel.workout.description,
                fontSize = 14.sp,
                color = TextSecondaryColor
            )
            Spacer(modifier = Modifier.height(16.dp))
            WorkoutTasksView(phases = viewModel.workout.phases)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
        ) {
            SeparatorView()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { }
                ) {
                    SecondaryButtonView(text = "Add to Calendar")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onStartPlaylist(workoutId) }
                ) {
                    PrimaryButtonView(text = if (viewModel.hasSession) "Continue" else "Start Now")
                }
            }
        }
    }
}

@Composable
fun WorkoutTasksView(phases: List<WorkoutPhase>) {
    Column {
        phases.forEach { phase ->
            Text(
                text = phase.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = FLPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            phase.taskRows.forEach { row ->
                val task = row.task
                if (task != null) {
                    if (task.isMovement) {
                        if (row.advancedTasks.isNotEmpty()) {
                            AdvancedMovementsView(row = row)
                        } else {
                            WorkoutMovementView(task = task)
                        }
                    } else if (task.isRest) {
                        WorkoutRestView(task = task)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun WorkoutMovementView(task: WorkoutTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ImagePlaceholder)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = task.metric,
                fontSize = 12.sp,
                color = TextSecondaryColor
            )
        }
        if (task.rest.isNotEmpty()) {
            Text(
                text = task.rest,
                fontSize = 11.sp,
                color = TextSecondaryColor
            )
        }
    }
}

@Composable
fun WorkoutRestView(task: WorkoutTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ImagePlaceholder),
            contentAlignment = Alignment.Center
        ) {
            Text("\uD83D\uDCA4", fontSize = 24.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "Rest",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = task.rest,
                fontSize = 12.sp,
                color = TextSecondaryColor
            )
        }
    }
}

@Composable
fun AdvancedMovementsView(row: TaskRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundColor, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        if (row.advanced.isNotEmpty()) {
            Row {
                Text(
                    text = row.advanced,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FLPrimary
                )
                if (row.totalRounds > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${row.rounds} rounds",
                        fontSize = 12.sp,
                        color = TextSecondaryColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        row.task?.let { task ->
            WorkoutMovementView(task = task)
            Spacer(modifier = Modifier.height(4.dp))
        }
        row.advancedTasks.forEach { task ->
            WorkoutMovementView(task = task)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
