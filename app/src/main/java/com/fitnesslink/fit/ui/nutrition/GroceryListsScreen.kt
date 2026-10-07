package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.GroceryList
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.GroceryListsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Grocery lists overview (FTNSSLNK-208, port of iOS `GroceryListsView`):
 * active lists ("My Lists") with a Default badge, archived lists as
 * "History", create (+), and per-list long-press actions — rename,
 * duplicate ("shop it again", items come back unchecked), archive /
 * restore, delete (never offered on the default list).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroceryListsScreen(onBack: () -> Unit, onOpenList: (GroceryList) -> Unit) {
    val viewModel: GroceryListsViewModel = viewModel()
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<GroceryList?>(null) }
    var renameText by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<GroceryList?>(null) }
    var menuFor by remember { mutableStateOf<GroceryList?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        PlanTopBar(title = "Grocery Lists", onBack = onBack) {
            IconButton(onClick = { newName = ""; showCreate = true }) { Icon(Icons.Filled.AddCircle, contentDescription = "New list", tint = FLPrimary) }
        }
        when {
            viewModel.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FLPrimary) }
            viewModel.errorMessage != null && viewModel.activeLists.isEmpty() && viewModel.archivedLists.isEmpty() ->
                ErrorState(viewModel.errorMessage ?: "") { scope.launch { viewModel.load() } }
            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                viewModel.errorMessage?.let { PlanInlineError(it) }
                PlanSectionLabel("My Lists")
                Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                    if (viewModel.activeLists.isEmpty()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No active lists", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                            Text("Tap + to start a shopping list.", fontSize = 13.sp, color = TextSecondaryColor)
                        }
                    } else {
                        viewModel.activeLists.forEachIndexed { index, list ->
                            ListRow(list, archived = false, onClick = { onOpenList(list) }, onLongClick = { menuFor = list })
                            if (index < viewModel.activeLists.lastIndex) PlanDivider()
                        }
                    }
                }
                if (viewModel.archivedLists.isNotEmpty()) {
                    PlanSectionLabel("History")
                    Column(modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp))) {
                        viewModel.archivedLists.forEachIndexed { index, list ->
                            ListRow(list, archived = true, onClick = { onOpenList(list) }, onLongClick = { menuFor = list })
                            if (index < viewModel.archivedLists.lastIndex) PlanDivider()
                        }
                    }
                    PlanFootnote("Archived lists keep their items. Duplicate one to shop it again — items come back unchecked.")
                }
                PlanFootnote("Long-press a list to rename, duplicate, archive or delete it.")
            }
        }
    }

    menuFor?.let { list ->
        val archived = list.isArchived
        Box {
            DropdownMenu(expanded = true, onDismissRequest = { menuFor = null }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuFor = null; renameText = list.name; renaming = list })
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                    menuFor = null
                    scope.launch { viewModel.duplicate(list)?.let(onOpenList) }
                })
                DropdownMenuItem(text = { Text(if (archived) "Restore" else "Archive") }, onClick = {
                    menuFor = null
                    scope.launch { viewModel.setArchived(list, !archived) }
                })
                if (!list.isDefault) DropdownMenuItem(text = { Text("Delete", color = OrangeTheme) }, onClick = { menuFor = null; pendingDelete = list })
            }
        }
    }

    if (showCreate) {
        PlanNameDialog("New Grocery List", "Name this shopping list.", newName, { newName = it }, confirm = "Create", onDismiss = { showCreate = false }) {
            showCreate = false
            scope.launch { viewModel.createList(newName)?.let(onOpenList) }
        }
    }
    renaming?.let { list ->
        PlanNameDialog("Rename List", null, renameText, { renameText = it }, confirm = "Save", onDismiss = { renaming = null }) {
            renaming = null
            scope.launch { viewModel.rename(list, renameText) }
        }
    }
    pendingDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete “${list.name}”?") },
            text = { Text("The list and its items are permanently removed.") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; scope.launch { viewModel.delete(list) } }) { Text("Delete", color = OrangeTheme) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(list: GroceryList, archived: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(if (archived) Icons.Filled.Archive else Icons.Filled.ShoppingCart, contentDescription = null, tint = if (archived) MediumGray else FLPrimary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(list.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                if (list.isDefault) {
                    Text(
                        "Default", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary,
                        modifier = Modifier.background(FLPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(listSubtitle(list), fontSize = 12.sp, color = TextSecondaryColor)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray)
    }
}

private val listDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

private fun listSubtitle(list: GroceryList): String {
    val count = "${list.itemCount} item${if (list.itemCount == 1) "" else "s"}"
    return "$count · ${listDateFormat.format(list.updatedAt ?: list.createdAt)}"
}

@Composable
internal fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = OrangeTheme, modifier = Modifier.size(24.dp))
        Text(message, fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.padding(vertical = 12.dp))
        TextButton(onClick = onRetry) { Text("Retry", color = FLPrimary, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
internal fun PlanInlineError(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(White, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = OrangeTheme, modifier = Modifier.size(16.dp))
        Text(message, fontSize = 13.sp, color = OrangeTheme)
    }
}

@Composable
internal fun PlanNameDialog(title: String, message: String?, value: String, onValueChange: (String) -> Unit, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message?.let { Text(it, fontSize = 13.sp, color = TextSecondaryColor) }
                PlanTextField(value, onValueChange, "List name")
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = value.trim().isNotEmpty()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
