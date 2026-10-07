package com.fitnesslink.fit.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.AchievementType
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.viewmodel.AchievementsViewModel
import java.text.DateFormat
import java.util.Date

/**
 * iOS `AchievementsView`: 3-column grid, Earned then Locked. Locked comes
 * from the `GET /achievements` definitions the user hasn't earned (client
 * enum fallback offline). Tapping a badge opens Android's detail dialog.
 */
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadData() }

    var selected by remember { mutableStateOf<AchievementSelection?>(null) }
    val earned = viewModel.earnedAchievements
    val lockedDefinitions = viewModel.lockedDefinitions
    val lockedTypes = if (viewModel.definitions.isEmpty()) viewModel.lockedTypes else emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Achievements", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            }

            if (earned.isEmpty() && lockedDefinitions.isEmpty() && lockedTypes.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
            }

            if (earned.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Earned", TextPrimaryColor) }
                items(earned, key = { it.id }) { achievement ->
                    val type = achievement.achievementType
                    AchievementBadge(
                        title = achievement.title.ifBlank { type?.title ?: "Achievement" },
                        icon = achievementIcon(type, achievement.title),
                        isEarned = true,
                        onClick = {
                            selected = AchievementSelection(
                                title = achievement.title.ifBlank { type?.title ?: "Achievement" },
                                description = achievement.description.ifBlank { type?.description ?: "" },
                                isEarned = true,
                                earnedAt = Date(achievement.earnedAt)
                            )
                        }
                    )
                }
            }

            if (lockedDefinitions.isNotEmpty() || lockedTypes.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("Locked", TextSecondaryColor)
                }
                items(lockedDefinitions, key = { "def-${it.id}" }) { def ->
                    val type = AchievementType.fromWire(def.achievementType)
                    AchievementBadge(
                        title = def.name.ifBlank { type?.title ?: def.achievementType },
                        icon = achievementIcon(type, def.name),
                        isEarned = false,
                        onClick = {
                            selected = AchievementSelection(
                                title = def.name.ifBlank { type?.title ?: def.achievementType },
                                description = def.description ?: type?.description ?: "",
                                isEarned = false
                            )
                        }
                    )
                }
                items(lockedTypes, key = { "type-${it.name}" }) { type ->
                    AchievementBadge(
                        type = type,
                        isEarned = false,
                        onClick = { selected = AchievementSelection(title = type.title, description = type.description, isEarned = false) }
                    )
                }
            }
        }
    }

    selected?.let { sel -> AchievementDetailDialog(selection = sel, onDismiss = { selected = null }) }
}

@Composable
private fun SectionHeader(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("No achievements yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        Text("Complete habits and goals to earn badges.", fontSize = 13.sp, color = TextSecondaryColor)
    }
}

@Composable
private fun AchievementDetailDialog(selection: AchievementSelection, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(selection.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selection.description.isNotBlank()) Text(selection.description, fontSize = 14.sp, color = TextSecondaryColor)
                if (selection.isEarned && selection.earnedAt != null) {
                    Text("Earned ${dateFormatter.format(selection.earnedAt)}", fontSize = 12.sp, color = FLPrimary, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Locked — keep going to earn this badge.", fontSize = 12.sp, color = TextSecondaryColor)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private data class AchievementSelection(
    val title: String,
    val description: String,
    val isEarned: Boolean,
    val earnedAt: Date? = null
)

private val dateFormatter: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
