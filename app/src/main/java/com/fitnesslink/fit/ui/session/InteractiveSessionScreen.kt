package com.fitnesslink.fit.ui.session

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.ExerciseProgress
import com.fitnesslink.fit.model.WorkoutProgress
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.ui.components.BackCircleButton
import com.fitnesslink.fit.ui.components.ContextCircleButton
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.PauseButtonView
import com.fitnesslink.fit.ui.components.PlayButtonView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.ImagePlaceholder
import com.fitnesslink.fit.ui.theme.InfoColor
import com.fitnesslink.fit.ui.theme.ProgressBG
import com.fitnesslink.fit.ui.theme.ProgressHighlight
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.InteractiveSessionViewModel

@Composable
fun InteractiveSessionScreen(
    workoutId: String,
    onBack: () -> Unit
) {
    val viewModel: InteractiveSessionViewModel = viewModel()

    LaunchedEffect(workoutId) { viewModel.loadData(workoutId) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stop()
            viewModel.removeRestTimer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Background
        if (viewModel.isRest) {
            FLImageView(url = viewModel.workoutTask.iconUrl, height = 400.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(Color.Black.copy(alpha = 0.8f))
            )
            // Rest text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 150.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "REST TIME",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = viewModel.rest,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(ImagePlaceholder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }
        }

        // Next exercise preview
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 250.dp, end = 20.dp)
                .clickable { viewModel.getNextExercise() }
        ) {
            Text(
                text = "Next Exercise",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(White)
            ) {
                FLImageView(url = viewModel.workoutTask.nextImageUrl, height = 88.dp)
            }
        }

        // Controls panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(BackgroundColor, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .padding(20.dp)
        ) {
            // Top tags
            WorkoutControlTopView(
                task = viewModel.workoutTask,
                currentRound = viewModel.currentRound
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Workout progress bar
            WorkoutProgressBarView(progress = viewModel.workoutProgress)

            Spacer(modifier = Modifier.height(16.dp))

            // Timer
            Text(
                text = viewModel.timerText,
                fontSize = 46.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Exercise info + progress
            WorkoutExerciseView(
                task = viewModel.workoutTask,
                exerciseProgress = viewModel.exerciseProgress
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PREV",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FLPrimary,
                    modifier = Modifier.clickable { viewModel.getPrevious() }
                )
                Spacer(modifier = Modifier.width(25.dp))
                Box(modifier = Modifier.clickable { viewModel.toggleVideoState() }) {
                    if (viewModel.isVideoPaused) PlayButtonView() else PauseButtonView()
                }
                Spacer(modifier = Modifier.width(25.dp))
                Text(
                    text = "NEXT",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FLPrimary,
                    modifier = Modifier.clickable { viewModel.getNext() }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 50.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackCircleButton(onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
            ContextCircleButton(onClick = {})
        }
    }
}

@Composable
fun WorkoutControlTopView(
    task: WorkoutTask,
    currentRound: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (task.phaseName.uppercase() == "WARM UP" || task.phaseName.uppercase() == "COOL DOWN") {
            Text(
                text = task.phaseName,
                fontSize = 14.sp,
                color = if (task.phaseName.uppercase() == "WARM UP") Color.Black else White,
                modifier = Modifier
                    .background(
                        if (task.phaseName.uppercase() == "WARM UP") ProgressHighlight else BlueTheme,
                        RoundedCornerShape(5.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        if (task.isSuperset || task.isCircuit) {
            Text(
                text = task.advancedMovement,
                fontSize = 14.sp,
                color = White,
                modifier = Modifier
                    .background(
                        if (task.isSuperset) PurpleTheme else BlueTheme,
                        RoundedCornerShape(5.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$currentRound/${task.totalRounds} ROUNDS",
                fontSize = 14.sp,
                modifier = Modifier
                    .background(InfoColor, RoundedCornerShape(5.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.kettlebell),
            contentDescription = "Kettlebell",
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun WorkoutProgressBarView(progress: List<WorkoutProgress>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        progress.forEach { item ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (item.progress > 0) ProgressHighlight else ProgressBG)
            )
        }
    }
}

@Composable
fun WorkoutExerciseView(
    task: WorkoutTask,
    exerciseProgress: List<ExerciseProgress>
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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
        Spacer(modifier = Modifier.height(8.dp))
        ExerciseProgressBarView(progress = exerciseProgress)
    }
}

@Composable
fun ExerciseProgressBarView(progress: List<ExerciseProgress>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        progress.forEach { item ->
            if (item.isRest) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(ProgressBG, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (item.progress > 0) FLPrimary else ProgressBG)
                )
            }
        }
    }
}
