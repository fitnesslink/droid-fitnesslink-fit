package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.NotificationItem
import com.fitnesslink.fit.model.NotificationTab
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch

class NotificationsViewModel : ViewModel() {
    var notifications by mutableStateOf<List<NotificationItem>>(emptyList())
    var selectedTab by mutableStateOf(NotificationTab.ALL)
    var unreadCount by mutableIntStateOf(0)

    fun loadData() {
        val type = selectedTab.notificationType?.name?.lowercase()
        notifications = DatabaseManager.allNotifications(type)
        unreadCount = DatabaseManager.unreadNotificationCount()
        viewModelScope.launch { refreshFromServer() }
    }

    fun markRead(id: String) {
        DatabaseManager.markNotificationRead(id)
        notifications = notifications.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        unreadCount = DatabaseManager.unreadNotificationCount()
        viewModelScope.launch {
            try { ApiClient.notificationApi.markRead(id) } catch (_: Exception) {}
        }
    }

    fun markAllRead() {
        DatabaseManager.markAllNotificationsRead()
        notifications = notifications.map { it.copy(isRead = true) }
        unreadCount = 0
        viewModelScope.launch {
            try { ApiClient.notificationApi.markAllRead() } catch (_: Exception) {}
        }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val response = ApiClient.notificationApi.getMyNotifications()
            response.items.forEach {
                DatabaseManager.insertNotification(
                    it.id, it.type.name.lowercase(), it.title, it.body,
                    it.isRead, it.createdAt.time, it.deepLink
                )
            }
            val type = selectedTab.notificationType?.name?.lowercase()
            notifications = DatabaseManager.allNotifications(type)
            unreadCount = DatabaseManager.unreadNotificationCount()
        } catch (_: Exception) { /* use cached */ }
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
