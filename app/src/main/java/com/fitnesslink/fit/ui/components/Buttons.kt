package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.White

@Composable
fun PrimaryButtonView(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(FLPrimary, RoundedCornerShape(100.dp))
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = White
        )
    }
}

@Composable
fun SecondaryButtonView(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(100.dp))
            .border(1.dp, FLPrimary, RoundedCornerShape(100.dp))
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = FLPrimary
        )
    }
}

@Composable
fun ActionButtonView(
    text: String,
    isEnabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isEnabled) FLPrimary else DisabledButton
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(bgColor, RoundedCornerShape(100.dp))
            .then(if (isEnabled && !isLoading) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = White, modifier = Modifier.size(24.dp))
        } else {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = if (isEnabled) FontWeight.SemiBold else FontWeight.Normal,
                color = White
            )
        }
    }
}

@Composable
fun SocialSignInButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .background(White, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (icon == "google") "G" else "f",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (icon == "google") Color(0xFFDB4437) else Color(0xFF4267B2)
        )
    }
}

@Composable
fun PlayButtonView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(83.dp)
            .background(FLPrimary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("\u25B6", fontSize = 30.sp, color = White)
    }
}

@Composable
fun PauseButtonView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(83.dp)
            .background(FLPrimary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("\u23F8", fontSize = 30.sp, color = White)
    }
}
