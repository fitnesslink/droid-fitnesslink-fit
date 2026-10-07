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
import com.fitnesslink.fit.push.NotificationBadge
import com.fitnesslink.fit.ui.navigation.DeepLinkResolver
import com.fitnesslink.fit.ui.notifications.NotificationSearch
import kotlinx.coroutines.launch

/**
 * Port of iOS `NotificationsViewModel`: local rows first, then
 * `GET notifications/me` + the server unread count. Search narrows the
 * selected tab (see [NotificationSearch]); deep links resolve through the
 * shared [DeepLinkResolver].
 */
class NotificationsViewModel : ViewModel() {
    var notifications by mutableStateOf<List<NotificationItem>>(emptyList())
    var selectedTab by mutableStateOf(NotificationTab.ALL)
    /** Live inbox filter; empty shows everything in the selected tab. */
    var searchText by mutableStateOf("")
    var unreadCount by mutableIntStateOf(0)
    var isRefreshing by mutableStateOf(false)
        private set

    fun loadData() {
        val type = selectedTab.notificationType?.name?.lowercase()
        notifications = DatabaseManager.allNotifications(type)
        unreadCount = DatabaseManager.unreadNotificationCount()
        viewModelScope.launch { refreshFromServer() }
    }

    fun markRead(id: String) {
        val wasUnread = notifications.firstOrNull { it.id == id }?.isRead == false
        DatabaseManager.markNotificationRead(id)
        notifications = notifications.map { if (it.id == id) it.copy(isRead = true) else it }
        unreadCount = DatabaseManager.unreadNotificationCount()
        if (wasUnread) NotificationBadge.decrement()
        viewModelScope.launch {
            try { ApiClient.notificationApi.markRead(id) } catch (_: Exception) {}
        }
    }

    fun markAllRead() {
        DatabaseManager.markAllNotificationsRead()
        notifications = notifications.map { it.copy(isRead = true) }
        unreadCount = 0
        NotificationBadge.clear()
        viewModelScope.launch {
            try { ApiClient.notificationApi.markAllRead() } catch (_: Exception) {}
        }
    }

    /** Local only (iOS parity) — there is no server delete. */
    fun delete(id: String) {
        DatabaseManager.deleteNotification(id)
        notifications = notifications.filter { it.id != id }
        unreadCount = DatabaseManager.unreadNotificationCount()
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        isRefreshing = true
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
            unreadCount = try {
                ApiClient.notificationApi.getUnreadCount().count
            } catch (_: Exception) {
                DatabaseManager.unreadNotificationCount()
            }
        } catch (_: Exception) {
            // use cached
        } finally {
            isRefreshing = false
        }
    }

    /** The selected tab's rows narrowed by the search box. */
    val visibleNotifications: List<NotificationItem>
        get() {
            val q = searchText.trim()
            if (q.isEmpty()) return notifications
            return notifications.filter { NotificationSearch.matches(q, it.title, it.body) }
        }

    val isSearching: Boolean get() = searchText.isNotBlank()

    val todayNotifications: List<NotificationItem>
        get() = visibleNotifications.filter { NotificationItem.isToday(it.createdAt) }

    val yesterdayNotifications: List<NotificationItem>
        get() = visibleNotifications.filter { NotificationItem.isYesterday(it.createdAt) }

    val earlierNotifications: List<NotificationItem>
        get() = visibleNotifications.filter {
            !NotificationItem.isToday(it.createdAt) && !NotificationItem.isYesterday(it.createdAt)
        }

    /** FTNSSLNK-234/261: routing table lives in [DeepLinkResolver]; kept for existing call sites. */
    fun resolveDeepLink(link: String): String? = DeepLinkResolver.resolve(link)
}
