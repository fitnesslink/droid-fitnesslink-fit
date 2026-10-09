# Parity gap report: nutrition

_Generated 2026-10-06 from a file-by-file iOS vs Android comparison. iOS is the spec._

NUTRITION PARITY GAP ANALYSIS: iOS vs Android

Roots: iOS is `/Users/quincywilliams/Repos/FitnessLink/iOS-fitnesslink-fit/FitnessLink/`. Android is `/Users/quincywilliams/Repos/FitnessLink/droid-fitnesslink-fit/app/src/main/java/com/fitnesslink/fit/`.

Size: iOS has 39 nutrition views (about 14.3k lines), a 1,292-line `NutritionService` and 12 view models. Android has 16 nutrition screens plus 2 profile screens (about 6.3k lines in total), an 83-line `NutritionApi` and 4 view models.

**Overall:** Android is an early skeleton of iOS nutrition. Several Android screens are wired to `MockDataProvider`, and their Save/Delete buttons just call `onBack` and do nothing:
- `NutritionGoalSettingsScreen` Save Goals
- `CustomFoodFormScreen` Save and Delete
- `QuickAddScreen` Save Entry
- `FoodEntryDetailScreen` Save and Delete (this screen also loads the entry from `MockDataProvider.foodEntry`)
- `RecentFoodsScreen` (reads `MockDataProvider.recentFoods`)
- `WeeklyProgressScreen` (reads `MockDataProvider.weeklyCalories`)

**Android `NutritionApi.kt` has only these endpoints:**
- `food-entries/me` (optional date), `food-entries/me/all`, POST/PUT/DELETE `food-entries`
- `nutrition-goals/me` and POST `nutrition-goals`
- `meal-slots/me?day=` and POST/PUT/DELETE `meal-slots`
- `grocery/me`, POST `grocery`, `grocery/generate`, `grocery/{id}/toggle`, DELETE `grocery/{id}`
- `barcode/{code}`
- `water-entries` CRUD
- `hydration-goals/me` and POST `hydration-goals`

**iOS endpoints with no Android client:**
- Food entries: `/food-entries/me/foods`, `/food-entries/batch`
- Quick log and recents: `/quick-log/sources`, `/quick-log/copy`, `/meals/recent`
- Favorites: `/favorites/me`, `/favorites/foods/{id}`, `/favorites/meals/{id}`
- Profile: `/profile/me`, `/profile`, `/profile/targets`, `/profile/calculate`, `/profile/targets/override`, `/profile/goals`
- Summary and analytics: `/summary` (daily and range), `/analytics/trends`, `/analytics/consistency`, `/analytics/weight-progress`
- Preferences: `/preferences/displayed-nutrients`
- Meal slots: `/meal-slots/{id}/foods`, `/meal-slots/me/copy-week`, `/meal-slots/me/copy-day`, `/meal-slots/generate-suggestion`, `/meal-slots/generate-week`, `/meal-slots/me/apply-week`
- Templates: `/meal-templates/me`, `/meal-templates`, `/meal-templates/{id}`
- Foods: `/foods/search`, `/foods/{id}`, `/foods/{id}/convert`, `/foods/estimate-macros`, `/foods/parse-meal`, `/foods/identify-meal-photo`
- Grocery: `/grocery/lists`, `/grocery/lists/{id}`, `/grocery/lists/{id}/duplicate`, `/grocery/lists/{id}/reorder`, `/grocery/analyze`, `/grocery/{id}/merge`, `/grocery/staples`
- Hydration: `/water-entries/recent-amounts`, `/hydration-goals/me/preferred-unit`

iOS routes are defined in `Services/Networking/APIEndpoint.swift`.

---------------------------------------------------------------
## 1. iOS features and their Android status

### Onboarding, profile and goals

- **Nutrition onboarding wizard** — MISSING. Seven steps: stats → activity → workout frequency → goal → timeline → dietary/allergies → summary. The summary shows the server's numbers with "why" explanations. iOS: `NutritionOnboardingView`, `NutritionOnboardingViewModel`, `POST /profile/calculate` then `POST /profile`, results pushed into `NutritionGoalsStore`.
- **TDEE / target calculation** — PARTIAL. Android `NutritionGoalSettingsScreen` has a local Mifflin-St Jeor TDEE calculator (weight, height, age, sex, 5 activity levels). It only fills in the calorie field. There is no server call, no macro split, no "why" text and no goal date.
- **Goals hub** — PARTIAL, effectively broken. iOS `NutritionGoalsView` shows a "Calculated" vs "Manually set" badge, a "why" explanation per number, a link to edit the profile, manual override via `POST /profile/targets/override`, revert to calculated, and an escape hatch to set manual targets when there is no profile. Android has 4 editable goal fields seeded from `MockDataProvider.nutritionGoal`, and Save Goals does nothing (no persistence, no `upsertGoal` call).
- **Profile edit screen** — MISSING. iOS `NutritionProfileEditView` does a rowVersion-guarded `PUT /profile` and the server recalculates targets.
- **Goal history** — MISSING on Android. On iOS, `NutritionService.getNutritionGoalHistory` (`/profile/goals`) exists but no view calls it, so it has no UI on either platform.

### Dashboard (iOS `NutritionDashboardView`, `NutritionViewModel`, `GET /summary`)

- **Date navigation** (chevrons, Yesterday/Tomorrow labels, past-day backfill banner, future-day planning banner) — MISSING. Android `NutritionScreen` is today only.
- **Week strip with completeness dots** (`NutritionWeekStripView`, range summary) — MISSING.
- **Calendar sheet** (`NutritionCalendarSheet`, month view with dots) — MISSING.
- **Calorie ring** (`CalorieRingView`) — PARTIAL. Android `CalorieRingCard` caps at 100% and clamps remaining to 0, so it has no amber over-target state. There is also a bug: `NutritionViewModel.todayEntries = DatabaseManager.allFoodEntries()` reads every row with no date filter, custom templates included, so "today" totals are really all-time totals.
- **Macro bars** (`MacroBarView`, amber "Xg over" state) — PARTIAL. Android has plain bars with no over-target state.
- **Micronutrient card** (fiber, sugars, sodium, saturated fat, cholesterol, from the summary's `micros` block, per user display preference) — MISSING.
- **Onboarding call-to-action card** — MISSING.
- **Water tile on the dashboard** — PARTIAL. See Hydration.
- **Logged meal sections** (`MealSectionView`, per-slot macro roll-up, quick-log copy badge) — PARTIAL. Android `MealSectionCard` has add, scan and tap-to-detail, but the detail screen it opens is mock-backed.
- **Planned meals section for the day** — MISSING.
- **Goals tile with provenance badge** — MISSING.
- **Shortcuts** — PARTIAL. Android has Recent and Weekly only. Groceries and Analytics are missing.
- **Nutrition home** (`NutritionHomeView`: This Week stats and quick actions) — PRESENT. Android `NutritionHomeScreen` also hosts the hydration card.

### Logging flows

- **Food search** (`FoodSearchView`, `/foods/search` over the USDA catalog with the user's custom foods merged in and badged "My Food", favorite star per row, "Add 'x' manually" and "Estimate macros with AI" when nothing is found) — MISSING. Android has no food search at all.
- **Food detail with serving conversion** — PARTIAL. iOS `FoodDetailView` is the single screen every flow lands on before saving ("nothing is logged until you save"). It has a unit and quantity picker using the client-side `FoodPortionMath`, confirmed against `/foods/{id}/convert`. It also shows micronutrients, notes water-density volume estimates, and has favorite, edit-logged-entry and backfill modes. Android `FoodEntryDetailScreen` only has a ×0.5 serving multiplier, loads from mock data, and its Save and Delete do nothing.
- **Favorites, foods** (`NutritionFavoritesStore`, `/favorites/*`; star in search, detail and recents; Favorites section in recents) — MISSING.
- **Favorites, meals** — MISSING on Android. On iOS the API exists and `NutritionFavoritesStore.toggleMeal` is implemented, but no view calls it, so there is no UI on iOS either.
- **Recent foods** — PARTIAL. iOS `RecentFoodsListView` has Favorites, My Foods (custom templates) and recent logged foods via `/food-entries/me/foods`, plus search. Android `RecentFoodsScreen` shows a static mock list.
- **Re-log a recent meal** (`RecentMealsView` with `/meals/recent`, then `RecentMealLogView` to edit before logging, saved through atomic `/food-entries/batch`) — MISSING.
- **Previous meals picker inside the meal builder** (`PreviousMealsPickerView`, `PreviousMealsAssembler`, deep copies) — MISSING.
- **Quick-log "Copy" card** (QuickAddView: copies the same meal from the most recent source day via `/quick-log/sources` and `/quick-log/copy`) — MISSING.
- **Meal templates** (`MealTemplatesPickerView`: pick, apply, manage, delete; "Save as Template" from the builder; `/meal-templates` CRUD; batch log) — MISSING.
- **Describe a Meal** (`DescribeMealView`: free text → `/foods/parse-meal` → editable items → batch log with source = ai_estimate, confidence and generation id) — MISSING.
- **AI macro estimate prefill** (QuickAddView "Fill with AI" via `/foods/estimate-macros`; typed values are locked, blanks filled; refine re-call) — MISSING.
- **Snap a Meal** (`SnapMealScreen`, feature-flagged: photo → `/foods/identify-meal-photo` → confirm list → parse and log with source = ai_photo) — BUILT; 2026-10-09 parity with iOS fa28fb0: photo-mode Describe screen hides the text box behind an estimating/retry card, and the meal-section header offers a camera shortcut.
- **Barcode scanner and review** — PARTIAL. iOS `BarcodeScannerContainerView` and `BarcodeScannerView` use backend catalog → device cache → client-side OpenFoodFacts (`Services/BarcodeLookupService.swift`). Catalog hits open `FoodDetailView` with full portions. On a miss you can search or add manually with the barcode carried along, so it is recognized next time. Android `BarcodeScannerScreen` uses ML Kit with `/barcode/{code}` → local DB cache and an inline review (×0.5 serving multiplier, meal picker, Save), which does log for real via the local DB plus the API. It has no OpenFoodFacts fallback, no catalog portions, and no barcode carried into manual entry. A standalone `ScannedFoodReviewScreen.kt` exists but no route uses it.
- **Custom food form** — PARTIAL, non-functional. iOS `CustomFoodFormView` does create/edit/delete of an `isCustomTemplate` row, never logs, and the result appears in search. Android `CustomFoodFormScreen` has the same fields plus a default meal, but it is mock-loaded and Save/Delete do nothing.
- **Quick add / manual entry** — PARTIAL, non-functional. iOS `QuickAddView` has validation, a "Save to My Foods" toggle, and entry links to Snap, Describe, Scan, Recent Foods, Recent Meals, Templates and the Copy card. Android `QuickAddScreen` has the form plus Scan and Recent links, but Save Entry does nothing.
- **Display settings** (`NutritionDisplaySettingsView`: micronutrient toggles stored server-side via `/preferences/displayed-nutrients` through `DisplayedNutrientsStore`, and preferred water unit Automatic/fl oz/mL via `/hydration-goals/me/preferred-unit`) — MISSING.

### Analytics and progress

- **Nutrition analytics** — MISSING. iOS `NutritionAnalyticsView` and `NutritionAnalyticsViewModel` cover Week/Month/3 Months ranges with calories vs target, macro lines with targets, water bars vs goal, and consistency (days logged, rate, streaks). They also chart weight progress with a 7-day moving average, plan projection and estimated goal date. Sources: `/analytics/trends`, `/analytics/consistency`, `/analytics/weight-progress`. Separately, the Profile `AnalyticsView.swift` is a stub on iOS.
- **Weekly progress** — PARTIAL. iOS `WeeklyProgressView` uses `GET /summary` range. Android `WeeklyProgressScreen` has the same layout but shows mock data.
- **Nutrition summary, daily/weekly over planned meals** (`NutritionSummaryView` with `MealPlanViewModel`) — PRESENT. Android `NutritionSummaryScreen` is equivalent but keyed by day of week.

### Meal plan

- **Multi-week planner** — PARTIAL. iOS `MealPlanView` and `MealPlanViewModel` have date-anchored weeks (Sunday start) with previous/next arrows, "Jump to this week", per-day calories vs target and over-target state, "X cal left to plan", and `GET /meal-slots/me?from&to` week rollups. Android `MealPlanScreen` has a single fixed Monday–Sunday week of DayOfWeek tabs. Its slots are saved to the local DB only: `assignSlot`/`removeSlot` never call the API, it only reads from the server.
- **Copy last week** (`/meal-slots/me/copy-week`, overwrite or keep-mine prompt) — MISSING.
- **Copy this day onto other days** (`/meal-slots/me/copy-day`, merge or replace) — MISSING.
- **Meal builder for a slot** — PARTIAL. iOS `MealBuilderView` and `MealBuilderStore` assemble itemized `MealSlotFood`s with servings from search, barcode, recents, manual entry, previous meals or templates. You can add to the plan or log now, save as a template, or remove. Server calls are `PUT /meal-slots/{id}/foods` and `GET /meal-slots/{id}`. Android `MealSlotDetailScreen` only has name, calories and P/F/C fields, saving locally.
- **AI meal suggestions for a slot** (`MealSuggestionsSheet`, `AIMealGeneratorViewModel`, `/meal-slots/generate-suggestion`, seeded from profile allergies and diet) — MISSING. Android shows a "Get AI Suggestion" panel listing `MockDataProvider.aiSuggestedMeals`, with "AI suggestions coming soon" when that list is empty.
- **AI week ideas** — MISSING. `MealPlanIdeasSheet` collects a prompt plus calories, meals, diet, allergies, dislikes, cuisines, prep time, skill and budget, using `AIFormChips`, then calls `/meal-slots/generate-week`. `MealPlanIdeasReviewView` lets you check/uncheck ideas, choose merge or replace, and apply via `/meal-slots/me/apply-week`.
- **Generate grocery list from a date range** (`/grocery/generate` creates a new list) — PARTIAL. Android regenerates locally from slot ingredients (`MealPlanViewModel.regenerateGroceryList`). Its `generateGroceryList` API method is never called.

### Grocery

- **Multiple lists** — MISSING. iOS `GroceryListsView` and `GroceryListsViewModel` have active lists plus an archived History section, create, rename, delete, a Default badge, and duplicate (`/grocery/lists*`). Android has one flat list.
- **List detail** — PARTIAL. iOS `GroceryListDetailView` and `GroceryListDetailViewModel` group items by category with checked items in an "In Cart" section, progress, manual and food-database adds, tap-to-edit, swipe-delete, drag reorder (`/grocery/lists/{id}/reorder`), and an offline banner. Android `GroceryListScreen` has category grouping, search, progress, check toggling (local only), Regenerate and Share. It has no add, edit, delete or reorder.
- **Analyze before adding** (`/grocery/analyze` suggests a category and flags a duplicate; "Add Separately" vs merge via `/grocery/{id}/merge`) — MISSING.
- **Staples ("You Usually Buy")** (`/grocery/staples`) — MISSING.
- **Build a meal from grocery items** (`GroceryMealBuilderView`, `GroceryMealBuilderViewModel`, `GroceryFoodMatcher`: match items to foods, then hand off to the meal builder) — MISSING.

### Hydration

- **Log water screen** — PARTIAL. iOS `LogWaterView` and `HydrationViewModel` let you log for the selected date (backfill), use server "Recent" amounts (`/water-entries/recent-amounts`), pick a unit, add notes, and see the entry list. Android `LogWaterScreen` has fixed presets (250/500/750 mL or 8/16/24 oz), unit, notes, the entry list with delete, and today only. It has no recent amounts and no backfill.
- **Hydration card** — PARTIAL. iOS `HydrationCardView` is on the dashboard for the selected day, with +8/+16/+24 oz presets converted into the goal unit and undo of the last tap (`WaterTapUndoStack`). Android `HydrationCard` sits on the nutrition home screen with +8/+16 oz or +250/+500 mL presets and no undo.
- **Hydration goal amount** — PRESENT on Android, missing on iOS (see section 2).
- **Preferred display unit** — MISSING. iOS has `preferredUnit` and `displayUnit`.
- **Offline queue** — PRESENT. Android water writes go through the `SyncManager` queue, matching iOS.

### Reports (Profile)

- **Nutrition report** (`NutritionReportView` and `NutritionReportViewModel`: Overview/By Meal/By Food tabs, metric cards, time ranges) — PRESENT. Android `NutritionReportScreen` and `NutritionReportViewModel` work the same way. Both compute everything locally from the DB, and neither uses `/reports/nutrition` even though both have a client for it (iOS `ReportService`, Android `ReportApi`).
- **Metric detail** (`NutritionMetricDetailView`, with Swift Charts per metric) — PARTIAL. Android `NutritionMetricDetailScreen` has range chips, stats and a daily breakdown list, but no chart.
- **Meal type detail** (`MealTypeDetailView`: calorie chart and entry list) — MISSING. The Android route `mealTypeDetail/{mealType}` points to `ProfileStubScreen`.
- **Food item detail** (`FoodItemDetailView`: stats, chart, history) — MISSING. The Android route `foodItemDetail/{foodName}` points to `ProfileStubScreen`.
- **Profile `AnalyticsView`** is a stub on iOS as well.

---------------------------------------------------------------
## 2. What Android has that iOS lacks

- **Hydration daily goal editor.** Android `LogWaterScreen` has a `GoalEditor` that calls `HydrationViewModel.updateGoal`, then `POST /hydration-goals`. iOS has no UI for setting the goal amount; `HydrationService.upsertGoal` is only used for the preferred-unit fallback in `NutritionDisplaySettingsView`.
- **Grocery list sharing.** Android `GroceryListScreen` shares the list as plain text through `ACTION_SEND`. No iOS grocery view has a `ShareLink`.
- **Manual barcode entry on a real device,** plus a fallback screen when camera permission is denied (`ManualEntryFallback` in `BarcodeScannerScreen`). On iOS, manual barcode entry only exists in the simulator mock scanner.
- **Local TDEE calculator** inside the goal settings screen (works offline, no profile needed). iOS relies on the server-side calculation.
- **mL quick presets on the hydration card** (250/500 mL). iOS card presets are ounces only and converted.
- **Per-screen range chips in metric detail** (Android `NutritionMetricDetailScreen`).
- **Calorie Tracking and Meal Plans feature cards** on the home screen. This is cosmetic.

---------------------------------------------------------------
## 3. Data model differences (Android `model/*.kt` vs iOS `Models/*.swift`)

**MealSlot (largest divergence).**
- Android anchors slots to a weekday: `day: DayOfWeek`, fetched with `GET meal-slots/me?day=`.
- iOS anchors slots to a date: `date: Date` (`yyyy-MM-dd` `Day` on the wire), fetched with `from`/`to` (`MealSlotWeek` / `MealSlotDayRollup`).
- iOS also adds `foods: [MealSlotFood]` (foodId, servings, servingSize/Unit, per-serving macros), `ingredientsText`, `ingredientItems` (`MealSlotIngredientWire` with sortOrder) and `rowVersion`.
- Android keeps only `recipeName` + macros + `isAISuggestion` + `ingredients: List<GroceryItem>`.
- Android `DailyMealSummary` uses `day: DayOfWeek`; iOS uses `date`. iOS also has `PlannedDayTargetState` and copy-week/day request and result types.
- Risk: Android posts `day` while the server is date-based. This is probably wire-incompatible and should be verified.

**FoodEntry.**
- Android lacks: `foodId`, `quantity`, `portionUnit`, `portionGramWeight`; micronutrients (`fiber`, `sugars`, `sodium`, `saturatedFat`, `cholesterol`); AI provenance (`source`, `aiConfidence`, `aiGenerationId`, `isAiEstimate`/badge).
- The `MealType` enum serializes differently: Android uses enum names (`BREAKFAST`), iOS uses raw values `"Breakfast"`.
- Android lowercases nothing; iOS ids are lowercase UUIDs.

**NutritionGoal.** Identical on both (calorieGoal, protein/fat/carbs targets). iOS layers on `NutritionProfile`, `NutritionTargets` (bmr, maintenance, weeklyWeightChangeKg, estimatedGoalDate, source Manual/Calculated, effectiveFrom, explanations) and `NutritionGoalHistoryEntry` (`Models/API/NutritionProfileModels.swift`). Android has none of these.

**Grocery.**
- Android `GroceryItem` lacks `rowVersion`, `groceryListId` and `sortOrder`.
- Android has no `GroceryList` model (id, name, isArchived, isDefault, itemCount, timestamps, rowVersion).
- Android has no `GrocerySmartModels` (analyze, merge, staples) and no `GroceryMealMatch`.
- The `GroceryCategory` enums match.

**Hydration.**
- `WaterUnit` (fl oz/ml/cups), `WaterIntakeEntry` and `HydrationGoal` match on both.
- Android `HydrationGoal` lacks `preferredUnit`/`displayUnit`.
- Android has no `RecentWaterAmount`.

**BarcodeProduct.** Field-for-field the same (per-100g macros, servingSizeGrams). iOS also has `BarcodeScanError` and `FoodSearchRouting`.

**Reports.**
- `NutritionReportData`, `DailyNutritionRow` and the `NutritionMetric` set match.
- On iOS, `NutritionReportTab` is overview/byMeal/byFood. Android declares `NutritionReportTab` separately in the same file.
- iOS `MealTypeAggregate` carries `entries: [FoodEntry]`; Android's does not, which is why there is no drill-down.

**DailyCalorieSummary.** Android-only (a `dayLabel` string, mock-fed). iOS uses `NutritionSummaryRange` from `/summary` instead.

**iOS-only model families** (all under `Models/API/`), each with no Android equivalent:
- `QuickLogModels`, `RecentMealModels`, `NutritionFavoriteModels`, `MealTemplateModels`
- `FoodPortionModels` (search result and convert)
- `EstimateMacros`, `ParseMeal`, `IdentifyMealPhoto`
- `GenerateMealSuggestion`, `GenerateMealPlanWeek` (draft week, generationId, context: calories, mealTypes, diet, allergies, avoid, cuisines, maxPrep, skill, budget)
- `MicronutrientModels`, `NutritionAnalyticsModels`

Plus, under `Models/`: `NutritionSummaryDisplay` (`MacroProgress`, `WaterTapUndoStack`, `NutritionDayDotState`), `PreviousMeal`, `ManualFoodForm`.

**Offline and sync.**
- Android caches food entries, meal slots and grocery items in SQLite with fire-and-forget API calls. Only water goes through the `SyncManager` queue (`WATER_ENTRIES_TYPE`).
- iOS syncs meal slot foods through `SyncManager` (`replaceMealSlotFoods`), plus the water queue.
- Android meal-slot and grocery edits never reach the server.
