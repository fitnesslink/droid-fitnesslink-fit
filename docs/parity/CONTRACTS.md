# Cross-module contracts for parallel parity work

Agents building different domains at the same time code against these signatures. Whoever
owns a symbol implements it exactly as written; everyone else may call it before it exists
(compile errors from a not-yet-landed symbol are expected during a wave and are reconciled
at integration).

## Rules every agent follows

- Do NOT edit `ui/navigation/AppNavigation.kt`, `ui/navigation/AppRoute.kt`, `network/ApiClient.kt`,
  `persistence/DatabaseManager.kt`, `sync/SyncManager.kt`, `app/build.gradle.kts`.
- New screens: register routes in your own `ui/navigation/<Domain>Routes.kt` as
  `fun NavGraphBuilder.<domain>Routes(navController: NavHostController)`. The integrator wires it
  into `FeatureRoutes.kt`. Keep existing composable names/signatures that `AppNavigation.kt` already
  calls (add optional params only).
- New DB helpers: extension functions in `persistence/<Domain>Dao.kt` using
  `DatabaseManager.rawDatabase()`; add new tables/columns via a `migrateToV12`-style function
  **only** inside your Dao file guarded by `PRAGMA table_info` and called from your own `ensureSchema()`
  at first use (the integrator folds them into the versioned migration later).
- New endpoints: only in the existing Retrofit interface for your domain (`network/api/<Domain>Api.kt`)
  and DTOs in `network/dto/<Domain>Dtos.kt`.
- Offline writes: `DatabaseManager.enqueueSyncEntry(entityType, entityId, action, payloadJson)` and
  register a drain with `SyncManager.registerDrain(SyncDomain.X) { ... }` from your own object's
  `ensureRegistered()`; use `SyncManager.drainQueue(domain) { entry -> ... }` and throw
  `TransientSyncException` to keep a row queued.
- Compile: `cd /Users/quincywilliams/Repos/FitnessLink/droid-fitnesslink-fit && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin -q 2>&1 | grep -v '^w:'`.
  Unit tests: `app/src/test/java/com/fitnesslink/fit/...` (JUnit 4) run with `./gradlew :app:testDebugUnitTest`.
  Never put `/*` inside a Kotlin comment (nested comments!).
- No git commands. iOS is the spec; when Android and iOS disagree, iOS wins.

## Shared components (owner: components agent, `ui/components/`, `classification/`, `contributors/`)

```kotlin
// ui/components/SearchView.kt
@Composable fun SearchView(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier,
                           placeholder: String = "Search", onFilterTap: (() -> Unit)? = null, showFilterIcon: Boolean = onFilterTap != null)

// ui/components/HeaderView.kt — unchanged signature; renders unread badge from NotificationBadge.unread + DatabaseManager.unreadNotificationCount()

// ui/components/ContentFilterComponents.kt
data class TagOption(val id: String, val name: String)
enum class ContentLayout { CARD, LIST }
@Composable fun FavoritesFilterControl(favoritesOnly: Boolean, onChange: (Boolean) -> Unit)
@Composable fun EquipmentChipStrip(options: List<TagOption>, selected: Set<String>, onToggle: (String) -> Unit)
@Composable fun AnatomyChipStrip(options: List<TagOption>, selected: Set<String>, onToggle: (String) -> Unit)
@Composable fun ContributorFilterMenu(selectedContributorId: String?, mineSelected: Boolean,
                                      onSelectContributor: (String?) -> Unit, onToggleMine: () -> Unit)
@Composable fun ClearFiltersButton(visible: Boolean, onClick: () -> Unit)
@Composable fun ContentLayoutToggle(layout: ContentLayout, onChange: (ContentLayout) -> Unit)
object ContentLayoutPrefs { fun get(): ContentLayout; fun set(ContentLayout) }   // SharedPreferences "catalogContentLayout"

// ui/components/ShareSheet.kt — user search + POST /content/share
@Composable fun ShareSheet(contentId: String, kind: ShareableContentKind, onDismiss: () -> Unit)

// ui/components/PendingShareBanner.kt
@Composable fun PendingShareBanner(share: SharedContent, kindLabel: String, onAccept: () -> Unit, onDecline: () -> Unit)

// ui/components/SharedBadge.kt
@Composable fun SharedBadge(sharedByName: String? = null, modifier: Modifier = Modifier)

// ui/components/InfoViews.kt — add
@Composable fun LocationInfoView(location: String)

// classification/ClassificationStore.kt
object ClassificationStore {
    val anatomy: StateFlow<List<Anatomy>>; val equipment: StateFlow<List<Equipment>>
    val primaryAnatomy: List<TagOption>; val primaryEquipment: List<TagOption>   // iOS "primary" chips
    suspend fun refresh()                                   // all /classification/* incl. task-types, rest-types, training-levels
    fun anatomyName(id: String): String?; fun equipmentName(id: String): String?
    fun anatomyId(name: String): String?; fun equipmentId(name: String): String?
    fun workoutTaskTypeId(name: String): String   // falls back to ClassificationFallbackIds
    fun restTypeId(name: String): String; fun restTypeName(id: String): String?
    fun trainingLevels(): List<FLTrainingLevel>
}

// contributors/ContributorStore.kt
object ContributorStore { val contributors: StateFlow<List<Contributor>>; suspend fun refresh(); fun name(id: String?): String?; val houseContributorId: String? }
```

## Session engine (owner: session agent, package `session/`, `ui/session/`)

```kotlin
enum class SessionMode { PLAYLIST, INTERACTIVE }
data class InProgressSessionEntry(val sessionId: String, val workoutId: String, val workoutName: String,
                                  val completionPercent: Int, val elapsedSeconds: Long, val lastMode: SessionMode, val startedAt: Long)
object ActiveWorkoutSession {
    fun inProgressEntries(): List<InProgressSessionEntry>
    fun inProgressEntry(workoutId: String): InProgressSessionEntry?
    suspend fun discard(sessionId: String)           // abandon + delete local + notify
    val events: SharedFlow<SessionEvent>             // Completed(sessionId, workoutId), Discarded(sessionId)
}
object SessionRecorder {
    suspend fun delete(sessionId: String)            // soft delete local, revert calendar, queue DELETE /sessions/{id}
    suspend fun updateRpe(sessionId: String, rpeValue: Int)   // 24 h rule, re-POST complete
}
// Composables other domains embed:
@Composable fun SessionResumeCard(entry: InProgressSessionEntry, onResume: () -> Unit, onRestart: () -> Unit, onDiscard: () -> Unit)
@Composable fun ResumeSessionBanner(onResume: (InProgressSessionEntry) -> Unit)   // Home "Continue where you left off"
// Existing nav entry points keep their names: PlaylistScreen(workoutId, onBack, onStartInteractive, ...), InteractiveSessionScreen(workoutId, onBack, ...)
// New routes in ui/navigation/SessionRoutes.kt: completedWorkoutReport/{workoutId}/{day}, workoutSummary/{sessionId}
```

## Workouts (owner: workouts agent, `ui/workout/` except browser/create/preview sheets, `ui/catalog/WorkoutsScreen.kt`, `viewmodel/Workouts*|WorkoutDetail*|WorkoutEditor*|AIWorkoutGenerator*`)

- Keeps `WorkoutsScreen`, `WorkoutDetailScreen`, `WorkoutEditorScreen` names. `WorkoutEditorScreen` gains
  `seedPrompt: String? = null` (opens the Generate sheet) and `initialMovementId: String? = null`, `onSaved: ((String) -> Unit)? = null`.
- Consumes `ExerciseBrowserSheet` (owner: movements agent) with its current signature plus the optional
  `mode` below.

## Movements (owner: movements agent, `ui/movements/`, `ui/workout/ExerciseBrowserSheet.kt`, `ui/workout/CreateExerciseSheet.kt`, `ui/workout/MovementPreviewSheet.kt`, `viewmodel/ExerciseBrowser*|Movements*`)

```kotlin
sealed class BrowserMode { object Multi : BrowserMode(); data class Single(val title: String) : BrowserMode() }
// ExerciseBrowserSheet: existing params unchanged + mode: BrowserMode = BrowserMode.Multi, onDone: (() -> Unit)? = null
// Routes in ui/navigation/MovementRoutes.kt: movements, movementDetail/{movementId}, movementEditor
@Composable fun MovementsScreen(onBack, onNavigateToMovementDetail: (String) -> Unit, onNavigateToEditor: () -> Unit)
@Composable fun MovementDetailScreen(movementId: String, onBack, onBuildWorkout: (String) -> Unit)
```

## Reports (owner: reports agent, `ui/profile/WorkoutReportScreen.kt`, `SessionDetailScreen.kt`, `MovementAggregateDetailScreen.kt`, new `ReportDetailScreen.kt`, `WorkoutAggregateDetailScreen.kt`, `viewmodel/WorkoutReportViewModel.kt`, `model/WorkoutReportModels.kt`)

```kotlin
@Composable fun ReportDetailScreen(metric: String, onBack: () -> Unit, onNavigateToSession: (String) -> Unit)
@Composable fun WorkoutAggregateDetailScreen(workoutName: String, onBack: () -> Unit, onNavigateToSession: (String) -> Unit)
// SessionDetailScreen(sessionId, onBack) gains delete + RPE edit via SessionRecorder (above)
```

## Wave 2 contracts

### Programs (owner: programs agent — `programs/`, `ui/programs/`, `ui/catalog/Programs*.kt`, `ui/catalog/ProgramDetailScreen.kt`, `ui/catalog/ProgramEditorScreen.kt`, `viewmodel/Programs*|ProgramDetail*|ProgramEditor*`)

```kotlin
object UserProgramStore {
    fun active(): UserProgram?                                   // running → next upcoming → latest open → completed ≤14 d
    fun all(): List<UserProgram>
    fun snapshot(program: UserProgram): ProgramSnapshot?
    suspend fun syncFromServer()                                 // GET /program-enrollments/me → rebuild instances
    fun openProgramEntry(workoutId: String): FitnessContent?     // the program calendar entry for a workout being completed
    suspend fun remove(enrollmentId: String)                     // calendar entries + queued /removal + local row
    suspend fun move(entryId: String, newDate: Date)             // keeps time of day; delegates to CalendarService.reschedule
    val changes: SharedFlow<Unit>
}
object ProgramScheduler { suspend fun addToCalendar(programId: String, startDate: Date, replacing: String? = null): String /* enrollmentId */ }
@Composable fun MyProgramHomeCard(onViewAll: () -> Unit, onViewProgress: () -> Unit, onOpenWorkout: (String) -> Unit)   // renders nothing when no active program
// ProgramEditorScreen(programId, onBack, onSaved: ((String) -> Unit)? = null)
// Routes (ui/navigation/ProgramRoutes.kt): startProgram/{programId}?replacing={enrollmentId}, myProgram?enrollmentId=, programProgress?enrollmentId=
```

### Calendar (owner: calendar agent — `calendar/`, `ui/calendar/`, `viewmodel/CalendarViewModel.kt`)

```kotlin
object CalendarService {
    fun ensureRegistered()
    suspend fun create(entry: FitnessContent)                    // local insert + device mirror + POST /calendar (queued)
    suspend fun reschedule(entryId: String, newDate: Date)       // local + mirror + queued PUT {scheduledDate}
    suspend fun remove(entryId: String)                          // unmirror + discard same-day session + local delete + queued DELETE
    suspend fun removeEnrollment(enrollmentId: String)           // whole-program scope
    suspend fun markCompleted(workoutId: String, day: Date)
    suspend fun refresh(from: Date, to: Date)                    // GET /calendar/me, skipping pendingCalendarMutationIds
    fun entries(forDate: Date): List<FitnessContent>
    val changes: SharedFlow<Unit>
}
fun FitnessContent.tapRoute(): String                            // completed → completedWorkoutReport/{workoutId}/{yyyy-MM-dd}, else workoutDetail/{workoutId}
@Composable fun ScheduleDatePickerSheet(initialDate: Date?, workoutEstimateMinutes: Int?, title: String, cta: String,
                                        onConfirm: (date: Date, durationMinutes: Int?) -> Unit, onDismiss: () -> Unit)
// CalendarScreen keeps its current signature (+ optional params). Routes in ui/navigation/CalendarRoutes.kt.
```

### Home + Catalog hub (owner: home/catalog agent — `ui/home/`, `viewmodel/HomeViewModel.kt`, `ui/catalog/CatalogScreen.kt`, `viewmodel/CatalogViewModel.kt`, `ui/catalog/MyContentScreen.kt`, `viewmodel/MyContentViewModel.kt`, `catalog/`)

```kotlin
object CatalogStore { suspend fun refresh(); val changes: SharedFlow<Unit> }   // workouts/programs/movements list-view in parallel, upsert, reconcile, sort order, prime media
// Routes (ui/navigation/CatalogRoutes.kt): myContent
// Home consumes: ResumeSessionBanner (session), MyProgramHomeCard (programs), CalendarService + tapRoute (calendar), goals/habits via ApiClient.goalApi + DatabaseManager (build HabitCard/GoalProgressCard/DailySummaryFooter locally in ui/home).
```

## Wave 3 contracts — Nutrition (three agents)

Ownership inside `ui/nutrition/` is split by file; `network/api/NutritionApi.kt` and
`network/dto/NutritionDtos.kt` already carry every iOS route — do NOT edit them (report gaps).

### A. Core: profile/onboarding/goals/dashboard/analytics/display settings/reports
Owns: `ui/nutrition/NutritionScreen.kt` (dashboard, route `calorieTracking`), `NutritionHomeScreen.kt`, `NutritionGoalSettingsScreen.kt`, `WeeklyProgressScreen.kt`, new `NutritionOnboardingScreen.kt`, `NutritionProfileEditScreen.kt`, `NutritionAnalyticsScreen.kt`, `NutritionDisplaySettingsScreen.kt`, `NutritionWeekStrip.kt`, `NutritionCalendarSheet.kt`, `CalorieRingView.kt`, `MacroBarView.kt`, `MicronutrientCard.kt`, `MealSectionView.kt`; `ui/profile/NutritionReportScreen.kt`, `NutritionMetricDetailScreen.kt`, new `MealTypeDetailScreen.kt`, `FoodItemDetailScreen.kt`; `viewmodel/NutritionViewModel.kt`, `NutritionReportViewModel.kt`, new `NutritionOnboardingViewModel.kt`, `NutritionAnalyticsViewModel.kt`; new `nutrition/NutritionGoalsStore.kt`, `nutrition/DisplayedNutrientsStore.kt`, `nutrition/NutritionSummaryStore.kt`; `persistence/NutritionDao.kt`; `ui/navigation/NutritionCoreRoutes.kt`; `model/NutritionGoal.kt`, `NutritionReportModels.kt`, `DailyCalorieSummary.kt`, `model/api/NutritionProfileModels.kt` (additions).
```kotlin
object NutritionGoalsStore { val goal: StateFlow<NutritionGoal>; val targets: StateFlow<NutritionTargets?>; val profile: StateFlow<NutritionProfile?>
    suspend fun refresh(); suspend fun overrideTargets(calories: Int, protein: Double, fat: Double, carbs: Double); suspend fun revertToCalculated(); fun calorieGoal(): Int; val changes: SharedFlow<Unit> }
object DisplayedNutrientsStore { val displayed: StateFlow<Set<String>>; suspend fun refresh(); suspend fun set(keys: Set<String>) }   // keys: fiber, sugars, sodium, saturatedFat, cholesterol
object NutritionSummaryStore { suspend fun daily(date: String): NutritionDailySummary?; suspend fun range(from: String, to: String): NutritionSummaryRange?; fun invalidate(date: String) }
// Routes: nutritionOnboarding, nutritionProfileEdit, nutritionAnalytics, nutritionDisplaySettings; existing calorieTracking / nutritionGoalSettings / weeklyProgress / nutritionReport / mealTypeDetail/{mealType} / foodItemDetail/{foodName} keep their strings.
// Dashboard embeds: HydrationCardView(date) (agent C), planned meals for the day via MealPlanStore.slots(date) (agent C), FoodLogStore.entries(date) (agent B); Add buttons navigate to FoodLoggingRoutes.foodSearch(mealType, date).
```

### B. Logging: search/detail/favorites/recents/templates/AI/barcode/custom/quick add
Owns: `ui/nutrition/FoodEntryDetailScreen.kt` (→ becomes `FoodDetailScreen`), `QuickAddScreen.kt`, `RecentFoodsScreen.kt`, `CustomFoodFormScreen.kt`, `BarcodeScannerScreen.kt`, `ScannedFoodReviewScreen.kt`, new `FoodSearchScreen.kt`, `RecentMealsScreen.kt`, `RecentMealLogScreen.kt`, `MealTemplatesPickerScreen.kt`, `DescribeMealScreen.kt`, `SnapMealScreen.kt`, `AIFormChips.kt`, `PreviousMealsPickerSheet.kt`; new `viewmodel/FoodSearchViewModel.kt` etc.; new `nutrition/FoodLogStore.kt`, `nutrition/NutritionFavoritesStore.kt`, `nutrition/FoodPortionMath.kt`, `nutrition/BarcodeLookupService.kt` (OpenFoodFacts fallback); `persistence/FoodLogDao.kt`; `ui/navigation/FoodLoggingRoutes.kt`; `model/FoodEntry.kt`, `BarcodeProduct.kt` (additions).
```kotlin
object FoodLogStore { fun entries(date: String): List<FoodEntry>; suspend fun log(entry: FoodEntry); suspend fun logBatch(entries: List<FoodEntry>, source: String? = null, generationId: String? = null)
    suspend fun update(entry: FoodEntry); suspend fun delete(id: String); suspend fun refresh(date: String); val changes: SharedFlow<Unit>; fun ensureRegistered() }   // SyncDomain.FOOD_ENTRIES drain
object NutritionFavoritesStore { val foodIds: StateFlow<Set<String>>; suspend fun refresh(); suspend fun toggleFood(foodId: String): Boolean; fun isFavorite(foodId: String): Boolean }
object FoodLoggingRoutes { fun foodSearch(mealType: MealType, date: String): String; fun foodDetail(foodId: String, mealType: MealType, date: String, mode: String = "log"): String   // mode: log | edit:{entryId} | addToMeal
    const val RECENT_MEALS = "recentMeals"; const val MEAL_TEMPLATES = "mealTemplates"; const val DESCRIBE_MEAL = "describeMeal/{mealType}/{date}"; const val SNAP_MEAL = "snapMeal/{mealType}/{date}" }
// FoodDetailScreen in mode "addToMeal" calls MealBuilderStore.add(MealBuilderItem(...)) (agent C) instead of logging.
```

### C. Meal plan / grocery / hydration
Owns: `ui/nutrition/MealPlanScreen.kt`, `MealSlotDetailScreen.kt` (→ meal builder), `NutritionSummaryScreen.kt`, `GroceryListScreen.kt` (→ list detail), `LogWaterScreen.kt`, `HydrationCard.kt`, new `MealPlanIdeasSheet.kt`, `MealPlanIdeasReviewScreen.kt`, `MealSuggestionsSheet.kt`, `MealBuilderScreen.kt`, `GroceryListsScreen.kt`, `GroceryMealBuilderScreen.kt`; `viewmodel/MealPlanViewModel.kt`, `HydrationViewModel.kt`, new `MealPlanIdeasViewModel.kt`, `AIMealGeneratorViewModel.kt`, `GroceryListsViewModel.kt`, `GroceryListDetailViewModel.kt`, `GroceryMealBuilderViewModel.kt`; new `nutrition/MealPlanStore.kt`, `nutrition/MealBuilderStore.kt`, `nutrition/GroceryStore.kt`, `nutrition/HydrationStore.kt`; `persistence/MealPlanDao.kt`; `ui/navigation/MealPlanRoutes.kt`; `model/MealPlan.kt`, `HydrationModels.kt` (additions).
```kotlin
object MealPlanStore { fun slots(date: String): List<MealSlot>; fun week(sundayDate: String): List<MealSlot>; suspend fun refreshWeek(sundayDate: String); suspend fun upsert(slot: MealSlot); suspend fun delete(slot: MealSlot)
    suspend fun replaceFoods(slotId: String, foods: List<MealSlotFood>); suspend fun copyWeek(...); suspend fun copyDay(...); val changes: SharedFlow<Unit>; fun ensureRegistered() }   // SyncDomain.MEAL_SLOTS drain
data class MealBuilderItem(val foodId: String?, val name: String, val servings: Double, val servingSize: Double?, val servingUnit: String?, val calories: Double, val protein: Double, val fat: Double, val carbs: Double)
object MealBuilderStore { val items: StateFlow<List<MealBuilderItem>>; var activeSlotId: String?; fun add(item: MealBuilderItem); fun remove(index: Int); fun clear() }
@Composable fun HydrationCardView(date: String, modifier: Modifier = Modifier)   // +8/+16/+24 oz presets in goal unit, undo last tap
object HydrationStore { val goal: StateFlow<HydrationGoal?>; val displayUnit: WaterUnit; suspend fun refresh(); suspend fun log(amount: Double, unit: WaterUnit, date: String, notes: String? = null); suspend fun undoLast(); suspend fun recentAmounts(): List<RecentWaterAmount>; suspend fun setPreferredUnit(unit: WaterUnit?) }
// Routes: existing mealPlan / mealSlotDetail/{id} / nutritionSummary / groceryList / logWater keep their strings; new mealPlanIdeas, groceryLists, groceryList/{listId}, groceryMealBuilder/{listId}.
```

## Wave 4 contracts

### D. Goals/habits/achievements + progress tracking
Owns: `ui/goals/*`, `ui/progress/*`, `viewmodel/Goal*|Habit*|Achievements*|WeightLog*|Measurements*|ProgressPhotos*|PhotoCapture*|PhotoComparison*`, new `goals/GoalStore.kt`, `progress/BodyTrackingStore.kt`, `persistence/GoalsDao.kt`, `persistence/ProgressDao.kt`, `network/api/GoalApi.kt`, `BodyTrackingApi.kt` + their DTO files, `ui/navigation/GoalsProgressRoutes.kt`, `model/BodyMeasurement.kt`, `ProgressPhoto.kt`, `WeightEntry.kt`, `model/GoalModels.kt` (additions only; Home reads `HabitTodayItem`/`DailyProgress`).
```kotlin
object GoalStore { suspend fun refresh(); fun goals(): List<Goal>; fun habits(goalId: String?): List<Habit>; suspend fun create(goal, habits); suspend fun update(goal); suspend fun delete(goalId); suspend fun logHabit(habitId, completed: Boolean); val changes: SharedFlow<Unit>; fun ensureRegistered() }  // drains GOALS/HABITS/HABIT_LOGS
object BodyTrackingStore { suspend fun refreshWeight(); suspend fun addWeight(entry); suspend fun deleteWeight(id); suspend fun refreshMeasurements(); suspend fun addMeasurements(entry: MeasurementEntry); suspend fun deleteMeasurements(entryId)
    suspend fun refreshPhotos(); suspend fun uploadPhotos(photos: List<Pair<PhotoAngle, Uri>>, takenAt: Date, notes: String, perAngleNotes: Map<PhotoAngle,String>): ProgressPhotoEntry; suspend fun deletePhotoEntry(id); val changes: SharedFlow<Unit>; fun ensureRegistered() }  // drains WEIGHT_ENTRIES/MEASUREMENTS
// Routes: existing goals/goalCreation/goalDetail/{id}/habitDetail/{id}/achievements/weightLog/measurementsLog/photos/photoEntryDetail/{id}/photoComparison keep strings; new measurementEntryDetail/{entryId}, photoCapture.
```

### E. Profile, preferences, billing/paywall, notifications, My Videos
Owns: `ui/profile/ProfileScreen.kt`, `ProfileStubScreen.kt`, `PersonalInfoScreen.kt`, `BillingScreen.kt`, `NotificationSettingsScreen.kt`, `DeveloperSettingsScreen.kt`, `CalendarSyncSettingsScreen.kt`, new `PreferencesScreen.kt`; `ui/notifications/*`, new `ui/billing/PaywallScreen.kt`, new `ui/videos/MyVideosScreen.kt`, `ui/theme/*` (dark theme), `ui/navigation/EntitlementGate.kt`, `ui/navigation/ProfileRoutes.kt`; `viewmodel/Profile*|PersonalInfo*|Notification*`, new `NotificationPreferencesViewModel.kt`, `MyVideosViewModel.kt`; new `billing/EntitlementStore.kt`, `billing/PlayBillingManager.kt`, `settings/UserSettings.kt`; `network/api/BillingApi.kt`, `NotificationServiceApi.kt`, `UserApi.kt` + DTOs; `persistence/ProfileDao.kt`.
```kotlin
object UserSettings { var appearance: Appearance (SYSTEM/LIGHT/DARK); var weightUnit: WeightUnit; var distanceUnit: DistanceUnit; var useHealthConnectCalories: Boolean; fun attach(context) }   // writes WeightUnit.current / DistanceUnit.current on change and at startup
object EntitlementStore { val state: StateFlow<EntitlementState> (Resolving / Entitled(entitlement) / NotEntitled / Error); suspend fun refresh(force = false); val isNearExpiry: Boolean; fun reset() }
// EntitlementGate(content) renders Splash while Resolving, PaywallScreen when NotEntitled, content otherwise; ApiClient.onPaymentRequired = { EntitlementStore.refresh(true) }.
// Session audio/voice settings live in session/SessionSettings.kt + AudioCueService.CoachVoicePrefs (read/write them; do not move them). Calendar target: CalendarSyncService.writableCalendars/targetCalendarId.
// Routes: existing preferences / billing / notifications / notificationSettings / personalInfo / developerSettings / calendarSyncSettings keep strings; new myVideos, notificationPreferences.
```
