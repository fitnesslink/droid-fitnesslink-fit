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

/** `PUT /goals/{id}/pin` body (server `SetGoalPinRequest`). */
data class GoalPinRequest(val pinned: Boolean)

/**
 * `POST /goals/{goalId}/entries` body (server `CreateGoalEntryRequest`):
 * client-minted lowercase [id] so a replay is idempotent, [entryDate] as the
 * user-local `yyyy-MM-dd` day.
 */
data class CreateGoalEntryRequest(
    val id: String,
    val entryDate: String,
    val activityKey: String? = null,
    val value: Double? = null,
    val source: String? = "Manual",
    val note: String? = null
) {
    companion object {
        fun from(entry: com.fitnesslink.fit.model.GoalEntry) =
            CreateGoalEntryRequest(entry.id, entry.entryDate.take(10), entry.activityKey, entry.value, entry.source, entry.note)
    }
}
