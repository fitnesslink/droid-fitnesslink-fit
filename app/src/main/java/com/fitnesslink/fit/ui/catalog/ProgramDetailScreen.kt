package com.fitnesslink.fit.ui.catalog

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.api.ShareableContentKind
import com.fitnesslink.fit.programs.ProgramCTA
import com.fitnesslink.fit.ui.components.BackCircleButton
import com.fitnesslink.fit.ui.components.ContentActionsRow
import com.fitnesslink.fit.ui.components.ContentKind
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.LocationInfoView
import com.fitnesslink.fit.ui.components.PendingShareBanner
import com.fitnesslink.fit.ui.components.PrimaryButtonView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.components.ShareSheet
import com.fitnesslink.fit.ui.components.SharedBadge
import com.fitnesslink.fit.ui.components.TimeInfoView
import com.fitnesslink.fit.ui.components.TrainingLevelInfoView
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.navigation.ProgramRoutes
import com.fitnesslink.fit.ui.programs.ProgramAudienceView
import com.fitnesslink.fit.ui.programs.ProgramContributorCard
import com.fitnesslink.fit.ui.programs.ProgramScheduleSection
import com.fitnesslink.fit.ui.programs.SneakPeekReel
import com.fitnesslink.fit.ui.programs.SneakPeekStrip
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.ui.workout.ContentTagChipRow
import com.fitnesslink.fit.ui.workout.ContentTagKind
import com.fitnesslink.fit.viewmodel.MovementPreview
import com.fitnesslink.fit.viewmodel.ProgramDetailViewModel
import kotlinx.coroutines.launch

/** Temporarily hides "Muscles Worked" on program details (product request, 2026-09-19); mirrors iOS. */
private const val SHOW_MUSCLES_WORKED = false

/**
 * Program Detail (iOS `ProgramDetailView`): hero + ownership-gated header
 * actions, shared badge / pending-share banner, level · time · location
 * chips, Sneak Peek reel, Overview, "Who This Program Is For", Equipment
 * Needed, the week-picker schedule section, "About the Coach" and the
 * state-aware CTA (`ProgramCTA`). Android keeps the favorite heart + OS
 * share (`ContentActionsRow`) as extras.
 *
 * @param onNavigate pushes a route string (CTA targets); defaults to `AppRouter.push`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgramDetailScreen(
    programId: String,
    onBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit = {},
    onNavigate: ((String) -> Unit)? = null
) {
    val viewModel: ProgramDetailViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val navigate: (String) -> Unit = onNavigate ?: { AppRouter.push(it) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showRemoveFromLibraryAlert by remember { mutableStateOf(false) }
    var removeFromLibraryFailed by remember { mutableStateOf(false) }
    var shareResponseFailed by remember { mutableStateOf(false) }
    var reelStart by remember { mutableStateOf<MovementPreview?>(null) }

    LaunchedEffect(programId) { viewModel.loadData(programId) }
    // Starting / removing happens on other screens — re-read on every appearance.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, programId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { viewModel.reloadFromCache(programId) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val program = viewModel.program

    // MARK: Dialogs

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text("Delete Program?") },
            text = { Text("This will permanently delete this program and its schedule.") },
            confirmButton = {
                TextButton(onClick = { showDeleteAlert = false; viewModel.deleteProgram(programId); onBack() }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAlert = false }) { Text("Cancel") } }
        )
    }
    if (showRemoveFromLibraryAlert) {
        AlertDialog(
            onDismissRequest = { showRemoveFromLibraryAlert = false },
            title = { Text("Remove from your library?") },
            text = { Text("This removes the shared program from your library. It won't delete the original or affect who shared it.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveFromLibraryAlert = false
                    scope.launch { if (viewModel.removeFromLibrary(programId)) onBack() else removeFromLibraryFailed = true }
                }) { Text("Remove", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showRemoveFromLibraryAlert = false }) { Text("Cancel") } }
        )
    }
    if (shareResponseFailed) {
        AlertDialog(
            onDismissRequest = { shareResponseFailed = false },
            title = { Text("Couldn't update") },
            text = { Text("Something went wrong responding to this share. Please try again.") },
            confirmButton = { TextButton(onClick = { shareResponseFailed = false }) { Text("OK") } }
        )
    }
    if (removeFromLibraryFailed) {
        AlertDialog(
            onDismissRequest = { removeFromLibraryFailed = false },
            title = { Text("Couldn't remove") },
            text = { Text("Something went wrong removing this program. Please try again.") },
            confirmButton = { TextButton(onClick = { removeFromLibraryFailed = false }) { Text("OK") } }
        )
    }
    if (showShareSheet) {
        ShareSheet(contentId = programId, kind = ShareableContentKind.PROGRAM, onDismiss = { showShareSheet = false })
    }
    reelStart?.let { start ->
        SneakPeekReel(previews = viewModel.previewMovements, startId = start.id, onDismiss = { reelStart = null })
    }

    // MARK: Body

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        Box {
            FLImageView(ref = (program.thumbnailId ?: program.imageId)?.let { MediaRef.ProgramThumbnail(it.toString()) }, height = 250.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 60.dp, start = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackCircleButton(onClick = onBack)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (program.name.isNotEmpty()) {
                        when {
                            // Templates (contributor-attributed) are read-only; only the creator edits / deletes / shares.
                            viewModel.isOwner -> {
                                HeaderCircle(Icons.Filled.People, "Share") { showShareSheet = true }
                                HeaderCircle(Icons.Filled.Edit, "Edit") { onNavigateToEditor(programId) }
                                HeaderCircle(Icons.Filled.Delete, "Delete") { showDeleteAlert = true }
                            }
                            viewModel.isSharedIn && viewModel.pendingShare == null -> {
                                HeaderCircle(Icons.Filled.PersonRemove, "Remove from library") { showRemoveFromLibraryAlert = true }
                            }
                        }
                        // Android extras: favorite heart + OS share chooser.
                        ContentActionsRow(contentId = program.id, contentKind = ContentKind.Program, contentTitle = program.name)
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (viewModel.isLoading && program.name.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FLPrimary)
                }
            }
            Text(text = program.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            if (program.isSharedWithMe) SharedBadge(sharedByName = viewModel.pendingShare?.sharedByUserName)

            viewModel.pendingShare?.let { share ->
                PendingShareBanner(
                    isBusy = viewModel.isRespondingToShare,
                    share = share,
                    kindLabel = "program",
                    onAccept = { scope.launch { if (!viewModel.respondToShare(programId, true)) shareResponseFailed = true } },
                    onDecline = { scope.launch { if (viewModel.respondToShare(programId, false)) onBack() else shareResponseFailed = true } }
                )
            }

            val levels = program.trainingLevels.ifEmpty { listOf(program.trainingLevel) }.filter { it.isNotEmpty() }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                levels.forEach { TrainingLevelInfoView(level = it) }
                if (program.time.isNotEmpty()) TimeInfoView(time = program.time)
                if (program.location.isNotEmpty()) LocationInfoView(location = program.location)
            }

            if (viewModel.previewMovements.isNotEmpty()) {
                SneakPeekStrip(previews = viewModel.previewMovements, onOpen = { reelStart = it })
            }

            if (program.description.isNotEmpty()) {
                DetailSection("Overview") { Text(program.description, fontSize = 14.sp, color = TextSecondaryColor) }
            }

            if (program.whoIsFor.isNotEmpty() || program.bestFor.isNotEmpty()) {
                DetailSection("Who This Program Is For") { ProgramAudienceView(whoIsFor = program.whoIsFor, bestFor = program.bestFor) }
            }

            // Equipment (+ muscles worked, hidden) from the effective tag ids on the detail payload — live-only.
            if (viewModel.equipmentNames.isNotEmpty() || (SHOW_MUSCLES_WORKED && viewModel.anatomyNames.isNotEmpty())) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ContentTagChipRow(title = "Equipment Needed", names = viewModel.equipmentNames, icon = ContentTagKind.EQUIPMENT.icon)
                    if (SHOW_MUSCLES_WORKED) {
                        ContentTagChipRow(title = "Muscles Worked", names = viewModel.anatomyNames, icon = ContentTagKind.ANATOMY.icon)
                    }
                }
            }

            if (viewModel.scheduleWeeks.isNotEmpty()) {
                ProgramScheduleSection(weeks = viewModel.scheduleWeeks, workoutInfo = viewModel.scheduleWorkoutInfo)
            }

            val contributor = program.contributor
            if (contributor != null && !ContributorStore.isHouse(program.contributorId?.toString())) {
                ProgramContributorCard(contributor)
            }
        }

        // A share still awaiting accept / decline is preview-only.
        if (viewModel.pendingShare == null && program.name.isNotEmpty()) {
            ProgramActionBar(
                programId = programId,
                cta = viewModel.cta,
                // Starting needs the schedule; it lands with the first refresh.
                isEnabled = viewModel.cta != ProgramCTA.Start || viewModel.scheduleWeeks.isNotEmpty(),
                onNavigate = navigate
            )
        }
    }
}

/** Primary CTA by program state (iOS `ProgramActionView`). */
@Composable
private fun ProgramActionBar(programId: String, cta: ProgramCTA, isEnabled: Boolean, onNavigate: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(White).padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SeparatorView()
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .alpha(if (isEnabled) 1f else 0.5f)
                .clickable(enabled = isEnabled) { onNavigate(cta.route(programId)) }
        ) { PrimaryButtonView(text = cta.title) }
        when (cta) {
            ProgramCTA.Start -> Text("Choose your start date and we'll schedule your workouts.", fontSize = 12.sp, color = TextSecondaryColor)
            is ProgramCTA.StartTodaysWorkout -> Text(
                "View My Program",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary,
                modifier = Modifier.clickable { onNavigate(ProgramRoutes.myProgram(cta.enrollmentId)) }.padding(4.dp)
            )
            else -> Unit
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(16.dp)) { content() }
    }
}

@Composable
private fun HeaderCircle(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = White, modifier = Modifier.size(16.dp))
    }
}
