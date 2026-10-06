package com.fitnesslink.fit.model

import com.fitnesslink.fit.model.TolerantAdapterFactory.Companion.optString
import com.google.gson.JsonObject
import com.google.gson.annotations.JsonAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One calendar entry. [scheduledDate] is epoch millis locally; the wire form
 * is ISO-8601 — use [scheduledDateIso] / [parseIsoDate] at the API boundary.
 */
@JsonAdapter(FitnessContent.Adapter::class)
data class FitnessContent(
    val id: String = "",
    val title: String = "",
    val programId: String = "",
    val workoutId: String = "",
    val mealPlanId: String = "",
    val status: String = "",
    val scheduledDate: Long? = null,
    val workoutImageId: String? = null,
    val workoutThumbnailId: String? = null,
    val programImageId: String? = null,
    val programThumbnailId: String? = null,
    /**
     * Unique id for one "add this program to my calendar" action. Every entry
     * created in a single add shares it. Null for standalone workouts.
     */
    val programEnrollmentId: String? = null,
    /** Planned length of the scheduled block, in minutes. Null = fall back to the workout's estimate. */
    val durationMinutes: Int? = null,
    /**
     * Template week / day (1 = Sunday … 7 = Saturday) of the program slot this
     * entry was generated from. Fixed for the entry's lifetime — moving the
     * workout changes `scheduledDate` only.
     */
    val programWeekNumber: Int? = null,
    val programDayNumber: Int? = null,
    /** Device-calendar mirror event id. Device-local, never synced. */
    val deviceEventId: String? = null
) {
    val isCompleted: Boolean get() = status.lowercase(Locale.US) == "completed"

    val scheduledDateValue: Date? get() = scheduledDate?.let { Date(it) }

    /** ISO-8601 UTC wire form of [scheduledDate], or null. */
    val scheduledDateIso: String? get() = scheduledDate?.let { formatIsoDate(it) }

    class Adapter : TolerantAdapterFactory<FitnessContent>(FitnessContent::class.java) {
        /** Accept the ISO string the server sends and the millis the cache / queue write. */
        override fun preprocess(raw: JsonObject) {
            val element = raw.get("scheduledDate") ?: return
            if (!element.isJsonPrimitive) { raw.remove("scheduledDate"); return }
            val prim = element.asJsonPrimitive
            if (prim.isNumber) return
            val millis = parseIsoDate(raw.optString("scheduledDate"))
            if (millis == null) raw.remove("scheduledDate") else raw.addProperty("scheduledDate", millis)
        }

        /** Emit the ISO-8601 wire form; [preprocess] accepts both, so cache / queue payloads round-trip. */
        override fun postwrite(tree: JsonObject) {
            val element = tree.get("scheduledDate") ?: return
            if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
                tree.addProperty("scheduledDate", formatIsoDate(element.asLong))
            }
            // Device-local only — never put on the wire.
            tree.remove("deviceEventId")
        }
    }

    companion object {
        private fun isoFormatter(): SimpleDateFormat =
            SimpleDateFormat(ModelGson.ISO_DATE_TIME, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun formatIsoDate(millis: Long): String = isoFormatter().format(Date(millis))

        /** ISO-8601 (with or without fractional seconds / offset) or epoch millis → millis. */
        fun parseIsoDate(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            return ModelGson.LenientDateAdapter().parse(raw)?.time
        }
    }
}
