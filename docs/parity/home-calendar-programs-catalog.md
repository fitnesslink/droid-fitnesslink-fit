# Parity gap report: home-calendar-programs-catalog

_Generated 2026-10-06 from a file-by-file iOS vs Android comparison. iOS is the spec._

# Feature parity: Home, Calendar, Programs and Catalog (iOS vs Android)

**Bottom line:** Android has the basic screens in this domain but very little of the program lifecycle. The Start Program flow, the enrollment model, My Program, Program Progress, moving a workout to another day, and accepting or declining a share are all missing. The Android home strip, dashboard tiles and My Content rail still run on mock data. The Start Program button on program detail is a no-op.

Paths below are relative to the repo roots:
- iOS: `/Users/quincywilliams/Repos/FitnessLink/iOS-fitnesslink-fit/FitnessLink/`
- Android: `/Users/quincywilliams/Repos/FitnessLink/droid-fitnesslink-fit/app/src/main/java/com/fitnesslink/fit/`

## 1. iOS features and their Android status

### HOME (iOS `Views/Home/*`, `ViewModels/HomeViewModel.swift`; Android `ui/home/HomeScreen.kt`, `viewmodel/HomeViewModel.kt`)

**Header with notification bell and unread badge** (`Views/Components/HeaderView`): opens Notifications; red count badge capped at 99, read from the local DB.
- **PARTIAL.** Android `ui/components/HeaderView.kt` has the bell and navigates to notifications, but shows no unread badge.
- `DatabaseManager.unreadNotificationCount()` exists on Android but only `NotificationsViewModel` uses it.

**Date scroll strip** (`DateScrollView`, `DateCellView`): today's day centred.
- iOS behaviour:
  - Status per day comes from real calendar rows: completed if every entry is done, otherwise scheduled. Days inside a program span with no workout are marked rest.
  - Each cell carries the workout title in its accessibility label.
  - It re-centres when the Home tab is re-selected (`.homeTabSelected` notification) and when the app returns to foreground (`selectToday`). Both also roll over to a new day or month.
  - It re-reads the local DB (`hydrateFromCache`) every time the screen appears.
- **PARTIAL.** Android `DateScrollView`/`DateCellView` exist and tapping a day works.
  - Statuses are fake: `MockDataProvider.calendarItems` marks every day before today completed, today scheduled.
  - No rest status, no title, no re-centre on tab or app return, no re-read on appear.
  - The strip starts at `currentDay-3` instead of centring today.

**Today's Workout card** (`TodayWorkoutView`):
- Title is "Today's Workout" or "Workouts on MMM d".
- Each row shows a thumbnail (falls back to the cached workout's image ids), a strikethrough when completed, and the scheduled time.
- Tapping uses `FitnessContent.tapRoute`: a completed row opens `completedWorkoutReport(workoutId, day)`, anything else opens workout detail.
- **PARTIAL.** Android `TodayWorkoutView`/`TodayWorkoutCard` list the day's entries with exercise count, duration and a Start pill.
  - No thumbnail, and no tap-to-report for completed rows (always opens workout detail).
  - Android has no completed-workout report route at all.

**Rest day versus nothing planned** (`RestView`, `NoWorkoutPlannedView`):
- iOS shows `RestView` only when the day falls inside a program's span (`DatabaseManager.programCalendarSpan`). It adds an "Up next tomorrow" link and a "Train anyway" link to Workouts.
- Otherwise iOS shows `NoWorkoutPlannedView` with three links: Create a workout, Start a program, Pick a workout.
- **PARTIAL.** Android always shows a plain `RestView` (text only) whenever the day is empty. There is no tomorrow preview, no Train anyway, and no "nothing planned" state.

**Quick actions row** (`HomeQuickActionsView`): New Workout, Programs, Log Food, and Set Goal (which opens the goal-creation sheet).
- **MISSING.**

**My Program home card** (`Views/Programs/MyProgramHomeCard.swift`):
- Hero image with a subtitle: "Week X of Y · Phase", "Starts <date>", or "Program complete".
- Two progress rows: weeks completed, and this week's workouts. Both use segmented bars (`ProgramProgressBar`).
- Links: View All goes to `myProgram`; the progress area goes to `programProgress`.
- Driven by `UserProgramStore.active()` and `snapshot()`.
- **MISSING.**

**Continue Where You Left Off** (`ResumeSessionBanner`):
- Lists in-progress sessions with percent done and elapsed time.
- Resume opens `playlistSession`.
- Dismiss asks for confirmation, then abandons the session (`ActiveWorkoutSession.discard`).
- Sessions older than the resume window are purged.
- **MISSING** from Android Home.

**Goal progress cards and Today's Habits checklist** (cross-domain):
- iOS shows goal cards (tap opens goalDetail) and a habits checklist with complete, undo, streaks and a daily progress footer.
- **PARTIAL.** Android VM loads `goalSummaries` and has `completeHabit`, but `HomeScreen` renders neither.

**Dashboard grid** (`HomeDashboardView`, `DashboardView`): four tiles computed from local data.
- The tiles are: Workouts this week (from sessions), Volume, Calories for the selected day, and Streak.
- Workouts tile has an editable weekly goal (`WeeklyGoalSheet` stepper, stored in UserDefaults `weeklyWorkoutGoal`); without a goal it shows a "Set weekly goal" prompt instead.
- Calories goal edit routes to `nutritionGoalSettings`.
- Each tile links somewhere: nutritionHome, achievements, or workoutReport.
- **PARTIAL.** Android shows static mock tiles (`MockDataProvider.dashboards`, seeded into the `home_dashboards` table). Nothing is computed, there is no goal editing, and tiles don't link anywhere.

**Server refresh for Home**:
- iOS drains goals, habits, workouts and calendar queues first.
- It then calls `GET /calendar/me?startDate&endDate` for the selected day, skipping rows with pending deletes or reschedules.
- It also calls `GET /program-enrollments/me` via `UserProgramStore.syncFromServer()`.
- **PARTIAL.** Android Home only pulls goals (`goalApi.list()`). It never calls the calendar API.

### CALENDAR (iOS `Views/Calendar/*`, `ViewModels/CalendarViewModel.swift`, `Services/API/CalendarService.swift`; Android `ui/calendar/CalendarScreen.kt`, `viewmodel/CalendarViewModel.kt`, `network/api/CalendarApi.kt`)

**Month grid with navigation** (`MonthCalendarView`, `CalendarCellView`):
- Previous/next month chevrons, and a wheel month/year picker sheet opened from the header.
- The server refresh covers the visible month.
- **PARTIAL.** Android shows only the current month.
  - The month header and down-arrow are a no-op clickable; there are no prev/next controls.
  - Cell status is wrong: a scheduled day before today is marked "completed" by date, not by the entry's status. iOS uses real status, with completed taking priority over scheduled.
  - Selection styling differs: Android uses a filled circle, iOS a 1.5pt ring.

**Tap selects the day only**, plus a custom anchored add menu (`CalendarAddMenu.swift`):
- Four triggers open the same menu:
  - the header +;
  - a + badge on the selected cell;
  - long-press on a day (0.45s, with tap suppression afterwards);
  - a dashed inline row "Add to <EEE, MMM d>".
- The menu is a branded popover (`CalendarAddMenuPopover`) with four actions: Schedule a Workout, Schedule a Program, Create New Workout, Create New Program.
- The chosen sheet opens only after the popover has fully dismissed (`chooseAddAction` / `runPendingAddAction`).
- **PARTIAL.** Android: tap selects the day; the header + opens the workout picker directly. There is no menu, no cell badge, no long-press, no inline add row, and no schedule-program or create-new options.

**Day list** (`DateContentView`):
- Header reads "EEE, MMM d". Empty state is "Nothing scheduled".
- Each row shows title, a time range (start time plus duration, falling back to the workout's estimate), and a program badge with the program name.
- A ⋯ menu and long-press context menu offer "Move to Another Day" and "Remove from Calendar".
- The row's main area navigates via `tapRoute`, so completed entries open the report.
- **PARTIAL.** Android `ScheduledContentView`/`DateContentView` show title and a status dot.
  - Tapping the row opens a dropdown with only "Remove from Calendar" (no confirmation).
  - The arrow icon opens workout detail.
  - No time, duration, program badge, move option, or completed-to-report routing.

**Delete a calendar entry**:
- iOS asks for confirmation. For a program workout it offers a scope choice: "Remove just this workout" or "Remove whole program (N workouts)".
- `CalendarService.remove` then does the full teardown: EventKit unmirror, discard any same-day in-progress session, delete the local row, and queue the server DELETE in `sync_queue` (entity type calendar).
- A 4xx response is treated as final. A pending-delete set stops the next server pull from re-inserting the row.
- **PARTIAL.** Android does `DatabaseManager.deleteCalendarContent`, then fires `DELETE /calendar/{id}` once and ignores the result.
  - Nothing is queued and nothing guards against the pull re-inserting the row.
  - No confirmation and no whole-program scope.
  - No device-calendar unmirror: `CalendarSyncService.removeEvent` is never called.

**Multi-select bulk delete**: Select/Done toggle, checkboxes, Select All / Deselect All, "Delete (N)", and a confirmation dialog (`deleteEntries`).
- **MISSING.**

**Schedule Workout picker** (`CalendarWorkoutPickerSheet`):
- Excludes workouts that belong to any program (`workoutIdsInAnyProgram`).
- Search box.
- Filters: All/Favorites segmented control; a "Mine" chip (`contributorId == nil`, mutually exclusive with a coach); a contributor menu; equipment and anatomy chip strips built from `GET /workouts/list-view`; "Clear filters".
- **PARTIAL.** Android `CalendarWorkoutPickerSheet` (inside `CalendarScreen.kt`) has search plus a Material `DatePickerDialog`. No filters and no program-workout exclusion.

**Schedule date picker** (`ScheduleDatePickerSheet`):
- Graphical date picker limited to today onward, plus a time picker.
- Optional duration chips: 15/30/45/60/75/90, with the workout's own estimate merged in and pre-selected. The chosen duration is stored as `durationMinutes`.
- Custom title and CTA text.
- **PARTIAL.** Android picks a date only. No time, no duration, and past dates are allowed.

**Schedule Program picker** (`CalendarProgramPickerSheet`):
- Lists only programs that have local schedules (`programIdsWithSchedules`).
- Same filter bar as the workout picker; filter maps come from `GET /programs/list-view`.
- Start-date sheet with CTA "Add to Calendar", then `ProgramScheduler.addToCalendar`.
- **MISSING.**

**Create new from the calendar**: `WorkoutEditorView(onSaved:)` or `ProgramEditorView(onSaved:)` opens as a sheet. On save, the new workout is scheduled on the selected day, or the new program is laid down starting that day.
- **MISSING.** Neither Android editor has an onSaved callback.

**Move to another day**:
- `ScheduleDatePickerSheet` opens seeded with the entry's current date and time.
- `CalendarService.reschedule`: update the local row, re-mirror to EventKit, queue `PUT /calendar/{id}` with `{scheduledDate}` only (queue payload `RescheduleQueuePayload`).
- The view then follows the entry, switching month if needed.
- **MISSING.** `CalendarApi.update` exists on Android but nothing calls it.

**Program scheduling into the calendar** (`Services/API/ProgramScheduler.swift`):
- One `enrollmentId` per run.
- Each workout lands at its relative offset from the program's first slot (week/day numbering is ignored).
- Every entry is stamped with `programId`, `programEnrollmentId`, `programWeekNumber`, `programDayNumber` and program/workout image ids.
- Each entry is mirrored to EventKit, then sent via `POST /calendar`.
- `ProgramEnrollmentService.recordAdd` queues a POST to `/program-enrollments`.
- A `user_programs` row is written (via `upsertUserProgram`).
- **MISSING.**

**Calendar wire format** (`CalendarService`):
- `POST /calendar` sends nulls instead of empty strings and includes `programEnrollmentId`, `durationMinutes`, `programWeekNumber`, `programDayNumber`.
- `GET /calendar/me` reads image/thumbnail ids, enrollment id, duration and week/day.
- **PARTIAL.** Android posts the raw `FitnessContent`, which has only id, title, programId, workoutId, mealPlanId, status and scheduledDate (epoch long). Empty strings are sent, and the extra fields are absent from both the model and the `calendar_content` table.

**Device-calendar mirror**:
- iOS `Services/CalendarSyncService.swift` (EventKit) mirrors on every create, move and delete. Users pick the target calendar in `PreferencesView`.
- **PARTIAL.** Android `calendar/CalendarSyncService.kt` only runs a bulk `syncAll` from `SyncScheduler` plus a settings screen (`ui/profile/CalendarSyncSettingsScreen.kt`).
  - Nothing is mirrored inline from `CalendarViewModel`.
  - Removals are never mirrored.
  - It always writes to the first writable calendar.
  - Events are fixed at 1 hour and ignore `durationMinutes`.

**Re-read on appear** (`hydrateFromCache`): **MISSING** on Android (loads once in `LaunchedEffect`).

### PROGRAMS LIST (iOS `Views/Programs/ProgramsView.swift`, `ProgramItemView.swift`, `ViewModels/ProgramsViewModel.swift`; Android `ui/catalog/ProgramsScreen.kt`, `viewmodel/ProgramsViewModel.kt`)

**List of template programs only** (`contributorId != nil`, via `CatalogViewModel.templatePrograms`), in server ranking order (`setProgramSortOrder`), with stale contributor rows removed (`reconcileContributorPrograms`).
- **PARTIAL.** Android shows every cached program unsorted.
  - No contributorId column, so no template/mine split.
  - Server rows are upserted but never reconciled.

**Search**: iOS filters by name.
- **PARTIAL.** Android shows the `SearchView` component, but its text is local state that never filters anything; its filter icon is decorative.

**Filters**:
- iOS has: All/Favorites (`FavoritesFilterControl`), a contributor menu (`ContributorFilterMenu`, from `ContributorStore` / `GET /contributors?pageSize=200`), `EquipmentChipStrip`, `AnatomyChipStrip` (both from `GET /programs/list-view` `equipmentIds`/`anatomyIds`), and `ClearFiltersButton`. These live in `Views/Components/ContentFilterComponents.swift`.
- **iOS has no level filter on Programs.**
- **PARTIAL.** Android has only Level chips (Beginner/Intermediate/Advanced) and Length chips (1–4, 5–8, 9–12, 13+ weeks). Favorites, contributor, equipment, anatomy and clear-all are all missing.
- `Views/Workouts/MoreFiltersSheet.swift` (anatomy, equipment, difficulty, goal, movement pattern, location) is only used by `MovementsView` and `ExerciseBrowserSheet`, not by Programs.

**Card/list layout toggle** (`ContentLayoutToggle`, `@AppStorage("catalogContentLayout")`; `ProgramRowView` is the compact row): **MISSING.**

**Favorite heart toggle on rows**:
- Optimistic local flip, then `POST /content/favorites/{id}?kind=program`, which returns `{isFavorited}`. iOS reconciles to that value and reverts on error.
- On refresh, `GET /content/favorites` replaces the local flags (`replaceFavoritePrograms`).
- **PARTIAL.** Android shows a heart image, but `isFavorite` is hard-coded false in `DatabaseManager.allPrograms`. It isn't tappable and there is no favorite column.

**"Shared" badge** (`SharedBadge`, `sharedByUserId`): **MISSING.**

**+ toolbar button opens the program editor**: **PRESENT.**

### PROGRAM DETAIL (iOS `Views/Programs/ProgramDetailView.swift`, `ViewModels/ProgramDetailViewModel.swift`; Android `ui/catalog/ProgramDetailScreen.kt`, `viewmodel/ProgramDetailViewModel.kt`)

**Hero image and back button**: **PRESENT.**
- Android passes the program id into `MediaRef.ProgramThumbnail`; iOS passes the thumbnail or image media id. Worth verifying.

**Ownership gating of header actions**:
- iOS shows edit, delete and share only when `contributorId == nil` and the program wasn't shared to the user.
- For a program shared to the user it shows "Remove from library" instead: `DELETE /content/shared/{shareId}`, with the share found via `GET /content/shared`.
- **PARTIAL.** Android always shows Edit, Delete, Favorite and Share. No template or shared gating, and no remove-from-library.

**Share**:
- iOS `Views/Components/ShareSheet.swift` searches users (`/users/search`) and sends `POST /content/share {contentId, kind, sharedWithUserId}`.
- **PARTIAL.** Android `ui/components/ContentActionsRow.kt` uses the system text-share chooser; there is no in-app user share.

**Favorite on detail**: iOS has no heart on the detail screen; favoriting happens on list rows. Android has one (see section 2).

**Pending share banner** (`Views/Components/PendingShareBanner.swift`):
- Shows "<sharer> shared this program with you" with Accept / Decline.
- Accept: `POST /content/shared/{id}/accept`. Decline: `POST /content/shared/{id}/decline`, delete the local copy, and pop the screen.
- While a share is pending, the program is preview-only: it isn't cached and the CTA is hidden.
- **MISSING.**

**Info chips**: multi-level `trainingLevels`, time, location (`LocationInfoView`) in a FlowLayout.
- **PARTIAL.** Android shows a single training level and time; no location.

**Sneak Peek reel**: 2–5 movement demo videos (`ProgramSneakPeekReelView`, full-screen pager with AVPlayer).
- **MISSING.**

**Overview, "Who This Program Is For"** (`whoIsFor`, `bestFor`; `ProgramAudienceView`), **and Equipment Needed chips** (`ContentTagChipRow`):
- **PARTIAL.** Android shows the plain description only. The audience fields and equipment are missing.

**Schedule section** (`ProgramScheduleSectionView`):
- Week chips (WK 1…n) and a Base/Build/Peak phase rail (programs of 3+ weeks).
- One week's day list at a time: rest rows, and workout cards with time and focus area that expand to list exercise names.
- `rebuildScheduleWorkoutInfo`; data hydrated by `GET /workouts/{id}` with 4 requests in flight.
- **PARTIAL.** Android `ProgramDetailWeekView` stacks every week as a 7-column grid of names or "Rest".
  - It reads local schedules only and never calls `GET /programs/{id}/weekly-workouts`, so a program never opened in the editor shows no schedule.
  - No week picker, phases, expansion or focus.

**About the Coach card** (`ProgramContributorView`): avatar, headline, bio, credentials, achievements and social links. Hidden for the FitnessLink house contributor.
- **MISSING.**

**State-aware primary CTA** (`ProgramActionView`, `ProgramCTA.current`):
- Start Program goes to `startProgram`.
- View My Program goes to `myProgram`.
- Start Today's Workout goes to workout detail, with a secondary "View My Program" link.
- View Results goes to `programProgress`.
- Disabled until the schedule loads. Helper text: "Choose your start date…".
- **MISSING.** Android's "Start Program" button has an empty `clickable {}`.

**Delete program**: confirmation alert, local delete, `DELETE /programs/{id}`. **PRESENT.**

### START PROGRAM FLOW (iOS `Views/Programs/StartProgramView.swift`, `Services/API/UserProgramStore.swift`, `ProgramEnrollmentService.swift`)

**Start Your Program screen**: **MISSING.**
- Header with thumbnail, levels and location.
- Stats: workouts per week, time per workout, weeks.
- Graphical date picker from today onward.
- "Your Program Timeline": each week's date range and phase (`UserProgram.weekRangeLabel`, `ProgramPhases`).
- Loads `GET /programs/{id}` and `/weekly-workouts` if they aren't cached. Start is disabled with an explanation when there is no schedule.

**One active program at a time**: **MISSING.**
- Alert "You already have an active program." with Keep Current Program / Replace Program.
- Replace runs `UserProgramStore.remove`: removes calendar entries, queues a POST to `/program-enrollments/{id}/removal`, deletes the `user_programs` row.
- Restart passes `replacing: enrollmentId`.

**"You're All Set!" full-screen confirmation** (`ProgramStartedView`): View My Program or View Calendar, both resetting the navigation stack (`AppRouter.resetStack`). **MISSING.**

**Enrollment sync**:
- Durable queue: `ProgramEnrollmentRecord` contains enrollmentId, userId, programId, contributorId, startDate, programName, splitId, weeks, workoutCount, source, appVersion, status.
- Drained by `SyncManager.drainProgramEnrollments`.
- Pull: `GET /program-enrollments/me` rebuilds instances on a fresh install or second device, fetching the full calendar range for each.
- **MISSING.** None of `/program-enrollments`, `/program-enrollments/{id}/removal` or `/program-enrollments/me` exists in Android `ProgramApi`.

### MY PROGRAM (iOS `Views/Programs/MyProgramView.swift`) and PROGRAM PROGRESS (`ProgramProgressView.swift`)

**MY PROGRAM is entirely MISSING.** It covers:
- **Header and tabs:** top bar with a ••• menu (Restart Program with confirmation, Find Another Program, Remove Program with confirmation); a hero image; chips; tabs Week Plan / Overview / Coach.
- **Week Plan:**
  - week selector circles with a completed check and phase label;
  - "Week X of Y · Phase" with a View Progress link;
  - a week progress row;
  - day rows by real date (rest rows, workout rows with check state, thumbnail, duration · exercise count · focus via `ProgramWorkoutMeta`);
  - completed rows open the report;
  - incomplete rows have a ••• menu with "Move Workout";
  - swiping horizontally changes week;
  - a completion card ("Program Complete", View Results, Start Another Program).
- **Move Workout sheet** (`MoveWorkoutSheet`): if the target day already has a workout, an alert offers "Move Anyway" or "Choose Another Day". `UserProgramStore.move` keeps the time of day and calls `CalendarService.reschedule`.
- **Overview tab:** description, audience, structure table, equipment.
- **Coach tab:** the contributor card, with an offline message.
- **Empty state:** "Browse Programs".

**Program Progress**: overall progress by weeks and by workouts, plus a weekly breakdown. **MISSING.**

**Data model behind both** (`UserProgram`, `ProgramSnapshot`, `ProgramWeekPlan`, `ProgramDayPlan`, `ProgramJourneyState`; `user_programs` table): **MISSING.**
- `UserProgramStore.active()` picks: the running program, else the next upcoming, else the latest open, else one completed within the last 14 days.
- `UserProgramStore.openProgramEntry(workoutId:)` handles completing a workout on any day (cross-domain).

### PROGRAM EDITOR (iOS `Views/Programs/ProgramEditorView.swift`, `WeekSectionView`, `DaySlotView`, `WorkoutPickerSheet`, `ViewModels/ProgramEditorViewModel.swift`; Android `ui/catalog/ProgramEditorScreen.kt`, `viewmodel/ProgramEditorViewModel.kt`)

**Present on both:**
- New and edit modes; a dirty flag with a "Discard Changes?" alert.
- Name; estimated-time chips 15/30/45/60/90; description.
- Weeks: add, duplicate, delete (renumbering), with a per-week count badge.
- 7-day slots: tap an empty slot to pick a workout; an assigned slot offers Replace or Remove.
- Workout picker sheet with search and a "Week N, Day" label.
- Summary chips.
- Save writes locally, then `POST`/`PUT /programs` and one `POST /programs/{id}/weekly-workouts` per slot.

**Differences:**
- **Program image picker:** **MISSING** on Android. iOS uses `PhotosPicker` and saves a draft image file locally (the image isn't uploaded in the editor). Android's `draftImageFilename` field is unused.
- **Fitness level:** **PARTIAL.** iOS is multi-select (`trainingLevels`, `hasExplicitTrainingLevels`) with a "· from workouts" hint. Android is single-select `trainingLevel`.
- **Location chips** (Gym/Home/Outdoors/Track/Studio/Pool; `hasExplicitLocation`): **MISSING.**
- **Equipment and anatomy tag editors** (`ContentTagsEditorSection`, `ContentTagSelection`): seeded from the live `GET /programs/{id}` and sent as `equipmentIds`/`anatomyIds` in `ProgramWriteRequest` (nil means unchanged, [] means clear). **MISSING.**
- **Drag and drop to swap slots** (`.draggable` / `.dropDestination` keyed "week:day"): **PARTIAL.** Android's VM has `moveWorkout`, but no UI calls it.
- **"Create New Workout" inside the picker** (creates a workout and auto-assigns it to the slot): **MISSING.**
- **Template guard:** iOS closes the editor if `contributorId != nil`. **MISSING.**
- **onSaved callback** for the calendar's create flow: **MISSING.**
- **Save gating:** iOS does **not** gate Save (always enabled). Android disables Save until at least one workout is assigned.

### CATALOG HUB (iOS `Views/Catalog/*`, `ViewModels/CatalogViewModel.swift`, `MyContentViewModel.swift`; Android `ui/catalog/CatalogScreen.kt`, `viewmodel/CatalogViewModel.kt`)

**Header bell**: iOS `HeaderView` on Catalog links to notifications. **PARTIAL** on Android: there's no unread badge, and Android's `CatalogScreen` passes no tap handler to `HeaderView`, so the bell does nothing there.

**First-load spinner** ("Loading your library…"): **MISSING.**

**My Content rail** (`MyContentScrollView`):
- Always shown.
- A + menu creates a Workout, Program or Movement (`movementEditor`).
- "See All" opens `myContent`.
- Cards show a kind badge and "Created <date>". Items are newest-first and include user-created movements.
- **PARTIAL.** Android's rail is hard-coded mock data (`MockDataProvider.myPrograms + myWorkouts`).
  - Its empty-state "+ Workout" / "+ Program" can never appear because the mock list is never empty.
  - No movements, no See All, no kind badge or created date.
  - Tapping a custom program opens program detail (the code comment claims it opens the editor).

**Programs and Workouts rails** (templates only, server-ranked):
- **PARTIAL.** Android rails list every cached program/workout with no contributor split.
- iOS also hides workouts that belong to a contributor program (`catalogVisibleWorkouts`, `ProgramService.backfillContributorProgramSchedules`, which fetches up to 6 schedules concurrently). Android does not.

**Movements rail** (`MovementScrollView`, links to `movements` and `movementDetail`): **MISSING** from the Android catalog.

**Catalog refresh**:
- iOS fetches `GET /workouts/list-view`, `/programs/list-view` and `/movements/list-view` in parallel, upserts, reconciles, stamps sort order, and batch-primes media URLs.
- **MISSING.** Android `CatalogViewModel` reads only the local DB and makes no network call.

**My Content "See All" screen** (`MyContentView`):
- Search; kind chips (Workouts/Programs/Movements); Favorites; equipment and anatomy strips; Clear.
- Card/list layout toggle; + create menu; rows show "Created" dates.
- **MISSING.** No route exists on Android.

**List-view filter params**: `ContentFilterQuery` adds `contributorId`, `personalOnly` and `favoritesOnly` to the list-view endpoints. **MISSING** from Android `ProgramApi.listView()`, which takes no params.

**Contributor rail and contributor filter** (`ContributorService`/`ContributorStore`, `GET /contributors`):
- iOS uses this for the filter menu and name lookup. There is no dedicated contributor rail in the iOS catalog; contributor content appears as the template rails.
- **MISSING** on Android.

**Quick Start card**: present on iOS Home and Catalog but turned off (`QuickStartCard.isEnabled = false`). Not needed for parity now.

### Dead code worth knowing about
iOS `ProgramDetailViewModel.addProgramToCalendar`, `removeProgramFromCalendar`, `calendarInstances` and `removeInstance` are not referenced by any view; the Start Program flow replaced them. Don't port them.

## 2. What Android has that iOS lacks
- Programs list filters by Level and by Length in weeks (`ProgramsViewModel.levelFilter` / `lengthFilter`).
- Workouts list has a Level filter chip row and a "No workouts match this filter" empty state (`WorkoutsScreen.kt`).
- Favorite toggle on the Program Detail header (`ContentActionsRow`); iOS only has it on list rows.
- System share-chooser text share ("Check out this program on FitnessLink").
- Save is disabled in the Program Editor until a workout is assigned.
- An explicit Calendar Sync settings screen (`calendarSyncSettings` route) with a manual "sync all". iOS has the equivalent in Preferences, with a calendar picker.
- Edit and Delete are visible on every program, including templates. This is a gap rather than a feature: iOS hides them.

## 3. Android conventions to follow

**ViewModels**
- `androidx.lifecycle.ViewModel` using Compose `mutableStateOf` with `by` delegates; no StateFlow, Hilt or Koin.
- Obtained in the screen with `viewModel()` and loaded via `LaunchedEffect(Unit|id) { vm.loadData(...) }`.
- Typical shape:
  - `loadData()` reads `DatabaseManager` synchronously first;
  - then `viewModelScope.launch { refreshFromServer() }`;
  - which returns early if `!NetworkMonitor.isConnected.value`;
  - wraps the API call in try/catch `/* use cached */`;
  - upserts into the DB and re-reads.
- Sheet visibility flags live on the VM (e.g. `showWorkoutPicker`).

**Network**
- `network/ApiClient.kt` is a singleton with lazy Retrofit services: `programApi`, `calendarApi`, `contentApi`, `workoutApi`, etc.
- Gson; date format `yyyy-MM-dd'T'HH:mm:ss'Z'`.
- Auth interceptor adds a Firebase token (`AuthManager.getIdToken`).
- Base URL per service: `ApiConfiguration.baseUrl(ApiService.CORE|NUTRITION|NOTIFICATION)`.
- API interfaces are Retrofit `suspend` functions with `@GET/@POST/@PUT/@DELETE`, `@Path`, `@Query`, `@Body`. Request DTOs are defined next to the interface (e.g. `AddWeeklyWorkoutRequest` in `ProgramApi.kt`) or in `network/dto/SyncDtos.kt` (`PaginatedResponse`, `ShareContentRequest`).
- New work will need endpoints iOS already calls:
  - `/program-enrollments`, `/program-enrollments/{id}/removal`, `/program-enrollments/me`;
  - `/content/shared/{id}/accept|decline`, `DELETE /content/shared/{id}`, `DELETE /content/share/{id}`;
  - `/content/favorites/{id}?kind=` returning `{isFavorited}`;
  - `/contributors`;
  - list-view query params;
  - `/notifications/me/unread-count`.

**Local DB**
- `persistence/DatabaseManager.kt` is a hand-written `SQLiteOpenHelper` singleton (no Room). `DB_VERSION = 10` with `migrateToVn` functions, and raw `rawQuery`/`insertWithOnConflict` with Cursor helpers.
- Relevant tables: `programs` (id, name, time, location, trainingLevel, description, weeks), `program_schedules`, `calendar_content` (id, title, programId, workoutId, mealPlanId, status, scheduledDate INTEGER millis), `workouts` (phasesJson), `home_dashboards`, `notifications`, `sync_queue`.
- To match iOS you'd need migrations adding:
  - to `programs`: contributorId, imageId/thumbnailId, isFavorite, sharedByUserId, sortIndex, createdAt, whoIsFor, bestFor, weekPhases, trainingLevels;
  - to `calendar_content`: programEnrollmentId, durationMinutes, programWeekNumber/programDayNumber, workout/program image ids;
  - a new `user_programs` table.
- Offline writes go through `DatabaseManager.enqueueSyncEntry(entityType, entityId, action, payload)` and `sync/SyncManager.kt` (`pull(domains)`, push loop). `SyncScheduler` also triggers `CalendarSyncService.syncAll`.
- Seed and mock data: `persistence/DatabaseSeeder.kt`, `data/MockDataProvider.kt`.

**Models**
- Plain Kotlin data classes in `model/` (`Program`, `ProgramList`, `ProgramWeek`/`ProgramDaySlot` with dayNumber 1=Mon…7=Sun, `FitnessContent`, `CatalogItem` with Kind PROGRAM/WORKOUT only, `CalendarCell`, `HorizontalCalendar`, `HomeDashboard`).
- API-aligned types in `model/api/` (`ProgramSchedule` with `EntityID` = UUID, `AuditFields`, `FavoritedContent`, `Contributor`).

**Navigation**
- `ui/navigation/AppNavigation.kt`: one `NavHost` inside `MainTabNavigation` with five bottom tabs (home, catalog, nutrition, calendar, profile).
- Routes are string literals; `composable("programDetail/{programId}")` with `navArgument`.
- Screens receive lambdas (`onBack`, `onNavigateToX`) rather than the navController.
- `AppRoute.kt` is a sealed class of route constants with `ROUTE`/`createRoute`, only partly used.
- New routes needed: startProgram/{programId}?replacing=, myProgram?enrollmentId=, programProgress?enrollmentId=, myContent, movements, completedWorkoutReport.

**UI and theme**
- Material3 Compose with custom styling throughout.
- `ui/theme/Color.kt`: FLPrimary #23AF8D, FLPrimaryDark, BackgroundColor #F0F4F5, TextSecondaryColor, OrangeTheme, PurpleTheme, BlueTheme, MediumGray, HighlightState, CalendarLine, White.
- `FitnessLinkTheme` lives in `Theme.kt`.
- Shared components in `ui/components/`: `HeaderView`/`HeaderBackView`, `SearchView` (not bindable today), `BackCircleButton`, `Buttons.kt` (`PrimaryButtonView`), `InfoViews.kt` (`TrainingLevelInfoView`, `TimeInfoView`), `FLImageView(ref = MediaRef.X(id), height)`, `SeparatorView`, `ContentActionsRow`.
- Patterns: drawable resources via `painterResource(R.drawable.*)`; `ModalBottomSheet` (skipPartiallyExpanded, `fillMaxHeight(0.85f)`) for pickers; `AlertDialog` for confirmations; `DropdownMenu` for context actions; chip styling is a clip/background/border Box (see `FilterChipRow` in `ProgramsScreen.kt`).

**Tests**: only `app/src/androidTest/.../DatabaseIntegrationTests.kt`. `DatabaseManager.initializeForTesting()` gives an in-memory DB.
