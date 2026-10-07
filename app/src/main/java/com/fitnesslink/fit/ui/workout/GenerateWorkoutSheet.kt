package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.model.Workout
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.AIWorkoutGeneratorViewModel
import com.fitnesslink.fit.viewmodel.WorkoutEditorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.exp

/**
 * Entry sheet for AI workout generation (iOS `GenerateWorkoutSheet`):
 * prompt + optional duration / level / equipment, offline banner, staged
 * progress card while generating, then the read-only review step. Nothing
 * is saved here — Approve hands an editable `Workout` to [onApprove].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerateWorkoutSheet(seedPrompt: String? = null, onApprove: (Workout) -> Unit, onDismiss: () -> Unit) {
    val aiVM: AIWorkoutGeneratorViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val isOnline by NetworkMonitor.isConnected.collectAsState()
    var selectedEquipmentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showEquipmentPicker by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var generationStartedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val durations = listOf(15, 30, 45, 60, 90)
    val canGenerate = aiVM.prompt.isNotBlank() && !aiVM.isGenerating && isOnline

    LaunchedEffect(Unit) {
        if (aiVM.prompt.isEmpty() && !seedPrompt.isNullOrEmpty()) aiVM.prompt = seedPrompt
        runCatching { ClassificationStore.refresh() }
    }

    if (showEquipmentPicker) {
        ClassificationPickerSheet(
            title = "Equipment",
            groups = ContentTagKind.EQUIPMENT.groups(),
            selectedIds = selectedEquipmentIds,
            onSelectionChange = { selectedEquipmentIds = it },
            onDismiss = { showEquipmentPicker = false }
        )
    }

    Dialog(
        onDismissRequest = { if (!aiVM.isGenerating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !aiVM.isGenerating)
    ) {
        if (showReview && aiVM.draft != null) {
            GeneratedWorkoutReviewScreen(
                aiVM = aiVM,
                onBack = { showReview = false },
                onDiscard = { aiVM.discard(); showReview = false },
                onApprove = { workout -> onApprove(workout) }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
                Row(modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !aiVM.isGenerating) { Text("Cancel", color = TextSecondaryColor) }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Generate with AI", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(72.dp))
                }
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!isOnline) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.WifiOff, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(16.dp))
                            Text("You need an active internet connection to generate a workout.", fontSize = 13.sp, color = TextSecondaryColor)
                        }
                    }
                    FormSection("Describe your workout") {
                        OutlinedTextField(
                            value = aiVM.prompt, onValueChange = { aiVM.prompt = it }, enabled = !aiVM.isGenerating,
                            placeholder = { Text("e.g. 45-minute upper body with dumbbells, intermediate level") },
                            minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    FormSection("Duration (optional)") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            durations.forEach { m ->
                                EditorChip("$m min", aiVM.durationMinutes == m, enabled = !aiVM.isGenerating,
                                    onClick = { aiVM.durationMinutes = if (aiVM.durationMinutes == m) null else m })
                            }
                        }
                    }
                    FormSection("Training level (optional)") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            WorkoutEditorViewModel.trainingLevelOptions.forEach { lvl ->
                                EditorChip(lvl, aiVM.level == lvl, enabled = !aiVM.isGenerating, onClick = { aiVM.level = if (aiVM.level == lvl) null else lvl })
                            }
                        }
                    }
                    FormSection("Equipment (optional)") {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !aiVM.isGenerating) { showEquipmentPicker = true }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Select equipment", fontSize = 15.sp, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                            Text(if (selectedEquipmentIds.isEmpty()) "Any" else "${selectedEquipmentIds.size} selected", fontSize = 14.sp, color = TextSecondaryColor)
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondaryColor)
                        }
                    }
                    aiVM.errorMessage?.let { Text(it, fontSize = 13.sp, color = Color.Red) }

                    if (aiVM.isGenerating) {
                        GenerationProgressView(startedAt = generationStartedAt)
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (canGenerate) FLPrimary else FLPrimary.copy(alpha = 0.4f))
                                .clickable(enabled = canGenerate) {
                                    aiVM.equipment = selectedEquipmentIds.mapNotNull { ClassificationStore.equipmentName(it) }
                                    generationStartedAt = System.currentTimeMillis()
                                    scope.launch {
                                        aiVM.generate()
                                        if (aiVM.result != null) showReview = true
                                    }
                                }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Workout", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title.uppercase(Locale.US), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryColor)
        content()
    }
}

private data class Stage(val after: Long, val text: String)

private val stages = listOf(
    Stage(0, "Reading your prompt"),
    Stage(2, "Choosing movements"),
    Stage(5, "Building supersets and circuits"),
    Stage(8, "Balancing sets, reps and rest"),
    Stage(11, "Matching to the video library"),
    Stage(15, "Almost there")
)

/** Staged progress card — time-based approximations so a 6–16 s wait never looks frozen. */
@Composable
private fun GenerationProgressView(startedAt: Long) {
    var elapsed by remember(startedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsed = (System.currentTimeMillis() - startedAt) / 1000
            delay(250)
        }
    }
    val current = stages.indexOfLast { elapsed >= it.after }.coerceAtLeast(0)
    val progress = minOf(0.92, 1 - exp(-elapsed / 7.0)).toFloat()

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp))
            Text("${stages[current].text}…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${elapsed}s", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
        }
        LinearProgressIndicator(progress = { progress }, color = FLPrimary, modifier = Modifier.fillMaxWidth())
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            stages.dropLast(1).forEachIndexed { index, stage ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (index < current) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = when { index < current -> FLPrimary; index == current -> TextPrimaryColor; else -> TextSecondaryColor.copy(alpha = 0.5f) },
                        modifier = Modifier.size(14.dp)
                    )
                    Text(stage.text, fontSize = 13.sp, color = if (index <= current) TextPrimaryColor else TextSecondaryColor)
                }
            }
        }
        Text("Usually 5–15 seconds. Longer, detailed prompts take a little more.", fontSize = 12.sp, color = TextSecondaryColor)
    }
}
