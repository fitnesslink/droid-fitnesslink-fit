package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.NotificationItem
import com.fitnesslink.fit.model.NotificationTab
import com.fitnesslink.fit.persistence.DatabaseManager

class NotificationsViewModel : ViewModel() {
    var notifications by mutableStateOf<List<NotificationItem>>(emptyList())
    var selectedTab by mutableStateOf(NotificationTab.ALL)
    var unreadCount by mutableIntStateOf(0)

    fun loadData() {
        val type = selectedTab.notificationType?.name?.lowercase()
        notifications = DatabaseManager.allNotifications(type)
        unreadCount = DatabaseManager.unreadNotificationCount()
    }

    fun markRead(id: String) {
        DatabaseManager.markNotificationRead(id)
        notifications = notifications.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        unreadCount = DatabaseManager.unreadNotificationCount()
    }

    fun markAllRead() {
        DatabaseManager.markAllNotificationsRead()
        notifications = notifications.map { it.copy(isRead = true) }
        unreadCount = 0
    }

    fun delete(id: String) {
        DatabaseManager.deleteNotification(id)
        notifications = notifications.filter { it.id != id }
        unreadCount = DatabaseManager.unreadNotificationCount()
    }

    val todayNotifications: List<NotificationItem>
        get() = notifications.filter { NotificationItem.isToday(it.createdAt) }

    val yesterdayNotifications: List<NotificationItem>
        get() = notifications.filter { NotificationItem.isYesterday(it.createdAt) }

    val earlierNotifications: List<NotificationItem>
        get() = notifications.filter {
            !NotificationItem.isToday(it.createdAt) && !NotificationItem.isYesterday(it.createdAt)
        }

    fun resolveDeepLink(link: String): String? {
        val parts = link.split("/")
        val base = parts.firstOrNull() ?: return null
        return when (base) {
            "personalInfo" -> "personalInfo"
            "workoutReport" -> "workoutReport"
            "nutritionReport" -> "nutritionReport"
            "goals" -> "goals"
            "programs" -> "programs"
            "workouts" -> "workouts"
            "groceryList" -> "groceryList"
            "workoutDetail" -> if (parts.size > 1) "workoutDetail/${parts[1]}" else "workouts"
            else -> null
        }
    }
}
