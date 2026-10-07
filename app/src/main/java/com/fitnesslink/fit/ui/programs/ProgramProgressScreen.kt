package com.fitnesslink.fit.ui.programs

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.ProgramJourneyState
import com.fitnesslink.fit.model.ProgramSnapshot
import com.fitnesslink.fit.model.ProgramWeekPlan
import com.fitnesslink.fit.programs.UserProgramStore
import com.fitnesslink.fit.ui.components.BackCircleButton
import com.fitnesslink.fit.ui.components.PrimaryButtonView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Program Progress (Programs PRD §23–24; iOS `ProgramProgressView`):
 * overall progress by completed weeks, the finer-grained workout
 * completion, and every week's breakdown.
 *
 * @param enrollmentId null = the active program.
 */
@Composable
fun ProgramProgressScreen(enrollmentId: String?, onBack: () -> Unit) {
    var snapshot by remember { mutableStateOf<ProgramSnapshot?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(enrollmentId) {
        snapshot = withContext(Dispatchers.IO) {
            val program = enrollmentId?.takeIf { it.isNotEmpty() }?.let { UserProgramStore.program(it) } ?: UserProgramStore.active()
            program?.let { UserProgramStore.snapshot(it) }
        }
        loaded = true
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackCircleButton(onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
            Text("Program Progress", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(36.dp))
        }

        val current = snapshot
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loaded) Text("No program in progress.", fontSize = 14.sp, color = TextSecondaryColor)
            }
            return
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val startLabel = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(current.program.startDate))
            ProgramHeroView(
                imageId = current.heroImageId(),
                name = current.program.programName,
                subtitle = if (current.hasStarted) "Started $startLabel" else "Starts $startLabel",
                height = 150.dp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
            )

            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(White).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProgramProgressRow(
                    title = "Overall Program Progress",
                    caption = "${current.weeksCompleted} of ${current.totalWeeks} weeks completed",
                    filled = current.weeksCompleted,
                    total = current.totalWeeks,
                    percent = current.weeksPercent
                )
                ProgramProgressRow(
                    title = "Workouts",
                    caption = "${current.completedWorkouts} of ${current.totalWorkouts} completed",
                    filled = current.completedWorkouts,
                    total = current.totalWorkouts,
                    percent = current.workoutsPercent,
                    tint = PurpleTheme
                )
            }

            Text("Weekly Breakdown", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                current.weeks.forEach { week ->
                    val isCurrent = current.hasStarted && current.state != ProgramJourneyState.COMPLETED && week.weekNumber == current.currentWeek
                    WeekBreakdownRow(week, isCurrent)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(100.dp))
                .clickable(onClick = onBack)
        ) { PrimaryButtonView(text = "Back to Week Plan") }
    }
}

@Composable
private fun WeekBreakdownRow(week: ProgramWeekPlan, isCurrent: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .border(1.dp, if (isCurrent) FLPrimary else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = when {
                week.isComplete -> Icons.Default.CheckCircle
                isCurrent -> Icons.Default.RadioButtonChecked
                else -> Icons.Default.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = when {
                week.isComplete -> FLPrimary
                isCurrent -> PurpleTheme
                else -> MediumGray
            },
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.width(104.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Week ${week.weekNumber}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text(week.rangeLabel, fontSize = 12.sp, color = TextSecondaryColor)
            week.phase?.let { Text("$it Phase", fontSize = 12.sp, color = TextSecondaryColor) }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgramProgressBar(
                filled = week.completed,
                total = maxOf(week.total, 1),
                tint = if (isCurrent) PurpleTheme else FLPrimary,
                height = 6.dp
            )
            Text("${week.completed} / ${week.total} workouts", fontSize = 12.sp, color = TextSecondaryColor)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 52.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${week.percent}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, textAlign = TextAlign.End)
            if (week.isComplete || isCurrent) {
                Text(
                    if (week.isComplete) "Complete" else "Current",
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    color = if (week.isComplete) FLPrimary else PurpleTheme
                )
            }
        }
    }
}
