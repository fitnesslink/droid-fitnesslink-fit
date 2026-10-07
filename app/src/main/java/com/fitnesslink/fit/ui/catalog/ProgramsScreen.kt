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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.ProgramList
import com.fitnesslink.fit.ui.components.AnatomyChipStrip
import com.fitnesslink.fit.ui.components.ClearFiltersButton
import com.fitnesslink.fit.ui.components.ContentLayout
import com.fitnesslink.fit.ui.components.ContentLayoutToggle
import com.fitnesslink.fit.ui.components.ContributorFilterMenu
import com.fitnesslink.fit.ui.components.EquipmentChipStrip
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.FavoritesFilterControl
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SearchView
import com.fitnesslink.fit.ui.components.SharedBadge
import com.fitnesslink.fit.ui.components.rememberContentLayout
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.ui.workout.FavoriteHeartButton
import com.fitnesslink.fit.viewmodel.ProgramsViewModel

/**
 * Programs "See All" (iOS `ProgramsView`): template programs in server
 * ranking order with search, favorites toggle, contributor menu,
 * equipment / anatomy chip strips from the list-view payload,
 * clear-filters, card / list layout toggle and a heart on every row.
 * Android keeps its Level / Length chip rows as extras.
 */
@Composable
fun ProgramsScreen(
    onBack: () -> Unit,
    onNavigateToProgramDetail: (String) -> Unit,
    onNavigateToProgramEditor: () -> Unit = {}
) {
    val viewModel: ProgramsViewModel = viewModel()
    var layout by rememberContentLayout()
    val contributors by ContributorStore.contributors.collectAsState()
    val anatomy by ClassificationStore.anatomy.collectAsState()
    val equipment by ClassificationStore.equipment.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
        runCatching { ClassificationStore.refresh() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.reloadFromCache() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val presentContributors = remember(contributors, viewModel.programs) { ContributorStore.present(viewModel.presentContributorIds) }
    val equipmentOptions = remember(equipment, viewModel.equipmentByProgramId) {
        val present = viewModel.equipmentByProgramId.values.flatten().toSet()
        ClassificationStore.primaryEquipment.filter { present.contains(it.id.lowercase()) }
    }
    val anatomyOptions = remember(anatomy, viewModel.anatomyByProgramId) {
        val present = viewModel.anatomyByProgramId.values.flatten().toSet()
        ClassificationStore.primaryAnatomy.filter { present.contains(it.id.lowercase()) }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchView(
                text = viewModel.searchText,
                onTextChange = { viewModel.searchText = it },
                modifier = Modifier.weight(1f),
                placeholder = "Search programs",
                showFilterIcon = false
            )
            Spacer(modifier = Modifier.width(8.dp))
            ContentLayoutToggle(layout = layout, onChange = { layout = it })
            Spacer(modifier = Modifier.width(4.dp))
            FloatingActionButton(onClick = onNavigateToProgramEditor, containerColor = FLPrimary, modifier = Modifier.size(42.dp)) {
                Text("+", color = White, fontSize = 20.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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

        // Android extras — iOS has no level / length filter on Programs.
        FilterChipRow(
            label = "Level",
            options = listOf(null, "Beginner", "Intermediate", "Advanced"),
            selected = viewModel.levelFilter,
            onSelect = { viewModel.levelFilter = it },
            displayName = { it ?: "All" }
        )
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

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
            ClearFiltersButton(visible = viewModel.hasActiveFilters, onClick = { viewModel.clearFilters() })
        }

        val visible = viewModel.visiblePrograms
        if (visible.isEmpty()) {
            val filtered = viewModel.hasActiveFilters || viewModel.searchText.isNotBlank()
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (filtered) "No programs match these filters" else "No programs yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (filtered) "Clear the filters to see everything." else "Programs appear here once the catalog loads.",
                    fontSize = 13.sp, color = TextSecondaryColor
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
                items(visible, key = { it.id }) { program ->
                    if (layout == ContentLayout.CARD) {
                        ProgramItemView(program = program, onClick = { onNavigateToProgramDetail(program.id) }, onToggleFavorite = { viewModel.toggleFavorite(program) })
                    } else {
                        ProgramRowView(program = program, onClick = { onNavigateToProgramDetail(program.id) }, onToggleFavorite = { viewModel.toggleFavorite(program) })
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondaryColor, modifier = Modifier.padding(end = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
            items(options) { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) FLPrimary else White)
                        .border(1.dp, if (isSelected) FLPrimary else TextSecondaryColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
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

/** Thumbnail ref for a program row: thumbnail first, image as the fallback. */
fun ProgramList.thumbRef(): MediaRef? = (thumbnailId ?: imageId)?.let { MediaRef.ProgramThumbnail(it) }

/** Card layout (iOS `ProgramItemView`). */
@Composable
fun ProgramItemView(
    program: ProgramList,
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
            ref = program.thumbRef(),
            height = 140.dp,
            modifier = Modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
        )
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = program.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                val meta = programMeta(program)
                if (meta.isNotEmpty()) Text(text = meta, fontSize = 13.sp, color = TextSecondaryColor)
                if (program.isSharedWithMe) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SharedBadge()
                }
            }
            FavoriteHeartButton(isFavorite = program.isFavorite, onToggle = onToggleFavorite)
        }
    }
}

/** Compact single-row presentation — the "list" layout counterpart (iOS `ProgramRowView`). */
@Composable
fun ProgramRowView(
    program: ProgramList,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FLImageView(ref = program.thumbRef(), modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Text(program.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = programMeta(program)
            if (meta.isNotEmpty()) Text(meta, fontSize = 13.sp, color = TextSecondaryColor, maxLines = 1)
            if (program.isSharedWithMe) {
                Spacer(modifier = Modifier.height(4.dp))
                SharedBadge()
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        FavoriteHeartButton(isFavorite = program.isFavorite, onToggle = onToggleFavorite)
    }
}

private fun programMeta(p: ProgramList): String = buildList {
    p.weeks?.takeIf { it > 0 }?.let { add("$it wk") }
    if (p.trainingLevel.isNotBlank()) add(p.trainingLevel)
    if (p.time.isNotBlank()) add(p.time)
}.joinToString(" · ")
