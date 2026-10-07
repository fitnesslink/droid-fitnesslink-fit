package com.fitnesslink.fit.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.goals.GoalStore
import com.fitnesslink.fit.model.Achievement
import com.fitnesslink.fit.model.AchievementType
import com.fitnesslink.fit.network.dto.AchievementDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * iOS `AchievementsViewModel`: earned from `GET /achievements/me` (cache
 * first), locked from the `GET /achievements` definitions the user hasn't
 * earned — falling back to the client enum when the definitions can't load.
 */
class AchievementsViewModel : ViewModel() {
    var achievements by mutableStateOf<List<Achievement>>(emptyList())
        private set
    var definitions by mutableStateOf<List<AchievementDefinition>>(emptyList())
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    val earnedAchievements: List<Achievement> get() = achievements

    private val earnedTypeNames: Set<String>
        get() = achievements.mapNotNull { it.achievementType?.name?.lowercase() }.toSet()

    /** Server definitions not yet earned (matched on `achievementType`, else on name). */
    val lockedDefinitions: List<AchievementDefinition>
        get() {
            val earnedTypes = earnedTypeNames
            val earnedTitles = achievements.map { it.title.lowercase() }.toSet()
            return definitions.filter { def ->
                def.achievementType.lowercase() !in earnedTypes && def.name.lowercase() !in earnedTitles
            }
        }

    /** Client-enum fallback for Locked when the definitions haven't loaded. */
    val lockedTypes: List<AchievementType>
        get() {
            val earnedTypes = achievements.mapNotNull { it.achievementType }.toSet()
            return AchievementType.entries.filter { it !in earnedTypes }
        }

    fun loadData() {
        achievements = GoalStore.cachedAchievements()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing = true
            try {
                val (mine, defs) = withContext(Dispatchers.IO) {
                    GoalStore.refreshAchievements() to GoalStore.achievementDefinitions()
                }
                achievements = mine
                if (defs.isNotEmpty()) definitions = defs
            } finally {
                isRefreshing = false
            }
        }
    }
}
