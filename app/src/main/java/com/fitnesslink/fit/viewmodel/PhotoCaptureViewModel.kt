package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.*
import java.util.Date

class PhotoCaptureViewModel : ViewModel() {
    var currentAngle by mutableStateOf(PhotoAngle.FRONT)
    var photos by mutableStateOf<Map<PhotoAngle, AnglePhoto>>(emptyMap())
    var notes by mutableStateOf<Map<PhotoAngle, String>>(emptyMap())
    var isComplete by mutableStateOf(false)

    val anglesCompleted: Int get() = photos.size

    val currentNotes: String
        get() = notes[currentAngle] ?: ""

    fun setCurrentNotes(value: String) {
        notes = notes + (currentAngle to value)
    }

    val hasCurrentPhoto: Boolean get() = photos.containsKey(currentAngle)

    fun setPhoto(data: ByteArray, angle: PhotoAngle) {
        val photo = AnglePhoto(
            angle = angle,
            imageData = data,
            notes = notes[angle] ?: ""
        )
        photos = photos + (angle to photo)
    }

    fun skipAngle() {
        moveToNextAngle()
    }

    fun retakeCurrentAngle() {
        photos = photos - currentAngle
    }

    fun moveToNextAngle() {
        val next = currentAngle.next
        if (next != null) {
            currentAngle = next
        } else {
            isComplete = true
        }
    }

    fun buildEntry(): ProgressPhotoEntry {
        val anglePhotos = PhotoAngle.entries.mapNotNull { angle ->
            photos[angle]?.copy(notes = notes[angle] ?: "")
        }
        return ProgressPhotoEntry(
            userId = "user1",
            date = Date(),
            photos = anglePhotos
        )
    }
}
