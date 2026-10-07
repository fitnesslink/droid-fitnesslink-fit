package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.AchievementType
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.UnlockAchievementRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.completedSessionStartDates
import java.util.Locale

/** Database-backed assembly of [SessionAchievements] (the pure core lives in SessionAchievements.kt). */
object SessionAchievementsBuilder {
    /**
     * Assemble for a session from the local database (history excluding this session, the server
     * seed for movements with nothing local) and the program calendar.
     */
    fun build(
        rows: List<SessionHistoryRow>,
        sessionId: String,
        workoutId: String,
        programId: String?,
        asOfMs: Long = System.currentTimeMillis()
    ): SessionAchievements {
        val userId = DatabaseManager.user()?.id ?: "user1"
        val names = rows.mapNotNull { row -> row.task.movementId?.lowercase(Locale.US)?.let { it to row.task.name } }
            .toMap()
        val since = asOfMs - 400L * 86_400_000L
        return SessionAchievements.assemble(
            rows = rows,
            priorSets = { movementId ->
                val local = DatabaseManager.movementHistorySets(movementId, userId, sessionId, names[movementId])
                if (local.isNotEmpty()) local
                else MovementHistoryStore.serverSummary(movementId)?.let { SessionAchievements.priorSets(it) } ?: emptyList()
            },
            priorCompletedSessions = DatabaseManager.completedSessionCount(userId, sessionId),
            sessionDatesMs = DatabaseManager.completedSessionStartDates(userId, since),
            programEntries = programId?.let { DatabaseManager.calendarContentForProgram(it) } ?: emptyList(),
            workoutId = workoutId,
            asOfMs = asOfMs
        )
    }

    /** The production unlock client over `GoalApi`. */
    val liveClient: AchievementUnlocker.Client
        get() = AchievementUnlocker.Client(
            mine = { ApiClient.goalApi.getMyAchievements() },
            definitions = { ApiClient.goalApi.getAchievementDefinitions() },
            unlock = { id -> ApiClient.goalApi.unlockAchievement(UnlockAchievementRequest(id)) }
        )

    /** Unlock the earned types on the server; silent on every failure. */
    suspend fun unlock(types: List<AchievementType>): List<String> =
        AchievementUnlocker.unlock(types, liveClient, NetworkMonitor.isConnected.value)
}
