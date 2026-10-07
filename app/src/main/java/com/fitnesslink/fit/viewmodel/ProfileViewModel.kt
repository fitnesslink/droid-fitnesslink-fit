package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.model.ProfileMenu
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.navigation.ProfileRouteNames

/**
 * Port of iOS `ProfileViewModel`. The menu is static (iOS order and labels)
 * — the `profile_menus` table / `MockDataProvider.profileMenuItems` are no
 * longer read. Android-only entries are appended after the iOS list.
 */
class ProfileViewModel : ViewModel() {
    var menuItems by mutableStateOf(defaultMenu)
        private set
    var userName by mutableStateOf("")
    var userEmail by mutableStateOf("")

    /** Name / email: `AuthManager.currentUser` first, the cached DB row as a fallback. */
    fun loadData() {
        val live = AuthManager.currentUser.value
        if (live != null) {
            userName = live.fullName.trim()
            userEmail = live.email
            return
        }
        DatabaseManager.user()?.let {
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
        3 -> "nutritionReport"
        4 -> "preferences"
        5 -> "billing"
        6 -> "goals"
        7 -> "measurements"
        8 -> "photos"
        9 -> "weight"
        10 -> "workoutReport"
        11 -> "achievements"
        12 -> ProfileRouteNames.NOTIFICATION_PREFERENCES
        13 -> "calendarSyncSettings"
        14 -> ProfileRouteNames.MY_VIDEOS
        else -> null
    }

    companion object {
        /** iOS `ProfileViewModel.defaultMenu` order, then the Android extras. */
        val defaultMenu: List<ProfileMenu> = listOf(
            ProfileMenu(id = 1, text = "Personal Info"),
            ProfileMenu(id = 2, text = "Personalization"),
            ProfileMenu(id = 6, text = "Goals"),
            ProfileMenu(id = 7, text = "Measurements"),
            ProfileMenu(id = 9, text = "Weight Log"),
            ProfileMenu(id = 8, text = "Progress Photos"),
            ProfileMenu(id = 10, text = "Workout Report"),
            ProfileMenu(id = 3, text = "Nutrition Report"),
            ProfileMenu(id = 4, text = "Preferences"),
            ProfileMenu(id = 5, text = "Subscription"),
            // Android extras (Appendix B)
            ProfileMenu(id = 11, text = "Achievements"),
            ProfileMenu(id = 12, text = "Notifications"),
            ProfileMenu(id = 13, text = "Calendar Sync"),
            ProfileMenu(id = 14, text = "My Videos")
        )
    }
}
