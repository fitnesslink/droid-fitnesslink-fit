package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.ContentTagSelection
import com.fitnesslink.fit.ui.components.TagOption
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.util.Locale

// MARK: - Detail: chip rows (iOS ContentTagsViews.swift)

/** Small pill for one equipment / muscle tag. */
@Composable
fun ContentTagChip(name: String, icon: ImageVector = Icons.Filled.FitnessCenter) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(FLPrimary.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(12.dp))
        Text(text = name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
    }
}

/** Titled chip row ("Equipment Needed"); renders nothing when [names] is empty. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentTagChipRow(title: String, names: List<String>, icon: ImageVector = Icons.Filled.FitnessCenter) {
    if (names.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEach { ContentTagChip(name = it, icon = icon) }
        }
    }
}

/** Resolves tag ids → display names through the classification cache, sorted. */
object ContentTagNames {
    fun equipment(ids: Collection<String>): List<String> = ids.mapNotNull { ClassificationStore.equipmentName(it) }.sorted()
    fun anatomy(ids: Collection<String>): List<String> = ids.mapNotNull { ClassificationStore.anatomyName(it) }.sorted()
}

// MARK: - Editor: picker section

enum class ContentTagKind {
    EQUIPMENT, ANATOMY;

    val title: String get() = when (this) { EQUIPMENT -> "Equipment"; ANATOMY -> "Muscles Worked" }
    val icon: ImageVector get() = when (this) { EQUIPMENT -> Icons.Filled.FitnessCenter; ANATOMY -> Icons.Outlined.Accessibility }

    fun groups(): List<Pair<String, List<TagOption>>> = when (this) {
        EQUIPMENT -> ClassificationStore.equipmentByCategory.map { (cat, items) -> cat to items.map { TagOption(it.id.toString().lowercase(Locale.US), it.name) } }
        ANATOMY -> ClassificationStore.anatomyByCategory.map { (cat, items) -> cat to items.map { TagOption(it.id.toString().lowercase(Locale.US), it.name) } }
    }

    fun names(ids: Collection<String>): List<String> = when (this) {
        EQUIPMENT -> ContentTagNames.equipment(ids)
        ANATOMY -> ContentTagNames.anatomy(ids)
    }
}

/**
 * Editor block for one explicit tag set (override-with-fallback contract).
 * [revision] is read so the block recomposes when the host mutates the
 * (non-observable) [selection]; [onSelect] / [onReset] route the writes back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentTagsEditorSection(
    kind: ContentTagKind,
    selection: ContentTagSelection,
    revision: Int,
    onSelect: (Set<String>) -> Unit,
    onReset: () -> Unit
) {
    @Suppress("UNUSED_VARIABLE") val rev = revision
    var showPicker by remember { mutableStateOf(false) }
    val names = kind.names(selection.selectedIds)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(kind.icon, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = kind.title, fontSize = 13.sp, color = TextSecondaryColor)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (selection.selectedIds.isEmpty()) "Select ›" else "Edit ›",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (selection.isLoaded) FLPrimary else TextSecondaryColor,
                modifier = Modifier.clickable(enabled = selection.isLoaded) { showPicker = true }
            )
        }
        if (names.isEmpty()) {
            val label = when {
                !selection.isLoaded -> "Unavailable offline"
                selection.isPendingReset -> "Will use the movements' ${kind.title.lowercase(Locale.US)} after save"
                else -> "None yet"
            }
            Text(text = label, fontSize = 13.sp, color = TextSecondaryColor)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                names.forEach { ContentTagChip(name = it, icon = kind.icon) }
            }
        }
        val hint = when {
            selection.isPendingReset -> "Resets to movement defaults on save"
            selection.isShowingDerived && selection.selectedIds.isNotEmpty() -> "From movements"
            else -> null
        }
        if (hint != null) Text(text = hint, fontSize = 11.sp, color = TextSecondaryColor, fontStyle = FontStyle.Italic)
        if (selection.isExplicit || selection.isTouched) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = !selection.isPendingReset) { onReset() }
            ) {
                Icon(Icons.Filled.Undo, contentDescription = null, tint = if (selection.isPendingReset) TextSecondaryColor else FLPrimary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Reset to movement defaults",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selection.isPendingReset) TextSecondaryColor else FLPrimary
                )
            }
        }
    }

    if (showPicker) {
        ClassificationPickerSheet(
            title = kind.title,
            groups = kind.groups(),
            selectedIds = selection.selectedIds,
            onSelectionChange = onSelect,
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * Categorised multi-select picker over a taxonomy (iOS
 * `ClassificationPickerView`): searchable, grouped by category, checkmark rows.
 * `*` / `?` act as wildcards in the search query.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassificationPickerSheet(
    title: String,
    groups: List<Pair<String, List<TagOption>>>,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var search by remember { mutableStateOf("") }
    var selected by remember(selectedIds) { mutableStateOf(selectedIds.map { it.lowercase(Locale.US) }.toSet()) }

    val filtered = remember(groups, search) {
        val q = search.trim()
        if (q.isEmpty()) groups else groups.mapNotNull { (cat, items) ->
            val hits = items.filter { matches(it.name, q) }
            if (hits.isEmpty()) null else cat to hits
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BackgroundColor) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done", fontWeight = FontWeight.SemiBold, color = FLPrimary) }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search ${title.lowercase(Locale.US)}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                if (filtered.isEmpty()) {
                    item { Text("No matches for “$search”", color = TextSecondaryColor, modifier = Modifier.padding(vertical = 12.dp)) }
                }
                filtered.forEach { (category, items) ->
                    item(key = "cat-$category") {
                        Text(
                            text = category.replaceFirstChar { it.uppercase() },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryColor,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(items, key = { "$category-${it.id}" }) { item ->
                        val isOn = selected.contains(item.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(White)
                                .clickable {
                                    selected = if (isOn) selected - item.id else selected + item.id
                                    onSelectionChange(selected)
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = item.name, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                            if (isOn) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = FLPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

private fun matches(name: String, query: String): Boolean {
    val q = query.lowercase(Locale.US)
    val n = name.lowercase(Locale.US)
    if (!q.contains('*') && !q.contains('?')) return n.contains(q)
    val pattern = StringBuilder("^")
    for (ch in q) {
        when (ch) {
            '*' -> pattern.append(".*")
            '?' -> pattern.append('.')
            else -> pattern.append(Regex.escape(ch.toString()))
        }
    }
    pattern.append('$')
    return Regex(pattern.toString()).containsMatchIn(n)
}
