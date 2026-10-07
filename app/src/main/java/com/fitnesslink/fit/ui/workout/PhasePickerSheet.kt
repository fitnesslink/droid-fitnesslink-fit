package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.fitnesslink.fit.model.api.AvailableWorkoutPhase
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.WorkoutPhaseTemplate
import com.fitnesslink.fit.network.dto.WorkoutPhaseTemplateSummary
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.workouts.WorkoutPhaseStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Picker for adding a phase to the workout (iOS `PhasePickerSheet`):
 * Suggested (system) / My Phases (custom, with rename + delete) / My
 * Templates (phase-with-tasks) / inline Create New Phase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhasePickerSheet(
    alreadyAttachedIds: Set<String>,
    onPick: (AvailableWorkoutPhase) -> Unit,
    onPickTemplate: ((WorkoutPhaseTemplate) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val available by WorkoutPhaseStore.available.collectAsState()
    val systemPhases = available.filter { it.isSystem }.sortedBy { it.sortOrder }
    val customPhases = available.filter { !it.isSystem }

    var newPhaseName by remember { mutableStateOf("") }
    var inlineError by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AvailableWorkoutPhase?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<AvailableWorkoutPhase?>(null) }
    var deleteAlertMessage by remember { mutableStateOf<String?>(null) }
    var templates by remember { mutableStateOf<List<WorkoutPhaseTemplateSummary>>(emptyList()) }
    var loadingTemplateId by remember { mutableStateOf<String?>(null) }
    var templateError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        WorkoutPhaseStore.refresh()
        if (onPickTemplate != null && NetworkMonitor.isConnected.value) {
            templates = withContext(Dispatchers.IO) { runCatching { ApiClient.workoutPhaseApi.listTemplates() }.getOrNull() } ?: templates
        }
    }

    fun create() {
        val name = newPhaseName.trim()
        if (name.isEmpty() || isCreating) return
        inlineError = null
        isCreating = true
        scope.launch {
            try {
                WorkoutPhaseStore.create(name)
                newPhaseName = ""
            } catch (e: Exception) {
                inlineError = e.message ?: "Couldn't create the phase."
            } finally {
                isCreating = false
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Phase") },
            text = { OutlinedTextField(value = renameDraft, onValueChange = { renameDraft = it }, singleLine = true, label = { Text("Phase name") }) },
            confirmButton = {
                TextButton(onClick = {
                    val name = renameDraft.trim()
                    renameTarget = null
                    if (name.isNotEmpty() && name != target.name) scope.launch { runCatching { WorkoutPhaseStore.rename(target.id, name) } }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
    deleteTarget?.takeIf { deleteAlertMessage == null }?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Phase?") },
            text = { Text("\"${target.name}\" will be removed from your phases.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            WorkoutPhaseStore.deletePhase(target.id)
                            deleteTarget = null
                        } catch (e: WorkoutPhaseStore.PhaseError.InUse) {
                            deleteAlertMessage = "\"${target.name}\" is attached to one or more workouts. Remove it from those workouts before deleting."
                        } catch (e: Exception) {
                            deleteAlertMessage = e.message ?: "Couldn't delete the phase."
                        }
                    }
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
    deleteAlertMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleteAlertMessage = null; deleteTarget = null },
            title = { Text("Phase In Use") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { deleteAlertMessage = null; deleteTarget = null }) { Text("OK") } }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BackgroundColor) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add Phase", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Done", fontWeight = FontWeight.SemiBold, color = FLPrimary) }
                }
            }
            if (systemPhases.isNotEmpty()) {
                item { SectionHeader("Suggested") }
                items(systemPhases, key = { "sys-${it.id}" }) { phase ->
                    PhaseRow(phase, alreadyAttachedIds.contains(phase.id.lowercase(Locale.US)), onPick = { onPick(phase); onDismiss() })
                }
            }
            if (customPhases.isNotEmpty()) {
                item { SectionHeader("My Phases") }
                items(customPhases, key = { "custom-${it.id}" }) { phase ->
                    PhaseRow(
                        phase, alreadyAttachedIds.contains(phase.id.lowercase(Locale.US)),
                        onPick = { onPick(phase); onDismiss() },
                        onRename = { renameTarget = phase; renameDraft = phase.name },
                        onDelete = { deleteTarget = phase }
                    )
                }
            }
            if (onPickTemplate != null && templates.isNotEmpty()) {
                item { SectionHeader("My Templates") }
                items(templates, key = { "tpl-${it.id}" }) { template ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(White)
                            .clickable(enabled = loadingTemplateId == null) {
                                templateError = null
                                loadingTemplateId = template.id
                                scope.launch {
                                    try {
                                        val full = withContext(Dispatchers.IO) { ApiClient.workoutPhaseApi.getTemplate(template.id) }
                                        onPickTemplate(full)
                                        onDismiss()
                                    } catch (_: Exception) {
                                        templateError = "Couldn't load \"${template.name}\". Please try again."
                                    } finally {
                                        loadingTemplateId = null
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Layers, contentDescription = null, tint = PurpleTheme, modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(template.name, fontSize = 15.sp, color = TextPrimaryColor)
                            Text(if (template.taskCount == 1) "1 task" else "${template.taskCount} tasks", fontSize = 12.sp, color = TextSecondaryColor)
                        }
                        if (loadingTemplateId == template.id) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else if (template.isSystem) PickerBadge("System", FLPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                templateError?.let { item { Text(it, fontSize = 12.sp, color = Color.Red) } }
            }
            item {
                SectionHeader("Create New Phase")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPhaseName, onValueChange = { newPhaseName = it }, placeholder = { Text("e.g. Mobility") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    if (isCreating) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Icon(
                        Icons.Filled.AddCircle, contentDescription = "Create phase",
                        tint = if (newPhaseName.isBlank()) MediumGray else FLPrimary,
                        modifier = Modifier.size(28.dp).clickable(enabled = newPhaseName.isNotBlank()) { create() }
                    )
                }
                inlineError?.let { Text(it, fontSize = 12.sp, color = Color.Red, modifier = Modifier.padding(top = 4.dp)) }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun PickerBadge(text: String, color: Color) {
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun PhaseRow(
    phase: AvailableWorkoutPhase, isAttached: Boolean, onPick: () -> Unit,
    onRename: (() -> Unit)? = null, onDelete: (() -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(White)
                .clickable(enabled = !isAttached, onClick = onPick).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (phase.isSystem) Icons.Filled.AutoAwesome else Icons.Filled.Label, contentDescription = null,
                tint = if (phase.isSystem) FLPrimary else BlueTheme, modifier = Modifier.size(18.dp)
            )
            Text(phase.name, fontSize = 15.sp, color = if (isAttached) TextSecondaryColor else TextPrimaryColor, modifier = Modifier.weight(1f))
            if (isAttached) Text("Added", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
            else if (!phase.isSystem) PickerBadge("Custom", BlueTheme)
            if (onRename != null) Icon(Icons.Filled.Edit, contentDescription = "Rename", tint = MediumGray, modifier = Modifier.size(18.dp).clickable(onClick = onRename))
            if (onDelete != null) Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MediumGray, modifier = Modifier.size(18.dp).clickable(onClick = onDelete))
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}
