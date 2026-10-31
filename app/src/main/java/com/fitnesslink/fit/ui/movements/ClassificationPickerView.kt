package com.fitnesslink.fit.ui.movements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.movements.classificationNameMatches
import com.fitnesslink.fit.ui.components.SearchView
import com.fitnesslink.fit.ui.components.TagOption
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.util.Locale

/** A titled bucket of taxonomy items (iOS `ClassificationGroup`). */
data class ClassificationGroup(
    val id: String,
    val title: String,
    val items: List<TagOption>
) {
    companion object {
        /** Anatomy grouped by server `category` (iOS `ClassificationGroup.anatomyGroups`). */
        fun anatomyGroups(): List<ClassificationGroup> =
            ClassificationStore.anatomyByCategory.map { (category, rows) ->
                ClassificationGroup("anatomy:$category", category, rows.map { TagOption(it.id.toString(), it.name) })
            }

        fun equipmentGroups(): List<ClassificationGroup> =
            ClassificationStore.equipmentByCategory.map { (category, rows) ->
                ClassificationGroup("equipment:$category", category, rows.map { TagOption(it.id.toString(), it.name) })
            }
    }
}

/**
 * Categorized multi-select picker shared by the "More filters" drawer and
 * the create-exercise form (iOS `ClassificationPickerView`). Search supports
 * `*` / `?` wildcards; groups left with no matches are dropped. Selection is
 * hoisted: [selectedIds] is read, [onToggle] mutates it, [onDone] closes.
 *
 * FTNSSLNK-517: explicit Cancel (restores the selection the picker opened
 * with) and "Done (n)" replace the bare back arrow, and a "Selected (n)"
 * section at the top lists the current picks (tap to deselect, Clear for all)
 * so a pick made from a search result stays visible once the search clears.
 * Cancel / Clear are expressed as [onToggle] calls, so callers need no new API.
 */
@Composable
fun ClassificationPickerView(
    title: String,
    groups: List<ClassificationGroup>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchText by remember { mutableStateOf("") }
    /** Selection when the picker opened — what Cancel restores. */
    val initialSelection = remember { selectedIds }
    val lowerSelected = remember(selectedIds) { selectedIds.mapTo(HashSet()) { it.lowercase(Locale.US) } }
    val filteredGroups = remember(groups, searchText) {
        val q = searchText.trim()
        if (q.isEmpty()) groups
        else groups.mapNotNull { g ->
            val items = g.items.filter { classificationNameMatches(it.name, q) }
            if (items.isEmpty()) null else g.copy(items = items)
        }
    }

    // Current picks resolved to items, in taxonomy order (ids match case-insensitively).
    val selectedItems = remember(groups, lowerSelected) {
        val seen = HashSet<String>()
        groups.flatMap { it.items }.filter { val key = it.id.lowercase(Locale.US); key in lowerSelected && seen.add(key) }
    }
    fun deselect(id: String) {
        selectedIds.filter { it.equals(id, ignoreCase = true) }.forEach(onToggle)
    }
    fun cancel() {
        val initialLower = initialSelection.mapTo(HashSet()) { it.lowercase(Locale.US) }
        selectedIds.filter { it.lowercase(Locale.US) !in initialLower }.forEach(onToggle)
        initialSelection.filter { it.lowercase(Locale.US) !in lowerSelected }.forEach(onToggle)
        onDone()
    }

    Column(modifier = modifier.fillMaxSize().background(BackgroundColor)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { cancel() }) { Text("Cancel", color = TextSecondaryColor) }
            Text(
                title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDone) {
                Text(if (selectedItems.isEmpty()) "Done" else "Done (${selectedItems.size})", fontWeight = FontWeight.SemiBold, color = FLPrimary)
            }
        }
        SearchView(
            text = searchText,
            onTextChange = { searchText = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = "Search ${title.lowercase(Locale.US)}",
            showFilterIcon = false
        )
        if (filteredGroups.isEmpty()) {
            Text(
                "No matches for “$searchText”",
                fontSize = 14.sp, color = TextSecondaryColor,
                modifier = Modifier.padding(20.dp)
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            if (selectedItems.isNotEmpty()) {
                item(key = "selected-header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Selected (${selectedItems.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor, modifier = Modifier.weight(1f))
                        TextButton(onClick = { selectedIds.toList().forEach(onToggle) }) {
                            Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                        }
                    }
                }
                items(selectedItems, key = { "selected:${it.id}" }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(White)
                            .clickable { deselect(item.id) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.name, fontSize = 15.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Cancel, contentDescription = "Remove ${item.name}", tint = MediumGray, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 20.dp), color = BackgroundColor)
                }
            }
            filteredGroups.forEach { group ->
                item(key = "header:${group.id}") {
                    Text(
                        // "upper_body" → "Upper Body" (iOS capitalizes every word).
                        group.title.replace("_", " ").split(" ").joinToString(" ") { w ->
                            w.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
                        },
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
                    )
                }
                items(group.items, key = { "${group.id}:${it.id}" }) { item ->
                    val checked = item.id.lowercase(Locale.US) in lowerSelected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(White)
                            .clickable { if (checked) deselect(item.id) else onToggle(item.id) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.name, fontSize = 15.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                        if (checked) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = FLPrimary, modifier = Modifier.size(18.dp))
                        } else {
                            Spacer(modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 20.dp), color = BackgroundColor)
                }
            }
        }
    }
}

/** Inline checkmark list for a small tag dimension (3–6 options) — no drill-in needed. */
@Composable
fun TagOptionChecklist(
    options: List<TagOption>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lowerSelected = remember(selectedIds) { selectedIds.mapTo(HashSet()) { it.lowercase(Locale.US) } }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        options.forEachIndexed { index, option ->
            val checked = option.id.lowercase(Locale.US) in lowerSelected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(White)
                    .clickable { onToggle(option.id) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(option.name, fontSize = 15.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                if (checked) Icon(Icons.Default.Check, contentDescription = "Selected", tint = FLPrimary, modifier = Modifier.size(18.dp))
            }
            if (index < options.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 20.dp), color = BackgroundColor)
        }
    }
}
