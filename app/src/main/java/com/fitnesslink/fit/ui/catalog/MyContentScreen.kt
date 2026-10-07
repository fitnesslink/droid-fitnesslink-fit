package com.fitnesslink.fit.ui.catalog

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.catalog.CatalogStore
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.CatalogItem
import com.fitnesslink.fit.ui.components.AnatomyChipStrip
import com.fitnesslink.fit.ui.components.ClearFiltersButton
import com.fitnesslink.fit.ui.components.ContentLayout
import com.fitnesslink.fit.ui.components.ContentLayoutToggle
import com.fitnesslink.fit.ui.components.EquipmentChipStrip
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.FavoritesFilterControl
import com.fitnesslink.fit.ui.components.FilterChip
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SearchView
import com.fitnesslink.fit.ui.components.SharedBadge
import com.fitnesslink.fit.ui.components.rememberContentLayout
import com.fitnesslink.fit.ui.navigation.MovementRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.MyContentViewModel

/**
 * "See All" page for the catalog's My Content rail (iOS `MyContentView`):
 * one searchable list of the user's workouts, programs and movements with
 * kind chips, All/Favorites, equipment + anatomy strips, Clear filters, the
 * card/list layout toggle and the create (+) menu. Rows show "Created
 * <date>" and route by kind.
 */
@Composable
fun MyContentScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val viewModel: MyContentViewModel = viewModel()
    var searchText by rememberSaveable { mutableStateOf("") }
    var layout by rememberContentLayout()
    val anatomy by ClassificationStore.anatomy.collectAsState()
    val equipment by ClassificationStore.equipment.collectAsState()

    // Reload on every appear so content created in the editors shows up on pop-back.
    LaunchedEffect(Unit) { viewModel.loadData() }

    val equipmentOptions = remember(equipment, viewModel.items) {
        val present = viewModel.items.flatMap { it.equipmentIds }.toSet()
        ClassificationStore.primaryEquipment.filter { it.id.lowercase() in present }
    }
    val anatomyOptions = remember(anatomy, viewModel.items) {
        val present = viewModel.items.flatMap { it.anatomyIds }.toSet()
        ClassificationStore.primaryAnatomy.filter { it.id.lowercase() in present }
    }

    val openItem: (CatalogItem) -> Unit = { item ->
        when (item.kind) {
            CatalogItem.Kind.WORKOUT -> onNavigate("workoutDetail/${item.id}")
            CatalogItem.Kind.PROGRAM -> onNavigate("programDetail/${item.id}")
            CatalogItem.Kind.MOVEMENT -> onNavigate(MovementRoutes.movementDetail(item.id))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            HeaderBackView(onBack = onBack)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            ) {
                ContentLayoutToggle(layout = layout, onChange = { layout = it })
                CreateContentMenu(
                    iconSize = 22.dp,
                    onCreateWorkout = { onNavigate("workoutEditor") },
                    onCreateProgram = { onNavigate("programEditor") },
                    onCreateMovement = { onNavigate(MovementRoutes.MOVEMENT_EDITOR) }
                )
            }
        }
        Text(
            text = "My Content",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        SearchView(
            text = searchText,
            onTextChange = { searchText = it },
            placeholder = "Search my content",
            showFilterIcon = false,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )

        // Content-type chips: multi-select OR; none selected = everything.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
            KindChip("Workouts", CatalogItem.Kind.WORKOUT, viewModel)
            KindChip("Programs", CatalogItem.Kind.PROGRAM, viewModel)
            KindChip("Movements", CatalogItem.Kind.MOVEMENT, viewModel)
        }

        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            FavoritesFilterControl(favoritesOnly = viewModel.showFavoritesOnly, onChange = { viewModel.showFavoritesOnly = it })
        }

        if (viewModel.anyItemHasEquipment) {
            EquipmentChipStrip(options = equipmentOptions, selected = viewModel.selectedEquipmentIds, onToggle = viewModel::toggleEquipment)
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (viewModel.anyItemHasAnatomy) {
            AnatomyChipStrip(options = anatomyOptions, selected = viewModel.selectedAnatomyIds, onToggle = viewModel::toggleAnatomy)
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (viewModel.hasActiveFilters) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                ClearFiltersButton(visible = true, onClick = viewModel::clearFilters)
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        val shown = viewModel.filtered(searchText)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shown, key = { "${it.kind}-${it.id}" }) { item ->
                if (layout == ContentLayout.CARD) MyContentRow(item = item, onClick = { openItem(item) })
                else MyContentRowCompact(item = item, onClick = { openItem(item) })
            }
        }
    }
}

@Composable
private fun KindChip(label: String, kind: CatalogItem.Kind, viewModel: MyContentViewModel) {
    FilterChip(label = label, isSelected = kind in viewModel.selectedKinds, onClick = { viewModel.toggleKind(kind) })
}

/** Card row (iOS `MyContentRow`): full-width image with the kind badge, title, caption, created date, Shared badge. */
@Composable
private fun MyContentRow(item: CatalogItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
    ) {
        Box {
            FLImageView(
                ref = CatalogStore.mediaRef(item),
                height = 140.dp,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            )
            Text(
                text = item.kindLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = White,
                modifier = Modifier
                    .padding(10.dp)
                    .background(catalogKindBadgeColor(item.kind), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(12.dp)) {
            Text(text = item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.caption.isNotEmpty()) {
                Text(text = item.caption, fontSize = 13.sp, color = TextSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            item.formattedCreatedAt?.let { Text(text = "Created $it", fontSize = 12.sp, color = TextSecondaryColor, maxLines = 1) }
            if (item.isSharedWithMe) SharedBadge()
        }
    }
}

/** Compact row (iOS `MyContentRowCompact`): 64dp thumbnail + badge, title, caption, created date. */
@Composable
private fun MyContentRowCompact(item: CatalogItem, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        FLImageView(
            ref = CatalogStore.mediaRef(item),
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = item.kindLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = White,
                modifier = Modifier
                    .background(catalogKindBadgeColor(item.kind), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Text(text = item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.caption.isNotEmpty()) {
                Text(text = item.caption, fontSize = 13.sp, color = TextSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            item.formattedCreatedAt?.let { Text(text = "Created $it", fontSize = 12.sp, color = TextSecondaryColor, maxLines = 1) }
            if (item.isSharedWithMe) SharedBadge()
        }
    }
}
