package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.BrowserTab
import com.fitnesslink.fit.model.EquipmentType
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.MuscleGroup
import com.fitnesslink.fit.persistence.DatabaseManager

class ExerciseBrowserViewModel : ViewModel() {
    var searchText by mutableStateOf("")
    var selectedMuscleGroup by mutableStateOf<MuscleGroup?>(null)
    var selectedEquipment by mutableStateOf<EquipmentType?>(null)
    var selectedTab by mutableStateOf(BrowserTab.All)
    var movements by mutableStateOf<List<MovementLibraryItem>>(emptyList())

    fun loadData() {
        movements = when (selectedTab) {
            BrowserTab.All -> {
                DatabaseManager.searchMovements(searchText, selectedMuscleGroup?.label, selectedEquipment?.label)
            }
            BrowserTab.Favorites -> DatabaseManager.favoriteMovements()
            BrowserTab.Recent -> DatabaseManager.recentMovements()
        }
    }

    fun search() {
        movements = DatabaseManager.searchMovements(
            searchText,
            selectedMuscleGroup?.label,
            selectedEquipment?.label
        )
    }

    fun toggleFavorite(movementId: String) {
        DatabaseManager.toggleMovementFavorite(movementId)
        loadData()
    }

    fun clearFilters() {
        searchText = ""
        selectedMuscleGroup = null
        selectedEquipment = null
        search()
    }
}
