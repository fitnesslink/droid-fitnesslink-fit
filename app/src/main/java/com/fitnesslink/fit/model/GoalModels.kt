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

enum class AchievementType(val title: String, val description: String) {
    FirstStep("First Step", "Complete your first habit"),
    WeekOne("Week One!", "7-day streak achieved"),
    HabitStacker("Habit Stacker", "3+ active habits simultaneously"),
    TierUp("Tier Up", "Graduate from Seedling to Sprout"),
    CenturyClub("Century Club", "100-day streak"),
    FullCircle("Full Circle", "Complete a goal start to finish"),
    DataDriven("Data Driven", "Log food, workout, and sleep in one day"),
    ConsistencyRoyalty("Consistency Royalty", "90%+ completion rate for 30 days")
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

data class Achievement(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val achievementType: AchievementType = AchievementType.FirstStep,
    val title: String = "",
    val earnedAt: Long = System.currentTimeMillis()
)

data class GoalNotificationPreference(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    var coachingTone: CoachingTone = CoachingTone.EncouragingCoach,
    var maxDailyNotifications: Int = 5,
    var enableHabitReminders: Boolean = true,
    var enableStreakAlerts: Boolean = true,
    var enableMilestones: Boolean = true,
    var enableAiCoaching: Boolean = true,
    var enableReengagement: Boolean = true,
    var enableGoalCheckIns: Boolean = true
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
    val streak: Streak = Streak()
)

data class DailyProgress(
    val completedToday: Int = 0,
    val totalToday: Int = 0,
    val streakAtRisk: Boolean = false
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
