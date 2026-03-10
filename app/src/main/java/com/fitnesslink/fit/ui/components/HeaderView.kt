package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextSecondaryColor

@Composable
fun HeaderView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "FitnessLink",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = FLPrimary,
            modifier = Modifier.align(Alignment.Center)
        )
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = "Notifications",
            tint = TextSecondaryColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable { }
        )
    }
}

@Composable
fun HeaderBackView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = FLPrimary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onBack)
        )
        Text(
            text = "FitnessLink",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = FLPrimary,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
