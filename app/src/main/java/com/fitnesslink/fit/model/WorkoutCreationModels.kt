package com.fitnesslink.fit.model

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.util.Date
import java.util.Locale
import java.util.UUID

// MARK: - Legacy local-only filter enums
//
// The exercise browser's free-text muscle / equipment chips. Server content
// is tagged with `anatomyIds` / `equipmentIds` (resolved through the
// classification cache in wave 0.10); these enums only survive so the
// current browser / create-exercise screens keep compiling until 1.9.

@Deprecated("Movements are tagged with anatomyIds; resolve names via the classification cache.")
enum class MuscleGroup(val label: String) {
    Chest("Chest"), Back("Back"), Shoulders("Shoulders"),
    Legs("Legs"), Arms("Arms"), Core("Core")
}

@Deprecated("Movements are tagged with equipmentIds; resolve names via the classification cache.")
enum class EquipmentType(val label: String) {
    Barbell("Barbell"), Dumbbell("Dumbbell"), Cable("Cable"),
    Bodyweight("Bodyweight"), Machine("Machine"), Band("Band")
}

// MARK: - Exercise Browser Tab

enum class BrowserTab(val label: String) {
    All("All"),
    /** Movements the current user authored — `contributorId == null` and not shared in. */
    Mine("Mine"),
    Favorites("Favorites"),
    Recent("Recent")
}

// MARK: - Per-Set Configuration

@JsonAdapter(TaskSetConfig.Adapter::class)
data class TaskSetConfig(
    val id: String = UUID.randomUUID().toString(),
    val setNumber: Int = 0,
    val reps: Int = 10,
    /** Server sends whole-lbs integers; decoding into Double is fine. Holds lbs. */
    val weightKg: Double? = null,
    val isDropSet: Boolean = false,
    val isWarmup: Boolean = false,
    val durationSeconds: Int? = null,
    /** Logged distance for cardio / distance work, in [distanceUnit] ("km" | "mi"). Session logs only. */
    val distance: Double? = null,
    val distanceUnit: String? = null
) {
    /** The logged distance with its unit, when both are present. */
    val distanceValue: DistanceValue?
        get() {
            val d = distance ?: return null
            if (d <= 0) return null
            val unit = DistanceUnit.fromWire(distanceUnit) ?: return null
            return DistanceValue(d, unit)
        }

    class Adapter : TolerantAdapterFactory<TaskSetConfig>(TaskSetConfig::class.java)
}

// MARK: - Set Preset

data class SetPreset(
    val label: String,
    val sets: Int,
    val reps: Int
) {
    val id: String get() = label

    companion object {
        val presets = listOf(
            SetPreset("3x10", 3, 10),
            SetPreset("4x8", 4, 8),
            SetPreset("5x5", 5, 5),
            SetPreset("3x12", 3, 12)
        )
    }
}

// MARK: - Grouping Type

enum class ExerciseGroupType(val label: String) {
    Superset("Superset"), Circuit("Circuit"), Interval("Interval")
}

// MARK: - Interval Training

/**
 * Named interval-training protocols. Most differ only in parameter values;
 * EMOM and AMRAP have distinct timer semantics the runner branches on.
 * Wire / cache raw values are lowercase (iOS `rawValue`).
 */
enum class IntervalMode(val rawValue: String) {
    /** 20s work / 10s rest × 8 rounds (4:00 total). */
    @SerializedName("tabata") TABATA("tabata"),
    /** User-defined work / rest / rounds with fixed-duration rest. */
    @SerializedName("hiit") HIIT("hiit"),
    /** Every-minute-on-the-minute: rest is the remainder of the minute; `window / 60` rounds. */
    @SerializedName("emom") EMOM("emom"),
    /** As many rounds as possible in a fixed window. */
    @SerializedName("amrap") AMRAP("amrap");

    /** "Tabata" / "HIIT" / "EMOM" / "AMRAP" — single source for every badge and header. */
    val displayName: String
        get() = when (this) {
            TABATA -> "Tabata"
            HIIT -> "HIIT"
            EMOM -> "EMOM"
            AMRAP -> "AMRAP"
        }

    companion object {
        fun fromRaw(raw: String?): IntervalMode? =
            raw?.let { r -> entries.firstOrNull { it.rawValue.equals(r, ignoreCase = true) || it.name.equals(r, ignoreCase = true) } }
    }
}

/** Runtime phase of an interval block. String-backed so a runner snapshot can encode it. */
enum class IntervalPhase(val rawValue: String) {
    @SerializedName("prepare") PREPARE("prepare"),          // 3-2-1 countdown before round 1
    @SerializedName("work") WORK("work"),
    @SerializedName("rest") REST("rest"),
    @SerializedName("betweenSetsRest") BETWEEN_SETS_REST("betweenSetsRest"),
    @SerializedName("done") DONE("done")
}

/**
 * Fully-resolved timing for one interval block — every optional on the lead
 * task collapsed to the value the runner will use. Build with
 * [IntervalMath.plan]; never read the raw task fields for timing elsewhere.
 */
data class IntervalPlan(
    val mode: IntervalMode,
    val workSeconds: Int,
    val restSeconds: Int,
    /** Tabata / HIIT: stored rounds; EMOM: `window / 60`; AMRAP: 0 (counted at runtime). */
    val roundsPerSet: Int,
    val sets: Int,
    /** Only applied when `sets > 1`. */
    val betweenSetsRestSeconds: Int,
    val windowSeconds: Int
)

/**
 * Pure timing math for interval blocks. Semantics (2026-09 HIIT PRD):
 * - No rest after the final round of a set: `setSeconds = rounds×work + (rounds−1)×rest`.
 * - EMOM and AMRAP sets are exactly the window.
 * - `blockSeconds = sets×setSeconds + (sets−1)×betweenSetsRest`.
 * - The prepare countdown is a lead-in, excluded from `blockSeconds`.
 */
object IntervalMath {
    /** Rest between sets when the lead task has none stored. */
    const val defaultSetRestSeconds = 60
    /** Lead-in countdown before round 1 of a block. */
    const val defaultPrepareSeconds = 3

    /** EMOM / AMRAP members carry a per-round rep target; Tabata / HIIT are pure timed work. */
    fun usesRepTargets(mode: IntervalMode?): Boolean = when (mode) {
        IntervalMode.EMOM, IntervalMode.AMRAP -> true
        IntervalMode.TABATA, IntervalMode.HIIT, null -> false
    }

    /**
     * Resolve a lead task (+ its row's `totalRounds`) into a plan. [mode]
     * overrides the task's stored mode when the row carries it. Defaults for
     * missing work / rest / window match [IntervalPreset].
     */
    fun plan(lead: WorkoutTask, rowTotalRounds: Int = 0, mode: IntervalMode? = null): IntervalPlan {
        val resolvedMode = mode ?: lead.intervalMode ?: IntervalMode.HIIT
        val sets = maxOf(1, lead.intervalSets ?: 1)
        val setRest = maxOf(0, lead.effectiveIntervalSetRestSeconds ?: defaultSetRestSeconds)
        val storedRounds = positive(lead.rounds) ?: positive(lead.totalRounds) ?: positive(rowTotalRounds)

        return when (resolvedMode) {
            IntervalMode.TABATA, IntervalMode.HIIT -> {
                val preset = if (resolvedMode == IntervalMode.TABATA) IntervalPreset.tabata else IntervalPreset.hiitDefault
                val work = maxOf(0, lead.intervalWorkSeconds ?: preset.workSeconds)
                val rest = maxOf(0, lead.intervalRestSeconds ?: preset.restSeconds)
                val rounds = maxOf(1, storedRounds ?: preset.rounds)
                IntervalPlan(
                    mode = resolvedMode, workSeconds = work, restSeconds = rest,
                    roundsPerSet = rounds, sets = sets, betweenSetsRestSeconds = setRest,
                    windowSeconds = rounds * work + maxOf(0, rounds - 1) * rest
                )
            }
            IntervalMode.EMOM -> {
                val window = maxOf(60, lead.intervalWindowSeconds ?: IntervalPreset.emomDefault.totalSeconds)
                val work = minOf(60, maxOf(0, lead.intervalWorkSeconds ?: IntervalPreset.emomDefault.workSeconds))
                IntervalPlan(
                    mode = IntervalMode.EMOM, workSeconds = work, restSeconds = maxOf(0, 60 - work),
                    roundsPerSet = maxOf(1, window / 60), sets = sets, betweenSetsRestSeconds = setRest,
                    windowSeconds = window
                )
            }
            IntervalMode.AMRAP -> {
                val window = maxOf(0, lead.intervalWindowSeconds ?: IntervalPreset.amrapDefault.totalSeconds)
                IntervalPlan(
                    mode = IntervalMode.AMRAP, workSeconds = window, restSeconds = 0,
                    roundsPerSet = 0, sets = sets, betweenSetsRestSeconds = setRest,
                    windowSeconds = window
                )
            }
        }
    }

    /** Seconds for one pass through the rounds. */
    fun setSeconds(plan: IntervalPlan): Int = when (plan.mode) {
        IntervalMode.TABATA, IntervalMode.HIIT -> {
            val rounds = maxOf(0, plan.roundsPerSet)
            rounds * plan.workSeconds + maxOf(0, rounds - 1) * plan.restSeconds
        }
        IntervalMode.EMOM, IntervalMode.AMRAP -> plan.windowSeconds
    }

    /** True total for the block — every set plus the between-sets rests (n − 1 gaps). Prepare excluded. */
    fun blockSeconds(plan: IntervalPlan): Int {
        val sets = maxOf(1, plan.sets)
        return sets * setSeconds(plan) + (sets - 1) * plan.betweenSetsRestSeconds
    }

    /**
     * Seconds left in the block from a runner position. [set] / [round] are
     * 1-based. In PREPARE the whole block is still ahead; DONE is 0.
     */
    fun remaining(plan: IntervalPlan, set: Int, round: Int, phase: IntervalPhase, phaseRemaining: Int): Int {
        val sets = maxOf(1, plan.sets)
        val currentSet = minOf(maxOf(1, set), sets)
        val setsAfter = sets - currentSet
        val left = maxOf(0, phaseRemaining)
        return when (phase) {
            IntervalPhase.PREPARE -> blockSeconds(plan)
            IntervalPhase.DONE -> 0
            IntervalPhase.BETWEEN_SETS_REST ->
                left + setsAfter * setSeconds(plan) + maxOf(0, setsAfter - 1) * plan.betweenSetsRestSeconds
            IntervalPhase.WORK, IntervalPhase.REST -> {
                val tail = restOfSetSeconds(plan, round, phase)
                left + tail + setsAfter * (plan.betweenSetsRestSeconds + setSeconds(plan))
            }
        }
    }

    /** "m:ss" (1275 → "21:15"). Zero and negatives render "0:00". */
    fun formatTime(seconds: Int): String {
        if (seconds <= 0) return "0:00"
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }

    // MARK: - Private

    /** Seconds still to run in the current set AFTER the current phase. */
    private fun restOfSetSeconds(plan: IntervalPlan, round: Int, phase: IntervalPhase): Int {
        return when (plan.mode) {
            IntervalMode.AMRAP -> 0
            IntervalMode.TABATA, IntervalMode.HIIT, IntervalMode.EMOM -> {
                val rounds = maxOf(1, plan.roundsPerSet)
                val r = minOf(maxOf(1, round), rounds)
                var total = 0
                if (phase == IntervalPhase.WORK) total += restAfter(r, rounds, plan)
                if (r < rounds) {
                    for (k in (r + 1)..rounds) total += plan.workSeconds + restAfter(k, rounds, plan)
                }
                total
            }
        }
    }

    /** Rest that follows round [k]. EMOM always fills the minute; Tabata / HIIT skip the rest after the final round. */
    private fun restAfter(k: Int, rounds: Int, plan: IntervalPlan): Int {
        if (plan.mode == IntervalMode.EMOM) return plan.restSeconds
        return if (k < rounds) plan.restSeconds else 0
    }

    private fun positive(value: Int?): Int? = value?.takeIf { it > 0 }
}

// MARK: - Interval metrics (display strings for cards, sheets, tiles)

/**
 * Everything a card, header or tile row needs to describe an interval
 * block, computed once from the lead task's [IntervalPlan].
 */
data class IntervalMetrics(
    val plan: IntervalPlan,
    /** Members of the block (lead + siblings) that are movements. */
    val exerciseCount: Int = 1,
    /** Active-recovery movement name when the lead uses active recovery; null for passive rest. */
    val recoveryMovementName: String? = null
) {
    data class Tile(val label: String, val value: String) {
        val id: String get() = label
    }

    val mode: IntervalMode get() = plan.mode
    val totalSeconds: Int get() = IntervalMath.blockSeconds(plan)
    val setSeconds: Int get() = IntervalMath.setSeconds(plan)

    /** "INTERVAL · HIIT" */
    val badgeText: String get() = "INTERVAL · ${mode.displayName.uppercase(Locale.US)}"
    /** "21:15" */
    val totalText: String get() = IntervalMath.formatTime(totalSeconds)
    /** "1:00" between-sets rest, formatted. */
    val betweenSetsText: String get() = IntervalMath.formatTime(plan.betweenSetsRestSeconds)

    /** Dense one-liner: "6 × 45s/15s · 5:45"; "3 × (6 × 45s/15s) + 2:00 rest · 21:15"; "10 min EMOM · 40s work · 10:00"; "10:00 AMRAP". */
    val compactLine: String
        get() {
            val total = totalText
            val sets = plan.sets
            return when (mode) {
                IntervalMode.TABATA, IntervalMode.HIIT -> {
                    val core = "${plan.roundsPerSet} × ${plan.workSeconds}s/${plan.restSeconds}s"
                    if (sets > 1) "$sets × ($core) + $betweenSetsText rest · $total" else "$core · $total"
                }
                IntervalMode.EMOM -> {
                    val setsPrefix = if (sets > 1) "$sets × " else ""
                    "$setsPrefix${plan.roundsPerSet} min EMOM · ${plan.workSeconds}s work · $total"
                }
                IntervalMode.AMRAP -> {
                    val setsPrefix = if (sets > 1) "$sets × " else ""
                    "$setsPrefix${IntervalMath.formatTime(plan.windowSeconds)} AMRAP" + (if (sets > 1) " · $total" else "")
                }
            }
        }

    /** Stacked plain-language summary, inner → outer, ending with the true total. */
    val summaryLines: List<String>
        get() {
            val lines = mutableListOf<String>()
            val sets = plan.sets
            when (mode) {
                IntervalMode.TABATA, IntervalMode.HIIT -> {
                    lines.add(if (sets > 1) "$sets Sets · ${plan.roundsPerSet} Rounds Each" else "${plan.roundsPerSet} Rounds")
                    lines.add("${plan.workSeconds}s Work · ${plan.restSeconds}s Rest")
                }
                IntervalMode.EMOM -> {
                    lines.add(if (sets > 1) "$sets Sets · ${plan.roundsPerSet} Minutes Each" else "${plan.roundsPerSet} Minutes")
                    lines.add("${plan.workSeconds}s Work Every Minute")
                }
                IntervalMode.AMRAP -> {
                    lines.add("As Many Rounds As Possible")
                    lines.add("${IntervalMath.formatTime(plan.windowSeconds)} Window")
                }
            }
            if (sets > 1) lines.add("$betweenSetsText Rest Between Sets")
            lines.add("Total Time: $totalText")
            return lines
        }

    /** Stat tiles for the metrics grid. */
    val tiles: List<Tile>
        get() {
            val out = mutableListOf<Tile>()
            val sets = plan.sets
            when (mode) {
                IntervalMode.TABATA, IntervalMode.HIIT -> {
                    out.add(Tile("Work", "${plan.workSeconds}s"))
                    out.add(Tile("Rest", "${plan.restSeconds}s"))
                    out.add(Tile("Rounds", "${plan.roundsPerSet}"))
                    out.add(Tile("Sets", "$sets"))
                }
                IntervalMode.EMOM -> {
                    out.add(Tile("Minutes", "${plan.roundsPerSet}"))
                    out.add(Tile("Work / min", "${plan.workSeconds}s"))
                    out.add(Tile("Rest / min", "${plan.restSeconds}s"))
                    out.add(Tile("Sets", "$sets"))
                }
                IntervalMode.AMRAP -> {
                    out.add(Tile("Window", IntervalMath.formatTime(plan.windowSeconds)))
                    if (sets > 1) out.add(Tile("Sets", "$sets"))
                }
            }
            if (sets > 1) out.add(Tile("Rest Between Sets", betweenSetsText))
            out.add(Tile("Total", totalText))
            return out
        }

    companion object {
        /** Build from an editor / detail row. Null when the row isn't an interval. */
        fun from(row: TaskRow): IntervalMetrics? {
            val lead = row.task ?: return null
            val mode = row.intervalMode ?: lead.intervalMode ?: return null
            val members = listOf(lead) + row.advancedTasks
            val recovery = if (lead.restType == RestType.ACTIVE_RECOVERY && lead.restMovementId != null) {
                lead.restMovementName?.takeIf { it.isNotEmpty() } ?: "Recovery"
            } else null
            return IntervalMetrics(
                plan = IntervalMath.plan(lead, rowTotalRounds = row.totalRounds, mode = mode),
                exerciseCount = maxOf(1, members.count { it.isMovement || it.movementId?.isNotEmpty() == true }),
                recoveryMovementName = recovery
            )
        }
    }
}

// MARK: - Workout duration estimate

/**
 * Planned seconds for a run of tasks — interval blocks via
 * [IntervalMath.blockSeconds] (+ prepare), superset / circuit rounds, loose
 * sets at 40 s of work per rep-based set plus the programmed rests.
 */
object WorkoutDurationEstimator {
    /** Default work allowance for a rep-based set / round member with no explicit duration. */
    const val defaultSetSeconds = 40

    /**
     * Estimate over a flat, ordered task list (the session's shape). Groups
     * are consecutive tasks sharing a non-empty `groupId`, first member =
     * lead. A start index inside a group backs up to the lead so the block
     * is counted once.
     */
    fun plannedSeconds(tasks: List<WorkoutTask>, from: Int = 0): Int {
        if (tasks.isEmpty()) return 0
        var i = maxOf(0, minOf(from, tasks.size))
        if (i < tasks.size && tasks[i].groupId.isNotEmpty()) {
            val gid = tasks[i].groupId
            while (i > 0 && tasks[i - 1].groupId == gid) i -= 1
        }
        var seconds = 0
        while (i < tasks.size) {
            val lead = tasks[i]
            val gid = lead.groupId
            var end = i + 1
            if (gid.isNotEmpty()) {
                while (end < tasks.size && tasks[end].groupId == gid) end += 1
            }
            seconds += groupSeconds(lead, tasks.subList(i, end))
            i = end
        }
        return seconds
    }

    /**
     * Estimate over editor / detail rows. Row-level group flags are stamped
     * onto the lead before delegating so a row whose task flags were dropped
     * by sync still counts as its group type.
     */
    fun plannedSeconds(rows: List<TaskRow>): Int {
        val flat = mutableListOf<WorkoutTask>()
        for (row in rows) {
            var lead = row.task
            if (lead == null) {
                flat.addAll(row.advancedTasks)
                continue
            }
            val isGroup = row.advancedTasks.isNotEmpty() || row.isInterval || row.isSuperset || row.isCircuit
            if (isGroup) {
                if (lead.groupId.isEmpty()) lead = lead.copy(groupId = "row-${lead.id}")
                lead = lead.copy(
                    isInterval = lead.isInterval || row.isInterval,
                    isSuperset = lead.isSuperset || row.isSuperset,
                    isCircuit = lead.isCircuit || row.isCircuit,
                    intervalMode = lead.intervalMode ?: row.intervalMode
                )
                if ((lead.rounds ?: 0) <= 0 && row.totalRounds > 0) lead = lead.copy(rounds = row.totalRounds)
                if (lead.totalRounds <= 0) lead = lead.copy(totalRounds = row.totalRounds)
            }
            flat.add(lead)
            for (member in row.advancedTasks) {
                flat.add(if (isGroup) member.copy(groupId = lead.groupId) else member)
            }
        }
        return plannedSeconds(tasks = flat)
    }

    /** Seconds for one group (or one loose task when `members.size == 1` and it isn't an interval). */
    private fun groupSeconds(lead: WorkoutTask, members: List<WorkoutTask>): Int {
        val movementMembers = members.filter { it.isMovement }
        val isInterval = lead.isInterval || lead.intervalMode != null

        if (isInterval) {
            val plan = IntervalMath.plan(lead, rowTotalRounds = lead.totalRounds)
            return IntervalMath.blockSeconds(plan) + IntervalMath.defaultPrepareSeconds
        }

        if (movementMembers.size > 1) {
            // Superset / circuit: rounds × (member work + member rests), plus the round rest between rounds.
            val rounds = maxOf(lead.rounds ?: lead.totalRounds, 1)
            var roundSeconds = 0
            for (m in movementMembers) {
                roundSeconds += if (m.usesDuration) (m.durationSeconds ?: defaultSetSeconds) else defaultSetSeconds
                roundSeconds += m.restSeconds
            }
            var total = roundSeconds * rounds
            val rr = lead.roundRestSeconds
            if (rr != null && rr > 0) total += rr * maxOf(rounds - 1, 0)
            return total
        }

        // Loose tasks (a non-interval "group" of one falls through here too).
        var total = 0
        for (t in members) {
            if (t.isRest) {
                total += t.restSeconds
            } else if (t.isMovement) {
                val sets = maxOf(t.sets, 1)
                if (t.usesDuration) {
                    val configured = t.setConfigs.mapNotNull { it.durationSeconds }.sum()
                    total += if (configured > 0) configured else (t.durationSeconds ?: defaultSetSeconds) * sets
                } else {
                    total += defaultSetSeconds * sets
                }
                total += t.restSeconds * maxOf(sets - 1, 0)
            }
        }
        return total
    }
}

data class IntervalPreset(
    val mode: IntervalMode,
    val label: String,
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    /** Total block duration. For AMRAP the window the user works against. */
    val totalSeconds: Int
) {
    companion object {
        val tabata = IntervalPreset(IntervalMode.TABATA, "Tabata", 20, 10, 8, 240)
        val hiitDefault = IntervalPreset(IntervalMode.HIIT, "HIIT", 45, 15, 6, 360)
        val emomDefault = IntervalPreset(IntervalMode.EMOM, "EMOM", 40, 20, 10, 600)
        val amrapDefault = IntervalPreset(IntervalMode.AMRAP, "AMRAP", 0, 0, 0, 600)
        val all = listOf(tabata, hiitDefault, emomDefault, amrapDefault)
    }
}

// MARK: - Explicit tag selection (workout / program editors)

/**
 * Editor state for one explicit tag set (equipment or anatomy) implementing
 * the override-with-fallback contract: untouched → `null` (key omitted);
 * edited → full id list; reset / deselected everything → `[]`.
 */
class ContentTagSelection(effectiveIds: List<String> = emptyList(), isExplicit: Boolean = false, isLoaded: Boolean = false) {
    /** Effective ids as last confirmed by the server, lowercased. */
    var originalIds: Set<String> = effectiveIds.map { it.lowercase(Locale.US) }.toSet()
        private set
    /** Server `hasExplicit*` — false means [originalIds] are derived. */
    var isExplicit: Boolean = isExplicit
        private set
    /** Ids currently checked in the picker, lowercased. */
    var selectedIds: Set<String> = originalIds
        private set
    /** True once the user has changed the selection or tapped reset. */
    var isTouched: Boolean = false
        private set
    /** True once the effective ids have been seeded from a server read. */
    var isLoaded: Boolean = isLoaded
        private set

    /** Seed from a server detail read. No-op once the user has started editing. */
    fun seed(effectiveIds: List<String>, isExplicit: Boolean) {
        if (isTouched) return
        originalIds = effectiveIds.map { it.lowercase(Locale.US) }.toSet()
        selectedIds = originalIds
        this.isExplicit = isExplicit
        isLoaded = true
        isTouched = false
    }

    /** Picker write-back. Only flips [isTouched] on an actual change. */
    fun select(ids: Set<String>) {
        val normalized = ids.map { it.lowercase(Locale.US) }.toSet()
        if (normalized == selectedIds) return
        selectedIds = normalized
        isTouched = true
    }

    /** "Reset to movement defaults" — saves as `[]`. */
    fun resetToDerived() {
        selectedIds = emptySet()
        isTouched = true
    }

    /** Value to put on the wire for this save. */
    val wireValue: List<String>?
        get() = if (isTouched && selectedIds != originalIds) selectedIds.sorted() else null

    val isPendingReset: Boolean get() = isTouched && selectedIds.isEmpty() && originalIds.isNotEmpty()
    val isShowingDerived: Boolean get() = isLoaded && !isExplicit && !isTouched

    /** Call after the server accepted a save so a second save sends `null` again. */
    fun markSaved() {
        if (!isTouched) return
        originalIds = selectedIds
        isExplicit = selectedIds.isNotEmpty()
        isTouched = false
    }
}

// MARK: - Movement Library Entry (for browser display)

data class MovementLibraryItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    /** Many-to-many anatomy tag ids (lowercased). Resolve names via the classification cache. */
    val anatomyIds: List<String> = emptyList(),
    /** Many-to-many equipment tag ids (lowercased). */
    val equipmentIds: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val imageId: String? = null,
    val thumbnailId: String? = null,
    val videoId: String? = null,
    /** Optional 9:16 phone variant id; prefer via [effectiveVideoId]. */
    val phoneVideoId: String? = null,
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    val videoUrl: String? = null,
    val phoneVideoUrl: String? = null,
    /** Null when the user authored it; non-null for system / partner templates. */
    val contributorId: String? = null,
    /** Set after refresh when shared with the current user. Live-only. */
    val sharedByUserId: String? = null,
    /** Server-side creation date (`audit.timestamp`), persisted locally. */
    val createdAt: Date? = null,
    /** Server-order rank from the last list-view snapshot. */
    val sortIndex: Int? = null
) {
    /** FULL-SCREEN preference: phone variant when attached, else landscape (presence check only). */
    val effectiveVideoId: String? get() = phoneVideoId ?: videoId
    val effectiveVideoUrl: String? get() = if (phoneVideoId != null) (phoneVideoUrl ?: videoUrl) else videoUrl

    /** INLINE preference: landscape when attached, else the phone variant. */
    val inlineVideoId: String? get() = videoId ?: phoneVideoId
    val inlineVideoUrl: String? get() = if (videoId != null) videoUrl else phoneVideoUrl

    val isSharedWithMe: Boolean get() = sharedByUserId != null

    /** Legacy free-text tag the pre-parity browser rendered. Always empty for server rows. */
    @Deprecated("Use anatomyIds resolved via the classification cache.")
    val muscleGroup: String get() = ""

    /** Legacy free-text tag the pre-parity browser rendered. Always empty for server rows. */
    @Deprecated("Use equipmentIds resolved via the classification cache.")
    val equipment: String get() = ""
}
