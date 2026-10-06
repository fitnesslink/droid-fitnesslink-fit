# Parity gap report: goals-progress-reports-components

_Generated 2026-10-06 from a file-by-file iOS vs Android comparison. iOS is the spec._

## Feature-parity gap analysis: Goals/Habits/Achievements, Progress Tracking, Reports, shared Components

Paths: iOS = `/Users/quincywilliams/Repos/FitnessLink/iOS-fitnesslink-fit/FitnessLink/`, Android = `/Users/quincywilliams/Repos/FitnessLink/droid-fitnesslink-fit/app/src/main/java/com/fitnesslink/fit/`.

**Main findings:**
- **Android progress photos don't work end to end.** There is no capture screen, no upload, and no image rendering anywhere.
- **Android measurements don't sync.** New and deleted entries stay on the device, and the read path likely can't parse what the server sends.
- **Android has no Home habit checklist,** so nothing writes habit logs and the habit heatmap is always empty.
- **Android reports only use local data.** The report detail and workout aggregate detail screens are placeholders, so Session Detail can't be reached.
- **Android has no local cache or offline queue** for goals, habits, weight or measurements. iOS has both.
- **A few iOS bugs turned up along the way** (listed at the end of section 1). The biggest: the Camera button in iOS photo capture does nothing.

---

### 1. iOS features and their Android status

#### Goals / Habits / Achievements

**API layer**
- iOS `Services/API/GoalService.swift` covers:
  - goals: `GET/POST /goals`, `GET/PUT/DELETE /goals/{id}`, `PUT /goals/{id}/progress`
  - habits: `GET /habits/goal/{goalId}`, `GET /habits/me`, `POST /habits`, `PUT/DELETE /habits/{id}`, `POST /habits/{id}/log`, `GET /habits/{id}/streak`
  - milestones: `GET /milestones/goal/{goalId}`, `POST /milestones`, `PUT /milestones/{id}/achieve`
  - achievements: `GET /achievements/me`, `GET /achievements` (definitions), `POST /achievements` with body `{achievementId}`
- Android `network/api/GoalApi.kt`: **PARTIAL**.
  - All goal, habit and milestone endpoints are there.
  - `GET /achievements` (definitions) is missing.
  - `unlockAchievement` posts a whole `Achievement` object instead of `{achievementId}`, so the request doesn't match what the server expects.

**Offline sync**
- iOS writes goals, habits and habit logs to the local DB first, queues them, and replays them later (`SyncManager.drainGoals`, `drainHabits`, `drainHabitLogs`). The goal list pushes queued changes before refreshing.
- Android: **MISSING.** Every call is a direct network call. Nothing is cached locally, and the goal list starts empty when offline.

**Goal list** (`Views/Goals/GoalListView.swift`, `GoalDetailCard.swift`): active goals as cards with a progress ring/% and trajectory, a collapsible "Archived" section, an empty state with a call to action, and a + button.
- Android `ui/goals/GoalsListScreen.kt`: **PARTIAL.**
  - Rows only show title, description and the raw status name. There is no progress %, ring or trajectory.
  - Archived goals are filtered out entirely; there is no Archived section.
  - Archive/pause exist in the ViewModel on both platforms but neither has UI for them.

**Goal creation flow** (`GoalCreationFlowView`, `GoalDirectionPicker`/`GoalDirectionCard`, `GoalDetailsStep`, `GoalHabitsStep`/`SuggestedHabitCard`/`HabitCard`; `GoalCreationViewModel`): three steps with a progress bar, back and cancel.
- Step 1, direction (6 goal types): **PRESENT** (`GoalDirectionStep.kt`).
- Step 2, details (title, description, target value and unit, target date picker, identity statement, all pre-filled by type): **PARTIAL.**
  - Missing: the optional "Current Weight" field for the body-composition type, which iOS pre-fills from the latest weigh-in (FTNSSLNK-158).
  - The goal's `currentValue` is never sent on create.
- Step 3, starter habits (hardcoded suggestions per type, toggle selection, "Start My Journey"): **PRESENT** (`GoalHabitsStep.kt`).
  - The Android passive-sensor suggestion lacks `autoCompleteSource` (iOS: `HealthKit_Steps`).
- Save:
  - iOS saves locally, queues, then sends the create without waiting (lowercased ids, habits stamped with `goalId`).
  - Android does a blocking `POST /goals` then `POST /habits` with a spinner and error message: **PARTIAL** (no offline support).

**Goal detail** (`GoalDetailView`, `GoalProgressHeader`, `HabitRowView`, `MilestoneRowView`; `GoalDetailViewModel`): progress ring with %, trajectory badge, identity-statement quote, linked habits with streak badge (tap opens habit detail), milestone timeline (achieved/next/% markers), Current vs Target card, Target Date row, and a delete button (trash plus confirmation) that also deletes the goal's habits, streaks and logs, offline-safe.
- Android `ui/goals/GoalDetailScreen.kt`: **PARTIAL.**
  - Has: title, description, habits with `StreakBadge` (tap opens habit), milestones list.
  - Missing: progress ring/% header, trajectory badge, identity statement, Current/Target card, target date, and delete goal (no `delete` call anywhere in the UI).
  - The milestone row lacks iOS's "Next" and % marker styling (Android uses a check icon and strikethrough instead).

**Habit detail** (`HabitDetailView`, `CalendarHeatmapView`, `StatCard`, `StreakBadge`; `HabitDetailViewModel`): type icon and tier pill, anchor behavior, linked goal title, streak panel (current/longest/freezes as 3 dots), activity heatmap (done/missed/freeze/pending legend), statistics grid (completion rate, total, completed, missed), "Recent Activity" log list (last 7, completion type).
- Note: iOS calendar days and recent logs come from `MockDataProvider` only; the server refresh updates only habit and streak.
- Android `ui/goals/HabitDetailScreen.kt`: **PARTIAL.**
  - Has: title, `StreakBadge`, description, a 12-week heatmap, current/longest streak card.
  - Missing: tier pill, anchor, goal title, freezes, stats grid, recent activity, and missed/freeze states in the heatmap.
  - The heatmap reads local `habit_logs` (`DatabaseManager.habitLogDates`), but **nothing on Android writes to `habit_logs`**, so it is always empty.
- Neither platform has a log/complete button on the habit detail screen.

**Today's habits on Home** (`HabitCard` with complete, undo via `onUncomplete` and haptics; passive-sensor progress bar; streak badge) plus **daily summary footer** (`DailySummaryFooter.swift`: "X of Y done", "Streak at risk", progress bar) and **goal progress cards** (`GoalProgressCard.swift`).
- iOS logic: `HomeViewModel.completeHabit`/`uncompleteHabit` and `HabitService.log`.
- Android: **MISSING.**
  - `HomeViewModel.completeHabit` exists but nothing calls it.
  - `goalSummaries` is loaded but never shown.
  - The `DailyProgress` model exists but has no UI.

**Achievements list** (`AchievementsView`, `AchievementBadge`; `AchievementsViewModel`): 3-column grid with Earned and Locked sections.
- Android `ui/goals/AchievementsScreen.kt`, `AchievementBadge.kt`: **PRESENT**, with extras (see section 2).
- The Android `AchievementType` enum has only the 8 habit types. It lacks the server types iOS added: `FirstWorkout`, `ConsistencyKing`, `StreakMaster`, `PersonalRecord`, `GoalCrusher`, `EarlyBird`, `Centurion`. Earned rows of those types may fail to parse.

**Session achievements** (`Services/Session/SessionAchievements.swift`):
- What it detects: personal records (heaviest weight, most reps at a weight, longest duration, longest distance), first time logging a movement, first workout, training streak, and program week/program completion.
- Where it shows: the "Your Achievements" section of `WorkoutCompletedView` with a "+N more" fold.
- `AchievementUnlocker` posts the earned types to the server.
- Android: **MISSING.** No PR detection, no achievements section on `WorkoutCompletedScreen`, no unlock call.

**Goal notification settings** (`Views/Goals/NotificationSettingsView.swift`, route `.goalNotificationSettings`): coaching tone, 6 toggles (habit reminders, streak alerts, milestones, AI coaching, re-engagement, goal check-ins), max daily notifications. Saved via `NotificationAPIService.updatePreferences`.
- Android `ui/profile/NotificationSettingsScreen.kt`: **PRESENT.** Same toggles, tone and daily cap, plus extras (section 2).

#### Progress tracking

**API layer** (`Services/API/BodyTrackingService.swift`):
- weight: `GET /weight/me?since&to`, `POST /weight`, `PUT/DELETE /weight/{id}`
- measurements: `GET /measurements/me` (one row per body part, which iOS groups by day), `POST /measurements` (one call per part, body part resolved to an anatomy id via `ClassificationStore`, values in cm), `DELETE /measurements/{id}` (one per part)
- photos: `GET /progress-photos/me` (flat rows grouped by day into multi-angle entries; angle inferred when missing), two-step upload `POST /media` then `POST /progress-photos` with `{imageId, takenAt, notes, angle}`, `DELETE /progress-photos/{id}`

Android `network/api/BodyTrackingApi.kt`: **PARTIAL.**
- Weight endpoints: complete.
- Measurements: the Android call expects already-grouped `MeasurementEntry` objects, but the server sends one row per body part (anatomy id, value). There is no anatomy mapping and no grouping, so the server read is likely broken.
- Photos: `GET` and `DELETE` only. **No upload path.** The Android `ProgressPhotoEntry`/`AnglePhoto` models have no media id field and expect a `photos[]` list the server doesn't return, so server rows likely come back empty.

**Weight log and chart** (`Views/Progress/WeightLogView.swift`, `WeightLogViewModel`): Current / Last Change / Total stats, Swift Charts trend line, history list, add sheet (unit picker, weight, date picker, notes). Local cache plus offline queue (`drainWeight`).
- Android `ui/progress/WeightLogScreen.kt`: **PARTIAL.**
  - Has: stats, a basic Canvas line chart (no axes), history, add dialog.
  - Missing: date picker in the add dialog (date is always today), local cache, offline queue.
  - The add call is sent once and failures are ignored. Initial data comes from the mock provider.
- Neither platform has delete UI (both ViewModels have `deleteEntry`, unused).

**Measurements with deltas** (`MeasurementsLogView.swift` with the shared `MeasurementBreakdownView`): latest entry grouped by category (Core/Arms/Legs/Other) with body-part icons, coloured +/- change vs. the previous entry, "Log Measurements" sheet (unit, date, part toggles with values, notes), history rows that open entry detail. Offline queue via `drainMeasurements`.
- Android `ui/progress/MeasurementsLogScreen.kt`: **PARTIAL.**
  - Has: latest entry with category grouping and deltas, add dialog.
  - Missing: icons, date picker.
  - History rows can't be tapped.
  - **`addEntry`/`deleteEntry` never call the API**, so entries stay on the device only.

**Measurement entry detail** (`MeasurementEntryDetailView.swift`, route `.measurementEntryDetail`: breakdown with deltas vs. the previous entry, plus notes): Android **MISSING** (no screen, no route).

**Progress photos list** (`ProgressPhotosView.swift`, `PhotoEntryCard.swift`): "Take New Photos" and "Compare" buttons, search by date (graphical picker, clearable), timeline of cards with real thumbnails (`FLImageView` `.progressPhoto`), plus nearest weight and measurement chips.
- Android `ui/progress/ProgressPhotosScreen.kt`: **PARTIAL.**
  - **"Take Photos" sets a local `showCapture` flag that nothing uses.**
  - No date-search UI (the ViewModel has `searchDate`).
  - Thumbnails are placeholder person icons, not images.

**Photo capture flow** (`PhotoCaptureFlowView.swift`, `PhotoCaptureViewModel`): Front → Side → Back steps with a 3-segment progress bar, per-angle header icon and copy, Camera or Library (PhotosPicker), preview with Retake, per-angle notes, Next/Finish, "Save N Photos Now" (finish early), "Skip {angle}", upload overlay, error alert.
- There are **no pose or silhouette guide overlays**; each angle only shows an SF Symbol icon.
- **iOS bug:** the "Camera" button sets `viewModel.showCamera`, but nothing presents a camera, so only Library works.
- Android: **MISSING.** No screen. `viewmodel/PhotoCaptureViewModel.kt` exists but nothing uses it, and it has no upload logic. No camera or picker integration (the only `GetContent` usage is in `PersonalInfoScreen`).

**Photo entry detail** (`PhotoEntryDetailView.swift`): angles stacked vertically as large `FLImageView` images, per-angle notes, entry notes, linked "Body Weight" and "Measurements" cards.
- Android `ui/progress/PhotoEntryDetailScreen.kt`: **PARTIAL.**
  - Uses angle tabs instead of a vertical stack and shows placeholder icons, not images.
  - Reads `MockDataProvider.photoEntry(entryId)` instead of the ViewModel/server, so real entries won't resolve.

**Photo comparison** (`PhotoComparisonView.swift`, `PhotoComparisonViewModel`): angle selector, Before/After pickers (`DateEntryPicker` sheet), real images, weight per side, Weight Change and Measurement Changes cards.
- Android `ui/progress/PhotoComparisonScreen.kt`: **PARTIAL.** Same layout and dialogs, but images are placeholder icons.
- Both platforms' "closest weight/measurements" read `MockDataProvider`.

**Photo delete:** both service layers have the endpoint. Neither platform has delete UI, and the ViewModel `deleteEntry` only removes the item locally. Parity: equal (both missing).

#### Reports / Analytics

**Workout report** (`Views/Profile/WorkoutReportView.swift`, `ReportCardView.swift`; `WorkoutReportViewModel`): time range (7d/30d/All) and tabs (Overview / By Workout / By Movement); Overview is a grid of 10 metric cards that open report detail; aggregate cards with trends; movement cards with PR. Pull-to-refresh. `load()` runs `hydrateFromServer()`:
- calls `GET /sessions/me`
- backfills completed sessions that aren't stored locally (FTNSSLNK-368 rule)
- fetches per-set history for each (`SessionService.getSessionHistory`, at most 6 at a time)
- maps RPE ids to midpoints via `ClassificationService.getRpeScales`

Weights display in the user's unit (`WeightUnit.current`).
- Android `ui/profile/WorkoutReportScreen.kt` and `viewmodel/WorkoutReportViewModel.kt`: **PARTIAL.**
  - UI structure matches (range, tabs, metric cards, aggregate and movement cards, trend pills).
  - Missing: **server hydration** (local DB only; `SessionApi` has no session-history endpoint), pull-to-refresh, and unit conversion ("lbs" is hardcoded).
- `ReportApi` (`/reports/workouts`, `/reports/nutrition`) exists on both platforms; iOS `ReportService.getWorkoutReport` is never called either.

**Report detail per metric** (`ReportDetailView.swift`, route `.reportDetail`): range picker; Swift Charts per metric (bars for completed/time/calories, lines for RPE/weight/volume); filtered session list with per-metric highlight that opens session detail; RPE list with badges; Volume tab (by workout / by movement, top 5 sets); PR list; streak view; Recent Workouts; long-press "Delete Session" (`SessionDeletion.delete`).
- Android: **MISSING.** The route `reportDetail/{metric}` shows `ProfileStubScreen` (marked TODO in `ui/navigation/AppNavigation.kt`).

**Workout aggregate detail** (`WorkoutAggregateDetailView.swift`): stats grid, trends, best session, Volume/RPE/Duration trend charts, session history that opens session detail, context-menu delete session.
- Android: **MISSING** (the `workoutAggregate/{workoutName}` route is a stub, TODO).

**Session detail** (`SessionDetailView.swift`): stats grid; exercise breakdown grouped by exercise with per-set reps, weight and volume; toolbar delete with confirmation; **RPE edit sheet** (`SessionRpeEditSheet`, FTNSSLNK-411: one edit within 24 h). The same file has `CompletedWorkoutReportView`, used by the route `.completedWorkoutReport(workoutId, day)` from Calendar.
- Android `ui/profile/SessionDetailScreen.kt`: **PARTIAL.**
  - Has: header, stats, exercise and set breakdown (lbs hardcoded).
  - Missing: delete, RPE edit, the completed-workout-report route.
  - **Nothing on Android navigates to `sessionDetail`**, because report detail and workout aggregate detail are stubs.

**Movement aggregate detail** (`MovementAggregateDetailView.swift`): range picker, stats grid, PR card, "Weight Progression" and "Volume Per Session" charts, weight log with per-set rows.
- Android `ui/profile/MovementAggregateDetailScreen.kt`: **PARTIAL.**
  - Has: range chips (30d/90d/All), stat grid, recent session rows with a PR tag.
  - Missing: PR card, both charts, per-set breakdown.

**Analytics view** (`Views/Profile/AnalyticsView.swift`): just a "coming soon" stub on iOS (also `GoalsView`, `WeightView`, `PhotosView`, `MeasurementsView`, which aren't routed). Android: nothing equivalent, so no gap.

#### iOS bugs and oddities found along the way
- The Camera button in photo capture does nothing (above).
- Habit-detail heatmap, stats and recent logs use mock data only.
- `GoalDetailViewModel`/`AchievementsViewModel` show mock data until the server call returns.
- Progress-photo "closest weight/measurements" come from `MockDataProvider`.
- No delete UI for weight, measurements or photos.
- Archive/pause exist in `GoalListViewModel` but have no UI.
- Milestones are read-only (`MilestoneService.create`/`achieve` are never called).

---

### 2. Things Android has that iOS lacks
- **Milestones:** inline "Add milestone" form (name and target) and tap-to-achieve on goal detail. Uses `POST /milestones` and `PUT /milestones/{id}/achieve`. iOS milestones are display-only.
- **Custom habits in goal creation:** "Add custom habit" row in step 3 (`AddCustomHabitRow`), plus a one-line rationale per habit type on suggestion cards. Creation also shows a "Creating…" state and an inline error.
- **Achievement detail:** tapping a badge opens a dialog with the description plus earned date or a locked hint. There is also an empty state. Achievements and notification settings are in the Profile menu (items 11 and 12 in `viewmodel/ProfileViewModel.kt`). On iOS, achievements are only reachable from the Home dashboard "streak" card, and goal notification settings only via `NotificationPreferencesView`.
- **Notification settings:** one screen combines the System (re-engagement plus "Open Android settings"), Goals, Content and Calendar sections with the daily cap. iOS splits these across `NotificationPreferencesView` and `NotificationSettingsView`.
- **Habit heatmap:** a real 12-week, week-column grid with future-day handling and accessibility descriptions per cell. (It has no data source, as noted above.)
- **Movement aggregate detail:** its own 30/90/All range chips instead of the report-wide range.

---

### 3. Shared iOS components (`Views/Components/`) and Android equivalents (`ui/components/`)

| iOS component | Android | Status |
|---|---|---|
| `HeaderView` (logo + bell with unread count from DB) | `HeaderView.kt` | PARTIAL: no unread badge |
| `HeaderBackView` | `HeaderView.kt` → `HeaderBackView` | PRESENT |
| `BackCircleButton`, `CloseCircleButton`, `ContextCircleButton` | all in `BackCircleButton.kt` | PRESENT |
| `SearchView` (bound text, placeholder, clear button, filter icon) | `SearchView.kt` | PARTIAL: text is internal state only, no placeholder param, no clear button, so the parent can't read the query |
| `SeparatorView` | `SeparatorView.kt` | PRESENT |
| `StepProgressView` (`ProgressTracker` pages) | `StepProgressView.kt` | PRESENT (goal creation uses its own private `StepProgressBar`) |
| `TimeInfoView`, `ExercisesInfoView`, `TrainingLevelInfoView` | `InfoViews.kt` | PRESENT |
| `LocationInfoView` (inside `TrainingLevelInfoView.swift`) | — | MISSING |
| `FLImageView` (MediaRef, URL resolve, local fallback, 403/404 retry with fresh SAS, memory cache) | `FLImageView.kt` + `media/MediaRef.kt` (has `ProgressPhoto`) | PRESENT, but never used for progress photos |
| `ShareSheet` (share with a user: debounced `/users/search`, sends a content share) | — | MISSING. Android `ContentActionsRow.kt` only opens the system share sheet; `ContentApi.share` (`POST content/share`) exists but nothing calls it |
| `PendingShareBanner` (accept/decline an incoming share) | — | MISSING |
| `SharedBadge` ("Shared" pill) | — | MISSING |
| `ContentFilterComponents`: `FilterChip`, `FavoritesFilterControl`, `ClearFiltersButton`, `EquipmentChipStrip`, `AnatomyChipStrip`, `ContributorFilterMenu` | only a private `FilterChip` in `ui/workout/ExerciseBrowserSheet.kt`; Material `FilterChip` used elsewhere | MISSING as shared components (no favorites toggle, equipment/anatomy chip strips, or contributor menu) |
| `NotificationsView` + `NotificationRowView` | `ui/notifications/NotificationsScreen.kt` (row inside) | PRESENT (outside this domain; not compared in depth) |
| `NotificationPreferencesView` (category switches) | folded into `ui/profile/NotificationSettingsScreen.kt` | PARTIAL / merged |
| `NotificationPrePromptSheet` (in-app explainer before the system push prompt, from Home) | — | MISSING |
| Buttons: `PrimaryButtonView`, `SecondaryButtonView`, `ActionButtonView`, `SocialSignInButton`, `PlayButtonView`, `PauseButtonView` | `Buttons.kt` | PRESENT |
| Buttons: `InteractiveButtonView`, `PlaylistButtonView` | — | MISSING as components (small image buttons; iOS uses `PlaylistButtonView` in `PlaylistView`) |

Goals-local iOS components and their Android counterparts:
- `StreakBadge` → `ui/goals/StreakBadge.kt`: PRESENT, adds tier colouring.
- `AchievementBadge` → `ui/goals/AchievementBadge.kt`: PRESENT.
- `StatCard`: missing in goals (only a private one in `SessionDetailScreen`).
- `CalendarHeatmapView`: inline in `HabitDetailScreen`.
- `GoalProgressHeader`, `GoalProgressCard`, `GoalDetailCard`, `HabitCard`, `DailySummaryFooter`: MISSING.
- `MilestoneRowView`, `HabitRowView`: private composables in `GoalDetailScreen`.
- `SuggestedHabitCard`: private in `GoalHabitsStep.kt`.
- `GoalDirectionCard`: private `DirectionCard` in `GoalDirectionStep.kt`.
