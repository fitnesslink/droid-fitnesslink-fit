package com.fitnesslink.fit.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.PhotoAngle
import com.fitnesslink.fit.model.ProgressPhotoEntry
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.progress.BodyTrackingStore
import com.fitnesslink.fit.progress.PhotoCaptureFlow
import com.fitnesslink.fit.progress.PhotoCaptureState
import kotlinx.coroutines.launch
import java.util.Date

/**
 * iOS `PhotoCaptureViewModel`: Front → Side → Back, one pending image per
 * angle (a URI here, bytes on iOS) and per-angle notes, then a two-hop
 * upload per captured angle through `BodyTrackingStore.uploadPhotos`.
 */
class PhotoCaptureViewModel : ViewModel() {
    var state by mutableStateOf(PhotoCaptureState())
        private set
    /** Pending image per angle until the upload lands. */
    var pendingUris by mutableStateOf<Map<PhotoAngle, Uri>>(emptyMap())
        private set
    var notes by mutableStateOf<Map<PhotoAngle, String>>(emptyMap())
        private set
    var isUploading by mutableStateOf(false)
        private set
    var uploadError by mutableStateOf<String?>(null)
        private set
    var savedEntry by mutableStateOf<ProgressPhotoEntry?>(null)
        private set

    val currentAngle: PhotoAngle get() = state.currentAngle
    val anglesCompleted: Int get() = state.anglesCompleted
    val hasCurrentPhoto: Boolean get() = state.hasCurrentPhoto
    val isComplete: Boolean get() = state.isComplete
    val currentUri: Uri? get() = pendingUris[state.currentAngle]
    val currentNotes: String get() = notes[state.currentAngle] ?: ""

    fun setCurrentNotes(value: String) {
        notes = notes + (state.currentAngle to value)
    }

    fun setPhoto(uri: Uri, angle: PhotoAngle = state.currentAngle) {
        pendingUris = pendingUris + (angle to uri)
        state = PhotoCaptureFlow.capture(state, angle)
    }

    fun retakeCurrentAngle() {
        pendingUris = pendingUris - state.currentAngle
        state = PhotoCaptureFlow.retake(state)
    }

    fun skipAngle() { state = PhotoCaptureFlow.skip(state) }

    fun moveToNextAngle() { state = PhotoCaptureFlow.advance(state) }

    fun finishNow() { state = PhotoCaptureFlow.finishNow(state) }

    fun dismissError() { uploadError = null }

    /** Upload every captured angle (iOS `uploadAndBuildEntry`); reopens the flow on failure. */
    fun upload(date: Date = Date()) {
        if (isUploading) return
        val photos = PhotoAngle.entries.mapNotNull { angle -> pendingUris[angle]?.let { angle to it } }
        if (photos.isEmpty()) { state = PhotoCaptureFlow.reopen(state); return }
        isUploading = true
        uploadError = null
        viewModelScope.launch {
            try {
                savedEntry = BodyTrackingStore.uploadPhotos(photos, date, notes = "", perAngleNotes = notes)
            } catch (e: Exception) {
                val detail = when (e) {
                    is ApiError -> e.message
                    else -> e.localizedMessage ?: e.javaClass.simpleName
                }
                uploadError = "Upload failed: $detail"
                state = PhotoCaptureFlow.reopen(state)
            } finally {
                isUploading = false
            }
        }
    }
}
