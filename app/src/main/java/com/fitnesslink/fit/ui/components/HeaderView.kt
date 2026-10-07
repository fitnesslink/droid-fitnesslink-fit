package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.push.NotificationBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tab-root header (iOS `HeaderView`): centred wordmark and a notification
 * bell with a red unread badge (capped at 99). The count is the larger of the
 * server-driven [NotificationBadge.unread] and the locally cached unread rows,
 * so a push that landed while offline and an inbox read in-app both reflect.
 */
@Composable
fun HeaderView(modifier: Modifier = Modifier, onNotificationsTap: () -> Unit = {}) {
    val serverUnread by NotificationBadge.unread.collectAsState()
    val localUnread by produceState(initialValue = 0, key1 = serverUnread) {
        value = withContext(Dispatchers.IO) {
            runCatching { DatabaseManager.unreadNotificationCount() }.getOrDefault(0)
        }
    }
    val unreadCount = maxOf(serverUnread, localUnread)

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
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable(onClick = onNotificationsTap)
        ) {
            Image(
                painter = painterResource(R.drawable.notificationbell),
                contentDescription = if (unreadCount > 0) "Notifications, $unreadCount unread" else "Notifications",
                modifier = Modifier.size(20.dp)
            )
            if (unreadCount > 0) {
                UnreadBadge(
                    count = unreadCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-6).dp)
                )
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .background(Color.Red, CircleShape)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = minOf(count, 99).toString(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1
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
