package com.fitnesslink.fit.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.calendarContentWithDeviceEvents
import com.fitnesslink.fit.persistence.calendarContentWithoutDeviceEvents
import java.util.TimeZone

/**
 * One-way mirror of `calendar_content` rows into the device Calendar
 * Provider — port of iOS `Services/CalendarSyncService.swift` (EventKit).
 * Our local DB / server is the source of truth; the device calendar is a
 * downstream replica we write to, never read back from.
 *
 * - `CalendarService` mirrors inline on every create / move / delete.
 * - `syncAll` (SyncScheduler + the settings screen) backfills rows that
 *   have no mirror yet.
 * - The event id lives on the row (`calendar_content.deviceEventId`); the
 *   pre-parity SharedPreferences slot is still read so events created by
 *   the old bulk sync can be updated / removed.
 * - The user's chosen target calendar is remembered in SharedPreferences
 *   ([targetCalendarId]); null falls back to the primary writable calendar.
 *
 * No-op when the toggle is off, permission is missing, the row has no
 * `scheduledDate`, or no writable calendar can be resolved.
 *
 * A `Context` is captured on the first call that receives one
 * ([attach], or any of the `(context)` overloads — the Calendar tab and the
 * settings screen both do this) so the inline mirror from `CalendarService`
 * has something to write with.
 */
object CalendarSyncService {
    private const val TAG = "CalendarSync"
    private const val PREFS_NAME = "fl_calendar_sync"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TARGET_CALENDAR = "target_calendar_id"
    private const val EVENT_ID_PREFIX = "event_id_"
    private const val DEFAULT_DURATION_MINUTES = 60

    /** A calendar the user may mirror into (for the Preferences picker). */
    data class DeviceCalendar(val id: Long, val displayName: String, val accountName: String, val color: Int?)

    @Volatile private var appContext: Context? = null

    /** Remember the application context so context-free callers (`CalendarService`) can mirror. */
    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // MARK: - Settings

    fun isEnabled(context: Context): Boolean { attach(context); return prefs(context).getBoolean(KEY_ENABLED, false) }

    val isEnabled: Boolean get() = appContext?.let { isEnabled(it) } ?: false

    /**
     * Flip the toggle. Turning it off tears down every event we created
     * (iOS `disable()`), so a later re-enable doesn't leave stale duplicates.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        attach(context)
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) tearDownAll(context)
    }

    fun hasPermission(context: Context): Boolean {
        attach(context)
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    /** The calendar the user picked as the mirror target; null = primary writable calendar. */
    var targetCalendarId: Long?
        get() = appContext?.let { targetCalendarId(it) }
        set(value) { appContext?.let { setTargetCalendarId(it, value) } }

    fun targetCalendarId(context: Context): Long? {
        attach(context)
        val raw = prefs(context).getLong(KEY_TARGET_CALENDAR, -1L)
        return if (raw == -1L) null else raw
    }

    fun setTargetCalendarId(context: Context, id: Long?) {
        attach(context)
        prefs(context).edit().apply { if (id == null) remove(KEY_TARGET_CALENDAR) else putLong(KEY_TARGET_CALENDAR, id) }.apply()
    }

    /** Calendars the current account can write to, for the target picker. */
    fun writableCalendars(context: Context): List<DeviceCalendar> {
        attach(context)
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR
        )
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection,
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE"
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(DeviceCalendar(
                            id = c.getLong(0),
                            displayName = c.getString(1) ?: "",
                            accountName = c.getString(2) ?: "",
                            color = if (c.isNull(3)) null else c.getInt(3)
                        ))
                    }
                }
            } ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    // MARK: - Bulk

    /**
     * Mirror every scheduled row (idempotent — rows with an event id update
     * in place). Returns the number of events written.
     */
    fun syncAll(context: Context): Int {
        attach(context)
        if (!isEnabled(context) || !hasPermission(context)) return 0
        val calendarId = resolvedCalendarId(context) ?: return 0
        var written = 0
        for (item in DatabaseManager.calendarContent()) {
            if (item.scheduledDate == null) continue
            if (upsertEvent(context, calendarId, item)) written++
        }
        return written
    }

    /** Rows the server pull brought in while we weren't looking (iOS `backfillUnsynced`). */
    fun backfillUnsynced(context: Context? = appContext) {
        if (context == null || !isEnabled(context) || !hasPermission(context)) return
        val calendarId = resolvedCalendarId(context) ?: return
        for (item in DatabaseManager.calendarContentWithoutDeviceEvents()) upsertEvent(context, calendarId, item)
    }

    /** Drop every event we created (iOS `tearDownAll`). */
    fun tearDownAll(context: Context) {
        attach(context)
        for (entry in DatabaseManager.calendarContentWithDeviceEvents()) unmirror(entry)
    }

    // MARK: - Inline mirror (CalendarService)

    /** Create or update the device event for this row. Safe to call without a context attached (no-op). */
    fun mirror(content: FitnessContent) {
        val context = appContext ?: return
        if (!isEnabled(context) || !hasPermission(context)) return
        val calendarId = resolvedCalendarId(context) ?: return
        // Re-read so the stored event id travels with the row (a caller may pass a fresh copy).
        val stored = DatabaseManager.calendarContentById(content.id) ?: content
        upsertEvent(context, calendarId, stored)
    }

    /** Drop the device event for this row. Call before deleting the row, or pass the row itself. */
    fun unmirror(contentId: String) {
        val entry = DatabaseManager.calendarContentById(contentId)
        if (entry != null) unmirror(entry) else appContext?.let { removeEvent(it, contentId) }
    }

    fun unmirror(content: FitnessContent) {
        val context = appContext ?: return
        removeEvent(context, content.id, content.deviceEventId?.toLongOrNull())
    }

    /** Insert or update one row's event. Honours `durationMinutes` (then the workout estimate, then 60 min). */
    fun upsertEvent(context: Context, calendarId: Long, content: FitnessContent): Boolean {
        attach(context)
        if (!hasPermission(context)) return false
        val start = content.scheduledDate ?: return false
        val end = start + durationMinutes(content) * 60_000L
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, content.title.ifBlank { "Workout" })
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.DESCRIPTION, "Open in FitnessLink: fitnesslink://workout/${content.workoutId}")
        }
        val existingId = content.deviceEventId?.toLongOrNull() ?: legacyEventId(context, content.id)
        return try {
            if (existingId != null) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
                val updated = context.contentResolver.update(uri, values, null, null) > 0
                if (updated) {
                    if (content.deviceEventId == null) persistEventId(context, content.id, existingId)
                    true
                } else {
                    // The user deleted the event in their calendar app — relink by creating a fresh one.
                    insertEvent(context, content.id, values)
                }
            } else {
                insertEvent(context, content.id, values)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "mirror failed for ${content.id}", e); false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "mirror failed for ${content.id}", e); false
        }
    }

    /** Remove the event for a content row, if one was created. */
    fun removeEvent(context: Context, contentId: String, knownEventId: Long? = null) {
        attach(context)
        val id = knownEventId ?: DatabaseManager.calendarContentById(contentId)?.deviceEventId?.toLongOrNull() ?: legacyEventId(context, contentId)
        if (id != null && hasPermission(context)) {
            try {
                context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
            } catch (e: SecurityException) {
                Log.w(TAG, "unmirror failed for $contentId", e)
            }
        }
        prefs(context).edit().remove(EVENT_ID_PREFIX + contentId).apply()
        runCatching { DatabaseManager.updateCalendarDeviceEventId(contentId, null) }
    }

    // MARK: - Internals

    private fun insertEvent(context: Context, contentId: String, values: ContentValues): Boolean {
        val newUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return false
        val newId = ContentUris.parseId(newUri)
        persistEventId(context, contentId, newId)
        return true
    }

    private fun persistEventId(context: Context, contentId: String, eventId: Long) {
        runCatching { DatabaseManager.updateCalendarDeviceEventId(contentId, eventId.toString()) }
        prefs(context).edit().remove(EVENT_ID_PREFIX + contentId).apply()
    }

    private fun legacyEventId(context: Context, contentId: String): Long? {
        val raw = prefs(context).getLong(EVENT_ID_PREFIX + contentId, -1L)
        return if (raw == -1L) null else raw
    }

    /** FTNSSLNK-329: the entry's planned duration, else the workout's estimate, else 60 minutes. */
    private fun durationMinutes(content: FitnessContent): Int {
        content.durationMinutes?.takeIf { it > 0 }?.let { return it }
        val workout = if (content.workoutId.isNotEmpty()) DatabaseManager.workout(content.workoutId) else null
        return CalendarDurationChips.resolvedDurationMinutes(content, workout?.time, workout?.estimatedContentMinutes)
            ?: DEFAULT_DURATION_MINUTES
    }

    /** The user's saved calendar if it still exists, else the primary writable calendar. */
    private fun resolvedCalendarId(context: Context): Long? {
        val calendars = writableCalendars(context)
        if (calendars.isEmpty()) return null
        val saved = targetCalendarId(context)
        return calendars.firstOrNull { it.id == saved }?.id ?: calendars.first().id
    }
}
