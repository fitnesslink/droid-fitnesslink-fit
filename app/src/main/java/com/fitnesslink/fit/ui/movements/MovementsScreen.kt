package com.fitnesslink.fit.ui.movements

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.movements.MovementStore
import com.fitnesslink.fit.ui.components.ClearFiltersButton
import com.fitnesslink.fit.ui.components.ContentLayout
import com.fitnesslink.fit.ui.components.ContentLayoutPrefs
import com.fitnesslink.fit.ui.components.ContentLayoutToggle
import com.fitnesslink.fit.ui.components.ContributorFilterMenu
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.FavoritesFilterControl
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SearchView
import com.fitnesslink.fit.ui.components.SharedBadge
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.ImagePlaceholder
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.ui.workout.MoreFiltersChip
import com.fitnesslink.fit.viewmodel.MovementsViewModel

/**
 * Movements catalog (iOS `MovementsView`): search, favorites-only, contributor
 * filter, primary equipment chips + More filters (anatomy / equipment
 * taxonomy), card / list toggle, hearts. Template-only visibility
 * (`contributorId != null`), server ranking, "+" opens the create form.
 */
@Composable
fun MovementsScreen(
    onBack: () -> Unit,
    onNavigateToMovementDetail: (String) -> Unit,
    onNavigateToEditor: () -> Unit
) {
    val viewModel: MovementsViewModel = viewModel()
    var layout by remember { mutableStateOf(runCatching { ContentLayoutPrefs.get() }.getOrDefault(ContentLayout.CARD)) }
    var showMoreFilters by remember { mutableStateOf(false) }

    val equipment by ClassificationStore.equipment.collectAsState()
    val primaryEquipment = remember(equipment) { ClassificationStore.primaryEquipment }
    val contributors by ContributorStore.contributors.collectAsState()
    val availableContributors = remember(contributors, viewModel.movements) {
        ContributorStore.present(viewModel.presentContributorIds)
    }

    LaunchedEffect(Unit) { viewModel.loadData() }

    if (showMoreFilters) {
        MoreFiltersSheet(
            initial = MoreFiltersSelection(anatomyIds = viewModel.selectedAnatomyIds, equipmentIds = viewModel.selectedEquipmentIds),
            onApply = { sel -> viewModel.applyMoreFilters(sel.anatomyIds, sel.equipmentIds) },
            onDismiss = { showMoreFilters = false }
        )
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
                placeholder = "Search movements",
                showFilterIcon = false
            )
            Spacer(modifier = Modifier.width(10.dp))
            ContentLayoutToggle(layout = layout, onChange = { layout = it; runCatching { ContentLayoutPrefs.set(it) } })
            Spacer(modifier = Modifier.width(6.dp))
            FloatingActionButton(onClick = onNavigateToEditor, containerColor = FLPrimary, modifier = Modifier.size(42.dp)) {
                Text("+", color = White, fontSize = 20.sp)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            FavoritesFilterControl(favoritesOnly = viewModel.showFavoritesOnly, onChange = { viewModel.showFavoritesOnly = it })
        }

        if (availableContributors.size > 1) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                ContributorFilterMenu(
                    selectedContributorId = viewModel.selectedContributorId,
                    mineSelected = false,
                    onSelectContributor = { viewModel.selectedContributorId = it },
                    onToggleMine = {}
                )
            }
        }

        // Primary equipment chips + "More filters" (full taxonomy), applied client-side here.
        LazyRow(
            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(primaryEquipment, key = { it.id }) { tag ->
                val selected = viewModel.selectedEquipmentIds.any { it.equals(tag.id, ignoreCase = true) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) FLPrimary else White)
                        .clickable { viewModel.toggleEquipment(tag.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(tag.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (selected) White else TextPrimaryColor)
                }
            }
            if (primaryEquipment.isNotEmpty()) item { VerticalDivider(modifier = Modifier.height(20.dp)) }
            item { MoreFiltersChip(count = viewModel.moreFiltersCount, onClick = { showMoreFilters = true }) }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
            ClearFiltersButton(visible = viewModel.hasActiveFilters, onClick = { viewModel.clearFilters() })
        }

        val items = viewModel.filtered
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (viewModel.isRefreshing && viewModel.movements.isEmpty()) "Loading movements…" else "No movements match these filters",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor
                )
                if (viewModel.hasActiveFilters || viewModel.searchText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Clear the filters to see everything.", fontSize = 13.sp, color = TextSecondaryColor)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { movement ->
                    if (layout == ContentLayout.CARD) {
                        MovementCardView(movement, onClick = { onNavigateToMovementDetail(movement.id) }, onToggleFavorite = { viewModel.toggleFavorite(movement.id) })
                    } else {
                        MovementListRow(movement, onClick = { onNavigateToMovementDetail(movement.id) }, onToggleFavorite = { viewModel.toggleFavorite(movement.id) })
                    }
                }
            }
        }
    }
}

private fun subtitle(movement: MovementLibraryItem): String {
    val anatomy = movement.anatomyIds.mapNotNull { ClassificationStore.anatomyName(it) }.joinToString(", ")
    val equipment = movement.equipmentIds.mapNotNull { ClassificationStore.equipmentName(it) }.joinToString(", ")
    return listOf(anatomy, equipment).filter { it.isNotEmpty() }.joinToString(" • ")
}

@Composable
private fun FavoriteHeart(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier.size(36.dp)) {
        Icon(
            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
            tint = if (isFavorite) Color.Red else MediumGray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MovementListRow(movement: MovementLibraryItem, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val caption = subtitle(movement)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(ImagePlaceholder)) {
            MovementStore.thumbnailRef(movement)?.let { FLImageView(ref = it, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(movement.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption.isNotEmpty()) Text(caption, fontSize = 13.sp, color = TextSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (movement.isSharedWithMe) SharedBadge()
        }
        FavoriteHeart(movement.isFavorite, onToggleFavorite)
    }
}

@Composable
private fun MovementCardView(movement: MovementLibraryItem, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val caption = subtitle(movement)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(ImagePlaceholder)) {
            MovementStore.thumbnailRef(movement)?.let { FLImageView(ref = it, modifier = Modifier.fillMaxSize()) }
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(18.dp)).background(White.copy(alpha = 0.85f))
            ) {
                FavoriteHeart(movement.isFavorite, onToggleFavorite)
            }
        }
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(movement.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption.isNotEmpty()) Text(caption, fontSize = 12.sp, color = TextSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (movement.isSharedWithMe) SharedBadge()
        }
    }
}
