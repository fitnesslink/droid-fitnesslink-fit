package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.EmomMath
import com.fitnesslink.fit.model.EmomPlan
import com.fitnesslink.fit.model.IntervalPhase
import com.fitnesslink.fit.model.WorkoutTask
import com.google.gson.annotations.SerializedName

// MARK: - EMOM Interactive Mode (EMOM PRD v2.0 §7, §8) — pure, no Android.

/** How one EMOM round ended (§7.2). */
enum class EmomRoundOutcome(val rawValue: String) {
    /** Reps-based round: the user tapped Round Done. */
    @SerializedName("confirmed") CONFIRMED("confirmed"),
    /** Time-based round: the WORK timer ran out. */
    @SerializedName("autoCompleted") AUTO_COMPLETED("autoCompleted"),
    /** Reps-based round: the interval expired in WORK without Round Done. */
    @SerializedName("unconfirmed") UNCONFIRMED("unconfirmed"),
    /** Next / Skip moved past the round before it was confirmed or auto-completed. */
    @SerializedName("skipped") SKIPPED("skipped");

    val isCompleted: Boolean get() = this == CONFIRMED || this == AUTO_COMPLETED
}

/** One recorded round. [completionSeconds] = elapsed work for confirmed / auto rounds. */
data class EmomRoundRecord(val round: Int, val outcome: EmomRoundOutcome, val completionSeconds: Int? = null)

/** One session-progress write produced when an EMOM block is left. */
data class EmomProgressWrite(
    val taskId: String,
    /** The member's cycle number for the round. */
    val iteration: Int,
    val state: SetState,
    val durationSeconds: Int? = null,
    /** "manual" (Round Done) / "auto" (timed work) for completed rounds. */
    val completionMethod: String? = null
)

/** Maps runner outcomes onto session progress (§7.2). */
object EmomOutcomeWriter {
    const val methodManual = "manual"
    const val methodAuto = "auto"

    /**
     * One write per round 1…totalRounds: confirmed / auto → completed (duration = completion
     * seconds, method manual / auto); unconfirmed → incomplete; skipped or never reached → skipped.
     * [memberTaskIds] are the block's members in round order (lead first).
     */
    fun writes(plan: EmomPlan, memberTaskIds: List<String>, outcomes: Map<Int, EmomRoundRecord>): List<EmomProgressWrite> {
        if (memberTaskIds.isEmpty()) return emptyList()
        val out = mutableListOf<EmomProgressWrite>()
        for (round in 1..plan.totalRounds) {
            val taskId = memberTaskIds.getOrNull(plan.memberIndex(round)) ?: continue
            val iteration = plan.cycle(round)
            val record = outcomes[round]
            out.add(
                when (record?.outcome) {
                    EmomRoundOutcome.CONFIRMED -> EmomProgressWrite(taskId, iteration, SetState.COMPLETED, record.completionSeconds, methodManual)
                    EmomRoundOutcome.AUTO_COMPLETED -> EmomProgressWrite(taskId, iteration, SetState.COMPLETED, record.completionSeconds, methodAuto)
                    EmomRoundOutcome.UNCONFIRMED -> EmomProgressWrite(taskId, iteration, SetState.INCOMPLETE)
                    EmomRoundOutcome.SKIPPED, null -> EmomProgressWrite(taskId, iteration, SetState.SKIPPED)
                }
            )
        }
        return out
    }
}

// MARK: - Voice coach (§8)

/** Priority levels (§8): low number wins. */
object EmomCuePriority {
    const val transition = 1
    const val roundStart = 2
    const val countdown = 3
    const val completion = 4
    const val motivation = 5
}

/** Whether a new coach cue plays against one in flight (§8): lower priority is dropped, equal-or-higher interrupts. */
object CoachSpeechArbiter {
    enum class Decision { SPEAK, INTERRUPT, DROP }

    fun decide(newLevel: Int, inFlightLevel: Int?): Decision = when {
        inFlightLevel == null -> Decision.SPEAK
        newLevel > inFlightLevel -> Decision.DROP
        else -> Decision.INTERRUPT
    }
}

/** What the coach needs to know about the runner at the moment an event fires. */
data class EmomCueContext(
    val phase: IntervalPhase,
    val round: Int,
    val totalRounds: Int,
    val exerciseCount: Int,
    val exerciseName: String,
    /** "20 reps" / "30 seconds"; null when no target is set. */
    val spokenTarget: String?,
    val isTimeBased: Boolean,
    /** Full length of the current phase. */
    val phaseDuration: Int,
    val secondsRemaining: Int,
    /** Outcome recorded for [round] (set when Round Done / timed work led into REST). */
    val outcome: EmomRoundOutcome?
) {
    val cycle: Int get() = (maxOf(1, round) - 1) / maxOf(1, exerciseCount) + 1

    companion object {
        fun from(runner: IntervalRunner): EmomCueContext {
            val exercise: WorkoutTask? = runner.currentExercise
            return EmomCueContext(
                phase = runner.phase,
                round = runner.currentRound,
                totalRounds = runner.roundsTotal,
                exerciseCount = runner.emomPlan?.exerciseCount ?: 1,
                exerciseName = exercise?.name ?: "",
                spokenTarget = exercise?.let { EmomMath.spokenTarget(it) },
                isTimeBased = exercise?.let { EmomMath.isTimeBased(it) } ?: false,
                phaseDuration = runner.phaseDuration,
                secondsRemaining = runner.secondsRemaining,
                outcome = runner.emomOutcome(runner.currentRound)?.outcome
            )
        }
    }
}

/** One thing the EMOM coach says: [lines] spoken back to back as one announcement at [level]. */
data class EmomCue(val lines: List<String>, val level: Int) {
    constructor(line: String, level: Int) : this(listOf(line), level)
}

/**
 * EMOM voice coach (§8): decides WHAT to say for each runner event. Stateful only for the phrase
 * rotations (which cycle in order and never repeat the previous pick) and for knowing a block just
 * left its lead-in. Speech arbitration lives in [CoachSpeechArbiter].
 */
class EmomCoach {
    private var starterIndex = 0
    private var roundDoneLongIndex = 0
    private var roundDoneShortIndex = 0
    private var timeLongIndex = 0
    private var lastPhase: IntervalPhase? = null

    /** A new block is starting (fresh runner): the first round-1 start says "Let's work!". */
    fun beginBlock() { lastPhase = null }

    fun cues(event: IntervalRunner.Event, ctx: EmomCueContext): List<EmomCue> {
        val out = mutableListOf<EmomCue>()
        when (event) {
            is IntervalRunner.Event.Tick -> tickCues(event.remaining, ctx)?.let(out::add)
            // A lead-in of 5 s or less starts on its first number ("5" / "3"), which no Tick announces.
            IntervalRunner.Event.PrepareStarted -> if (ctx.secondsRemaining in 1..5) out.add(EmomCue("${ctx.secondsRemaining}", EmomCuePriority.countdown))
            IntervalRunner.Event.TransitionToWork -> {
                val line = roundStartLine(ctx)
                val fromLeadIn = ctx.round == 1 && (lastPhase == null || lastPhase == IntervalPhase.PREPARE)
                // "Let's work!" ends the lead-in countdown and carries round 1's line in the same
                // announcement, so the round-start can't be dropped behind it (iOS does the same).
                out.add(EmomCue(if (fromLeadIn) listOf(letsWork, line) else listOf(line), EmomCuePriority.roundStart))
            }
            IntervalRunner.Event.TransitionToRest -> completionCue(ctx)?.let(out::add)
            IntervalRunner.Event.BlockComplete -> out.add(EmomCue(blockComplete, EmomCuePriority.transition))
            else -> Unit
        }
        lastPhase = ctx.phase
        return out
    }

    private fun tickCues(remaining: Int, ctx: EmomCueContext): EmomCue? {
        when (ctx.phase) {
            IntervalPhase.PREPARE -> if (remaining in 1..5) return EmomCue("$remaining", EmomCuePriority.countdown)
            IntervalPhase.WORK -> {
                if (remaining == 10 && ctx.phaseDuration >= warningMinimumPhaseSeconds) return EmomCue(tenSeconds, EmomCuePriority.countdown)
                if (remaining in 1..5) return EmomCue("$remaining", EmomCuePriority.countdown)
            }
            else -> Unit
        }
        return null
    }

    /** "Cycle 2. Round 3. Push-ups. 20 reps. Let's go!" / "Last round! Give it all you've got! Push-ups. 20 reps!" */
    fun roundStartLine(ctx: EmomCueContext): String {
        val name = ctx.exerciseName.trim()
        val target = ctx.spokenTarget?.trim()?.takeIf { it.isNotEmpty() }
        if (ctx.totalRounds > 1 && ctx.round >= ctx.totalRounds) {
            val tail = listOfNotNull(name.ifEmpty { null }, target).joinToString(". ")
            return if (tail.isEmpty()) "Last round! Give it all you've got!" else "Last round! Give it all you've got! $tail!"
        }
        val prefix = if (ctx.exerciseCount >= 2 && ctx.round > 1 && (ctx.round - 1) % ctx.exerciseCount == 0) "Cycle ${ctx.cycle}. " else ""
        val starter = starters[starterIndex % starters.size]
        starterIndex += 1
        val parts = listOfNotNull("Round ${ctx.round}", name.ifEmpty { null }, target, starter)
        return prefix + parts.joinToString(". ")
    }

    private fun completionCue(ctx: EmomCueContext): EmomCue? {
        val rest = ctx.secondsRemaining
        val line = when (ctx.outcome) {
            EmomRoundOutcome.CONFIRMED -> when {
                rest >= 20 -> roundDoneLong[roundDoneLongIndex++ % roundDoneLong.size]
                rest >= 8 -> roundDoneShort[roundDoneShortIndex++ % roundDoneShort.size]
                else -> null
            }
            EmomRoundOutcome.AUTO_COMPLETED -> when {
                rest >= 20 -> timeLong[timeLongIndex++ % timeLong.size]
                rest >= 8 -> timeShort
                else -> null
            }
            else -> null
        } ?: return null
        return EmomCue(line, EmomCuePriority.completion)
    }

    companion object {
        const val letsWork = "Let's work!"
        const val tenSeconds = "Ten seconds remaining!"
        const val blockComplete = "Block complete. Way to work!"
        const val timeShort = "Time! Recover."
        /** The 10 s warning only plays when the phase it counts down is at least this long. */
        const val warningMinimumPhaseSeconds = 15
        val starters = listOf("Let's go!", "You've got this!", "Time to work!", "Let's get after it!")
        val roundDoneLong = listOf("Nice work! Take your rest.", "Well done! Catch your breath.", "Strong work! You've earned that rest.")
        val roundDoneShort = listOf("Great job! Recover.", "Nice work! Recover.")
        val timeLong = listOf("Time! Nice work.", "Great job! Take your rest.", "Work complete. Recover.")
    }
}

/** Playlist Mode for EMOM members (PRD §6). */
object EmomPlaylist {
    /**
     * At most ONE logging circle per member: completed once every occurrence is completed,
     * otherwise the first unfinished occurrence's state. Empty stays empty.
     */
    fun circleStates(states: List<SetState>): List<SetState> {
        if (states.isEmpty()) return states
        return listOf(states.firstOrNull { it != SetState.COMPLETED } ?: SetState.COMPLETED)
    }
}
