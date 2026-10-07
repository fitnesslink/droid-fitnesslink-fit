package com.fitnesslink.fit.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Opens from a completed calendar / Today entry (port of iOS `CompletedWorkoutReportView`):
 * resolve the completed session for [workoutId] on [day] (`yyyy-MM-dd`) locally, then backfill
 * from `/sessions/me`, then fall back to the workout detail.
 */
@Composable
fun CompletedWorkoutReportView(workoutId: String, day: String, onBack: () -> Unit, onOpenWorkout: (String) -> Unit) {
    var sessionId by remember { mutableStateOf<String?>(null) }
    var resolved by remember { mutableStateOf(false) }
    LaunchedEffect(workoutId, day) {
        val dayMs = parseDay(day)
        var found = dayMs?.let { DatabaseManager.completedSession(workoutId, it)?.id }
        if (found == null && NetworkMonitor.isConnected.value && dayMs != null) {
            try {
                val mine = withContext(Dispatchers.IO) { ApiClient.sessionApi.getMySessions() }
                mine.filter { it.isCompletedOnServer }.forEach { DatabaseManager.insertWorkoutSession(it) }
                found = DatabaseManager.completedSession(workoutId, dayMs)?.id
            } catch (_: Exception) {}
        }
        sessionId = found
        resolved = true
    }
    when {
        !resolved -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = White) }
        sessionId != null -> WorkoutSummaryReopenView(sessionId!!, onBack)
        else -> LaunchedEffect(Unit) { onOpenWorkout(workoutId) }
    }
}

private fun parseDay(day: String): Long? = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getDefault() }.parse(day)?.time
} catch (_: Exception) { day.toLongOrNull() }
