package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.Personalization
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.UserPersonalizationRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.fitnesslink.fit.model.PersonalizationItem
import com.fitnesslink.fit.model.ProgressPage
import com.fitnesslink.fit.model.ProgressTracker

class PersonalizationViewModel : ViewModel() {
    var personalizations by mutableStateOf<List<Personalization>>(emptyList())
    var currentIndex by mutableIntStateOf(0)
    var isComplete by mutableStateOf(false)

    val currentPage: Personalization?
        get() = if (currentIndex < personalizations.size) personalizations[currentIndex] else null

    val progress: ProgressTracker
        get() {
            val pages = personalizations.indices.map { i ->
                ProgressPage(id = i, selected = i <= currentIndex)
            }
            return ProgressTracker(totalPages = personalizations.size, pages = pages)
        }

    val isFirstPage: Boolean get() = currentIndex == 0
    val isLastPage: Boolean get() = currentIndex == personalizations.size - 1

    val hasSelection: Boolean
        get() = currentPage?.options?.any { it.selected } ?: false

    fun loadData() {
        personalizations = DatabaseManager.personalizations()
    }

    fun toggleSelection(item: PersonalizationItem) {
        if (currentIndex >= personalizations.size) return
        val page = personalizations[currentIndex]

        val updatedOptions = if (page.singleSelection) {
            page.options.map { it.copy(selected = it.id == item.id) }
        } else {
            page.options.map {
                if (it.id == item.id) it.copy(selected = !it.selected) else it
            }
        }

        personalizations = personalizations.toMutableList().also {
            it[currentIndex] = page.copy(options = updatedOptions)
        }
        viewModelScope.launch { DatabaseManager.savePersonalizations(personalizations) }
    }

    fun next() {
        if (isLastPage) {
            viewModelScope.launch {
                DatabaseManager.savePersonalizations(personalizations)
                DatabaseManager.user()?.id?.let { DatabaseManager.setUserPersonalized(it) }
            }
            viewModelScope.launch {
                try {
                    val selections = personalizations.flatMap { page ->
                        page.options.filter { it.selected }.map { option ->
                            UserPersonalizationRequest(
                                personalizationId = page.id,
                                personalizationOptionId = option.id
                            )
                        }
                    }
                    ApiClient.personalizationApi.saveSelections(selections)
                } catch (_: Exception) {}
            }
            isComplete = true
        } else {
            currentIndex++
        }
    }

    fun previous() {
        if (currentIndex > 0) {
            currentIndex--
        }
    }
}
