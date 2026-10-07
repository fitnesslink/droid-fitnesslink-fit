package com.fitnesslink.fit.goals

import android.util.Log
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.model.Achievement
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.Habit
import com.fitnesslink.fit.model.HabitLog
import com.fitnesslink.fit.model.Milestone
import com.fitnesslink.fit.model.ProgramDates
import com.fitnesslink.fit.model.Streak
import com.fitnesslink.fit.model.advanceHabitStreak
import com.fitnesslink.fit.model.rollBackHabitStreak
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.AchievementDefinition
import com.fitnesslink.fit.network.dto.GoalProgressRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.cancelPendingEntries
import com.fitnesslink.fit.persistence.deleteGoalCascade
import com.fitnesslink.fit.persistence.deleteHabitLogById
import com.fitnesslink.fit.persistence.ensureGoalsSchema
import com.fitnesslink.fit.persistence.firstHabitLogAt
import com.fitnesslink.fit.persistence.goalById
import com.fitnesslink.fit.persistence.goalsCached
import com.fitnesslink.fit.persistence.habitById
import com.fitnesslink.fit.persistence.habitLogOn
import com.fitnesslink.fit.persistence.habitLogsFor
import com.fitnesslink.fit.persistence.habitsCached
import com.fitnesslink.fit.persistence.milestonesForGoal
import com.fitnesslink.fit.persistence.pendingEntityIds
import com.fitnesslink.fit.persistence.purgeOrphanedHabits
import com.fitnesslink.fit.persistence.replaceMilestones
import com.fitnesslink.fit.persistence.streakFull
import com.fitnesslink.fit.persistence.upsertHabitFull
import com.fitnesslink.fit.persistence.upsertMilestone
import com.fitnesslink.fit.persistence.upsertStreakFull
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Locale
import java.util.UUID

/**
 * Goals / habits / habit-log pipeline — port of the iOS write paths spread
 * across `GoalCreationViewModel.save`, `GoalDetailViewModel.deleteGoal`,
 * `HomeViewModel.completeHabit` and the `SyncManager.drainGoals` /
 * `drainHabits` / `drainHabitLogs` drains.
 *
 * Every mutation is local row first (so screens redraw at once) → durable
 * `sync_queue` row → inline attempt. 2xx or a final 4xx marks the queue row
 * synced; offline / 5xx leaves it for the registered drain. Reads come from
 * the SQLite cache so the goal list renders offline; [refresh] pushes queued
 * writes before pulling so a stale `GET /goals` can't clobber a just-saved
 * goal (the race iOS fixed in `GoalListViewModel.refreshFromServer`).
 *
 * Home keeps its own `HomeDao` readers; everything it reads (`goals`,
 * `habits`, `habit_logs`, `streaks`) is what this store writes.
 */
object GoalStore {
    private const val TAG = "GoalStore"

    private const val ACTION_CREATE = "create"
    private const val ACTION_UPDATE = "update"
    private const val ACTION_DELETE = "delete"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** Fires after any local goal / habit / log / streak change — screens re-read the cache. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    @Volatile private var registered = false

    private val gson get() = ApiClient.gson

    private val userId: String
        get() = runCatching { AuthManager.currentUser.value?.id?.toString() }.getOrNull() ?: ""

    /** Register the three drains once per process and make sure the Wave 4 columns exist. Safe to call repeatedly. */
    fun ensureRegistered() {
        if (registered) return
        registered = true
        runCatching { DatabaseManager.ensureGoalsSchema() }
        SyncManager.registerDrain(SyncDomain.GOALS) { drainGoals() }
        SyncManager.registerDrain(SyncDomain.HABITS) { drainHabits() }
        SyncManager.registerDrain(SyncDomain.HABIT_LOGS) { drainHabitLogs() }
    }

    // MARK: - Reads (cache)

    fun goals(): List<Goal> = runCatching { DatabaseManager.goalsCached() }.getOrDefault(emptyList())

    fun goal(id: String): Goal? = runCatching { DatabaseManager.goalById(id) }.getOrNull()

    /** Habits linked to [goalId], or every habit when null. */
    fun habits(goalId: String?): List<Habit> = runCatching { DatabaseManager.habitsCached(goalId) }.getOrDefault(emptyList())

    fun habit(id: String): Habit? = runCatching { DatabaseManager.habitById(id) }.getOrNull()

    fun streak(habitId: String): Streak? = runCatching { DatabaseManager.streakFull(habitId) }.getOrNull()

    fun milestones(goalId: String): List<Milestone> = runCatching { DatabaseManager.milestonesForGoal(goalId) }.getOrDefault(emptyList())

    fun habitLogs(habitId: String, sinceMillis: Long? = null, limit: Int = 50): List<HabitLog> =
        runCatching { DatabaseManager.habitLogsFor(habitId, sinceMillis, limit) }.getOrDefault(emptyList())

    /** Local-day starts with at least one log, back to [sinceMillis]. */
    fun completedDays(habitId: String, sinceMillis: Long): Set<Long> =
        runCatching { DatabaseManager.habitLogDates(habitId, sinceMillis) }.getOrDefault(emptySet())

    fun isCompletedToday(habitId: String): Boolean =
        runCatching { DatabaseManager.habitLogOn(habitId) }.getOrNull() != null

    /** The habit's first tracked day: its `createdAt`, or the earliest log when that is older. */
    fun trackedFrom(habit: Habit): Long {
        val firstLog = runCatching { DatabaseManager.firstHabitLogAt(habit.id) }.getOrNull()
        return if (firstLog != null && firstLog < habit.createdAt) firstLog else habit.createdAt
    }

    // MARK: - Refresh

    /**
     * Drain queued goal / habit / log writes, then pull `GET /goals` and
     * `GET /habits/me` into the cache. Rows with a pending local mutation are
     * left alone; cached goals the server no longer has (and that aren't
     * pending) are removed with their habits.
     */
    suspend fun refresh() {
        ensureRegistered()
        if (!NetworkMonitor.isConnected.value) return
        runCatching { SyncManager.drain(SyncDomain.GOALS) }
        runCatching { SyncManager.drain(SyncDomain.HABITS) }
        runCatching { SyncManager.drain(SyncDomain.HABIT_LOGS) }

        var goalsAuthoritative = false
        val remoteGoals = runCatching { ApiClient.goalApi.list() }
            .onFailure { Log.i(TAG, "goal list failed — using cache (${it.javaClass.simpleName})") }
            .getOrNull()
        if (remoteGoals != null) {
            withContext(Dispatchers.IO) {
                val pending = DatabaseManager.pendingEntityIds(SyncDomain.GOALS.wireName)
                val remoteIds = remoteGoals.mapTo(HashSet()) { it.id.lowercase(Locale.US) }
                remoteGoals.forEach { goal ->
                    if (goal.id.lowercase(Locale.US) !in pending) runCatching { DatabaseManager.insertGoal(goal.normalized()) }
                }
                DatabaseManager.goalsCached()
                    .filter { it.id.lowercase(Locale.US) !in remoteIds && it.id.lowercase(Locale.US) !in pending }
                    .forEach { stale -> runCatching { DatabaseManager.deleteGoalCascade(stale.id) } }
            }
            goalsAuthoritative = true
        }

        runCatching { ApiClient.goalApi.getMyHabits() }.onSuccess { remote ->
            withContext(Dispatchers.IO) {
                val pending = DatabaseManager.pendingEntityIds(SyncDomain.HABITS.wireName)
                remote.forEach { habit ->
                    if (habit.id.lowercase(Locale.US) !in pending) runCatching { DatabaseManager.upsertHabitFull(habit.normalized()) }
                }
                // Guarded on a successful goal pull so a failed list() can't make every habit look orphaned.
                if (goalsAuthoritative) runCatching { DatabaseManager.purgeOrphanedHabits() }
            }
        }
        notifyChanged()
    }

    /** iOS `GoalDetailViewModel.refreshFromServer`: goal, its habits, milestones and each habit's streak. */
    suspend fun refreshGoalDetail(goalId: String) {
        ensureRegistered()
        if (!NetworkMonitor.isConnected.value) return
        val pendingGoals = DatabaseManager.pendingEntityIds(SyncDomain.GOALS.wireName)
        if (goalId.lowercase(Locale.US) !in pendingGoals) {
            runCatching { ApiClient.goalApi.get(goalId) }.onSuccess { goal ->
                withContext(Dispatchers.IO) { runCatching { DatabaseManager.insertGoal(goal.normalized()) } }
            }
        }
        val habits = runCatching { ApiClient.goalApi.getHabitsForGoal(goalId) }.getOrNull()
        if (habits != null) {
            withContext(Dispatchers.IO) {
                val pending = DatabaseManager.pendingEntityIds(SyncDomain.HABITS.wireName)
                habits.forEach { if (it.id.lowercase(Locale.US) !in pending) runCatching { DatabaseManager.upsertHabitFull(it.normalized()) } }
            }
        }
        runCatching { ApiClient.goalApi.getMilestones(goalId) }.onSuccess { list ->
            withContext(Dispatchers.IO) { runCatching { DatabaseManager.replaceMilestones(goalId, list.map { it.copy(id = it.id.lowercase(Locale.US)) }) } }
        }
        for (habit in habits ?: habits(goalId)) refreshStreak(habit.id)
        notifyChanged()
    }

    /** iOS `HabitDetailViewModel.refreshFromServer`: the habit and its streak. */
    suspend fun refreshHabitDetail(habitId: String) {
        ensureRegistered()
        if (!NetworkMonitor.isConnected.value) return
        runCatching { ApiClient.goalApi.getMyHabits() }.onSuccess { remote ->
            remote.firstOrNull { it.id.equals(habitId, ignoreCase = true) }?.let { habit ->
                val pending = DatabaseManager.pendingEntityIds(SyncDomain.HABITS.wireName)
                if (habit.id.lowercase(Locale.US) !in pending) {
                    withContext(Dispatchers.IO) { runCatching { DatabaseManager.upsertHabitFull(habit.normalized()) } }
                }
            }
        }
        refreshStreak(habitId)
        notifyChanged()
    }

    private suspend fun refreshStreak(habitId: String) {
        // A completion logged offline hasn't reached the server yet; its local streak is ahead of the server's.
        val pendingLogs = runCatching { DatabaseManager.pendingSyncEntries(SyncDomain.HABIT_LOGS.wireName) }.getOrDefault(emptyList())
        val hasPendingLog = pendingLogs.any { entry ->
            SyncManager.decodePayload<HabitLog>(entry.payload)?.habitId?.equals(habitId, ignoreCase = true) == true
        }
        if (hasPendingLog) return
        runCatching { ApiClient.goalApi.getStreak(habitId) }.onSuccess { streak ->
            val keyed = streak.copy(
                id = streak.id.ifBlank { UUID.randomUUID().toString() }.lowercase(Locale.US),
                habitId = streak.habitId.ifBlank { habitId }.lowercase(Locale.US),
                userId = streak.userId.ifBlank { userId }
            )
            withContext(Dispatchers.IO) { runCatching { DatabaseManager.upsertStreakFull(keyed) } }
        }
    }

    // MARK: - Create

    /**
     * iOS `GoalCreationViewModel.save`: lowercase ids so the local row matches
     * the Guid the server echoes back, stamp each habit with the goal id,
     * write + queue everything synchronously, then send without waiting.
     *
     * `CreateGoalRequest` has no `currentValue`, so a non-zero starting value
     * is sent as a follow-up `PUT /goals/{id}/progress` (same queue row).
     */
    suspend fun create(goal: Goal, habits: List<Habit>) {
        ensureRegistered()
        val now = System.currentTimeMillis()
        val uid = userId
        val stored = goal.copy(
            id = goal.id.lowercase(Locale.US),
            userId = goal.userId.ifBlank { uid },
            startDate = goal.startDate,
            createdAt = now,
            updatedAt = now,
            trajectoryStatus = GoalMath.deriveTrajectory(goal, now)
        )
        val storedHabits = habits.mapIndexed { index, habit ->
            habit.copy(
                id = UUID.randomUUID().toString().lowercase(Locale.US),
                goalId = stored.id,
                userId = habit.userId.ifBlank { uid },
                sortOrder = if (habit.sortOrder == 0) index else habit.sortOrder,
                createdAt = now,
                updatedAt = now
            )
        }
        withContext(Dispatchers.IO) {
            DatabaseManager.insertGoal(stored)
            storedHabits.forEach { DatabaseManager.upsertHabitFull(it) }
        }
        notifyChanged()
        commit(SyncDomain.GOALS, ACTION_CREATE, stored.id, gson.toJson(stored)) { sendGoalCreate(stored) }
        storedHabits.forEach { habit ->
            commit(SyncDomain.HABITS, ACTION_CREATE, habit.id, gson.toJson(habit)) { ApiClient.goalApi.createHabit(habit) }
        }
    }

    private suspend fun sendGoalCreate(goal: Goal) {
        ApiClient.goalApi.create(goal)
        if (goal.currentValue > 0) {
            runCatching { ApiClient.goalApi.updateProgress(goal.id, GoalProgressRequest(goal.currentValue)) }
                .onFailure { Log.w(TAG, "initial progress for ${goal.id} failed: ${it.message}") }
        }
    }

    // MARK: - Update

    /** Archive / pause / edit: local row first, then queued `PUT /goals/{id}` (iOS `GoalListViewModel.archiveGoal`). */
    suspend fun update(goal: Goal) {
        ensureRegistered()
        val stored = goal.copy(id = goal.id.lowercase(Locale.US), updatedAt = System.currentTimeMillis())
        withContext(Dispatchers.IO) { DatabaseManager.insertGoal(stored) }
        notifyChanged()
        commit(SyncDomain.GOALS, ACTION_UPDATE, stored.id, gson.toJson(stored)) { ApiClient.goalApi.update(stored.id, stored) }
    }

    /** Local-first `PUT /goals/{id}/progress` (also keeps the cached ring current). */
    suspend fun updateProgress(goalId: String, currentValue: Double) {
        val goal = goal(goalId) ?: return
        update(goal.copy(currentValue = currentValue))
    }

    // MARK: - Delete

    /**
     * iOS `GoalDetailViewModel.deleteGoal`: cascade to the goal's habits
     * (streaks + logs) so Home stops listing them, queue one DELETE per
     * habit and one for the goal. Habits are re-read from the DB, not the
     * screen state, so a fast tap can't race the detail fetch.
     */
    suspend fun delete(goalId: String) {
        ensureRegistered()
        val id = goalId.lowercase(Locale.US)
        val goal = goal(id)
        val removedHabits = withContext(Dispatchers.IO) { DatabaseManager.deleteGoalCascade(id) }
        notifyChanged()
        removedHabits.forEach { habit ->
            DatabaseManager.cancelPendingEntries(SyncDomain.HABITS.wireName, habit.id)
            commit(SyncDomain.HABITS, ACTION_DELETE, habit.id, gson.toJson(habit)) { ApiClient.goalApi.deleteHabit(habit.id) }
        }
        DatabaseManager.cancelPendingEntries(SyncDomain.GOALS.wireName, id)
        commit(SyncDomain.GOALS, ACTION_DELETE, id, goal?.let { gson.toJson(it) } ?: "") { ApiClient.goalApi.delete(id) }
    }

    // MARK: - Habit logs

    /**
     * iOS `HomeViewModel.completeHabit` / `uncompleteHabit`. [completed]
     * true: today's log + advanced streak locally, queued
     * `POST /habits/{id}/log`. false: drop today's log, roll the streak back
     * and cancel the queued POST if it hasn't shipped (the server has no
     * DELETE for habit logs). Returns the streak now on record.
     */
    suspend fun logHabit(habitId: String, completed: Boolean): Streak {
        ensureRegistered()
        val id = habitId.lowercase(Locale.US)
        val uid = userId
        val prior = streak(id) ?: Streak(habitId = id, userId = uid)
        if (completed) {
            if (isCompletedToday(id)) return prior
            val log = HabitLog(id = UUID.randomUUID().toString().lowercase(Locale.US), habitId = id, userId = uid, completedAt = System.currentTimeMillis())
            val advanced = advanceHabitStreak(prior, id, uid, log.completedAt)
            withContext(Dispatchers.IO) {
                DatabaseManager.insertHabitLog(log)
                DatabaseManager.upsertStreakFull(advanced)
            }
            notifyChanged()
            commit(SyncDomain.HABIT_LOGS, ACTION_CREATE, log.id, gson.toJson(log)) { ApiClient.goalApi.logHabit(id, log) }
            return advanced
        }
        val todayLog = runCatching { DatabaseManager.habitLogOn(id) }.getOrNull() ?: return prior
        val restored = rollBackHabitStreak(prior)
        withContext(Dispatchers.IO) {
            DatabaseManager.deleteHabitLogById(todayLog.id)
            DatabaseManager.upsertStreakFull(restored)
            DatabaseManager.cancelPendingEntries(SyncDomain.HABIT_LOGS.wireName, todayLog.id)
        }
        notifyChanged()
        return restored
    }

    // MARK: - Milestones (Android extra — iOS milestones are read-only)

    /** Optimistic create: cached row now, `POST /milestones` when online (no queue domain exists for milestones). */
    suspend fun addMilestone(goalId: String, title: String, targetValue: Double): Milestone {
        val draft = Milestone(id = UUID.randomUUID().toString().lowercase(Locale.US), goalId = goalId.lowercase(Locale.US), title = title.trim(), targetValue = targetValue)
        withContext(Dispatchers.IO) { runCatching { DatabaseManager.upsertMilestone(draft) } }
        notifyChanged()
        if (NetworkMonitor.isConnected.value) {
            scope.launch { runCatching { ApiClient.goalApi.createMilestone(draft) }.onFailure { Log.w(TAG, "milestone create failed: ${it.message}") } }
        }
        return draft
    }

    suspend fun achieveMilestone(goalId: String, milestoneId: String) {
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            milestones(goalId).firstOrNull { it.id.equals(milestoneId, true) }?.let { runCatching { DatabaseManager.upsertMilestone(it.copy(achievedAt = now)) } }
        }
        notifyChanged()
        if (NetworkMonitor.isConnected.value) {
            scope.launch { runCatching { ApiClient.goalApi.achieveMilestone(milestoneId) }.onFailure { Log.w(TAG, "milestone achieve failed: ${it.message}") } }
        }
    }

    // MARK: - Achievements

    fun cachedAchievements(): List<Achievement> = runCatching { DatabaseManager.achievements() }.getOrDefault(emptyList())

    /** `GET /achievements/me`, cached into `achievements`; falls back to the cache offline / on failure. */
    suspend fun refreshAchievements(): List<Achievement> {
        if (!NetworkMonitor.isConnected.value) return cachedAchievements()
        return runCatching { ApiClient.goalApi.getMyAchievements() }
            .onSuccess { list -> withContext(Dispatchers.IO) { list.forEach { runCatching { DatabaseManager.insertAchievement(it) } } } }
            .getOrNull() ?: cachedAchievements()
    }

    /** `GET /achievements` definitions (what "Locked" renders); empty offline. */
    suspend fun achievementDefinitions(): List<AchievementDefinition> {
        if (!NetworkMonitor.isConnected.value) return emptyList()
        return runCatching { ApiClient.goalApi.getAchievementDefinitions() }.getOrDefault(emptyList())
    }

    // MARK: - Queue

    /**
     * Enqueue, then try inline. Success or a final 4xx marks the row synced;
     * offline / transient leaves it queued for the drain. Fire-and-forget so
     * the caller's UI returns at once (iOS `Task { … markSyncEntrySynced }`).
     */
    private fun commit(domain: SyncDomain, action: String, entityId: String, payload: String, call: suspend () -> Unit) {
        val queueId = runCatching { DatabaseManager.enqueueSyncEntry(domain.wireName, entityId, action, payload) }.getOrNull() ?: return
        if (!NetworkMonitor.isConnected.value) return
        scope.launch {
            try {
                call()
                DatabaseManager.markSyncEntrySynced(queueId)
            } catch (e: Exception) {
                if (isFinalFailure(e)) {
                    Log.w(TAG, "$action ${domain.wireName} $entityId: final ${e.javaClass.simpleName} — dropping")
                    DatabaseManager.markSyncEntrySynced(queueId)
                } else {
                    Log.i(TAG, "$action ${domain.wireName} $entityId: transient ${e.javaClass.simpleName} — left queued")
                }
            }
        }
    }

    /** iOS semantics: 404 / 403 / other 4xx are final; network, 5xx and 401 are transient. */
    internal fun isFinalFailure(e: Throwable): Boolean = when (e) {
        is HttpException -> e.code() in 400..499 && e.code() != 401
        is ApiError.Forbidden, is ApiError.NotFound -> true
        is ApiError.ServerError -> e.statusCode in 400..499 && e.statusCode != 401
        else -> false
    }

    /** iOS `drainGoals`: every row carries the full `Goal`; delete uses its id. */
    private suspend fun drainGoals() {
        SyncManager.drainQueue(SyncDomain.GOALS) { entry ->
            val goal = SyncManager.decodePayload<Goal>(entry.payload)
            when (entry.action) {
                ACTION_CREATE -> sendGoalCreate(goal ?: throw ApiError.NotFound)
                ACTION_UPDATE -> { val g = goal ?: throw ApiError.NotFound; ApiClient.goalApi.update(g.id, g) }
                ACTION_DELETE -> ApiClient.goalApi.delete(entry.entityId)
                else -> Log.w(TAG, "unknown goals queue action '${entry.action}' — dropping")
            }
        }
    }

    /** iOS `drainHabits`: walked in created order so a habit never lands before its goal. */
    private suspend fun drainHabits() {
        SyncManager.drainQueue(SyncDomain.HABITS) { entry ->
            val habit = SyncManager.decodePayload<Habit>(entry.payload)
            when (entry.action) {
                ACTION_CREATE -> ApiClient.goalApi.createHabit(habit ?: throw ApiError.NotFound)
                ACTION_UPDATE -> { val h = habit ?: throw ApiError.NotFound; ApiClient.goalApi.updateHabit(h.id, h) }
                ACTION_DELETE -> ApiClient.goalApi.deleteHabit(entry.entityId)
                else -> Log.w(TAG, "unknown habits queue action '${entry.action}' — dropping")
            }
        }
    }

    /** iOS `drainHabitLogs`: the endpoint is per habit, so the habit id comes from the decoded payload. */
    private suspend fun drainHabitLogs() {
        SyncManager.drainQueue(SyncDomain.HABIT_LOGS) { entry ->
            val log = SyncManager.decodePayload<HabitLog>(entry.payload) ?: throw ApiError.NotFound
            ApiClient.goalApi.logHabit(log.habitId, log)
        }
    }

    private fun Goal.normalized(): Goal = copy(id = id.lowercase(Locale.US))
    private fun Habit.normalized(): Habit = copy(id = id.lowercase(Locale.US), goalId = goalId?.lowercase(Locale.US))

    private fun notifyChanged() { _changes.tryEmit(Unit) }

    /** Local day start helper for screens (keeps `ProgramDates` out of the UI). */
    fun startOfDay(millis: Long): Long = ProgramDates.startOfDay(millis)
}
