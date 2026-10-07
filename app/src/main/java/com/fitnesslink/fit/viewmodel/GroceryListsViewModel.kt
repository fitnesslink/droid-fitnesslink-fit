package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.model.GroceryList
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.nutrition.GroceryStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Drives the grocery lists overview (FTNSSLNK-208, port of iOS
 * `GroceryListsViewModel`): active lists up top, archived lists as
 * shopping history below. Online-first through [GroceryStore]; a 409 is
 * retried once with the fresh token and otherwise surfaced as a banner.
 */
class GroceryListsViewModel : ViewModel() {
    var activeLists by mutableStateOf<List<GroceryList>>(emptyList())
        private set
    var archivedLists by mutableStateOf<List<GroceryList>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)

    private var hasLoadedOnce = false
    private var observing = false

    fun observe() {
        if (observing) return
        observing = true
        viewModelScope.launch { GroceryStore.changes.collectLatest { adopt(GroceryStore.cachedLists()) } }
    }

    suspend fun load() {
        observe()
        adopt(GroceryStore.cachedLists())
        if (!hasLoadedOnce) isLoading = true
        try {
            adopt(GroceryStore.refreshLists())
            hasLoadedOnce = true
            errorMessage = null
        } catch (e: Exception) {
            if (!hasLoadedOnce) errorMessage = describe(e)
        } finally {
            isLoading = false
        }
    }

    private fun adopt(all: List<GroceryList>) {
        activeLists = all.filter { !it.isArchived }
        archivedLists = all.filter { it.isArchived }
    }

    suspend fun createList(name: String): GroceryList? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return try {
            GroceryStore.createList(trimmed).also { load() }
        } catch (e: Exception) {
            errorMessage = describe(e); null
        }
    }

    suspend fun rename(list: GroceryList, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == list.name) return
        run { GroceryStore.renameList(list, trimmed) }
    }

    suspend fun setArchived(list: GroceryList, archived: Boolean) = run { GroceryStore.setArchived(list, archived) }

    suspend fun delete(list: GroceryList) {
        if (list.isDefault) return
        run { GroceryStore.deleteList(list) }
    }

    suspend fun duplicate(list: GroceryList): GroceryList? = try {
        GroceryStore.duplicateList(list).also { load() }
    } catch (e: Exception) {
        errorMessage = describe(e); null
    }

    private suspend fun run(op: suspend () -> Unit) {
        try {
            op()
            load()
        } catch (e: Exception) {
            errorMessage = describe(e)
            runCatching { load() }
        }
    }

    companion object {
        fun describe(e: Throwable): String = when (e) {
            is GroceryStore.ConflictException -> e.message ?: "This changed on another device. Try again."
            is ApiError.Offline -> "No internet connection. Check and try again."
            is ApiError.NetworkError -> "Network error. Check your connection and try again."
            is ApiError.Unauthorized -> "Session expired. Please log in again."
            is ApiError.ServerError -> if (e.isBadRequest) (e.envelope?.displayMessage ?: "The server rejected that change.") else "Server error (${e.statusCode}). Try again later."
            is ApiError -> e.message ?: "Something went wrong."
            else -> "Something went wrong: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }
}
