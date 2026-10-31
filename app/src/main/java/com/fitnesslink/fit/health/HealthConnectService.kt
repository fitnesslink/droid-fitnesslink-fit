package com.fitnesslink.fit.health

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Read-only Health Connect access — the Android counterpart of iOS
 * `HealthKitService`'s Progress reads (Progress PRD §9, Navigation PRD §6.3).
 *
 * Steps, active calories, exercise minutes, sleep, resting heart rate, HRV
 * and body fat feed the Progress tab and the Health-backed goals. Nothing is
 * ever written. Aggregate reads let Health Connect de-duplicate overlapping
 * sources the way its own app does; every read returns only days that
 * actually have data, so callers must never treat a missing day as zero.
 *
 * "Connected" is the user's Connect choice in Connected Apps **and** at
 * least one read permission still granted. Unlike HealthKit, Android reports
 * what was granted, so revoking in Health Connect's settings disconnects too.
 * [refresh] re-reads the grants (app start, every foreground, before a
 * Progress load); [isConnected] serves the cached answer to synchronous callers.
 */
object HealthConnectService {
    private const val TAG = "HealthConnect"
    private const val PREFS = "health_connect"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_FIRST_GRANT = "first_grant_ms"
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

    /** How far back an All Time read goes, at most. */
    private const val MAX_HISTORY_DAYS = 400L
    /**
     * Without READ_HEALTH_DATA_HISTORY, Health Connect serves data from 30
     * days before the first grant onward — and an aggregate whose range
     * starts any earlier comes back empty for *every* bucket (seen on API 35).
     */
    private const val GRANT_HISTORY_DAYS = 30L
    private const val READ_TIMEOUT_MS = 15_000L

    enum class Availability { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, UNAVAILABLE }

    /** Per-day series [daily] can read. */
    enum class Daily { STEPS, ACTIVE_CALORIES, ACTIVE_MINUTES, RESTING_HEART_RATE, HRV, SLEEP_SECONDS }

    private val recordTypes: List<KClass<out Record>> = listOf(
        StepsRecord::class, ActiveCaloriesBurnedRecord::class, ExerciseSessionRecord::class, SleepSessionRecord::class,
        RestingHeartRateRecord::class, HeartRateVariabilityRmssdRecord::class, BodyFatRecord::class
    )

    /** Every permission FitnessLink asks for — one read per type above (mirrored in the manifest). */
    val readPermissions: Set<String> = recordTypes.map { HealthPermission.getReadPermission(it) }.toSet()

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    @Volatile private var granted: Set<String> = emptySet()
    private val _connected = MutableStateFlow(false)
    /** Observable [isConnected] for screens that should flip when the connection changes. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun attach(context: Context) {
        appContext = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private var userEnabled: Boolean
        get() = prefs?.getBoolean(KEY_ENABLED, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply() }

    val isConnected: Boolean get() = userEnabled && granted.isNotEmpty()

    fun availability(context: Context? = appContext): Availability {
        val ctx = context ?: return Availability.UNAVAILABLE
        return when (runCatching { HealthConnectClient.getSdkStatus(ctx, PROVIDER_PACKAGE) }.getOrNull()) {
            HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_INSTALL_OR_UPDATE
            else -> Availability.UNAVAILABLE
        }
    }

    private fun client(): HealthConnectClient? {
        val ctx = appContext ?: return null
        if (availability(ctx) != Availability.AVAILABLE) return null
        return runCatching { HealthConnectClient.getOrCreate(ctx, PROVIDER_PACKAGE) }.getOrNull()
    }

    /** Re-reads which of [readPermissions] are granted. Safe to call often. */
    suspend fun refresh(): Boolean {
        val client = client()
        granted = if (client == null) emptySet()
        else runCatching { client.permissionController.getGrantedPermissions() intersect readPermissions }
            .onFailure { Log.w(TAG, "getGrantedPermissions failed", it) }
            .getOrDefault(granted)
        // First time we see a grant ≈ when it happened; a later read can't reach further back than 30 days before it.
        if (granted.isEmpty()) prefs?.edit()?.remove(KEY_FIRST_GRANT)?.apply()
        else if (prefs?.contains(KEY_FIRST_GRANT) == false) prefs?.edit()?.putLong(KEY_FIRST_GRANT, System.currentTimeMillis())?.apply()
        _connected.value = isConnected
        return isConnected
    }

    /** [refresh] for synchronous callers already off the main thread (goal progress passes). */
    fun refreshBlocking(): Boolean = runBlocking { withTimeoutOrNull(READ_TIMEOUT_MS) { refresh() } ?: isConnected }

    /** Launches Health Connect's permission screen for [readPermissions]; the result is the granted set. */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract(PROVIDER_PACKAGE)

    /** Connect result: connected when any read was granted. Declining everything leaves it disconnected. */
    suspend fun onPermissionResult(result: Set<String>): Boolean {
        if ((result intersect readPermissions).isNotEmpty()) userEnabled = true
        return refresh()
    }

    /**
     * Stops reading and revokes every Health Connect permission FitnessLink
     * holds (iOS can only forget the choice; Android can revoke). Android
     * applies the revocation when the process ends (verified on API 35), so
     * clearing the opt-in is what disconnects immediately.
     */
    suspend fun disconnect() {
        userEnabled = false
        client()?.let { c -> runCatching { c.permissionController.revokeAllPermissions() }.onFailure { Log.w(TAG, "revoke failed", it) } }
        refresh()
    }

    /** Health Connect's own settings, where the user picks exactly what FitnessLink can read. */
    fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    /** Play Store page that installs / updates the Health Connect provider (Android 13 and lower). */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", appContext?.packageName)
    }

    // MARK: - Reads

    /** Earliest instant a read covers: [from] clamped to [MAX_HISTORY_DAYS]. */
    fun clampStart(from: Instant, now: Instant = Instant.now()): Instant =
        maxOf(from, now.minusSeconds(MAX_HISTORY_DAYS * 86_400))

    /** Start of the first whole local day Health Connect will serve (see [GRANT_HISTORY_DAYS]); null = unknown. */
    private fun readableSince(zone: ZoneId): Instant? {
        val firstGrant = prefs?.takeIf { it.contains(KEY_FIRST_GRANT) }?.getLong(KEY_FIRST_GRANT, 0L) ?: return null
        return Instant.ofEpochMilli(firstGrant).minusSeconds(GRANT_HISTORY_DAYS * 86_400)
            .atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    }

    /**
     * Per-day values in [from, to) keyed by local date — sums for steps,
     * active kcal and exercise minutes; means for resting HR and HRV (ms);
     * seconds asleep for sleep (keyed by the morning the night ended).
     * Empty when not connected, not permitted or on error.
     */
    suspend fun daily(kind: Daily, from: Instant, to: Instant, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Double> {
        val start = readableSince(zone)?.let { maxOf(from, it) } ?: from
        if (!isConnected || !to.isAfter(start)) return emptyMap()
        val client = client() ?: return emptyMap()
        return withTimeoutOrNull(READ_TIMEOUT_MS) {
            runCatching {
                when (kind) {
                    Daily.STEPS -> aggregateDaily(client, StepsRecord.COUNT_TOTAL, start, to, zone) { it.toDouble() }
                    Daily.ACTIVE_CALORIES -> aggregateDaily(client, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, start, to, zone) { it.inKilocalories }
                    Daily.ACTIVE_MINUTES -> aggregateDaily(client, ExerciseSessionRecord.EXERCISE_DURATION_TOTAL, start, to, zone) { it.seconds / 60.0 }
                    Daily.RESTING_HEART_RATE -> aggregateDaily(client, RestingHeartRateRecord.BPM_AVG, start, to, zone) { it.toDouble() }
                    // HRV has no aggregate metric. Health Connect stores RMSSD (iOS reads SDNN): both in ms, not interchangeable.
                    Daily.HRV -> HealthConnectMath.dailyAverages(
                        readAll(client, HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(start, to))
                            .map { it.time to it.heartRateVariabilityMillis },
                        zone
                    )
                    Daily.SLEEP_SECONDS -> sleepByNight(client, start, to, zone)
                }
            }.onFailure { Log.w(TAG, "daily $kind read failed", it) }.getOrDefault(emptyMap())
        } ?: emptyMap<LocalDate, Double>().also { Log.w(TAG, "daily $kind read timed out") }
    }

    /** Most recent body-fat reading (percent, 0–100) at or before [before]. */
    suspend fun latestBodyFat(before: Instant): Pair<Double, Instant>? {
        if (!isConnected) return null
        val client = client() ?: return null
        return withTimeoutOrNull(READ_TIMEOUT_MS) {
            runCatching {
                client.readRecords(
                    ReadRecordsRequest(BodyFatRecord::class, TimeRangeFilter.before(before), ascendingOrder = false, pageSize = 1)
                ).records.firstOrNull()?.let { it.percentage.value to it.time }
            }.onFailure { Log.w(TAG, "body fat read failed", it) }.getOrNull()
        }
    }

    private suspend fun <T : Any> aggregateDaily(
        client: HealthConnectClient,
        metric: AggregateMetric<T>,
        from: Instant,
        to: Instant,
        zone: ZoneId,
        value: (T) -> Double
    ): Map<LocalDate, Double> {
        // Period slicing needs a local-time filter; start on a day boundary so each bucket is one calendar day.
        val start = from.atZone(zone).toLocalDate().atStartOfDay()
        val end = to.atZone(zone).toLocalDateTime()
        val groups = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(setOf(metric), TimeRangeFilter.between(start, end), Period.ofDays(1))
        )
        return groups.mapNotNull { group -> group.result[metric]?.let { group.startTime.toLocalDate() to value(it) } }.toMap()
    }

    private suspend fun sleepByNight(client: HealthConnectClient, from: Instant, to: Instant, zone: ZoneId): Map<LocalDate, Double> {
        val asleepStages = setOf(
            SleepSessionRecord.STAGE_TYPE_SLEEPING, SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_DEEP, SleepSessionRecord.STAGE_TYPE_REM
        )
        val spans = readAll(client, SleepSessionRecord::class, TimeRangeFilter.between(from, to)).flatMap { session ->
            // A session without stages is all sleep; with stages only the asleep ones count (iOS excludes in-bed / awake).
            if (session.stages.isEmpty()) listOf(HealthConnectMath.Span(session.startTime, session.endTime))
            else session.stages.filter { it.stage in asleepStages }.map { HealthConnectMath.Span(it.startTime, it.endTime) }
        }
        return HealthConnectMath.sleepByNight(spans, zone)
    }

    private suspend fun <T : Record> readAll(client: HealthConnectClient, type: KClass<T>, filter: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, filter, pageSize = 1000, pageToken = token))
            out += response.records
            token = response.pageToken
        } while (token != null && out.size < 20_000)
        return out
    }
}
