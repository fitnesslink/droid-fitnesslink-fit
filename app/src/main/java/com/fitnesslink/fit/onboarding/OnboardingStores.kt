package com.fitnesslink.fit.onboarding

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.model.onboarding.*
import com.fitnesslink.fit.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Device-local copy of the in-progress answers + step, keyed by user, so a
 * killed app resumes exactly where the user left off (PRD §4). Port of iOS
 * `OnboardingDraftStore`.
 */
object OnboardingDraftStore {
    private const val PREFS = "onboarding_draft"

    data class Draft(val answers: OnboardingAnswers = OnboardingAnswers(), val step: String? = null)

    private fun key(userId: String) = "draft.${userId.lowercase(Locale.US)}"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context, userId: String): Draft? = runCatching {
        prefs(context).getString(key(userId), null)?.let { ApiClient.gson.fromJson(it, Draft::class.java) }
            ?.let { it.copy(answers = (it.answers ?: OnboardingAnswers()).normalized()) }
    }.getOrNull()

    fun save(context: Context, userId: String, draft: Draft) {
        prefs(context).edit().putString(key(userId), ApiClient.gson.toJson(draft)).apply()
    }

    fun clear(context: Context, userId: String) {
        prefs(context).edit().remove(key(userId)).apply()
    }
}

/**
 * Onboarding PRD v1.1 §25: catalog recommendations for Home. Port of iOS
 * `HomeRecommendationsStore` — refreshed from GET /recommendations/me,
 * cached per user, dismissals are optimistic with Undo.
 */
object HomeRecommendationsStore {
    private const val PREFS = "home_recommendations"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var response by mutableStateOf<RecommendationsResponse?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    /** The item most recently hidden with "Not Interested" (drives the Undo snackbar). */
    var lastDismissed by mutableStateOf<RecommendationItem?>(null)
        private set
    private var hidden by mutableStateOf(emptySet<String>())
    private var endedLocally by mutableStateOf(false)
    private var cacheLoaded = false

    val programs: List<RecommendationItem> get() = response?.programs.orEmpty().filter { it.id !in hidden }
    val workouts: List<RecommendationItem> get() = response?.workouts.orEmpty().filter { it.id !in hidden }
    val isFirstRunActive: Boolean get() = !endedLocally && (response?.showsFirstRunSection ?: false)
    val hasNoMatches: Boolean get() = response?.matchSummary == "None" && (response?.dismissedCount ?: 0) == 0
    val allDismissed: Boolean get() = response != null && programs.isEmpty() && workouts.isEmpty() && !hasNoMatches

    private fun cacheKey() = "recs.${AuthManager.currentUser.value?.id?.toString()?.lowercase(Locale.US) ?: "anon"}"

    fun loadCache(context: Context) {
        if (cacheLoaded) return
        cacheLoaded = true
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(cacheKey(), null)
                ?.let { response = ApiClient.gson.fromJson(it, RecommendationsResponse::class.java) }
        }
    }

    suspend fun refresh(context: Context? = null) {
        isLoading = true
        try {
            val fresh = ApiClient.onboardingApi.recommendations()
            response = fresh
            hidden = emptySet()
            if (!fresh.showsFirstRunSection) endedLocally = false
            context?.let {
                it.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(cacheKey(), ApiClient.gson.toJson(fresh)).apply()
            }
        } catch (_: Exception) {
            // Keep whatever is cached; Home falls back to Today's Workout when nothing is.
        } finally {
            isLoading = false
        }
    }

    fun dismiss(item: RecommendationItem) {
        hidden = hidden + item.id
        lastDismissed = item
        scope.launch { runCatching { ApiClient.onboardingApi.dismiss(DismissRecommendationRequest(item.contentType, item.id, null)) } }
    }

    /** Optional feedback (§29) — re-posting is idempotent server-side. */
    fun setReason(item: RecommendationItem, reason: RecommendationDismissReason) {
        scope.launch { runCatching { ApiClient.onboardingApi.dismiss(DismissRecommendationRequest(item.contentType, item.id, reason.wire)) } }
    }

    fun undoDismiss() {
        val item = lastDismissed ?: return
        hidden = hidden - item.id
        lastDismissed = null
        scope.launch { runCatching { ApiClient.onboardingApi.undoDismiss(item.contentType, item.id) } }
    }

    fun clearUndo() { lastDismissed = null }

    /** "Dismissed" (user hid it) or "Graduated" (started a program / scheduled a workout, §25.5). */
    fun endFirstRun(state: String) {
        if (!isFirstRunActive) return
        endedLocally = true
        scope.launch {
            runCatching { ApiClient.onboardingApi.endHomeExperience(HomeExperienceRequest(state)) }
            refresh()
        }
    }


    fun reset() {
        response = null
        hidden = emptySet()
        lastDismissed = null
        endedLocally = false
        cacheLoaded = false
    }
}
