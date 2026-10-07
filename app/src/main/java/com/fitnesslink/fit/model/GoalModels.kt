package com.fitnesslink.fit.model

import java.util.UUID

// MARK: - Enums

enum class GoalType(val displayName: String) {
    BodyComposition("Lose Weight"),
    Performance("Get Stronger"),
    Consistency("Build a Routine"),
    Nutrition("Eat Better"),
    RecoveryWellness("Sleep & Recover"),
    Custom("Custom Goal")
}

enum class GoalStatus { Active, Paused, Completed, Archived }

enum class TrajectoryStatus(val displayName: String) {
    Ahead("Ahead"), OnTrack("On Track"), Behind("Behind"), AtRisk("At Risk")
}

enum class HabitType { ActionBased, TimeBased, PassiveSensor, Nutrition, Avoidance, Reflection }

enum class HabitTier { Seedling, Sprout, Growing, Rooted }

enum class FrequencyType { Daily, WeekDays, Custom }

enum class CompletionType { Manual, Auto, Partial }

enum class CoachingTone(val displayName: String) {
    EncouragingCoach("Encouraging Coach"),
    DrillSergeant("Drill Sergeant"),
    ZenMaster("Zen Master"),
    DataNerd("Data Nerd")
}

/**
 * Server `AchievementType` names (core CoreEnums.cs) plus the habit-side
 * types. Gson tolerates unknown values: an unfamiliar server string decodes
 * to null (see [Adapter]) instead of failing the whole achievements fetch.
 */
@com.google.gson.annotations.JsonAdapter(AchievementType.Adapter::class)
enum class AchievementType(val title: String, val description: String) {
    FirstStep("First Step", "Complete your first habit"),
    WeekOne("Week One!", "7-day streak achieved"),
    HabitStacker("Habit Stacker", "3+ active habits simultaneously"),
    TierUp("Tier Up", "Graduate from Seedling to Sprout"),
    CenturyClub("Century Club", "100-day streak"),
    FullCircle("Full Circle", "Complete a goal start to finish"),
    DataDriven("Data Driven", "Log food, workout, and sleep in one day"),
    ConsistencyRoyalty("Consistency Royalty", "90%+ completion rate for 30 days"),
    // Server-side values the workout summary unlocks (FTNSSLNK-413).
    FirstWorkout("First Workout", "Complete your first workout"),
    ConsistencyKing("Consistency King", "Train consistently week after week"),
    StreakMaster("Streak Master", "Keep a multi-day training streak"),
    PersonalRecord("Personal Record", "Set a new personal record"),
    GoalCrusher("Goal Crusher", "Crush a goal you set"),
    EarlyBird("Early Bird", "Train before the day starts"),
    Centurion("Centurion", "100 workouts completed");

    companion object {
        fun fromWire(raw: String?): AchievementType? =
            raw?.let { r -> entries.firstOrNull { it.name.equals(r, ignoreCase = true) } }
    }

    class Adapter : com.google.gson.TypeAdapter<AchievementType>() {
        override fun write(out: com.google.gson.stream.JsonWriter, value: AchievementType?) {
            if (value == null) out.nullValue() else out.value(value.name)
        }

        override fun read(reader: com.google.gson.stream.JsonReader): AchievementType? {
            if (reader.peek() == com.google.gson.stream.JsonToken.NULL) { reader.nextNull(); return null }
            return fromWire(reader.nextString())
        }
    }
}

// MARK: - Data classes

data class Goal(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val goalType: GoalType = GoalType.Performance,
    val title: String = "",
    val description: String = "",
    val targetValue: Double? = null,
    val targetUnit: String? = null,
    val currentValue: Double = 0.0,
    val startDate: Long = System.currentTimeMillis(),
    val targetDate: Long? = null,
    val completedDate: Long? = null,
    var status: GoalStatus = GoalStatus.Active,
    val identityStatement: String? = null,
    val trajectoryStatus: TrajectoryStatus = TrajectoryStatus.OnTrack,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val goalId: String? = null,
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val habitType: HabitType = HabitType.ActionBased,
    val anchorBehavior: String? = null,
    val frequencyType: FrequencyType = FrequencyType.Daily,
    val frequencyDays: List<Int>? = null,
    val targetValue: Double? = null,
    val targetUnit: String? = null,
    val tier: HabitTier = HabitTier.Seedling,
    val estimatedMinutes: Int = 2,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class HabitLog(
    val id: String = UUID.randomUUID().toString(),
    val habitId: String = "",
    val userId: String = "",
    val completedAt: Long = System.currentTimeMillis(),
    val completionType: CompletionType = CompletionType.Manual,
    val actualValue: Double? = null,
    val notes: String? = null
)

data class Streak(
    val id: String = UUID.randomUUID().toString(),
    val habitId: String = "",
    val userId: String = "",
    val currentCount: Int = 0,
    val longestCount: Int = 0,
    val lastCompletedDate: Long? = null,
    val streakStartDate: Long? = null
)

data class Milestone(
    val id: String = UUID.randomUUID().toString(),
    val goalId: String = "",
    val title: String = "",
    val targetValue: Double = 0.0,
    val achievedAt: Long? = null
)

@com.google.gson.annotations.JsonAdapter(Achievement.Adapter::class)
data class Achievement(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    /** Null when the server sent a type this build doesn't know. */
    val achievementType: AchievementType? = AchievementType.FirstStep,
    val title: String = "",
    val description: String = "",
    val iconName: String = "",
    val earnedAt: Long = System.currentTimeMillis(),
    val sharedToFeed: Boolean = false
) {
    /** The server sends `earnedAt` as an ISO string; the local model keeps millis. */
    class Adapter : TolerantAdapterFactory<Achievement>(Achievement::class.java) {
        override fun preprocess(raw: com.google.gson.JsonObject) {
            val e = raw.get("earnedAt") ?: return
            if (e.isJsonPrimitive && e.asJsonPrimitive.isString) {
                val millis = ModelGson.LenientDateAdapter().parse(e.asString)?.time
                if (millis == null) raw.remove("earnedAt") else raw.addProperty("earnedAt", millis)
            }
        }

        override fun postwrite(tree: com.google.gson.JsonObject) {
            val e = tree.get("earnedAt") ?: return
            if (e.isJsonPrimitive && e.asJsonPrimitive.isNumber) {
                tree.addProperty("earnedAt", FitnessContent.formatIsoDate(e.asLong))
            }
        }
    }
}

/** An achievement definition from `GET /achievements`: the id is what `POST /achievements` unlocks. */
data class AchievementDefinition(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val iconUrl: String? = null,
    val category: String = "",
    val achievementType: String = ""
)

data class GoalNotificationPreference(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    var coachingTone: CoachingTone = CoachingTone.EncouragingCoach,
    var maxDailyNotifications: Int = 5,
    /** "HH:mm" local quiet-hours window; null = none. */
    var quietHoursStart: String? = null,
    var quietHoursEnd: String? = null,
    var enableHabitReminders: Boolean = true,
    var enableStreakAlerts: Boolean = true,
    var enableMilestones: Boolean = true,
    var enableAiCoaching: Boolean = true,
    var enableReengagement: Boolean = true,
    var enableGoalCheckIns: Boolean = true,
    // FTNSSLNK-233: per-category inbox toggles gating whole producers
    // (System / Goals / Content / Calendar) server-side. Missing on older
    // backends → default true, matching the server.
    var enableSystem: Boolean = true,
    var enableGoals: Boolean = true,
    var enableContent: Boolean = true,
    var enableCalendar: Boolean = true
)

data class GoalSummary(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val goalType: GoalType = GoalType.Performance,
    val progressPercent: Double = 0.0,
    val trajectoryStatus: TrajectoryStatus = TrajectoryStatus.OnTrack,
    val targetDate: Long? = null,
    val activeHabitsCount: Int = 0,
    val longestStreak: Int = 0
)

data class HabitTodayItem(
    val id: String = UUID.randomUUID().toString(),
    val habitId: String = "",
    val title: String = "",
    val habitType: HabitType = HabitType.ActionBased,
    val tier: HabitTier = HabitTier.Seedling,
    var isCompletedToday: Boolean = false,
    val streak: Streak = Streak(),
    // iOS `HabitTodayItem` fields the Home checklist renders (HabitCard).
    val anchorBehavior: String? = null,
    val targetValue: Double? = null,
    val targetUnit: String? = null,
    /** Live sensor reading for `PassiveSensor` habits; null when no provider is wired. */
    val currentSensorValue: Double? = null,
    val goalTitle: String? = null,
    val sortOrder: Int = 0
)

data class DailyProgress(
    val completedToday: Int = 0,
    val totalToday: Int = 0,
    val streakAtRisk: Boolean = false
) {
    /** 0–100, 0 when there are no habits today (iOS `DailyProgress.completionPercent`). */
    val completionPercent: Double
        get() = if (totalToday <= 0) 0.0 else completedToday.toDouble() / totalToday * 100.0
}

/**
 * iOS `HomeViewModel.updateDailyProgress`: counts today's completions and
 * flags "streak at risk" when an unfinished habit carries a streak longer
 * than a week.
 */
fun dailyProgressFor(habits: List<HabitTodayItem>): DailyProgress = DailyProgress(
    completedToday = habits.count { it.isCompletedToday },
    totalToday = habits.size,
    streakAtRisk = habits.any { !it.isCompletedToday && it.streak.currentCount > 7 }
)

/**
 * iOS `HomeViewModel.advanceStreak`: roll a habit's streak forward after a
 * completion at [nowMillis]. A one-day gap extends the count; any larger gap
 * resets to 1 and rebases `streakStartDate`; the same day returns [prior]
 * unchanged. `longestCount` only ever grows.
 */
fun advanceHabitStreak(prior: Streak, habitId: String, userId: String, nowMillis: Long = System.currentTimeMillis()): Streak {
    val today = ProgramDates.startOfDay(nowMillis)
    val keyedHabitId = prior.habitId.ifEmpty { habitId }
    val keyedUserId = prior.userId.ifEmpty { userId }
    val priorDate = prior.lastCompletedDate
    val currentCount: Int
    var streakStart = prior.streakStartDate
    if (priorDate != null) {
        when (ProgramDates.daysBetween(priorDate, today)) {
            0 -> return prior
            1 -> currentCount = prior.currentCount + 1
            else -> { currentCount = 1; streakStart = today }
        }
    } else {
        currentCount = 1
        streakStart = today
    }
    return prior.copy(
        habitId = keyedHabitId,
        userId = keyedUserId,
        currentCount = currentCount,
        longestCount = maxOf(prior.longestCount, currentCount),
        lastCompletedDate = nowMillis,
        streakStartDate = streakStart
    )
}

/**
 * iOS `uncompleteHabit` fallback when no pre-completion snapshot exists:
 * drop the count by one and clear `lastCompletedDate` so a follow-up tap
 * doesn't think today already counts. `longestCount` is historical — kept.
 */
fun rollBackHabitStreak(streak: Streak): Streak = streak.copy(
    currentCount = maxOf(0, streak.currentCount - 1),
    lastCompletedDate = null
)

data class HabitCalendarDay(
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val status: String = ""
)

data class HabitStats(
    val totalDays: Int = 0,
    val completedDays: Int = 0,
    val missedDays: Int = 0,
    val freezeDays: Int = 0,
    val completionRate: Double = 0.0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0
)
