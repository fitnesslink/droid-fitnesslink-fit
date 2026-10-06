package com.fitnesslink.fit.model

import com.fitnesslink.fit.model.TolerantAdapterFactory.Companion.optInt
import com.fitnesslink.fit.model.TolerantAdapterFactory.Companion.optString
import com.fitnesslink.fit.model.api.AuditFields
import com.fitnesslink.fit.model.api.EntityID
import com.fitnesslink.fit.model.api.ProgramContributor
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.JsonAdapter
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@JsonAdapter(Workout.Adapter::class)
data class Workout(
    val id: String = "",
    val name: String = "",
    /** iOS-only display string ("45 min"); synthesized from [estimatedTime] when the server omits it. */
    val time: String = "",
    val location: String = "",
    /** Legacy single training level — mirrors `trainingLevels.first()`. */
    val trainingLevel: String = "",
    /** All training levels in the server's canonical order. Source of truth for editing + save. */
    val trainingLevels: List<String> = emptyList(),
    val description: String = "",
    val phases: List<WorkoutPhase> = emptyList(),

    // MARK: - API-aligned fields

    val statusId: EntityID? = null,
    val contributorId: EntityID? = null,
    val imageId: EntityID? = null,
    val thumbnailId: EntityID? = null,
    /** Pre-resolved SAS URLs; when present the image view skips the `/media/resolve` hop. */
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    /** SECONDS. */
    val estimatedTime: Int? = null,
    val showInLibrary: Boolean = true,
    val preBuiltTemplateId: EntityID? = null,
    val audit: AuditFields? = null,
    /** When a shared workout entered this user's library (accept time). Cached as `createdAt`. */
    val libraryAddedAt: Date? = null,

    /** Sharing flags from /workouts/list-view + /workouts/{id}. Live-only. */
    val isSharedWithMe: Boolean = false,
    val sharedByUserId: EntityID? = null,

    /** Equipment / anatomy ids derived server-side from the movements (list-view). Live-only. */
    val equipmentIds: List<EntityID> = emptyList(),
    val anatomyIds: List<EntityID> = emptyList(),
    /** Override-with-fallback tags contract: true when the editor chose an explicit set. */
    val hasExplicitEquipment: Boolean = false,
    val hasExplicitAnatomy: Boolean = false,
    /** "Equipment Needed" — distinct equipment names, deduplicated and alphabetized. Persisted. */
    val equipment: List<String> = emptyList(),
    /** Server flag: scheduled inside a contributor (template) program. null = server hasn't said. */
    val partOfContributorProgram: Boolean? = null,
    /** Public contributor profile for the "About the Coach" card. Detail payload only. Live-only. */
    val contributor: ProgramContributor? = null,

    /** Server-order rank from the last list-view snapshot (local only). */
    val sortIndex: Int? = null,
    /** Local favorite flag (local only — the list-view payload doesn't carry it). */
    val isFavorite: Boolean = false
) {
    /**
     * Every [WorkoutTask] in playback order, including the secondary members
     * of grouped rows. Each task carries its phase's name.
     */
    val allTasks: List<WorkoutTask> get() = phases.flatMap { it.allTasks }

    /**
     * Content-derived duration estimate in minutes, for workouts whose
     * display [time] was never set. Null when there is no timeable content.
     */
    val estimatedContentMinutes: Int?
        get() {
            val seconds = WorkoutDurationEstimator.plannedSeconds(rows = phases.flatMap { it.taskRows })
            if (seconds <= 0) return null
            return maxOf((seconds / 60.0).roundToInt(), 1)
        }

    /**
     * The estimated time every workout shows before it starts: the
     * configured [estimatedTime] when the builder set one ("45 min"),
     * otherwise an estimate over the programmed tasks ("~38 min"). Empty
     * only when neither is known.
     */
    val displayTime: String
        get() {
            if (time.isNotEmpty()) return time
            estimatedTime?.takeIf { it > 0 }?.let { return formatEstimatedTime(it) }
            estimatedContentMinutes?.let { return "~" + formatEstimatedTime(it * 60) }
            return ""
        }

    /**
     * Reconcile the three places "rounds" gets stored on a grouped row so
     * callers see a consistent value no matter which surface they read:
     * `WorkoutTask.rounds` (wire source of truth), `WorkoutTask.totalRounds`
     * (session "X/Y ROUNDS" label) and `TaskRow.totalRounds` (editor
     * stepper). Call after loading from the local DB or accepting a server
     * response. Exact port of iOS `Workout.hydrateGroupRounds()`.
     */
    fun hydrateGroupRounds(): Workout = copy(phases = phases.map { phase ->
        phase.copy(taskRows = phase.taskRows.map(::hydrateRow))
    })

    private fun hydrateRow(row: TaskRow): TaskRow {
        // Server round-trips can clear the isSuperset/isCircuit flags
        // entirely (groups persist as plain "Movement" tasks sharing a
        // groupId). Infer the type the same way the playlist container does —
        // 2 movements = superset, 3+ = circuit.
        val isInterval = (row.task?.isInterval ?: false) || row.isInterval
        val leadIsMovement = row.task?.isMovement ?: false
        val movementCount = (if (leadIsMovement) 1 else 0) + row.advancedTasks.count { it.isMovement }
        val isSuperset = row.isSuperset || (!row.isCircuit && !isInterval && movementCount == 2)
        val isCircuit = row.isCircuit || (!row.isSuperset && !isInterval && movementCount >= 3)
        if (isInterval) {
            // Interval protocol fields live on the lead only, so members
            // decode with isInterval == false. Stamp the row-level flag on
            // every member so metric lines stay hidden for timed work.
            return row.copy(
                isInterval = true,
                task = row.task?.copy(isInterval = true),
                advancedTasks = row.advancedTasks.map { it.copy(isInterval = true) }
            )
        }
        if (!isSuperset && !isCircuit) return row
        val canonical: Int = row.task?.rounds?.takeIf { it > 0 }
            ?: row.totalRounds.takeIf { it > 0 }
            ?: 3
        // Legacy workouts had grouping expressed only on the TaskRow — the
        // tasks had empty groupId. Backfill so the session player (which
        // keys off task.groupId) can recognize the group.
        val groupId = row.task?.groupId?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().lowercase(Locale.US)
        return row.copy(
            isSuperset = isSuperset,
            isCircuit = isCircuit,
            totalRounds = canonical,
            rounds = "$canonical rounds",
            task = row.task?.copy(
                rounds = canonical,
                totalRounds = canonical,
                groupId = groupId,
                isSuperset = isSuperset,
                isCircuit = isCircuit
            ),
            // Mirror rounds onto siblings so the round counter renders
            // correctly when positioned on a non-lead member.
            advancedTasks = row.advancedTasks.map {
                it.copy(groupId = groupId, isSuperset = isSuperset, isCircuit = isCircuit,
                    rounds = canonical, totalRounds = canonical)
            }
        )
    }

    /**
     * The local `workouts` table used to persist only the "<n> min" display
     * string. Recover the numeric value from it so a save built from a cached
     * row pushes the time the user chose instead of 0 / null.
     */
    fun recoverEstimatedTimeFromDisplayString(): Workout {
        if (estimatedTime != null) return this
        val seconds = secondsFromDisplayTime(time) ?: return this
        return copy(estimatedTime = seconds)
    }

    companion object {
        /**
         * Formats a raw `estimatedTime` (SECONDS) into "53 min" / "1 hr" /
         * "1 hr 20 min". Returns "" for non-positive input.
         */
        fun formatEstimatedTime(seconds: Int): String {
            val totalMinutes = (seconds / 60.0).roundToInt()
            if (totalMinutes <= 0) return ""
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours == 0 -> "$minutes min"
                minutes == 0 -> "$hours hr"
                else -> "$hours hr $minutes min"
            }
        }

        /**
         * Inverse of [formatEstimatedTime] for the configured value only:
         * "45 min" → 2700, "1 hr 30 min" → 5400. A content estimate ("~38
         * min") or anything else → null.
         */
        fun secondsFromDisplayTime(text: String): Int? {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("~")) return null
            var minutes = 0
            var matched = false
            val parts = trimmed.split(" ").filter { it.isNotEmpty() }
            var i = 0
            while (i + 1 < parts.size) {
                val n = parts[i].toIntOrNull()?.takeIf { it >= 0 } ?: return null
                when (parts[i + 1]) {
                    "hr", "hrs", "hour", "hours" -> minutes += n * 60
                    "min", "mins", "minute", "minutes" -> minutes += n
                    else -> return null
                }
                matched = true
                i += 2
            }
            if (!matched || i != parts.size || minutes <= 0) return null
            return minutes * 60
        }
    }

    /**
     * iOS `init(from:)` back-compat: seed `trainingLevels` from the legacy
     * single string (and vice versa) and synthesize the display `time` from
     * the numeric `estimatedTime` when the payload omits it.
     */
    class Adapter : TolerantAdapterFactory<Workout>(Workout::class.java) {
        override fun preprocess(raw: JsonObject) {
            val level = raw.optString("trainingLevel") ?: ""
            val levels = raw.get("trainingLevels")?.takeIf { it.isJsonArray }?.asJsonArray
            if ((levels == null || levels.size() == 0) && level.isNotEmpty()) {
                raw.add("trainingLevels", JsonArray().apply { add(level) })
            } else if (level.isEmpty() && levels != null && levels.size() > 0) {
                raw.addProperty("trainingLevel", levels[0].asString)
            }
            val time = raw.optString("time") ?: ""
            val seconds = raw.optInt("estimatedTime")
            if (time.isEmpty() && seconds != null && seconds > 0) {
                raw.addProperty("time", formatEstimatedTime(seconds))
            }
            // A malformed contributor object should drop the card, not fail the decode.
            raw.get("contributor")?.let { if (!it.isJsonObject) raw.remove("contributor") }
        }
    }
}

typealias WorkoutProgram = Program
