package com.fitnesslink.fit.model

import com.fitnesslink.fit.model.TolerantAdapterFactory.Companion.optInt
import com.fitnesslink.fit.model.api.MuxStatus
import com.fitnesslink.fit.model.api.RestTypeId
import com.google.gson.JsonObject
import com.google.gson.annotations.JsonAdapter
import java.util.Locale

/**
 * Distinguishes a rest task between *passive rest* (just stop and wait) and
 * *active recovery* (perform a low-intensity movement during the rest
 * window). In-memory only — the wire format is a Guid string on
 * [WorkoutTask.restTypeId], resolved against the server's seeded `RestType`
 * rows ("Passive" / "Active Recovery").
 */
enum class RestType {
    PASSIVE, ACTIVE_RECOVERY;

    /** Display name as seeded on the backend's `RestType` lookup table. */
    val displayName: String
        get() = when (this) {
            PASSIVE -> "Passive"
            ACTIVE_RECOVERY -> "Active Recovery"
        }

    companion object {
        /**
         * Hooks for the classification cache (wave 0.10). Until it is
         * wired, the hard-coded [RestTypeId] fallbacks resolve both ways.
         */
        @Volatile var nameResolver: ((id: String) -> String?)? = null
        @Volatile var idResolver: ((displayName: String) -> String?)? = null

        /** Inverse of [displayName], tolerant of the legacy iOS spellings. */
        fun from(displayName: String): RestType? = when (displayName.lowercase(Locale.US)) {
            "passive" -> PASSIVE
            "active recovery", "activerecovery" -> ACTIVE_RECOVERY
            else -> null
        }

        fun resolve(restTypeId: String?): RestType {
            if (restTypeId.isNullOrEmpty()) return PASSIVE
            val name = nameResolver?.invoke(restTypeId) ?: RestTypeId.fallbackName(restTypeId)
            name?.let { from(it) }?.let { return it }
            // Legacy on-disk values written before the Guid contract.
            return from(restTypeId) ?: PASSIVE
        }

        fun id(for_: RestType): String =
            idResolver?.invoke(for_.displayName) ?: RestTypeId.fallbackByName(for_.displayName)
    }
}

@JsonAdapter(WorkoutTask.Adapter::class)
data class WorkoutTask(
    val id: String = "",
    val name: String = "",
    val metric: String = "",
    val isMovement: Boolean = false,
    val isRest: Boolean = false,
    val isAdvanced: Boolean = false,
    val isSuperset: Boolean = false,
    val isCircuit: Boolean = false,
    val isInterval: Boolean = false,
    val reps: Int = 0,
    val sets: Int = 0,
    val restSeconds: Int = 0,
    val rest: String = "",
    val round: String = "",
    val totalRounds: Int = 0,
    val phaseName: String = "",
    val phaseId: String = "",
    val advancedMovement: String = "",
    val currentSet: Int = 0,
    val order: Int = 0,
    val groupId: String = "",

    // MARK: - API-aligned fields

    val workoutId: String? = null,
    val movementId: String? = null,
    val movementImageId: String? = null,
    val movementThumbnailId: String? = null,
    val movementVideoId: String? = null,
    /** Pre-resolved SAS URLs from the backend payload (see `Workout.imageUrl`). */
    val movementImageUrl: String? = null,
    val movementThumbnailUrl: String? = null,
    val movementVideoUrl: String? = null,
    /** Mux ingest state for the movement's form video. READY = playable signed HLS manifest. */
    val movementVideoStatus: MuxStatus? = null,
    val movementVideoAspectRatio: String? = null,
    val movementVideoDurationMs: Int? = null,
    /** Optional 9:16 portrait variant; read through the `effective*` accessors. */
    val movementPhoneVideoId: String? = null,
    val movementPhoneVideoUrl: String? = null,
    val movementPhoneVideoStatus: MuxStatus? = null,
    val movementPhoneVideoAspectRatio: String? = null,
    val movementPhoneVideoDurationMs: Int? = null,
    val advancedMovementId: String? = null,
    val workoutTaskTypeId: String? = null,
    val workoutPhaseId: String? = null,
    val rpeId: String? = null,
    val metricTypeId: String? = null,
    val restTypeId: String? = null,
    /** Movement chosen for active-recovery rests. Null for passive rests. */
    val restMovementId: String? = null,
    val restMovementName: String? = null,
    val restMovementThumbnailId: String? = null,
    val restMovementThumbnailUrl: String? = null,
    val restMovementVideoId: String? = null,
    val restMovementVideoUrl: String? = null,
    val restMovementVideoStatus: MuxStatus? = null,
    val restMovementPhoneVideoId: String? = null,
    val restMovementPhoneVideoUrl: String? = null,
    val restMovementPhoneVideoStatus: MuxStatus? = null,
    /** Holds POUNDS app-wide despite the legacy column name. */
    val weightKg: Int? = null,
    val caloriesBurned: Int? = null,
    val heartRate: Double? = null,
    val incline: Double? = null,
    val paceSeconds: Int? = null,
    val speed: Double? = null,
    val rounds: Int? = null,
    val instructions: String? = null,
    val contributorId: String? = null,

    // MARK: - Metric mode

    /** True when the task is timed (plank, treadmill): per-set values are `durationSeconds`, not `reps`. */
    val usesDuration: Boolean = false,
    /** Task-level default duration in seconds (analogous to `reps`). Null when `usesDuration` is false. */
    val durationSeconds: Int? = null,

    // MARK: - Per-set configuration

    val sameAcrossSets: Boolean = true,
    val setConfigs: List<TaskSetConfig> = emptyList(),

    // MARK: - Interval training (lead task only)

    val intervalMode: IntervalMode? = null,
    val intervalWorkSeconds: Int? = null,
    val intervalRestSeconds: Int? = null,
    val intervalWindowSeconds: Int? = null,
    /** Outer loop count — sets of the inner rounds. null / <= 1 = single pass. */
    val intervalSets: Int? = null,
    /** Rest between SETS of an interval block. Read through [effectiveIntervalSetRestSeconds]. */
    val intervalSetRestSeconds: Int? = null,

    // MARK: - Superset / Circuit between-rounds rest

    /** Pause between rounds of a superset / circuit. null = unset, 0 = explicitly off. */
    val roundRestSeconds: Int? = null,

    /** Prescribed distance for cardio / distance work, in [distanceUnit] ("km" | "mi"). */
    val distance: Double? = null,
    val distanceUnit: String? = null
) {
    /**
     * Between-sets rest for an interval lead: the dedicated column when
     * present, else the legacy `roundRestSeconds` (interval tasks only).
     */
    val effectiveIntervalSetRestSeconds: Int?
        get() = intervalSetRestSeconds ?: if (isInterval) roundRestSeconds else null

    // MARK: - Video readiness

    val isMovementVideoReady: Boolean get() = (movementVideoStatus ?: MuxStatus.READY) == MuxStatus.READY
    val isRestMovementVideoReady: Boolean get() = (restMovementVideoStatus ?: MuxStatus.READY) == MuxStatus.READY
    val isMovementPhoneVideoReady: Boolean
        get() = movementPhoneVideoId != null && (movementPhoneVideoStatus ?: MuxStatus.READY) == MuxStatus.READY
    val isRestMovementPhoneVideoReady: Boolean
        get() = restMovementPhoneVideoId != null && (restMovementPhoneVideoStatus ?: MuxStatus.READY) == MuxStatus.READY

    /** FULL-SCREEN playback: the 9:16 variant when present and ready, else the landscape video. */
    val effectiveMovementVideoId: String? get() = if (isMovementPhoneVideoReady) movementPhoneVideoId else movementVideoId
    val effectiveMovementVideoUrl: String? get() = if (isMovementPhoneVideoReady) movementPhoneVideoUrl else movementVideoUrl
    val effectiveMovementVideoStatus: MuxStatus? get() = if (isMovementPhoneVideoReady) movementPhoneVideoStatus else movementVideoStatus
    val effectiveMovementVideoAspectRatio: String? get() = if (isMovementPhoneVideoReady) movementPhoneVideoAspectRatio else movementVideoAspectRatio

    private val hasLandscapeMovementVideo: Boolean get() = movementVideoId != null && isMovementVideoReady

    /** INLINE / horizontal surfaces: landscape first, phone variant only as a fallback. */
    val inlineMovementVideoId: String? get() = if (hasLandscapeMovementVideo) movementVideoId else effectiveMovementVideoId
    val inlineMovementVideoUrl: String? get() = if (hasLandscapeMovementVideo) movementVideoUrl else effectiveMovementVideoUrl
    val inlineMovementVideoStatus: MuxStatus? get() = if (hasLandscapeMovementVideo) movementVideoStatus else effectiveMovementVideoStatus

    val effectiveRestMovementVideoId: String? get() = if (isRestMovementPhoneVideoReady) restMovementPhoneVideoId else restMovementVideoId
    val effectiveRestMovementVideoUrl: String? get() = if (isRestMovementPhoneVideoReady) restMovementPhoneVideoUrl else restMovementVideoUrl
    val effectiveRestMovementVideoStatus: MuxStatus? get() = if (isRestMovementPhoneVideoReady) restMovementPhoneVideoStatus else restMovementVideoStatus

    val isEffectiveMovementVideoReady: Boolean
        get() = effectiveMovementVideoId != null && (effectiveMovementVideoStatus ?: MuxStatus.READY) == MuxStatus.READY
    val isEffectiveRestMovementVideoReady: Boolean
        get() = effectiveRestMovementVideoId != null && (effectiveRestMovementVideoStatus ?: MuxStatus.READY) == MuxStatus.READY

    // MARK: - Rest type

    /** Convenience accessor over the raw [restTypeId] Guid. Unknown values map to PASSIVE. */
    val restType: RestType get() = RestType.resolve(restTypeId)

    /**
     * Setter counterpart of [restType]: stores the Guid for the enum and
     * clears the `restMovement*` fields when switching back to passive so
     * stale picker state doesn't leak into the wire format.
     */
    fun withRestType(type: RestType): WorkoutTask {
        val base = copy(restTypeId = RestType.id(type))
        if (type != RestType.PASSIVE) return base
        return base.copy(
            restMovementId = null, restMovementName = null,
            restMovementThumbnailId = null, restMovementThumbnailUrl = null,
            restMovementVideoId = null, restMovementVideoUrl = null, restMovementVideoStatus = null,
            restMovementPhoneVideoId = null, restMovementPhoneVideoUrl = null, restMovementPhoneVideoStatus = null
        )
    }

    // MARK: - Display

    /**
     * Display-ready metric line for workout / session views. Falls back to
     * computing from raw fields when the cached `metric` string is empty.
     */
    val displayMetric: String
        get() {
            if (hasPerSetBreakdown) return variedMetricSummary
            val base: String
            var cachedLoadLbs: Double? = null
            if (usesDuration) {
                val dur = humanizeDuration(durationSeconds ?: 0)
                base = if (sets > 0) "$sets x $dur" else dur
            } else if (metric.isNotEmpty()) {
                // Cached `metric` strings bake in "@ N lbs". Peel the load
                // suffix off so the re-append below is the single source of
                // truth and renders in the user's display unit.
                val split = splitWeightSuffix(metric)
                base = split.first
                cachedLoadLbs = split.second
            } else if (sets > 0 && reps > 0) {
                base = "$sets x $reps"
            } else if (reps > 0) {
                base = "$reps reps"
            } else {
                base = ""
            }
            if (base.isEmpty()) return base
            val loadLbs: Double? = weightKg?.takeIf { it > 0 }?.toDouble() ?: cachedLoadLbs
            return if (loadLbs != null) "$base @ ${WeightUnit.displayString(fromLbs = loadLbs)}" else base
        }

    /** Summary for a varied-set prescription ("20 / 15 / 10 reps", "3 x 30s · 12 reps", "4 sets · varied"). */
    private val variedMetricSummary: String
        get() {
            val configs = setConfigs.sortedBy { it.setNumber }
            val count = maxOf(sets, configs.size)
            if (!usesDuration && configs.size == count && configs.size <= 5 && configs.all { it.reps > 0 }) {
                return configs.joinToString(" / ") { it.reps.toString() } + " reps"
            }
            val window = configs.firstOrNull()?.durationSeconds
            if (usesDuration && configs.size == count && configs.size <= 5 &&
                window != null && window > 0 &&
                configs.all { (it.durationSeconds ?: 0) == window } &&
                configs.all { it.reps > 0 }
            ) {
                val dur = humanizeDuration(window)
                val repValues = configs.map { it.reps }
                if (repValues.toSet().size == 1) return "${configs.size} x $dur · ${repValues[0]} reps"
                return "$dur · " + repValues.joinToString(" / ") + " reps"
            }
            return "$count sets · varied"
        }

    /** Task-level target load ("75 lbs" / "34 kg"), or null when there is none. */
    val weightSummary: String?
        get() = weightKg?.takeIf { it > 0 }?.let { WeightUnit.displayString(fromLbs = it.toDouble()) }

    /** True when the task carries individually-edited sets to break out. */
    val hasPerSetBreakdown: Boolean
        get() = !sameAcrossSets && setConfigs.any { (it.weightKg ?: 0.0) > 0 || it.reps > 0 || (it.durationSeconds ?: 0) > 0 }

    companion object {
        private val weightSuffix = Regex("""\s*@\s*[0-9]+(?:\.[0-9]+)?\s*lbs?$""", RegexOption.IGNORE_CASE)

        /**
         * Split a trailing "@ N lbs" load off a cached metric string —
         * ("3 x 12 @ 75 lbs") → ("3 x 12", 75.0). A zero load strips to null.
         */
        fun splitWeightSuffix(s: String): Pair<String, Double?> {
            val match = weightSuffix.find(s) ?: return s to null
            val numeric = match.value
                .replace("@", "")
                .replace(Regex("lbs?", RegexOption.IGNORE_CASE), "")
                .trim()
            val value = numeric.toDoubleOrNull() ?: 0.0
            return s.substring(0, match.range.first) to (if (value > 0) value else null)
        }

        /** Under 60s → "30s"; 60s+ → "M:SS min" (600 → "10:00 min"). */
        fun humanizeDuration(seconds: Int): String {
            if (seconds <= 0) return ""
            if (seconds < 60) return "${seconds}s"
            return String.format(Locale.US, "%d:%02d min", seconds / 60, seconds % 60)
        }
    }

    /**
     * iOS `init(from:)` back-compat: the server only knows `intervalSeconds`;
     * infer `usesDuration` / `durationSeconds` from it when the payload
     * doesn't carry our own keys.
     */
    class Adapter : TolerantAdapterFactory<WorkoutTask>(WorkoutTask::class.java) {
        override fun preprocess(raw: JsonObject) {
            val intervalSeconds = raw.optInt("intervalSeconds")
            if (!raw.has("usesDuration")) raw.addProperty("usesDuration", intervalSeconds != null)
            if (!raw.has("durationSeconds") && intervalSeconds != null) raw.addProperty("durationSeconds", intervalSeconds)
        }
    }
}
