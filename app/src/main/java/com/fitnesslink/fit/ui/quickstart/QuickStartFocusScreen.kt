package com.fitnesslink.fit.ui.quickstart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.TrainingFocus
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.QuickStartViewModel
import kotlinx.coroutines.launch

/** Material glyph for a focus's symbol key. */
fun focusIcon(symbol: String): ImageVector = when (symbol) {
    "lowerBody" -> Icons.Filled.DirectionsWalk
    "upperBody" -> Icons.Filled.AccessibilityNew
    "fullBody" -> Icons.Filled.SportsGymnastics
    "legs" -> Icons.Filled.DirectionsWalk
    "push" -> Icons.Filled.ArrowUpward
    "pull" -> Icons.Filled.ArrowDownward
    "chest" -> Icons.Filled.FitnessCenter
    "back" -> Icons.Filled.Rowing
    "shoulders" -> Icons.Filled.SportsMartialArts
    "arms" -> Icons.Filled.FitnessCenter
    "core" -> Icons.Filled.SelfImprovement
    "conditioning" -> Icons.Filled.DirectionsRun
    "mobility" -> Icons.Filled.SelfImprovement
    "recovery" -> Icons.Filled.Spa
    "other" -> Icons.Filled.MoreHoriz
    else -> Icons.Filled.AutoAwesome
}

/**
 * Quick Start PRD §8 — "What are you training today?". A grid of the
 * suggested focuses plus a custom one; Next mints the workout, starts the
 * session clock and hands off to the live canvas (replacing this screen in
 * the stack, so Back from the canvas doesn't land here). Port of iOS
 * `QuickStartFocusView`.
 *
 * In picker mode ([onPick] set — the canvas's "Change focus") the choice
 * is handed back and the presenter dismisses.
 */
@Composable
fun QuickStartFocusScreen(
    onBack: () -> Unit,
    onPick: ((TrainingFocus) -> Unit)? = null,
    initial: TrainingFocus? = null,
    onStarted: ((String) -> Unit)? = null
) {
    val viewModel: QuickStartViewModel = viewModel()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(initial) }
    var customDraft by remember { mutableStateOf("") }
    var showCustomPrompt by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }
    val isPickerMode = onPick != null

    fun next() {
        val focus = selected ?: return
        if (onPick != null) { onPick(focus); return }
        isStarting = true
        scope.launch {
            val id = viewModel.start(focus, null)
            isStarting = false
            // Swap this screen for the canvas so Back returns to wherever Quick Start was launched from.
            if (onStarted != null) onStarted(id)
            else if (!AppRouter.replaceTop("quickStartCanvas/$id")) AppRouter.push("quickStartCanvas/$id")
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            HeaderBackView(onBack = onBack)
            Text(
                if (isPickerMode) "Training Focus" else "Quick Start", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor, modifier = Modifier.align(Alignment.Center)
            )
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("What are you training today?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, textAlign = TextAlign.Center)
                Text("This helps us name your workout and suggest relevant exercises.", fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center)
            }
            TrainingFocus.presets.chunked(3).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { focus -> FocusTile(focus, selected?.id == focus.id, Modifier.weight(1f)) { selected = focus } }
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            selected?.takeIf { !it.isPreset }?.let { custom ->
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLPrimary.copy(alpha = 0.12f))
                        .border(1.5.dp, FLPrimary, RoundedCornerShape(12.dp)).padding(14.dp)
                ) {
                    Icon(focusIcon(custom.symbol), contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(custom.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                        Text("Custom focus", fontSize = 12.sp, color = TextSecondaryColor)
                    }
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FLPrimary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(Modifier.weight(1f), color = MediumGray.copy(alpha = 0.4f))
                Text("OR", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                HorizontalDivider(Modifier.weight(1f), color = MediumGray.copy(alpha = 0.4f))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).clickable {
                    customDraft = selected?.takeIf { !it.isPreset }?.name ?: ""
                    showCustomPrompt = true
                }.padding(14.dp)
            ) {
                Icon(Icons.Filled.AddCircleOutline, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp))
                Text("Create Custom", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(16.dp))
            }
        }
        // A real inset, not an overlay, so short content never slides under the button.
        Box(Modifier.fillMaxWidth().background(BackgroundColor).padding(horizontal = 20.dp, vertical = 8.dp)) {
            val enabled = selected != null && !isStarting
            Box(
                modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (enabled) FLPrimary else MediumGray.copy(alpha = 0.5f)).clickable(enabled = enabled) { next() },
                contentAlignment = Alignment.Center
            ) {
                if (isStarting) CircularProgressIndicator(color = White, modifier = Modifier.size(22.dp))
                else Text(if (isPickerMode) "Use This Focus" else "Next", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showCustomPrompt) {
        AlertDialog(
            onDismissRequest = { showCustomPrompt = false },
            title = { Text("Custom Training Focus") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Examples: Athletic Performance, Coach Session, Glute Day, Full Body + Conditioning.", fontSize = 13.sp, color = TextSecondaryColor)
                    OutlinedTextField(value = customDraft, onValueChange = { customDraft = it }, singleLine = true, placeholder = { Text("e.g. Coach Session") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { TrainingFocus.custom(customDraft)?.let { selected = it }; showCustomPrompt = false }) { Text("Use", color = FLPrimary, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { showCustomPrompt = false }) { Text("Cancel", color = TextSecondaryColor) } }
        )
    }
}

@Composable
private fun FocusTile(focus: TrainingFocus, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(if (isSelected) FLPrimary.copy(alpha = 0.12f) else White)
            .border(1.5.dp, if (isSelected) FLPrimary else Color.Transparent, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 4.dp)
    ) {
        Icon(focusIcon(focus.symbol), contentDescription = null, tint = if (isSelected) FLPrimary else TextPrimaryColor, modifier = Modifier.size(24.dp))
        Text(focus.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isSelected) FLPrimary else TextPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
