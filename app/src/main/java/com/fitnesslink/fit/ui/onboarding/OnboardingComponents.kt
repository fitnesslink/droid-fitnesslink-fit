package com.fitnesslink.fit.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.onboarding.OnboardingOption
import com.fitnesslink.fit.onboarding.OnboardingStep
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.ui.theme.OrangeTheme

// Port of iOS Views/Onboarding/OnboardingComponents.swift.

@Composable
fun OnboardingScaffold(
    title: String,
    subtitle: String? = null,
    questionNumber: Int? = null,
    showsBack: Boolean = true,
    onBack: () -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val focus = LocalFocusManager.current
    Column(
        Modifier
            .fillMaxSize()
            .background(FLTheme.colors.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        OnboardingHeader(showsBack, onBack)
        if (questionNumber != null) {
            OnboardingProgressBar(questionNumber, OnboardingStep.QUESTION_COUNT, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 24.dp)
        ) {
            Text(title, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                Text(subtitle, fontSize = 15.sp, color = FLTheme.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = footer
        )
    }
}

@Composable
fun OnboardingHeader(showsBack: Boolean = true, onBack: () -> Unit = {}, logoColor: Color = FLTheme.colors.textPrimary) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
        if (showsBack) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(FLTheme.colors.surface)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FLTheme.colors.textPrimary, modifier = Modifier.size(20.dp))
            }
        }
        OnboardingWordmark(Modifier.align(Alignment.Center), logoColor)
    }
}

@Composable
fun OnboardingWordmark(modifier: Modifier = Modifier, color: Color = FLTheme.colors.textPrimary) {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "FitnessLink",
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.width(134.dp).height(15.dp)
    )
}

@Composable
fun OnboardingProgressBar(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Question $current of $total" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 1..total) {
                Box(
                    Modifier.weight(1f).height(5.dp).clip(CircleShape)
                        .background(if (i <= current) FLPrimary else FLTheme.colors.progressTrack)
                )
            }
        }
        Text("$current of $total", fontSize = 12.sp, color = FLTheme.colors.textSecondary, modifier = Modifier.padding(start = 10.dp))
    }
}

// MARK: Buttons

@Composable
fun OnboardingPrimaryButton(
    title: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(CircleShape)
            .background(FLPrimary.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) CircularProgressIndicator(color = FLTheme.colors.onBrand, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = FLTheme.colors.onBrand, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (icon != null) Icon(icon, null, tint = FLTheme.colors.onBrand, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun OnboardingSecondaryButton(title: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(CircleShape)
            .border(1.2.dp, FLTheme.colors.textTertiary.copy(alpha = 0.6f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = FLTheme.colors.textPrimary, modifier = Modifier.size(18.dp))
            Text(title, color = FLTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun OnboardingTextButton(title: String, onClick: () -> Unit) {
    Text(
        title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        textDecoration = TextDecoration.Underline,
        color = FLTheme.colors.textPrimary,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(8.dp)
    )
}

// MARK: Selection

@Composable
fun SelectionIndicator(selected: Boolean, multi: Boolean = false) {
    Box(
        Modifier
            .size(22.dp)
            .then(
                if (selected) Modifier.clip(CircleShape).background(FLPrimary)
                else Modifier.border(1.5.dp, FLTheme.colors.textTertiary, if (multi) RoundedCornerShape(5.dp) else CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = FLTheme.colors.onBrand, modifier = Modifier.size(14.dp))
    }
}

fun Modifier.selectableCard(selected: Boolean, radius: Dp = 14.dp, onClick: () -> Unit): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .border(BorderStroke(2.dp, if (selected) FLPrimary else Color.Transparent), shape)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { this.selected = selected }
}

@Composable
fun OnboardingIconBadge(icon: ImageVector, size: Dp = 52.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(FLPrimary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = FLPrimary, modifier = Modifier.size(size * 0.46f)) }
}

/** Full-width row card: photo (or icon) left, title + description, indicator right. */
@Composable
fun OnboardingOptionCard(
    option: OnboardingOption,
    selected: Boolean,
    multi: Boolean = false,
    showsPhoto: Boolean = true,
    enabled: Boolean = true,
    disabledNote: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 84.dp)
            .selectableCard(selected) { if (enabled) onClick() }
            .background(FLTheme.colors.surface)
            .then(if (enabled) Modifier else Modifier.background(FLTheme.colors.background.copy(alpha = 0.5f))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val photo = option.photo
        if (showsPhoto && photo != null) {
            Image(painterResource(photo), null, contentScale = ContentScale.Crop, modifier = Modifier.width(96.dp).fillMaxHeight())
        } else {
            Spacer(Modifier.width(14.dp))
            OnboardingIconBadge(option.icon)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(option.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = FLTheme.colors.textPrimary.copy(alpha = if (enabled) 1f else 0.45f))
            (disabledNote ?: option.subtitle)?.let {
                Text(it, fontSize = 13.sp, color = FLTheme.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
        SelectionIndicator(selected, multi)
        Spacer(Modifier.width(14.dp))
    }
}

/** Photo tile with caption (locations). */
@Composable
fun OnboardingPhotoTile(option: OnboardingOption, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(150.dp).selectableCard(selected, onClick = onClick).background(FLTheme.colors.surface)) {
        val photo = option.photo
        if (photo != null) {
            Image(painterResource(photo), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))) // theme-exempt: over media
        } else {
            Icon(option.icon, null, tint = FLPrimary, modifier = Modifier.align(Alignment.Center).size(34.dp))
        }
        Text(option.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = if (photo != null) Color.White else FLTheme.colors.textPrimary, // theme-exempt: over media
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
        Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { SelectionIndicator(selected) }
    }
}

/** Compact multi-select tile (background, equipment items, allergies, interests). */
@Composable
fun OnboardingCompactTile(title: String, icon: ImageVector?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 52.dp)
            .selectableCard(selected, 12.dp, onClick)
            .background(FLTheme.colors.surface)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = FLPrimary, modifier = Modifier.size(18.dp))
        else SelectionIndicator(selected, multi = true)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLTheme.colors.textPrimary,
            maxLines = 3, modifier = Modifier.weight(1f))
        if (icon != null && selected) Icon(Icons.Filled.CheckCircle, null, tint = FLPrimary, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun OnboardingSectionHeader(title: String, subtitle: String? = null, icon: ImageVector? = null) {
    Row(Modifier.semantics(mergeDescendants = true) { heading() }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) Icon(icon, null, tint = FLPrimary, modifier = Modifier.size(22.dp))
        Column {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = FLTheme.colors.textSecondary)
        }
    }
}

@Composable
fun OnboardingNotice(text: String, warning: Boolean = false) {
    val tint = if (warning) OrangeTheme else FLPrimary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.10f)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(if (warning) Icons.Filled.Warning else Icons.Filled.Info, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 13.sp, color = if (warning) FLTheme.colors.textPrimary else FLTheme.colors.textSecondary)
    }
}

/** Branded processing screen for both transitions (PRD §17 / §23). Steps tick — no fake percentages. */
@Composable
fun OnboardingProcessingView(
    title: String,
    subtitle: String,
    icon: ImageVector,
    steps: List<Pair<ImageVector, String>>,
    completedSteps: Int,
    footerTitle: String? = null,
    footerText: String? = null,
    errorText: String? = null,
    onRetry: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    skipTitle: String = "Skip for Now"
) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Column(Modifier.fillMaxSize().background(FLTheme.colors.background).systemBarsPadding()) {
        OnboardingHeader(showsBack = false)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(Modifier.padding(top = 24.dp).size(110.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().border(6.dp, FLPrimary.copy(alpha = 0.18f), CircleShape))
                if (errorText == null) {
                    CircularProgressIndicator(progress = { 0.7f }, color = FLPrimary, strokeWidth = 6.dp,
                        trackColor = Color.Transparent, modifier = Modifier.fillMaxSize().rotate(angle))
                }
                Icon(if (errorText == null) icon else Icons.Filled.Warning, null,
                    tint = if (errorText == null) FLPrimary else OrangeTheme, modifier = Modifier.size(38.dp))
            }
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = FLTheme.colors.textPrimary)
            Text(subtitle, fontSize = 15.sp, textAlign = TextAlign.Center, color = FLTheme.colors.textSecondary)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FLTheme.colors.surface)) {
                steps.forEachIndexed { i, (stepIcon, text) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(stepIcon, null, tint = FLTheme.colors.textPrimary, modifier = Modifier.size(22.dp))
                        Text(text, fontSize = 15.sp, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
                        when {
                            i < completedSteps -> Icon(Icons.Filled.CheckCircle, "Done", tint = FLPrimary, modifier = Modifier.size(24.dp))
                            i == completedSteps && errorText == null -> CircularProgressIndicator(color = FLPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            else -> Icon(Icons.Filled.RadioButtonUnchecked, "Waiting", tint = FLTheme.colors.textTertiary, modifier = Modifier.size(24.dp))
                        }
                    }
                    if (i < steps.size - 1) HorizontalDivider(color = FLTheme.colors.divider, modifier = Modifier.padding(start = 50.dp))
                }
            }
            if (errorText != null) OnboardingNotice(errorText, warning = true)
            else if (footerTitle != null && footerText != null) {
                Row(
                    Modifier.fillMaxWidth().border(1.dp, FLPrimary.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Home, null, tint = FLPrimary, modifier = Modifier.size(26.dp))
                    Column {
                        Text(footerTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
                        Text(footerText, fontSize = 13.sp, color = FLTheme.colors.textSecondary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (errorText != null) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                onRetry?.let { OnboardingPrimaryButton("Try Again", onClick = it) }
                onSkip?.let { OnboardingTextButton(skipTitle, it) }
            }
        }
    }
}

@Composable
internal fun singleLine(text: String, size: Int, weight: FontWeight = FontWeight.Normal, color: Color = FLTheme.colors.textPrimary) =
    Text(text, fontSize = size.sp, fontWeight = weight, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
