package com.fitnesslink.fit.network.dto

// Wire shapes for /achievements. Mirrors iOS `GoalModels.swift`
// (`AchievementDefinition`) and `AchievementService.unlock`.

/** `GET /achievements` row (server `AchievementDto`): `id` is what `POST /achievements` unlocks. */
data class AchievementDefinition(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val iconUrl: String? = null,
    val category: String = "",
    val achievementType: String = ""
)

/** `POST /achievements` body; 201 returns the new user-achievement id as a bare string. */
data class UnlockAchievementRequest(
    val achievementId: String
)
