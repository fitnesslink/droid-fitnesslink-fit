package com.fitnesslink.fit.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.MeasurementsViewModel

/**
 * iOS `MeasurementEntryDetailView` (route `measurementEntryDetail/{entryId}`):
 * the entry date, the shared category-grouped breakdown with deltas vs. the
 * chronologically previous entry, and notes. Delete (with confirmation) is
 * an Android addition.
 */
@Composable
fun MeasurementEntryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    viewModel: MeasurementsViewModel = viewModel()
) {
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(entryId) { viewModel.loadData() }

    val entry = viewModel.entry(entryId)
    val previous = viewModel.previousOf(entryId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            HeaderBackView(onBack = onBack)
            if (entry != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(White)
                        .clickable { confirmDelete = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete entry", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                }
            }
        }

        if (entry == null) {
            Text(
                if (viewModel.entries.isEmpty()) "Loading…" else "This entry is no longer available.",
                fontSize = 14.sp, color = TextSecondaryColor, modifier = Modifier.padding(20.dp)
            )
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(longDate.format(entry.date), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, modifier = Modifier.padding(top = 10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(White, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                MeasurementBreakdownView(entry = entry, previous = previous, unit = viewModel.preferredUnit)
                if (previous == null) {
                    Text(
                        "First entry — changes appear once you log again.",
                        fontSize = 12.sp, color = TextSecondaryColor, modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            if (entry.notes.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(White, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Notes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                    Text(entry.notes, fontSize = 14.sp, color = TextSecondaryColor)
                }
            }
        }
    }

    val deleting = entry
    if (confirmDelete && deleting != null) {
        ConfirmDeleteDialog(
            title = "Delete measurements?",
            message = "Remove the ${deleting.measurements.size} measurements logged ${longDate.format(deleting.date)}?",
            onConfirm = {
                viewModel.deleteEntry(deleting.id)
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
