package com.fitnesslink.fit.ui.programs

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.model.ProgramJourneyState
import com.fitnesslink.fit.model.ProgramPhases
import com.fitnesslink.fit.model.UserProgram
import com.fitnesslink.fit.model.api.ProgramSchedule
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.programs.ProgramScheduler
import com.fitnesslink.fit.programs.ProgramStore
import com.fitnesslink.fit.programs.UserProgramStore
import com.fitnesslink.fit.ui.components.BackCircleButton
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.LocationInfoView
import com.fitnesslink.fit.ui.components.PrimaryButtonView
import com.fitnesslink.fit.ui.components.SecondaryButtonView
import com.fitnesslink.fit.ui.components.TrainingLevelInfoView
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.navigation.ProgramRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Pure preview of the instance a start date would create — drives the
 * "Your Program Timeline" (iOS `StartProgramView.preview`).
 */
object StartProgramPreview {
    fun totalWeeks(program: Program, schedules: List<ProgramSchedule>): Int =
        maxOf(program.weeks ?: 0, schedules.maxOfOrNull { it.weekNumber } ?: 0, 1)

    fun workoutsPerWeek(schedules: List<ProgramSchedule>, totalWeeks: Int): Int =
        (schedules.size.toDouble() / maxOf(totalWeeks, 1)).roundToInt()

    fun instance(program: Program, programId: String, schedules: List<ProgramSchedule>, startDateMillis: Long): UserProgram {
        val anchor = ProgramScheduler.anchor(schedules)
        return UserProgram(
            enrollmentId = "",
            programId = programId,
            programName = program.name,
            startDate = ProgramDates.startOfDay(startDateMillis),
            weekOneStart = UserProgram.weekOneStart(startDateMillis, anchor?.weekNumber ?: 1, anchor?.dayNumber ?: 1),
            weeks = totalWeeks(program, schedules),
            createdAt = System.currentTimeMillis()
        )
    }
}

/**
 * Start Your Program (Programs PRD §13–15; iOS `StartProgramView`): pick the
 * day Week 1 begins, see the resulting week-by-week timeline, start.
 * Starting schedules the workouts, activates the program and hands off to
 * "You're All Set!" → My Program — never back to Program Detail.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StartProgramScreen(
    programId: String,
    replacingEnrollmentId: String?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var program by remember { mutableStateOf(Program()) }
    var schedules by remember { mutableStateOf<List<ProgramSchedule>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isStarting by remember { mutableStateOf(false) }
    var showReplaceAlert by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableStateOf<UserProgram?>(null) }
    val todayStart = remember { ProgramDates.startOfDay(System.currentTimeMillis()) }
    var startDate by remember { mutableStateOf(todayStart) }

    // Material's DatePicker works in UTC-midnight millis; keep local-midnight millis in `startDate`.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = toUtcDay(todayStart),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= toUtcDay(todayStart)
        }
    )
    LaunchedEffect(pickerState.selectedDateMillis) {
        pickerState.selectedDateMillis?.let { startDate = fromUtcDay(it) }
    }

    LaunchedEffect(programId) {
        withContext(Dispatchers.IO) {
            program = DatabaseManager.program(programId) ?: Program()
            schedules = DatabaseManager.schedulesForProgram(programId)
        }
        isLoading = schedules.isEmpty()
        ProgramStore.fetchDetail(programId)?.let { if (it.pendingShare == null) program = it.program }
        if (schedules.isEmpty()) {
            ProgramStore.fetchSchedules(programId)
            schedules = withContext(Dispatchers.IO) { DatabaseManager.schedulesForProgram(programId) }
        }
        isLoading = false
    }

    val totalWeeks = StartProgramPreview.totalWeeks(program, schedules)
    val workoutsPerWeek = StartProgramPreview.workoutsPerWeek(schedules, totalWeeks)
    val preview = StartProgramPreview.instance(program, programId, schedules, startDate)

    /** Unfinished programs a new start would replace; the one being restarted is replaced without asking. */
    fun otherOpenPrograms(): List<UserProgram> = UserProgramStore.all().filter {
        !it.enrollmentId.equals(replacingEnrollmentId, ignoreCase = true) &&
            UserProgramStore.snapshot(it).state != ProgramJourneyState.COMPLETED
    }

    fun start(replacing: List<UserProgram>) {
        if (isStarting) return
        isStarting = true
        scope.launch {
            try {
                val toReplace = replacing.map { it.enrollmentId }.toMutableList()
                replacingEnrollmentId?.takeIf { it.isNotEmpty() }?.let { toReplace.add(it) }
                // Replace Program / Restart: the extra instances come out first, then the scheduler removes `replacing`.
                toReplace.drop(1).forEach { UserProgramStore.remove(it) }
                val enrollmentId = ProgramScheduler.addToCalendar(programId, Date(startDate), replacing = toReplace.firstOrNull())
                started = withContext(Dispatchers.IO) { UserProgramStore.program(enrollmentId) }
                    ?: preview.copy(enrollmentId = enrollmentId)
            } catch (e: Exception) {
                startError = e.message ?: "Couldn't start the program."
            } finally {
                isStarting = false
            }
        }
    }

    started?.let { instance ->
        ProgramStartedScreen(
            program = instance,
            onViewMyProgram = { AppRouter.resetStack(ProgramRoutes.myProgram(instance.enrollmentId)) },
            onViewCalendar = {
                AppRouter.resetStack(null)
                AppRouter.push(AppRouter.CALENDAR_TAB)
            }
        )
        return
    }

    if (showReplaceAlert) {
        AlertDialog(
            onDismissRequest = { showReplaceAlert = false },
            title = { Text("You already have an active program.") },
            text = { Text("Would you like to replace your current program? Its remaining workouts will be removed from your calendar.") },
            confirmButton = {
                TextButton(onClick = { showReplaceAlert = false; start(otherOpenPrograms()) }) { Text("Replace Program", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showReplaceAlert = false }) { Text("Keep Current Program") } }
        )
    }
    startError?.let { message ->
        AlertDialog(
            onDismissRequest = { startError = null },
            title = { Text("Couldn't start") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { startError = null }) { Text("OK") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackCircleButton(onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
            Text("Start Your Program", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(36.dp))
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                FLImageView(
                    ref = (program.thumbnailId ?: program.imageId)?.let { MediaRef.ProgramThumbnail(it.toString()) },
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(program.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor, maxLines = 3)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        program.trainingLevels.filter { it.isNotEmpty() }.forEach { TrainingLevelInfoView(level = it) }
                        if (program.location.isNotEmpty()) LocationInfoView(location = program.location)
                    }
                }
            }

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(White).padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Stat("$workoutsPerWeek", "Workouts/Week", Modifier.weight(1f))
                VerticalRule()
                if (program.time.isNotEmpty()) {
                    Stat(program.time, "Per Workout", Modifier.weight(1f))
                    VerticalRule()
                }
                Stat("$totalWeeks", if (totalWeeks == 1) "Week" else "Weeks", Modifier.weight(1f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose Your Start Date", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                Text("Select the date you'd like to begin Week 1.", fontSize = 13.sp, color = TextSecondaryColor)
            }

            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(White)) {
                DatePicker(
                    state = pickerState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor = White,
                        selectedDayContainerColor = FLPrimary,
                        todayDateBorderColor = FLPrimary,
                        todayContentColor = FLPrimary
                    )
                )
            }

            // Timeline
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(White).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Your Program Timeline", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                (1..totalWeeks).forEach { week ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Week $week", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor, modifier = Modifier.width(70.dp))
                        Text(preview.weekRangeLabel(week), fontSize = 14.sp, color = TextSecondaryColor)
                        Spacer(modifier = Modifier.weight(1f))
                        ProgramPhases.name(week, totalWeeks, program.weekPhases)?.let {
                            Text("$it Phase", fontSize = 12.sp, color = TextSecondaryColor)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val canStart = schedules.isNotEmpty() && !isStarting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(100.dp))
                    .clickable(enabled = canStart) {
                        if (otherOpenPrograms().isEmpty()) start(emptyList()) else showReplaceAlert = true
                    }
            ) {
                if (isStarting) {
                    Box(modifier = Modifier.fillMaxWidth().background(FLPrimary).padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Box(modifier = Modifier.alpha(if (canStart) 1f else 0.5f)) { PrimaryButtonView(text = "Start Program") }
                }
            }
            if (schedules.isEmpty() && !isLoading) {
                Text(
                    "This program's schedule isn't available yet. Check your connection and try again.",
                    fontSize = 12.sp, color = TextSecondaryColor, textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
        Text(label, fontSize = 11.sp, color = TextSecondaryColor)
    }
}

@Composable
private fun VerticalRule() {
    Box(modifier = Modifier.width(1.dp).height(32.dp).background(TextSecondaryColor.copy(alpha = 0.2f)))
}

/** Local-midnight millis → the UTC-midnight millis Material's DatePicker uses for the same calendar day. */
internal fun toUtcDay(localMidnight: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMidnight }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** UTC-midnight millis from Material's DatePicker → local-midnight millis of that calendar day. */
internal fun fromUtcDay(utcMidnight: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/**
 * "You're All Set!" (PRD §15; iOS `ProgramStartedView`). Both exits leave
 * the Program Detail / setup screens behind (the caller resets the stack).
 */
@Composable
fun ProgramStartedScreen(program: UserProgram, onViewMyProgram: () -> Unit, onViewCalendar: () -> Unit) {
    // Back must not return to the Start screen (iOS: interactiveDismissDisabled).
    BackHandler { onViewMyProgram() }
    val dateLabel = remember(program.startDate) { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(program.startDate)) }

    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(White).clickable(onClick = onViewMyProgram),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimaryColor, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Default.EventAvailable, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("You're All Set!", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Your ${program.programName} program starts $dateLabel.",
            fontSize = 15.sp, color = TextSecondaryColor, textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Confirmation("All workouts have been added to your calendar.")
            Confirmation("You can view and manage your program in My Program.")
            Confirmation("My Program now appears on your Home screen.")
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).clickable(onClick = onViewMyProgram)) {
            PrimaryButtonView(text = "View My Program")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).clickable(onClick = onViewCalendar)) {
            SecondaryButtonView(text = "View Calendar")
        }
    }
}

@Composable
private fun Confirmation(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 14.sp, color = TextPrimaryColor)
    }
}
