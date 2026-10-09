# iOS → Android feature-parity plan

Generated 2026-10-06 from a file-by-file comparison of `iOS-fitnesslink-fit` (SwiftUI) and this repo.
This document is the working plan; tick items off as they land. The iOS source is the spec.

## Executive summary

The Android app has the right screen skeletons but almost none of the product behaviour behind them.
Across every domain the same three problems repeat:

1. **Mock data instead of the server.** Home strip, dashboard tiles, My Content rail, recent foods,
   weekly progress, food-entry detail, photo detail and profile menu read `MockDataProvider`.
2. **Local-only writes.** The workout editor, Create Exercise, meal plan, grocery list, measurements
   and nutrition goals save to SQLite only (or do nothing on Save). Nothing is queued for the server.
3. **Wrong wire contracts.** Production base URLs point at a host that does not exist
   (`core.fitnesslink.app`); the sync push drops its payload; `POST /users` and `PUT /users/{id}`
   decode the wrong response type; meal slots are weekday-keyed where the server is date-keyed;
   phase ids are random client UUIDs where the server expects catalog GUIDs.

**Workout execution is the biggest single gap.** Finishing a workout on Android records nothing,
Interactive mode is unreachable from navigation, and supersets/circuits/intervals lose their member
exercises. The iOS session engine (≈2.2k-line view model + 11 pure service types) has to be ported.

Rough size of the iOS surface: 39 nutrition views, 28 workout views, 24 session views, 24 profile
views, 23 API services, 46 view models. Android has 16 nutrition screens, 5 workout screens,
4 session screens, 13 profile screens, 16 API interfaces, 34 view models, most of them thin.

## Delivery waves

Waves are ordered so each one is usable on its own and unblocks the next. Within a wave, items
are grouped by the files they touch so they can be built in parallel.

### Wave 0 — Foundation (prerequisite for everything)

| # | Item | Status |
|---|------|--------|
| 0.1 | Prod base URLs → `https://api.fitnesslink.fit/{core,nutrition,notifications,billing}/api/v1`; add `ApiService.BILLING`; default env PRODUCTION; keep LOCAL/NGROK for dev | ✅ 2026-10-06 |
| 0.2 | API client: force-refresh token + one retry on 401; 402 → payment-required hook; drop `runBlocking` token fetch for an `Authenticator` | ✅ 2026-10-06 |
| 0.3 | API surface: add every endpoint iOS calls (see Appendix A) with request/response DTOs matching iOS wire shapes | ✅ 2026-10-06 |
| 0.4 | Auth: session restore on launch (splash), real logout (token unregister → Firebase signOut → clear DB → sync reset), password reset (`POST /auth/password-reset` + Firebase fallback), real sign-up (username availability, `POST /users` → GUID), social sign-in policy (no account creation; CompleteProfileSheet for shell users), Google via Credential Manager, Facebook hidden behind a flag | ✅ 2026-10-06 |
| 0.5 | Push: `FirebaseMessagingService`, `POST_NOTIFICATIONS` permission, token register/refresh/unregister, tap → deep link, badge sync | ✅ 2026-10-06 |
| 0.6 | Sync: match iOS `/sync/pull` + `/sync/push` contract, allow-listed `applySyncPayload`, per-domain queue drains framework (`SyncDomain` enum, drain order), `performInitialSync` re-saves the user, `reset()` on logout | ✅ 2026-10-06 |
| 0.7 | Models: port full `Workout`/`WorkoutPhase`/`TaskRow`/`WorkoutTask` field set (≈45 missing fields), `hydrateGroupRounds`, `allTasks`, `IntervalMode`/`IntervalMath`/`IntervalPlan`, `WorkoutDurationEstimator`, `TaskSetConfig`; `Movement` with `anatomyIds`/`equipmentIds`/media/sharing; `FoodEntry` + date-anchored `MealSlot`; `Program` fields; `FitnessContent` calendar fields | ✅ 2026-10-06 |
| 0.8 | DB migration v11: full task JSON persistence, session/history columns, programs + calendar columns, `user_programs`, movements tag arrays, favorites columns, notification prefs fields | ✅ 2026-10-06 |
| 0.9 | Media: `media3-exoplayer-hls`; MP4 rendition rewrite (`/{playbackId}/highest.mp4`) with playback-id-stamped files; 2-wide download queue; persisted URL cache; `primeCatalog`; per-content prefetch | ✅ 2026-10-06 |
| 0.10 | Shared components: bindable `SearchView`, header unread badge, `ContentFilterComponents` (favorites toggle, equipment/anatomy chip strips, contributor menu, clear), `ShareSheet` (user search + `POST /content/share`), `PendingShareBanner`, `SharedBadge`, `LocationInfoView`, `ClassificationStore` | ✅ 2026-10-06 |

### Wave 1 — Core loop: workouts and sessions

| # | Item | Status |
|---|------|--------|
| 1.1 | Session engine port: `SessionTransitionEngine`, `SessionProgress`, `IntervalRunner`, `SessionPrescription`, `UpNextDescriptor`, `SessionCues`, `ActiveWorkoutSession` singleton (queue-first `/sessions` start/complete/abandon/progress/history), `SessionRecorder`, `MovementHistoryStore`, 24 h resume window, SyncManager session drains | ✅ 2026-10-06 |
| 1.2 | Interactive UI: prep screen + cue gate, rest screens, info strip, transport bar + skip menu, set-log sheet, interval overlay, edit menu, HLS player with video hold rules, keep-screen-on, wall-clock timers, TTS/tones/haptics + coach voice picker | ✅ 2026-10-06 |
| 1.3 | Playlist mode: shared session, set rings, log sheet, group containers, row menu, fullscreen video, Interactive hand-off | ✅ 2026-10-06 |
| 1.4 | Completion: actual stats, RPE (0–10, 24 h edit), achievements (PRs/first/streak/program), share results, calendar completion, resume surfaces on Home + Workout Detail, Discard Session | ✅ 2026-10-06 |
| 1.5 | Workouts list: server catalog refresh (`/workouts/list-view` with filter params), search, favorites, contributor/equipment/anatomy filters, card/list toggle, `displayTime` | ✅ 2026-10-06 |
| 1.6 | Workout detail: owner actions (edit/delete/share), fork-to-edit (`/workouts/{id}/fork`), shared-in banner + remove from library, tags, structure view, movement info sheet, interval metrics grid, coach card | ✅ 2026-10-06 |
| 1.7 | Workout editor rewrite: server save (`POST /workouts`, `PUT` + `tasks/replace`), offline queue, phase catalog (`/workout-phases`, templates), set editor (reps/time, per-set configs, rest picker, cue), rest type + recovery movement, group editor (rounds, round rest, member rest, member edit), interval edit sheet + timeline, tags editor, image upload, move-to-phase, drag reorder | ✅ 2026-10-06 |
| 1.8 | AI generation: prompt sheet, `POST /workouts/generate-from-prompt` (180 s), review screen, movement resolution + auto-create, apply to editor | ✅ 2026-10-06 |
| 1.9 | Movements: catalog screen, detail (video hero, sections, share, recents), server-driven exercise browser (list-view params, taxonomy chips, Mine tab, favorites/recents endpoints), preview sheet hydrate + video | ✅ 2026-10-06 |
| 1.10 | Create Exercise: image + form-video upload (`POST /media` multipart, background), Mux status polling, `POST /movements` → GUID, deferred video attach | ✅ 2026-10-06 |
| 1.11 | Reports: server hydration (`/sessions/me` + history), report detail per metric, workout aggregate detail, session detail delete + RPE edit, charts, unit conversion | ✅ 2026-10-06 |

### Wave 2 — Programs, calendar, home

| # | Item | Status |
|---|------|--------|
| 2.1 | Program enrollment model + `user_programs`, `/program-enrollments` queue + `/me` pull | ✅ 2026-10-06 |
| 2.2 | Start Program flow (date picker, timeline, one-active rule, replace), "You're all set" | ✅ 2026-10-06 |
| 2.3 | My Program (Week Plan / Overview / Coach, move workout, restart/remove), Program Progress | ✅ 2026-10-06 |
| 2.4 | Program detail: state-aware CTA, week chips + phase rail, `/weekly-workouts` hydrate, audience + equipment, sneak-peek reel, coach card, ownership gating, share banner | ✅ 2026-10-06 |
| 2.5 | Programs list: template-only, server order, favorites, contributor/equipment/anatomy filters, layout toggle | ✅ 2026-10-06 |
| 2.6 | Program editor: image picker, multi-level, location, tags, drag/drop, create-workout-in-picker, template guard, `onSaved` | ✅ 2026-10-06 |
| 2.7 | Calendar: month nav + picker, add menu (4 triggers), workout/program pickers with filters, schedule date+time+duration sheet, move to another day, delete with scope + queue, bulk delete, program scheduling, wire format, inline device-calendar mirror | ✅ 2026-10-06 |
| 2.8 | Home: real calendar data, quick actions, My Program card, resume banner, computed dashboard tiles + weekly goal, habits checklist + daily footer, goal cards, rest vs nothing-planned, tap-to-report | ✅ 2026-10-06 |
| 2.9 | Catalog hub: server refresh, template rails, movements rail, My Content rail + See All screen, loading state | ✅ 2026-10-06 |

### Wave 3 — Nutrition

| # | Item | Status |
|---|------|--------|
| 3.1 | Fix broken basics: Save on goals/custom food/quick add/entry detail; today-filter bug; recent foods + weekly progress from server | ✅ 2026-10-06 |
| 3.2 | Profile + onboarding wizard (`/profile/calculate`, `/profile`), goals hub with provenance + override, profile edit | ✅ 2026-10-06 |
| 3.3 | Dashboard: date nav, week strip, calendar sheet, over-target states, micros card, planned meals, goals tile, shortcuts | ✅ 2026-10-06 |
| 3.4 | Food search + food detail (portion math, `/foods/{id}/convert`), favorites, recents, recent meals re-log, previous meals picker, quick-log copy, meal templates, batch log | ✅ 2026-10-06 |
| 3.5 | AI: Describe a Meal, estimate-macros prefill, Snap a Meal (flagged) | ✅ 2026-10-06 |
| 3.6 | Barcode: OpenFoodFacts fallback, catalog portions, barcode carried into manual entry | ✅ 2026-10-06 |
| 3.7 | Meal plan: date-anchored multi-week, copy week/day, meal builder (`/meal-slots/{id}/foods`), AI suggestion, AI week ideas + review/apply, server persistence | ✅ 2026-10-06 |
| 3.8 | Grocery: multiple lists, detail with add/edit/delete/reorder, analyze + merge, staples, build meal from grocery | ✅ 2026-10-06 |
| 3.9 | Hydration: recent amounts, backfill, preferred unit, undo; display settings | ✅ 2026-10-06 |
| 3.10 | Analytics (trends/consistency/weight progress), metric detail charts, meal-type + food-item detail | ✅ 2026-10-06 |

### Wave 4 — Goals, progress, profile, billing

| # | Item | Status |
|---|------|--------|
| 4.1 | Goals offline cache + drains; list cards with progress; detail header/trajectory/current-target/delete; habit detail stats + activity; achievement types; `GET /achievements` | ✅ 2026-10-06 |
| 4.2 | Weight: date picker, cache + queue. Measurements: server shape (per-part rows ↔ anatomy ids), sync, entry detail, icons | ✅ 2026-10-06 |
| 4.3 | Progress photos: capture flow (camera + picker, 3 angles, notes), two-step upload, real thumbnails everywhere, date search | ✅ 2026-10-06 |
| 4.4 | Preferences screen (appearance + dark theme, units, session audio/voice, calendar, Health Connect placeholder), profile photo via `POST /media` + `PUT /users/{id}` | ✅ 2026-10-06 |
| 4.5 | Billing: Play Billing, entitlement store + 402 gate, paywall, subscription screen from `/subscriptions/me`, restore | ✅ 2026-10-06 |
| 4.6 | Notifications: inbox search, server unread count, category prefs with full model, pre-prompt sheet, deep-link parity | ✅ 2026-10-06 |
| 4.7 | My Videos screen | ✅ 2026-10-06 |

## Status log

- 2026-10-06 — **All waves (0–4) built and committed on local main.** Module compiles; 423 JVM unit tests green; debug APK assembles. Not yet verified on a device/emulator (none on this machine).

### Open follow-ups after the port
- **Backend (launch-blocking for Android billing):** no `POST billing/subscriptions/google/verify`, and `restore` rejects `source: "Google"` (400). Play purchases cannot be verified, so the client does not acknowledge them and Google auto-refunds after 3 days. Needs a Google verify command + Play RTDN webhook mapper; `obfuscatedAccountId` carries the FitnessLink user id.
- **Backend:** `CreateGoalRequest` lacks `CurrentValue` (client works around with a follow-up progress PUT); `GET /progress-photos/me` omits per-row `notes`; no habit-log delete endpoint; rowVersion required on first delete of offline-created meal slots / grocery items.
- **Schema:** fold the guarded side tables/columns created lazily by the Dao files into the versioned migration (`nutrition_cache`, `meal_slot_foods`, `movement_detail_cache`, `movement_pending_creates`, `habits.autoCompleteSource`, `streaks.freezes*`).
- **Cleanup:** two parallel `ShareableContentKind` enums (`model.api` vs `network.dto`); duplicate `NutritionTargets`/`NutritionProfile` data classes in `model/api` vs `network/dto`; `MealMacroSummary` defined twice in `ui/nutrition`; `Plan*` vs `AI*` chip duplicates; dead `DatabaseManager.dashboards()/catalog*()`, `MockDataProvider` leftovers, `DatabaseSeeder` profile_menus seed, unused `HorizontalCalendar`.
- **UI parity gaps left on purpose:** most screens hardcode light colours (dark theme only affects Material components until a colour-token sweep); in-app CameraX form-video capture (dep added, system intent used); drag-and-drop reorder uses move menus; `MovementInfoSheet` lacks "Your Performance"; Home re-centres on today after a pop-back; Apple-Health wearable slot not ported (Quick Start ported 2026-10-09 per the Quick Start PRD).
- **Verification:** create an AVD in Android Studio and exercise sign-in → catalog → start a workout (Interactive + Playlist) → complete → report; nutrition logging; calendar add/move; Start Program. Then file FA tickets for anything found.

- 2026-10-06 — Wave 0 (0.1–0.10), Wave 1 (1.1–1.11) and Wave 2 (2.1–2.9) built and committed; module compiles, 267 JVM unit tests green. Known follow-ups: in-app CameraX form-video capture (camera-video dep added, UI uses system intent), two parallel `ShareableContentKind` enums to collapse, `MovementInfoSheet` lacks "Your Performance", pop-back re-centres Home on today, Google Play verify endpoint missing on backend. Cross-agent signatures live in `docs/parity/CONTRACTS.md`.

## Appendix A — endpoints iOS calls that Android lacks

Core: `auth/password-reset`, `users/username-available`, `users/search`, `users/me/profile-photo` (unused on iOS),
`workouts` POST/PUT shapes, `workouts/{id}/tasks(/replace)`, `workouts/{id}/fork`, `workouts/generate-from-prompt`,
`workout-phases*`, `workout-phase-templates*`, `programs/list-view` params, `program-enrollments*`,
`sessions/{id}/abandon|progress|history|body-data|share-results`, `sessions/me/movement-history`,
`movements/favorites`, `movements/{id}/favorite|recent`, `movements/recents`, `movements/list-view` params,
`achievements` (definitions), `content/shared/{id}/accept|decline`, `DELETE content/shared/{id}`, `DELETE content/share/{id}`,
`content/favorites/{id}?kind=`, `contributors`, `classification/workout-task-types|rest-types`,
`media` POST multipart, `media/{id}`, `calendar` PUT, `notifications/me/unread-count`, `device-tokens/{id}/refresh`.

Nutrition: see `docs/IOS_PARITY_PLAN.md` Wave 3; 40+ routes (`/profile/*`, `/summary`, `/analytics/*`,
`/foods/*`, `/favorites/*`, `/quick-log/*`, `/meals/recent`, `/meal-templates*`, `/meal-slots/*` extras,
`/grocery/lists*`, `/grocery/analyze|merge|staples`, `/water-entries/recent-amounts`, `/hydration-goals/me/preferred-unit`,
`/preferences/displayed-nutrients`, `/food-entries/me/foods`, `/food-entries/batch`).

Billing (new service): `plans`, `entitlement/me`, `customers/me/checkout-context`, `subscriptions/me`,
`subscriptions/google/verify` (**does not exist on the backend yet** — `RestorePurchaseRequest` already accepts a Google `purchaseToken`; a Google verify command + Play webhook mapper is a backend task for Wave 4.5), `subscriptions/restore`.

## Appendix B — Android-only behaviour worth keeping

Level/length filters on lists, favorite toggle on detail headers, OS share chooser, milestone add/achieve,
custom habits in goal creation, achievement detail dialog, hydration goal editor, grocery list text share,
manual barcode entry fallback, metered-network prefetch deferral, Calendar Sync settings screen.

## Appendix C — iOS bugs found while comparing (fix on iOS, do not port)

Photo-capture Camera button does nothing; habit-detail heatmap/stats are mock-only; goal/achievement VMs
show mock data until the server responds; no delete UI for weight/measurements/photos; milestones read-only;
`ProgramDetailViewModel.addProgramToCalendar` and friends are dead code.
