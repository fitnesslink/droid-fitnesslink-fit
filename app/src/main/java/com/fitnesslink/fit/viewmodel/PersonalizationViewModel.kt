package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fitnesslink.fit.model.Personalization
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
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
        viewModelScope.launch { refreshFromServer() }
    }

    /**
     * Pull authoritative question list and the user's saved selections from
     * the server, merge them, persist locally, and update state. Falls back
     * to whatever was already in `personalizations` on any failure so the
     * Profile-edit entry still pre-fills from cache when offline.
     */
    private suspend fun refreshFromServer() {
        if (!NetworkMonitor.isConnected.value) return

        val remoteQuestions = runCatching { ApiClient.personalizationApi.getAll() }.getOrNull()
            ?: return
        if (remoteQuestions.isEmpty()) return

        // Selected option ids the server has on file for this user. Used as
        // source of truth — fresh sign-ins on a new device will pick these
        // up and pre-fill the form.
        val serverSelectedIds = runCatching {
            ApiClient.personalizationApi.getMySelections()
                .map { it.personalizationOptionId.toString() }
                .toSet()
        }.getOrElse { emptySet() }

        // Locally selected ids in case the server doesn't yet know about
        // changes the user made offline. Server wins on overlap.
        val localSelectedIds = personalizations.flatMap { page ->
            page.options.filter { it.selected }.map { it.id }
        }.toSet()

        val effectiveSelected = serverSelectedIds.ifEmpty { localSelectedIds }

        val merged = remoteQuestions.map { page ->
            page.copy(options = page.options.map {
                it.copy(selected = it.id in effectiveSelected)
            })
        }
        personalizations = merged
        DatabaseManager.savePersonalizations(merged)
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
