package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.ProgramList
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch

class ProgramsViewModel : ViewModel() {
    var programs by mutableStateOf<List<ProgramList>>(emptyList())

    fun loadData() {
        programs = DatabaseManager.allPrograms()
        viewModelScope.launch { refreshFromServer() }
    }

    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remote = ApiClient.programApi.listView()
            remote.forEach { DatabaseManager.insertProgram(it) }
            programs = DatabaseManager.allPrograms()
        } catch (_: Exception) { /* use cached */ }
    }
}
