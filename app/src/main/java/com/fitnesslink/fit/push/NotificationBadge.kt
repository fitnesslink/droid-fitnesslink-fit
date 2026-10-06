package com.fitnesslink.fit.push

import android.util.Log
import com.fitnesslink.fit.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FTNSSLNK-261: the unread-notification count the header bell renders.
 *
 * Independent of `AuthManager` so UI can collect it without an auth
 * dependency. Refreshed from `GET notifications/me/unread-count` on every
 * foreground activation and whenever a push arrives; cleared on logout.
 */
object NotificationBadge {
    private const val TAG = "NotificationBadge"

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    /** Recompute from the server. Failures keep the last known value. */
    suspend fun refreshFromServer() {
        try {
            _unread.value = ApiClient.notificationApi.getUnreadCount().count
        } catch (e: Exception) {
            Log.w(TAG, "unread-count refresh failed: ${e.message}")
        }
    }

    /** Inbox opened or logout — nothing is unread any more from the UI's view. */
    fun clear() {
        _unread.value = 0
    }

    /** Local adjustment (e.g. a row marked read in the inbox) without a round trip. */
    fun set(count: Int) {
        _unread.value = count.coerceAtLeast(0)
    }

    fun decrement() {
        set(_unread.value - 1)
    }
}
