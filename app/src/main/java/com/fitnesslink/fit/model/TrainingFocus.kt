package com.fitnesslink.fit.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Quick Start PRD §8–9: what the user is training today. The presets are
 * the suggested taxonomy (it can grow with the movement taxonomy); a
 * custom focus is any trimmed name the user types ("Coach Session",
 * "Glute Day"). Only the name is persisted — on the local session row
 * (`workout_sessions.trainingFocus`) — so [resolve] rebuilds the preset
 * glyph for a known name and falls back to the custom glyph. Port of iOS
 * `TrainingFocus`; [symbol] is a Material icon key, mapped in the UI.
 */
data class TrainingFocus(val name: String, val symbol: String = CUSTOM_SYMBOL) {
    val id: String get() = name.lowercase(Locale.US)

    val isPreset: Boolean get() = presets.any { it.name.equals(name, ignoreCase = true) }

    companion object {
        const val CUSTOM_SYMBOL = "sparkles"

        val presets: List<TrainingFocus> = listOf(
            TrainingFocus("Lower Body", "lowerBody"),
            TrainingFocus("Upper Body", "upperBody"),
            TrainingFocus("Full Body", "fullBody"),
            TrainingFocus("Legs", "legs"),
            TrainingFocus("Push", "push"),
            TrainingFocus("Pull", "pull"),
            TrainingFocus("Chest", "chest"),
            TrainingFocus("Back", "back"),
            TrainingFocus("Shoulders", "shoulders"),
            TrainingFocus("Arms", "arms"),
            TrainingFocus("Core", "core"),
            TrainingFocus("Conditioning", "conditioning"),
            TrainingFocus("Mobility", "mobility"),
            TrainingFocus("Recovery", "recovery"),
            TrainingFocus("Other", "other")
        )

        /** A user-typed focus; null when the name is blank. A preset typed in any case is that preset. */
        fun custom(raw: String): TrainingFocus? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            presets.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
            return TrainingFocus(trimmed, CUSTOM_SYMBOL)
        }

        /** The stored name back to a focus: a preset when it matches, else a custom focus with that name. */
        fun resolve(name: String): TrainingFocus = custom(name) ?: TrainingFocus("Other", "other")

        /** PRD §9: the automatic workout name — focus plus the day: `Lower Body — Oct 5`. */
        fun defaultWorkoutName(focus: TrainingFocus, on: Date = Date()): String {
            val df = SimpleDateFormat("MMM d", Locale.US)
            return "${focus.name} — ${df.format(on)}"
        }
    }
}
