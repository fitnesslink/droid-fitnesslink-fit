package com.fitnesslink.fit.session

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * Plays session cues — countdown beeps, transition dings, chimes, spoken coach phrases and
 * haptics — through Android `TextToSpeech`, `ToneGenerator` and `VibrationEffect`. Port of iOS
 * `AudioCueService`: every decision comes from the pure [SessionCuePolicy] / [SessionCueScheduler]
 * / [IntervalCueRouter]; this object only performs the outputs.
 *
 * Channels: beeps / dings / chimes → `SessionSettings.intervalCuesEnabled`; voice →
 * `intervalVoiceCuesEnabled`; haptics → `intervalHapticsEnabled`; [isMasterMuted] silences every
 * audio channel for the current session without touching the preferences.
 */
object AudioCueService {
    private const val TAG = "AudioCueService"

    private var appContext: Context? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var toneGenerator: ToneGenerator? = null
    private val main = Handler(Looper.getMainLooper())
    private val scheduler = SessionCueScheduler()

    /** Completion per queued utterance id, so a caller can wait for the voice (the prep cue gate). */
    private val completions = mutableMapOf<String, () -> Unit>()
    /** The phrase most recently handed to the synthesizer (meaningful only while speaking). */
    private var currentPhrase: VoicePhrase? = null
    private var speaking = false

    /** Session-scoped master mute (top-bar speaker). Reset by the session VM when the session ends. */
    @Volatile var isMasterMuted = false

    /** Must be called once with a context before any cue plays (the session screens do this). */
    fun attach(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        SessionSettings.attach(app)
        tts = TextToSpeech(app) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.getDefault()
                CoachVoicePrefs.invalidate()
                applyVoice()
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { fire(utteranceId) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { fire(utteranceId) } }
            override fun onError(utteranceId: String?, errorCode: Int) { main.post { fire(utteranceId) } }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { main.post { fire(utteranceId) } }
        })
    }

    private fun fire(utteranceId: String?) {
        if (utteranceId == null) return
        val handler = completions.remove(utteranceId)
        if (completions.isEmpty()) { speaking = false; currentPhrase = null }
        handler?.invoke()
    }

    val policy: SessionCuePolicy get() = SessionSettings.policy(isMasterMuted)

    // MARK: - Session events

    /** Route a session-state event through the policy + scheduler and perform the outputs. */
    fun play(event: SessionCueEvent) = play(event, whenSpoken = null)

    /**
     * [play] that also reports when the event's voice has finished — the preparation screen holds
     * its countdown until then. Sounds and haptics fire immediately; the spoken phrases go out as
     * one sequence. [whenSpoken] runs at once when the event says nothing.
     */
    fun play(event: SessionCueEvent, whenSpoken: (() -> Unit)?) {
        scheduler.policy = policy
        val outputs = scheduler.cues(event)
        val phrases = mutableListOf<VoicePhrase>()
        for (output in outputs) {
            if (output is SessionCueOutput.Speak) phrases.add(output.phrase) else perform(output)
        }
        speakSequence(phrases, whenSpoken)
    }

    /** A new set / rest / preparation began — reset per-countdown state. */
    fun resetCountdownCues() = scheduler.resetCountdown()

    /** Route an interval-runner event (`finalRound` → "Final round"; `leadsToWork` → spoken 3-2-1). */
    fun handle(event: IntervalRunner.Event, finalRound: Boolean = false, leadsToWork: Boolean = false) {
        for (output in IntervalCueRouter.outputs(event, finalRound, leadsToWork, policy)) perform(output)
    }

    /** Skip Set / Skip Exercise / Skip Block: a short neutral tone plus a light tap — no speech. */
    fun skipped() = play(SessionCueEvent.Skipped)

    private fun perform(output: SessionCueOutput) {
        when (output) {
            is SessionCueOutput.Speak -> speakSequence(listOf(output.phrase), null)
            SessionCueOutput.Beep -> tone(ToneGenerator.TONE_PROP_BEEP, 120)
            SessionCueOutput.Ding -> tone(ToneGenerator.TONE_PROP_ACK, 200)
            SessionCueOutput.Tone -> tone(ToneGenerator.TONE_PROP_PROMPT, 80)
            SessionCueOutput.Chime -> tone(ToneGenerator.TONE_CDMA_CONFIRM, 400)
            is SessionCueOutput.Haptic -> haptic(output.kind)
        }
    }

    private fun tone(type: Int, durationMs: Int) {
        try {
            val generator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 80).also { toneGenerator = it }
            generator.startTone(type, durationMs)
        } catch (e: Exception) {
            Log.w(TAG, "tone failed: ${e.message}")
        }
    }

    private fun haptic(kind: SessionHaptic) {
        val context = appContext ?: return
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            val effect = when (kind) {
                SessionHaptic.TICK -> VibrationEffect.createOneShot(25, 120)
                SessionHaptic.TRANSITION -> VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
                SessionHaptic.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 80), -1)
                SessionHaptic.SELECTION -> VibrationEffect.createOneShot(15, 80)
            }
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.w(TAG, "haptic failed: ${e.message}")
        }
    }

    // MARK: - Speech

    /** Speak a coach phrase, honouring its priority against speech in flight. */
    fun speak(phrase: VoicePhrase) = speakSequence(listOf(phrase), null)

    /**
     * Speak [phrases] back to back as ONE announcement; [completion] runs on the main thread once the
     * last phrase finishes or is cut off, and at once when nothing is spoken.
     */
    fun speakSequence(phrases: List<VoicePhrase>, completion: (() -> Unit)?) {
        val first = phrases.firstOrNull()
        if (!policy.voiceOn || first == null) { completion?.invoke(); return }
        val engine = tts
        if (engine == null || !ttsReady) { completion?.invoke(); return }
        if (speaking) {
            when (first.priority) {
                VoicePhrase.Priority.DROP_IF_BUSY -> { completion?.invoke(); return }
                VoicePhrase.Priority.INTERRUPT -> stopSpeaking()
                VoicePhrase.Priority.QUEUE -> if (currentPhrase?.isShort != true) stopSpeaking()
            }
        }
        applyVoice()
        speaking = true
        for ((index, phrase) in phrases.withIndex()) {
            currentPhrase = phrase
            val id = UUID.randomUUID().toString()
            if (index == phrases.size - 1 && completion != null) completions[id] = completion
            else completions[id] = {}
            engine.setSpeechRate(phrase.speechRate)
            engine.speak(phrase.spokenText, TextToSpeech.QUEUE_ADD, null, id)
            if (index < phrases.size - 1) engine.playSilentUtterance(200, TextToSpeech.QUEUE_ADD, UUID.randomUUID().toString())
        }
    }

    /** Cut everything queued; pending completions are reported first so nothing waits forever. */
    private fun stopSpeaking() {
        val pending = completions.values.toList()
        completions.clear()
        speaking = false
        currentPhrase = null
        try { tts?.stop() } catch (_: Exception) {}
        pending.forEach { it() }
    }

    /** Stop any in-flight phrase (session teardown, master mute). */
    fun silenceVoice() { if (speaking) stopSpeaking() }

    /** Speak a sample so the user can audition the voice chosen in Preferences (ignores mute / the voice switch). */
    fun previewVoice() {
        val engine = tts ?: return
        if (!ttsReady) return
        stopSpeaking()
        applyVoice()
        engine.setSpeechRate(0.94f)
        engine.speak("Up next, Barbell Squat, 3 sets of 10 reps. Get ready.", TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    private fun applyVoice() {
        val engine = tts ?: return
        val voice = CoachVoicePrefs.current(engine) ?: return
        try { engine.voice = voice } catch (_: Exception) {}
    }

    /** Installed voices, for the Preferences picker. */
    fun availableVoices(): List<CoachVoicePrefs.Option> = tts?.let { CoachVoicePrefs.options(it) } ?: emptyList()
}

/**
 * Which TTS voice reads the coach phrases (port of iOS `AudioCueService.CoachVoice`): honour an
 * explicit choice from Preferences, otherwise the best-quality installed English voice, preferring
 * the user's own English variant, then en-US. Network-only and novelty voices are excluded.
 */
object CoachVoicePrefs {
    data class Option(val id: String, val name: String, val language: String, val quality: Int) {
        /** "Enhanced" / "Premium" style label from the engine's quality rating; null for a normal voice. */
        val qualityLabel: String?
            get() = when {
                quality >= Voice.QUALITY_VERY_HIGH -> "Premium"
                quality >= Voice.QUALITY_HIGH -> "Enhanced"
                else -> null
            }
        val languageLabel: String get() = Locale.forLanguageTag(language.replace('_', '-')).displayName
        val title: String get() = qualityLabel?.let { "$name ($it)" } ?: name
    }

    private var cached: Voice? = null

    fun invalidate() { cached = null }

    /** The voice the next utterance uses. Cached; refreshed when the preference changes. */
    fun current(engine: TextToSpeech): Voice? {
        cached?.let { return it }
        val voice = resolve(engine)
        cached = voice
        return voice
    }

    private fun resolve(engine: TextToSpeech): Voice? {
        val chosenName = SessionSettings.coachVoiceIdentifier
        val voices = usable(engine)
        if (chosenName != null) voices.firstOrNull { it.name == chosenName }?.let { return it }
        return automatic(engine)
    }

    /** Best installed English voice: quality first, then the user's English variant, then en-US. */
    fun automatic(engine: TextToSpeech): Voice? {
        val preferred = preferredLanguage()
        return usable(engine).maxWithOrNull(compareBy<Voice> { it.quality }.thenBy { languageRank(it.locale, preferred) })
    }

    /** Every voice the picker offers, best first. */
    fun options(engine: TextToSpeech): List<Option> {
        val preferred = preferredLanguage()
        return usable(engine)
            .sortedWith(compareByDescending<Voice> { it.quality }.thenByDescending { languageRank(it.locale, preferred) }.thenBy { it.name.lowercase(Locale.US) })
            .map { Option(it.name, it.name, it.locale.toLanguageTag(), it.quality) }
    }

    /** The option currently in effect (explicit choice, else the automatic pick). */
    fun effectiveOption(engine: TextToSpeech): Option? =
        current(engine)?.let { Option(it.name, it.name, it.locale.toLanguageTag(), it.quality) }

    private fun usable(engine: TextToSpeech): List<Voice> = try {
        (engine.voices ?: emptySet()).filter { v ->
            v.locale.language == "en" && !v.isNetworkConnectionRequired &&
                !v.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun preferredLanguage(): Locale {
        val device = Locale.getDefault()
        return if (device.language == "en") device else Locale.US
    }

    private fun languageRank(locale: Locale, preferred: Locale): Int = when {
        locale.language == preferred.language && locale.country == preferred.country -> 2
        locale.language == "en" && locale.country == "US" -> 1
        else -> 0
    }
}
