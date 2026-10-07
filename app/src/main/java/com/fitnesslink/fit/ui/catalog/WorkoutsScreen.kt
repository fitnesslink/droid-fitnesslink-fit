package com.fitnesslink.fit.ui.catalog

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.model.WorkoutList
import com.fitnesslink.fit.ui.components.AnatomyChipStrip
import com.fitnesslink.fit.ui.components.ClearFiltersButton
import com.fitnesslink.fit.ui.components.ContentLayout
import com.fitnesslink.fit.ui.components.ContentLayoutPrefs
import com.fitnesslink.fit.ui.components.ContentLayoutToggle
import com.fitnesslink.fit.ui.components.ContributorFilterMenu
import com.fitnesslink.fit.ui.components.EquipmentChipStrip
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.FavoritesFilterControl
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SearchView
import com.fitnesslink.fit.ui.components.SharedBadge
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.ui.workout.FavoriteHeartButton
import com.fitnesslink.fit.ui.workout.WorkoutRowView
import com.fitnesslink.fit.ui.workout.thumbRef
import com.fitnesslink.fit.viewmodel.WorkoutsViewModel

/**
 * Workouts "See All" (iOS `WorkoutsView`): search, favorites toggle,
 * contributor menu, equipment / anatomy chip strips from the list-view
 * payload, clear-filters, card / list layout toggle, heart toggle on every
 * card. Keeps Android's training-level row as an extra.
 */
@Composable
fun WorkoutsScreen(
    onBack: () -> Unit,
    onNavigateToWorkoutDetail: (String) -> Unit,
    onNavigateToWorkoutEditor: () -> Unit = {}
) {
    val viewModel: WorkoutsViewModel = viewModel()
    var layout by remember { mutableStateOf(runCatching { ContentLayoutPrefs.get() }.getOrDefault(ContentLayout.CARD)) }
    val contributors by ContributorStore.contributors.collectAsState()
    val anatomy by ClassificationStore.anatomy.collectAsState()
    val equipment by ClassificationStore.equipment.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
        runCatching { ClassificationStore.refresh() }
    }
    // Re-read the cache whenever the list comes back on screen (a workout deleted on the detail screen).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.reloadFromCache() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val presentContributors = remember(contributors, viewModel.workouts) {
        ContributorStore.present(viewModel.presentContributorIds)
    }
    val equipmentOptions = remember(equipment, viewModel.equipmentByWorkoutId) {
        val present = viewModel.equipmentByWorkoutId.values.flatten().toSet()
        ClassificationStore.primaryEquipment.filter { present.contains(it.id.lowercase()) }
    }
    val anatomyOptions = remember(anatomy, viewModel.anatomyByWorkoutId) {
        val present = viewModel.anatomyByWorkoutId.values.flatten().toSet()
        ClassificationStore.primaryAnatomy.filter { present.contains(it.id.lowercase()) }
    }

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
            SearchView(
                text = viewModel.searchText,
                onTextChange = { viewModel.searchText = it },
                modifier = Modifier.weight(1f),
                placeholder = "Search workouts",
                showFilterIcon = false
            )
            Spacer(modifier = Modifier.width(8.dp))
            ContentLayoutToggle(layout = layout, onChange = {
                layout = it
                runCatching { ContentLayoutPrefs.set(it) }
            })
            Spacer(modifier = Modifier.width(4.dp))
            FloatingActionButton(
                onClick = onNavigateToWorkoutEditor,
                containerColor = FLPrimary,
                modifier = Modifier.size(42.dp)
            ) {
                Text("+", color = White, fontSize = 20.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FavoritesFilterControl(favoritesOnly = viewModel.showFavoritesOnly, onChange = { viewModel.showFavoritesOnly = it })
            if (presentContributors.size > 1 || viewModel.mineSelected) {
                ContributorFilterMenu(
                    selectedContributorId = viewModel.selectedContributorId,
                    mineSelected = viewModel.mineSelected,
                    onSelectContributor = {
                        viewModel.selectedContributorId = it
                        if (it != null && viewModel.mineSelected) viewModel.toggleMine()
                    },
                    onToggleMine = { viewModel.toggleMine() }
                )
            }
        }

        if (equipmentOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            EquipmentChipStrip(options = equipmentOptions, selected = viewModel.selectedEquipmentIds, onToggle = viewModel::toggleEquipment)
        }
        if (anatomyOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            AnatomyChipStrip(options = anatomyOptions, selected = viewModel.selectedAnatomyIds, onToggle = viewModel::toggleAnatomy)
        }

        LevelFilterRow(selected = viewModel.levelFilter, onSelect = { viewModel.levelFilter = it })

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.End
        ) {
            ClearFiltersButton(visible = viewModel.hasActiveFilters, onClick = { viewModel.clearFilters() })
        }

        val visible = viewModel.visibleWorkouts
        if (visible.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (viewModel.hasActiveFilters || viewModel.searchText.isNotBlank()) "No workouts match these filters" else "No workouts yet",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (viewModel.hasActiveFilters || viewModel.searchText.isNotBlank()) "Clear the filters to see everything." else "Tap + to build your first workout.",
                    fontSize = 13.sp,
                    color = TextSecondaryColor
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {
                items(visible, key = { it.id }) { workout ->
                    if (layout == ContentLayout.CARD) {
                        WorkoutItemView(
                            workout = workout,
                            onClick = { onNavigateToWorkoutDetail(workout.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(workout) }
                        )
                    } else {
                        WorkoutRowView(
                            workout = workout,
                            onClick = { onNavigateToWorkoutDetail(workout.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(workout) }
                        )
                    }
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

/** Card layout for a workout row (iOS `WorkoutItemView`). */
@Composable
fun WorkoutItemView(
    workout: WorkoutList,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
    ) {
        FLImageView(
            ref = workout.thumbRef(),
            height = 140.dp,
            modifier = Modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workout.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (workout.time.isNotEmpty()) {
                    Text(
                        text = workout.time,
                        fontSize = 13.sp,
                        color = TextSecondaryColor
                    )
                }
                if (workout.isSharedWithMe) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SharedBadge()
                }
            }
            FavoriteHeartButton(isFavorite = workout.isFavorite, onToggle = onToggleFavorite)
        }
    }
}
