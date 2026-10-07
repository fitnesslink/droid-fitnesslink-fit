package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.Achievement
import com.fitnesslink.fit.model.AchievementType
import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.MovementSetRecord
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.network.dto.AchievementDefinition
import com.fitnesslink.fit.network.dto.MovementHistorySummary
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

// MARK: - PRs and achievements (FTNSSLNK-413) — pure core. The database-backed
// assembly lives in SessionAchievementsBuilder.kt.

/** Ordered by ordinal: the summary lists weight → reps → duration → distance. */
enum class PersonalRecordKind { HEAVIEST_WEIGHT, MOST_REPS_AT_WEIGHT, LONGEST_DURATION, LONGEST_DISTANCE }

/** A new best for a movement, beaten against history that excludes the session being celebrated. */
data class SessionPersonalRecord(
    val movementId: String,
    val name: String,
    val kind: PersonalRecordKind,
    val weightLbs: Double = 0.0,
    val reps: Int = 0,
    val durationSeconds: Int = 0,
    val distance: DistanceValue? = null
) {
    val id: String get() = "$movementId#${kind.ordinal}"

    /** `185 lbs` / `12 reps @ 135 lbs` / `1:30 min` / `5 km`. */
    val valueText: String
        get() = when (kind) {
            PersonalRecordKind.HEAVIEST_WEIGHT -> WeightUnit.displayString(fromLbs = weightLbs)
            PersonalRecordKind.MOST_REPS_AT_WEIGHT -> "$reps reps @ ${WeightUnit.displayString(fromLbs = weightLbs)}"
            PersonalRecordKind.LONGEST_DURATION -> WorkoutTask.humanizeDuration(durationSeconds)
            PersonalRecordKind.LONGEST_DISTANCE -> distance?.let { DistanceUnit.displayInCurrent(it.value, it.unit) } ?: ""
        }

    /** `🔥 NEW PR · Barbell Squat · 185 lbs` */
    val line: String get() = "🔥 NEW PR · $name · $valueText"
}

/** Pure PR detection: this session's sets for one movement against every prior set. Nothing is declared without prior history. */
object PersonalRecordDetector {
    const val weightTolerance = 0.01

    fun detect(movementId: String, name: String, session: List<MovementSetRecord>, prior: List<MovementSetRecord>): List<SessionPersonalRecord> {
        if (prior.isEmpty() || session.isEmpty()) return emptyList()
        val out = mutableListOf<SessionPersonalRecord>()

        // Heaviest weight.
        val priorMaxWeight = prior.maxOfOrNull { it.weightLbs } ?: 0.0
        val best = session.filter { it.weightLbs > 0 }.maxByOrNull { it.weightLbs }
        if (best != null && priorMaxWeight > 0 && best.weightLbs > priorMaxWeight + weightTolerance) {
            out.add(SessionPersonalRecord(movementId, name, PersonalRecordKind.HEAVIEST_WEIGHT, weightLbs = best.weightLbs, reps = best.reps))
        }

        // Most reps at a weight lifted before.
        var repPR: SessionPersonalRecord? = null
        for (set in session) {
            if (set.weightLbs <= 0 || set.reps <= 0) continue
            val sameWeight = prior.filter { abs(it.weightLbs - set.weightLbs) <= weightTolerance }
            val priorReps = sameWeight.maxOfOrNull { it.reps } ?: continue
            if (priorReps <= 0 || set.reps <= priorReps) continue
            val current = repPR
            if (current != null && !(set.weightLbs > current.weightLbs || (set.weightLbs == current.weightLbs && set.reps > current.reps))) continue
            repPR = SessionPersonalRecord(movementId, name, PersonalRecordKind.MOST_REPS_AT_WEIGHT, weightLbs = set.weightLbs, reps = set.reps)
        }
        repPR?.let { pr ->
            if (out.none { it.kind == PersonalRecordKind.HEAVIEST_WEIGHT && abs(it.weightLbs - pr.weightLbs) <= weightTolerance }) out.add(pr)
        }

        // Longest duration.
        val priorMaxDuration = prior.mapNotNull { it.durationSeconds }.maxOrNull() ?: 0
        val bestDuration = session.mapNotNull { it.durationSeconds }.maxOrNull()
        if (bestDuration != null && priorMaxDuration > 0 && bestDuration > priorMaxDuration) {
            out.add(SessionPersonalRecord(movementId, name, PersonalRecordKind.LONGEST_DURATION, durationSeconds = bestDuration))
        }

        // Longest distance (compared in km).
        val priorMaxKm = prior.mapNotNull { it.distance }.maxOfOrNull { it.converted(DistanceUnit.KM).value } ?: 0.0
        val bestDistance = session.mapNotNull { it.distance }.maxByOrNull { it.converted(DistanceUnit.KM).value }
        if (bestDistance != null && priorMaxKm > 0 && bestDistance.converted(DistanceUnit.KM).value > priorMaxKm + 0.0001) {
            out.add(SessionPersonalRecord(movementId, name, PersonalRecordKind.LONGEST_DISTANCE, distance = bestDistance))
        }
        return out.sortedBy { it.kind.ordinal }
    }
}

/** Consecutive days with a completed session, counting back from `asOf` (which itself counts). */
object TrainingStreak {
    fun days(sessionDatesMs: List<Long>, asOfMs: Long, timeZone: TimeZone = TimeZone.getDefault()): Int {
        val days = (sessionDatesMs + asOfMs).map { startOfDay(it, timeZone) }.toSet()
        var cursor = startOfDay(asOfMs, timeZone)
        var count = 0
        while (days.contains(cursor)) {
            count += 1
            cursor = addDays(cursor, -1, timeZone)
        }
        return count
    }

    internal fun startOfDay(ms: Long, tz: TimeZone): Long = Calendar.getInstance(tz, Locale.US).apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    internal fun addDays(ms: Long, days: Int, tz: TimeZone): Long = Calendar.getInstance(tz, Locale.US).apply {
        timeInMillis = ms
        add(Calendar.DAY_OF_MONTH, days)
    }.timeInMillis
}

/** Week / program completion for a session started from an enrolled program, judged on the program's calendar entries. */
sealed class ProgramMilestone {
    data class WeekComplete(val week: Int) : ProgramMilestone()
    object ProgramComplete : ProgramMilestone() { override fun toString() = "ProgramComplete" }

    val text: String
        get() = when (this) {
            is WeekComplete -> "Program milestone · Week $week complete"
            ProgramComplete -> "Program milestone · Program complete"
        }

    companion object {
        /** Weeks start on Sunday, like the program calendar. Null when the workout isn't on the program's calendar or work remains. */
        fun evaluate(entries: List<FitnessContent>, workoutId: String, asOfMs: Long, timeZone: TimeZone = TimeZone.getDefault()): ProgramMilestone? {
            val dated = entries.filter { it.scheduledDate != null && it.workoutId.isNotEmpty() }
            if (dated.isEmpty()) return null
            val today = TrainingStreak.startOfDay(asOfMs, timeZone)
            val mine = dated.filter { it.workoutId.equals(workoutId, ignoreCase = true) }
            val entry = mine.firstOrNull { TrainingStreak.startOfDay(it.scheduledDate!!, timeZone) == today }
                ?: mine.minByOrNull { abs(it.scheduledDate!! - today) }
                ?: return null
            fun isDone(e: FitnessContent) = e.id == entry.id || e.status.equals("completed", ignoreCase = true)
            if (dated.all(::isDone)) return ProgramComplete
            val anchor = entry.scheduledDate ?: return null
            val weekStart = startOfWeek(anchor, timeZone)
            val sameWeek = dated.filter { startOfWeek(it.scheduledDate!!, timeZone) == weekStart }
            if (!sameWeek.all(::isDone)) return null
            val first = dated.mapNotNull { it.scheduledDate }.minOrNull() ?: anchor
            val firstWeek = startOfWeek(first, timeZone)
            val weeks = ((weekStart - firstWeek) / (7L * 86_400_000L)).toInt()
            return WeekComplete(maxOf(weeks, 0) + 1)
        }

        private fun startOfWeek(ms: Long, tz: TimeZone): Long {
            val cal = Calendar.getInstance(tz, Locale.US).apply {
                timeInMillis = TrainingStreak.startOfDay(ms, tz)
            }
            val dow = cal.get(Calendar.DAY_OF_WEEK) // Sunday = 1
            cal.add(Calendar.DAY_OF_MONTH, -(dow - Calendar.SUNDAY))
            return cal.timeInMillis
        }
    }
}

/** One line of the summary's "Your Achievements" section. */
sealed class SessionCelebration {
    data class PersonalRecord(val record: SessionPersonalRecord) : SessionCelebration()
    data class FirstTimeLogged(val movementId: String, val name: String) : SessionCelebration()
    object FirstWorkout : SessionCelebration()
    data class Streak(val days: Int) : SessionCelebration()
    data class Milestone(val milestone: ProgramMilestone) : SessionCelebration()

    val id: String
        get() = when (this) {
            is PersonalRecord -> "pr:${record.id}"
            is FirstTimeLogged -> "first:$movementId"
            FirstWorkout -> "firstWorkout"
            is Streak -> "streak"
            is Milestone -> "milestone"
        }

    val text: String
        get() = when (this) {
            is PersonalRecord -> record.line
            is FirstTimeLogged -> "First time logged · $name"
            FirstWorkout -> "First workout completed"
            is Streak -> "$days-day training streak"
            is Milestone -> milestone.text
        }

    /** The server achievement this unlocks; null for celebrations with no matching type. */
    val achievementType: AchievementType?
        get() = when (this) {
            is PersonalRecord -> AchievementType.PersonalRecord
            FirstWorkout -> AchievementType.FirstWorkout
            is Streak -> AchievementType.StreakMaster
            is FirstTimeLogged, is Milestone -> null
        }
}

/** Everything the summary celebrates for one session. Empty → the section is not rendered at all. */
data class SessionAchievements(
    val records: List<SessionPersonalRecord> = emptyList(),
    val firstTimeLogged: List<Pair<String, String>> = emptyList(),
    val isFirstWorkout: Boolean = false,
    val streakDays: Int = 0,
    val milestone: ProgramMilestone? = null
) {
    /** PRs, first-time logs, then the session-level milestones. */
    val items: List<SessionCelebration>
        get() {
            val out = mutableListOf<SessionCelebration>()
            out += records.map { SessionCelebration.PersonalRecord(it) }
            out += firstTimeLogged.map { (id, name) -> SessionCelebration.FirstTimeLogged(id, name) }
            if (isFirstWorkout) out.add(SessionCelebration.FirstWorkout)
            if (streakDays >= minimumStreakDays) out.add(SessionCelebration.Streak(streakDays))
            milestone?.let { out.add(SessionCelebration.Milestone(it)) }
            return out
        }

    val isEmpty: Boolean get() = items.isEmpty()

    /** How many per-movement lines hide behind "+N more". */
    val foldedRecordCount: Int get() = maxOf(0, records.size + firstTimeLogged.size - visibleRecordLimit)

    /** Server achievement types to unlock, deduplicated. */
    val achievementTypes: List<AchievementType> get() = items.mapNotNull { it.achievementType }.distinct()

    companion object {
        /** PRs (and first-time logs) shown before the "+N more" fold. */
        const val visibleRecordLimit = 3
        /** A streak is worth mentioning from two days on. */
        const val minimumStreakDays = 2

        data class MovementSets(val movementId: String, val name: String, val sets: List<MovementSetRecord>)

        /** Sets of this session per movement, from the performed history rows. */
        fun sessionSets(rows: List<SessionHistoryRow>): List<MovementSets> {
            val order = mutableListOf<String>()
            val byMovement = mutableMapOf<String, Pair<String, MutableList<MovementSetRecord>>>()
            for (row in rows) {
                if (!row.isPerformed) continue
                val movementId = row.task.movementId?.lowercase(Locale.US)
                if (movementId.isNullOrEmpty()) continue
                val record = MovementSetRecord(row.weightLbs, row.reps, row.durationSeconds, row.distance)
                val existing = byMovement[movementId]
                if (existing == null) {
                    order.add(movementId)
                    byMovement[movementId] = Pair(row.task.name, mutableListOf(record))
                } else existing.second.add(record)
            }
            return order.mapNotNull { id -> byMovement[id]?.let { MovementSets(id, it.first, it.second) } }
        }

        /** Prior sets from the server's per-movement summary (a fresh install with no local history). */
        fun priorSets(summary: MovementHistorySummary): List<MovementSetRecord> {
            val out = mutableListOf<MovementSetRecord>()
            summary.bestWeight?.takeIf { it > 0 }?.let { out.add(MovementSetRecord(weightLbs = it, reps = summary.bestReps ?: 0)) }
            summary.bestDurationSeconds?.takeIf { it > 0 }?.let { out.add(MovementSetRecord(durationSeconds = it)) }
            val distance = summary.bestDistance
            val unit = DistanceUnit.fromWire(summary.distanceUnit)
            if (distance != null && distance > 0 && unit != null) out.add(MovementSetRecord(distance = DistanceValue(distance, unit)))
            return out
        }

        /** Pure assembly from already-fetched inputs (the testable core). */
        fun assemble(
            rows: List<SessionHistoryRow>,
            priorSets: (String) -> List<MovementSetRecord>,
            priorCompletedSessions: Int,
            sessionDatesMs: List<Long>,
            programEntries: List<FitnessContent>,
            workoutId: String,
            asOfMs: Long,
            timeZone: TimeZone = TimeZone.getDefault()
        ): SessionAchievements {
            val records = mutableListOf<SessionPersonalRecord>()
            val firstTime = mutableListOf<Pair<String, String>>()
            for (movement in sessionSets(rows)) {
                val prior = priorSets(movement.movementId)
                if (prior.isEmpty()) {
                    if (movement.sets.any { it.hasMetric }) firstTime.add(movement.movementId to movement.name)
                    continue
                }
                records += PersonalRecordDetector.detect(movement.movementId, movement.name, movement.sets, prior)
            }
            val milestone = if (programEntries.isNotEmpty()) ProgramMilestone.evaluate(programEntries, workoutId, asOfMs, timeZone) else null
            return SessionAchievements(
                records = records,
                firstTimeLogged = firstTime,
                isFirstWorkout = priorCompletedSessions == 0,
                streakDays = TrainingStreak.days(sessionDatesMs, asOfMs, timeZone),
                milestone = milestone
            )
        }
    }
}

// MARK: - Unlocking (achievements API)

/**
 * Unlocks the server achievements a summary earned: `GET /achievements/me` to skip what is already
 * earned, `GET /achievements` for the definition ids, then `POST /achievements` per missing type.
 * Every failure is silent; the summary has already celebrated locally.
 */
object AchievementUnlocker {
    /** Injectable calls for tests. */
    class Client(
        val mine: suspend () -> List<Achievement>,
        val definitions: suspend () -> List<AchievementDefinition>,
        val unlock: suspend (String) -> Unit
    )

    /** Which definitions to post for [types], given what the user has. */
    fun pending(types: List<AchievementType>, earned: List<Achievement>, definitions: List<AchievementDefinition>): List<AchievementDefinition> {
        val have = earned.mapNotNull { it.achievementType }.toSet()
        return types.filter { !have.contains(it) }.mapNotNull { type ->
            definitions.firstOrNull { it.achievementType.equals(type.name, ignoreCase = true) }
        }
    }

    suspend fun unlock(types: List<AchievementType>, client: Client, isConnected: Boolean): List<String> {
        if (types.isEmpty() || !isConnected) return emptyList()
        return try {
            val earned = client.mine()
            val definitions = client.definitions()
            val unlocked = mutableListOf<String>()
            for (definition in pending(types, earned, definitions)) {
                try {
                    client.unlock(definition.id)
                    unlocked.add(definition.id)
                } catch (_: Exception) {
                    continue
                }
            }
            unlocked
        } catch (_: Exception) {
            emptyList()
        }
    }
}
