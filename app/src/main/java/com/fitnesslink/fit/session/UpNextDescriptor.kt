package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.IntervalMath

/**
 * The single "Up Next" model for Interactive Mode (FTNSSLNK-392): the next activity the user is
 * expected to perform, with one canonical [title] so every surface says the same thing:
 *
 * - next set of the same exercise → `Set 2 — Ab Crunch`
 * - next exercise → `Barbell Squat · 3 × 8`
 * - next member of a superset / circuit round → `A2 Bench Press · 10 reps`
 * - next round → `Round 2 — A1 Goblet Squat`
 * - next interval work phase → `Work · Burpees · 20 s`
 */
data class UpNextDescriptor(
    val taskId: String,
    val name: String,
    val mediaId: String? = null,
    /** Compact prescription (`3 × 8`, `30s`, `4 rounds × 12`, `HIIT · 5:45`) for the next-exercise form. */
    val prescription: String? = null,
    val kind: Kind,
    /** Secondary counter for the card ("Set 2 of 3", "Round 2 of 4"). */
    val detail: String? = null,
    /** Seconds until it starts while a countdown runs toward it. */
    val startsInSeconds: Int? = null
) {
    sealed class Kind {
        data class NextSet(val set: Int, val totalSets: Int) : Kind()
        object NextExercise : Kind()
        /** [label] is the member's position in its group ("A2"). */
        data class NextMember(val label: String) : Kind()
        data class NextRound(val round: Int, val totalRounds: Int, val label: String?) : Kind()
        /** [phase] is the runner phase the next activity is ("Work"). */
        data class Interval(val phase: String, val seconds: Int) : Kind()
    }

    /** The one line every Up Next surface renders. */
    val title: String
        get() = when (val k = kind) {
            is Kind.NextSet -> "Set ${k.set} — $name"
            Kind.NextExercise -> prescription?.let { "$name · $it" } ?: name
            is Kind.NextMember -> {
                val head = if (k.label.isEmpty()) name else "${k.label} $name"
                prescription?.let { "$head · $it" } ?: head
            }
            is Kind.NextRound -> {
                val member = k.label?.let { "$it $name" } ?: name
                "Round ${k.round} — $member"
            }
            is Kind.Interval -> "${k.phase} · $name · ${intervalSeconds(k.seconds)}"
        }

    /** "Starts in 12s" / "Starts in 1:00" while a countdown runs. */
    val startsInText: String?
        get() {
            val s = startsInSeconds ?: return null
            if (s <= 0) return null
            return if (s < 60) "Starts in ${s}s" else "Starts in ${IntervalMath.formatTime(s)}"
        }

    companion object {
        /** `20 s` under a minute, `10:00` from a minute up. */
        fun intervalSeconds(seconds: Int): String =
            if (seconds < 60) "${maxOf(0, seconds)} s" else IntervalMath.formatTime(seconds)
    }
}
