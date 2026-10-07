package com.fitnesslink.fit.ui.programs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.api.ProgramContributor
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * "About the Coach" (iOS `ProgramContributorView`): the contributor's name,
 * headline, bio, credentials, achievements and social / website links.
 * Driven by the `ProgramContributor` embedded in `/programs/{id}`. Callers
 * hide it for the FitnessLink house contributor (`ContributorStore.isHouse`).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ProgramContributorCard(contributor: ProgramContributor, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) Text("About the Coach", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val avatarId = contributor.avatarMediaAssetId?.toString()
                if (avatarId != null) {
                    FLImageView(ref = MediaRef.UserPhoto(avatarId), modifier = Modifier.size(52.dp).clip(CircleShape))
                } else {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(52.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(contributor.displayName ?: "Coach", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                    contributor.headline?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = TextSecondaryColor) }
                }
            }

            contributor.bio?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = TextSecondaryColor) }

            val credentials = contributor.credentials.sortedBy { it.sortOrder }
            if (credentials.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Credentials", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                    credentials.forEach { credential ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(15.dp))
                            Text(credential.displayText, fontSize = 13.sp, color = TextPrimaryColor)
                        }
                    }
                }
            }

            val achievements = contributor.achievements.sortedBy { it.sortOrder }
            if (achievements.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Achievements", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryColor)
                    achievements.forEach { achievement ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(15.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(achievement.displayTitle, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                                achievement.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = TextSecondaryColor) }
                            }
                        }
                    }
                }
            }

            val links = contributor.socialLinks.sortedBy { it.sortOrder }.filter { it.url.isNotBlank() }
            if (links.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    links.forEach { link ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(FLPrimary.copy(alpha = 0.12f))
                                .clickable {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
                            Text(link.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                        }
                    }
                }
            }
        }
    }
}
