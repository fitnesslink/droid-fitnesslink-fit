package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.launch

class ProgramDetailViewModel : ViewModel() {
    var program by mutableStateOf(Program())

    fun loadData(programId: String) {
        program = DatabaseManager.program(programId) ?: Program()
        viewModelScope.launch { refreshFromServer(programId) }
    }

    private suspend fun refreshFromServer(programId: String) {
        if (!NetworkMonitor.isConnected.value) return
        try {
            val remote = ApiClient.programApi.get(programId)
            DatabaseManager.insertProgram(remote)
            program = DatabaseManager.program(programId) ?: program
        } catch (_: Exception) { /* use cached */ }
    }
}
