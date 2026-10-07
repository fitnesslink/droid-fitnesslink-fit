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
    val lowerSelected = remember(selectedIds) { selectedIds.mapTo(HashSet()) { it.lowercase(Locale.US) } }
    val filteredGroups = remember(groups, searchText) {
        val q = searchText.trim()
        if (q.isEmpty()) groups
        else groups.mapNotNull { g ->
            val items = g.items.filter { classificationNameMatches(it.name, q) }
            if (items.isEmpty()) null else g.copy(items = items)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(BackgroundColor)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimaryColor)
            }
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Done", fontWeight = FontWeight.SemiBold, color = FLPrimary) }
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
            filteredGroups.forEach { group ->
                item(key = "header:${group.id}") {
                    Text(
                        group.title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() },
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
                            .clickable { onToggle(item.id) }
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
