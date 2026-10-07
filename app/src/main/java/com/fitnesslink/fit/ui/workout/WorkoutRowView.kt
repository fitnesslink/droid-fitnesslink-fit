package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.WorkoutList
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.components.SharedBadge
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * Tappable heart used on catalog cards / rows. With a null [onToggle] it
 * renders display-only (iOS `FavoriteHeartButton`).
 */
@Composable
fun FavoriteHeartButton(isFavorite: Boolean, onToggle: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(if (isFavorite) R.drawable.heartselected else R.drawable.heart),
        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
        modifier = modifier
            .size(20.dp)
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
    )
}

/** Thumbnail ref for a list row: thumbnail first, image as the fallback. */
fun WorkoutList.thumbRef(): MediaRef? = (thumbnailId ?: imageId)?.let { MediaRef.WorkoutThumbnail(it) }

/**
 * Compact single-row presentation of a workout — the "list" layout
 * counterpart to the card (iOS `WorkoutRowView`).
 */
@Composable
fun WorkoutRowView(
    workout: WorkoutList,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null
) {
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
            ref = workout.thumbRef(),
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workout.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (workout.time.isNotEmpty()) {
                Text(text = workout.time, fontSize = 13.sp, color = TextSecondaryColor, maxLines = 1)
            }
            if (workout.isSharedWithMe) {
                Spacer(modifier = Modifier.height(4.dp))
                SharedBadge()
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        FavoriteHeartButton(isFavorite = workout.isFavorite, onToggle = onToggleFavorite)
    }
}
