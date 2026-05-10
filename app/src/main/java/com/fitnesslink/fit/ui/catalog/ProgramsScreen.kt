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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.ProgramList
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SearchView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.ProgramsViewModel

@Composable
fun ProgramsScreen(
    onBack: () -> Unit,
    onNavigateToProgramDetail: (String) -> Unit,
    onNavigateToProgramEditor: () -> Unit = {}
) {
    val viewModel: ProgramsViewModel = viewModel()

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            HeaderBackView(onBack = onBack)
            IconButton(
                onClick = onNavigateToProgramEditor,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create Program",
                    tint = FLPrimary
                )
            }
        }
        SearchView(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )

        // Training-level filter strip — null entry means "All".
        FilterChipRow(
            label = "Level",
            options = listOf(null, "Beginner", "Intermediate", "Advanced"),
            selected = viewModel.levelFilter,
            onSelect = { viewModel.levelFilter = it },
            displayName = { it ?: "All" }
        )
        // Length filter strip — buckets aligned to common program durations.
        FilterChipRow(
            label = "Length",
            options = listOf(null, 1..4, 5..8, 9..12, 13..Int.MAX_VALUE),
            selected = viewModel.lengthFilter,
            onSelect = { viewModel.lengthFilter = it },
            displayName = { range ->
                when {
                    range == null -> "Any"
                    range.last == Int.MAX_VALUE -> "13+ wk"
                    else -> "${range.first}–${range.last} wk"
                }
            }
        )

        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            items(viewModel.visiblePrograms) { program ->
                ProgramItemView(
                    program = program,
                    onClick = { onNavigateToProgramDetail(program.id) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun <T> FilterChipRow(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    displayName: (T) -> String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
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
                        text = displayName(option),
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
fun ProgramItemView(
    program: ProgramList,
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
            ref = MediaRef.ProgramThumbnail(program.id),
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
                    text = program.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = programMeta(program),
                    fontSize = 13.sp,
                    color = TextSecondaryColor
                )
            }
            Image(
                painter = painterResource(
                    if (program.isFavorite) R.drawable.heartselected else R.drawable.heart
                ),
                contentDescription = "Favorite",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun programMeta(p: ProgramList): String {
    val parts = buildList {
        p.weeks?.takeIf { it > 0 }?.let { add("$it wk") }
        if (p.trainingLevel.isNotBlank()) add(p.trainingLevel)
        if (p.time.isNotBlank()) add(p.time)
    }
    return parts.joinToString(" · ")
}
