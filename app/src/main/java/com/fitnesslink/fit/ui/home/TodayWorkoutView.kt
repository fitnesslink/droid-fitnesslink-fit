package com.fitnesslink.fit.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.calendar.tapRoute
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.text.DateFormat
import java.util.Date

/**
 * "Today's Workout" / "Workouts on MMM d" section (iOS `TodayWorkoutView`).
 * Rows route through `FitnessContent.tapRoute()` so a completed entry opens
 * its report and anything else opens the workout detail. An empty day shows
 * the program rest card when [isRestDay], else the actionable empty state.
 */
@Composable
fun TodayWorkoutView(
    workouts: List<FitnessContent>,
    title: String = "Today's Workout",
    isRestDay: Boolean = false,
    tomorrowWorkout: FitnessContent? = null,
    onNavigate: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        if (workouts.isEmpty()) {
            if (isRestDay) RestView(tomorrowWorkout = tomorrowWorkout, onNavigate = onNavigate)
            else NoWorkoutPlannedView(onNavigate = onNavigate)
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                workouts.forEach { entry ->
                    TodayWorkoutCard(entry = entry, onClick = { onNavigate(entry.tapRoute()) })
                }
            }
        }
    }
}

@Composable
private fun TodayWorkoutCard(entry: FitnessContent, onClick: () -> Unit) {
    val isCompleted = entry.isCompleted
    val thumbRef = (entry.workoutThumbnailId ?: entry.workoutImageId)?.let { MediaRef.WorkoutThumbnail(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FLImageView(
            ref = thumbRef,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = entry.title.ifBlank { "Workout" },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = if (isCompleted) FLPrimary else TextSecondaryColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(text = scheduleLabel(entry, isCompleted), fontSize = 12.sp, color = TextSecondaryColor)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MediumGray,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun scheduleLabel(entry: FitnessContent, completed: Boolean): String {
    if (completed) return "Completed"
    val date = entry.scheduledDate ?: return "Scheduled for today"
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(date))
}

/**
 * Rest-day card for a day a program prescribes rest (iOS `RestView`,
 * FTNSSLNK-366): recovery copy, tomorrow's workout preview and a
 * "Train anyway" escape hatch into the workout library.
 */
@Composable
fun RestView(tomorrowWorkout: FitnessContent? = null, onNavigate: (String) -> Unit = {}) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Image(
                painter = painterResource(R.drawable.rest),
                contentDescription = null,
                modifier = Modifier.size(60.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(text = "Rest Day", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                Text(
                    text = "Rest days are essential for recovery and muscle growth. Use this time to stretch, hydrate, and prepare for your next workout.",
                    fontSize = 12.sp,
                    color = TextSecondaryColor
                )
            }
        }
        if (tomorrowWorkout != null && tomorrowWorkout.workoutId.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BackgroundColor)
                    .clickable { onNavigate("workoutDetail/${tomorrowWorkout.workoutId}") }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = FLPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Up next tomorrow", fontSize = 11.sp, color = TextSecondaryColor)
                    Text(
                        text = tomorrowWorkout.title.ifBlank { "Workout" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MediumGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = "Train anyway",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = FLPrimary,
            modifier = Modifier.clickable { onNavigate("workouts") }
        )
    }
}

/**
 * Actionable empty state for a day with nothing scheduled and no program
 * prescribing rest (iOS `NoWorkoutPlannedView`, FTNSSLNK-363).
 */
@Composable
fun NoWorkoutPlannedView(onNavigate: (String) -> Unit = {}) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "No workout planned yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text(
                text = "Pick how you want to train — it only takes a minute to get something on the calendar.",
                fontSize = 12.sp,
                color = TextSecondaryColor
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmptyDayActionRow(icon = Icons.Filled.Edit, title = "Create a workout") { onNavigate("workoutEditor") }
            EmptyDayActionRow(icon = Icons.Filled.DateRange, title = "Start a program") { onNavigate("programs") }
            EmptyDayActionRow(icon = Icons.Filled.FitnessCenter, title = "Pick a workout") { onNavigate("workouts") }
        }
    }
}

@Composable
private fun EmptyDayActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BackgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.width(22.dp).height(16.dp))
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MediumGray,
            modifier = Modifier.size(16.dp)
        )
    }
}
