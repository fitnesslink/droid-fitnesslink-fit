package com.fitnesslink.fit.programs

import android.util.Log
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.media.MediaLayer
import com.fitnesslink.fit.model.ModelGson
import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.ProgramEnrollmentRecord
import com.fitnesslink.fit.network.dto.ProgramEnrollmentRemovalRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.sync.SyncDomain
import com.fitnesslink.fit.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Port of iOS `ProgramEnrollmentService`: program enrollment ("download")
 * + removal history for contributor payouts (FTNSSLNK-164/166). Writes are
 * append-only and payout-critical, so every record is durably queued in
 * `sync_queue` under [SyncDomain.PROGRAM_ENROLLMENTS] and replayed by the
 * registered drain until the server answers 2xx (a 4xx other than 401 is
 * final and drops the row — `SyncManager.drainQueue` semantics).
 */
object ProgramEnrollmentService {
    private const val TAG = "ProgramEnrollment"
    private const val ACTION_CREATE = "create"
    private const val ACTION_REMOVE = "remove"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var registered = false

    /** Register the queue drain once per process. Safe to call repeatedly. */
    fun ensureRegistered() {
        if (registered) return
        registered = true
        SyncManager.registerDrain(SyncDomain.PROGRAM_ENROLLMENTS) { drain() }
    }

    /** Capture an "add to calendar" enrollment: build the denormalized record, queue it, kick a best-effort drain. */
    fun recordAdd(enrollmentId: String, program: Program, startDate: Date, workoutCount: Int) {
        ensureRegistered()
        val record = buildRecord(enrollmentId, program, startDate, workoutCount)
        DatabaseManager.enqueueSyncEntry(
            SyncManager.PROGRAM_ENROLLMENT_TYPE, enrollmentId, ACTION_CREATE, ModelGson.instance.toJson(record)
        )
        kickDrain()
    }

    /** Capture the removal of one enrollment instance (the server flips the row to removed, never deletes). */
    fun recordRemoval(enrollmentId: String) {
        ensureRegistered()
        DatabaseManager.enqueueSyncEntry(SyncManager.PROGRAM_ENROLLMENT_TYPE, enrollmentId, ACTION_REMOVE, "")
        kickDrain()
    }

    internal fun buildRecord(enrollmentId: String, program: Program, startDate: Date, workoutCount: Int) =
        ProgramEnrollmentRecord(
            enrollmentId = enrollmentId,
            userId = AuthManager.currentUser.value?.id?.toString() ?: "",
            programId = program.id,
            contributorId = program.contributorId?.toString(),
            startDate = startDate,
            programName = program.name,
            splitId = program.splitId?.toString(),
            weeks = program.weeks,
            workoutCount = workoutCount,
            source = "Android",
            appVersion = appVersion(),
            status = "active"
        )

    private fun appVersion(): String? = runCatching {
        val context = MediaLayer.requireContext()
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    private fun kickDrain() {
        scope.launch { runCatching { SyncManager.drain(SyncDomain.PROGRAM_ENROLLMENTS) } }
    }

    /** Queue walk (iOS `SyncManager.drainProgramEnrollments`). */
    suspend fun drain() {
        SyncManager.drainQueue(SyncDomain.PROGRAM_ENROLLMENTS) { entry ->
            when (entry.action) {
                ACTION_CREATE -> {
                    val record = SyncManager.decodePayload<ProgramEnrollmentRecord>(entry.payload)
                    if (record == null) {
                        Log.w(TAG, "dropping undecodable enrollment record ${entry.entityId}")
                        return@drainQueue
                    }
                    ApiClient.programApi.recordEnrollment(record)
                }
                ACTION_REMOVE ->
                    ApiClient.programApi.recordEnrollmentRemoval(entry.entityId, ProgramEnrollmentRemovalRequest(entry.entityId))
                else -> Log.w(TAG, "unknown enrollment action ${entry.action} — dropping")
            }
        }
    }
}
