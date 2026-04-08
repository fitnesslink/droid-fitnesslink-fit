package com.fitnesslink.fit.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.NotificationItem
import com.fitnesslink.fit.model.NotificationTab
import com.fitnesslink.fit.model.NotificationType
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.*
import com.fitnesslink.fit.viewmodel.NotificationsViewModel

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val viewModel: NotificationsViewModel = viewModel()

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        // Title + Mark All Read
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Notifications", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            if (viewModel.unreadCount > 0) {
                TextButton(onClick = { viewModel.markAllRead() }) {
                    Text("Mark All Read", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                }
            }
        }

        // Tab picker
        LazyRow(
            modifier = Modifier.padding(vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(NotificationTab.entries) { tab ->
                val isSelected = viewModel.selectedTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) FLPrimary else BackgroundColor)
                        .clickable {
                            viewModel.selectedTab = tab
                            viewModel.loadData()
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(tab.label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = if (isSelected) White else TextPrimaryColor)
                }
            }
        }

        // Notification list
        if (viewModel.notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NotificationsOff, contentDescription = null,
                        tint = MediumGray, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No notifications", fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        color = TextSecondaryColor)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                val today = viewModel.todayNotifications
                val yesterday = viewModel.yesterdayNotifications
                val earlier = viewModel.earlierNotifications

                if (today.isNotEmpty()) {
                    item {
                        Text("Today", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    items(today, key = { it.id }) { notification ->
                        NotificationRowView(
                            notification = notification,
                            onTap = {
                                viewModel.markRead(notification.id)
                                notification.deepLink?.let { link ->
                                    viewModel.resolveDeepLink(link)?.let { route -> onNavigate(route) }
                                }
                            },
                            onDelete = { viewModel.delete(notification.id) }
                        )
                    }
                }

                if (yesterday.isNotEmpty()) {
                    item {
                        Text("Yesterday", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    items(yesterday, key = { it.id }) { notification ->
                        NotificationRowView(
                            notification = notification,
                            onTap = {
                                viewModel.markRead(notification.id)
                                notification.deepLink?.let { link ->
                                    viewModel.resolveDeepLink(link)?.let { route -> onNavigate(route) }
                                }
                            },
                            onDelete = { viewModel.delete(notification.id) }
                        )
                    }
                }

                if (earlier.isNotEmpty()) {
                    item {
                        Text("Earlier", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    items(earlier, key = { it.id }) { notification ->
                        NotificationRowView(
                            notification = notification,
                            onTap = {
                                viewModel.markRead(notification.id)
                                notification.deepLink?.let { link ->
                                    viewModel.resolveDeepLink(link)?.let { route -> onNavigate(route) }
                                }
                            },
                            onDelete = { viewModel.delete(notification.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationRowView(
    notification: NotificationItem,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = White)
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
                .clickable(onClick = onTap)
                .padding(vertical = 10.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Unread dot
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (notification.isRead) Color.Transparent else BlueTheme)
            )

            // Type icon
            val icon = notificationIcon(notification.type)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(notification.type.color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = notification.type.color, modifier = Modifier.size(16.dp))
            }

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notification.title,
                        fontSize = 14.sp,
                        fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(notification.timeAgo, fontSize = 11.sp, color = TextSecondaryColor)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    notification.body,
                    fontSize = 13.sp,
                    color = TextSecondaryColor,
                    maxLines = 2
                )
            }
        }
    }
}

private fun notificationIcon(type: NotificationType): ImageVector = when (type) {
    NotificationType.SYSTEM -> Icons.Default.Campaign
    NotificationType.GOALS -> Icons.Default.TrackChanges
    NotificationType.CONTENT -> Icons.Default.MenuBook
    NotificationType.CALENDAR -> Icons.Default.CalendarMonth
}
