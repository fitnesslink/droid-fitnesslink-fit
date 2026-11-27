package com.fitnesslink.fit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitnesslink.fit.calendar.CalendarService
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.api.ShareableContentKind
import com.fitnesslink.fit.model.onboarding.RecommendationDismissReason
import com.fitnesslink.fit.model.onboarding.RecommendationItem
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.onboarding.HomeRecommendationsStore
import com.fitnesslink.fit.ui.calendar.ScheduleDatePickerSheet
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Onboarding PRD v1.1 §25: "Recommended for You". Port of iOS
 * `HomeRecommendationsSection` — replaces Today's Workout for first-run users
 * until they start a program, schedule a workout, or hide the section. Every
 * card is real published catalog content.
 */
@Composable
fun HomeRecommendationsSection(onNavigate: (String) -> Unit) {
    val store = HomeRecommendationsStore
    var showHideConfirm by remember { mutableStateOf(false) }
    var reasonItem by remember { mutableStateOf<RecommendationItem?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(FLTheme.colors.surface.copy(alpha = 0.45f)).padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recommended for You", fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() })
                Row(
                    Modifier.border(1.dp, FLPrimary.copy(alpha = 0.6f), CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = FLPrimary, modifier = Modifier.size(12.dp))
                    Text("Personalized", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreHoriz, "Recommendation options", tint = FLTheme.colors.textSecondary)
                    }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Update Training Preferences") }, onClick = { menuOpen = false; onNavigate("personalizationProfile") })
                        DropdownMenuItem(text = { Text("Hide from Home") }, onClick = { menuOpen = false; showHideConfirm = true })
                    }
                }
            }
            Text("Based on your goals and preferences, we've found programs and workouts to help you get started.",
                fontSize = 13.sp, color = FLTheme.colors.textSecondary)
        }

        when {
            store.response == null && store.isLoading ->
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FLPrimary) }
            store.hasNoMatches || store.response == null -> RecommendationsEmptyState(
                "We're Growing Our Workout Library!",
                "We haven't found a program that fits your preferences yet. You can explore other workouts and programs while our library continues to grow.",
                onNavigate
            )
            store.allDismissed -> RecommendationsEmptyState(
                "You've cleared your recommendations",
                "Explore the full catalog, or update your training preferences to see new matches.",
                onNavigate
            )
            else -> {
                if (store.programs.isNotEmpty()) Rail("Recommended Programs", store.programs, onNavigate)
                if (store.workouts.isNotEmpty()) Rail("Recommended Workouts", store.workouts, onNavigate)
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onNavigate("recommendations") }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("See All Recommendations", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = FLPrimary)
                }
            }
        }

        store.lastDismissed?.let { item ->
            LaunchedEffect(item.id) { delay(6000); store.clearUndo() }
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FLTheme.colors.surface)
                    .border(1.dp, FLTheme.colors.divider, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Removed \"${item.name}\"", fontSize = 13.sp, color = FLTheme.colors.textPrimary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton({ reasonItem = item }) { Text("Why?", color = FLTheme.colors.textSecondary) }
                TextButton({ store.undoDismiss() }) { Text("Undo", color = FLPrimary, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showHideConfirm) {
        AlertDialog(
            onDismissRequest = { showHideConfirm = false },
            title = { Text("Hide recommendations?") },
            text = { Text("Today's Workout returns to Home. Your recommendations stay available from Catalog.") },
            confirmButton = { TextButton({ showHideConfirm = false; store.endFirstRun("Dismissed") }) { Text("Hide from Home") } },
            dismissButton = { TextButton({ showHideConfirm = false }) { Text("Cancel") } }
        )
    }
    reasonItem?.let { item ->
        AlertDialog(
            onDismissRequest = { reasonItem = null },
            title = { Text("Why aren't you interested?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Optional — it helps us improve your matches.", fontSize = 13.sp, color = FLTheme.colors.textSecondary)
                    RecommendationDismissReason.entries.forEach { reason ->
                        Text(reason.title, fontSize = 15.sp, color = FLTheme.colors.textPrimary,
                            modifier = Modifier.fillMaxWidth().clickable {
                                store.setReason(item, reason); store.clearUndo(); reasonItem = null
                            }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = { TextButton({ reasonItem = null }) { Text("Skip") } }
        )
    }
}

@Composable
private fun Rail(title: String, items: List<RecommendationItem>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary, modifier = Modifier.padding(horizontal = 20.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEach { RecommendationCard(it, onNavigate, width = 318.dp) }
        }
    }
}

@Composable
fun RecommendationCard(item: RecommendationItem, onNavigate: (String) -> Unit, width: Dp? = null) {
    val store = HomeRecommendationsStore
    val scope = rememberCoroutineScope()
    var showSchedule by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var scheduled by remember { mutableStateOf(false) }
    val detailRoute = if (item.isProgram) "programDetail/${item.id}" else "workoutDetail/${item.id}"

    Column(
        (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(16.dp)).background(FLTheme.colors.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(96.dp).height(116.dp).clip(RoundedCornerShape(12.dp)).background(FLTheme.colors.imagePlaceholder)) {
                if (item.imageUrl != null) {
                    AsyncImage(item.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    FLImageView(
                        ref = item.imageId?.let { if (item.isProgram) MediaRef.ProgramThumbnail(it) else MediaRef.WorkoutThumbnail(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MatchBadge(item, Modifier.padding(6.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 12.sp, color = FLTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                details(item).take(4).forEach { (icon, text) -> Detail(icon, text) }
                if (item.equipment.isNotEmpty()) Detail(Icons.Filled.FitnessCenter, item.equipment.take(3).joinToString(", "))
            }
        }
        Text(item.explanation, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
            color = if (item.isStrongMatch) FLTheme.colors.textSecondary else FLTheme.colors.textPrimary.copy(alpha = 0.85f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(CircleShape).background(FLPrimary).clickable(role = Role.Button) { onNavigate(detailRoute) }
                    .heightIn(min = 36.dp).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text(if (item.isProgram) "View Program" else "View Workout", color = FLTheme.colors.onBrand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            if (!item.isProgram) {
                Pill(Icons.Filled.CalendarMonth, "Schedule") { showSchedule = true }
                Pill(if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (saved) "Saved" else "Save") {
                    saved = !saved
                    scope.launch { runCatching { ApiClient.contentApi.toggleFavorite(item.id, ShareableContentKind.WORKOUT.serverName) } }
                }
            } else {
                Spacer(Modifier.weight(1f))
                NotInterested(item)
            }
        }
        if (!item.isProgram) NotInterested(item)
    }

    if (showSchedule) {
        ScheduleDatePickerSheet(
            initialDate = null,
            workoutEstimateMinutes = item.estimatedMinutes,
            title = "Schedule \"${item.name}\"",
            cta = "Schedule",
            onConfirm = { date, minutes ->
                showSchedule = false
                scope.launch {
                    runCatching { CalendarService.scheduleWorkout(item.id, item.name, date, minutes ?: item.estimatedMinutes) }
                        .onSuccess { scheduled = true }
                }
            },
            onDismiss = { showSchedule = false }
        )
    }
    if (scheduled) {
        AlertDialog(
            onDismissRequest = { scheduled = false; store.endFirstRun("Graduated") },
            title = { Text("Workout scheduled") },
            text = { Text("\"${item.name}\" is on your calendar.") },
            confirmButton = { TextButton({ scheduled = false; store.endFirstRun("Graduated") }) { Text("OK") } }
        )
    }
}

@Composable
private fun NotInterested(item: RecommendationItem) {
    Text("Not Interested", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLTheme.colors.textSecondary,
        modifier = Modifier.clickable(role = Role.Button) { HomeRecommendationsStore.dismiss(item) }
            .semantics { contentDescription = "Not interested in ${item.name}" }.padding(vertical = 8.dp))
}

private fun details(item: RecommendationItem): List<Pair<ImageVector, String>> = buildList {
    if (item.isProgram) {
        item.weeks?.let { add(Icons.Filled.CalendarMonth to "$it Weeks") }
        item.daysPerWeek?.let { add(Icons.Filled.Repeat to "$it Days/Week") }
    } else item.estimatedMinutes?.let { add(Icons.Filled.Schedule to "$it min") }
    item.levelText?.let { add(Icons.Filled.BarChart to it) }
    if (!item.isProgram) item.category?.let { add(Icons.Filled.Sell to it) }
    item.location?.let { add(Icons.Filled.Home to it) }
}

@Composable
private fun Detail(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = FLTheme.colors.textSecondary, modifier = Modifier.size(13.dp))
        Text(text, fontSize = 11.sp, color = FLTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MatchBadge(item: RecommendationItem, modifier: Modifier) {
    Row(
        modifier.clip(CircleShape).background(if (item.isStrongMatch) FLPrimary else PurpleTheme).padding(horizontal = 6.dp, vertical = 3.dp)
            .semantics(mergeDescendants = true) { contentDescription = if (item.isStrongMatch) "Strong match" else "Partial match" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(if (item.isStrongMatch) Icons.Filled.Check else Icons.Filled.Contrast, null, tint = FLTheme.colors.onBrand, modifier = Modifier.size(10.dp))
        Text(if (item.isStrongMatch) "Strong Match" else "Partial Match", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.onBrand)
    }
}

@Composable
private fun Pill(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).border(1.dp, FLTheme.colors.textTertiary.copy(alpha = 0.5f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick).heightIn(min = 36.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = FLTheme.colors.textPrimary, modifier = Modifier.size(14.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLTheme.colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun RecommendationsEmptyState(title: String, message: String, onNavigate: (String) -> Unit) {
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FLTheme.colors.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
        Text(message, fontSize = 13.sp, color = FLTheme.colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Explore Programs") { onNavigate("programs") }
            Chip("Explore Workouts") { onNavigate("workouts") }
        }
        Text("Update Training Preferences", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary,
            modifier = Modifier.clickable(role = Role.Button) { onNavigate("personalizationProfile") }.padding(vertical = 4.dp))
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(FLPrimary).clickable(role = Role.Button, onClick = onClick).heightIn(min = 36.dp).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = FLTheme.colors.onBrand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

/** "See All Recommendations" (route `recommendations`). */
@Composable
fun RecommendationsListScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val store = HomeRecommendationsStore
    LaunchedEffect(Unit) { store.refresh(context) }
    Column(Modifier.fillMaxSize().background(FLTheme.colors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = FLTheme.colors.textPrimary) }
            Text("Recommended for You", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (store.programs.isNotEmpty()) {
                Text("Programs", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
                store.programs.forEach { RecommendationCard(it, onNavigate) }
            }
            if (store.workouts.isNotEmpty()) {
                Text("Workouts", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
                store.workouts.forEach { RecommendationCard(it, onNavigate) }
            }
            if (store.programs.isEmpty() && store.workouts.isEmpty()) {
                Text("No recommendations right now. Explore the Catalog or update your training preferences from your profile.",
                    fontSize = 14.sp, color = FLTheme.colors.textSecondary)
            }
        }
    }
}
