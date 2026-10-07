package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.api.SharedContent
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * Accept / Decline card shown at the top of a workout, program or movement
 * detail while a share of it awaits this user's decision (iOS
 * `PendingShareBanner`). Presentational: the accept/decline network calls are
 * the caller's job. This overload tracks its own busy state — it dims and
 * disables the buttons once either is tapped and resets when [share] changes
 * (status flips or a different share arrives). Use the overload with an
 * explicit `isBusy` when the caller wants to drive it (e.g. to re-enable on
 * failure).
 */
@Composable
fun PendingShareBanner(share: SharedContent, kindLabel: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    var busy by remember(share.id, share.status) { mutableStateOf(false) }
    PendingShareBanner(
        isBusy = busy,
        share = share,
        kindLabel = kindLabel,
        onAccept = { busy = true; onAccept() },
        onDecline = { busy = true; onDecline() }
    )
}

/** Caller-driven loading state variant (iOS `isBusy`). */
@Composable
fun PendingShareBanner(
    isBusy: Boolean,
    share: SharedContent,
    kindLabel: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sharer = share.sharedByUserName.ifBlank { "Someone" }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FLPrimary.copy(alpha = 0.08f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = FLPrimary,
                modifier = Modifier.size(16.dp).padding(top = 1.dp)
            )
            Text(
                text = "$sharer shared this $kindLabel with you",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor
            )
        }
        Text(
            text = "Accept to add it to your library, or decline to leave it out.",
            fontSize = 13.sp,
            color = TextSecondaryColor
        )
        Row(
            modifier = Modifier.fillMaxWidth().alpha(if (isBusy) 0.6f else 1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onAccept,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FLPrimary, contentColor = White)
            ) {
                Text("Accept", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onDecline,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, FLPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FLPrimary)
            ) {
                Text("Decline", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
