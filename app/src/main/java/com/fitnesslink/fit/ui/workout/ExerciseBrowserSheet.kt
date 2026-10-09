package com.fitnesslink.fit.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.BrowserTab
import com.fitnesslink.fit.model.ExerciseGroupType
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.movements.MovementStore
import com.fitnesslink.fit.session.QuickStartSuggestions
import com.fitnesslink.fit.workouts.WorkoutRowOps
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.SearchView
import com.fitnesslink.fit.ui.movements.MoreFiltersSelection
import com.fitnesslink.fit.ui.movements.MoreFiltersSheet
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.ImagePlaceholder
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.ExerciseBrowserViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How the browser hands picks back (CONTRACTS.md, Movements block).
 * [Multi] is the editor's "Add Exercise" flow — the sheet stays open and
 * stacks an "N added" badge. [Single] is a chained picker ("Replace Exercise",
 * "Recovery Exercise"): the title is overridden, Create New is hidden, and the
 * first pick is final (the presenter dismisses from `onSelect`).
 */
sealed class BrowserMode {
    data object Multi : BrowserMode()
    data class Single(val title: String) : BrowserMode()
}

/**
 * Server-driven exercise browser (iOS `ExerciseBrowserSheet`). Existing
 * parameters are unchanged for the workout editor; [mode] / [onDone] /
 * [allowCreateNew] are the optional additions from the contract.
 *
 * - search + chip changes → debounced `GET /movements/list-view` with the full
 *   param set; SQLite is the instant / offline source;
 * - chips are the server taxonomy's primary anatomy + equipment;
 * - tabs All / Mine / Favorites / Recent; favorites + recents via the movement
 *   endpoints (optimistic, cached locally; a recent is recorded on add);
 * - "More filters" drawer, preview sheet, Create New Exercise, "added" toast.
 */
@Composable
fun ExerciseBrowserSheet(
    onSelect: (MovementLibraryItem) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
    pendingGroupType: ExerciseGroupType?,
    pendingCount: Int,
    mode: BrowserMode = BrowserMode.Multi,
    onDone: (() -> Unit)? = null,
    allowCreateNew: Boolean = mode is BrowserMode.Multi,
    /**
     * Quick Start PRD §18–19: rows toggle a selection (○ → ✓) instead of
     * adding at once; a bottom "Add N Exercises" button hands the picks
     * back in pick order. Favoriting or opening info never selects.
     */
    multiSelect: Boolean = false,
    onAddSelected: ((List<MovementLibraryItem>) -> Unit)? = null,
    /** Caps the selection (a superset takes exactly two). */
    maxSelection: Int? = null,
    /** PRD §17: "Suggested for <phase>" listed above the library, each row directly selectable. */
    suggestionPhaseName: String? = null
) {
    val viewModel: ExerciseBrowserViewModel = viewModel()
    val scope = rememberCoroutineScope()
    var addedIds by remember { mutableStateOf(emptySet<String>()) }
    var selected by remember { mutableStateOf<List<MovementLibraryItem>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<MovementLibraryItem>>(emptyList()) }
    var previewMovement by remember { mutableStateOf<MovementLibraryItem?>(null) }
    var lastAddedName by remember { mutableStateOf("") }
    var showAddedToast by remember { mutableStateOf(false) }
    var showMoreFilters by remember { mutableStateOf(false) }

    // Collecting keeps the chip strip reactive to the taxonomy landing.
    val anatomy by ClassificationStore.anatomy.collectAsState()
    val equipment by ClassificationStore.equipment.collectAsState()
    val primaryAnatomy = remember(anatomy) { ClassificationStore.primaryAnatomy }
    val primaryEquipment = remember(equipment) { ClassificationStore.primaryEquipment }

    LaunchedEffect(Unit) {
        viewModel.loadData()
        suggestionPhaseName?.let { suggestions = runCatching { QuickStartSuggestions.suggest(it) }.getOrDefault(emptyList()) }
    }

    /** Picks so far in group-building mode: the owner's running count, or this sheet's own selection. */
    val effectivePendingCount = if (multiSelect) selected.size else pendingCount
    /** Suggestions show on the unfiltered All tab only — a search or a filter means the user is looking for something specific. */
    val showsSuggestions = suggestionPhaseName != null && suggestions.isNotEmpty() && viewModel.searchText.isEmpty() &&
        viewModel.selectedTab == BrowserTab.All && viewModel.selectedAnatomyIds.isEmpty() && viewModel.selectedEquipmentIds.isEmpty() && viewModel.nonPrimaryFilterCount == 0

    /** ○ → ✓ / ✓ → ○ (PRD §18). At the cap, a new pick replaces the oldest. */
    fun toggleSelection(movement: MovementLibraryItem) {
        if (selected.any { it.id == movement.id }) { selected = selected.filter { it.id != movement.id }; return }
        var next = selected
        if (maxSelection != null && maxSelection > 0 && next.size >= maxSelection) next = next.drop(next.size - maxSelection + 1)
        selected = next + movement
    }

    fun addMovement(movement: MovementLibraryItem) {
        if (multiSelect) { toggleSelection(movement); return }
        onSelect(movement)
        viewModel.recordRecent(movement.id)
        addedIds = addedIds + movement.id
        lastAddedName = movement.name
        showAddedToast = true
        scope.launch { delay(2000); showAddedToast = false }
    }

    previewMovement?.let { mv ->
        MovementPreviewSheet(
            movement = mv,
            onAdd = { previewMovement = null; addMovement(mv) },
            onDismiss = { previewMovement = null },
            addLabel = if (mode is BrowserMode.Single) "Select" else "Add to Workout"
        )
    }

    if (showMoreFilters) {
        val f = viewModel.filters
        MoreFiltersSheet(
            initial = MoreFiltersSelection(
                anatomyIds = f.anatomyIds, equipmentIds = f.equipmentIds,
                difficultyIds = f.difficultyIds, goalIds = f.goalIds,
                movementPatternIds = f.movementPatternIds, locationIds = f.locationIds
            ),
            tagOptions = viewModel.tagOptions,
            onApply = { sel ->
                viewModel.applyFilters(sel.anatomyIds, sel.equipmentIds, sel.difficultyIds, sel.goalIds, sel.movementPatternIds, sel.locationIds)
            },
            onDismiss = { showMoreFilters = false }
        )
    }

    val minimumGroupCount = when (pendingGroupType) {
        ExerciseGroupType.Interval -> 1
        ExerciseGroupType.Superset, ExerciseGroupType.Circuit -> 2
        null -> 2
    }
    val title = (mode as? BrowserMode.Single)?.title ?: "Add Exercise"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(White)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Title bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                    if (pendingGroupType != null && onDone != null && !multiSelect) {
                        TextButton(onClick = onDone, enabled = pendingCount >= minimumGroupCount) {
                            Text("Done", fontWeight = FontWeight.SemiBold, color = if (pendingCount >= minimumGroupCount) FLPrimary else MediumGray)
                        }
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimaryColor) }
                }

                // Search
                Box(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BackgroundColor)) {
                    SearchView(
                        text = viewModel.searchText,
                        onTextChange = { viewModel.setSearchText(it) },
                        placeholder = "Search exercises...",
                        showFilterIcon = false
                    )
                }

                // Group-building banner
                pendingGroupType?.let { groupType ->
                    val pendingCount = effectivePendingCount
                    val done = if (multiSelect) "Add" else "Done"
                    val remaining = if (groupType == ExerciseGroupType.Superset) maxOf(0, 2 - pendingCount) else 0
                    val message = when (groupType) {
                        ExerciseGroupType.Superset -> if (remaining > 0) "Pick $remaining more ${if (remaining == 1) "exercise" else "exercises"} for superset" else "Superset ready — tap $done"
                        ExerciseGroupType.Circuit -> if (pendingCount < 2) "Pick at least ${2 - pendingCount} more exercises" else "$pendingCount exercises selected — tap $done or keep adding"
                        ExerciseGroupType.Interval -> if (pendingCount < 1) "Pick one or more exercises" else "$pendingCount selected — tap $done"
                    }
                    val bannerColor = when (groupType) {
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
                        Text("Building ${groupType.label} — $message", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = White)
                    }
                }

                // Taxonomy chips (primary anatomy | primary equipment | More filters)
                LazyRow(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(primaryAnatomy, key = { "a:${it.id}" }) { tag ->
                        TaxonomyChip(
                            label = tag.name,
                            isSelected = viewModel.selectedAnatomyIds.any { it.equals(tag.id, ignoreCase = true) },
                            onClick = { viewModel.toggleAnatomy(tag.id) }
                        )
                    }
                    if (primaryAnatomy.isNotEmpty() && primaryEquipment.isNotEmpty()) {
                        item { VerticalDivider(modifier = Modifier.height(20.dp)) }
                    }
                    items(primaryEquipment, key = { "e:${it.id}" }) { tag ->
                        TaxonomyChip(
                            label = tag.name,
                            isSelected = viewModel.selectedEquipmentIds.any { it.equals(tag.id, ignoreCase = true) },
                            onClick = { viewModel.toggleEquipment(tag.id) }
                        )
                    }
                    item { VerticalDivider(modifier = Modifier.height(20.dp)) }
                    item {
                        MoreFiltersChip(count = viewModel.nonPrimaryFilterCount, onClick = { showMoreFilters = true })
                    }
                }

                // Tabs
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    BrowserTab.entries.forEach { tab ->
                        val isSelected = viewModel.selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) FLPrimary else BackgroundColor)
                                .clickable { viewModel.selectTab(tab) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tab.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isSelected) White else TextPrimaryColor)
                        }
                    }
                }

                if (allowCreateNew) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onCreateNew).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create New Exercise", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                    }
                }

                HorizontalDivider()

                if (viewModel.movements.isEmpty() && !showsSuggestions) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MediumGray, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(if (viewModel.isRefreshing) "Searching…" else "No exercises found", fontSize = 15.sp, color = TextSecondaryColor)
                            if (viewModel.filters.hasAnyFilter) {
                                TextButton(onClick = { viewModel.clearFilters() }) { Text("Clear filters", color = FLPrimary) }
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 72.dp)) {
                        if (showsSuggestions) {
                            item(key = "suggested-header") {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Suggested for $suggestionPhaseName", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FLPrimary, modifier = Modifier.weight(1f))
                                    Text("From your workouts", fontSize = 11.sp, color = FLPrimary)
                                }
                            }
                            items(suggestions, key = { "s:${it.id}" }) { movement ->
                                BrowserRow(movement, if (multiSelect) selected.any { it.id == movement.id } else addedIds.contains(movement.id), rowSubtitle(movement), multiSelect,
                                    onTap = { addMovement(movement) }, onPreview = { previewMovement = movement }, onToggleFavorite = { viewModel.toggleFavorite(movement.id) })
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = BackgroundColor)
                            }
                            item(key = "library-header") {
                                Text("Exercise Library", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondaryColor, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }
                        items(viewModel.movements, key = { it.id }) { movement ->
                            val isAdded = if (multiSelect) selected.any { it.id == movement.id } else addedIds.contains(movement.id)
                            BrowserRow(
                                movement = movement,
                                isAdded = isAdded,
                                subtitle = rowSubtitle(movement),
                                selectable = multiSelect,
                                onTap = { addMovement(movement) },
                                onPreview = { previewMovement = movement },
                                onToggleFavorite = { viewModel.toggleFavorite(movement.id) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = BackgroundColor)
                        }
                    }
                }
            }

            // "Add N Exercises" (PRD §19) — the count follows the selection.
            if (multiSelect) {
                val n = selected.size
                val minimum = pendingGroupType?.let { WorkoutRowOps.minimumGroupCount(it) } ?: 1
                val label = when {
                    n == 0 -> "Select exercises"
                    n < minimum -> "Pick ${minimum - n} more"
                    else -> "Add $n ${if (n == 1) "Exercise" else "Exercises"}"
                }
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(White).padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        label, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (n >= minimum) FLPrimary else MediumGray.copy(alpha = 0.5f))
                            .clickable(enabled = n >= minimum) { onAddSelected?.invoke(selected) }.padding(vertical = 15.dp)
                    )
                }
            }
            // Added-count badge (multi mode only)
            if (!multiSelect && mode is BrowserMode.Multi && addedIds.isNotEmpty()) {
                val n = addedIds.size
                val noun = if (n == 1) "exercise" else "exercises"
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(FLPrimary)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (pendingGroupType == ExerciseGroupType.Interval) "$n $noun added to your interval" else "$n $noun added",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White
                    )
                }
            }

            // "<name> added" toast
            AnimatedVisibility(
                visible = showAddedToast,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(FLPrimary.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = White, modifier = Modifier.size(12.dp))
                    Text("$lastAddedName added", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = White)
                }
            }
        }
    }
}

/** "Anatomy · Equipment" caption resolved through the classification cache; empty parts drop out. */
private fun rowSubtitle(movement: MovementLibraryItem): String {
    val anatomyNames = movement.anatomyIds.mapNotNull { ClassificationStore.anatomyName(it) }
    val equipmentNames = movement.equipmentIds.mapNotNull { ClassificationStore.equipmentName(it) }
    return listOf(anatomyNames.joinToString(", "), equipmentNames.joinToString(", "))
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
}

@Composable
private fun BrowserRow(
    movement: MovementLibraryItem,
    isAdded: Boolean,
    subtitle: String,
    selectable: Boolean = false,
    onTap: () -> Unit,
    onPreview: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectable) {
            Icon(
                if (isAdded) Icons.Default.CheckCircle else Icons.Outlined.Circle, contentDescription = if (isAdded) "Selected" else "Not selected",
                tint = if (isAdded) FLPrimary else MediumGray, modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else if (isAdded) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        val thumb = MovementStore.thumbnailRef(movement)
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(ImagePlaceholder)) {
            if (thumb != null) FLImageView(ref = thumb, modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(movement.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (isAdded) FLPrimary else TextPrimaryColor)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 11.sp, color = TextSecondaryColor)
        }
        IconButton(onClick = onPreview, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = "Preview", tint = FLPrimary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                if (movement.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (movement.isFavorite) "Unfavorite" else "Favorite",
                tint = if (movement.isFavorite) Color.Red else MediumGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TaxonomyChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) FLPrimary else BackgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isSelected) White else TextPrimaryColor)
    }
}

/** The "More filters" chip with its drawer-only count badge (shared by browser + catalog). */
@Composable
internal fun MoreFiltersChip(count: Int, onClick: () -> Unit) {
    val active = count > 0
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) FLPrimary.copy(alpha = 0.15f) else BackgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Default.Tune, contentDescription = null, tint = if (active) FLPrimary else TextPrimaryColor, modifier = Modifier.size(12.dp))
        Text("More filters", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (active) FLPrimary else TextPrimaryColor)
        if (active) {
            Box(modifier = Modifier.clip(CircleShape).background(FLPrimary).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
            }
        }
    }
}
