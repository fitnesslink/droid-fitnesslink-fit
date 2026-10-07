package com.fitnesslink.fit.calendar

import android.util.Log
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.CreateCalendarEntryRequest
import com.fitnesslink.fit.network.dto.UpdateCalendarEntryRequest
import com.fitnesslink.fit.network.dto.toCreateRequest
import com.fitnesslink.fit.network.dto.toFitnessContent
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.persistence.inProgressSessionsForWorkoutOnDay
import com.fitnesslink.fit.session.ActiveWorkoutSession
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Date
import java.util.Locale

/**
 * Calendar-entry write pipeline — port of iOS `Services/API/CalendarService.swift`
 * (create / reschedule / remove + the `SyncManager.drainCalendar` drain) and
 * the schedule helpers on `CalendarViewModel`.
 *
 * Every mutation is: local row first (so the UI redraws at once) → device
 * calendar mirror ([CalendarSyncService]) → durable `sync_queue` row under
 * [SyncDomain.CALENDAR] with an inline attempt. 2xx or a final 4xx (anything
 * but 401) marks the queue row synced; offline / 5xx / network errors leave
 * it for the drain. A server pull ([refresh]) skips rows that still have a
 * queued delete or reschedule ([DatabaseManager.pendingCalendarMutationIds]).
 *
 * Wire format: `POST /calendar` sends nulls, never empty strings
 * (`FitnessContent.toCreateRequest`), and includes `programEnrollmentId`,
 * `durationMinutes`, `programWeekNumber`, `programDayNumber`.
 */
object CalendarService {
    private const val TAG = "CalendarService"

    /** Queue tag (iOS `calendarQueueEntityType`). Paired producer ↔ drain. */
    val queueEntityType: String = SyncDomain.CALENDAR.wireName

    // Queue actions.
    private const val ACTION_CREATE = "create"
    private const val ACTION_UPDATE = "update"          // reschedule: payload RescheduleQueuePayload
    private const val ACTION_STATUS = "update-status"   // markCompleted: payload StatusQueuePayload
    private const val ACTION_DELETE = "delete"          // payload = entry id

    /** iOS `RescheduleQueuePayload` — seconds since 1970 so the drain never depends on a date formatter. */
    data class RescheduleQueuePayload(val scheduledAt: Double) {
        val date: Date get() = Date((scheduledAt * 1000).toLong())
        companion object { fun of(date: Date) = RescheduleQueuePayload(date.time / 1000.0) }
    }

    data class StatusQueuePayload(val status: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** Fires after any local calendar change (create / move / remove / refresh) — screens re-read the DB. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    @Volatile private var registered = false

    /** Register the queue drain once per process. Safe to call repeatedly. */
    fun ensureRegistered() {
        if (registered) return
        registered = true
        SyncManager.registerDrain(SyncDomain.CALENDAR) { drain() }
    }

    // MARK: - Reads

    fun entries(forDate: Date): List<FitnessContent> = DatabaseManager.calendarContent(forDate = forDate.time)

    fun entries(from: Date, to: Date): List<FitnessContent> = DatabaseManager.calendarContent(from.time, to.time)

    // MARK: - Create

    /**
     * Schedule one entry: local insert → device mirror → queued `POST /calendar`
     * with an inline attempt. An entry without a `scheduledDate` stays local
     * only (iOS skips the round-trip for unscheduled drafts).
     */
    suspend fun create(entry: FitnessContent) {
        ensureRegistered()
        val normalized = entry.copy(
            id = entry.id.lowercase(Locale.US),
            status = entry.status.ifBlank { "scheduled" }
        )
        withContext(Dispatchers.IO) {
            DatabaseManager.insertCalendarContent(normalized)
            CalendarSyncService.mirror(normalized)
        }
        notifyChanged()
        if (normalized.scheduledDate == null) return
        commit(ACTION_CREATE, normalized.id, ApiClient.gson.toJson(normalized.toCreateRequest())) {
            ApiClient.calendarApi.create(normalized.toCreateRequest())
        }
    }

    /** Convenience used by the pickers / editors: a standalone workout on [date]. */
    suspend fun scheduleWorkout(workoutId: String, title: String, date: Date, durationMinutes: Int? = null): FitnessContent {
        val entry = FitnessContent(
            id = java.util.UUID.randomUUID().toString().lowercase(Locale.US),
            title = title,
            workoutId = workoutId.lowercase(Locale.US),
            status = "scheduled",
            scheduledDate = date.time,
            durationMinutes = durationMinutes
        )
        create(entry)
        return entry
    }

    // MARK: - Reschedule

    /**
     * Move one entry to another day / time. Local row first, then the device
     * mirror, then a queued `PUT /calendar/{id}` carrying only `scheduledDate`
     * — id, program link, status and duration are untouched.
     */
    suspend fun reschedule(entryId: String, newDate: Date) {
        ensureRegistered()
        val stored = withContext(Dispatchers.IO) {
            val entry = DatabaseManager.calendarContentById(entryId) ?: return@withContext null
            DatabaseManager.updateCalendarScheduledDate(entry.id, newDate.time)
            val updated = DatabaseManager.calendarContentById(entry.id) ?: entry.copy(scheduledDate = newDate.time)
            CalendarSyncService.mirror(updated)
            updated
        } ?: return
        notifyChanged()
        commit(ACTION_UPDATE, stored.id, ApiClient.gson.toJson(RescheduleQueuePayload.of(newDate))) {
            ApiClient.calendarApi.update(stored.id, UpdateCalendarEntryRequest(scheduledDate = newDate))
        }
    }

    // MARK: - Remove

    /**
     * Full teardown of one entry: device unmirror, discard any in-progress
     * session of that workout started on the entry's day (so the Home resume
     * banner drops it too), delete the local row, queue the server DELETE.
     * Every removal path routes through here so the calendar, Today and the
     * resume banner can never disagree.
     */
    suspend fun remove(entryId: String) {
        ensureRegistered()
        val id = entryId.lowercase(Locale.US)
        val entry = withContext(Dispatchers.IO) { DatabaseManager.calendarContentById(id) }
        withContext(Dispatchers.IO) {
            if (entry != null) CalendarSyncService.unmirror(entry) else CalendarSyncService.unmirror(id)
        }
        if (entry != null) discardInProgressSessions(entry)
        withContext(Dispatchers.IO) { DatabaseManager.deleteCalendarContent(id) }
        notifyChanged()
        commit(ACTION_DELETE, id, id) { ApiClient.calendarApi.delete(id) }
    }

    /** Remove several entries with the exact per-entry teardown of [remove]; one change notification. */
    suspend fun removeAll(entryIds: Collection<String>) {
        for (id in entryIds) remove(id)
    }

    /**
     * Whole-program scope: tear down every calendar entry of one program run.
     * The enrollment record itself (`/program-enrollments/{id}/removal`,
     * `user_programs`) is the programs domain's — callers that want the run
     * gone from My Program follow up with `UserProgramStore.remove`.
     */
    suspend fun removeEnrollment(enrollmentId: String) {
        val entries = withContext(Dispatchers.IO) { DatabaseManager.calendarContentForEnrollment(enrollmentId) }
        removeAll(entries.map { it.id })
    }

    private suspend fun discardInProgressSessions(entry: FitnessContent) {
        if (entry.workoutId.isEmpty()) return
        val sessions = withContext(Dispatchers.IO) {
            DatabaseManager.inProgressSessionsForWorkoutOnDay(entry.workoutId, entry.scheduledDate)
        }
        for (session in sessions) {
            runCatching { ActiveWorkoutSession.discard(session.id) }
                .onFailure { Log.w(TAG, "discard session ${session.id} failed", it) }
        }
    }

    // MARK: - Completion

    /**
     * Mark the calendar entries for [workoutId] on [day] completed (the
     * session engine calls this when a workout finishes). Local status first,
     * then a queued `PUT /calendar/{id}` with `{status}` only.
     */
    suspend fun markCompleted(workoutId: String, day: Date) {
        ensureRegistered()
        val wanted = workoutId.lowercase(Locale.US)
        val targets = withContext(Dispatchers.IO) {
            DatabaseManager.calendarContent(forDate = day.time).filter { it.workoutId.lowercase(Locale.US) == wanted && !it.isCompleted }
        }
        if (targets.isEmpty()) return
        withContext(Dispatchers.IO) { targets.forEach { DatabaseManager.updateCalendarStatus(it.id, "completed") } }
        notifyChanged()
        for (t in targets) {
            commit(ACTION_STATUS, t.id, ApiClient.gson.toJson(StatusQueuePayload("completed"))) {
                ApiClient.calendarApi.update(t.id, UpdateCalendarEntryRequest(status = "completed"))
            }
        }
    }

    // MARK: - Pull

    /**
     * `GET /calendar/me?startDate&endDate` → upsert locally, skipping rows
     * with a queued delete / reschedule. Flushes the queue first so the
     * server can't hand back a row the user just removed.
     */
    suspend fun refresh(from: Date, to: Date) {
        ensureRegistered()
        if (!NetworkMonitor.isConnected.value) return
        runCatching { drain() }
        val remote = try {
            ApiClient.calendarApi.getEntries(FitnessContent.formatIsoDate(from.time), FitnessContent.formatIsoDate(to.time))
        } catch (e: Exception) {
            Log.i(TAG, "refresh failed — using cache (${e.javaClass.simpleName})"); return
        }
        withContext(Dispatchers.IO) {
            val pending = DatabaseManager.pendingCalendarMutationIds()
            CalendarPullGuard.filterPulled(remote.map { it.toFitnessContent() }, pending)
                .forEach { DatabaseManager.insertCalendarContent(it) }
            CalendarSyncService.backfillUnsynced()
        }
        notifyChanged()
    }

    // MARK: - Queue

    /**
     * Enqueue, then try inline. Success or a final 4xx marks the row synced;
     * offline / transient leaves it queued for [drain] (iOS `commitDelete` /
     * `commitReschedule`). Fire-and-forget so the caller's UI returns at once.
     */
    private fun commit(action: String, entityId: String, payload: String, call: suspend () -> Unit) {
        val queueId = DatabaseManager.enqueueSyncEntry(queueEntityType, entityId, action, payload)
        if (!NetworkMonitor.isConnected.value) return
        scope.launch {
            try {
                call()
                DatabaseManager.markSyncEntrySynced(queueId)
            } catch (e: Exception) {
                if (isFinalFailure(e)) {
                    Log.w(TAG, "$action $entityId: final ${e.javaClass.simpleName} — dropping")
                    DatabaseManager.markSyncEntrySynced(queueId)
                } else {
                    Log.i(TAG, "$action $entityId: transient ${e.javaClass.simpleName} — left queued")
                }
            }
        }
    }

    /** iOS semantics: 404 / 403 / other 4xx are final; network, 5xx and 401 are transient. */
    internal fun isFinalFailure(e: Throwable): Boolean = when (e) {
        is HttpException -> e.code() in 400..499 && e.code() != 401
        is ApiError.Forbidden, is ApiError.NotFound -> true
        is ApiError.ServerError -> e.statusCode in 400..499 && e.statusCode != 401
        else -> false
    }

    /** Replay queued calendar rows through the REST endpoints (iOS `SyncManager.drainCalendar`). */
    suspend fun drain() {
        SyncManager.drainQueue(SyncDomain.CALENDAR) { entry ->
            when (entry.action) {
                ACTION_CREATE -> {
                    val request = SyncManager.decodePayload<CreateCalendarEntryRequest>(entry.payload)
                        ?: throw ApiError.NotFound // undecodable → drop (iOS drops unreadable payloads)
                    ApiClient.calendarApi.create(request)
                }
                ACTION_UPDATE -> {
                    val payload = SyncManager.decodePayload<RescheduleQueuePayload>(entry.payload) ?: throw ApiError.NotFound
                    ApiClient.calendarApi.update(entry.entityId, UpdateCalendarEntryRequest(scheduledDate = payload.date))
                }
                ACTION_STATUS -> {
                    val payload = SyncManager.decodePayload<StatusQueuePayload>(entry.payload) ?: throw ApiError.NotFound
                    ApiClient.calendarApi.update(entry.entityId, UpdateCalendarEntryRequest(status = payload.status))
                }
                ACTION_DELETE -> ApiClient.calendarApi.delete(entry.entityId)
                else -> Log.w(TAG, "unknown calendar queue action '${entry.action}' — dropping")
            }
        }
    }

    private fun notifyChanged() { _changes.tryEmit(Unit) }
}
