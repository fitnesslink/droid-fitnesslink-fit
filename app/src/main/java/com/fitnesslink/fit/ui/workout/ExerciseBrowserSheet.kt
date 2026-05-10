package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.*
import com.fitnesslink.fit.ui.theme.*
import com.fitnesslink.fit.viewmodel.ExerciseBrowserViewModel

@Composable
fun ExerciseBrowserSheet(
    onSelect: (MovementLibraryItem) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
    pendingGroupType: ExerciseGroupType?,
    pendingCount: Int
) {
    val viewModel: ExerciseBrowserViewModel = viewModel()
    var addedIds by remember { mutableStateOf(emptySet<String>()) }
    var previewMovement by remember { mutableStateOf<MovementLibraryItem?>(null) }

    LaunchedEffect(Unit) { viewModel.loadData() }

    previewMovement?.let { mv ->
        MovementPreviewSheet(
            movement = mv,
            onAdd = {
                onSelect(mv)
                addedIds = addedIds + mv.id
                previewMovement = null
            },
            onDismiss = { previewMovement = null }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(White)
        ) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Add Exercise", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Search bar
            OutlinedTextField(
                value = viewModel.searchText,
                onValueChange = { viewModel.searchText = it; viewModel.search() },
                placeholder = { Text("Search exercises...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MediumGray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = BackgroundColor,
                    focusedContainerColor = BackgroundColor
                )
            )

            // Group building banner
            if (pendingGroupType != null) {
                val message = when {
                    pendingGroupType == ExerciseGroupType.Superset && (2 - pendingCount) > 0 ->
                        "Pick ${2 - pendingCount} more exercise for superset"
                    pendingGroupType == ExerciseGroupType.Superset ->
                        "Superset ready — close to finish"
                    pendingCount < 2 ->
                        "Pick at least ${2 - pendingCount} more exercises"
                    else ->
                        "$pendingCount exercises selected — close to finish or keep adding"
                }
                val bannerColor = when (pendingGroupType) {
                    ExerciseGroupType.Superset -> PurpleTheme
                    ExerciseGroupType.Circuit -> OrangeTheme
                    ExerciseGroupType.Interval -> BlueTheme
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bannerColor)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Building ${pendingGroupType.label} — $message",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = White
                    )
                }
            }

            // Filter chips
            LazyRow(
                modifier = Modifier.padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MuscleGroup.entries) { mg ->
                    FilterChip(
                        label = mg.label,
                        isSelected = viewModel.selectedMuscleGroup == mg,
                        onClick = {
                            viewModel.selectedMuscleGroup = if (viewModel.selectedMuscleGroup == mg) null else mg
                            viewModel.search()
                        }
                    )
                }
                items(EquipmentType.entries) { eq ->
                    FilterChip(
                        label = eq.label,
                        isSelected = viewModel.selectedEquipment == eq,
                        onClick = {
                            viewModel.selectedEquipment = if (viewModel.selectedEquipment == eq) null else eq
                            viewModel.search()
                        }
                    )
                }
            }

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                BrowserTab.entries.forEach { tab ->
                    val isSelected = viewModel.selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) FLPrimary else BackgroundColor)
                            .clickable {
                                viewModel.selectedTab = tab
                                viewModel.loadData()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tab.label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = if (isSelected) White else TextPrimaryColor)
                    }
                }
            }

            // Create new button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreateNew)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New Exercise", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
            }

            HorizontalDivider()

            // Exercise list
            if (viewModel.movements.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MediumGray, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No exercises found", fontSize = 15.sp, color = TextSecondaryColor)
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn {
                        items(viewModel.movements) { movement ->
                            val isAdded = addedIds.contains(movement.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect(movement)
                                        addedIds = addedIds + movement.id
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isAdded) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                                        tint = FLPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(movement.name, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                        color = if (isAdded) FLPrimary else TextPrimaryColor)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(movement.muscleGroup, fontSize = 11.sp, color = TextSecondaryColor)
                                        Text("·", color = TextSecondaryColor)
                                        Text(movement.equipment, fontSize = 11.sp, color = TextSecondaryColor)
                                    }
                                }
                                IconButton(onClick = { previewMovement = movement }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "Preview",
                                        tint = MediumGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleFavorite(movement.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        if (movement.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (movement.isFavorite) androidx.compose.ui.graphics.Color.Red else MediumGray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }

                    // Added count badge
                    if (addedIds.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(FLPrimary)
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${addedIds.size} exercise${if (addedIds.size == 1) "" else "s"} added",
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) FLPrimary else BackgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = if (isSelected) White else TextPrimaryColor)
    }
}
