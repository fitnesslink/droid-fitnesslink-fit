package com.fitnesslink.fit.session

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local user preferences for session cues (port of iOS `SettingsStore`'s session half). Backed by
 * `SharedPreferences` once [attach]ed (the session screens attach on first composition); until then
 * the in-memory defaults apply. Device-level, never synced.
 */
object SessionSettings {
    private const val PREFS = "fl_session_settings"
    private const val KEY_CUES = "interval_cues_enabled"
    private const val KEY_VOICE = "interval_voice_cues_enabled"
    private const val KEY_HAPTICS = "interval_haptics_enabled"
    private const val KEY_VIDEO_AUDIO = "session_video_audio_enabled"
    private const val KEY_HINT = "playlist_interactive_hint_announced"
    private const val KEY_COACH_VOICE = "coach_voice_identifier"

    private var prefs: SharedPreferences? = null
    /** Application context captured by [attach]; used for best-effort device-calendar mirroring. */
    var appContext: Context? = null
        private set
    private val _version = MutableStateFlow(0)
    /** Bumped on every write so Compose readers refresh. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun bool(key: String, default: Boolean): Boolean = prefs?.getBoolean(key, default) ?: default
    private fun put(key: String, value: Boolean) { prefs?.edit()?.putBoolean(key, value)?.apply(); _version.value += 1 }

    /** Countdown beeps + transition dings / chimes ("Countdown Beeps"). */
    var intervalCuesEnabled: Boolean
        get() = bool(KEY_CUES, true)
        set(value) = put(KEY_CUES, value)

    /** Spoken coach phrases plus the 3-2-1 into work ("Voice Cues"). */
    var intervalVoiceCuesEnabled: Boolean
        get() = bool(KEY_VOICE, true)
        set(value) = put(KEY_VOICE, value)

    var intervalHapticsEnabled: Boolean
        get() = bool(KEY_HAPTICS, true)
        set(value) = put(KEY_HAPTICS, value)

    /** Audio for exercise demo videos during a session. Off = videos start muted. */
    var sessionVideoAudioEnabled: Boolean
        get() = bool(KEY_VIDEO_AUDIO, true)
        set(value) = put(KEY_VIDEO_AUDIO, value)

    /** Set once the playlist has spoken the "interactive mode is up here" tip. */
    var didAnnounceInteractiveModeHint: Boolean
        get() = bool(KEY_HINT, false)
        set(value) = put(KEY_HINT, value)

    /** TTS voice name chosen in Preferences ("Coach Voice"); null = automatic (best installed English voice). */
    var coachVoiceIdentifier: String?
        get() = prefs?.getString(KEY_COACH_VOICE, null)
        set(value) {
            prefs?.edit()?.apply { if (value == null) remove(KEY_COACH_VOICE) else putString(KEY_COACH_VOICE, value) }?.apply()
            CoachVoicePrefs.invalidate()
            _version.value += 1
        }

    /** The live channel switches for the cue policy. */
    fun policy(isMasterMuted: Boolean): SessionCuePolicy = SessionCuePolicy(
        isMasterMuted = isMasterMuted,
        soundsEnabled = intervalCuesEnabled,
        voiceEnabled = intervalVoiceCuesEnabled,
        hapticsEnabled = intervalHapticsEnabled
    )
}
