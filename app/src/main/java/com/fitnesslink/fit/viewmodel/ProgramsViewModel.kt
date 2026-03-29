package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.ProgramList
import com.fitnesslink.fit.persistence.DatabaseManager

class ProgramsViewModel : ViewModel() {
    var programs by mutableStateOf<List<ProgramList>>(emptyList())

    fun loadData() {
        programs = DatabaseManager.allPrograms()
    }
}
