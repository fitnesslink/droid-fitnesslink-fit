package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.HighlightState

/**
 * Small "Shared" pill for content rows + detail headers when the current
 * user got access via a content share (iOS `SharedBadge`). Pass
 * [sharedByName] when already resolved; otherwise it reads a generic "Shared".
 */
@Composable
fun SharedBadge(sharedByName: String? = null, modifier: Modifier = Modifier) {
    val label = sharedByName?.takeIf { it.isNotBlank() }?.let { "Shared by $it" } ?: "Shared"
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(HighlightState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = FLPrimary,
            modifier = Modifier.size(11.dp)
        )
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = FLPrimary, maxLines = 1)
    }
}
