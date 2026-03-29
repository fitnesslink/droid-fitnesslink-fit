package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.CatalogItem
import com.fitnesslink.fit.persistence.DatabaseManager

class CatalogViewModel : ViewModel() {
    var programs by mutableStateOf<List<CatalogItem>>(emptyList())
    var workouts by mutableStateOf<List<CatalogItem>>(emptyList())
    var myWorkouts by mutableStateOf<List<CatalogItem>>(emptyList())

    fun loadData() {
        programs = DatabaseManager.catalogPrograms()
        workouts = DatabaseManager.catalogWorkouts()
        myWorkouts = MockDataProvider.myWorkouts
    }
}
