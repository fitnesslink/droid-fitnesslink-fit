package com.fitnesslink.fit.ui.movements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnesslink.fit.movements.TagFilterOptions
import com.fitnesslink.fit.ui.components.TagOption
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.util.Locale

/** The drawer's full selection, applied in one shot. */
data class MoreFiltersSelection(
    val anatomyIds: Set<String> = emptySet(),
    val equipmentIds: Set<String> = emptySet(),
    val difficultyIds: Set<String> = emptySet(),
    val goalIds: Set<String> = emptySet(),
    val movementPatternIds: Set<String> = emptySet(),
    val locationIds: Set<String> = emptySet()
) {
    val totalCount: Int
        get() = anatomyIds.size + equipmentIds.size + difficultyIds.size + goalIds.size + movementPatternIds.size + locationIds.size
}

/**
 * Drawer behind the "More filters" chip (iOS `MoreFiltersSheet`): the full
 * anatomy + equipment taxonomy (drill-in pickers grouped by category) plus
 * the tag dimensions (Difficulty / Goal / Movement Pattern / Location) as
 * inline checklists. Drafts are local; Apply writes them back, Clear All
 * wipes, dismiss discards. A dimension with no options renders no section.
 */
@Composable
fun MoreFiltersSheet(
    initial: MoreFiltersSelection,
    tagOptions: TagFilterOptions = TagFilterOptions(),
    onApply: (MoreFiltersSelection) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    var drillIn by remember { mutableStateOf<DrillIn?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
            when (drillIn) {
                DrillIn.ANATOMY -> ClassificationPickerView(
                    title = "Anatomy",
                    groups = remember { ClassificationGroup.anatomyGroups() },
                    selectedIds = draft.anatomyIds,
                    onToggle = { draft = draft.copy(anatomyIds = draft.anatomyIds.toggled(it)) },
                    onDone = { drillIn = null }
                )
                DrillIn.EQUIPMENT -> ClassificationPickerView(
                    title = "Equipment",
                    groups = remember { ClassificationGroup.equipmentGroups() },
                    selectedIds = draft.equipmentIds,
                    onToggle = { draft = draft.copy(equipmentIds = draft.equipmentIds.toggled(it)) },
                    onDone = { drillIn = null }
                )
                null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { draft = MoreFiltersSelection() }, enabled = draft.totalCount > 0) {
                            Text("Clear All", color = if (draft.totalCount > 0) FLPrimary else MediumGray)
                        }
                        Text(
                            "More Filters", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor,
                            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        TextButton(onClick = { onApply(draft); onDismiss() }) {
                            Text(
                                if (draft.totalCount > 0) "Apply (${draft.totalCount})" else "Apply",
                                fontWeight = FontWeight.SemiBold, color = FLPrimary
                            )
                        }
                    }
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SectionHeader("Anatomy")
                        SummaryRow("Anatomy", draft.anatomyIds.size) { drillIn = DrillIn.ANATOMY }
                        SectionHeader("Equipment")
                        SummaryRow("Equipment", draft.equipmentIds.size) { drillIn = DrillIn.EQUIPMENT }
                        TagSection("Difficulty", tagOptions.difficulty, draft.difficultyIds) {
                            draft = draft.copy(difficultyIds = draft.difficultyIds.toggled(it))
                        }
                        TagSection("Goal", tagOptions.goal, draft.goalIds) {
                            draft = draft.copy(goalIds = draft.goalIds.toggled(it))
                        }
                        TagSection("Movement Pattern", tagOptions.movementPattern, draft.movementPatternIds) {
                            draft = draft.copy(movementPatternIds = draft.movementPatternIds.toggled(it))
                        }
                        TagSection("Location", tagOptions.location, draft.locationIds) {
                            draft = draft.copy(locationIds = draft.locationIds.toggled(it))
                        }
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

private enum class DrillIn { ANATOMY, EQUIPMENT }

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(Locale.US), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun SummaryRow(title: String, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(White)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
        Text(if (count == 0) "Any" else "$count selected", fontSize = 14.sp, color = TextSecondaryColor)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MediumGray)
    }
    HorizontalDivider(color = BackgroundColor)
}

@Composable
private fun TagSection(title: String, options: List<TagOption>, selected: Set<String>, onToggle: (String) -> Unit) {
    if (options.isEmpty()) return
    SectionHeader(title)
    TagOptionChecklist(options = options, selectedIds = selected, onToggle = onToggle)
}

private fun Set<String>.toggled(id: String): Set<String> =
    if (any { it.equals(id, ignoreCase = true) }) filterNot { it.equals(id, ignoreCase = true) }.toSet() else this + id
