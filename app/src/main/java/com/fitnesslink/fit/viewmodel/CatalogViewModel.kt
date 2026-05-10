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

    /**
     * Workouts and programs the current user authored, mixed into a
     * single carousel matching iOS's "My Content" layout. Programs come
     * first since they're typically the bigger commitment.
     */
    var myContent by mutableStateOf<List<CatalogItem>>(emptyList())

    fun loadData() {
        programs = DatabaseManager.catalogPrograms()
        workouts = DatabaseManager.catalogWorkouts()
        myContent = MockDataProvider.myPrograms + MockDataProvider.myWorkouts
    }
}
