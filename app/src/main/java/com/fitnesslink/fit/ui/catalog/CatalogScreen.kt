package com.fitnesslink.fit.ui.catalog

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.catalog.CatalogStore
import com.fitnesslink.fit.model.CatalogItem
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.HeaderView
import com.fitnesslink.fit.ui.components.SharedBadge
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.navigation.CatalogRoutes
import com.fitnesslink.fit.ui.navigation.MovementRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.CatalogViewModel

/**
 * Catalog hub (port of iOS `CatalogView`): My Content rail (always shown,
 * with the create menu), then template Programs / Workouts / Movements
 * rails that hide when empty. First load with an empty cache shows a
 * "Loading your library…" spinner instead of blank rails.
 *
 * The original navigation callbacks are kept for `AppNavigation.kt`; new
 * destinations (movements, movement detail / editor, My Content) go through
 * [onNavigate], which defaults to `AppRouter.push`.
 */
@Composable
fun CatalogScreen(
    onNavigateToPrograms: () -> Unit = {},
    onNavigateToWorkouts: () -> Unit = {},
    onNavigateToProgramDetail: (String) -> Unit = {},
    onNavigateToWorkoutDetail: (String) -> Unit = {},
    onNavigateToWorkoutEditor: () -> Unit = {},
    onNavigateToProgramEditor: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigate: (String) -> Unit = { AppRouter.push(it) }
) {
    val viewModel: CatalogViewModel = viewModel()

    // Re-fires on every (re)entry into composition (iOS `.task` + `.onAppear`):
    // a workout deleted on its detail screen leaves the My Content rail on pop.
    LaunchedEffect(Unit) { viewModel.loadData() }

    val openItem: (CatalogItem) -> Unit = { item ->
        when (item.kind) {
            CatalogItem.Kind.WORKOUT -> onNavigateToWorkoutDetail(item.id)
            CatalogItem.Kind.PROGRAM -> onNavigateToProgramDetail(item.id)
            CatalogItem.Kind.MOVEMENT -> onNavigate(MovementRoutes.movementDetail(item.id))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderView(onNotificationsTap = onNavigateToNotifications)

        if (viewModel.isLoading && viewModel.isEmpty) {
            CatalogLoadingState()
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 24.dp)
            ) {
                MyContentRail(
                    items = viewModel.myContent,
                    onItemClick = openItem,
                    onCreateWorkout = onNavigateToWorkoutEditor,
                    onCreateProgram = onNavigateToProgramEditor,
                    onCreateMovement = { onNavigate(MovementRoutes.MOVEMENT_EDITOR) },
                    onQuickStart = { onNavigate("quickStart") },
                    onSeeAll = { onNavigate(CatalogRoutes.MY_CONTENT) }
                )
                if (viewModel.programs.isNotEmpty()) {
                    CatalogSection(title = "Programs", items = viewModel.programs, showSeeAll = true, onSeeAll = onNavigateToPrograms, onItemClick = openItem)
                }
                if (viewModel.workouts.isNotEmpty()) {
                    CatalogSection(title = "Workouts", items = viewModel.workouts, showSeeAll = true, onSeeAll = onNavigateToWorkouts, onItemClick = openItem)
                }
                if (viewModel.movements.isNotEmpty()) {
                    CatalogSection(
                        title = "Movements", items = viewModel.movements, showSeeAll = true,
                        onSeeAll = { onNavigate(MovementRoutes.MOVEMENTS) }, onItemClick = openItem
                    )
                }
            }
        }
    }
}

/** First-time load: no cache, refresh in flight (iOS "Loading your library…"). */
@Composable
private fun CatalogLoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        CircularProgressIndicator(color = FLPrimary, modifier = Modifier.size(44.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Loading your library…", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
    }
}

/**
 * Combined "My Content" rail (iOS `MyContentScrollView`): user-authored
 * workouts, programs and movements newest-first, each card carrying a kind
 * badge and its created date. Always shown — the + menu is the only
 * authoring entry point, so new accounts must be able to reach it.
 */
@Composable
private fun MyContentRail(
    items: List<CatalogItem>,
    onItemClick: (CatalogItem) -> Unit,
    onCreateWorkout: () -> Unit,
    onCreateProgram: () -> Unit,
    onCreateMovement: () -> Unit,
    onQuickStart: () -> Unit,
    onSeeAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(text = "My Content", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            CreateContentMenu(
                iconSize = 26.dp,
                onCreateWorkout = onCreateWorkout,
                onCreateProgram = onCreateProgram,
                onCreateMovement = onCreateMovement,
                onQuickStart = onQuickStart
            )
            if (items.isNotEmpty()) {
                Spacer(modifier = Modifier.width(12.dp))
                SeeAllLink(onClick = onSeeAll)
            }
        }
        if (items.isEmpty()) {
            Text(
                text = "Tap + to create your first workout, program, or movement.",
                fontSize = 14.sp,
                color = TextSecondaryColor,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { "${it.kind}-${it.id}" }) { item ->
                    CatalogItemView(item = item, showKindBadge = true, showCreatedDate = true, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

/** "+" menu offering Workout / Program / Movement (iOS `Menu` with `plus.circle.fill`). */
@Composable
fun CreateContentMenu(
    iconSize: androidx.compose.ui.unit.Dp,
    onCreateWorkout: () -> Unit,
    onCreateProgram: () -> Unit,
    onCreateMovement: () -> Unit,
    /** Quick Start PRD §5: the same flow as the Home Quick Tap. Null hides the item. */
    onQuickStart: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Icon(
            imageVector = Icons.Filled.AddCircle,
            contentDescription = "Create content",
            tint = FLPrimary,
            modifier = Modifier
                .size(iconSize)
                .clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onQuickStart != null) {
                DropdownMenuItem(
                    text = { Text("Quick Start") },
                    leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
                    onClick = { expanded = false; onQuickStart() }
                )
            }
            DropdownMenuItem(
                text = { Text("Workout") },
                leadingIcon = { Icon(Icons.Filled.FitnessCenter, contentDescription = null) },
                onClick = { expanded = false; onCreateWorkout() }
            )
            DropdownMenuItem(
                text = { Text("Program") },
                leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                onClick = { expanded = false; onCreateProgram() }
            )
            DropdownMenuItem(
                text = { Text("Movement") },
                leadingIcon = { Icon(Icons.Filled.DirectionsRun, contentDescription = null) },
                onClick = { expanded = false; onCreateMovement() }
            )
        }
    }
}

@Composable
private fun SeeAllLink(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(text = "See All", fontSize = 14.sp, color = FLPrimary)
        Image(
            painter = painterResource(R.drawable.rightarrow),
            contentDescription = null,
            modifier = Modifier.size(width = 6.dp, height = 10.dp)
        )
    }
}

/** A titled horizontal rail of catalog cards (iOS `ProgramScrollView` / `WorkoutScrollView` / `MovementScrollView`). */
@Composable
fun CatalogSection(
    title: String,
    items: List<CatalogItem>,
    showSeeAll: Boolean = false,
    onSeeAll: () -> Unit = {},
    onItemClick: (CatalogItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            if (showSeeAll) SeeAllLink(onClick = onSeeAll)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { "${it.kind}-${it.id}" }) { item ->
                CatalogItemView(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

/** Kind badge colour (iOS `badgeColor`). */
fun catalogKindBadgeColor(kind: CatalogItem.Kind): Color = when (kind) {
    CatalogItem.Kind.PROGRAM -> BlueTheme
    CatalogItem.Kind.WORKOUT, CatalogItem.Kind.MOVEMENT -> FLPrimary
}

/**
 * Rail card (iOS `CatalogItemView`): 160-wide, fixed height sized for the
 * tallest variant so cards in a rail never clip. The thumbnail is keyed by
 * the media ASSET id (`thumbnailId ?: imageId`) via `CatalogStore.mediaRef`.
 */
@Composable
fun CatalogItemView(
    item: CatalogItem,
    showKindBadge: Boolean = false,
    showCreatedDate: Boolean = false,
    onClick: () -> Unit
) {
    val cardHeight = if (showCreatedDate) 228.dp else 210.dp
    Column(
        modifier = Modifier
            .width(160.dp)
            .height(cardHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
    ) {
        Box {
            FLImageView(
                ref = CatalogStore.mediaRef(item),
                modifier = Modifier
                    .width(160.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            )
            // Movements carry no badge on iOS rails — the section header says it.
            if (showKindBadge && item.kind != CatalogItem.Kind.MOVEMENT) {
                Text(
                    text = item.kindLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = White,
                    modifier = Modifier
                        .padding(8.dp)
                        .background(catalogKindBadgeColor(item.kind), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.caption,
                fontSize = 12.sp,
                color = TextSecondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showCreatedDate) {
                item.formattedCreatedAt?.let { created ->
                    Text(text = "Created $created", fontSize = 11.sp, color = TextSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (item.isSharedWithMe) SharedBadge()
        }
    }
}
