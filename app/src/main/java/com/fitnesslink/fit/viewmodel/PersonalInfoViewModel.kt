package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.media.MediaURLProvider
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class PersonalInfoViewModel : ViewModel() {
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var username by mutableStateOf("")
    var email by mutableStateOf("")
    var phone by mutableStateOf("")
    var country by mutableStateOf("")
    var isActive by mutableStateOf(true)
    var showDeleteConfirmation by mutableStateOf(false)
    var isUploadingPhoto by mutableStateOf(false)
    var photoUploadError by mutableStateOf<String?>(null)

    var userId by mutableStateOf("")
        private set

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

    /** MediaRef for the user's profile photo, used by FLImageView. */
    val profilePhotoRef: MediaRef?
        get() = if (userId.isEmpty()) null else MediaRef.UserPhoto(userId)

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
        viewModelScope.launch {
            try {
                val current = AuthManager.currentUser.value ?: return@launch
                val updated = current.copy(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    phone = phone,
                    username = username,
                    country = country
                )
                ApiClient.userApi.update(current.id.toString(), updated)
            } catch (_: Exception) {}
        }
    }

    fun uploadProfilePhoto(bytes: ByteArray) {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            isUploadingPhoto = true
            photoUploadError = null
            try {
                val filename = "$userId.jpg"
                val mediaType = "image/jpeg".toMediaTypeOrNull()
                val requestBody = bytes.toRequestBody(mediaType, 0, bytes.size)
                val part = MultipartBody.Part.createFormData("file", filename, requestBody)
                val updated = ApiClient.userApi.uploadProfilePhoto(part)

                updated.profileImageId?.toString()?.let { imageId ->
                    DatabaseManager.updateProfileImage(userId, imageId)
                }
                // Same UserPhoto MediaRef keys for this user - force a refresh.
                MediaURLProvider.invalidate(MediaRef.UserPhoto(userId))
            } catch (_: Exception) {
                photoUploadError = "Upload failed. Please try again."
            } finally {
                isUploadingPhoto = false
            }
        }
    }
}
