package com.fitnesslink.fit.ui.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.ui.components.BackCircleButton
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.TimeInfoView
import com.fitnesslink.fit.ui.components.TrainingLevelInfoView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.ImagePlaceholder
import com.fitnesslink.fit.ui.theme.InfoColor
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.PlaylistViewModel

@Composable
fun PlaylistScreen(
    workoutId: String,
    onBack: () -> Unit,
    onNavigateToInteractive: (String) -> Unit = {}
) {
    val viewModel: PlaylistViewModel = viewModel()

    LaunchedEffect(workoutId) { viewModel.loadData(workoutId) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image
        FLImageView(url = viewModel.workout.imageUrl, height = 370.dp)

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(370.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 60.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackCircleButton(onClick = onBack)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(White, CircleShape)
                        .clip(CircleShape)
                        .then(
                            Modifier.padding(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.interactive),
                        contentDescription = "Interactive",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(140.dp))

            // Workout info
            Column(
                modifier = Modifier.padding(horizontal = 25.dp)
            ) {
                Text(
                    text = viewModel.workout.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    TrainingLevelInfoView(level = viewModel.workout.trainingLevel)
                    Spacer(modifier = Modifier.width(10.dp))
                    TimeInfoView(time = viewModel.workout.time)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 30.dp)
            ) {
                Text(
                    text = "Description",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = viewModel.workout.description,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                viewModel.workout.phases.forEach { phase ->
                    PlaylistPhaseView(phase = phase)
                }

                Spacer(modifier = Modifier.height(200.dp))
            }
        }
    }
}

@Composable
fun PlaylistPhaseView(phase: WorkoutPhase) {
    Column {
        Text(
            text = phase.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        phase.taskRows.forEach { row ->
            if (row.advancedTasks.isNotEmpty()) {
                PlaylistAdvancedMovementsView(row = row)
            } else {
                val task = row.task
                if (task != null) {
                    if (task.isMovement) {
                        PlaylistMovementView(task = task)
                    } else if (task.isRest) {
                        PlaylistRestView(task = task)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun PlaylistMovementView(task: WorkoutTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
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
                fontWeight = FontWeight.Bold
            )
            Text(
                text = task.metric,
                fontSize = 14.sp
            )
        }
        Image(
            painter = painterResource(R.drawable.menu),
            contentDescription = "Menu",
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun PlaylistRestView(task: WorkoutTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ImagePlaceholder),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.rest),
                contentDescription = "Rest",
                modifier = Modifier.size(30.dp)
            )
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
fun PlaylistAdvancedMovementsView(row: TaskRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, InfoColor, RoundedCornerShape(6.dp))
            .background(White, RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(
                text = row.advanced,
                fontSize = 14.sp,
                color = if (row.isSuperset) White else Color.Black,
                modifier = Modifier
                    .background(
                        if (row.isSuperset) PurpleTheme else BlueTheme,
                        RoundedCornerShape(5.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = row.rounds,
                fontSize = 14.sp,
                modifier = Modifier
                    .background(InfoColor, RoundedCornerShape(5.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Column(modifier = Modifier.padding(10.dp)) {
            row.advancedTasks.forEach { task ->
                if (task.isMovement) {
                    PlaylistMovementView(task = task)
                } else if (task.isRest) {
                    PlaylistRestView(task = task)
                }
            }
        }
    }
}
