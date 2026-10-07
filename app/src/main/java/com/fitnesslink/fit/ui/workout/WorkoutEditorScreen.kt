package com.fitnesslink.fit.ui.workout

import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.ExerciseGroupType
import com.fitnesslink.fit.model.WorkoutPhase
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.navigation.AppRoute
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.WorkoutEditorViewModel
import com.fitnesslink.fit.workouts.WorkoutRowOps
import com.fitnesslink.fit.workouts.WorkoutStore
import kotlinx.coroutines.launch

/**
 * Workout builder (iOS `WorkoutEditorView`). Header card (image, Generate
 * with AI, name, estimated time, levels, location, explicit tags,
 * description), one section per phase, Add Phase, multi-select bar and the
 * save-error banner. All mutations go through [WorkoutEditorViewModel].
 *
 * @param seedPrompt opens the Generate sheet pre-filled (new workouts only)
 * @param initialMovementId drops this movement into the main phase of a new workout
 * @param onSaved sheet presenters that need the saved workout's id (fires on Save & Close)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutEditorScreen(
    workoutId: String?,
    onBack: () -> Unit,
    seedPrompt: String? = null,
    initialMovementId: String? = null,
    onSaved: ((String) -> Unit)? = null
) {
    val viewModel: WorkoutEditorViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val workout = viewModel.workout

    var showDiscardAlert by remember { mutableStateOf(false) }
    var showAddPhase by remember { mutableStateOf(false) }
    var showAddRest by remember { mutableStateOf(false) }
    var showGenerate by remember { mutableStateOf(false) }
    var didAutoOpenGenerate by remember { mutableStateOf(false) }
    var showSaveMenu by remember { mutableStateOf(false) }
    var customTime by remember { mutableStateOf("") }
    var renamePhaseIndex by remember { mutableStateOf<Int?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var saveTemplateIndex by remember { mutableStateOf<Int?>(null) }
    var saveTemplateName by remember { mutableStateOf("") }
    var templateResult by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showForkPrompt by remember { mutableStateOf(false) }
    var forkFailedMessage by remember { mutableStateOf<String?>(null) }
    var isForking by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.pickImage(it) }
    }

    fun seedCustomTime() {
        val minutes = (workout.estimatedTime ?: 0) / 60
        customTime = if (minutes > 0 && !WorkoutEditorViewModel.estimatedTimePresets.contains(minutes)) "$minutes" else ""
    }

    LaunchedEffect(workoutId) {
        if (workoutId != null) {
            viewModel.loadExistingWorkout(workoutId)
            if (viewModel.isReadOnlySource) { showForkPrompt = true; return@LaunchedEffect }
        } else {
            viewModel.createNewWorkout()
            initialMovementId?.let { viewModel.prefillInitialMovement(it) }
            if (seedPrompt != null && !didAutoOpenGenerate) { didAutoOpenGenerate = true; showGenerate = true }
        }
        seedCustomTime()
    }

    fun performSave(closeAfter: Boolean) {
        if (viewModel.isSaving) return
        val wasNew = viewModel.isNewWorkout
        val job = viewModel.save()
        if (!closeAfter) return
        scope.launch {
            if (onSaved != null) {
                job.join()
                onSaved(viewModel.workout.id)
                onBack()
                return@launch
            }
            if (!wasNew) { onBack(); return@launch }
            job.join()
            if (viewModel.saveErrorIsFatal) return@launch
            if (!AppRouter.replaceTop(AppRoute.WorkoutDetail.createRoute(viewModel.workout.id))) onBack()
        }
    }

    // MARK: Sheets

    if (viewModel.showExerciseBrowser) {
        ExerciseBrowserSheet(
            onSelect = { viewModel.addExercise(it, viewModel.activePhaseIndex) },
            onCreateNew = { viewModel.showExerciseBrowser = false; viewModel.showCreateExercise = true },
            onDismiss = { viewModel.showExerciseBrowser = false; viewModel.finalizePendingGroup() },
            pendingGroupType = viewModel.pendingGroupType,
            pendingCount = viewModel.pendingGroupTaskIds.size,
            onDone = { viewModel.showExerciseBrowser = false; viewModel.finalizePendingGroup() }
        )
    }
    if (viewModel.showCreateExercise) {
        CreateExerciseSheet(
            onCreated = { viewModel.addExercise(it, viewModel.activePhaseIndex); viewModel.showCreateExercise = false },
            onDismiss = { viewModel.showCreateExercise = false }
        )
    }
    viewModel.replaceExerciseTaskId?.let { targetId ->
        ExerciseBrowserSheet(
            onSelect = { viewModel.replaceMovement(targetId, it); viewModel.replaceExerciseTaskId = null },
            onCreateNew = {},
            onDismiss = { viewModel.replaceExerciseTaskId = null },
            pendingGroupType = null, pendingCount = 0,
            mode = BrowserMode.Single("Replace Exercise")
        )
    }
    // Recovery picker for loose rest rows (interval leads present it from inside IntervalEditSheet).
    viewModel.restMovementPickerTaskId?.let { targetId ->
        val isIntervalLead = workout.phases.any { p -> p.taskRows.any { it.isInterval && it.task?.id == targetId } }
        if (!isIntervalLead) {
            ExerciseBrowserSheet(
                onSelect = { viewModel.updateRestMovement(targetId, it); viewModel.restMovementPickerTaskId = null },
                onCreateNew = {},
                onDismiss = { viewModel.restMovementPickerTaskId = null },
                pendingGroupType = null, pendingCount = 0,
                mode = BrowserMode.Single("Recovery Exercise")
            )
        }
    }
    if (viewModel.showIntervalModePicker) {
        IntervalModePickerSheet(
            onPick = { viewModel.completeIntervalModePick(it) },
            onCancel = { viewModel.cancelPendingGroup() }
        )
    }
    if (showAddPhase) {
        PhasePickerSheet(
            alreadyAttachedIds = viewModel.attachedPhaseIds,
            onPick = { viewModel.addPhase(it) },
            onPickTemplate = { template -> scope.launch { viewModel.insertPhaseTemplate(template) } },
            onDismiss = { showAddPhase = false }
        )
    }
    if (showAddRest) {
        RestDurationPickerSheet(
            initialSeconds = 60, title = "Add Rest", confirmLabel = "Add",
            onConfirm = { viewModel.addRestToPhase(viewModel.activePhaseIndex, it); showAddRest = false },
            onCancel = { showAddRest = false }
        )
    }
    if (showGenerate) {
        GenerateWorkoutSheet(
            seedPrompt = seedPrompt,
            onApprove = { viewModel.applyGenerated(it); seedCustomTime(); showGenerate = false },
            onDismiss = { showGenerate = false }
        )
    }

    // MARK: Alerts

    if (showDiscardAlert) {
        AlertDialog(
            onDismissRequest = { showDiscardAlert = false },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved changes that will be lost.") },
            confirmButton = { TextButton(onClick = { showDiscardAlert = false; onBack() }) { Text("Discard", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { showDiscardAlert = false }) { Text("Keep Editing") } }
        )
    }
    renamePhaseIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { renamePhaseIndex = null },
            title = { Text("Rename Phase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = renameDraft, onValueChange = { renameDraft = it }, singleLine = true, label = { Text("Phase name") })
                    Text("Give this phase a name that describes it, e.g. \"Main Set\" or \"Finisher\". The new name applies everywhere you use this phase.", fontSize = 12.sp, color = TextSecondaryColor)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = renameDraft.trim()
                    renamePhaseIndex = null
                    if (name.isNotEmpty()) scope.launch { viewModel.renamePhase(index, name) }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renamePhaseIndex = null }) { Text("Cancel") } }
        )
    }
    viewModel.phaseRenameError?.let { msg -> InfoAlert("Couldn't Rename Phase", msg) { viewModel.phaseRenameError = null } }
    saveTemplateIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { saveTemplateIndex = null },
            title = { Text("Save as Template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = saveTemplateName, onValueChange = { saveTemplateName = it }, singleLine = true, label = { Text("Template name") })
                    Text("Saves this phase and its exercises as a reusable template you can add to any workout.", fontSize = 12.sp, color = TextSecondaryColor)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = saveTemplateName.trim()
                    saveTemplateIndex = null
                    if (name.isNotEmpty()) scope.launch {
                        templateResult = try {
                            viewModel.savePhaseAsTemplate(index, name)
                            "Template Saved" to "\"$name\" was added to My Templates. You'll find it in the Add Phase picker."
                        } catch (e: Exception) {
                            "Couldn't Save Template" to WorkoutStore.templateSaveFailureMessage(e)
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saveTemplateIndex = null }) { Text("Cancel") } }
        )
    }
    templateResult?.let { (title, message) -> InfoAlert(title, message) { templateResult = null } }
    if (showForkPrompt) {
        AlertDialog(
            onDismissRequest = { showForkPrompt = false; onBack() },
            title = { Text("Customize This Workout?") },
            text = { Text("This workout is read-only. We'll save your own editable copy — the original stays unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    showForkPrompt = false
                    val sourceId = workoutId ?: return@TextButton
                    scope.launch {
                        isForking = true
                        try {
                            val forked = WorkoutStore.customizeFork(sourceId)
                            viewModel.loadExistingWorkout(forked.id)
                            seedCustomTime()
                        } catch (e: Exception) {
                            forkFailedMessage = WorkoutStore.forkFailureMessage(e)
                        } finally {
                            isForking = false
                        }
                    }
                }) { Text("Create My Copy") }
            },
            dismissButton = { TextButton(onClick = { showForkPrompt = false; onBack() }) { Text("Cancel") } }
        )
    }
    forkFailedMessage?.let { msg -> InfoAlert("Couldn't Customize", msg) { forkFailedMessage = null; onBack() } }

    // MARK: Body

    Box(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextPrimaryColor,
                    modifier = Modifier.size(22.dp).clickable { if (viewModel.isDirty) showDiscardAlert = true else onBack() })
                Spacer(modifier = Modifier.weight(1f))
                Text(if (viewModel.isNewWorkout) "New Workout" else "Edit Workout", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                if (viewModel.isSaving || isForking) {
                    Text("Saving…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary.copy(alpha = 0.6f))
                } else {
                    Box {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showSaveMenu = true }) {
                            Text("Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showSaveMenu, onDismissRequest = { showSaveMenu = false }) {
                            DropdownMenuItem(text = { Text("Save") }, onClick = { showSaveMenu = false; performSave(closeAfter = false) })
                            DropdownMenuItem(text = { Text("Save & Close") }, onClick = { showSaveMenu = false; performSave(closeAfter = true) })
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WorkoutHeaderCard(
                    viewModel = viewModel,
                    customTime = customTime,
                    onCustomTimeChange = { raw ->
                        val digits = raw.filter(Char::isDigit)
                        customTime = digits
                        viewModel.setEstimatedMinutes(digits.toIntOrNull())
                    },
                    onPresetTime = { customTime = ""; viewModel.setEstimatedMinutes(it) },
                    onPickImage = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onGenerate = { showGenerate = true }
                )

                workout.phases.forEachIndexed { index, phase ->
                    PhaseSection(
                        phase = phase, index = index, viewModel = viewModel,
                        onRename = { renamePhaseIndex = index; renameDraft = phase.name },
                        onSaveAsTemplate = { saveTemplateIndex = index; saveTemplateName = phase.name },
                        onAddRest = { viewModel.activePhaseIndex = index; showAddRest = true }
                    )
                }

                Row(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(FLPrimary.copy(alpha = 0.08f)).clickable { showAddPhase = true }.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AddCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Phase", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                }
                Spacer(modifier = Modifier.height(80.dp))
            }

            if (viewModel.isMultiSelectMode) MultiSelectBar(viewModel)
        }

        viewModel.saveErrorMessage?.let { message ->
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Red.copy(alpha = 0.92f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                Text(message, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = White, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = White,
                    modifier = Modifier.size(22.dp).clip(CircleShape).background(White.copy(alpha = 0.15f)).clickable { viewModel.saveErrorMessage = null }.padding(4.dp))
            }
        }
    }
}

@Composable
private fun InfoAlert(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

// MARK: - Header card

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutHeaderCard(
    viewModel: WorkoutEditorViewModel,
    customTime: String,
    onCustomTimeChange: (String) -> Unit,
    onPresetTime: (Int) -> Unit,
    onPickImage: () -> Unit,
    onGenerate: () -> Unit
) {
    val workout = viewModel.workout
    val minutes = (workout.estimatedTime ?: 0) / 60
    val isCustomSelected = minutes > 0 && !WorkoutEditorViewModel.estimatedTimePresets.contains(minutes)

    Column(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Image
        Box(modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(BackgroundColor).clickable(onClick = onPickImage)) {
            val localUri = viewModel.localImageUri
            val imageId = workout.imageId
            when {
                localUri != null -> AsyncImage(model = localUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                imageId != null -> FLImageView(ref = MediaRef.WorkoutThumbnail(imageId.toString()), height = 160.dp)
                else -> Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = MediumGray, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Add Workout Image", fontSize = 13.sp, color = MediumGray)
                }
            }
            if (localUri != null || imageId != null) {
                Icon(Icons.Filled.Edit, contentDescription = "Change image", tint = White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).padding(6.dp))
            }
            if (viewModel.isUploadingImage) {
                CircularProgressIndicator(color = White, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center).size(28.dp))
            }
        }

        // Generate with AI
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PurpleTheme.copy(alpha = 0.08f)).clickable(onClick = onGenerate)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = PurpleTheme, modifier = Modifier.size(18.dp))
            Text("Generate with AI", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PurpleTheme, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = PurpleTheme.copy(alpha = 0.6f))
        }

        OutlinedTextField(
            value = workout.name, onValueChange = { viewModel.updateName(it) },
            placeholder = { Text("Workout Name", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold), singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Estimated time
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Estimated Time", fontSize = 13.sp, color = TextSecondaryColor)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutEditorViewModel.estimatedTimePresets.forEach { m -> EditorChip("$m", minutes == m, onClick = { onPresetTime(m) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = customTime, onValueChange = onCustomTimeChange,
                    placeholder = { Text("Custom", fontSize = 13.sp, textAlign = TextAlign.Center) }, singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = if (isCustomSelected) FLPrimary else TextPrimaryColor),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp)
                )
                Text("min", fontSize = 12.sp, color = TextSecondaryColor)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Training Level", fontSize = 13.sp, color = TextSecondaryColor)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutEditorViewModel.trainingLevelOptions.forEach { level ->
                    EditorChip(level, workout.trainingLevels.contains(level), onClick = { viewModel.toggleTrainingLevel(level) })
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Location", fontSize = 13.sp, color = TextSecondaryColor)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutEditorViewModel.locationOptions.forEach { location ->
                    val selected = workout.location == location
                    EditorChip(location, selected, onClick = { viewModel.setLocation(if (selected) null else location) })
                }
            }
        }

        ContentTagsEditorSection(ContentTagKind.EQUIPMENT, viewModel.equipmentTags, viewModel.tagsRevision, viewModel::selectEquipmentTags, viewModel::resetEquipmentTags)
        ContentTagsEditorSection(ContentTagKind.ANATOMY, viewModel.anatomyTags, viewModel.tagsRevision, viewModel::selectAnatomyTags, viewModel::resetAnatomyTags)

        OutlinedTextField(
            value = workout.description, onValueChange = { viewModel.updateDescription(it) },
            placeholder = { Text("Description (optional)", fontSize = 14.sp) },
            textStyle = TextStyle(fontSize = 14.sp), minLines = 2, maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// MARK: - Phase section

@Composable
private fun PhaseSection(
    phase: WorkoutPhase, index: Int, viewModel: WorkoutEditorViewModel,
    onRename: () -> Unit, onSaveAsTemplate: () -> Unit, onAddRest: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showMenu by remember(phase.id) { mutableStateOf(false) }
    val exerciseCount = phase.allTasks.count { it.isMovement }
    val phaseCount = viewModel.workout.phases.size

    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!phase.isSystem) Icon(Icons.Filled.Label, contentDescription = null, tint = BlueTheme, modifier = Modifier.size(12.dp))
            Text(
                phase.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FLPrimary,
                modifier = if (phase.isSystem) Modifier else Modifier.clickable(onClick = onRename)
            )
            if (!phase.isSystem) Icon(Icons.Filled.Edit, contentDescription = "Rename", tint = MediumGray, modifier = Modifier.size(12.dp).clickable(onClick = onRename))
            if (exerciseCount > 0) {
                Text("$exerciseCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(FLPrimary).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            Box {
                Icon(Icons.Filled.MoreVert, contentDescription = "Phase actions", tint = MediumGray, modifier = Modifier.size(22.dp).clickable { showMenu = true })
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Duplicate Phase") }, onClick = { showMenu = false; scope.launch { viewModel.duplicatePhase(index) } })
                    if (phase.taskRows.isNotEmpty()) DropdownMenuItem(text = { Text("Save as Template") }, onClick = { showMenu = false; onSaveAsTemplate() })
                    if (index > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { showMenu = false; viewModel.movePhase(index, up = true) })
                    if (index < phaseCount - 1) DropdownMenuItem(text = { Text("Move down") }, onClick = { showMenu = false; viewModel.movePhase(index, up = false) })
                }
            }
            if (phase.taskRows.isEmpty()) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove phase", tint = MediumGray, modifier = Modifier.size(20.dp).clickable { viewModel.deletePhase(index) })
            }
        }

        phase.taskRows.forEach { row ->
            val task = row.task ?: return@forEach
            when {
                row.advancedTasks.isNotEmpty() || row.isInterval -> GroupEditorView(row = row, phaseIndex = index, viewModel = viewModel)
                task.isMovement -> MovementEditorRow(task = task, phaseIndex = index, isSelected = viewModel.selectedTaskIds.contains(task.id), viewModel = viewModel)
                task.isRest -> RestEditorRow(task = task, phaseIndex = index, viewModel = viewModel)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill("+ Add Exercise", FLPrimary, FLPrimary.copy(alpha = 0.06f), Modifier.weight(1f)) {
                viewModel.activePhaseIndex = index
                viewModel.cancelPendingGroup()
                viewModel.showExerciseBrowser = true
            }
            ActionPill("Add Rest", TextSecondaryColor, MediumGray.copy(alpha = 0.12f), Modifier.weight(1f), onClick = onAddRest)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupAddButton("Superset", PurpleTheme) { viewModel.activePhaseIndex = index; viewModel.pendingGroupType = ExerciseGroupType.Superset; viewModel.showExerciseBrowser = true }
            GroupAddButton("Circuit", OrangeTheme) { viewModel.activePhaseIndex = index; viewModel.pendingGroupType = ExerciseGroupType.Circuit; viewModel.showExerciseBrowser = true }
            GroupAddButton("Interval", BlueTheme) { viewModel.beginIntervalSetup(index) }
        }
    }
}

@Composable
private fun ActionPill(label: String, color: Color, background: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color, textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(background).clickable(onClick = onClick).padding(vertical = 10.dp)
    )
}

@Composable
private fun GroupAddButton(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

// MARK: - Multi-select bar

@Composable
private fun MultiSelectBar(viewModel: WorkoutEditorViewModel) {
    val count = viewModel.selectedTaskIds.size
    Column(modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$count selected", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor, modifier = Modifier.weight(1f))
            Text("Cancel", fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.clickable { viewModel.cancelMultiSelect() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupPill("Superset", count >= WorkoutRowOps.minimumGroupCount(ExerciseGroupType.Superset), Modifier.weight(1f)) { viewModel.groupSelection(ExerciseGroupType.Superset) }
            GroupPill("Circuit", count >= WorkoutRowOps.minimumGroupCount(ExerciseGroupType.Circuit), Modifier.weight(1f)) { viewModel.groupSelection(ExerciseGroupType.Circuit) }
            GroupPill("Interval", count >= WorkoutRowOps.minimumGroupCount(ExerciseGroupType.Interval), Modifier.weight(1f)) { viewModel.beginIntervalFromSelection() }
        }
    }
}

@Composable
private fun GroupPill(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White, textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(FLPrimary.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 11.dp)
    )
}
