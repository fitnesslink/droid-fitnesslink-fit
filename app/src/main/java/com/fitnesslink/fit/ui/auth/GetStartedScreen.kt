package com.fitnesslink.fit.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.navigation.AppRoute
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.PurpleTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * Port of iOS `Views/Personalization/GetStartedView.swift` (FTNSSLNK-356).
 * Post-personalization routing screen, shown exactly once right after the
 * questionnaire completes. Each option hands a Navigation-Compose route back
 * through [onFinish]; the app root parks it on `AppRouter` so
 * `MainTabNavigation` pushes it as soon as it appears. "I'll explore on my
 * own" finishes with no route and lands on Home.
 */
@Composable
fun GetStartedScreen(onFinish: (route: String?) -> Unit) {
    val firstName = remember {
        (AuthManager.currentUser.value?.firstName
            ?: runCatching { DatabaseManager.user()?.firstName }.getOrNull()
            ?: "").trim()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "FitnessLink",
                colorFilter = ColorFilter.tint(TextPrimaryColor),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 24.dp)
                    .width(134.dp)
            )

            Box(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .size(64.dp)
                    .background(FLPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(28.dp))
            }

            Text(
                text = if (firstName.isEmpty()) "You're all set" else "You're all set, $firstName",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                text = "Your profile is ready. Choose how you want to start training — you can always do the rest later.",
                fontSize = 15.sp,
                color = TextSecondaryColor,
                modifier = Modifier.padding(top = 8.dp)
            )

            // AI deliberately last — lead with programs and the library,
            // the options we want new users to try first.
            Column(
                modifier = Modifier.padding(top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OptionCard(
                    icon = Icons.Filled.CalendarMonth,
                    tint = OrangeTheme,
                    title = "Start a program",
                    subtitle = "Follow a multi-week plan that fills your calendar for you"
                ) { onFinish(AppRoute.Programs.route) }
                OptionCard(
                    icon = Icons.Filled.FitnessCenter,
                    tint = BlueTheme,
                    title = "Start a workout",
                    subtitle = "Pick a ready-made workout from the library and train today"
                ) { onFinish(AppRoute.Workouts.route) }
                OptionCard(
                    icon = Icons.Filled.Edit,
                    tint = PurpleTheme,
                    title = "Build your own",
                    subtitle = "Create a workout from scratch — exercises, sets and reps"
                ) { onFinish(AppRoute.WorkoutEditorNew.route) }
                OptionCard(
                    icon = Icons.Filled.AutoAwesome,
                    tint = FLPrimary,
                    title = "Build with AI",
                    subtitle = "Get a workout generated from your questionnaire answers"
                ) { onFinish(AppRoute.WorkoutEditorGenerate.createRoute(aiSeedPrompt())) }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "I'll explore on my own",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = FLPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFinish(null) }
                .padding(vertical = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun OptionCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(tint.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text(subtitle, fontSize = 13.sp, color = TextSecondaryColor)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondaryColor)
    }
}

/**
 * FTNSSLNK-357: seed the AI generator from the questionnaire answers so the
 * first generated workout already reflects the user's goals, level and
 * equipment. Falls back to a generic beginner prompt when nothing was picked.
 */
internal fun aiSeedPrompt(): String {
    val answers = runCatching { DatabaseManager.personalizations() }.getOrDefault(emptyList())
        .mapNotNull { page ->
            val chosen = page.options.filter { it.selected }.map { it.text }
            if (chosen.isEmpty() || page.name.isEmpty()) null
            else "${page.name}: ${chosen.joinToString(", ")}"
        }
    if (answers.isEmpty()) return "Create a beginner-friendly full-body workout for me."
    return "Create a workout tailored to my profile. " + answers.joinToString(". ") + "."
}
