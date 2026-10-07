package com.fitnesslink.fit.ui.movements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.api.MovementSection
import com.fitnesslink.fit.model.api.MovementStep
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * Renders a movement's plain-text summary plus its typed, ordered sections
 * (Setup / Execution / Breathe / Tips). Port of iOS `MovementContentView`;
 * shared by Movement Detail and the preview sheet. Empty input renders nothing.
 */
@Composable
fun MovementContentView(
    summary: String?,
    sections: List<MovementSection>,
    modifier: Modifier = Modifier
) {
    if (summary.isNullOrBlank() && sections.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!summary.isNullOrBlank()) {
            Text(summary, fontSize = 15.sp, color = TextPrimaryColor)
        }
        sections.sortedBy { it.ordinal }.forEach { section ->
            MovementSectionCard(section)
        }
    }
}

private data class SectionStyle(val icon: ImageVector, val tint: Color)

private fun styleFor(type: String): SectionStyle = when (type.lowercase()) {
    "setup" -> SectionStyle(Icons.AutoMirrored.Filled.List, FLPrimary)
    "execution" -> SectionStyle(Icons.Default.DirectionsRun, FLPrimary)
    "breathe" -> SectionStyle(Icons.Default.Air, BlueTheme)
    "tips" -> SectionStyle(Icons.Default.Lightbulb, OrangeTheme)
    else -> SectionStyle(Icons.AutoMirrored.Filled.List, FLPrimary)
}

@Composable
private fun MovementSectionCard(section: MovementSection) {
    val style = styleFor(section.type)
    val steps = section.steps.sortedBy { it.ordinal }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(style.tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(style.icon, contentDescription = null, tint = style.tint, modifier = Modifier.size(14.dp))
            }
            Text(section.type, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
        }
        when (section.type.lowercase()) {
            "breathe" -> BreatheSectionBody(steps, section.note, style.tint)
            "tips" -> TipsSectionBody(steps, style.tint)
            else -> NumberedStepsBody(steps, section.note, style.tint)
        }
    }
}

@Composable
private fun NumberedStepsBody(steps: List<MovementStep>, note: String?, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!step.label.isNullOrEmpty()) {
                        Text(step.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                    }
                    Text(step.text, fontSize = 14.sp, color = TextPrimaryColor)
                }
            }
        }
        if (!note.isNullOrEmpty()) SectionNote(note, tint)
    }
}

@Composable
private fun BreatheSectionBody(steps: List<MovementStep>, note: String?, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            steps.forEach { step ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.08f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!step.label.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                if (step.label.equals("exhale", ignoreCase = true)) Icons.Default.NorthEast else Icons.Default.SouthEast,
                                contentDescription = null, tint = tint, modifier = Modifier.size(11.dp)
                            )
                            Text(step.label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
                        }
                    }
                    Text(step.text, fontSize = 13.sp, color = TextPrimaryColor)
                }
            }
        }
        if (!note.isNullOrEmpty()) SectionNote(note, tint)
    }
}

@Composable
private fun TipsSectionBody(steps: List<MovementStep>, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEach { step ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Text(step.text, fontSize = 14.sp, color = TextPrimaryColor)
            }
        }
    }
}

@Composable
private fun SectionNote(text: String, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.06f))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = TextSecondaryColor)
    }
}

/**
 * Small tinted tag (anatomy = brand green, equipment = blue), the iOS
 * `chip(text:icon:color:)` used on detail + preview.
 */
@Composable
fun MovementTagChip(text: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
