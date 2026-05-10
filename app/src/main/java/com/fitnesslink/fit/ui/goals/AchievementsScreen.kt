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

@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadData() }

    var selected by remember { mutableStateOf<AchievementSelection?>(null) }
    val earned = viewModel.earnedAchievements
    val locked = viewModel.lockedTypes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Achievements",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )
            }

            if (earned.isEmpty() && locked.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
            }

            // Earned section header
            if (earned.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(text = "Earned", color = TextPrimaryColor)
                }
                items(earned, key = { it.id }) { achievement ->
                    AchievementBadge(
                        type = achievement.achievementType,
                        isEarned = true,
                        onClick = {
                            selected = AchievementSelection(
                                type = achievement.achievementType,
                                isEarned = true,
                                earnedAt = Date(achievement.earnedAt)
                            )
                        }
                    )
                }
            }

            // Locked section header
            if (locked.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(text = "Locked", color = TextSecondaryColor)
                }
                items(locked, key = { it.name }) { type ->
                    AchievementBadge(
                        type = type,
                        isEarned = false,
                        onClick = {
                            selected = AchievementSelection(type = type, isEarned = false)
                        }
                    )
                }
            }
        }
    }

    selected?.let { sel ->
        AchievementDetailDialog(selection = sel, onDismiss = { selected = null })
    }
}

@Composable
private fun SectionHeader(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
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
        Text(
            text = "No achievements yet",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryColor
        )
        Text(
            text = "Complete habits and goals to earn badges.",
            fontSize = 13.sp,
            color = TextSecondaryColor
        )
    }
}

@Composable
private fun AchievementDetailDialog(
    selection: AchievementSelection,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(selection.type.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selection.type.description, fontSize = 14.sp, color = TextSecondaryColor)
                if (selection.isEarned && selection.earnedAt != null) {
                    Text(
                        text = "Earned ${dateFormatter.format(selection.earnedAt)}",
                        fontSize = 12.sp,
                        color = FLPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "Locked — keep going to earn this badge.",
                        fontSize = 12.sp,
                        color = TextSecondaryColor
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private data class AchievementSelection(
    val type: AchievementType,
    val isEarned: Boolean,
    val earnedAt: Date? = null
)

private val dateFormatter: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
