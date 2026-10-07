package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.IntervalMath
import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.IntervalPhase
import com.fitnesslink.fit.model.IntervalPlan
import com.fitnesslink.fit.model.TaskRow
import com.fitnesslink.fit.model.WorkoutTask

/**
 * Drives the work/rest state machine for an interval group. Port of iOS `IntervalRunner`.
 *
 * Every counter comes from the shared [IntervalPlan] ([IntervalMath.plan]), so the builder's
 * "Total Time" is this runner's elapsed time by construction.
 *
 * Timing is wall-clock anchored (epoch millis from [clock]): each phase records its [phaseEndMs]
 * and every [reconcile] recomputes the remaining seconds from the clock. When a phase expired while
 * no ticks were delivered the runner fast-forwards through the missed phases — chaining anchors so
 * nothing drifts — and emits exactly ONE transition event for the phase it lands on. Pause keeps
 * the sub-second remainder so a resume doesn't round away time.
 *
 * Unlike iOS the runner owns no timer: the session view model's 250 ms ticker calls [reconcile].
 */
class IntervalRunner(
    group: TaskRow,
    prepareSeconds: Int = IntervalMath.defaultPrepareSeconds,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    sealed class Event {
        /** Seconds remaining in the current phase. */
        data class Tick(val remaining: Int) : Event()
        object PrepareStarted : Event() { override fun toString() = "PrepareStarted" }
        object TransitionToWork : Event() { override fun toString() = "TransitionToWork" }
        object TransitionToRest : Event() { override fun toString() = "TransitionToRest" }
        object TransitionToBetweenSetsRest : Event() { override fun toString() = "TransitionToBetweenSetsRest" }
        object RoundComplete : Event() { override fun toString() = "RoundComplete" }
        /** A set of rounds finished; about to start the next. */
        object SetComplete : Event() { override fun toString() = "SetComplete" }
        /** Entering the last round of the last set. */
        object FinalRound : Event() { override fun toString() = "FinalRound" }
        object BlockComplete : Event() { override fun toString() = "BlockComplete" }
    }

    /**
     * Everything needed to rebuild a runner mid-block. Session-scoped (held in memory on
     * `ActiveWorkoutSession`). Exactly one of [phaseEndMs] / [pausedRemainingMs] is set while the
     * block is live. Gson-serializable.
     */
    data class Snapshot(
        val groupId: String,
        val phase: IntervalPhase,
        val currentSet: Int,
        val currentRound: Int,
        val currentExerciseIndex: Int,
        val phaseEndMs: Long?,
        val pausedRemainingMs: Long?,
        val phaseDuration: Int,
        val roundsCompleted: Int,
        val amrapPartialReps: Int,
        val prepareSeconds: Int
    )

    // MARK: - Inputs

    val plan: IntervalPlan
    /** `groupId` of the block this runner drives — the key a snapshot is matched against on restore. */
    val groupId: String
    private val exercises: List<WorkoutTask>
    val prepareSeconds: Int = maxOf(0, prepareSeconds)

    // MARK: - Outputs

    var phase: IntervalPhase = IntervalPhase.PREPARE
        private set
    var secondsRemaining: Int = 0
        private set
    var currentRound: Int = 0
        private set
    var currentSet: Int = 0
        private set
    /** AMRAP score. */
    var roundsCompleted: Int = 0
        private set
    /** AMRAP-only — extra reps completed within the in-flight round. */
    var amrapPartialReps: Int = 0
    var lastEvent: Event? = null
        private set
    var isPaused: Boolean = false
        private set
    /** Cue sink. Set by the session view model; fires synchronously from reconcile, transitions and user actions. */
    var onEvent: ((Event) -> Unit)? = null
    /** Full length of the current phase — the denominator for the phase progress ring / bar. */
    var phaseDuration: Int = 0
        private set

    // MARK: - Private

    /** Wall-clock instant the current phase ends. Null while paused or done. */
    private var phaseEndMs: Long? = null
    /** Remaining millis captured at [pause], restored by [resume]. */
    private var pausedRemainingMs: Long = 0
    private var amrapExerciseIndex: Int = 0
    private var suppressEvents = false
    private var setCompletedDuringWalk = false

    init {
        val lead = group.task
        groupId = lead?.groupId ?: ""
        if (lead == null) {
            plan = IntervalPlan(IntervalMode.HIIT, 0, 0, 0, 1, 0, 0)
            exercises = emptyList()
        } else {
            plan = IntervalMath.plan(lead, rowTotalRounds = group.totalRounds, mode = group.intervalMode ?: lead.intervalMode)
            exercises = listOf(lead) + group.advancedTasks
        }
    }

    /** Rebuild a runner from a [Snapshot]. The caller then runs [reconcile] so the block catches up with the wall clock. */
    constructor(group: TaskRow, restoring: Snapshot, clock: () -> Long = { System.currentTimeMillis() }) :
        this(group, restoring.prepareSeconds, clock) {
        phase = restoring.phase
        currentSet = restoring.currentSet
        currentRound = restoring.currentRound
        amrapExerciseIndex = restoring.currentExerciseIndex
        phaseDuration = maxOf(0, restoring.phaseDuration)
        roundsCompleted = restoring.roundsCompleted
        amrapPartialReps = restoring.amrapPartialReps
        val paused = restoring.pausedRemainingMs
        if (paused != null) {
            isPaused = true
            pausedRemainingMs = maxOf(0L, paused)
            phaseEndMs = null
            secondsRemaining = ceilSeconds(pausedRemainingMs)
        } else {
            isPaused = false
            pausedRemainingMs = 0
            phaseEndMs = restoring.phaseEndMs
            val end = phaseEndMs
            secondsRemaining = if (end != null) maxOf(0, ceilSeconds(end - clock())) else 0
        }
    }

    /** Capture the current position for the restoring constructor. */
    fun snapshot(): Snapshot = Snapshot(
        groupId = groupId,
        phase = phase,
        currentSet = currentSet,
        currentRound = currentRound,
        currentExerciseIndex = if (plan.mode == IntervalMode.AMRAP) amrapExerciseIndex else currentExerciseIndex,
        phaseEndMs = if (isPaused) null else phaseEndMs,
        pausedRemainingMs = if (isPaused && phase != IntervalPhase.DONE) pausedRemainingMs else null,
        phaseDuration = phaseDuration,
        roundsCompleted = roundsCompleted,
        amrapPartialReps = amrapPartialReps,
        prepareSeconds = prepareSeconds
    )

    // MARK: - Derived state

    /** Which member of the group is on screen. Tabata / HIIT / EMOM rotate one exercise per round; AMRAP rotates on each user-marked round. */
    val currentExerciseIndex: Int
        get() {
            if (exercises.isEmpty()) return 0
            if (plan.mode == IntervalMode.AMRAP) return amrapExerciseIndex % exercises.size
            return maxOf(0, currentRound - 1) % exercises.size
        }

    val currentExercise: WorkoutTask? get() = exercises.getOrNull(currentExerciseIndex)

    /** First task in the group — carries the cycle-rest config (passive / active recovery). */
    val leadTask: WorkoutTask? get() = exercises.firstOrNull()

    val roundsTotal: Int get() = plan.roundsPerSet
    val setsTotal: Int get() = plan.sets
    val intervalMode: IntervalMode get() = plan.mode

    /** 0 → 1 through the current phase. 1 when the phase has no length. */
    val phaseProgress: Double
        get() {
            if (phaseDuration <= 0) return 1.0
            return minOf(1.0, maxOf(0.0, (phaseDuration - secondsRemaining).toDouble() / phaseDuration.toDouble()))
        }

    /** Whole-block length (prepare excluded) — the builder's Total Time. */
    val blockTotalSeconds: Int get() = IntervalMath.blockSeconds(plan)

    /** Seconds left in the block from the current position. */
    val blockRemainingSeconds: Int
        get() = IntervalMath.remaining(plan, set = currentSet, round = currentRound, phase = phase, phaseRemaining = secondsRemaining)

    /** True once the last round of the last set has begun. AMRAP: the last set's window. */
    val isFinalRound: Boolean
        get() {
            if (phase != IntervalPhase.WORK && phase != IntervalPhase.REST) return false
            if (currentSet < plan.sets) return false
            if (plan.mode == IntervalMode.AMRAP) return true
            return currentRound >= plan.roundsPerSet
        }

    /** True when the phase that follows the current one is work — the gate for the spoken 3-2-1. */
    val leadsIntoWork: Boolean
        get() = when (phase) {
            IntervalPhase.PREPARE, IntervalPhase.BETWEEN_SETS_REST -> true
            IntervalPhase.DONE -> false
            IntervalPhase.REST -> if (currentRound < plan.roundsPerSet) true else nextSetStartsImmediately
            IntervalPhase.WORK -> when (plan.mode) {
                IntervalMode.TABATA, IntervalMode.HIIT ->
                    if (currentRound < plan.roundsPerSet) plan.restSeconds == 0 else nextSetStartsImmediately
                IntervalMode.EMOM -> when {
                    plan.restSeconds > 0 -> false
                    currentRound < plan.roundsPerSet -> true
                    else -> nextSetStartsImmediately
                }
                IntervalMode.AMRAP -> nextSetStartsImmediately
            }
        }

    private val nextSetStartsImmediately: Boolean
        get() = currentSet < plan.sets && plan.betweenSetsRestSeconds == 0

    /** The set / round that starts after the current phase finishes, or null when the block ends instead. */
    val nextSlot: Pair<Int, Int>?
        get() = when (phase) {
            IntervalPhase.DONE -> null
            IntervalPhase.PREPARE -> Pair(1, if (plan.mode == IntervalMode.AMRAP) 0 else 1)
            IntervalPhase.BETWEEN_SETS_REST ->
                if (currentSet < plan.sets) Pair(currentSet + 1, if (plan.mode == IntervalMode.AMRAP) 0 else 1) else null
            IntervalPhase.WORK, IntervalPhase.REST -> {
                if (plan.mode == IntervalMode.AMRAP) {
                    if (currentSet < plan.sets) Pair(currentSet + 1, 0) else null
                } else if (currentRound < plan.roundsPerSet) {
                    Pair(currentSet, currentRound + 1)
                } else if (currentSet < plan.sets) {
                    Pair(currentSet + 1, 1)
                } else null
            }
        }

    /** The exercise the next work phase will show; null once the final round of the final set is under way. */
    val nextExercise: WorkoutTask?
        get() {
            if (exercises.isEmpty()) return null
            val slot = nextSlot ?: return null
            if (plan.mode == IntervalMode.AMRAP) return exercises[0]
            return exercises[maxOf(0, slot.second - 1) % exercises.size]
        }

    // MARK: - Control

    fun start() {
        val now = clock()
        currentRound = 0
        currentSet = 1
        amrapExerciseIndex = 0
        roundsCompleted = 0
        amrapPartialReps = 0
        isPaused = false
        if (prepareSeconds > 0) {
            setPhase(IntervalPhase.PREPARE, prepareSeconds, now)
            emit(Event.PrepareStarted)
        } else {
            advance(now)
        }
    }

    fun pause() {
        if (isPaused || phase == IntervalPhase.DONE) return
        isPaused = true
        phaseEndMs?.let { end -> pausedRemainingMs = maxOf(0L, end - clock()) }
        phaseEndMs = null
    }

    fun resume() {
        if (!isPaused) return
        isPaused = false
        if (phase == IntervalPhase.DONE) return
        phaseEndMs = clock() + pausedRemainingMs
        pausedRemainingMs = 0
        reconcile(clock())
    }

    /** Jump to the end of the current phase — the normal advance path then decides what follows. */
    fun skip() {
        if (phase == IntervalPhase.DONE) return
        val now = clock()
        if (isPaused) {
            // Advance while frozen: land on the next phase, still paused.
            isPaused = false
            phaseEndMs = now
            reconcile(now)
            pause()
            return
        }
        phaseEndMs = now
        reconcile(now)
    }

    /**
     * The Up Next tap inside a block (FTNSSLNK-437): go straight to the work phase the card names.
     * Returns false when no work follows (the final round is under way). Never completes the block.
     */
    fun jumpToNextWork(): Boolean {
        if (phase == IntervalPhase.DONE) return false
        val slot = nextSlot ?: return false
        val now = clock()
        when (phase) {
            IntervalPhase.PREPARE -> {
                currentRound = if (plan.mode == IntervalMode.AMRAP) 0 else 1
                enterWork(now)
            }
            IntervalPhase.WORK, IntervalPhase.REST, IntervalPhase.BETWEEN_SETS_REST -> {
                if (slot.first > currentSet) {
                    if (phase != IntervalPhase.BETWEEN_SETS_REST) emit(Event.SetComplete)
                    startNextSet(now)
                } else {
                    currentRound = slot.second
                    enterWork(now)
                }
            }
            IntervalPhase.DONE -> return false
        }
        return true
    }

    /** Silent teardown — no `BlockComplete`, no further ticks. */
    fun cancel() {
        onEvent = null
        phaseEndMs = null
        phase = IntervalPhase.DONE
    }

    /** Legacy alias of [cancel]. */
    fun stop() = cancel()

    /** Re-enter a finished (or fresh) block on the final round's work — what Previous lands on when stepping back into a block. */
    fun jumpToLastRound() {
        val now = clock()
        isPaused = false
        currentSet = plan.sets
        currentRound = if (plan.mode == IntervalMode.AMRAP) 0 else plan.roundsPerSet
        amrapExerciseIndex = 0
        enterWork(now)
    }

    /**
     * Previous inside the block. Returns false only when the block is at its very start so the
     * caller can leave the block instead. More than 3 s into any phase restarts that phase.
     */
    fun back(): Boolean {
        if (phase == IntervalPhase.DONE) return false
        if (phase == IntervalPhase.PREPARE) return false
        val now = clock()
        val remainingMs: Long = if (isPaused) pausedRemainingMs else maxOf(0L, (phaseEndMs ?: now) - now)
        val elapsed = phaseDuration.toDouble() - remainingMs / 1000.0
        val lastRound = if (plan.mode == IntervalMode.AMRAP) 0 else plan.roundsPerSet

        if (elapsed > 3) {
            restartPhase(now)
            return true
        }

        return when (phase) {
            IntervalPhase.WORK -> {
                if (plan.mode != IntervalMode.AMRAP && currentRound > 1) {
                    currentRound -= 1
                    if (plan.restSeconds > 0) enterRest(now) else enterWork(now)
                    true
                } else if (currentSet > 1) {
                    currentSet -= 1
                    currentRound = lastRound
                    if (plan.betweenSetsRestSeconds > 0) enterBetweenSetsRest(now) else enterWork(now)
                    true
                } else false
            }
            IntervalPhase.REST, IntervalPhase.BETWEEN_SETS_REST -> {
                enterWork(now)
                true
            }
            IntervalPhase.PREPARE, IntervalPhase.DONE -> false
        }
    }

    /** AMRAP-only: the user taps a button each time they complete a round. */
    fun markAmrapRound() {
        if (plan.mode != IntervalMode.AMRAP || phase != IntervalPhase.WORK) return
        roundsCompleted += 1
        amrapExerciseIndex += 1
        emit(Event.RoundComplete)
    }

    /** EMOM-only: convert the rest of the current minute into rest; the minute boundary is kept. */
    fun markEmomRoundDone() {
        if (plan.mode != IntervalMode.EMOM || phase != IntervalPhase.WORK) return
        val extraMs = plan.restSeconds * 1000L
        phase = IntervalPhase.REST
        val end = phaseEndMs
        if (end != null) phaseEndMs = end + extraMs else pausedRemainingMs += extraMs
        secondsRemaining += plan.restSeconds
        phaseDuration = secondsRemaining
        emit(Event.TransitionToRest)
    }

    // MARK: - Clock

    /**
     * Bring the runner up to date with the wall clock. Fast-forwards through any phases that
     * expired while no ticks were delivered; emits one transition for the landing phase (or
     * `BlockComplete`).
     */
    fun reconcile(nowMs: Long) {
        if (isPaused || phase == IntervalPhase.DONE) return
        val end = phaseEndMs ?: return
        val remaining = end - nowMs
        if (remaining > 0) {
            updateSecondsRemaining(ceilSeconds(remaining))
            return
        }

        suppressEvents = true
        setCompletedDuringWalk = false
        var anchor = end
        var landed: Event? = null
        while (phase != IntervalPhase.DONE) {
            advance(anchor)
            landed = transitionEvent(phase)
            if (phase == IntervalPhase.DONE) break
            val nextEnd = phaseEndMs ?: break
            if (nextEnd > nowMs) break
            anchor = nextEnd
        }
        suppressEvents = false

        if (setCompletedDuringWalk) {
            setCompletedDuringWalk = false
            emit(Event.SetComplete)
        }
        if (phase == IntervalPhase.DONE) {
            secondsRemaining = 0
            finishBlock()
            return
        }
        phaseEndMs?.let { secondsRemaining = maxOf(0, ceilSeconds(it - nowMs)) }
        landed?.let { emit(it) }
        if (isFinalRound && landed == Event.TransitionToWork && plan.roundsPerSet * plan.sets > 1) {
            emit(Event.FinalRound)
        }
    }

    private fun updateSecondsRemaining(value: Int) {
        val clamped = maxOf(0, value)
        if (clamped == secondsRemaining) return
        secondsRemaining = clamped
        if (clamped > 0) emit(Event.Tick(clamped))
    }

    // MARK: - State machine

    private fun advance(anchor: Long) {
        when (phase) {
            IntervalPhase.PREPARE -> {
                currentRound = if (plan.mode == IntervalMode.AMRAP) 0 else 1
                enterWork(anchor)
            }
            IntervalPhase.WORK -> when (plan.mode) {
                IntervalMode.TABATA, IntervalMode.HIIT -> {
                    if (currentRound < plan.roundsPerSet) {
                        if (plan.restSeconds > 0) enterRest(anchor) else { currentRound += 1; enterWork(anchor) }
                    } else finishSetOrAdvance(anchor)
                }
                IntervalMode.EMOM -> {
                    if (plan.restSeconds > 0) enterRest(anchor)
                    else if (currentRound < plan.roundsPerSet) { currentRound += 1; enterWork(anchor) }
                    else finishSetOrAdvance(anchor)
                }
                IntervalMode.AMRAP -> finishSetOrAdvance(anchor)
            }
            IntervalPhase.REST -> {
                if (currentRound < plan.roundsPerSet) { currentRound += 1; enterWork(anchor) }
                else finishSetOrAdvance(anchor)
            }
            IntervalPhase.BETWEEN_SETS_REST -> startNextSet(anchor)
            IntervalPhase.DONE -> Unit
        }
    }

    private fun finishSetOrAdvance(anchor: Long) {
        if (currentSet < plan.sets) {
            if (suppressEvents) setCompletedDuringWalk = true else emit(Event.SetComplete)
            if (plan.betweenSetsRestSeconds > 0) enterBetweenSetsRest(anchor) else startNextSet(anchor)
        } else {
            phase = IntervalPhase.DONE
            phaseEndMs = null
        }
    }

    private fun startNextSet(anchor: Long) {
        currentSet += 1
        currentRound = if (plan.mode == IntervalMode.AMRAP) 0 else 1
        amrapExerciseIndex = 0
        enterWork(anchor)
    }

    private fun enterWork(anchor: Long) {
        val seconds = if (plan.mode == IntervalMode.AMRAP) plan.windowSeconds else plan.workSeconds
        setPhase(IntervalPhase.WORK, seconds, anchor)
        emit(Event.TransitionToWork)
        if (isFinalRound && plan.roundsPerSet * plan.sets > 1) emit(Event.FinalRound)
    }

    private fun enterRest(anchor: Long) {
        setPhase(IntervalPhase.REST, plan.restSeconds, anchor)
        emit(Event.TransitionToRest)
    }

    private fun enterBetweenSetsRest(anchor: Long) {
        setPhase(IntervalPhase.BETWEEN_SETS_REST, plan.betweenSetsRestSeconds, anchor)
        emit(Event.TransitionToBetweenSetsRest)
    }

    private fun restartPhase(anchor: Long) {
        setPhase(phase, phaseDuration, anchor)
    }

    private fun setPhase(newPhase: IntervalPhase, duration: Int, anchor: Long) {
        phase = newPhase
        phaseDuration = maxOf(0, duration)
        secondsRemaining = phaseDuration
        phaseEndMs = anchor + phaseDuration * 1000L
        if (isPaused) {
            // Entered via back() / jumpToLastRound() while frozen: hold the full phase until resume.
            pausedRemainingMs = phaseDuration * 1000L
            phaseEndMs = null
        }
    }

    private fun finishBlock() {
        phase = IntervalPhase.DONE
        phaseEndMs = null
        emit(Event.BlockComplete)
    }

    private fun transitionEvent(phase: IntervalPhase): Event? = when (phase) {
        IntervalPhase.WORK -> Event.TransitionToWork
        IntervalPhase.REST -> Event.TransitionToRest
        IntervalPhase.BETWEEN_SETS_REST -> Event.TransitionToBetweenSetsRest
        IntervalPhase.PREPARE -> Event.PrepareStarted
        IntervalPhase.DONE -> null
    }

    private fun emit(event: Event) {
        if (suppressEvents) return
        lastEvent = event
        onEvent?.invoke(event)
    }

    companion object {
        /** Round a positive remainder UP to whole seconds (iOS `rounded(.up)`). */
        fun ceilSeconds(ms: Long): Int = if (ms <= 0) 0 else ((ms + 999) / 1000).toInt()
    }
}
