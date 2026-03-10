package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.model.CatalogItem

class CatalogViewModel : ViewModel() {
    var programs by mutableStateOf<List<CatalogItem>>(emptyList())
    var workouts by mutableStateOf<List<CatalogItem>>(emptyList())
    var myWorkouts by mutableStateOf<List<CatalogItem>>(emptyList())

    fun loadData() {
        programs = MockDataProvider.catalogPrograms
        workouts = MockDataProvider.catalogWorkouts
        myWorkouts = MockDataProvider.myWorkouts
    }
}
