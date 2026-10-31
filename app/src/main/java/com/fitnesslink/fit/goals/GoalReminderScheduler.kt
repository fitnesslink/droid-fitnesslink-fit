package com.fitnesslink.fit.goals

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fitnesslink.fit.MainActivity
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.Goal
import com.fitnesslink.fit.model.GoalStatus
import com.fitnesslink.fit.model.GoalSubtype
import com.fitnesslink.fit.push.PushNotificationManager
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Local reminders for a goal (PRD 1.5 step 7; iOS `GoalReminderScheduler`).
 * Repeating WorkManager jobs on the chosen weekdays (or daily); removed when
 * the goal is paused, completed, archived or deleted. Tapping one opens the
 * goal via the `goals/{id}` deep link.
 */
object GoalReminderScheduler {
    private const val TAG = "GoalReminders"
    internal const val KEY_GOAL_ID = "goalId"

    @Volatile private var appContext: Context? = null

    /** Screens call this with any context; reminders no-op until a context is known. */
    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun tag(goalId: String) = "goal-reminder-${goalId.lowercase(Locale.US)}"

    fun cancel(goalId: String) {
        val ctx = appContext ?: return
        runCatching { WorkManager.getInstance(ctx).cancelAllWorkByTag(tag(goalId)) }
    }

    fun sync(goal: Goal) {
        val ctx = appContext ?: return
        cancel(goal.id)
        val reminder = goal.settings.reminder
        if (goal.status != GoalStatus.Active || reminder == null || !reminder.enabled) return
        val manager = runCatching { WorkManager.getInstance(ctx) }.getOrNull() ?: return
        val weekdays = reminder.weekdays?.takeIf { it.isNotEmpty() }
        val now = LocalDateTime.now()
        val slots: List<Int> = weekdays ?: listOf(0)
        for (weekday in slots) {
            val next = nextOccurrence(now, reminder.hour, reminder.minute, weekday)
            val delayMs = Duration.between(now.atZone(ZoneId.systemDefault()), next.atZone(ZoneId.systemDefault())).toMillis().coerceAtLeast(0)
            val periodDays = if (weekday == 0) 1L else 7L
            val request = PeriodicWorkRequestBuilder<GoalReminderWorker>(periodDays, TimeUnit.DAYS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_GOAL_ID to goal.id.lowercase(Locale.US)))
                .addTag(tag(goal.id))
                .build()
            runCatching {
                manager.enqueueUniquePeriodicWork("${tag(goal.id)}-$weekday", ExistingPeriodicWorkPolicy.UPDATE, request)
            }.onFailure { Log.w(TAG, "schedule failed: ${it.message}") }
        }
    }

    /** Next [hour]:[minute] on [weekday] (1 = Sunday … 7 = Saturday; 0 = any day), strictly after [now]. */
    internal fun nextOccurrence(now: LocalDateTime, hour: Int, minute: Int, weekday: Int): LocalDateTime {
        var candidate = now.toLocalDate().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        if (weekday in 1..7) {
            // iOS weekday 1 = Sunday; java.time Monday = 1 … Sunday = 7.
            val target = DayOfWeek.of(((weekday + 5) % 7) + 1)
            while (candidate.dayOfWeek != target || !candidate.isAfter(now)) candidate = candidate.plusDays(1)
        } else if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    internal fun body(goal: Goal): String = when (goal.subtype) {
        GoalSubtype.LoseWeight -> "Time to log your weight."
        GoalSubtype.SleepDuration, GoalSubtype.SleepConsistency -> "Log last night's sleep to keep your goal on track."
        GoalSubtype.CalorieTarget, GoalSubtype.MacroTarget, GoalSubtype.HealthyHabit -> "Log your food for today."
        GoalSubtype.Hydration -> "Remember to log your water."
        GoalSubtype.WorkoutConsistency -> "Time for today's workout."
        GoalSubtype.ImproveLift, GoalSubtype.PersonalRecord, GoalSubtype.RepsAtWeight, GoalSubtype.TrainingVolume ->
            "Log your training to track your strength goal."
        else -> "Check in on your goal for today."
    }
}

/** Fires one goal reminder; skips it if the goal is gone or no longer active. */
class GoalReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val goalId = inputData.getString(GoalReminderScheduler.KEY_GOAL_ID) ?: return Result.success()
        val goal = GoalStore.goal(goalId)
        if (goal == null || goal.status != GoalStatus.Active || goal.settings.reminder?.enabled != true) {
            GoalReminderScheduler.attach(applicationContext)
            GoalReminderScheduler.cancel(goalId)
            return Result.success()
        }
        val ctx = applicationContext
        val tap = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(PushNotificationManager.EXTRA_DEEP_LINK, "goals/$goalId")
        }
        val requestCode = goalId.hashCode()
        val pending = PendingIntent.getActivity(ctx, requestCode, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = GoalReminderScheduler.body(goal)
        val notification = NotificationCompat.Builder(ctx, PushNotificationManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fitnesslink)
            .setContentTitle(goal.title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(requestCode, notification)
        } catch (_: SecurityException) { /* notification permission not granted */ }
        return Result.success()
    }
}
