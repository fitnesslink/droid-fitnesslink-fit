package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.media.MediaURLProvider
import com.fitnesslink.fit.model.api.FLUser
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Date
import java.util.UUID

/**
 * Port of iOS `PersonalInfoViewModel`. Saves locally, then a full-body
 * `PUT core/users/{id}` (204), then re-reads `users/me`. The profile photo is
 * two steps: `POST core/media` (container `photos`) → `mediaId`, then the
 * same full-body PUT carrying `profileImageId`. `MediaRef.UserPhoto` is keyed
 * by that media asset id, not the user id.
 */
class PersonalInfoViewModel : ViewModel() {
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var username by mutableStateOf("")
    var email by mutableStateOf("")
    var phone by mutableStateOf("")
    var country by mutableStateOf("")
    var isActive by mutableStateOf(true)
    var memberSince by mutableStateOf<Date?>(null)
    var showDeleteConfirmation by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var saveError by mutableStateOf<String?>(null)
    var isUploadingPhoto by mutableStateOf(false)
    var photoUploadError by mutableStateOf<String?>(null)

    var userId by mutableStateOf("")
        private set
    var profileImageId by mutableStateOf<String?>(null)
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

    /** MediaRef for the user's profile photo (asset-id keyed), used by FLImageView. */
    val profilePhotoRef: MediaRef?
        get() = profileImageId?.takeIf { it.isNotBlank() }?.let { MediaRef.UserPhoto(it) }

    fun loadData() {
        val live = AuthManager.currentUser.value
        if (live != null) {
            apply(live)
            return
        }
        val user = DatabaseManager.user() ?: return
        userId = user.id
        firstName = user.firstName; origFirstName = user.firstName
        lastName = user.lastName; origLastName = user.lastName
        username = user.username; origUsername = user.username
        email = user.email; origEmail = user.email
        phone = user.phone; origPhone = user.phone
        country = user.country; origCountry = user.country
        isActive = user.isActive
        profileImageId = user.profileImageId.ifBlank { null }
    }

    private fun apply(user: FLUser) {
        userId = user.id.toString()
        firstName = user.firstName; origFirstName = user.firstName
        lastName = user.lastName; origLastName = user.lastName
        username = user.username; origUsername = user.username
        email = user.email; origEmail = user.email
        phone = user.phone.orEmpty(); origPhone = phone
        country = user.country.orEmpty(); origCountry = country
        isActive = user.isActive
        profileImageId = user.profileImageId?.toString()
        memberSince = user.audit.timestamp
    }

    /** The full user to PUT: the live object with the edited fields swapped in. */
    private fun currentUserBody(): FLUser? {
        val base = AuthManager.currentUser.value ?: return null
        return base.copy(
            firstName = firstName,
            lastName = lastName,
            email = email,
            phone = phone.ifBlank { null },
            username = username,
            country = country.ifBlank { null },
            profileImageId = profileImageId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: base.profileImageId
        )
    }

    fun saveChanges() {
        if (userId.isEmpty() || isSaving) return
        DatabaseManager.updateUserProfile(userId, firstName, lastName, email, phone, username, country)
        val previous = listOf(origFirstName, origLastName, origUsername, origEmail, origPhone, origCountry)
        origFirstName = firstName; origLastName = lastName
        origUsername = username; origEmail = email
        origPhone = phone; origCountry = country
        viewModelScope.launch {
            isSaving = true
            saveError = null
            try {
                val body = currentUserBody() ?: return@launch
                ApiClient.userApi.update(userId, body)
                refreshFromServer()
            } catch (e: Exception) {
                // Revert the "saved" baseline so Save re-enables with the user's edits intact.
                origFirstName = previous[0]; origLastName = previous[1]; origUsername = previous[2]
                origEmail = previous[3]; origPhone = previous[4]; origCountry = previous[5]
                saveError = "Couldn't save your changes. Check your connection and try again."
            } finally {
                isSaving = false
            }
        }
    }

    fun uploadProfilePhoto(bytes: ByteArray, mimeType: String = "image/jpeg") {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            isUploadingPhoto = true
            photoUploadError = null
            try {
                val ext = if (mimeType.contains("png")) "png" else "jpg"
                val filename = "profile-${UUID.randomUUID()}.$ext"
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull(), 0, bytes.size)
                val part = MultipartBody.Part.createFormData("file", filename, requestBody)
                val container = "photos".toRequestBody("text/plain".toMediaTypeOrNull())
                val uploaded = ApiClient.mediaApi.upload(container, part)

                val oldRef = profilePhotoRef
                val base = currentUserBody() ?: throw IllegalStateException("no signed-in user")
                ApiClient.userApi.update(userId, base.copy(profileImageId = UUID.fromString(uploaded.mediaId)))
                profileImageId = uploaded.mediaId
                withContext(Dispatchers.IO) { DatabaseManager.updateProfileImage(userId, uploaded.mediaId) }
                oldRef?.let { MediaURLProvider.invalidate(it) }
                refreshFromServer()
            } catch (_: Exception) {
                photoUploadError = "Upload failed. Please try again."
            } finally {
                isUploadingPhoto = false
            }
        }
    }

    /**
     * Re-read `users/me` and persist it. `AuthManager.currentUser` itself is
     * owned by the auth agent (its setter is private); until the integrator
     * exposes `AuthManager.refreshCurrentUser()` the live object stays stale
     * until the next session restore, while this screen and the DB are fresh.
     */
    private suspend fun refreshFromServer() {
        try {
            val fresh = ApiClient.userApi.getMe()
            withContext(Dispatchers.IO) { DatabaseManager.saveUserFromApi(fresh) }
            userId = fresh.id.toString()
            profileImageId = fresh.profileImageId?.toString()
            isActive = fresh.isActive
            memberSince = fresh.audit.timestamp
        } catch (_: Exception) {
        }
    }
}
