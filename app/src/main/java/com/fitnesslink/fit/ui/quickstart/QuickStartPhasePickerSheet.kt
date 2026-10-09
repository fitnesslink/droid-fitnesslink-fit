package com.fitnesslink.fit.ui.quickstart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.api.AvailableWorkoutPhase
import com.fitnesslink.fit.session.QuickStartSuggestions
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.workouts.WorkoutPhaseStore
import kotlinx.coroutines.launch

/**
 * One of the standard phases the Quick Start picker offers (PRD §12), with
 * the copy and glyph the canvas reuses for its phase headers.
 */
data class QuickStartPhaseOption(val name: String, val subtitle: String, val icon: ImageVector, val color: Color) {
    companion object {
        val standard: List<QuickStartPhaseOption> = listOf(
            QuickStartPhaseOption("Warm-Up", "Mobility, activation, light movement", Icons.Filled.LocalFireDepartment, OrangeTheme),
            QuickStartPhaseOption("Strength", "Main strength or resistance training", Icons.Filled.FitnessCenter, PurpleTheme),
            QuickStartPhaseOption("Exercises", "General exercises / main work", Icons.Filled.DirectionsRun, BlueTheme),
            QuickStartPhaseOption("Core", "Core and midline training", Icons.Filled.SelfImprovement, FLPrimary),
            QuickStartPhaseOption("Conditioning", "Cardio, intervals, circuits", Icons.Filled.Timer, OrangeTheme),
            QuickStartPhaseOption("Cool Down", "Stretching, breathing, recovery", Icons.Filled.Air, PurpleTheme),
            QuickStartPhaseOption("Mobility", "Mobility and flexibility work", Icons.Filled.AccessTime, BlueTheme),
            QuickStartPhaseOption("Recovery", "Restorative movement, rehab", Icons.Filled.Favorite, FLPrimary)
        )

        /** "Warm-Up", "Warmup" and "warm up" name the same phase. */
        fun normalized(name: String): String = QuickStartSuggestions.normalizedPhaseName(name)

        /** The catalog phase an option (or a typed name) maps to, matched loosely ("Warmup" vs "Warm-Up"). */
        fun match(name: String, available: List<AvailableWorkoutPhase>): AvailableWorkoutPhase? {
            val wanted = normalized(name)
            if (wanted.isEmpty()) return null
            return available.firstOrNull { normalized(it.name) == wanted }
        }

        /** Glyph + colour for a phase header by name; custom phases get the generic stack glyph. */
        fun style(name: String): Pair<ImageVector, Color> =
            standard.firstOrNull { normalized(it.name) == normalized(name) }?.let { it.icon to it.color } ?: (Icons.Filled.Layers to FLPrimary)
    }
}

/**
 * Quick Start PRD §12 — Add Phase: the standard phases with a one-line
 * description each, the user's own phases, and a custom phase name. Picks
 * resolve to catalog [AvailableWorkoutPhase] rows (created on the fly when
 * no phase of that name exists yet) so the workout save only ever
 * references phase ids the server knows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickStartPhasePickerSheet(alreadyAttachedIds: Set<String>, onPick: (AvailableWorkoutPhase) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val available by WorkoutPhaseStore.available.collectAsState()
    var customName by remember { mutableStateOf("") }
    var resolvingName by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { WorkoutPhaseStore.refresh() }

    val attachedNames = available.filter { alreadyAttachedIds.contains(it.id.lowercase()) || alreadyAttachedIds.contains(it.id) }.map { QuickStartPhaseOption.normalized(it.name) }.toSet()
    val standardNames = QuickStartPhaseOption.standard.map { QuickStartPhaseOption.normalized(it.name) }.toSet()
    val extraCustomPhases = available.filter { !it.isSystem && !standardNames.contains(QuickStartPhaseOption.normalized(it.name)) }

    fun pick(rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty() || resolvingName != null) return
        errorMessage = null
        QuickStartPhaseOption.match(name, available)?.let { existing ->
            if (alreadyAttachedIds.contains(existing.id)) return
            onPick(existing); onDismiss(); return
        }
        resolvingName = name
        scope.launch {
            try {
                val id = WorkoutPhaseStore.create(name)
                val created = WorkoutPhaseStore.available.value.firstOrNull { it.id.equals(id, true) }
                    ?: AvailableWorkoutPhase(id, name, (available.maxOfOrNull { it.sortOrder } ?: 99) + 1, false)
                onPick(created)
                onDismiss()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Couldn't add the phase."
            } finally {
                resolvingName = null
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BackgroundColor) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add Phase", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
            QuickStartPhaseOption.standard.forEach { option ->
                val attached = attachedNames.contains(QuickStartPhaseOption.normalized(option.name))
                val busy = resolvingName == option.name
                PhaseRow(option.icon, option.color, option.name, option.subtitle, attached, busy) { pick(option.name) }
            }
            if (extraCustomPhases.isNotEmpty()) {
                Text("MY PHASES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryColor, modifier = Modifier.padding(top = 8.dp))
                extraCustomPhases.forEach { phase ->
                    val attached = alreadyAttachedIds.contains(phase.id)
                    PhaseRow(Icons.Filled.Label, FLPrimary, phase.name, null, attached, false) { if (!attached) { onPick(phase); onDismiss() } }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                HorizontalDivider(Modifier.weight(1f), color = MediumGray.copy(alpha = 0.4f))
                Text("OR", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                HorizontalDivider(Modifier.weight(1f), color = MediumGray.copy(alpha = 0.4f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Custom Phase", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                    OutlinedTextField(value = customName, onValueChange = { customName = it }, singleLine = true, placeholder = { Text("e.g. Power, Skill Work, Rehab") }, modifier = Modifier.fillMaxWidth())
                }
                val canAdd = customName.isNotBlank() && resolvingName == null
                if (resolvingName != null && resolvingName == customName.trim()) CircularProgressIndicator(color = FLPrimary, modifier = Modifier.size(24.dp))
                else Icon(Icons.Filled.ArrowCircleRight, contentDescription = "Add custom phase", tint = if (canAdd) FLPrimary else MediumGray, modifier = Modifier.size(28.dp).clickable(enabled = canAdd) { pick(customName) })
            }
            errorMessage?.let { Text(it, fontSize = 12.sp, color = Color(0xFFE53935)) }
        }
    }
}

@Composable
private fun PhaseRow(icon: ImageVector, color: Color, name: String, subtitle: String?, attached: Boolean, busy: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).clickable(enabled = !attached && !busy, onClick = onClick).padding(12.dp)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (attached) TextSecondaryColor else TextPrimaryColor)
            subtitle?.let { Text(it, fontSize = 12.sp, color = TextSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        when {
            busy -> CircularProgressIndicator(color = FLPrimary, modifier = Modifier.size(18.dp))
            attached -> Text("Added", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
            else -> Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(16.dp))
        }
    }
}
