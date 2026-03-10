package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.Program

class ProgramDetailViewModel : ViewModel() {
    var program by mutableStateOf(Program())

    fun loadData(programId: String) {
        program = MockDataProvider.programDetail(programId)
    }
}
