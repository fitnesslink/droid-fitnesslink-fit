package com.fitnesslink.fit.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.persistence.DatabaseManager
import java.util.TimeZone

/**
 * FA-96 — mirrors scheduled FitnessContent rows into the device's
 * Calendar Provider so workouts surface alongside other commitments.
 *
 * Strategy:
 *   • Settings toggle (`isEnabled`) gated on runtime permission.
 *   • Per-content event id stored in SharedPreferences so updates
 *     and deletes can target the previously-created event without
 *     a schema migration.
 *   • Writes against the user's first writable calendar (account
 *     picker is left as a follow-up — single calendar is the common
 *     case for personal devices).
 */
object CalendarSyncService {
    private const val PREFS_NAME = "fl_calendar_sync"
    private const val KEY_ENABLED = "enabled"
    private const val EVENT_ID_PREFIX = "event_id_"
    private const val DEFAULT_DURATION_MS = 60L * 60 * 1000 // 1 hour fallback

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Synchronize every scheduled workout into the device calendar. Idempotent —
     * an event id stored locally lets us update in place rather than create
     * duplicates on every run. Returns the number of events written.
     */
    fun syncAll(context: Context): Int {
        if (!isEnabled(context) || !hasPermission(context)) return 0
        val calendarId = primaryWritableCalendarId(context) ?: return 0
        val items = DatabaseManager.calendarContent().filter {
            it.workoutId.isNotEmpty() && it.scheduledDate != null
        }
        var written = 0
        for (item in items) {
            if (upsertEvent(context, calendarId, item)) written++
        }
        return written
    }

    /** Insert or update one workout-content row. */
    fun upsertEvent(context: Context, calendarId: Long, content: FitnessContent): Boolean {
        if (!hasPermission(context)) return false
        val start = content.scheduledDate ?: return false
        val end = start + DEFAULT_DURATION_MS
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, content.title.ifBlank { "FitnessLink workout" })
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.DESCRIPTION, "Scheduled via FitnessLink")
        }

        val existingId = eventIdFor(context, content.id)
        return try {
            if (existingId != null) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
                context.contentResolver.update(uri, values, null, null) > 0
            } else {
                val newUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                val newId = newUri?.let { ContentUris.parseId(it) }
                if (newId != null) {
                    setEventId(context, content.id, newId)
                    true
                } else false
            }
        } catch (_: SecurityException) {
            false
        }
    }

    /** Remove the calendar event for a content row, if one was created. */
    fun removeEvent(context: Context, contentId: String) {
        if (!hasPermission(context)) return
        val id = eventIdFor(context, contentId) ?: return
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
            context.contentResolver.delete(uri, null, null)
        } catch (_: SecurityException) {
        }
        prefs(context).edit().remove(EVENT_ID_PREFIX + contentId).apply()
    }

    private fun primaryWritableCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun eventIdFor(context: Context, contentId: String): Long? {
        val raw = prefs(context).getLong(EVENT_ID_PREFIX + contentId, -1L)
        return if (raw == -1L) null else raw
    }

    private fun setEventId(context: Context, contentId: String, eventId: Long) {
        prefs(context).edit().putLong(EVENT_ID_PREFIX + contentId, eventId).apply()
    }
}
