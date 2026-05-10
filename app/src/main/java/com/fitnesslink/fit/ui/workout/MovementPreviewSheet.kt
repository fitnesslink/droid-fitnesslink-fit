package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * Modal preview for a movement before the user commits to adding it
 * to the workout. Shows demo thumbnail, description, and the muscle
 * group + equipment as chips. Add-to-workout flows through the same
 * onAdd callback the row tap uses.
 */
@Composable
fun MovementPreviewSheet(
    movement: MovementLibraryItem,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(White)
        ) {
            // Header thumbnail (server resolves the demo video frame to a
            // poster image; if unavailable, FLImageView shows its placeholder).
            FLImageView(
                ref = MediaRef.MovementThumbnail(movement.id),
                height = 180.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            )

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = movement.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (movement.muscleGroup.isNotBlank()) {
                        Chip(label = movement.muscleGroup)
                    }
                    if (movement.equipment.isNotBlank()) {
                        Chip(label = movement.equipment)
                    }
                }

                if (movement.description.isNotBlank()) {
                    Text(
                        text = movement.description,
                        fontSize = 14.sp,
                        color = TextSecondaryColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onAdd,
                        colors = ButtonDefaults.buttonColors(containerColor = FLPrimary)
                    ) {
                        Text("Add to workout")
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundColor)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondaryColor
        )
    }
}
