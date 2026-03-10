package com.fitnesslink.fit.ui.catalog

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.ui.components.BackCircleButton
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.PrimaryButtonView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.components.TimeInfoView
import com.fitnesslink.fit.ui.components.TrainingLevelInfoView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.ProgramDetailViewModel

@Composable
fun ProgramDetailScreen(
    programId: String,
    onBack: () -> Unit
) {
    val viewModel: ProgramDetailViewModel = viewModel()

    LaunchedEffect(programId) { viewModel.loadData(programId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Box {
            FLImageView(url = viewModel.program.imageUrl, height = 250.dp)
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
