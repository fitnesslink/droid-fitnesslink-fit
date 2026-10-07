package com.fitnesslink.fit.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.media.MediaUploadCoordinator
import com.fitnesslink.fit.media.UploadRecord
import com.fitnesslink.fit.media.UploadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * FB-89 port of iOS `MyVideosView` state. Records persist in
 * [MediaUploadCoordinator] (survive relaunch, WorkManager-backed); this VM
 * just observes each one's [UploadState] and exposes it to the rows.
 */
class MyVideosViewModel : ViewModel() {
    var records by mutableStateOf<List<UploadRecord>>(emptyList())
        private set
    val states = mutableStateMapOf<String, UploadState>()
    var isImporting by mutableStateOf(false)
        private set
    var pickError by mutableStateOf<String?>(null)
    var playingUrl by mutableStateOf<String?>(null)

    private val watchers = mutableMapOf<String, Job>()

    fun loadData() {
        records = MediaUploadCoordinator.records()
        records.forEach { watch(it.uploadId) }
    }

    private fun watch(uploadId: String) {
        if (watchers.containsKey(uploadId)) return
        watchers[uploadId] = viewModelScope.launch {
            MediaUploadCoordinator.observe(uploadId).collect { states[uploadId] = it }
        }
    }

    /** Picker result: stage + enqueue the upload (container `videoassets`). */
    fun upload(uri: Uri) {
        viewModelScope.launch {
            isImporting = true
            pickError = null
            try {
                MediaUploadCoordinator.enqueue(fileUri = uri, container = "videoassets")
                loadData()
            } catch (e: Exception) {
                pickError = e.message ?: "Selection couldn't be loaded."
            } finally {
                isImporting = false
            }
        }
    }

    fun remove(uploadId: String) {
        watchers.remove(uploadId)?.cancel()
        states.remove(uploadId)
        MediaUploadCoordinator.remove(uploadId)
        records = MediaUploadCoordinator.records()
    }

    fun play(state: UploadState) {
        val ready = state as? UploadState.Ready ?: return
        playingUrl = ready.videoUrl
    }
}
