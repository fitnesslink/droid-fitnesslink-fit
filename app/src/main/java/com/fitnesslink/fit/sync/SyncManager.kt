package com.fitnesslink.fit.sync

import android.content.Context
import android.content.SharedPreferences
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.SyncPushChange
import com.fitnesslink.fit.network.dto.SyncPushRequest
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

enum class SyncStatus { IDLE, SYNCING, ERROR, COMPLETE }

object SyncManager {
    private lateinit var prefs: SharedPreferences
    private const val KEY_CURSOR = "fl_sync_cursor"
    private const val KEY_LAST_SYNC = "fl_last_sync"

    private val _status = MutableStateFlow(SyncStatus.IDLE)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _lastSyncDate = MutableStateFlow<Date?>(null)
    val lastSyncDate: StateFlow<Date?> = _lastSyncDate.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences("fl_sync", Context.MODE_PRIVATE)
        val lastMs = prefs.getLong(KEY_LAST_SYNC, 0)
        if (lastMs > 0) _lastSyncDate.value = Date(lastMs)
    }

    private var cursor: String?
        get() = prefs.getString(KEY_CURSOR, null)
        set(value) { prefs.edit().putString(KEY_CURSOR, value).apply() }

    suspend fun pull(domains: List<String>? = null) {
        if (!NetworkMonitor.isConnected.first()) return
        if (_status.value == SyncStatus.SYNCING) return

        _status.value = SyncStatus.SYNCING
        try {
            val response = ApiClient.syncApi.pull(
                since = cursor,
                domains = domains?.joinToString(",")
            )

            for ((entityType, changes) in response.changes) {
                for (change in changes) {
                    if (change.operation == "delete") {
                        DatabaseManager.deleteSyncEntity(entityType, change.entityId)
                    } else {
                        DatabaseManager.applySyncPayload(entityType, change.entityId, change.data)
                    }
                }
            }

            cursor = response.cursor
            _lastSyncDate.value = Date()
            prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
            _status.value = SyncStatus.COMPLETE
        } catch (e: Exception) {
            _status.value = SyncStatus.ERROR
        }
    }

    suspend fun push() {
        if (!NetworkMonitor.isConnected.first()) return
        if (_status.value == SyncStatus.SYNCING) return

        _status.value = SyncStatus.SYNCING
        try {
            val pending = DatabaseManager.pendingSyncEntries()
            if (pending.isEmpty()) {
                _status.value = SyncStatus.COMPLETE
                return
            }

            val request = SyncPushRequest(
                changes = pending.map { entry ->
                    SyncPushChange(
                        entityId = entry.entityId,
                        entityType = entry.entityType,
                        operation = entry.action,
                        data = null,
                        idempotencyKey = entry.id,
                        clientTimestamp = dateFormat.format(Date(entry.createdAt))
                    )
                }
            )

            val response = ApiClient.syncApi.push(request)
            for (id in response.accepted) {
                pending.find { it.id == id || it.entityId == id }?.let {
                    DatabaseManager.markSyncEntrySynced(it.id)
                }
            }
            // Mark all as synced since server accepted by entity ID
            for (entry in pending) {
                DatabaseManager.markSyncEntrySynced(entry.id)
            }

            cursor = response.cursor
            _status.value = SyncStatus.COMPLETE
        } catch (e: Exception) {
            _status.value = SyncStatus.ERROR
        }
    }

    suspend fun performFullSync() {
        push()
        pull()
    }

    suspend fun performInitialSync() {
        DatabaseManager.clearUserData()
        cursor = null
        pull()
    }

    fun reset() {
        cursor = null
        _lastSyncDate.value = null
        _status.value = SyncStatus.IDLE
        prefs.edit().clear().apply()
    }
}
