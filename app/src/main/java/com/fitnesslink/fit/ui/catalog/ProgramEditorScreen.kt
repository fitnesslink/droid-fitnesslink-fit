package com.fitnesslink.fit.ui.catalog

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.programs.WeekSectionView
import com.fitnesslink.fit.ui.programs.WorkoutPickerSheet
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.ui.workout.ContentTagKind
import com.fitnesslink.fit.ui.workout.ContentTagsEditorSection
import com.fitnesslink.fit.ui.workout.WorkoutEditorScreen
import com.fitnesslink.fit.viewmodel.ProgramEditorViewModel

/**
 * Program editor (iOS `ProgramEditorView`): image picker, name, time
 * chips, multi-select fitness level (+ "· from workouts" hint), location
 * chips, equipment / anatomy tag editors, description, weeks with 7-day
 * slots (picker / replace / move / remove), summary chips. Save is always
 * enabled (iOS). Template programs (`contributorId != null`) are read-only —
 * the editor closes itself if one is opened.
 *
 * @param onSaved fires with the program id after the local write (calendar's "Create New Program" lays it on the tapped date).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgramEditorScreen(
    programId: String?,
    onBack: () -> Unit,
    onSaved: ((String) -> Unit)? = null
) {
    val viewModel: ProgramEditorViewModel = viewModel()
    var showDiscardAlert by remember { mutableStateOf(false) }
    /** (week, day) captured when "Create New Workout" was tapped, so the assignment lands on the slot the user picked. */
    var createTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.pickImage(it) }
    }

    LaunchedEffect(programId) {
        if (programId != null) {
            viewModel.loadExistingProgram(programId)
            // Template guard: contributor programs are read-only (iOS backstop).
            if (viewModel.program.contributorId != null) onBack()
        } else {
            viewModel.createNewProgram()
        }
    }

    fun close() { if (viewModel.isDirty) showDiscardAlert = true else onBack() }
    BackHandler { close() }

    // "Create New Workout" from the picker: full-screen workout editor; its onSaved assigns the slot.
    createTarget?.let { (week, day) ->
        WorkoutEditorScreen(
            workoutId = null,
            onBack = { createTarget = null },
            onSaved = { workoutId ->
                viewModel.assignWorkoutById(workoutId, week, day)
                createTarget = null
                viewModel.showWorkoutPicker = false
            }
        )
        return
    }

    if (viewModel.showWorkoutPicker) {
        WorkoutPickerSheet(
            viewModel = viewModel,
            onCreateNew = { createTarget = viewModel.targetWeekNumber to viewModel.targetDayNumber },
            onDismiss = { viewModel.showWorkoutPicker = false }
        )
    }

    if (showDiscardAlert) {
        AlertDialog(
            onDismissRequest = { showDiscardAlert = false },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved changes that will be lost.") },
            confirmButton = { TextButton(onClick = { showDiscardAlert = false; onBack() }) { Text("Discard", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { showDiscardAlert = false }) { Text("Keep Editing") } }
        )
    }
    viewModel.saveErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.saveErrorMessage = null },
            title = { Text("Couldn't save") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.saveErrorMessage = null }) { Text("OK") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { close() }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimaryColor) }
            Spacer(modifier = Modifier.weight(1f))
            Text(if (viewModel.isNewProgram) "New Program" else "Edit Program", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Spacer(modifier = Modifier.weight(1f))
            // Save is always enabled (iOS) — a program with no schedule is still a valid draft.
            TextButton(
                enabled = !viewModel.isSaving,
                onClick = {
                    viewModel.save { id ->
                        onSaved?.invoke(id)
                        onBack()
                    }
                }
            ) {
                if (viewModel.isSaving) CircularProgressIndicator(color = FLPrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                else Text("Save", color = FLPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProgramHeaderSection(viewModel, onPickImage = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })

            viewModel.weeks.forEach { week -> WeekSectionView(week = week, viewModel = viewModel) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(FLPrimary.copy(alpha = 0.08f))
                    .clickable { viewModel.addWeek() }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Week", color = FLPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            if (viewModel.assignedWorkoutCount > 0) {
                Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SummaryChip(Icons.Default.DateRange, "${viewModel.weeks.size} week${if (viewModel.weeks.size == 1) "" else "s"}")
                    SummaryChip(Icons.Default.FitnessCenter, "${viewModel.assignedWorkoutCount} workout${if (viewModel.assignedWorkoutCount == 1) "" else "s"}")
                }
            }
        }
    }
}

// MARK: - Program Header

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramHeaderSection(viewModel: ProgramEditorViewModel, onPickImage: () -> Unit) {
    val program = viewModel.program
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Program image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BackgroundColor)
                .clickable(onClick = onPickImage),
            contentAlignment = Alignment.Center
        ) {
            val localUri = viewModel.localImageUri
            val remoteId = (program.thumbnailId ?: program.imageId)?.toString()
            when {
                localUri != null -> AsyncImage(model = localUri, contentDescription = "Program image", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                remoteId != null -> FLImageView(ref = MediaRef.ProgramThumbnail(remoteId), modifier = Modifier.fillMaxSize())
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MediumGray, modifier = Modifier.size(28.dp))
                    Text("Add Program Image", fontSize = 13.sp, color = MediumGray)
                }
            }
            if (localUri != null || remoteId != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    if (viewModel.isUploadingImage) CircularProgressIndicator(color = White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    else Icon(Icons.Default.Edit, contentDescription = "Change image", tint = White, modifier = Modifier.size(24.dp))
                }
            }
        }

        OutlinedTextField(
            value = program.name,
            onValueChange = viewModel::setName,
            placeholder = { Text("Program Name", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Estimated time
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(Icons.Default.Schedule, "Estimated Time per Workout")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ProgramEditorViewModel.timePresets.forEach { minutes ->
                    Chip(label = "$minutes", selected = program.time == "$minutes min") { viewModel.setTime(minutes) }
                }
                Text("min", fontSize = 12.sp, color = TextSecondaryColor)
            }
        }

        // Fitness level — multi-select; the server derives from the scheduled workouts when there are none.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(
                Icons.Default.Speed, "Fitness Level",
                hint = if (program.trainingLevels.isNotEmpty() && !program.hasExplicitTrainingLevels) "· from workouts" else null
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgramEditorViewModel.trainingLevelOptions.forEach { level ->
                    Chip(label = level, selected = level in program.trainingLevels) { viewModel.toggleTrainingLevel(level) }
                }
            }
        }

        // Location — single-select; tapping the selected chip clears it back to "derived".
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(
                Icons.Default.Place, "Location",
                hint = if (program.location.isNotEmpty() && !program.hasExplicitLocation) "· from workouts" else null
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgramEditorViewModel.locationOptions.forEach { location ->
                    Chip(label = location, selected = program.location == location) { viewModel.toggleLocation(location) }
                }
            }
        }

        // Explicit equipment / muscles-worked tags — same state machine as the workout editor.
        ContentTagsEditorSection(
            kind = ContentTagKind.EQUIPMENT,
            selection = viewModel.equipmentTags,
            revision = viewModel.tagsRevision,
            onSelect = viewModel::selectEquipmentTags,
            onReset = viewModel::resetEquipmentTags
        )
        ContentTagsEditorSection(
            kind = ContentTagKind.ANATOMY,
            selection = viewModel.anatomyTags,
            revision = viewModel.tagsRevision,
            onSelect = viewModel::selectAnatomyTags,
            onReset = viewModel::resetAnatomyTags
        )

        OutlinedTextField(
            value = program.description,
            onValueChange = viewModel::setDescription,
            placeholder = { Text("Description (optional)") },
            textStyle = TextStyle(fontSize = 14.sp, color = TextSecondaryColor),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent),
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FieldLabel(icon: ImageVector, text: String, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 13.sp, color = TextSecondaryColor)
        if (hint != null) Text(hint, fontSize = 12.sp, color = TextSecondaryColor)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) FLPrimary else BackgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected) White else TextPrimaryColor)
    }
}

@Composable
private fun SummaryChip(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(FLPrimary.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
    }
}
