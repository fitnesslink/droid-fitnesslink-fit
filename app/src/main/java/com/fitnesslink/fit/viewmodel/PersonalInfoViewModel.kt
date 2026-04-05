package com.fitnesslink.fit.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.persistence.DatabaseManager
import java.io.File

class PersonalInfoViewModel : ViewModel() {
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var username by mutableStateOf("")
    var email by mutableStateOf("")
    var phone by mutableStateOf("")
    var country by mutableStateOf("")
    var isActive by mutableStateOf(true)
    var profileImage by mutableStateOf<Bitmap?>(null)
    var showDeleteConfirmation by mutableStateOf(false)

    private var userId = ""
    private var origFirstName = ""
    private var origLastName = ""
    private var origUsername = ""
    private var origEmail = ""
    private var origPhone = ""
    private var origCountry = ""

    val hasChanges: Boolean get() =
        firstName != origFirstName || lastName != origLastName ||
        username != origUsername || email != origEmail ||
        phone != origPhone || country != origCountry

    val initials: String get() {
        val f = firstName.firstOrNull()?.uppercase() ?: ""
        val l = lastName.firstOrNull()?.uppercase() ?: ""
        return "$f$l"
    }

    fun loadData() {
        val user = DatabaseManager.user() ?: return
        userId = user.id
        firstName = user.firstName; origFirstName = user.firstName
        lastName = user.lastName; origLastName = user.lastName
        username = user.username; origUsername = user.username
        email = user.email; origEmail = user.email
        phone = user.phone; origPhone = user.phone
        country = user.country; origCountry = user.country
        isActive = user.isActive
    }

    fun saveChanges() {
        if (userId.isEmpty()) return
        DatabaseManager.updateUserProfile(userId, firstName, lastName, email, phone, username, country)
        origFirstName = firstName; origLastName = lastName
        origUsername = username; origEmail = email
        origPhone = phone; origCountry = country
    }

    fun saveProfileImage(bytes: ByteArray, filesDir: File) {
        val imageId = "profile_$userId.jpg"
        val file = File(filesDir, imageId)
        file.writeBytes(bytes)
        profileImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        DatabaseManager.updateProfileImage(userId, imageId)
    }

    fun loadProfileImage(filesDir: File) {
        val user = DatabaseManager.user() ?: return
        if (user.profileImageId.isNotEmpty()) {
            val file = File(filesDir, user.profileImageId)
            if (file.exists()) {
                profileImage = BitmapFactory.decodeFile(file.absolutePath)
            }
        }
    }
}
