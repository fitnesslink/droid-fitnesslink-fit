# Parity gap report: workouts-editor-movements

_Generated 2026-10-06 from a file-by-file iOS vs Android comparison. iOS is the spec._

# Workouts, editor, AI generation and movements: iOS vs Android parity

**Bottom line:** Android has a local-only skeleton of the workout builder. Nothing the Android editor or Create Exercise produces ever reaches the server.
- `WorkoutEditorViewModel.save()` and `CreateExerciseSheet` only write SQLite (`DatabaseManager.insertWorkout` / `insertMovementFull`).
- No sync queue entry is made for workouts or movements; `enqueueSyncEntry` is only used by hydration.
- `ApiClient.workoutApi` and `ApiClient.movementApi` are only ever used for GETs (`listView`, `get`).
- `ClassificationApi` is wired but never called.

Path roots used below: iOS = `/Users/quincywilliams/Repos/FitnessLink/iOS-fitnesslink-fit/FitnessLink/`, Android = `/Users/quincywilliams/Repos/FitnessLink/droid-fitnesslink-fit/app/src/main/java/com/fitnesslink/fit/`.

---

## 1. iOS features and their Android status

### A. Workouts list (iOS `Views/Workouts/WorkoutsView.swift`, `ViewModels/WorkoutsViewModel.swift`)

| Feature | Android | Notes |
|---|---|---|
| Search by name | **Missing (broken)** | Android `ui/components/SearchView.kt` keeps its own text state, isn't bound to anything, and the filter icon does nothing. |
| Favorites-only toggle (`FavoritesFilterControl`) | **Missing** | |
| Heart toggle on each card, synced to the server | **Missing** | iOS uses `POST /content/favorites/{id}` and hydrates from `GET /content/favorites`. Android `WorkoutItemView` heart is a static image; `DatabaseManager.allWorkouts()` hard-codes `isFavorite=false`. |
| Contributor filter menu (`ContributorFilterMenu`, `ContributorStore`) | **Missing** | |
| Equipment and anatomy chip filters | **Missing** | iOS filters on the `equipmentIds` / `anatomyIds` the server returns from `/workouts/list-view`. |
| Clear-filters button | **Missing** | |
| Card/list layout toggle (`ContentLayoutToggle`, `WorkoutRowView`) | **Missing** | |
| Quick Start (Quick Start PRD, 2026-10: `QuickStartViewModel`, `ui/quickstart/*`, `quickStart` + `quickStartCanvas/{id}` routes, `ExerciseBrowserSheet` multi-select + suggestions) | **Built** 2026-10-09 | Exercise RPE / Training Log / training focus are local-only (`session_exercise_notes`, V12) until the sessions API carries them. |
| "+" to open a new editor | **Present** | |
| Template-only visibility (`visibleTemplates`) | **Missing** | iOS hides user-authored workouts and those inside a contributor program (`partOfContributorProgram`). Android lists everything. |
| `listView` query params (`contributorId` / `personalOnly` / `favoritesOnly` via `ContentFilterQuery`) | **Missing** | Android `WorkoutApi.listView()` takes no params. |
| Time display (`displayTime`) | **Missing** | iOS uses the configured `estimatedTime` (seconds) → "45 min", or a content estimate → "~38 min". Android shows `time`, which is blank for server rows (it never reads `estimatedTime`). |

Android also has a list-view caching bug: list-view rows are written with `CONFLICT_REPLACE` and `phasesJson=[]`, which wipes cached phases until detail is reopened.

### B. Workout detail (iOS `WorkoutDetailView.swift`, `WorkoutDetailViewModel.swift`, `WorkoutActionView.swift`, `WorkoutTasksView` / `WorkoutMovementView` / `WorkoutRestView` / `AdvancedMovementsView`)

Overall: **Partial.**

**Present on Android:**
- Hero image, name, single training level, time, exercise count, description.
- Phase task list.
- Add to Calendar date picker.
- Start/Continue button — but `hasSession` is never set, so it always says "Start Now".

**Missing on Android:**
- **Owner actions:** edit (`AppRoute.workoutEditor`), delete with confirmation, and share-to-user. Android has no edit entry point from detail at all; `AppRoute.WorkoutEditor.createRoute` is never called.
- **Share-to-user** (`Views/Components/ShareSheet.swift`): debounced `GET /users/search?q=&limit=` → `POST /content/share {contentId, kind, sharedWithUserId}`. Android's `ContentActionsRow` only fires a system text share intent. `ContentApi.share` exists but is unused, and there is no users-search API on Android.
- **Customize (fork-to-edit):** `POST /workouts/{id}/fork` via `WorkoutEditorViewModel.customizeFork`, with error mapping for 400/404/409 and navigation to the editor on the fork. The fork carries `preBuiltTemplateId` = source id. Android has nothing.
- **Shared-in content:** SharedBadge; PendingShareBanner accept/decline (`/content/shared/{id}/accept|decline`); remove from library (`DELETE /content/shared/{id}`).
- **Header metadata:** multiple training levels (`trainingLevels`), Location info, `displayTime` including the content estimate.
- **Tags:** "Equipment Needed" chip row (`ContentTagChipRow` from `equipmentIds` plus `equipment` names). "Muscles Worked" exists but is currently feature-flagged off.
- **WorkoutStructureView:** phase-summary rows that scroll to the phase.
- **Task tap → `MovementInfoSheet`:** hero, Watch Demo video, tags, prescription stats, coaching cue, structured Setup/Execution/Breathe/Tips sections.
- **Interval display:** `IntervalMetricsGrid` (Work/Rest/Rounds/Sets/Rest Between Sets/Total).
- **Session handling:** "About the Coach" contributor card; in-progress session resume card plus Restart from beginning and Discard session; loading overlay; reload after `.workoutSaveDidCommit`.

**Android bug:** `AdvancedMovementsView` renders `"${row.rounds} rounds"`, but `row.rounds` already contains "3 rounds", so it shows "3 rounds rounds".

### C. Workout editor shell and metadata (iOS `WorkoutEditorView.swift`, `WorkoutEditorViewModel.swift` — 2,796 lines; Android `ui/workout/WorkoutEditorScreen.kt` 777 lines plus a 382-line VM)

**Server persistence — Missing on Android.**
- iOS save writes locally, then:
  - Create: `POST /workouts` (`CreateWorkoutRequest` with `tasks: [WorkoutTaskInput]`, `estimatedTime` in seconds, `location`, `trainingLevels`, `equipmentIds` / `anatomyIds`, `statusId`, `preBuiltTemplateId`).
  - Edit: `PUT /workouts/{id}` (shell only, 204), then `POST /workouts/{id}/tasks/replace`.
- iOS also has an offline sync queue, a phase drain before saving, a fatal-vs-transient error banner, Save vs Save & Close, and server-id adoption and merge (`mergePreservingPhases`).
- Android `WorkoutApi.create/update` post the whole display model, which is the wrong shape. Android has no tasks/replace or fork endpoints.

**Header fields:**

| Field | Android | Notes |
|---|---|---|
| Name, description, discard-changes alert | **Present** | |
| Workout image (PhotosPicker → `MediaService.upload` → `imageId`) | **Missing** | Android shows a non-functional "Add Workout Image" placeholder. |
| Estimated time | **Partial** | Android has the 15/30/45/60/90 chips but only sets the string `time`. It has no custom-minutes field and never sets `estimatedTime` (iOS sets it in seconds). |
| Training level | **Partial** | Android is single-select (`trainingLevel` string). iOS is multi-select `trainingLevels[]` in canonical order (`toggleTrainingLevel`). |
| Location (Gym/Home/Outdoors/Track/Studio/Pool) | **Missing** | Android's model has `location`, but there is no UI and it is never sent. |
| Explicit equipment and anatomy tags (`ContentTagsEditorSection`, `ContentTagSelection`) | **Missing** | iOS wire semantics: untouched → key omitted (nil), edited → full list, reset → `[]`. Shows a "From movements" hint and "Reset to movement defaults". |
| "Generate with AI" button and auto-open (`workoutEditorGenerate(seedPrompt)` from onboarding `GetStartedView`) | **Missing** | |
| Open with a movement prefilled (`workoutEditorWithMovement`, from MovementDetail's "Build a Workout") | **Missing** | |
| Read-only workout opened in editor → fork prompt | **Missing** | |

### D. Phases (iOS `PhasePickerSheet.swift`, `WorkoutPhaseService.swift` with `WorkoutPhaseStore` and `WorkoutPhaseTemplateService`, `Models/API/AvailableWorkoutPhase.swift`)

| Feature | Android | Notes |
|---|---|---|
| Phase catalog | **Missing** | iOS phases are server catalog rows: `GET/POST /workout-phases`, `PUT/DELETE /workout-phases/{id}`, with offline create queue and id swap. System phases Warmup / Exercises / Cool Down have fixed GUIDs. Android creates free-text phases ("Warm Up" / "Main Workout" / "Cool Down") with random UUIDs, which are not valid `workoutPhaseId`s. |
| Add-phase picker (system vs custom, "Added" badge, inline create, duplicate-name errors) | **Partial** | Android has a free-text Add Phase dialog only. |
| Rename phase (server rename; system phases forbidden) | **Missing** | The Android VM has `renamePhase` but no UI. |
| Delete phase | **Partial** | Both allow deleting an empty phase from the workout. iOS also has catalog delete with an in-use error. |
| Duplicate phase (deep copy with remapped group ids) | **Missing** | |
| Save phase as template / insert template (`/workout-phase-templates`, `/workout-phase-templates/{id}`) | **Missing** | |
| Drag to reorder phases | **Missing** | |
| Move-to-phase (`MoveToPhaseMenuSection`, `moveRow`) and cross-phase drag-drop | **Missing** | |

### E. Exercise rows and set editor (iOS `MovementEditorRow.swift`, `SetEditorView.swift`)

| Feature | Android | Notes |
|---|---|---|
| Row actions | **Partial** | Android has up/down/remove and a tap-to-open dialog. iOS has inline expand, drag-drop reorder within and across phases plus an end-of-phase drop zone, Replace exercise, Move to phase, and Remove. Android's drag handle is decorative, and tapping a row enters multi-select. |
| Set editor | **Partial** | Android has sets/reps/weight text fields; the note says "per-set customization is coming". The weight label says kg but the metric renders "lbs". |

iOS set-editor features missing on Android:
- Reps/Time toggle (`usesDuration`, `durationSeconds`), with duration presets 30s/1m/2m/5m/10m plus custom mm:ss.
- Steppers.
- SetPreset chips. The model exists on Android but no UI uses it.
- "Edit each set individually" (`sameAcrossSets` / `setConfigs` / `TaskSetConfig`, with drop set and warm-up flags).
- "Different weight each round" for grouped members (`togglePerRound`).
- Rest-between-sets picker on straight sets (presets 15s–5m, None, Custom).
- Instructions / coaching cue field.
- Weight unit preference (`WeightUnit`).
- Interval-member behaviour: rep target only for EMOM/AMRAP, "Timed work" note for Tabata/HIIT.

"Distance" and "per-side" are not set-editor features on iOS either:
- `TaskSetConfig.distance` / `distanceUnit` exist only on session logs.
- "Per-side" only appears as a free-text instructions cue (e.g. "8 on both sides").

### F. Rests (iOS `RestEditorRow.swift`, `RestDurationPickerSheet.swift`)

| Feature | Android | Notes |
|---|---|---|
| Duration | **Present** | Android: seconds dialog with 30/60/90/120 presets. iOS has more presets. |
| Rest type Passive vs Active Recovery (`RestType`, `restTypeId` from `/classification/rest-types` with fallback GUIDs) | **Missing** | |
| Active-recovery movement picker (`restMovementPickerContext` → single-select browser, `restMovementId` plus media) | **Missing** | |
| Auto-insert rest when adding to a "main" phase | **Present** | |

### G. Groups: supersets and circuits (iOS `GroupEditorView.swift`, 730 lines)

**Present on Android:**
- Create a group via multi-select bar or "Superset/Circuit" buttons. The pending-group browser banner auto-finalizes a superset at 2 picks, as on iOS.
- Ungroup.

**Missing or partial:**
- Rounds stepper — **Missing in UI** (Android VM has `updateGroupRounds` but nothing calls it).
- Rest between rounds (`roundRestSeconds`) — **Missing**.
- Per-member rest after each member (`updateGroupMemberRest`) — **Missing**.
- Members are read-only on Android. iOS supports editing a member's sets, replacing, moving up/down within the group, and deleting a member (lead promotion via `migrateGroupLeadConfig`).
- "Add next movement" (fold the following loose movement into a circuit) — **Missing**.
- Remove whole group, and move the whole block to another phase — **Missing**.
- Done-button gating (`minimumGroupCount`) — **Missing**.
- `groupId` stamping and `hydrateGroupRounds` (infers 2 movements = superset, 3+ = circuit after a server round-trip) — **Missing**.

### H. Intervals: HIIT / Tabata / EMOM / AMRAP (iOS `IntervalEditSheet.swift`, `IntervalTimelinePreview.swift`, `IntervalModePickerSheet` inside `GroupEditorView`; model `IntervalMode` / `IntervalPlan` / `IntervalMath` / `IntervalMetrics` / `IntervalPreset` / `WorkoutDurationEstimator` in `Models/WorkoutCreationModels.swift`)

Overall: **Missing.** Android only has an "Interval" button that builds a generic group with "3 rounds" and requires at least 2 movements (iOS allows 1). No mode or timing is stored.

iOS features with no Android counterpart:
- Guided style picker.
- Summary card (`IntervalMetricsLine`).
- Edit sheet:
  - Timing: Work/Rest/Rounds; EMOM and AMRAP windows.
  - Sets plus Rest Between Sets (`intervalSetRestSeconds`).
  - Active Recovery: passive/active plus a recovery-exercise picker and in-group quick-picks.
  - Round-by-round timeline preview and a Total Time readout.
  - Coach notes; change style with a reset confirmation.
- Wire fields: `intervalMode`, `intervalWorkSeconds`, `intervalRestSeconds`, `intervalWindowSeconds`, `intervalSets`, `intervalSetRestSeconds`.
- Runtime: `ViewModels/IntervalRunner.swift` (session domain) is needed for intervals to actually play.

### I. Estimated duration
- Content-derived estimate (`WorkoutDurationEstimator`, `estimatedContentMinutes`, "~N min") — **Missing**.

### J. AI workout generation (iOS `GenerateWorkoutSheet.swift`, `GeneratedWorkoutReviewView.swift`, `AIWorkoutGeneratorViewModel.swift`, `AIWorkoutService.swift`, `Models/API/GenerateWorkout.swift`)

Overall: **Missing entirely** on Android. iOS flow:
1. **Prompt sheet:** prompt; optional duration chips, level, and equipment picker (`ClassificationPickerView`); offline banner; staged progress card.
2. **Request:** `POST /workouts/generate-from-prompt` (long-running 180s session), with error-envelope recovery for 400 safety, 502 and 504 (`errorReasonCode`, `errorDetail`).
3. **Response:** `DraftWorkout` and `DraftWorkoutTask`, including:
   - groupKey/groupType blocks and rounds/roundRest;
   - interval fields and `durationSeconds`;
   - `MovementResolution` (matched / ambiguous / unmatched, with candidates).
4. **Review screen:** counts of "N exercises · X from your library · Y new", block chips, Discard / Approve.
5. **Approve:** resolves movements, auto-creating personal movements via `POST /movements` for unmatched ones and skipping rest tasks. It builds phases and groups, then `WorkoutEditorViewModel.applyGenerated` merges the result into the editor unsaved.

### K. Exercise browser (iOS `ExerciseBrowserSheet.swift`, `ExerciseBrowserViewModel.swift`, `MoreFiltersSheet.swift`, `ClassificationPickerView.swift`)

Overall: **Partial.**

**Present on Android:** search, chips, All/Favorites/Recent tabs, "Create New Exercise", added-count badge, preview button, heart.

**Gaps:**
- **Search runs locally.** iOS calls `GET /movements/list-view` with `q`, `anatomyIds`, `equipmentIds`, `role=primary`, `difficultyIds`, `goalIds`, `movementPatternIds`, `locationIds`, `contributorId`, `personalOnly`, `favoritesOnly`, debounced. Android searches local SQLite only.
- **Filter chips.** iOS chips are the server taxonomy's primary anatomy/equipment (`ClassificationStore`). Android uses hard-coded `MuscleGroup` / `EquipmentType` enums matched against local columns that server sync fills with "", so the chips never match server movements.
- **"Mine" tab** — **Missing**.
- **Favorites and recents are local-only.** iOS uses `GET /movements/favorites`, `POST/DELETE /movements/{id}/favorite`, `GET /movements/recents`, `POST /movements/{id}/recent`.
  - Android bug: `insertMovementFull` uses `CONFLICT_REPLACE` with `isFavorite=0`, so every server refresh wipes local favorites.
- **More-filters drawer** (anatomy/equipment taxonomy plus Difficulty/Goal/Pattern/Location from `/personalization`) — **Missing**.
- **Single-select modes** (Replace Exercise, Recovery Exercise), the Done button for groups, and the "added" toast — **Missing**.

### L. Movement preview sheet (iOS `MovementPreviewSheet.swift`)
**Partial.** Android shows a thumbnail, local muscle/equipment strings, the description, and Add.

Missing: `GET /movements/{id}` hydrate, Watch Demo video (Mux, phone/landscape variants), taxonomy chips, and structured `MovementContentView` details.

### M. Movements catalog (iOS `MovementsView.swift`, `MovementsViewModel.swift`)
**Missing.** Android has no movements screen.

iOS has search, favorites-only, contributor filter, equipment and anatomy chips, More filters, card/list toggle, favorite hearts, and template-only visibility. Its catalog create entry, `MovementEditorView`, reuses `MovementCreateForm`.

### N. Movement detail (iOS `MovementDetailView.swift`)
**Missing.**

iOS has:
- Inline looping video hero (`InteractivePlayerView`) plus fullscreen player and poster.
- Anatomy/equipment chips, summary, and typed sections (Setup / Execution / Breathe / Tips).
- Share-to-user for own movements, remove-from-library for shared ones, pending-share accept/decline.
- Records a recent on open.
- "Build a Workout" CTA.

There is no per-movement history on this screen. Movement history (`GET /sessions/me/movement-history`, `DatabaseManager.movementHistorySets`) is consumed by the session/achievements domain only.

### O. Create New Exercise (iOS `CreateExerciseSheet.swift` = `MovementCreateForm`, `FormVideoSlot.swift`, `Services/Media/BackgroundUploadCoordinator.swift`, `MuxStatusPoller.swift`, `MovementVideoAttachmentCoordinator.swift`)

Overall: **Missing** server-backed creation and video. Android is a local-only dialog: name, description, a "Video URL" text field folded into the description, and single enum muscle and equipment.

iOS flow:
1. **Form fields:** name, description, and a still image uploaded via `MediaService.upload` that sets both `imageId` and `thumbnailId`.
2. **Tags:** multi-select anatomy and equipment from server taxonomy (`ClassificationPickerView`).
3. **Form video:** record with the camera (`VideoCaptureView`) or pick from Photos.
4. **Upload:** background `URLSession` multipart to `POST /media`, persisted across relaunch.
5. **Ingest polling:** `MuxStatusPoller` polls `GET /media/{id}` with backoff (2–30s, 40 attempts, early-out on stuck None).
6. **UI states:** progress → processing → ready (inline preview) or failed (with retry and remove).
7. **Create:** `POST /movements` with `CreateMovementRequest`; the server returns a bare GUID.
   - Android's `MovementApi.create` expects a `Movement` back, so it would mis-decode.
8. **Video attach:** `MovementVideoAttachmentCoordinator` PUTs `/movements/{id}` with `videoId` once the upload is Ready. Jobs are persisted in UserDefaults.

Android `MediaApi` has only `resolve` and `todayManifest`, so it cannot upload or poll.

### P. Classification
**Partial.** Android declares anatomy, equipment, training-levels, content-statuses and rpe endpoints but never calls them.

Missing:
- `/classification/workout-task-types` and `/classification/rest-types`.
- An in-memory `ClassificationStore` with name→id maps, primary chips, category grouping and fallback GUIDs (`WorkoutTaskTypeId`, `RestTypeId`, `WorkoutContentStatusId`).
- Android hard-codes only the Rest task-type GUID.

---

## 2. Things Android has that iOS lacks
- A training-level filter row on the Workouts list (All/Beginner/Intermediate/Advanced). iOS has no level filter.
- A favorite toggle on the Workout detail header (`ContentActionsRow`). iOS only toggles favorites from list cards.
- A generic system share-sheet text intent for any workout. iOS shares to in-app users only, and only for owned workouts.
- Tapping an editor row enters multi-select. This is a UX quirk, not a real feature.

Nothing else in this domain is Android-only.

---

## 3. Data-model distance and how much the editor needs reworked

**Shared skeleton.** Android mirrors iOS's legacy display structure: `Workout → phases[WorkoutPhase] → taskRows[TaskRow{task, advanced, rounds, totalRounds, isSuperset, isCircuit, advancedTasks}] → WorkoutTask` with the legacy flags. The server's `/workouts/{id}` payload decodes into both. Android's `ExerciseGroupType` and `TaskSetConfig` also match.

**Fields Android is missing, by class:**
- **`Workout`:** `trainingLevels[]`, `imageUrl` / `thumbnailUrl`, `libraryAddedAt`, `isSharedWithMe`, `sharedByUserId`, `equipmentIds`, `anatomyIds`, `hasExplicitEquipment` / `hasExplicitAnatomy`, `equipment[]`, `partOfContributorProgram`, `contributor`. Also helpers: `hydrateGroupRounds`, `allTasks`, `displayTime`, `formatEstimatedTime` / `seconds(fromDisplayTime:)`, `estimatedContentMinutes`.
- **`WorkoutPhase`:** `order`, `isSystem`.
- **`TaskRow`:** `isInterval`, `intervalMode`.
- **`WorkoutTask` — roughly 45 fields:**
  - Movement media: image/thumb/video ids and URLs, Mux status, aspect ratio, duration, phone-video variants.
  - Rest movement: `restMovementId`, name, and media.
  - Set modelling: `usesDuration`, `durationSeconds`, `sameAcrossSets`, `setConfigs`.
  - Intervals: `intervalMode` / `Work` / `Rest` / `Window` / `Sets` / `SetRestSeconds`, plus the `effectiveIntervalSetRestSeconds` helper.
  - Other: `roundRestSeconds`, `distance` / `distanceUnit`, the `RestType` enum, `displayMetric`.
- **Missing types:** `IntervalMode`, `IntervalPhase`, `IntervalPlan`, `IntervalMath`, `IntervalMetrics`, `IntervalPreset`, `WorkoutDurationEstimator`, `ContentTagSelection`, `AvailableWorkoutPhase`, the phase-template models, the `GenerateWorkout*` models, `SharedContent` / `UserSummary`.
- **Wire models (none exist on Android):** `WorkoutTaskInput`, `WorkoutTaskSetConfigInput`, `CreateWorkoutRequest`, `UpdateWorkoutRequest`, `ReplaceWorkoutTasksRequest`.
- **`MovementLibraryItem`:** Android uses free-text `muscleGroup` / `equipment` strings. iOS uses `anatomyIds` / `equipmentIds` arrays plus image/thumbnail/video/phone-video ids and URLs, `contributorId`, `sharedByUserId`, `createdAt`.
- **API `Movement`:** Android lacks video/phone-video status fields, `anatomyIds`, `equipmentIds`, `isFavorite`, sharing fields, `summary`, `sections` / `steps`.

**Android persistence loses data.** `DatabaseManager.taskToJson` only serializes the legacy fields. `movementId`, `workoutTaskTypeId`, `workoutPhaseId`, `weightKg`, `restTypeId`, `rounds`, `instructions` and the other API fields are dropped on every local save and load. Even a server-fetched workout loses its `movementId`s once cached. The SQLite schema (`workouts` columns plus `phasesJson`; `movements` with `muscleGroup` / `equipment` strings) would need migration.

**Phase identity is incompatible.** Android phase ids are random client UUIDs with free-text names. iOS uses server catalog phase ids (system GUIDs plus user phases) as `workoutPhaseId`. Android's ids would 400 on save.

**Android editor VM logic vs iOS:**
- Android has: add/remove exercise, auto-rest, up/down move, uniform sets/reps/weight, add/update/remove rest, create group/ungroup/rounds, multi-select, pending-group building, and a `forTask` helper that already walks `advancedTasks`.
- iOS adds everything listed in section 1, plus the `taskInputs` / `makeInput` flattening to the wire format, `groupId` assignment, the save/commit/queue/merge pipeline, and the fork flow.

**Recommendation.** Treat the Android editor as a rewrite of the VM and data layer rather than incremental patches:
1. Port the iOS models and `WorkoutCreationModels` math first.
2. Add the missing APIs: workouts create/PUT/tasks-replace/fork/generate; workout-phases and phase-templates; movements favorites/recents/filters and create returning a GUID; media upload and get; users search; classification task-types and rest-types.
3. Fix persistence: serialize the full `WorkoutTask` and migrate movement tags to id arrays.
4. Then rebuild the UI: SetEditor, Rest, Group, Interval sheets and timeline, PhasePicker, tags, AI flow, Movements and MovementDetail, and the video upload pipeline.

The existing Compose screens are reusable mainly for layout: header chips, phase sections, the browser shell.
