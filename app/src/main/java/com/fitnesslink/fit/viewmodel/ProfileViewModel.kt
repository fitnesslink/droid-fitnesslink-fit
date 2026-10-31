package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.ProfileMenu
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.navigation.ProfileRouteNames

/**
 * Port of iOS `ProfileViewModel` — the Profile menu opened from the header
 * avatar (Navigation PRD §6). Account items only: Goals, Measurements,
 * Weight Log, Progress Photos and the reports moved to the Progress tab.
 * Android-only entries are appended after the iOS list.
 */
class ProfileViewModel : ViewModel() {
    var menuItems by mutableStateOf(defaultMenu)
        private set
    var userName by mutableStateOf("")
    var userEmail by mutableStateOf("")
    var profileImageId by mutableStateOf<String?>(null)

    val photoRef: MediaRef? get() = profileImageId?.let { MediaRef.UserPhoto(it) }

    val initials: String get() = initialsOf(userName)

    /** Name / email: `AuthManager.currentUser` first, the cached DB row as a fallback; photo from the DB row. */
    fun loadData() {
        val cached = runCatching { DatabaseManager.user() }.getOrNull()
        profileImageId = cached?.profileImageId?.takeIf { it.isNotBlank() }
        val live = AuthManager.currentUser.value
        if (live != null) {
            userName = live.fullName.trim()
            userEmail = live.email
            if (profileImageId == null) profileImageId = live.profileImageId?.toString()
            return
        }
        cached?.let {
            userName = it.name
            userEmail = it.email
        }
    }

    /** Full logout (iOS `AuthManager.logout` order); fire-and-forget. */
    fun logout() {
        AuthManager.logout()
    }

    fun routeForMenuItem(item: ProfileMenu): String? = when (item.id) {
        1 -> "personalInfo"
        2 -> "personalizationProfile"
        4 -> "preferences"
        5 -> "billing"
        12 -> "connectedApps"
        13 -> ProfileRouteNames.NOTIFICATION_PREFERENCES
        14 -> "calendarSyncSettings"
        15 -> ProfileRouteNames.MY_VIDEOS
        else -> null
    }

    companion object {
        /**
         * iOS `ProfileViewModel.defaultMenu` order (minus Connect Apple TV,
         * which has no Android counterpart), then the Android extras.
         */
        val defaultMenu: List<ProfileMenu> = listOf(
            ProfileMenu(id = 1, text = "Personal Information", icon = "person"),
            ProfileMenu(id = 2, text = "Personalization", icon = "pencil"),
            ProfileMenu(id = 4, text = "Preferences", icon = "gear"),
            ProfileMenu(id = 5, text = "Subscription", icon = "card"),
            ProfileMenu(id = 12, text = "Connected Apps & Devices", icon = "watch"),
            // Android extras (Appendix B)
            ProfileMenu(id = 13, text = "Notifications", icon = "bell"),
            ProfileMenu(id = 14, text = "Calendar Sync", icon = "calendar"),
            ProfileMenu(id = 15, text = "My Videos", icon = "video")
        )

        /** "Quincy Williams" → "QW"; up to two initials. */
        fun initialsOf(name: String): String =
            name.split(' ').filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")
    }
}
