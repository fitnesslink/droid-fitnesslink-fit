package com.fitnesslink.fit.ui.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.WorkoutList
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SearchView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.WorkoutsViewModel

@Composable
fun WorkoutsScreen(
    onBack: () -> Unit,
    onNavigateToWorkoutDetail: (String) -> Unit,
    onNavigateToWorkoutEditor: () -> Unit = {}
) {
    val viewModel: WorkoutsViewModel = viewModel()

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchView(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(10.dp))
            FloatingActionButton(
                onClick = onNavigateToWorkoutEditor,
                containerColor = FLPrimary,
                modifier = Modifier.size(42.dp)
            ) {
                Text("+", color = White, fontSize = 20.sp)
            }
        }
        LevelFilterRow(
            selected = viewModel.levelFilter,
            onSelect = { viewModel.levelFilter = it }
        )

        if (viewModel.visibleWorkouts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No workouts match this filter",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Clear the level filter to see everything.",
                    fontSize = 13.sp,
                    color = TextSecondaryColor
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                items(viewModel.visibleWorkouts) { workout ->
                    WorkoutItemView(
                        workout = workout,
                        onClick = { onNavigateToWorkoutDetail(workout.id) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun LevelFilterRow(
    selected: String?,
    onSelect: (String?) -> Unit
) {
    val options = listOf(null, "Beginner", "Intermediate", "Advanced")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Level",
            fontSize = 12.sp,
            color = TextSecondaryColor,
            modifier = Modifier.padding(end = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(options) { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) FLPrimary else White)
                        .border(
                            1.dp,
                            if (isSelected) FLPrimary else TextSecondaryColor.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = option ?: "All",
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) White else TextSecondaryColor
                    )
                }
            }
        }
    }
}

@Composable
fun WorkoutItemView(
    workout: WorkoutList,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
    ) {
        FLImageView(
            ref = MediaRef.WorkoutThumbnail(workout.id),
            height = 140.dp,
            modifier = Modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workout.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = workout.time,
                    fontSize = 13.sp,
                    color = TextSecondaryColor
                )
            }
            Image(
                painter = painterResource(
                    if (workout.isFavorite) R.drawable.heartselected else R.drawable.heart
                ),
                contentDescription = "Favorite",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
