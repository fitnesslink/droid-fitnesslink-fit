package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fitnesslink.fit.R

@Composable
fun HeaderView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.fitnesslink),
            contentDescription = "FitnessLink",
            modifier = Modifier
                .align(Alignment.Center)
                .width(134.dp)
        )
        Image(
            painter = painterResource(R.drawable.notificationbell),
            contentDescription = "Notifications",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(20.dp)
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
        Image(
            painter = painterResource(R.drawable.back),
            contentDescription = "Back",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(20.dp)
                .clickable(onClick = onBack)
        )
        Image(
            painter = painterResource(R.drawable.fitnesslink),
            contentDescription = "FitnessLink",
            modifier = Modifier
                .align(Alignment.Center)
                .width(134.dp)
        )
    }
}
