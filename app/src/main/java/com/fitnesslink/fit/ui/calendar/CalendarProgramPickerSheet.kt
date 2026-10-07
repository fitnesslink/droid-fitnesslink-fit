package com.fitnesslink.fit.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.model.ProgramList
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

/**
 * "Schedule a Program" picker (iOS `CalendarProgramPickerSheet`): only programs
 * with a local schedule (`programIdsWithSchedules`) — those are the ones that
 * can be laid onto the calendar — with the same filter bar as the workout
 * picker. The start-date sheet's CTA is "Add to Calendar".
 */
@Composable
fun CalendarProgramPickerSheet(
    scheduleDate: Date,
    onSchedule: (program: ProgramList, startDate: Date) -> Unit,
    onDismiss: () -> Unit
) {
    var programs by remember { mutableStateOf<List<ProgramList>>(emptyList()) }
    val filters = remember { CalendarPickerFilterState() }
    var selectedProgram by remember { mutableStateOf<ProgramList?>(null) }
    ContributorStore.contributors.collectAsState()
    ClassificationStore.equipment.collectAsState()
    ClassificationStore.anatomy.collectAsState()

    LaunchedEffect(Unit) {
        programs = withContext(Dispatchers.IO) {
            val scheduledIds = DatabaseManager.programIdsWithSchedules()
            DatabaseManager.allPrograms().filter { it.id.lowercase(Locale.US) in scheduledIds }
        }
    }
    LaunchedEffect(Unit) { runCatching { ContributorStore.refresh() } }
    LaunchedEffect(Unit) {
        if (!NetworkMonitor.isConnected.value) return@LaunchedEffect
        val remote = withContext(Dispatchers.IO) { runCatching { ApiClient.programApi.listView() }.getOrNull() } ?: return@LaunchedEffect
        filters.equipmentByContentId = remote.associate { p ->
            p.id.lowercase(Locale.US) to p.equipmentIds.map { it.toString().lowercase(Locale.US) }.toSet()
        }.filterValues { it.isNotEmpty() }
        filters.anatomyByContentId = remote.associate { p ->
            p.id.lowercase(Locale.US) to p.anatomyIds.map { it.toString().lowercase(Locale.US) }.toSet()
        }.filterValues { it.isNotEmpty() }
    }

    val filtered = filters.apply(programs, { it.id }, { it.name }, { it.isFavorite }, { it.contributorId })

    selectedProgram?.let { program ->
        ScheduleDatePickerSheet(
            initialDate = scheduleDate,
            workoutEstimateMinutes = null,
            title = "Start \"${program.name}\" on...",
            cta = "Add to Calendar",
            showDurationChips = false,
            onConfirm = { date, _ -> selectedProgram = null; onSchedule(program, date); onDismiss() },
            onDismiss = { selectedProgram = null }
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(White)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Schedule Program", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextPrimaryColor) }
                }
                CalendarPickerFilterBar(
                    state = filters,
                    searchPlaceholder = "Search programs",
                    hasPersonalContent = programs.any { it.contributorId == null },
                    presentContributorIds = programs.mapNotNull { it.contributorId?.lowercase(Locale.US) }.toSet()
                )
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = MediumGray, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (filters.hasActiveFilters || filters.searchText.isNotEmpty()) "No programs match these filters" else "No programs found",
                                fontSize = 15.sp, color = TextSecondaryColor
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.id }) { program ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProgram = program }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(program.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                                    if (program.time.isNotEmpty()) {
                                        Text(program.time, fontSize = 12.sp, color = TextSecondaryColor)
                                    }
                                }
                                Icon(Icons.Default.EventAvailable, contentDescription = "Schedule", tint = FLPrimary, modifier = Modifier.size(20.dp))
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MediumGray.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}
