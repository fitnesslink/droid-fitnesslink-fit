package com.fitnesslink.fit.session

import java.util.Locale

// MARK: - Voice and sound cues (FTNSSLNK-415) — pure policy, no Android.

/**
 * Spoken coach phrases — every string the coach says lives here so
 * localisation is one file. Port of iOS `AudioCueService.VoicePhrase`.
 */
sealed class VoicePhrase {
    object GetReady : VoicePhrase()
    object Begin : VoicePhrase()
    object Rest : VoicePhrase()
    object FinalRound : VoicePhrase()
    object WorkoutComplete : VoicePhrase()
    /** One-time playlist onboarding: where the interactive-mode button lives. */
    object InteractiveModeHint : VoicePhrase()
    object Three : VoicePhrase()
    object Two : VoicePhrase()
    object One : VoicePhrase()
    /** The preparation countdown hit zero. */
    object Go : VoicePhrase()
    /** A timed set reached zero. */
    object Done : VoicePhrase()
    /** A timed set is half over. */
    object Halfway : VoicePhrase()
    /** Preparation screen: "Up next: Squat, 3 sets of 10 reps. Get ready." [structure] replaces exercise + prescription. */
    data class UpNext(val exercise: String, val prescription: String?, val structure: String? = null) : VoicePhrase()
    /** Phase boundary: "Warm-Up complete. Next phase: Exercises." */
    data class PhaseTransition(val completed: String, val next: String) : VoicePhrase()
    /** Rest: "Rest, 30 seconds. Up next: Set 2, Squat." — with "Next phase: Core." before Up Next when given. */
    data class RestFor(val seconds: Int, val upNext: String?, val nextPhase: String? = null) : VoicePhrase()
    /** Playlist → Interactive: the first thing the coach says on the entry preparation screen (FTNSSLNK-435). */
    object InteractiveModeEntered : VoicePhrase()
    /** A new set of the same exercise began: "Set 2 of 3" (FTNSSLNK-423). */
    data class SetAdvanced(val set: Int, val total: Int) : VoicePhrase()

    /** How a phrase meets speech already in flight. */
    enum class Priority {
        /** Cuts whatever is playing ("Begin" wins over a trailing "1"). */
        INTERRUPT,
        /** Waits for a short phrase to finish, but cuts a long one. */
        QUEUE,
        /** Dropped for that moment ("Get ready" must not be cut by "2"). */
        DROP_IF_BUSY
    }

    /** The utterance. */
    val text: String
        get() = when (this) {
            GetReady -> "Get ready"
            Begin -> "Begin"
            Rest -> "Rest"
            FinalRound -> "Final round"
            WorkoutComplete -> "Workout complete"
            InteractiveModeHint ->
                "Tip: tap the white play button at the top right to switch to interactive mode. It guides you through each exercise with videos and timers."
            Three -> "3"
            Two -> "2"
            One -> "1"
            Go -> "Go"
            Done -> "Done"
            Halfway -> "Halfway"
            is UpNext -> {
                val structureText = structure?.trim()
                if (!structureText.isNullOrEmpty()) {
                    val body = if (structureText.endsWith(".")) structureText.dropLast(1) else structureText
                    "Up next: $body. Get ready."
                } else {
                    val name = exercise.trim()
                    if (!prescription.isNullOrEmpty()) "Up next: $name, $prescription. Get ready."
                    else "Up next: $name. Get ready."
                }
            }
            is PhaseTransition -> "$completed complete. Next phase: $next."
            is RestFor -> {
                var rest = "Rest, ${spokenSeconds(seconds)}."
                if (!nextPhase.isNullOrEmpty()) rest += " Next phase: $nextPhase."
                if (!upNext.isNullOrEmpty()) "$rest Up next: $upNext." else rest
            }
            InteractiveModeEntered -> "You have entered Interactive Mode."
            is SetAdvanced -> if (total > 1) "Set $set of $total" else "Set $set"
        }

    /** What the synthesizer actually reads — punctuation softened so a colon doesn't rush the next word. */
    val spokenText: String
        get() = text.replace(": ", ", ").replace(" — ", ", ").replace(" - ", ", ")

    /** Longer coach phrases read a touch slower than the default (1.0) so they sound like a person. */
    val speechRate: Float
        get() = if (isShort) 1.0f else 0.94f

    val priority: Priority
        get() = when (this) {
            Three, Two, One -> Priority.DROP_IF_BUSY
            is UpNext, is RestFor, is PhaseTransition, InteractiveModeEntered, is SetAdvanced -> Priority.QUEUE
            else -> Priority.INTERRUPT
        }

    /** Short phrases a queued phrase waits for. */
    val isShort: Boolean
        get() = when (this) {
            Three, Two, One, Go, Done, Halfway, Begin, Rest, GetReady, FinalRound, is SetAdvanced -> true
            else -> false
        }

    /** Countdown numbers never interrupt a phrase in flight; every other phrase cuts (or queues behind) what is playing. */
    val interruptsSpeech: Boolean get() = priority != Priority.DROP_IF_BUSY

    override fun toString(): String = "VoicePhrase(${text})"

    companion object {
        /** The countdown number for [remaining] seconds, null outside 1…3. */
        fun countdown(remaining: Int): VoicePhrase? = when (remaining) {
            3 -> Three
            2 -> Two
            1 -> One
            else -> null
        }

        /** `30 seconds` / `1 minute` / `1 minute 30 seconds`. */
        fun spokenSeconds(seconds: Int): String {
            val s = maxOf(0, seconds)
            if (s < 60) return "$s ${if (s == 1) "second" else "seconds"}"
            val m = s / 60
            val r = s % 60
            val minutes = "$m ${if (m == 1) "minute" else "minutes"}"
            return if (r == 0) minutes else "$minutes $r ${if (r == 1) "second" else "seconds"}"
        }
    }
}

/** The phase-change wording carried by a preparation-screen cue. */
data class PhaseChangeCue(val completed: String, val next: String)

/** What happens in a session that the coach may react to. */
sealed class SessionCueEvent {
    /**
     * The preparation screen opened for [exercise] (and, on a phase boundary, announces the phase
     * change first). [structure] is the spoken description of a structured block, which replaces
     * exercise + prescription; [modeEntry] is true for the screen that opens when Interactive Mode
     * is entered.
     */
    data class PrepStarted(
        val exercise: String,
        val prescription: String?,
        val phaseChange: PhaseChangeCue?,
        val structure: String? = null,
        val modeEntry: Boolean = false
    ) : SessionCueEvent()
    data class PrepTick(val remaining: Int) : SessionCueEvent()
    object PrepEnded : SessionCueEvent()
    data class RestStarted(val seconds: Int, val upNext: String?, val nextPhase: String? = null) : SessionCueEvent()
    data class SetAdvanced(val set: Int, val total: Int) : SessionCueEvent()
    data class RestTick(val remaining: Int) : SessionCueEvent()
    data class TimedSetTick(val remaining: Int, val total: Int) : SessionCueEvent()
    object TimedSetEnded : SessionCueEvent()
    object Skipped : SessionCueEvent()
    object WorkoutComplete : SessionCueEvent()
}

enum class SessionHaptic { TICK, TRANSITION, SUCCESS, SELECTION }

/** One thing to play. Speech carries its phrase; sounds are named by role. */
sealed class SessionCueOutput {
    data class Speak(val phrase: VoicePhrase) : SessionCueOutput()
    /** Countdown tick (3-2-1). */
    object Beep : SessionCueOutput() { override fun toString() = "Beep" }
    /** Transition ding (work begins). */
    object Ding : SessionCueOutput() { override fun toString() = "Ding" }
    /** Short neutral tone (skip). */
    object Tone : SessionCueOutput() { override fun toString() = "Tone" }
    /** Celebratory chime (workout complete). */
    object Chime : SessionCueOutput() { override fun toString() = "Chime" }
    data class Haptic(val kind: SessionHaptic) : SessionCueOutput()
}

/**
 * The channel switches: the session's master mute on top of the per-channel preferences.
 * Muting silences every audio output and nothing else — the workout never pauses for it.
 */
data class SessionCuePolicy(
    val isMasterMuted: Boolean = false,
    val soundsEnabled: Boolean = true,
    val voiceEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true
) {
    val soundsOn: Boolean get() = !isMasterMuted && soundsEnabled
    val voiceOn: Boolean get() = !isMasterMuted && voiceEnabled

    /** Outputs for an event, already filtered by the switches. */
    fun outputs(event: SessionCueEvent): List<SessionCueOutput> {
        val out = mutableListOf<SessionCueOutput>()
        fun speak(phrase: VoicePhrase) { if (voiceOn) out.add(SessionCueOutput.Speak(phrase)) }
        fun sound(output: SessionCueOutput) { if (soundsOn) out.add(output) }
        fun haptic(kind: SessionHaptic) { if (hapticsEnabled) out.add(SessionCueOutput.Haptic(kind)) }

        when (event) {
            is SessionCueEvent.PrepStarted -> {
                // Order: mode entry → phase → exercise / structure → sets-reps-or-time.
                if (event.modeEntry) speak(VoicePhrase.InteractiveModeEntered)
                event.phaseChange?.let { speak(VoicePhrase.PhaseTransition(it.completed, it.next)) }
                speak(VoicePhrase.UpNext(event.exercise, event.prescription, event.structure))
            }
            is SessionCueEvent.SetAdvanced -> {
                haptic(SessionHaptic.TRANSITION)
                speak(VoicePhrase.SetAdvanced(event.set, event.total))
            }
            is SessionCueEvent.PrepTick -> countdownOutputs(event.remaining, ::sound, ::haptic, ::speak)
            is SessionCueEvent.RestTick -> countdownOutputs(event.remaining, ::sound, ::haptic, ::speak)
            SessionCueEvent.PrepEnded -> {
                sound(SessionCueOutput.Ding)
                haptic(SessionHaptic.TRANSITION)
                speak(VoicePhrase.Go)
            }
            is SessionCueEvent.RestStarted -> {
                sound(SessionCueOutput.Ding)
                haptic(SessionHaptic.TRANSITION)
                speak(VoicePhrase.RestFor(event.seconds, event.upNext, event.nextPhase))
            }
            is SessionCueEvent.TimedSetTick -> {
                if (event.total >= minimumHalfwaySeconds && event.remaining == event.total / 2) speak(VoicePhrase.Halfway)
                // The last three seconds beep only — "Done" follows.
                if (event.remaining in 1..3) {
                    sound(SessionCueOutput.Beep)
                    haptic(SessionHaptic.TICK)
                }
            }
            SessionCueEvent.TimedSetEnded -> {
                sound(SessionCueOutput.Ding)
                haptic(SessionHaptic.TRANSITION)
                speak(VoicePhrase.Done)
            }
            SessionCueEvent.Skipped -> {
                sound(SessionCueOutput.Tone)
                haptic(SessionHaptic.SELECTION)
            }
            SessionCueEvent.WorkoutComplete -> {
                sound(SessionCueOutput.Chime)
                haptic(SessionHaptic.SUCCESS)
                speak(VoicePhrase.WorkoutComplete)
            }
        }
        return out
    }

    private fun countdownOutputs(
        remaining: Int,
        sound: (SessionCueOutput) -> Unit,
        haptic: (SessionHaptic) -> Unit,
        speak: (VoicePhrase) -> Unit
    ) {
        if (remaining !in 1..3) return
        sound(SessionCueOutput.Beep)
        haptic(SessionHaptic.TICK)
        VoicePhrase.countdown(remaining)?.let(speak)
    }

    companion object {
        /** Timed sets shorter than this get no halfway call. */
        const val minimumHalfwaySeconds = 10
    }
}

/**
 * Stateful scheduling on top of the policy: a halfway call once per set, one cue per countdown
 * second, and skip tones rate-limited on the injected clock (millis).
 */
class SessionCueScheduler(
    var policy: SessionCuePolicy = SessionCuePolicy(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var halfwayAnnounced = false
    private var lastCountdownSecond: Int? = null
    private var lastSkipAtMs: Long? = null

    /** A new set / rest / preparation started — countdown bookkeeping starts over. */
    fun resetCountdown() {
        halfwayAnnounced = false
        lastCountdownSecond = null
    }

    fun cues(event: SessionCueEvent): List<SessionCueOutput> {
        when (event) {
            is SessionCueEvent.PrepStarted, is SessionCueEvent.RestStarted, is SessionCueEvent.SetAdvanced -> resetCountdown()
            SessionCueEvent.PrepEnded, SessionCueEvent.TimedSetEnded, SessionCueEvent.WorkoutComplete -> resetCountdown()
            is SessionCueEvent.PrepTick -> {
                if (event.remaining == lastCountdownSecond) return emptyList()
                lastCountdownSecond = event.remaining
            }
            is SessionCueEvent.RestTick -> {
                if (event.remaining == lastCountdownSecond) return emptyList()
                lastCountdownSecond = event.remaining
            }
            is SessionCueEvent.TimedSetTick -> {
                if (event.remaining == lastCountdownSecond) return emptyList()
                lastCountdownSecond = event.remaining
                if (event.total >= SessionCuePolicy.minimumHalfwaySeconds && event.remaining == event.total / 2) {
                    if (halfwayAnnounced) {
                        return policy.outputs(event).filter { it != SessionCueOutput.Speak(VoicePhrase.Halfway) }
                    }
                    halfwayAnnounced = true
                }
            }
            SessionCueEvent.Skipped -> {
                val now = clock()
                val last = lastSkipAtMs
                if (last != null && now - last < skipToneSpacingMs) return emptyList()
                lastSkipAtMs = now
            }
        }
        return policy.outputs(event)
    }

    companion object {
        /** Minimum spacing between two skip tones. */
        const val skipToneSpacingMs = 300L
    }
}

/** Text shaping for spoken cues (the screen keeps `3 SETS × 10 REPS`, the voice says "3 sets of 10 reps"). */
object SessionCuePhrasing {
    /** `3 SETS × 10 REPS` → `3 sets of 10 reps`; `HIIT · 5:45` → `HIIT, 5:45`; null stays null. */
    fun spokenPrescription(line: String?): String? {
        if (line == null || line.trim().isEmpty()) return null
        val keepUpper = setOf("HIIT", "EMOM", "AMRAP")
        var spoken = line.split(" ").joinToString(" ") { word ->
            if (keepUpper.contains(word.uppercase(Locale.US))) word.uppercase(Locale.US) else word.lowercase(Locale.US)
        }
        spoken = spoken.replace(" × ", " of ")
        spoken = spoken.replace("×", " of ")
        spoken = spoken.replace(" · ", ", ")
        spoken = spoken.replace("tabata", "Tabata")
        return spoken.replace("  ", " ")
    }

    /** An Up Next title (`Set 2 — Ab Crunch`, `A2 Bench Press · 10 reps`) as the coach says it: `Set 2, Ab Crunch`. */
    fun spokenUpNext(title: String?): String? {
        if (title.isNullOrEmpty()) return null
        return title.replace(" — ", ", ").replace(" · ", ", ").replace(" × ", " of ")
    }

    /**
     * A structured block as the coach announces it (FTNSSLNK-428): structure first, then its
     * configuration, then the exercises.
     */
    fun spokenBlock(block: PreparationBlockSummary): String {
        val parts = mutableListOf<String>()
        val head = listOf(block.spokenTitle, block.spokenDetail).filter { it.isNotEmpty() }.joinToString(", ")
        parts.add("$head.")
        val kind = block.kind
        val listsMembers = when (kind) {
            PreparationBlockSummary.Kind.Superset, PreparationBlockSummary.Kind.Circuit -> true
            is PreparationBlockSummary.Kind.Interval ->
                kind.mode == com.fitnesslink.fit.model.IntervalMode.EMOM || kind.mode == com.fitnesslink.fit.model.IntervalMode.AMRAP
        }
        if (listsMembers) {
            for (member in block.members) {
                val metric = member.metric?.let { ", $it" } ?: ""
                parts.add("${ordinalExercise(member.ordinal)}: ${member.name}$metric.")
            }
        } else {
            val names = block.members.map { it.name }
            if (names.isNotEmpty()) {
                parts.add("${if (names.size == 1) "Exercise" else "Exercises"}: ${names.joinToString(", ")}.")
            }
        }
        return parts.joinToString(" ")
    }

    /** "First exercise" … "Tenth exercise", then "Exercise 11". */
    fun ordinalExercise(n: Int): String {
        val words = listOf("First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Eighth", "Ninth", "Tenth")
        if (n in 1..words.size) return "${words[n - 1]} exercise"
        return "Exercise $n"
    }
}

/**
 * Routes an [IntervalRunner.Event] into cue outputs the way iOS `AudioCueService.handle` does.
 * [finalRound] lets the work transition say "Final round" instead of "Begin"; [leadsToWork]
 * adds the spoken 3-2-1 to the final ticks (voice counts INTO work, never into a rest).
 */
object IntervalCueRouter {
    fun outputs(event: IntervalRunner.Event, finalRound: Boolean, leadsToWork: Boolean, policy: SessionCuePolicy): List<SessionCueOutput> {
        val out = mutableListOf<SessionCueOutput>()
        fun speak(phrase: VoicePhrase) { if (policy.voiceOn) out.add(SessionCueOutput.Speak(phrase)) }
        fun sound(output: SessionCueOutput) { if (policy.soundsOn) out.add(output) }
        fun haptic(kind: SessionHaptic) { if (policy.hapticsEnabled) out.add(SessionCueOutput.Haptic(kind)) }
        when (event) {
            is IntervalRunner.Event.Tick -> {
                if (event.remaining in 1..3) {
                    sound(SessionCueOutput.Beep)
                    haptic(SessionHaptic.TICK)
                    if (leadsToWork) VoicePhrase.countdown(event.remaining)?.let(::speak)
                }
            }
            IntervalRunner.Event.PrepareStarted -> speak(VoicePhrase.GetReady)
            IntervalRunner.Event.TransitionToWork -> {
                sound(SessionCueOutput.Ding)
                haptic(SessionHaptic.TRANSITION)
                speak(if (finalRound) VoicePhrase.FinalRound else VoicePhrase.Begin)
            }
            IntervalRunner.Event.TransitionToRest, IntervalRunner.Event.TransitionToBetweenSetsRest -> {
                sound(SessionCueOutput.Ding)
                haptic(SessionHaptic.TRANSITION)
                speak(VoicePhrase.Rest)
            }
            IntervalRunner.Event.RoundComplete, IntervalRunner.Event.SetComplete -> {
                sound(SessionCueOutput.Chime)
                haptic(SessionHaptic.SUCCESS)
            }
            IntervalRunner.Event.FinalRound -> haptic(SessionHaptic.SUCCESS)
            IntervalRunner.Event.BlockComplete -> {
                sound(SessionCueOutput.Chime)
                haptic(SessionHaptic.SUCCESS)
            }
        }
        return out
    }
}
