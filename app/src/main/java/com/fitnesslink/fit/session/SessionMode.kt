package com.fitnesslink.fit.session

import java.util.Locale

/**
 * Which player the user last had a session open in (FTNSSLNK-404).
 * Persisted on the session row (`lastMode`) so Resume reopens the same
 * one. Port of iOS `SessionMode`; the raw value is the iOS spelling.
 */
enum class SessionMode(val rawValue: String) {
    PLAYLIST("playlist"),
    INTERACTIVE("interactive"),
    /** The Quick Start live canvas (Quick Start PRD §38): built and logged as it happens, never run in a player. */
    QUICK_START("quickStart");

    companion object {
        fun fromRaw(raw: String?): SessionMode? =
            raw?.let { r -> entries.firstOrNull { it.rawValue.equals(r, ignoreCase = true) || it.name.equals(r, ignoreCase = true) } }
    }
}

/**
 * A started-but-unfinished session as the resume surfaces show it — the
 * Home "Continue Where You Left Off" card and the Workout Details resume
 * card (FTNSSLNK-404). Built from the local `workout_sessions` row, so it
 * needs no player and no network. Shape per `docs/parity/CONTRACTS.md`.
 */
data class InProgressSessionEntry(
    val sessionId: String,
    val workoutId: String,
    val workoutName: String,
    /** Completed sets ÷ required sets at the last progress write; 0 when the row carries no figure. */
    val completionPercent: Int,
    /** Wall-clock seconds since the session started, measured when the entry was built. */
    val elapsedSeconds: Long,
    val lastMode: SessionMode,
    /** Epoch millis of the session start. */
    val startedAt: Long,
    val programId: String? = null,
    /** True when the row carried a completion figure (older rows predate the per-set model). */
    val hasCompletionPercent: Boolean = true
) {
    /** "62% complete"; null when the row carries no figure. */
    val progressText: String?
        get() = if (hasCompletionPercent) "$completionPercent% complete" else null

    /** "12m elapsed" / "1h 05m elapsed" / "just started", measured from the session's start on the wall clock. */
    fun elapsedText(nowMs: Long = System.currentTimeMillis()): String {
        val seconds = maxOf(0L, (nowMs - startedAt) / 1000)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        if (h > 0) return String.format(Locale.US, "%dh %02dm elapsed", h, m)
        if (m > 0) return "${m}m elapsed"
        return "just started"
    }

    /** "62% complete · 12m elapsed" (or just the elapsed part). */
    fun summaryText(nowMs: Long = System.currentTimeMillis()): String =
        listOfNotNull(progressText, elapsedText(nowMs)).joinToString(" · ")

    /**
     * Where Resume lands: a Quick Start session reopens its live canvas;
     * every player session goes back through the playlist, which re-enters
     * Interactive Mode on its own when that was the last mode (FTNSSLNK-404).
     */
    val resumeRoute: String
        get() = if (lastMode == SessionMode.QUICK_START) "quickStartCanvas/$workoutId" else "playlistSession/$workoutId"
}
