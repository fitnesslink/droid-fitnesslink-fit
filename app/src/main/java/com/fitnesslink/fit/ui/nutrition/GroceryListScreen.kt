package com.fitnesslink.fit.ui.nutrition

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.GroceryCategory
import com.fitnesslink.fit.model.GroceryItem
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.FoodSearchResult
import com.fitnesslink.fit.network.dto.GroceryAnalyzeResponse
import com.fitnesslink.fit.network.dto.GroceryDuplicateMatch
import com.fitnesslink.fit.network.dto.GroceryStaple
import com.fitnesslink.fit.nutrition.GroceryAnalyzeRules
import com.fitnesslink.fit.nutrition.GroceryStore
import com.fitnesslink.fit.ui.navigation.MealPlanRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.ProgressBG
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.GroceryListDetailViewModel
import com.fitnesslink.fit.viewmodel.GroceryListsViewModel
import com.fitnesslink.fit.viewmodel.MealPlanViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One grocery list (port of iOS `GroceryListDetailView`). With [listId] =
 * null this is the pre-Wave-3 `groceryList` route: it shows the default
 * list, or redirects once to the lists overview when several active lists
 * exist. Items are grouped by category with checked items in "In Cart";
 * adds go through analyze (category suggestion + duplicate → merge /
 * "Add Separately"), rows tap-to-edit and swipe-to-delete, a Reorder mode
 * moves items within their category (`PUT /reorder`), and the staples
 * section offers "You Usually Buy". Android extras kept: text Share and
 * Regenerate (now the range → new list generator).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryListScreen(onBack: () -> Unit, listId: String? = null, onNavigate: ((String) -> Unit)? = null) {
    val viewModel: GroceryListDetailViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var resolvedId by rememberSaveable { mutableStateOf(listId) }
    var redirected by rememberSaveable { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(listId == null) }

    // Resolve the default list for the legacy route.
    LaunchedEffect(listId) {
        if (listId != null) { resolvedId = listId; resolving = false; return@LaunchedEffect }
        if (resolvedId != null) { resolving = false; return@LaunchedEffect }
        val lists = GroceryStore.cachedLists().ifEmpty { runCatching { GroceryStore.refreshLists() }.getOrDefault(emptyList()) }
        val active = lists.filter { !it.isArchived }
        if (active.size > 1 && onNavigate != null && !redirected) {
            redirected = true
            onNavigate(MealPlanRoutes.GROCERY_LISTS)
        }
        resolvedId = lists.firstOrNull { it.isDefault }?.id ?: active.firstOrNull()?.id
        resolving = false
    }

    val id = resolvedId
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        viewModel.bind(id)
        launch { viewModel.load() }
        launch { viewModel.loadStaples() }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showManualAdd by remember { mutableStateOf(false) }
    var showFoodSearch by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GroceryItem?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }
    var showGenerate by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(false) }

    val total = viewModel.items.size
    val checked = viewModel.checkedItems.size
    val listName = viewModel.list?.name ?: "Grocery List"

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        PlanTopBar(title = listName, onBack = onBack) {
            if (viewModel.uncheckedItems.isNotEmpty() && !viewModel.isShowingOfflineCache) {
                IconButton(onClick = { reorderMode = !reorderMode }) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "Reorder", tint = if (reorderMode) FLPrimary else TextPrimaryColor)
                }
            }
            Box {
                IconButton(onClick = { showAddMenu = true }, enabled = !viewModel.isShowingOfflineCache) { Icon(Icons.Filled.AddCircle, contentDescription = "Add", tint = FLPrimary) }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(text = { Text("Add Manually") }, onClick = { showAddMenu = false; showManualAdd = true })
                    DropdownMenuItem(text = { Text("Search Food Database") }, onClick = { showAddMenu = false; showFoodSearch = true })
                    DropdownMenuItem(text = { Text("Build Meal from Items") }, enabled = viewModel.items.isNotEmpty() && id != null && onNavigate != null,
                        onClick = { showAddMenu = false; id?.let { onNavigate?.invoke(MealPlanRoutes.groceryMealBuilder(it)) } })
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextPrimaryColor) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; renameText = listName; showRename = true })
                    val archived = viewModel.list?.isArchived ?: false
                    DropdownMenuItem(text = { Text(if (archived) "Restore" else "Archive") }, onClick = {
                        showMenu = false
                        scope.launch { viewModel.setArchived(!archived); if (!archived) onBack() }
                    })
                    DropdownMenuItem(text = { Text("Share") }, enabled = total > 0, onClick = {
                        showMenu = false
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Grocery list from FitnessLink")
                            putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent, "Share grocery list")) }
                    })
                    DropdownMenuItem(text = { Text("Regenerate from plan") }, onClick = { showMenu = false; showGenerate = true })
                    if (onNavigate != null) DropdownMenuItem(text = { Text("All lists") }, onClick = { showMenu = false; onNavigate(MealPlanRoutes.GROCERY_LISTS) })
                    if (viewModel.list?.isDefault == false) DropdownMenuItem(text = { Text("Delete List", color = OrangeTheme) }, onClick = { showMenu = false; showDelete = true })
                }
            }
        }

        if (total > 0) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$checked of $total items", fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.weight(1f))
                    Text("${(checked * 100.0 / total.coerceAtLeast(1)).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                }
                val fraction by animateFloatAsState(checked.toFloat() / total.coerceAtLeast(1), label = "groceryProgress")
                Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)).background(ProgressBG)) {
                    Box(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(FLPrimary, RoundedCornerShape(4.dp)))
                }
            }
        }

        when {
            resolving || (viewModel.isLoading && viewModel.items.isEmpty()) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FLPrimary) }
            id == null -> ErrorState("Couldn't find a grocery list. Pull to refresh or create one.") { scope.launch { GroceryStore.refreshLists() } }
            viewModel.errorMessage != null && viewModel.items.isEmpty() -> ErrorState(viewModel.errorMessage ?: "") { scope.launch { viewModel.load() } }
            viewModel.items.isEmpty() && viewModel.visibleStaples.isEmpty() -> EmptyListState(onAdd = { showManualAdd = true }, onSearch = { showFoodSearch = true })
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (viewModel.isShowingOfflineCache) item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(16.dp))
                        Text("Offline — showing your last synced list.", fontSize = 13.sp, color = TextSecondaryColor)
                    }
                }
                viewModel.errorMessage?.let { msg -> item { PlanInlineError(msg) } }

                if (viewModel.visibleStaples.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(12.dp))
                            PlanSectionLabel("You Usually Buy")
                        }
                    }
                    item {
                        Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                            viewModel.visibleStaples.forEachIndexed { index, staple ->
                                StapleRow(staple, onDismiss = { viewModel.dismissStaple(staple) }, onAdd = { scope.launch { viewModel.addStaple(staple) } })
                                if (index < viewModel.visibleStaples.lastIndex) PlanDivider()
                            }
                        }
                    }
                    item { PlanFootnote("Based on your past lists. Tap + to add one, × to hide it.") }
                }

                viewModel.groupedUnchecked.forEach { (category, rows) ->
                    item(key = "header-${category.name}") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(12.dp))
                            PlanSectionLabel(category.displayName)
                        }
                    }
                    items(rows, key = { "item-${it.id}" }) { item ->
                        val index = rows.indexOf(item)
                        GroceryItemRow(
                            item = item,
                            enabled = !viewModel.isShowingOfflineCache,
                            reorder = if (reorderMode) ReorderControls(
                                canUp = index > 0, canDown = index < rows.lastIndex,
                                onUp = { scope.launch { viewModel.moveUnchecked(category, index, index - 1) } },
                                onDown = { scope.launch { viewModel.moveUnchecked(category, index, index + 1) } }
                            ) else null,
                            onToggle = { scope.launch { viewModel.toggle(item) } },
                            onEdit = { editing = item },
                            onDelete = { scope.launch { viewModel.delete(item) } }
                        )
                    }
                }

                if (viewModel.checkedItems.isNotEmpty()) {
                    item(key = "header-cart") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(12.dp))
                            PlanSectionLabel("In Cart ($checked)")
                        }
                    }
                    items(viewModel.checkedItems, key = { "item-${it.id}" }) { item ->
                        GroceryItemRow(
                            item = item, enabled = !viewModel.isShowingOfflineCache, reorder = null,
                            onToggle = { scope.launch { viewModel.toggle(item) } },
                            onEdit = { editing = item },
                            onDelete = { scope.launch { viewModel.delete(item) } }
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showManualAdd && id != null) {
        GroceryItemFormSheet(
            title = "Add Grocery Item", item = GroceryItem(), isNew = true, analyzeListId = id,
            onMerge = { dup, qty, unit -> scope.launch { viewModel.merge(dup, qty, unit) } },
            onSave = { scope.launch { viewModel.addItem(it) } },
            onDismiss = { showManualAdd = false }
        )
    }
    editing?.let { item ->
        GroceryItemFormSheet(title = "Edit Item", item = item, isNew = false, analyzeListId = null, onMerge = null,
            onSave = { scope.launch { viewModel.update(it) } }, onDismiss = { editing = null })
    }
    if (showFoodSearch) {
        PlanFoodSearchSheet(
            title = "Search Foods", initialQuery = "", stayOpenAfterPick = true,
            onPick = { result -> scope.launch { viewModel.addFromSearch(result) } },
            onDismiss = { showFoodSearch = false }
        )
    }
    if (showRename) {
        PlanNameDialog("Rename List", null, renameText, { renameText = it }, confirm = "Save", onDismiss = { showRename = false }) {
            showRename = false
            scope.launch { viewModel.renameList(renameText) }
        }
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete this list?") },
            text = { Text("The list and its items are permanently removed.") },
            confirmButton = { TextButton(onClick = { showDelete = false; scope.launch { if (viewModel.deleteList()) onBack() } }) { Text("Delete", color = OrangeTheme) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }
    if (showGenerate) {
        GenerateGroceryListSheet(
            weekStart = todayIso(),
            onGenerated = { list -> showGenerate = false; onNavigate?.invoke(MealPlanRoutes.groceryListDetail(list.id)) },
            onDismiss = { showGenerate = false }
        )
    }
}

private data class ReorderControls(val canUp: Boolean, val canDown: Boolean, val onUp: () -> Unit, val onDown: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroceryItemRow(item: GroceryItem, enabled: Boolean, reorder: ReorderControls?, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart && enabled) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            Box(modifier = Modifier.fillMaxSize().background(Color.Red, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = White)
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (item.isChecked) Icons.Filled.CheckCircle else Icons.Outlined.Circle, contentDescription = if (item.isChecked) "In cart" else "Not in cart",
                tint = if (item.isChecked) FLPrimary else MediumGray, modifier = Modifier.size(24.dp).clickable(enabled = enabled, onClick = onToggle)
            )
            Column(modifier = Modifier.weight(1f).clickable(enabled = enabled, onClick = onEdit)) {
                Text(
                    item.name, fontSize = 15.sp, color = if (item.isChecked) TextSecondaryColor else TextPrimaryColor,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
                )
                val amount = "${item.quantity} ${item.unit}".trim()
                if (amount.isNotEmpty()) Text(amount, fontSize = 12.sp, color = TextSecondaryColor)
            }
            if (reorder != null) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", tint = if (reorder.canUp) FLPrimary else MediumGray,
                    modifier = Modifier.size(24.dp).clickable(enabled = reorder.canUp, onClick = reorder.onUp))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", tint = if (reorder.canDown) FLPrimary else MediumGray,
                    modifier = Modifier.size(24.dp).clickable(enabled = reorder.canDown, onClick = reorder.onDown))
            }
        }
    }
}

@Composable
private fun StapleRow(staple: GroceryStaple, onDismiss: () -> Unit, onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(staple.name, fontSize = 15.sp, color = TextPrimaryColor)
            Text(GroceryAnalyzeRules.stapleSubtitle(staple), fontSize = 12.sp, color = TextSecondaryColor)
        }
        Icon(Icons.Filled.Cancel, contentDescription = "Hide", tint = MediumGray, modifier = Modifier.size(22.dp).clickable(onClick = onDismiss))
        Icon(Icons.Filled.AddCircle, contentDescription = "Add", tint = FLPrimary, modifier = Modifier.size(24.dp).clickable(onClick = onAdd))
    }
}

@Composable
private fun EmptyListState(onAdd: () -> Unit, onSearch: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = MediumGray, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(14.dp))
        Text("Nothing on this list yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        Text("Add items by hand or from the food database.", fontSize = 13.sp, color = TextSecondaryColor, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add Item", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White,
                modifier = Modifier.background(FLPrimary, RoundedCornerShape(10.dp)).clickable(onClick = onAdd).padding(horizontal = 16.dp, vertical = 10.dp))
            Text("Search Foods", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary,
                modifier = Modifier.background(FLPrimary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).clickable(onClick = onSearch).padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

// MARK: - Add / edit sheet with analyze (FTNSSLNK-220)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroceryItemFormSheet(
    title: String,
    item: GroceryItem,
    isNew: Boolean,
    analyzeListId: String?,
    onMerge: ((GroceryDuplicateMatch, Double?, String?) -> Unit)?,
    onSave: (GroceryItem) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity) }
    var unit by remember { mutableStateOf(item.unit) }
    var category by remember { mutableStateOf(item.category) }
    var analysis by remember { mutableStateOf<GroceryAnalyzeResponse?>(null) }
    var analyzedName by remember { mutableStateOf("") }
    var dismissedForName by remember { mutableStateOf<String?>(null) }
    var pendingDuplicate by remember { mutableStateOf<GroceryDuplicateMatch?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    val trimmedName = name.trim()

    suspend fun runAnalyze(current: String): GroceryAnalyzeResponse? =
        runCatching { GroceryStore.analyze(analyzeListId, current, quantity.toDoubleOrNull(), unit) }.getOrNull()

    // Debounced, read-only analyze while typing (new items only).
    LaunchedEffect(name, quantity, unit) {
        if (!isNew) return@LaunchedEffect
        if (trimmedName.length < 2) { analysis = null; analyzedName = ""; return@LaunchedEffect }
        delay(400)
        runAnalyze(trimmedName)?.let { analysis = it; analyzedName = trimmedName }
    }

    fun commit() {
        onSave(item.copy(name = trimmedName, quantity = quantity.trim(), unit = unit.trim(), category = category))
        onDismiss()
    }

    fun attemptSave() {
        if (trimmedName.isEmpty()) return
        if (!isNew) { commit(); return }
        isChecking = true
        scope.launch {
            var current = analysis
            if (current == null || analyzedName != trimmedName) current = runAnalyze(trimmedName)
            isChecking = false
            when (val decision = GroceryAnalyzeRules.decide(isNew, current, onMerge != null)) {
                is GroceryAnalyzeRules.AddDecision.PromptMerge -> pendingDuplicate = decision.duplicate
                GroceryAnalyzeRules.AddDecision.AddSeparately -> commit()
            }
        }
    }

    val suggestion = GroceryAnalyzeRules.visibleSuggestion(isNew, analysis, analyzedName, trimmedName, dismissedForName, category)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BackgroundColor) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlanSheetTitle(title, onCancel = onDismiss, confirm = "Save", confirmEnabled = trimmedName.isNotEmpty(), isWorking = isChecking, onConfirm = ::attemptSave)
            PlanSectionLabel("Item Details")
            PlanCard {
                PlanTextField(name, { name = it }, "Item Name")
                PlanTextField(quantity, { quantity = it }, "Quantity (optional)", keyboardType = KeyboardType.Decimal)
                PlanTextField(unit, { unit = it }, "Unit (optional)")
            }
            PlanSectionLabel("Category")
            PlanCard {
                Box {
                    PlanValueRow(category.displayName, "Change") { showCategories = true }
                    DropdownMenu(expanded = showCategories, onDismissRequest = { showCategories = false }) {
                        GroceryCategory.entries.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat.displayName) }, onClick = { category = cat; showCategories = false })
                        }
                    }
                }
                if (suggestion != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.background(FLPrimary.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).clickable { category = suggestion }.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(12.dp))
                            Text("Suggested: ${suggestion.displayName}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                        }
                        Icon(Icons.Filled.Cancel, contentDescription = "Dismiss suggestion", tint = TextSecondaryColor, modifier = Modifier.size(18.dp).clickable { dismissedForName = trimmedName })
                    }
                }
            }
        }
    }

    pendingDuplicate?.let { duplicate ->
        AlertDialog(
            onDismissRequest = { pendingDuplicate = null },
            title = { Text("“${duplicate.name}” is already on your list") },
            text = {
                var line = "Combining updates the existing item instead of adding a second one."
                if (duplicate.isChecked) line += " It's currently checked off — combining brings it back to the list."
                Text(line)
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        pendingDuplicate = null
                        onMerge?.invoke(duplicate, quantity.toDoubleOrNull(), unit.trim().ifEmpty { null })
                        onDismiss()
                    }) { Text(GroceryAnalyzeRules.combineLabel(duplicate, unit)) }
                    TextButton(onClick = { pendingDuplicate = null; commit() }) { Text("Add Separately") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingDuplicate = null }) { Text("Cancel") } }
        )
    }
}

// MARK: - Food-database search sheet (shared with the grocery meal builder)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanFoodSearchSheet(title: String, initialQuery: String, stayOpenAfterPick: Boolean, onPick: (FoodSearchResult) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<FoodSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var justAdded by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        errorMessage = null
        val trimmed = query.trim()
        if (trimmed.isEmpty()) { results = emptyList(); hasSearched = false; isSearching = false; return@LaunchedEffect }
        delay(300)
        isSearching = true
        try {
            results = ApiClient.nutritionApi.searchFood(trimmed)
            hasSearched = true
        } catch (e: Exception) {
            results = emptyList(); hasSearched = true; errorMessage = GroceryListsViewModel.describe(e)
        } finally {
            isSearching = false
        }
    }
    LaunchedEffect(justAdded) { if (justAdded != null) { delay(1200); justAdded = null } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BackgroundColor) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlanSheetTitle(title, onCancel = onDismiss, confirm = if (stayOpenAfterPick) "Done" else null, onConfirm = onDismiss)
            Row(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(18.dp))
                PlanTextField(query, { query = it }, "e.g. chicken breast, oats, banana", modifier = Modifier.weight(1f))
                if (query.isNotEmpty()) Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TextSecondaryColor, modifier = Modifier.size(18.dp).clickable { query = "" })
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isSearching -> CircularProgressIndicator(color = FLPrimary, modifier = Modifier.align(Alignment.Center))
                    errorMessage != null -> Text(errorMessage ?: "", fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.align(Alignment.Center))
                    results.isEmpty() -> Text(
                        if (hasSearched) "No results for “$query”" else "Type to search the food database",
                        fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.align(Alignment.Center)
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize().background(White, RoundedCornerShape(12.dp))) {
                        items(results, key = { it.id }) { result ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onPick(result); if (stayOpenAfterPick) justAdded = result.name else onDismiss() }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(result.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                                    result.brand?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = TextSecondaryColor) }
                                }
                                Icon(Icons.Filled.AddCircle, contentDescription = "Add", tint = FLPrimary, modifier = Modifier.size(20.dp))
                            }
                            PlanDivider()
                        }
                    }
                }
                justAdded?.let {
                    Text(
                        "Added $it", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = White,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).background(FLPrimary, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}

@Suppress("unused")
private fun weekLabelForToday(): String = MealPlanViewModel.weekRangeLabel(todayIso())
