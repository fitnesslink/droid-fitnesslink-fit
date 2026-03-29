package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.ProfileMenu
import com.fitnesslink.fit.persistence.DatabaseManager

class ProfileViewModel : ViewModel() {
    var menuItems by mutableStateOf<List<ProfileMenu>>(emptyList())
    var userName by mutableStateOf("Fitness User")
    var userEmail by mutableStateOf("user@fitnesslink.com")

    fun loadData() {
        menuItems = DatabaseManager.profileMenuItems()
        DatabaseManager.user()?.let {
            userName = it.name
            userEmail = it.email
        }
    }

    fun routeForMenuItem(item: ProfileMenu): String? {
        return when (item.id) {
            1 -> "personalInfo"
            2 -> "personalizationProfile"
            3 -> "analyticsProgress"
            4 -> "preferences"
            5 -> "billing"
            6 -> "goals"
            7 -> "measurements"
            8 -> "photos"
            9 -> "weight"
            10 -> "workoutReport"
            else -> null
        }
    }
}
