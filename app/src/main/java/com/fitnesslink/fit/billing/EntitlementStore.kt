package com.fitnesslink.fit.billing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.Entitlement
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

/** What the gate renders (CONTRACTS Wave 4 E). */
sealed class EntitlementState {
    /** No answer yet (no trustworthy cache, first refresh in flight) — show the splash. */
    data object Resolving : EntitlementState()
    data class Entitled(override val entitlement: Entitlement) : EntitlementState()
    /** The server said no (or the cache lapsed and we failed closed). */
    data class NotEntitled(override val entitlement: Entitlement = Entitlement.NONE) : EntitlementState()
    /** Could not reach the server and nothing cached is trustworthy — fail closed with an explanation. */
    data class Error(val message: String) : EntitlementState()

    /** The entitlement this state carries (`Entitlement.NONE` while resolving / on error). */
    open val entitlement: Entitlement get() = Entitlement.NONE
    val isEntitled: Boolean get() = this is Entitled
}

/**
 * Pure cache-trust rules (iOS `EntitlementStore.isCacheStale` / `isNearExpiry`),
 * separated so they are unit-testable without Android.
 */
object EntitlementTrust {
    const val NEAR_EXPIRY_WINDOW_MS = 24L * 60 * 60 * 1000

    /**
     * A cached entitlement is trusted only until the period it describes has
     * actually ended; a cached "not entitled" tells us nothing useful.
     */
    fun isTrusted(cached: Entitlement?, nowMs: Long): Boolean {
        if (cached == null || !cached.isEntitled) return false
        val end = cached.currentPeriodEnd ?: return true
        return end.time > nowMs
    }

    /** Entitled, but the granting period ends within a day (or already did per the cache). */
    fun isNearExpiry(entitlement: Entitlement, nowMs: Long): Boolean {
        if (!entitlement.isEntitled) return false
        val end = entitlement.currentPeriodEnd ?: return false
        return end.time - nowMs < NEAR_EXPIRY_WINDOW_MS
    }

    /** State to show when the server can't be reached: keep a live cache, otherwise fall closed. */
    fun onRefreshFailed(cached: Entitlement?, nowMs: Long, message: String): EntitlementState =
        if (isTrusted(cached, nowMs)) EntitlementState.Entitled(cached!!) else EntitlementState.Error(message)

    /** State for a fresh server answer. */
    fun fromServer(fresh: Entitlement): EntitlementState =
        if (fresh.isEntitled) EntitlementState.Entitled(fresh) else EntitlementState.NotEntitled(fresh)
}

/**
 * FTNSSLNK-283 port. The app's answer to "can this user get past the paywall?".
 *
 * Always the server's answer (`GET billing/entitlement/me`), cached in
 * SharedPreferences so a cold launch renders the right screen immediately
 * and an offline subscriber still gets in — but trusted only until
 * `currentPeriodEnd`. Past that an offline user is treated as unentitled.
 *
 * Refreshed on login (the gate appearing), on foreground, after a purchase /
 * restore, and after any 402 (`ApiClient.onPaymentRequired`, wired in [attach]).
 */
object EntitlementStore {
    private const val TAG = "Entitlement"
    private const val PREFS = "fl_entitlement"
    private const val KEY_CACHE = "entitlement.cache.v1"
    /** Non-forced refreshes closer together than this are coalesced. */
    private const val MIN_REFRESH_INTERVAL_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val refreshMutex = Mutex()
    private var prefs: SharedPreferences? = null
    private var lastRefreshAtMs = 0L
    private var inFlight: Job? = null

    private val _state = MutableStateFlow<EntitlementState>(EntitlementState.Resolving)
    val state: StateFlow<EntitlementState> = _state.asStateFlow()

    /** Set when the most recent refresh threw (the gate shows nothing for this; the Subscription screen may). */
    @Volatile var lastRefreshFailed: Boolean = false
        private set

    val entitlement: Entitlement get() = _state.value.entitlement
    val isEntitled: Boolean get() = _state.value.isEntitled
    val isResolving: Boolean get() = _state.value is EntitlementState.Resolving

    /** iOS `isNearExpiry`: re-verify the store purchase so a missed renewal doesn't drop the user at the paywall. */
    val isNearExpiry: Boolean get() = EntitlementTrust.isNearExpiry(entitlement, System.currentTimeMillis())

    /**
     * Idempotent. Loads the cache, wires the 402 hook and the foreground
     * refresh. The gate calls it on first composition; `FitnessLinkApp` may
     * call it earlier.
     */
    fun attach(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadCache()?.let { cached ->
            _state.value = if (EntitlementTrust.isTrusted(cached, System.currentTimeMillis())) {
                EntitlementState.Entitled(cached)
            } else {
                // Lapsed cache: don't flash content at an expired subscriber;
                // stay Resolving until the server answers (or refresh fails closed).
                EntitlementState.Resolving
            }
        }
        ApiClient.onPaymentRequired = { scope.launch { refresh(force = true) } }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (AuthManager.isAuthenticated.value) scope.launch { refresh() }
            }
        })
    }

    /**
     * Ask the server. `force = false` coalesces with an in-flight refresh and
     * skips if one completed in the last 30 s; `force = true` always goes.
     */
    suspend fun refresh(force: Boolean = false) {
        if (!force) {
            inFlight?.let { if (it.isActive) { it.join(); return } }
            if (System.currentTimeMillis() - lastRefreshAtMs < MIN_REFRESH_INTERVAL_MS && _state.value !is EntitlementState.Resolving) return
        }
        refreshMutex.withLock {
            val job = scope.launch { doRefresh() }
            inFlight = job
            job.join()
        }
    }

    private suspend fun doRefresh() {
        try {
            val fresh = ApiClient.billingApi.getEntitlement()
            apply(fresh)
            lastRefreshFailed = false
        } catch (e: Exception) {
            lastRefreshFailed = true
            val message = when (e) {
                is ApiError.Offline, is ApiError.NetworkError, is ApiError.Timeout ->
                    "You're offline. Connect to the internet to verify your subscription."
                is ApiError.Unauthorized -> "Your session expired. Please sign in again."
                else -> "We couldn't verify your subscription right now. Please try again."
            }
            Log.w(TAG, "refresh failed: ${e.message}")
            val cached = _state.value.entitlement.takeIf { it.isEntitled } ?: loadCache()
            _state.value = EntitlementTrust.onRefreshFailed(cached, System.currentTimeMillis(), message)
        } finally {
            lastRefreshAtMs = System.currentTimeMillis()
        }
    }

    /** Adopt an entitlement the server just returned (verify / restore), saving a round trip. */
    fun apply(fresh: Entitlement) {
        _state.value = EntitlementTrust.fromServer(fresh)
        saveCache(fresh)
    }

    /** Logout — the next account must not inherit this one's access. The integrator calls this from `AuthManager.logoutSuspending`. */
    fun reset() {
        _state.value = EntitlementState.Resolving
        lastRefreshFailed = false
        lastRefreshAtMs = 0L
        prefs?.edit()?.remove(KEY_CACHE)?.apply()
    }

    // MARK: - Cache (epoch millis, not Gson's locale-dependent Date format)

    private data class CachedEntitlement(
        val isEntitled: Boolean,
        val status: String,
        val source: String?,
        val currentPeriodEndMs: Long?,
        val trialEndsAtMs: Long?
    )

    private fun loadCache(): Entitlement? {
        val raw = prefs?.getString(KEY_CACHE, null) ?: return null
        return try {
            val c = gson.fromJson(raw, CachedEntitlement::class.java)
            Entitlement(c.isEntitled, c.status, c.source, c.currentPeriodEndMs?.let(::Date), c.trialEndsAtMs?.let(::Date))
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCache(e: Entitlement) {
        val c = CachedEntitlement(e.isEntitled, e.status, e.source, e.currentPeriodEnd?.time, e.trialEndsAt?.time)
        prefs?.edit()?.putString(KEY_CACHE, gson.toJson(c))?.apply()
    }
}
