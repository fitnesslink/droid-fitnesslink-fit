package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnesslink.fit.model.NutritionCitation
import com.fitnesslink.fit.model.NutritionMethod
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme

/** Full-screen Sources & Methods: each calculation with its method and linked citations. */
@Composable
fun NutritionSourcesDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(FLTheme.colors.background).systemBarsPadding()) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    "Sources & Methods",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FLTheme.colors.textPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text("Done", color = FLPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "FitnessLink's nutrition numbers are estimates for general fitness and food tracking. They are not medical advice and don't diagnose or treat any condition. Talk to your doctor or a registered dietitian before changing your diet, especially if you are pregnant, breastfeeding, or managing a health condition.",
                    fontSize = 13.sp,
                    color = FLTheme.colors.textSecondary
                )
                NutritionMethod.all.forEach { MethodCard(it) }
            }
        }
    }
}

@Composable
private fun MethodCard(method: NutritionMethod) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth().background(FLTheme.colors.surface, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(method.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
        Text(method.explanation, fontSize = 13.sp, color = FLTheme.colors.textSecondary)
        method.citations.forEach { citation ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { runCatching { uriHandler.openUri(citation.url) } },
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(citation.reference, fontSize = 12.sp, color = FLPrimary, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Opens in browser",
                    tint = FLPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** Compact link that sits inside a card, next to the numbers it explains. */
@Composable
fun NutritionSourcesLink(title: String = "How this is calculated · Sources", modifier: Modifier = Modifier) {
    var showSources by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.clickable(role = Role.Button) { showSources = true }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
    }
    if (showSources) NutritionSourcesDialog(onDismiss = { showSources = false })
}

/** Full-width row for stacked screens. */
@Composable
fun NutritionSourcesRow(modifier: Modifier = Modifier) {
    var showSources by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(FLTheme.colors.surface, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { showSources = true }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(FLPrimary.copy(alpha = 0.12f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Sources & Methods", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textPrimary)
            Text("The research behind these numbers", fontSize = 12.sp, color = FLTheme.colors.textSecondary)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = FLTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
    }
    if (showSources) NutritionSourcesDialog(onDismiss = { showSources = false })
}

/** Linked citations shown under a "Why this number?" explanation. */
@Composable
fun NutritionCitationLinks(citations: List<NutritionCitation>) {
    if (citations.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Sources", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FLTheme.colors.textSecondary)
        citations.forEach { citation ->
            Text(
                "${citation.label} ↗",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = FLPrimary,
                modifier = Modifier.clickable(role = Role.Button) { runCatching { uriHandler.openUri(citation.url) } }
            )
        }
    }
}
