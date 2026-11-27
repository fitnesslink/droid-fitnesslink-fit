package com.fitnesslink.fit.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.model.onboarding.*
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.NutritionTargets
import com.fitnesslink.fit.network.dto.UpdateNutritionProfileRequest
import com.fitnesslink.fit.nutrition.NutritionGoalsStore
import com.fitnesslink.fit.onboarding.*
import com.fitnesslink.fit.persistence.DatabaseManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

/**
 * Onboarding PRD v1.1 flow state. Port of iOS `OnboardingFlowViewModel`;
 * the branch/validation/request rules live in [OnboardingRules].
 */
class OnboardingFlowViewModel(application: Application) : AndroidViewModel(application) {

    var mode by mutableStateOf(OnboardingMode.FULL)
        private set
    var answers by mutableStateOf(OnboardingAnswers())
        private set
    var history by mutableStateOf(listOf(OnboardingStep.WELCOME))
        private set
    val step: OnboardingStep get() = history.last()

    var previewTargets by mutableStateOf<NutritionTargets?>(null)
        private set
    var calculationError by mutableStateOf<String?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set
    var completionError by mutableStateOf<String?>(null)
        private set
    private var nutritionDecision: String? = null

    private val context get() = getApplication<Application>()
    private val userId: String =
        AuthManager.currentUser.value?.id?.toString()?.lowercase(Locale.US) ?: "anonymous"
    private var configured = false
    private var syncJob: Job? = null
    private var calcJob: Job? = null

    /** Called once by the screen; restores the local draft (full mode) or the server copy. */
    fun configure(mode: OnboardingMode) {
        if (configured) return
        configured = true
        this.mode = mode
        val draft = if (mode == OnboardingMode.FULL) OnboardingDraftStore.load(context, userId) else null
        if (draft != null) {
            answers = draft.answers
            history = OnboardingRules.replayPath(OnboardingStep.fromKey(draft.step) ?: OnboardingStep.WELCOME, draft.answers)
        } else {
            history = listOf(if (mode == OnboardingMode.FULL) OnboardingStep.WELCOME else OnboardingStep.PRIMARY_GOAL)
        }
        if (mode == OnboardingMode.EDIT_TRAINING || draft == null) {
            viewModelScope.launch {
                val remote = runCatching { ApiClient.onboardingApi.getMine() }.getOrNull() ?: return@launch
                val remoteAnswers = remote.answers?.normalized() ?: return@launch
                answers = remoteAnswers
                if (mode == OnboardingMode.FULL) {
                    OnboardingStep.fromKey(remote.currentStep)?.let { history = OnboardingRules.replayPath(it, remoteAnswers) }
                }
            }
        }
    }

    // MARK: Answers

    fun update(transform: (OnboardingAnswers) -> OnboardingAnswers) {
        answers = transform(answers)
        persistLocally()
    }

    val canContinue: Boolean get() = OnboardingRules.canContinue(step, answers)
    val isSkippable: Boolean get() = OnboardingRules.isSkippable(step)

    // MARK: Navigation

    val canGoBack: Boolean
        get() = history.size > 1 && step != OnboardingStep.NUTRITION_TRANSITION && step != OnboardingStep.FITNESS_TRANSITION

    fun goBack() {
        if (!canGoBack) return
        history = history.dropLast(1)
        if (step == OnboardingStep.DIETARY || step == OnboardingStep.ABOUT_YOU) previewTargets = null
        saveError = null
    }

    fun advance() {
        OnboardingRules.nextStep(step, answers, mode)?.let { push(it) }
    }

    fun push(next: OnboardingStep) {
        history = history + next
        syncToServer()
        if (next == OnboardingStep.NUTRITION_TRANSITION) calculateNutrition()
    }

    fun returnTo(target: OnboardingStep) {
        val idx = history.lastIndexOf(target)
        if (idx >= 0) history = history.take(idx + 1) else push(target)
    }

    fun skip() {
        when (step) {
            OnboardingStep.SECONDARY_GOAL -> update { it.copy(secondaryGoal = null, noSecondaryGoal = true) }
            OnboardingStep.NUTRITION_GOAL -> update { it.copy(nutritionGoal = null, muscleFocus = null, recompFocus = null) }
            OnboardingStep.WEIGHT_GOAL -> update {
                it.copy(targetWeightKg = null, skipTargetWeight = true,
                    weeklyRateKg = it.weeklyRateKg ?: OnboardingRules.paceOptions(it).firstOrNull()?.kgPerWeek)
            }
            else -> {}
        }
        advance()
    }

    // MARK: Nutrition (Transition Screen 1 + approval)

    fun calculateNutrition() {
        calculationError = null
        val request = OnboardingRules.buildNutritionRequest(answers)
        if (request == null) {
            history = history.dropLast(1) + OnboardingStep.NUTRITION_MISSING
            return
        }
        calcJob?.cancel()
        calcJob = viewModelScope.launch {
            val started = System.currentTimeMillis()
            try {
                val targets = ApiClient.nutritionApi.calculateNutritionTargets(request)
                val elapsed = System.currentTimeMillis() - started
                if (elapsed < 1600) delay(1600 - elapsed) // let the four steps read; no fake percentages
                previewTargets = targets
                if (step == OnboardingStep.NUTRITION_TRANSITION) {
                    history = history.dropLast(1) + OnboardingStep.NUTRITION_TARGETS
                    syncToServer()
                }
            } catch (e: ApiError.ServerError) {
                calculationError = e.envelope?.displayMessage ?: "We couldn't calculate your targets. Please try again."
            } catch (e: Exception) {
                calculationError = "We couldn't calculate your targets. Check your connection and try again."
            }
        }
    }

    fun skipNutrition(decision: String) {
        nutritionDecision = decision
        if (step == OnboardingStep.NUTRITION_TRANSITION) history = history.dropLast(1)
        push(OnboardingStep.FITNESS_TRANSITION)
    }

    /**
     * Accept (custom == null) or Customize. Persists the profile (updating an
     * existing one on 409), then the manual override for customized values.
     * Never claims success on failure (PRD §34).
     */
    fun saveTargets(custom: CustomTargets?) {
        if (isSaving) return
        val request = OnboardingRules.buildNutritionRequest(answers) ?: return
        isSaving = true
        saveError = null
        viewModelScope.launch {
            try {
                try {
                    NutritionGoalsStore.createProfile(request)
                } catch (e: ApiError.ServerError) {
                    if (!e.isConflict) throw e
                    val existing = ApiClient.nutritionApi.getNutritionProfile()
                    NutritionGoalsStore.updateProfile(
                        UpdateNutritionProfileRequest(
                            rowVersion = existing.profile.rowVersion,
                            dateOfBirth = request.dateOfBirth, sex = request.sex, heightCm = request.heightCm,
                            currentWeightKg = request.currentWeightKg, goalWeightKg = request.goalWeightKg,
                            activityLevel = request.activityLevel, workoutFrequency = request.workoutFrequency,
                            primaryGoal = request.primaryGoal, timeline = request.timeline,
                            timelineCustomDate = request.timelineCustomDate,
                            dietaryPreferences = request.dietaryPreferences ?: emptyList(),
                            allergies = request.allergies ?: emptyList(),
                            goalSubtype = request.goalSubtype, weeklyRateKg = request.weeklyRateKg,
                            hasGoalWeight = request.hasGoalWeight
                        )
                    )
                }
                if (custom != null) {
                    NutritionGoalsStore.overrideTargets(custom.calories, custom.protein.toDouble(), custom.fat.toDouble(), custom.carbs.toDouble())
                }
                nutritionDecision = if (custom == null) "Accepted" else "Customized"
                push(OnboardingStep.FITNESS_TRANSITION)
            } catch (e: ApiError.Offline) {
                saveError = "You're offline. Your targets weren't saved — reconnect and try again."
            } catch (e: ApiError.ServerError) {
                saveError = e.envelope?.displayMessage ?: "Your targets weren't saved. Please try again."
            } catch (e: Exception) {
                saveError = "Your targets weren't saved. Please try again."
            } finally {
                isSaving = false
            }
        }
    }

    // MARK: Completion (Transition Screen 2)

    /** Marks onboarding complete server-side and primes Home's recommendations. */
    suspend fun finish(): Boolean {
        completionError = null
        val started = System.currentTimeMillis()
        try {
            ApiClient.onboardingApi.save(SaveOnboardingRequest(answers, OnboardingStep.FITNESS_TRANSITION.key))
            ApiClient.onboardingApi.complete(CompleteOnboardingRequest(nutritionDecision ?: "Skipped"))
        } catch (e: Exception) {
            completionError = if (NetworkMonitor.isConnected.value)
                "We couldn't finish setting up your profile. Please try again."
            else "You're offline. Reconnect to finish setting up your profile."
            return false
        }
        HomeRecommendationsStore.refresh(context)
        val elapsed = System.currentTimeMillis() - started
        if (elapsed < 2000) delay(2000 - elapsed)
        runCatching { DatabaseManager.user()?.let { DatabaseManager.setUserPersonalized(it.id) } }
        AuthManager.markPersonalizationComplete()
        OnboardingDraftStore.clear(context, userId)
        return true
    }

    /** Edit mode: save the fitness answers and refresh recommendations. */
    fun saveTrainingPreferences(onDone: () -> Unit) {
        isSaving = true
        viewModelScope.launch {
            try {
                ApiClient.onboardingApi.save(SaveOnboardingRequest(answers, null))
                HomeRecommendationsStore.refresh(context)
                onDone()
            } catch (_: Exception) {
                saveError = "Your preferences weren't saved. Check your connection and try again."
            } finally {
                isSaving = false
            }
        }
    }

    // MARK: Persistence

    private fun persistLocally() {
        if (mode != OnboardingMode.FULL) return
        OnboardingDraftStore.save(context, userId, OnboardingDraftStore.Draft(answers, step.key))
    }

    private fun syncToServer() {
        persistLocally()
        if (mode != OnboardingMode.FULL) return
        val snapshot = answers
        val stepKey = step.key
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(400)
            runCatching { ApiClient.onboardingApi.save(SaveOnboardingRequest(snapshot, stepKey)) }
        }
    }

    data class CustomTargets(val calories: Int, val protein: Int, val carbs: Int, val fat: Int)

    @Suppress("unused") private val now: Date get() = Date()
}
