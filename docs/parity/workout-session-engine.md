# Parity gap report: workout-session-engine

_Generated 2026-10-06 from a file-by-file iOS vs Android comparison. iOS is the spec._

## Feature parity: Workout Session Execution (iOS compared with Android)

**Bottom line:** Android has almost none of this domain. Its whole session engine is one 255-line view model, `viewmodel/InteractiveSessionViewModel.kt`. It doesn't detect groups, intervals or phases, and it doesn't log or persist anything. Some code exists but is never reached:
- `saveSession()` is never called.
- `InteractivePlayerView` is never used. The session background is a grey placeholder box.
- The Interactive icon on the playlist screen has no click handler.
- `WorkoutDetailViewModel.hasSession` is never set to true.
- The session endpoints `start`, `get` and `getMySessions` are never called.

In practice, **finishing a workout on Android records nothing, locally or on the server.**

Paths below are relative to:
- iOS: `/Users/quincywilliams/Repos/FitnessLink/iOS-fitnesslink-fit/FitnessLink/`
- Android: `/Users/quincywilliams/Repos/FitnessLink/droid-fitnesslink-fit/app/src/main/java/com/fitnesslink/fit/`

---

### 1. iOS features and their Android status

**A. Session lifecycle** (`ViewModels/PlaylistViewModel.swift`, which contains `ActiveWorkoutSession`; `Services/API/SessionService.swift`; `Services/Networking/APIEndpoint.swift`)

1. **Shared session singleton.** One `ActiveWorkoutSession.shared` holds the timer, logs and per-set state for both Playlist and Interactive, so switching modes keeps state. **MISSING.** Android VMs are separate and nothing is shared.
2. **Start session `POST /sessions`.** Body: id, workoutId, programId, userId, workoutPhaseId, `platform:"ios"`. The client mints a lowercase UUID. It is queued first (`sync_queue`, entityType `sessions`, action `start`) and also tried inline. 4xx/403/404 drops the queue row; transient errors stay queued. It refuses when userId isn't a GUID. **MISSING.** `SessionApi.start` exists but is never called, and Android's `StartSessionRequest` has no id, phase or platform.
3. **Server dedup reconcile (`applyResume`).** If the server returns a different id, iOS renames the local row and queued sync entries. It also adopts the server startDate, programId, workoutPhaseId, currentIndex, nextMovementId and statusId, and mirrors them to the row. **MISSING.**
4. **Resume from the local in-progress row.** The local `workout_sessions` row is written at start (`isCompleted=0`). On relaunch iOS restores id, startDate, wall-clock elapsed, logs, progressJson, breadcrumbs, overrides and lastMode. **MISSING.** Android writes no in-progress row.
5. **24h resume window / stale abandon.** `ActiveWorkoutSession.resumeWindow` mirrors the backend `StartSessionHandler.ResumeWindow`.
   - A stale local row: `commitAbandon` and delete the row, then start fresh.
   - A stale server row: `POST /sessions/{id}/abandon`, then re-issue the start.
   - Home also calls `purgeStaleInProgressSessions`.

   **MISSING.**
6. **Abandon / Discard Session.** `ActiveWorkoutSession.discard` queues `abandon`, deletes the local row and history, resets the singleton, and posts `.workoutSessionDiscarded` (navigation pops to Home). `SessionDiscardRule` holds the copy. Reachable from the Interactive ⋯ menu, the Playlist ⋯ menu, the Workout Detail resume card and the Home resume card. **MISSING.**
7. **Complete `POST /sessions/{id}/complete`.** Body: `{rpeId (explicit null), rpeValue 0–10, completionPercent 0–100, platform}`. Queued (`complete`, `QueuedCompletePayload`) plus inline; a 404 stays queued until the start lands. **PARTIAL in code only.** Android's `saveSession` posts `{rpeId:null}` fire-and-forget with no queue, and it is never invoked.
8. **Per-set history `POST /sessions/{id}/history`.**
   - Fields: workoutTaskId, workoutId, programId, userId, logDate, reps, set, intervalSeconds, weightLifted, status (Completed/Skipped/Incomplete), metricsLogged, distance, distanceUnit.
   - Built by `SessionHistoryRows`. Queued under entityType `session-progress`, action `history`.

   **PARTIAL, dead code.** Android posts planned values for every set, never actuals and never status. It has no queue and is never called.
9. **Progress breadcrumbs `POST /sessions/{id}/progress`.** Body: workoutPhaseId, currentIndex, nextMovementId.
   - Queued only when the phase changes.
   - Same-phase moves are written locally only (`recordPositionLocally`).
   - `currentRound` is a local-only column.

   **MISSING.**
10. **Hydrate state on reinstall.** `GET /sessions/{id}/history` is fed to `SessionProgress.rebuilt` (`Services/Session/SessionHistorySync.swift:227`) when local state is empty. **MISSING.**
11. **SyncManager drains.** `drainSessions` handles start/abandon/delete/complete in createdAt order. `drainSessionProgress` handles progress/history and skips entries whose start hasn't landed. Both run before `/sync/push`. **MISSING.** Android's `sync/SyncManager.kt` only does a generic push with `data=null`.
12. **Calendar side effects.** `ensureCalendarEntryForToday` runs at start (local insert, `CalendarSyncService.mirror`, `CalendarService.create`). `markCalendarCompleted` runs at finish. **MISSING.**
13. **Quick Start id adoption.** When the workout id is renamed via `.workoutServerCreateDidApply`, `adoptWorkoutId` follows it. **MISSING.**
14. **Workout refresh at session start** (`Services/Session/SessionWorkoutRefresh.swift`). Fetch when the cache is shallow, or once per launch while the session is fresh. Re-apply only if the phase/task id structure changed, and never once progress exists. **MISSING** in sessions. Android's `WorkoutDetailViewModel` refreshes, but sessions read the cache only.
15. **Workout-not-found guard.** `workoutNotFound` dismisses the session. **MISSING.** Android falls back to `Workout()`.
16. **Resume surfaces.**
    - `InProgressSessionEntry` (in `SessionProgress.swift`) drives Home "Continue Where You Left Off" and the Workout Detail resume card: "62% complete · 12m elapsed", with discard.
    - `lastMode` reopens the player that was last used.

    **MISSING.** Android has a "Continue" label but the flag is never set.
17. **Mode memory** (`SessionMode` playlist/interactive saved in the row's `lastMode`). The playlist pushes Interactive automatically when the last mode was interactive. **MISSING.**

**B. Per-set progress model** (`Services/Session/SessionProgress.swift`)

18. **SetState** (upcoming, active derived, completed, skipped, incomplete) plus `metricsLogged`, `durationSeconds` and distance per `taskId#iteration`. Persisted on every change to `workout_sessions.progressJson`, `completionPercent` and `completedTaskIds`. **MISSING.** Android only has throwaway `WorkoutProgress` / `ExerciseProgress` bar models with 0/1/2 values.
19. **ExerciseState roll-up** (upcoming, inProgress, completed, skipped, incomplete); `isResolved`; completion % = completed sets ÷ required. **MISSING.**
20. **requiredIterations.** Sets for a loose movement; the lead's rounds for group or interval members. **MISSING.** Android's `totalSets` ignores groups.
21. **Resume cursor.** `firstUpcomingCursor` walks group rounds round-major and resumes interval blocks from the lead. `continueCursor` falls back to an incomplete exercise. **MISSING.**
22. **Finish rule** (`SessionFinishRule`): "Finish Workout? You have N incomplete exercises remaining", with Continue Workout / Finish Anyway. Used in both modes. **MISSING.**
23. **Legacy migration.** Folds pre-V41 `completedTaskIds` into progress. **N/A on Android**, but the schema has nothing to migrate to.

**C. Interactive transition engine** (`Services/Session/SessionTransitionEngine.swift`, and VM `ViewModels/InteractiveSessionViewModel.swift`, 2241 lines)

24. **Flattening.** `Workout.hydrateGroupRounds()` (`Models/Workout.swift:149`), then `allTasks` (lead + advancedTasks per row), then overrides, then `order` re-index. **MISSING.** Android flattens with `taskRows.mapNotNull { it.task }`, which **drops every superset/circuit/interval member** (the bug iOS fixed).
25. **Transitions:**
    - nextSet / step / rest(kind, then) / restBlock / prepare(phaseChange) / workoutComplete.
    - Rest kinds: betweenSets (`restSeconds`), betweenMembers (member's `restSeconds`), betweenRounds (lead `roundRestSeconds`).
    - A Rest task inside a group never stacks a second rest on top of itself.

    **MISSING.** Android's NEXT does `currentSet++`, else `position++`.
26. **Superset/circuit round navigation.** Next member, then back to the lead with round+1. Member labels A1/B2 (`memberLabel`). Group rounds come from `rounds`, else `totalRounds`. **MISSING.** Android shows a static "currentRound/totalRounds ROUNDS" chip, and currentRound never changes.
27. **Phases.**
    - `phaseDisplayName` (Warm-Up / Exercises / Cool Down).
    - `phaseLabel` ("PHASE 2 — STRENGTH").
    - `phaseSummary` ("6 Exercises • 3 Sets Each" / "10-minute HIIT").
    - PhaseChange shown on the prep screen and on the rest-block screen.

    **MISSING.** Android only shows a phase chip for "WARM UP"/"COOL DOWN".
28. **Preparation screen** (10 s, `preWorkoutCountdownSeconds`) before every new exercise, group or block, and on entry. Includes Start Now, structure tag (STANDARD SETS / SUPERSET / HIIT…), member list with the current member highlighted (`PreparationBlockSummary`), coach note card and countdown ring. Programmed rest blocks replace the prep screen. **MISSING.**
29. **Prep cue gate.** The countdown is held until the voice finishes (`prepCuePlayer`, with a 20 s max hold). The first prep after entry announces "You have entered Interactive Mode". **MISSING.**
30. **Previous semantics.** Never marks anything complete. It walks back through sets, members and rounds, and lands at the END of the previous task. During a rest it cancels the deferred advance. Inside a block, `runner.back()` applies. **PARTIAL.** Android decrements set or position and resets set to 1, with no group awareness.
31. **Skip actions** (`SessionCenterControls` menu):
    - Skip Set → incomplete; the next step runs without rest.
    - Skip Exercise / Skip Block → remaining sets skipped; group-aware.

    **MISSING.**
32. **Up Next / skip-forward** (`getNextExercise`, `engine.skipForward`). Walks group members and rounds, recording completion; skips interval blocks, recording the remainder as skipped. **PARTIAL.** Android's "Next Exercise" thumbnail does `position++` and records nothing.
33. **Workout complete.** Stops clocks, snapshots elapsed time, builds achievements, plays a chime. **PARTIAL.** Android sets `isWorkoutComplete` but saves nothing.

**D. Prescriptions** (`Services/Session/SessionPrescription.swift`)

34. **`line`** (prep): `3 SETS × 10 REPS`, `30 SECONDS`, `4 ROUNDS × 12 REPS`, `HIIT · 5:45`; per-set breakdown handled via `displayMetric`. **MISSING.** Android shows the raw `task.metric` string.
35. **`compact`** (Up Next) and **`unit`** (per round). **MISSING.**
36. **`target`** (active set): reps/time per set config, `@ 135 lbs` in the display unit, prescribed distance first. **MISSING.**
37. **`SessionRestHeadline`**: REST vs ACTIVE RECOVERY + movement. **MISSING.** Android shows "REST TIME" only.
38. **Model prerequisites.** Android's `model/WorkoutTask.kt` lacks:
    - `usesDuration`, `durationSeconds`, `setConfigs`, `sameAcrossSets`
    - `intervalMode`, `intervalWorkSeconds`, `intervalRestSeconds`, `intervalWindowSeconds`, `intervalSets`, `intervalSetRestSeconds`, `roundRestSeconds`
    - `restType` / `restMovement*`, `distance` / `distanceUnit`
    - `movementThumbnailId` / `ImageId` / `VideoId`, and the phone video fields

    Android `TaskRow` lacks `isInterval` and `intervalMode`. **Port these first.**

**E. Interval runner** (`ViewModels/IntervalRunner.swift`; `IntervalMath` / `IntervalPlan` / `IntervalPhase` in `Models/WorkoutCreationModels.swift:143–330`)

39. **HIIT/Tabata/EMOM/AMRAP plans.** Multi-set with between-sets rest. EMOM's implicit rest fills the minute. AMRAP counts tapped rounds plus partial reps. **MISSING.** Android has no mode enum and no runner.
40. **Prep countdown.** 3 s in-block (`defaultPrepareSeconds`). It is 0 when the 10 s prep screen already ran. **MISSING.**
41. **Wall-clock anchored phases.** `phaseEndDate`; `reconcile` fast-forwards through missed phases and emits one landing event. **MISSING.**
42. **Controls:** pause/resume (keeps the sub-second remainder), skip, `jumpToNextWork`, `back` (restarts the phase if more than 3 s elapsed), `jumpToLastRound`, `markAmrapRound`, `markEmomRoundDone`, `cancel`. **MISSING.**
43. **Snapshot/restore.** The block survives a Playlist↔Interactive flip and keeps running on the wall clock. **MISSING.**
44. **Block outcome recording.** Rounds played are marked completed and the rest skipped. The AMRAP score is logged as reps=rounds, partial in the weight field. Legacy chained-Tabata recovery rest. **MISSING.**
45. **IntervalOverlayView.** Mode chip, phase badge colors, "HIIT BLOCK · 13:40 remaining" with a progress bar, phase countdown, SET/ROUND counters, AMRAP Round button with a partial-reps stepper, EMOM round-done, Up Next card. **MISSING.**

**F. Timers**

46. **Whole-workout timer.** Wall-clock anchor plus accumulator; survives backgrounding; keeps counting when Interactive is paused. **PARTIAL.** Android's coroutine counter does `+1/s`, so it drifts and stops when backgrounded, and it is lost on exit.
47. **Set timer.** Counts up for rep sets and down for timed sets, auto-advancing at 0. Halfway cue, 3-2-1 beeps, "Done". **MISSING.**
48. **Rest/prep countdown.** Wall-clock anchored, pausable, with a ring (`SessionCountdownRing`). **PARTIAL.** Android's `delay` loop decrements; Skip rest works.
49. **Pause.** Freezes set, rest and runner clocks plus the video; shows a "PAUSED" badge. **PARTIAL.** Android only toggles an `isVideoPaused` flag that nothing consumes.
50. **Foreground resync** (`scenePhase`, `resyncClocks`) and **idle timer disabled** while active. **MISSING** (no `keepScreenOn` either).

**G. Voice / sound / haptic cues** (`Services/Session/SessionCues.swift`, `Services/AudioCueService.swift`)

51. **SessionCueEvent → SessionCuePolicy → SessionCueScheduler.**
    - Events: prepStarted (mode entry → phase change → up next / structure), prepTick, prepEnded "Go", restStarted "Rest, 30 seconds. Up next…", setAdvanced, restTick, timedSetTick/halfway, timedSetEnded, skipped tone, workoutComplete chime.
    - Interval runner events (work/rest/final round/block complete) are routed through `AudioCueService.handle`.
    - Spoken 3-2-1 only into work.
    - The scheduler dedups countdown seconds and rate-limits skip tones.

    **MISSING.** Android has no TTS, no tones and no cue settings; the only cue is one LongPress haptic when rest ends.
52. **Channels.** Master mute (top-bar speaker) plus a long-press per-channel menu: video audio, voice, sounds; haptics separate (SettingsStore flags). **MISSING.** Android's video volume is hard-coded to 0.
53. **Coach voice selection** (`AudioCueService.CoachVoice`). Picker of installed Enhanced/Premium English voices, excluding novelty voices, with a preview. Speech priority: interrupt / queue / dropIfBusy. **MISSING.**
54. **One-time playlist hint** ("interactive mode is here", `interactiveModeHint`). **MISSING.**

**H. Up Next** (`Services/Session/UpNextDescriptor.swift`)

55. **One canonical title for every surface:**
    - "Set 2 — Ab Crunch"
    - "Squat · 3 × 8"
    - "A2 Bench · 10 reps"
    - "Round 2 — A1 Goblet Squat"
    - "Work · Burpees · 20 s"

    Plus detail ("Round 3 of 8") and "Starts in". **PARTIAL.** Android shows a "Next Exercise" thumbnail of `position+1` (which can be a rest), with no title.
56. **Rest-screen Up Next.** Structure label, unit prescription, coach note, phase change; background media is the next movement, the same movement for a set rest, or the recovery movement. **PARTIAL.** Android's rest background is the current task's thumbnail, with an OVERLAY/TEXT/IMAGE tap-cycle.

**I. Active-screen info**

57. **SessionInfoStrip.** "SET 1 OF 3 · 10 REPS · ⏱ set timer", name, "Exercise 4 of 12", previous weight, edit menu, info button. `WorkoutControlTopView` shows group/interval badges and "INTERVAL · TABATA". **PARTIAL.** Android shows name, metric and a set bar; its tags are much simpler.
58. **Estimated workout time remaining** (`workoutRemainingSeconds` via `WorkoutDurationEstimator`). **MISSING.**
59. **Movement info sheet** (structured detail, top set). **MISSING.**

**J. Set logging** (`Views/WorkoutSession/Interactive/SetLogSheet.swift`, `Views/WorkoutSession/PlaylistMovementLogSheet.swift`)

60. **Interactive Log Set / Save Set** (completed + logged). Fields: weight in lbs with display unit, reps, duration picker, distance (km/mi, auto-shown for distance-capable movements, else "Add distance"). Shows Previous, Top set and "This session" sets. Prefill: last set this session, else plan/per-set config, else previous weight. The CTA label shows the logged values. **MISSING.**
61. **Live persistence** of each logged set to `workout_session_history` with deterministic id `sessionId-taskId-setNumber`; `clearLoggedSet` deletes it. **MISSING.**
62. **Playlist log sheet.** Tap a set circle (one set) or a row (all sets). Only edited or previously logged rows are saved. Placeholders, not values, are prefilled. "Mark as completed". **MISSING.**

**K. Movement history** (`Services/Session/MovementHistory.swift`)

63. **MovementHistoryStore.** Previous weight and top set from local history keyed by `movementId`, excluding the current session. Server seed via `GET /sessions/me/movement-history?movementIds=` (max 50), once per launch. **MISSING.** Android history has no `movementId`, distance or status columns.

**L. In-session editing (Do → Build)** (`ViewModels/SessionWorkoutEditor.swift`, `Models/SessionOverrides.swift`, `Views/WorkoutSession/Interactive/SessionEditSheets.swift`)

64. **Edit actions.** Add after current / at end / to a phase; swap movement; edit sets & reps; remove; Create New Exercise from the picker. **MISSING** in sessions. Android has a workout editor but no session hooks.
65. **Scope.** Permanent for the user's own workout (`contributorId` and `sharedByUserId` both nil), else session-only overrides (swaps / removed / setChanges / added). Overrides are persisted in `workout_sessions.sessionOverrides` JSON and applied on every load in both modes. **MISSING.**
66. **Scope prompt and "Save as my workout".** "Just this session" / "This session + save my own version". `saveMyVersion` forks a copy with `preBuiltTemplateId`. **MISSING.**
67. **Quick Start.** Pick one movement, mint a workout, push Interactive. At finish: name it and Keep in My Workouts, or delete. Currently feature-flagged off on iOS. **MISSING.**
68. **Re-anchor after an edit** (`applyTaskList`): clamp the cursor, cancel the runner if the group changed. **MISSING.**

**M. Playlist mode** (`Views/WorkoutSession/PlaylistView.swift`, `PlaylistWorkoutMovementView.swift`, `AdvancedPlaylistMovementsView.swift`, `PlaylistWorkoutTasksView.swift`)

69. **Shared session.** Starts or resumes the session; whole-workout timer; Finish Workout with the finish rule; ⋯ Discard; completion overlay with RPE that saves via `SessionRecorder`. **MISSING.** Android's playlist is a read-only list with no session.
70. **Per-set rings.** Completed check, next set "+" with pulsing halo, Skipped/Incomplete tag, "3 of 8 ✓" collapse. Checkbox toggles a whole exercise. "Tap a set…" hint shown once per session; latest-logged line. **MISSING.**
71. **Group containers.** Superset/circuit badges with icons, rounds, "Rest 0:30" member chips, round-rest chip, interval metrics grid, RECOVERY. **PARTIAL.** Android shows a badge plus a rounds string and the members.
72. **Row menu** (swap / sets / add after / remove), footer "Add to <phase>", "coach's workout → session only" notice. **MISSING.**
73. **Fullscreen movement video and info sheet** per row. **MISSING.** Android rows have a grey box and a menu icon with no action.
74. **Interactive button.** Push into Interactive with state preserved; the top-bar "Playlist" control returns. **MISSING.** Android's icon isn't clickable, and Interactive's back button simply pops.

**N. Completion summary, RPE, share, report** (`Views/WorkoutSession/Interactive/WorkoutCompletedView.swift`, `ShareResultsSheet.swift`, `Services/Session/WorkoutSummary.swift`, `Services/Session/SessionAchievements.swift`, `Services/SessionRecorder.swift`)

75. **`SessionRecorder.save`.**
    - Writes the Completed row with stats, completionPercent and progressJson.
    - Writes history rows for performed sets only.
    - Calls `commitHistory` (all terminal sets) and `commitFinish`.
    - Marks the calendar completed and posts `.workoutSessionCompleted` (pop to Home).

    **MISSING / dead.** Android's `saveSession` writes planned values for every set using random ids, and it is never called.
76. **WorkoutSummaryStats.** Actuals: duration frozen at completion, exercises completed, sets completed, reps, volume (hidden when nil), distance, missed exercises ("Skipped" / "2 of 3 sets"), "Completed · 62%". **PARTIAL.** Android shows planned exercise count, sets and duration.
77. **RPE (`SummaryRPE`).** Optional, 0–10 integers, nil when untouched, labels "Not difficult"…"Maximum difficulty". Editable once within 24 h (`rpeEditedAt`) from `SessionDetailView`, which re-posts complete. **PARTIAL.** Android's slider is 1–10 in 0.5 steps, defaults to 5, never saves, and has no edit.
78. **Achievements.**
    - Kinds: PRs (heaviest weight / most reps at weight / longest duration / longest distance), first time logged, first workout, streak (2 days or more), program week/program complete.
    - Server unlock via `AchievementUnlocker`.

    **MISSING.**
79. **Wearable slot.** Apple Health active energy and max heart rate for the session window. **MISSING.** Health Connect would be the Android equivalent.
80. **Share Results.** Email up to 10 recipients plus a note via `POST /sessions/{id}/share-results`; re-sends completion with RPE and %. "Skip sharing". **MISSING.** Android has a different, OS-level share (see section 2).
81. **Quick Start keep/name** on the summary. **MISSING.**
82. **Reopen the summary from history** (`WorkoutSummaryReopenView`, read-only plus the RPE edit). **MISSING.**
83. **Session detail / report** (`Views/Profile/SessionDetailView.swift`, `ViewModels/WorkoutReportViewModel.swift`):
    - Delete Session: `SessionRecorder.delete`, which reverts the calendar entry, queues `DELETE /sessions/{id}`, and tries inline.
    - Timed-set windows; Health calories.
    - `hydrateFromServer` backfills `/sessions/me` and `/history`, completed sessions only (FTNSSLNK-368).
    - `CompletedWorkoutReportView` opens from a calendar tap.

    **PARTIAL.** Android's `ui/profile/SessionDetailScreen.kt` shows a stats grid and per-set list from local rows only. It has no delete, RPE edit, server backfill or calendar link.
84. **Body data `POST /sessions/{id}/body-data`.** Endpoint defined on iOS. **MISSING** on Android.

**O. Media playback** (`Views/WorkoutSession/Interactive/InteractivePlayerView.swift`, `Services/Media/*`)

85. **Mux HLS (`MuxPlaybackController`).**
    - Signed URL with proactive refresh about 90 s before JWT expiry.
    - Recovery after 403s, with a cap.
    - Optional ABR peak bitrate; kill-switch `MuxPlaybackConfig`.
    - Loop; fill vs fit; phone (portrait) video variants with aspect ratio.

    **MISSING / PARTIAL.** Android's `ui/session/InteractivePlayerView.kt` (ExoPlayer from cache or `MediaURLProvider` URL) is **never used**. There is no `media3-exoplayer-hls` dependency, so HLS can't play, and there's no token refresh.
86. **Offline MP4.** `MediaPrefetcher` downloads the Mux MP4 rendition to `movement-video-mp4/{entityId}.{playbackId}.mp4`; `MediaCacheStore` prefers the local file. **PARTIAL.** Android has `media/MediaPrefetcher.kt` and `MediaCacheStore` with the older single-file `mp4` contract, not playbackId-versioned.
87. **Video behavior during the session.** Held during prep, between-sets rest and passive interval rest (`shouldHoldVideo`). Active recovery swaps in the recovery movement's video. Video audio is a user toggle. **MISSING.**

**P. Persistence schema**

88. **iOS `workout_sessions`** adds: statusId, workoutPhaseId, currentIndex, nextMovementId, currentRound, completedTaskIds, progressJson, completionPercent, lastMode, sessionOverrides, rpeEditedAt. It also lowercases ids. **iOS `workout_session_history`** adds movementId, distance and distanceUnit. **MISSING.** Android has only the V3 base columns plus taskName.

---

### 2. What Android has that iOS lacks

- **Rest display modes.** `RestDisplayMode` OVERLAY / TEXT / IMAGE, cycled by tapping the rest screen and remembered with `rememberSaveable`. iOS has one rest design; its `RestOverlayView` and `RestImageView` are vestigial stubs.
- **OS share sheet.** The completion screen offers `Intent.ACTION_SEND` plain text: "Just crushed X on FitnessLink 💪 • duration • N exercises • N sets". iOS only has server email share.
- **Unmuted music.** ExoPlayer is set not to take audio focus, so the user's music keeps playing. iOS uses a user toggle instead.
- **Per-phase/task progress bar.** A segmented bar per task (`WorkoutProgressBarView`). iOS uses the "Exercise 4 of 12" text instead.

Nothing else. Android has no session behavior that iOS lacks.

---

### 3. iOS session engine architecture (port guide)

**Layering.** Every rule lives in pure value types (unit-tested). The view model only applies decisions and owns timers. One singleton owns persistence.

1. **Flattening.**
   - `Workout.hydrateGroupRounds()`:
     - An interval row gets `isInterval` stamped on the lead and every member.
     - Otherwise grouping is inferred: the row flags win; 2 movements make a superset and 3 or more a circuit.
     - Canonical rounds = lead `rounds`, else `row.totalRounds`, else 3.
     - A missing `groupId` gets a fresh UUID.
     - groupId, flags and rounds/totalRounds are copied onto every member.
   - `WorkoutPhase.allTasks` = per row `[task] + advancedTasks`, stamping `phaseName` from the phase when blank.
   - `Workout.allTasks` = phases flattened in order.
   - Then `SessionOverrides.apply` (swaps / removed / setChanges / added with `afterTaskId` or `phaseId` anchors), then `order = index`.
   - **A group is a run of consecutive tasks sharing a non-empty `groupId`.** The lead (index `lowerBound`) carries `rounds`, `roundRestSeconds`, interval config and `advancedMovement` (the group name).
   - A Rest task outside a group is a "rest block".
   - Phase identity = `workoutPhaseId` lowercased, else `phaseName`.
2. **`SessionTransitionEngine(tasks)`** (pure struct).
   - **Cursor** `{position, set (loose, 1-based), round (group, 1-based)}`.
   - **`next(from:)`:**
     - Group member, not last → `.step(position+1, same round)`, wrapped in `.rest(member.restSeconds, .betweenMembers, then:)` if more than 0 and the member isn't a Rest task.
     - Last member with `round < rounds` → `.step(lowerBound, round+1)`, wrapped in `.rest(lead.roundRestSeconds, .betweenRounds)`.
     - Final round → `afterItem`.
     - An interval task means the block is finished → `afterItem(upperBound-1)`.
     - Loose task with `set < sets` → `.nextSet`, wrapped in `.rest(restSeconds, .betweenSets)`.
   - **`afterItem(endingAt:)`:**
     - End of list → `.workoutComplete`.
     - Next is a standalone Rest → `.restBlock`.
     - Next is a movement or group → `.prepare(cursor, phaseChange)`, but `.step` if the item just finished was a rest block (programmed rest replaces prep).
     - Filler → `.step`.
   - **`skipForward`** is `next` without rests.
   - Helpers: `groupRange`, `groupRounds` (`max(rounds ?? totalRounds, 1)`), `memberLabel` (letter = ordinal of non-interval groups), `phaseKey`, `phaseChange`, `phaseDisplayName`, `phaseSummary`, `phaseOrdinal`, `phaseLabel`.
3. **Interactive VM state machine.** Exactly one of these is live at a time:
   - a transient rest (`activeRestKind` + `afterRest: Transition`),
   - prep (`isPreparing`, `pendingPhaseChange`),
   - an interval runner, or
   - work (a movement plus the set timer, or a Rest task counting itself down).

   **Key operations:**
   - **`getNext()`:**
     - If preparing → `startWork`.
     - Else if `afterRest` is set → apply it.
     - Else if a runner is live → `runner.skip`.
     - Else `apply(engine.next(cursor), completing: true)`. "Completing" marks the current set completed with the set-timer duration; NEXT means "I did it".
   - **`apply`** maps each transition onto state:
     - `nextSet`: set++ and a cue.
     - `step`: move the cursor.
     - `rest`: start the countdown and store `then`.
     - `restBlock` / `prepare`: `land()`.
     - `complete`: run the finish rule.
   - **`land(preparing)`** shows prep with the full countdown, plays the `prepStarted` cue, and starts the countdown when the voice finishes (token-guarded, 20 s cap).
   - **`startWork`** spins up the runner with in-block prep = 0 for interval leads; otherwise plays the "Go" cue and starts the set timer.
   - **Rest expiry** re-enters `getNext()`. A timed set reaching zero calls `getNext()`.
   - **`updateUI`** (on every cursor move): set `workoutTask`; create, reuse or cancel the runner; start a rest-block countdown; write breadcrumbs (enqueue progress if the phase changed, else local only; always write `currentRound` locally); then `syncSetTimer` (sets `session.activeSet`).
   - **Opening position** (`applyDetail`): resume from `SessionProgress.firstUpcomingCursor`; if nothing is upcoming, use `continueCursor` or show completion; if the session is fresh, use the saved index. Always open on prep.
4. **`IntervalRunner(group: TaskRow)`.**
   - The plan comes from `IntervalMath.plan(lead, rowTotalRounds, mode)`:
     - Tabata/HIIT: work, rest, rounds (lead rounds → totalRounds → row → preset).
     - EMOM: window ≥ 60, work ≤ 60, rest = 60 − work, rounds = window/60.
     - AMRAP: work = window, rounds 0.
     - `sets = intervalSets ?? 1`; set rest = `intervalSetRestSeconds`, falling back to legacy `roundRestSeconds`, then 60.
   - `blockSeconds` = sets × setSeconds + (sets − 1) × setRest. HIIT has no rest after the last round.
   - **Phases:** prepare → work → (rest | next round work | betweenSetsRest | done). The member shown is `(round-1) % n`; AMRAP rotates on each tap.
   - **Timing:** each phase stores `phaseEndDate`. A 0.25 s timer calls `reconcile(now)`, which fast-forwards through expired phases with events suppressed (chaining anchors), then emits one landing event plus a replayed `setComplete` / `finalRound`.
   - **Events:** tick, prepareStarted, transitionToWork/Rest/BetweenSetsRest, roundComplete, setComplete, finalRound, blockComplete. They are delivered via `onEvent` to `AudioCueService.handle` (spoken 3-2-1 only when `leadsIntoWork`) and refresh the in-memory `session.intervalSnapshot`.
   - On `blockComplete`, the VM's `exitIntervalGroup` records the outcome and applies `engine.afterItem`.
   - **Snapshot** (Codable, in memory only): groupId, phase, set, round, exerciseIndex, `phaseEndDate` or `pausedRemaining`, phaseDuration, roundsCompleted, amrapPartialReps, prepareSeconds.
5. **`SessionProgress`** (Codable value).
   - A map `"taskId#iteration" → Entry{state, metricsLogged, durationSeconds, distance, distanceUnit}`.
   - Mutations: complete, log, clearLog, skipSet, skipRemaining, completeExercise, revertExercise.
   - Queries take `requiredIterations` from the caller. JSON goes in the `progressJson` column.
6. **`ActiveWorkoutSession`** (singleton).
   - Holds: sessionId, startDate, timer (anchor + accumulated), `actualSetLogs [taskId: [set: TaskSetConfig]]`, progress, requiredIterations, activeSet, breadcrumbs, overrides, lastMode, intervalSnapshot.
   - Every mutation persists immediately to SQLite (progressJson, completionPercent, completedTaskIds, history upserts, breadcrumbs, overrides, lastMode).
   - Server writes are queue-first (`sync_queue`, entity types `sessions` and `session-progress`) with an inline attempt.
   - `SessionRecorder.save` finalizes; `reset()` clears.
7. **Supporting pure types:** `SessionPrescription`, `PreparationBlockSummary`, `UpNextDescriptor`, `SessionCuePolicy` / `SessionCueScheduler` / `SessionCuePhrasing`, `SessionHistoryRows`, `WorkoutSummaryStats`, `SummaryRPE`, `PersonalRecordDetector` / `TrainingStreak` / `ProgramMilestone`, `SessionFinishRule`, `SessionDiscardRule`, `InProgressSessionEntry`. All are portable as Kotlin objects or data classes.
8. **Suggested Kotlin mapping:**
   - Engine, progress, prescription, plan/math and cue policy become plain classes with JUnit tests (port the iOS test names).
   - `ActiveWorkoutSession` becomes a Hilt/app-scoped singleton exposing StateFlow.
   - Timers become one coroutine ticker at 250 ms that recomputes from `SystemClock.elapsedRealtime` or wall-clock anchors (never `+1`).
   - Cues use Android `TextToSpeech` with `UtteranceProgressListener` for the prep gate, `ToneGenerator` / SoundPool, and `VibrationEffect`.
   - The video player is ExoPlayer with `media3-exoplayer-hls` and signed-URL refresh.
   - `WorkManager` drains the session queues.

---

### 4. Android today, and how far it is from iOS

- **Navigation.**
  - Workout Detail's "Start Now" opens `playlistSession/{id}`. `onStartInteractive` is wired in `AppNavigation.kt:268`, but WorkoutDetailScreen never calls it.
  - The playlist screen's interactive icon has no click handler, so Interactive is effectively unreachable unless another route calls it.
- **`PlaylistViewModel.kt`** (25 lines) only loads the cached workout; the screen renders phases and rows statically.
- **`InteractiveSessionViewModel.kt`:**
  - Flattens only row leads (members are lost) and counts the timer with `delay(1000)`.
  - NEXT increments set, then position; rest tasks count down via `delay` and auto-advance.
  - PREV and the "Next Exercise" tap are naive; `currentRound` is never changed.
  - Groups, intervals, phases, transient rests and prep don't exist.
  - On completion it shows `WorkoutCompletedScreen` with planned counts. The RPE slider (local state only) is never saved, and `saveSession` is never invoked.
- **Nothing is persisted mid-session.** No session row, no queue, no server start, no resume, no dedup, no abandon.
- **Report and detail screens** read `workout_sessions` / `workout_session_history`, which Android never writes. They would only show rows from the generic sync pull, if any.
- **Gap estimate.** Effectively a rewrite. Android needs, in order:
  1. Model parity: WorkoutTask/TaskRow interval, duration, setConfigs, restType, media and distance fields, plus `hydrateGroupRounds` / `allTasks`.
  2. DB migration for the iOS session columns and history columns.
  3. `SessionProgress` + `SessionTransitionEngine` + `IntervalMath` / `IntervalRunner` + `SessionPrescription` / `UpNext` as pure Kotlin with tests.
  4. An `ActiveWorkoutSession` equivalent with the queue-first `SessionApi`. Add the abandon, progress, history GET, movement-history, share-results, body-data and DELETE endpoints, and fix the DTOs: start needs id, workoutPhaseId and platform; complete needs rpeValue, completionPercent and platform; history needs status, metricsLogged and distance.
  5. A SyncManager drain for sessions.
  6. The Interactive UI: prep, rest, info strip, transport bar with the skip menu, dock, log sheet, interval overlay, edit menu.
  7. Playlist set rings and log sheet.
  8. Cues and voice settings.
  9. HLS player wiring.
  10. Summary: stats, achievements, RPE persist/edit, share.
  11. Resume surfaces on Home and Workout Detail.

  Today almost none of the roughly 89 iOS behaviors above are present on Android: a few are PARTIAL and the rest MISSING.
