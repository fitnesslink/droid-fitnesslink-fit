# Parity gap report: auth-profile-notifications-media-sync

_Generated 2026-10-06 from a file-by-file iOS vs Android comparison. iOS is the spec._

## Feature-parity gap analysis: Auth, Onboarding, Personalization, Billing, Profile and Settings, Notifications, My Videos, Sync, Media

Paths below are relative to these roots:
- iOS: `/Users/quincywilliams/Repos/FitnessLink/iOS-fitnesslink-fit/FitnessLink/`
- Android: `/Users/quincywilliams/Repos/FitnessLink/droid-fitnesslink-fit/app/src/main/java/com/fitnesslink/fit/`

**Five problems to fix before any feature work:**
1. **Sync contract mismatch.** Android's sync DTOs and query parameters don't match iOS's. Push sends `data=null`, and pulled data is never written locally.
2. **No session handling.** There is no session restore on launch, and logout does not sign out of Firebase.
3. **Sign-up is a dead form.**
4. **No billing or paywall at all.** Android also has no Billing service base URL.
5. **Offline video prefetch downloads the HLS playlist (.m3u8), not an MP4**, and Android has no HLS player module.

---

### 1. iOS features and their Android status

#### A. Auth and app entry

1. **Launch splash and session restore.** iOS shows a splash while Firebase restores the session (`isRestoringSession`), fills the user in from the local DB, then refreshes `GET users/me` in the background. Files: `FitnessLinkApp.swift`, `Services/Auth/AuthManager.swift` (`listenToAuthState`).
   - **Android: MISSING.** `AuthManager.restoreSession()` exists but is never called.
   - `AppNavigation()` keeps `isAuthenticated` in `rememberSaveable(false)`, so every cold start lands on Welcome even though Firebase still has the session.
   - The auth-state listener in `auth/AuthManager.kt` updates a flag that nothing reads.
2. **Welcome screen** (`Views/WelcomeView.swift`): Sign Up / Login. **Android: PRESENT** (`ui/auth/WelcomeScreen.kt`).
3. **Email/password login.** Firebase `signIn`, then `GET users/me`, with Firebase errors mapped to messages (`LoginFormView.swift`, `LoginViewModel.swift`). **Android: PRESENT** (`LoginScreen.kt`, `LoginViewModel.kt`, `AuthManager.login`).
   - Gap: no password reveal (eye) button. iOS has `RevealablePasswordField`.
4. **Bearer token with forced refresh and one retry on 401** (`Services/Networking/APIClient.swift`).
   - **Android: PARTIAL.** `ApiClient` uses an OkHttp interceptor with `getIdToken(false)` called through `runBlocking`. There is no force-refresh or 401 retry.
5. **Password reset.** iOS calls `POST core/auth/password-reset` and falls back to Firebase `sendPasswordReset` on failure, with special messages for 429 and 400. UI is `ForgotPasswordSheet` (enter email, then a "Check Your Email" state). Files: `AuthManager.sendPasswordReset`, `LoginViewModel` (`beginForgotPassword` / `sendPasswordReset`).
   - **Android: MISSING.** The "Forgot Password?" text has an empty `clickable {}`, and there is no endpoint in `UserApi`.
6. **Sign-up form** (FTNSSLNK-285). Fields: first name, last name, email, optional phone, `@username`, password (minimum 8). Files: `SignUpViewModel.swift`, `LoginFormView.swift` sign-up tab.
   - Client checks: email format, username format (3–30 characters; lowercase letters, digits, `_` and `.`; must start with a letter or digit).
   - Live username check: `GET core/users/username-available?username=` with a 400 ms debounce. It returns `available`, `reason` and `suggestions`; suggestions are tappable chips.
   - Submit flow: Firebase `createUser`, then `POST core/users` (`{firebaseId,email,firstName,lastName,phone,username}`, which returns a bare GUID), then `GET users/me`.
   - Error handling: if Firebase is already signed in with the same email, that account is reused. A 409 resets the username state ("That username was just taken").
   - **Android: MISSING.** `SignUpContent` text fields are bound to `""` with `onValueChange = {}`. The button is permanently disabled, and its `onClick` would call `onLogin(true)` without creating anything.
   - `AuthManager.signUp()` exists but takes no phone or username (username is derived from the email), and expects an `FLUser` back from `POST users`. iOS says that endpoint returns a bare GUID.
   - `UserApi` has no username-availability endpoint.
7. **Google sign-in.** Native GIDSignIn, then a Firebase credential (`AuthManager.signInWithGoogle`), with the custom URL scheme handled by `handleOpenURL`.
   - **Android: PARTIAL / stub.** `AuthManager.loginWithGoogle(idToken)` exists, but `LoginViewModel.acquireGoogleIdToken()` returns null. The UI shows "Google sign-in is not yet configured". There is no Credential Manager or googleid dependency.
8. **Facebook sign-in.** Uses Firebase `OAuthProvider("facebook.com")` web flow, with no Facebook SDK.
   - iOS hides the button: `LoginFormView.isFacebookSignInEnabled = false`.
   - **Android: PARTIAL / stub**, and the button is visible. `acquireFacebookAccessToken()` returns null. To match iOS, hide the button behind a flag.
9. **Social sign-in policy: no account creation** (`AuthManager.allowsSocialAccountCreation = false`). If Firebase reports `isNewUser`, iOS deletes the Firebase user and shows "No FitnessLink account is linked… sign up with your email first".
   - Sign-in cancellation (`SignInCancelled`) is silent.
   - **Android: MISSING.** Android does the opposite: when `users/me` fails it auto-provisions the user via `POST users`, and it has no cancel handling.
10. **CompleteProfileSheet for first-time social users** (`Views/Auth/CompleteProfileSheet.swift`).
    - Trigger: `users/me` returns a "shell" user with empty names.
    - The sheet is pre-filled with name and email from the provider and the server's placeholder username. It uses the same username rules and debounced availability check as sign-up.
    - Submit calls `AuthManager.completeSocialProfile` (`POST users` adopts the shell row). Dismissing calls `abandonProfileCompletion`, which signs out.
    - **Android: MISSING.**
11. **Logout.** Runs in this order:
    - push unregister (`DELETE notifications/device-tokens/{id}`, capped at about 3 s)
    - Firebase `signOut`
    - clear the DB
    - `KeychainHelper.deleteAll`
    - `EntitlementStore.reset`, `StoreKitManager.reset`, `SyncManager.reset`
    
    Files: `AuthManager.logout`, `FitnessLinkApp` MainTabView binding.
    - **Android: PARTIAL / broken.** `ProfileScreen` Logout only flips nav state in `AppNavigation`. `AuthManager.logout()` (Firebase sign-out plus `clearUserData`) is never called, and nothing resets sync or unregisters the device token.
12. **Root gating order:** auth, then entitlement (splash while resolving), then paywall, then personalization (triggered by the explicit flag or by `currentUser.requirePersonalization`, so a server-side flip is respected), then GetStarted, then MainTabView (`FitnessLinkApp.swift`).
    - **Android: PARTIAL.** Only auth, personalization flag and main tabs. No entitlement gate, no reactive `requirePersonalization`, no GetStarted.
13. **After-login hooks.** On login iOS refreshes entitlements, runs `performInitialSync` (which re-saves the `users` row afterwards), `MediaPrefetcher.runNow`, and `MediaURLProvider.primeCatalog`.
    - **Android: PARTIAL.** It runs only `SyncManager.performInitialSync()`, which calls `DatabaseManager.clearUserData()` and wipes the `users` row that login just saved, without re-saving it. Profile then shows "Fitness User".
14. **Foreground hooks.** On scene active iOS refreshes entitlements (and re-verifies if near expiry), syncs the badge from the server, primes the catalog URLs, and reconciles classification, calendar and reminders.
    - **Android: PARTIAL.** `SyncScheduler` on `ProcessLifecycleOwner` `onStart` runs a full sync, `MediaPrefetcher.runIfNeeded`, and calendar sync. iOS removed prefetch from the foreground path on purpose, because bulk video downloads starved streaming.

#### B. Personalization / onboarding

15. **Personalization questionnaire.** Paged single- or multi-select, using `GET core/personalization`, `GET core/personalization/me` and `POST core/personalization/me` (`[{personalizationId, personalizationOptionId}]`). Files: `Views/Personalization/PersonalizationView.swift`, `PersonalizationItemView.swift`, `ViewModels/PersonalizationViewModel.swift`, `Services/API/PersonalizationService.swift`.
    - **Android: PARTIAL** (`ui/personalization/PersonalizationScreen.kt`, `PersonalizationViewModel.kt`, `PersonalizationApi.kt`). Differences:
      - iOS saves selections to the server on every toggle; Android saves only on Finish.
      - iOS blocks Finish when offline ("online-only by design"); Android marks the user personalized locally even when offline.
      - iOS shows an error alert; Android swallows all errors.
      - iOS calls `AuthManager.markPersonalizationComplete()`; Android has no equivalent, so the in-memory `requirePersonalization` stays stale.
      - Selection merging differs: iOS takes the union of local and server; Android lets the server win and uses local only if the server returns nothing.
16. **GetStartedView** (FTNSSLNK-356). Shown once after personalization: "You're all set, {name}" with four choices — Start a program (`.programs`), Start a workout (`.workouts`), Build your own (`.workoutEditor(nil)`), Build with AI (`.workoutEditorGenerate(seedPrompt:)`, prompt built from the answers) — plus "I'll explore on my own". The chosen route is parked on `AppRouter` and consumed by MainTabView.
    - **Android: MISSING.** There is no pending-route router either.
17. **Profile > Personalization (edit)** (`PersonalizationProfileView.swift` wraps PersonalizationView). **Android: PRESENT** (route `personalizationProfile`, which reuses PersonalizationScreen with `onBack`).

#### C. Billing / paywall (Billing service, base path `billing/api/v1`)

18. **Hard paywall: PaywallView** (`Views/Billing/PaywallView.swift`). It cannot be dismissed. Contents:
    - Benefits list.
    - Monthly and annual plan cards (`com.fitnesslink.monthly` / `com.fitnesslink.annual`), with an annual savings badge computed from store prices.
    - Trial eligibility per product; trial copy and period come from the StoreKit intro offer ("Start 1 Week Free Trial").
    - Disclosure text, Restore Purchases, Terms and Privacy links (`AppLinks.swift`), and Sign Out.
    - Error states: product-load retry, "already linked to another account" (409), and a stalled-purchase watchdog (120 s).
    - **Android: MISSING.** No Play Billing dependency, no paywall, no gate.
19. **EntitlementStore** (`Services/Billing/EntitlementStore.swift`). Wraps `GET billing/entitlement/me`.
    - Cached in UserDefaults and trusted only until `currentPeriodEnd`; fails closed.
    - `isResolving` drives the splash; `isNearExpiry` triggers re-verification.
    - Refreshed on login and foreground, and after any 402 (`APIClient` treats 402 as `APIError.paymentRequired` and refreshes).
    - **Android: MISSING.** There is no 402 handling, and `ApiService` has no `BILLING` entry or base URL.
20. **StoreKitManager purchase flow** (`Services/Billing/StoreKitManager.swift` plus `Services/API/BillingService.swift`):
    1. `GET billing/customers/me/checkout-context` returns `appleAppAccountToken`.
    2. Purchase with that token.
    3. `POST billing/subscriptions/apple/verify {jwsRepresentation}`.
    4. Only then finish the transaction (never on a 409).
    
    It also runs a `Transaction.updates` listener, drains unfinished transactions on launch, prevents duplicate verifies, and has `reverifyCurrentEntitlements`.
    - Restore: `POST billing/subscriptions/restore {source:"Apple", signedTransaction}`.
    - Also available: `GET billing/plans`, `GET billing/subscriptions/me` (404 means none).
    - **Android: MISSING.** It needs a Play Billing equivalent. The models already allow source `Google`, but there is no Google verify endpoint in the iOS client; check the backend for a Google verify/restore contract.
21. **BillingView / Subscription screen** (`Views/Profile/BillingView.swift`, FTNSSLNK-284).
    - Plan name and a status pill: ACTIVE / CANCELS SOON / TRIAL / GRACE, etc.
    - Renewal or end date, trial-end notice, "Billed through X", and a grace-period payment-problem banner.
    - Actions depend on the source: Comped shows "on us" with no actions; Stripe shows "Manage on the web" (`AppLinks.webBillingPortal`); App Store shows Manage Subscription (system sheet) plus Restore.
    - Data comes from EntitlementStore and `GET subscriptions/me`.
    - **Android: PARTIAL / placeholder.** `ui/profile/BillingScreen.kt` hardcodes "FitnessLink Pro / Active / $9.99 / +30 days / Google Play". It has only a Play Store subscriptions deep link and no API calls.

#### D. Profile and settings

22. **ProfileView** (`Views/Profile/ProfileView.swift`, `ProfileViewModel.swift`). Name and email, then a static menu: Personal Info, Personalization, Goals, Measurements, Weight Log, Progress Photos, Workout Report, Nutrition Report, Preferences, Subscription, then Logout.
    - **Android: PRESENT.** `ProfileScreen.kt` and `ProfileViewModel.kt`; the menu is seeded from `MockDataProvider.profileMenuItems` into the `profile_menus` table.
    - Android adds Achievements, Notifications and Calendar sync entries. Labels and order differ ("Billing" vs "Subscription", "Weight" vs "Weight Log", "Photos" vs "Progress Photos").
23. **PersonalInfoView** (`Views/Profile/PersonalInfoView.swift`, `PersonalInfoViewModel.swift`). Editable first and last name, username, email, phone and country, with Save enabled only when something changed. Saves locally, then `PUT core/users/{id}` (returns 204), then re-reads `users/me`.
    - **Android: PRESENT, with a bug** (`PersonalInfoScreen.kt`, `PersonalInfoViewModel.kt`). `UserApi.update` declares an `FLUser` return for a 204 response. The error is swallowed, so it is silent.
24. **Profile photo upload.** iOS does it in two steps:
    1. `POST core/media` (multipart, `container=photos`) returns `mediaId`.
    2. A full-body `PUT core/users/{id}` with `profileImageId`.
    
    The MediaRef is `.userPhoto(id: profileImageId)` (keyed by the media asset ID), and the old ref is invalidated.
    - **Android: PARTIAL / divergent.** It uses `POST users/me/profile-photo` (`UserApi.uploadProfilePhoto`, endpoint defined on iOS but unused there), and keys `MediaRef.UserPhoto(userId)` by **user ID**. To match iOS, switch to `POST media` plus `PUT users/{id}` and key the ref by `profileImageId`.
25. **Delete account.** Both platforms show a confirm dialog that does nothing. Equal (stub on both).
26. **"Billing & Subscription" link from Personal Info.** Present on both.
27. **PreferencesView** (`Views/Profile/PreferencesView.swift`):
    - Appearance picker (System / Light / Dark via `@AppStorage`).
    - Link to Notification Preferences.
    - Workout session settings: exercise video audio, voice cues, coach voice picker, countdown beeps, weight unit (lbs/kg), distance unit (km/mi).
    - "Use Apple Health for Calories".
    - iOS Calendar sync toggle plus target-calendar picker and backfill.
    - Grocery list to Apple Reminders sync.
    - **Android: MISSING.** The `preferences` route shows ProfileStubScreen ("Coming Soon"). The theme is light-only (no `darkColorScheme`).
    - Calendar sync exists separately in Android's `CalendarSyncSettingsScreen`. Reminders sync, units, audio and voice settings, and Health Connect are all absent.
28. **DeveloperSettingsView** (`Views/Profile/DeveloperSettingsView.swift`):
    - Environment: NGROK or Production.
    - NGROK URL, connection test, and status (network, sync, last sync, base URLs).
    - Actions: Force Sync, "Reset Sync Cursor & Re-pull", Clear Local Cache.
    - No screen links to it on iOS.
    - **Android: PRESENT**, also with no entry point (`ui/profile/DeveloperSettingsScreen.kt`, route `developerSettings`). Missing "Reset cursor & re-pull".
    - Android's environments are LOCAL (`10.0.2.2:5100/5200/5300`), NGROK, STAGING and PRODUCTION. Android production hosts are `core.fitnesslink.app/api/v1/`, etc.; iOS production is `https://api.fitnesslink.fit/{core|nutrition|notifications|billing}/api/v1`. **Android production URLs look wrong.**
29. **ProfileStubView / Goals / Photos / Weight / Measurements / Analytics views.** These files are "Coming Soon" stubs on iOS. The real routes go to GoalListView, ProgressPhotosView, WeightLogView and MeasurementsLogView. Android routes go to real screens too (GoalsListScreen, ProgressPhotosScreen, WeightLogScreen, MeasurementsLogScreen). **Android: PRESENT.** `ProfileStubScreen` is used for `preferences`, `mealTypeDetail`, `foodItemDetail`, `reportDetail` and `workoutAggregate`.

#### E. Notifications (Notification service)

30. **Inbox: NotificationsView** (`Views/Components/NotificationsView.swift`, `NotificationRowView.swift`, `NotificationsViewModel.swift`). Endpoints: `GET notifications/me?page&pageSize`, `GET notifications/me/unread-count`, `PUT notifications/{id}/read`, `PUT notifications/read-all`.
    - Features: tabs (All / System / Goals / Content / Calendar); Today / Yesterday / Earlier groups; Mark All Read; swipe to delete (local only); tap to mark read and navigate.
    - Search bar with wildcard `*` and multi-term AND (`NotificationsViewModel.matches`).
    - Gear icon linking to Notification Preferences.
    - On appear: asks for push permission if needed and clears the badge. Reloads on `.pushNotificationReceived`.
    - **Android: PARTIAL** (`ui/notifications/NotificationsScreen.kt`, `NotificationsViewModel.kt`). Present: tabs, groups, mark-all, swipe delete, deep-link tap.
    - Missing: search, the gear link, server unread count (Android counts locally), badge clearing, live refresh on push.
31. **Deep-link resolver** (`Services/DeepLinkResolver.swift`). Handles personalInfo, workoutReport, nutritionReport, goals, programs, workouts, calendar (switches to the tab), groceryList, `workoutDetail/{id}`, `programDetail/{id}`, `movementDetail/{id}`, and legacy `fitnesslink://shared/{workout|program|movement|mealplan}/{id}`.
    - **Android: PARTIAL** (`NotificationsViewModel.resolveDeepLink`). Missing programDetail, movementDetail, calendar and the legacy shared links.
32. **Unread badge on the header bell.** iOS reads the count from the local DB (`Views/Components/HeaderView.swift`). **Android: MISSING.** `ui/components/HeaderView.kt` shows the bell with no badge.
33. **Push token lifecycle (FCM)** (`Services/PushNotificationManager.swift`, `AppDelegate.swift`).
    - Register: `POST notifications/device-tokens {token, platform:"ios"}`, which returns a GUID that is stored.
    - Token rotation: `POST device-tokens/{id}/refresh` (body is the bare token string).
    - Logout: `DELETE device-tokens/{id}`.
    - A token that arrives before auth is held until `userDidAuthenticate()`.
    - **Android: MISSING.** `firebase-messaging` is a dependency and `NotificationServiceApi` has register/unregister, but there is no `FirebaseMessagingService`, no `onNewToken`, no refresh endpoint, and nothing calls them. The manifest also lacks the `POST_NOTIFICATIONS` permission.
34. **Push tap and foreground handling.** On tap: parse `deepLink`, `notificationId` and `type`; mark read locally and on the server; send the route to `AppRouter`; MainTabView consumes it (including on cold start). In the foreground the banner still shows and the inbox refreshes. The badge is re-synced from the server unread count on every foreground.
    - **Android: MISSING.**
35. **Notification pre-prompt sheet** (`Views/Components/NotificationPrePromptSheet.swift`). Offered from MainTabView 2 s after Home loads, at most 3 times, at least 3 days apart, and only while OS permission is undetermined. "Turn on" triggers the system prompt; "Not now" is respected.
    - **Android: MISSING.** It would need the Android 13+ `POST_NOTIFICATIONS` runtime permission.
36. **Notification category preferences** (`Views/Components/NotificationPreferencesView.swift`, `NotificationPreferencesViewModel.swift`, FTNSSLNK-233). Four toggles — System, Goals, Content, Calendar (`enableSystem`, `enableGoals`, `enableContent`, `enableCalendar`) — via `GET/PUT notifications/notification-preferences/me`. Saves optimistically and reverts on failure with an error message. Links to Goal Reminder Settings.
    - **Android: PARTIAL / divergent.** `ui/profile/NotificationSettingsScreen.kt` merges both screens. Content and Calendar are "Coming soon" info rows, and System has only re-engagement plus a link to OS settings.
    - Android's `GoalNotificationPreference` (`model/GoalModels.kt`) lacks `enableSystem`, `enableGoals`, `enableContent`, `enableCalendar`, `quietHoursStart` and `quietHoursEnd`. A full-object PUT will therefore reset those fields on the server. There is no revert on failure.
37. **Goal reminder settings** (`Views/Goals/NotificationSettingsView.swift`, `NotificationSettingsViewModel.swift`): coaching tone, six toggles, max daily notifications. **Android: PRESENT** (same file as above). It does not revert on failure.

#### F. Content sharing UI (shown on detail screens)

38. **PendingShareBanner** (`Views/Components/PendingShareBanner.swift`, used in Workout, Program and Movement detail). "X shared this {kind} with you" with Accept or Decline.
    - Endpoints: `GET content/shared` (`ContentService.pendingShare`), `POST content/shared/{id}/accept`, `POST content/shared/{id}/decline`.
    - **Android: MISSING.** `ContentApi` has only `share`, `getSharedContent` and `uploadMedia`. Accept, decline, revoke (`DELETE content/share/{id}`) and remove (`DELETE content/shared/{id}`) are absent.
39. **SharedBadge** ("Shared" / "Shared by X" pill on content rows and detail headers; `Views/Components/SharedBadge.swift`). **Android: MISSING.**
40. **In-app share to a user.** iOS uses ShareSheet with user search (`GET core/users/search?q&limit`) and `POST content/share`.
    - **Android: PARTIAL.** `ui/components/ContentActionsRow.kt` uses the system text chooser (`ACTION_SEND`) and never calls `ContentApi.share`.

#### G. My Videos / uploads

41. **MyVideosView** (`Views/MyVideos/MyVideosView.swift`, `VideoUploadRow.swift`; route `.myVideos`, which no screen currently links to). Flow:
    - Pick a video, then upload it in the background (`BackgroundUploadCoordinator`, `POST core/media` multipart with `container=videoassets`).
    - Row status: Queued, Uploading % (byte progress), Processing (awaiting Mux), Ready (play via AVKit with Mux HLS) or Failed (remove).
    - Upload state persists across relaunches and reattaches to background URLSession tasks.
    - **Android: MISSING.**
42. **MuxStatusPoller.** Polls `GET core/media/{id}` until `MuxStatus` is Ready or Errored, with backoff and a "never handed off to Mux" detection. **Android: MISSING.**
43. **MovementVideoAttachmentCoordinator.** Deferred "stitch" that does a PUT on the movement with the video once the upload is Ready; persisted. **Android: MISSING.**

#### H. Media

44. **MediaRef** (6 kinds; `typeWireValue`). **Android: PRESENT** (`media/MediaRef.kt`). Difference: Android's `fileExtensionHint` gives videos "mp4"; iOS keeps videos out of the single-file cache.
45. **MediaURLProvider** (`Services/Media/MediaURLProvider.swift`). Batched `POST core/media/resolve` with in-flight de-duplication, a 5-minute safety margin, and invalidate on 403.
    - Extra on iOS: the cache is **persisted** to `Caches/MediaCache/url-cache.json`. Also `refresh(ref)` (forced re-resolve for Mux JWT expiry), `cachedExpiry`, `primeURL(s)`, `primeCatalog()` (batch-resolves every catalog thumbnail in the DB on login and foreground), and `ingest(workout/program/movement/schedules)` (primes from URLs that arrive inline on payloads).
    - **Android: PARTIAL** (`media/MediaURLProvider.kt`). Has cached, url, resolve, invalidate and prime. Missing everything in the extras list above; the cache is in-memory only.
46. **MediaCacheStore** (`Services/Media/MediaCacheStore.swift`). Single-file cache plus 7-day sweep.
    - Extra on iOS: movement-video MP4s stored under a playback-ID-stamped name (`movement-video-mp4`) with `hasCurrentVideoFile` / `storeVideoFile`; legacy HLS bundle registry (`localHLSBundleURL`, `hasCurrentHLSBundle`, `sweepHLS`); `purgeRetiredVideoFiles`.
    - **Android: PARTIAL** (`media/MediaCacheStore.kt`). It has only the single-file cache and sweep, with no playback-ID staleness check.
47. **Today media manifest prefetch.** `GET core/media/me/today?utcOffsetMinutes`, prime URLs, download what is missing, then sweep. **Android: PRESENT** (`media/MediaPrefetcher.kt`). It even adds a self-healing retry and defers videos on metered networks.
48. **Offline MP4 downloads for movement videos.** iOS turns `stream.mux.com/{playbackId}.m3u8?token=…` into `/{playbackId}/highest.mp4?token=…`. It uses a plain foreground download on purpose, avoiding `AVAssetDownloadTask`, which forces iOS's "Downloading N items…" UI. Each file is stamped with the playback ID so a re-ingested asset is re-downloaded. Concurrency is capped at 2. `RetiredHLSDownloads.cancelLeftovers()` kills old background HLS sessions.
    - **Android: PARTIAL / broken.** It downloads the resolved URL as-is, which saves the `.m3u8` playlist as `.mp4`. There is no MP4 rendition rewrite, no playback-ID staleness check, no concurrency cap (it launches everything at once), and no 2-at-a-time queue.
    - Android lacks `media3-exoplayer-hls`, so `InteractivePlayerView` probably cannot stream Mux HLS either.
49. **Per-content prefetch** (`MediaPrefetcher.prefetch(refs:)`, called from `WorkoutDetailViewModel`, `MovementDetailView` and `MovementPreviewSheet`). **Android: MISSING.**
50. **FLImageView** (local file, then cached URL, then resolve; retry on 403). **Android: PRESENT** (`ui/components/FLImageView.kt`).

#### I. Sync

51. **SyncManager pull** (`Services/Sync/SyncManager.swift`): `GET core/sync/pull?cursor=&entities=a,b`.
    - Response: `{cursor, changes:[{entityType, entityId, isDeleted, payload(JSON string)}]}`.
    - Applied with `applySyncPayload` through the `tableName(forSyncEntityType:)` allow-list (handles both hyphen and underscore names). Pending calendar mutations are protected from being overwritten.
    - Neither platform drains `hasMore`.
    - **Android: PARTIAL / contract mismatch** (`sync/SyncManager.kt`, `network/api/SyncApi.kt`, `network/dto/SyncDtos.kt`).
      - Android queries `since=&domains=&limit=500`.
      - It expects `changes` as a map from entity type to a list of `{operation, data}`.
      - `DatabaseManager.applySyncPayload` is an empty placeholder, and `deleteSyncEntity` interpolates the raw server `entityType` as the table name (SQL injection risk; iOS uses an allow-list).
      - Android declares `hasMore` but ignores it.
52. **SyncManager push**: `POST core/sync/push {changes:[{entityType, entityId, action, payload}]}`. Response `{success, syncedAt?, idMappings?}`; `idMappings` are applied with `updateEntityId`.
    - **Android: PARTIAL / broken.** It sends `{entityId, entityType, operation, data:null, idempotencyKey, clientTimestamp}` — the payload is dropped. It expects `{accepted, rejected, cursor}` and marks everything synced regardless.
53. **Per-domain queue drains** (the real write path on iOS; generic `push()` is an ack-only stub). `performFullSync` order:
    1. purge stale in-progress sessions (enqueue abandons)
    2. `drainWorkoutPhases`, `drainWorkouts`, `drainSessions`, `drainSessionProgress`
    3. `drainFoodEntries`, `drainWaterEntries`, `drainNutritionGoals`, `drainMealSlots`, `drainGroceryItems`
    4. `drainGoals`, `drainHabits`, `drainHabitLogs`, `drainMeasurements`, `drainWeight`, `drainProgramEnrollments`
    5. `drainCalendar`
    6. `push()`, then `pull()`
    
    - Payloads carry `rowVersion`; a 409 drops the queue row.
    - **Android: MISSING.** Only the generic push/pull exists. HydrationViewModel enqueues `water_entries`, but nothing replays them.
54. **performInitialSync / reset.** **Android: PARTIAL.** It does not re-save the user after clearing, and `reset()` is never called on logout.
55. **SyncScheduler.** iOS syncs on foreground, on reconnect (5 s polling) and via a BGProcessingTask every ~15 min. **Android: PRESENT** (`sync/SyncScheduler.kt`: `ProcessLifecycleOwner`, `NetworkMonitor` flow, WorkManager periodic 15-min `SyncWorker`). Android also triggers media prefetch and calendar sync here.
56. **ConflictResolver.** **Android: PRESENT** (identical; unused on both).
57. **SyncDomain enum** (19 domains). **Android: MISSING.** Android only has constants for water and hydration.

---

### 2. Features Android has that iOS lacks
- `CalendarSyncSettingsScreen` as its own Profile screen, using the device Calendar Provider and runtime permission. iOS puts calendar sync inside Preferences.
- Profile menu entries for Achievements, Notifications (settings) and Calendar sync.
- Developer settings environments LOCAL (emulator `10.0.2.2` ports) and STAGING.
- Metered-network deferral for video prefetch, plus a "last run complete" self-healing retry.
- WorkManager periodic sync with a constraint requiring a network connection (iOS uses BGProcessingTask).
- An "Open Android settings" link (OS notification settings) inside notification settings.
- `UserApi.uploadProfilePhoto` (`POST users/me/profile-photo`) is actually used; the iOS endpoint exists but is unused.
- A seeded mock DB (`DatabaseSeeder` / `MockDataProvider`), including a fake user "user1".

---

### 3. Android architecture conventions to match
- **App bootstrap:** `FitnessLinkApp : Application` initializes the singletons in order: Firebase, `DatabaseManager`, `ApiConfiguration`, `NetworkMonitor`, `MediaCacheStore`, `MediaPrefetcher`, `SyncManager`, `SyncScheduler.start`. There is one `MainActivity` (ComponentActivity, `enableEdgeToEdge`, `setContent { FitnessLinkTheme { AppNavigation() } }`).
- **Singletons are Kotlin `object`s** (`AuthManager`, `ApiClient`, `SyncManager`, `MediaURLProvider`, `MediaPrefetcher`, `DatabaseManager`, `CalendarSyncService`). State is exposed as `MutableStateFlow` with `asStateFlow()`, and screens read it with `collectAsState()`. Small settings use SharedPreferences (`fl_api_config`, `fl_sync`). There is no DI framework (no Hilt or Koin).
- **ViewModels:** `class XViewModel : ViewModel()` using Compose `var x by mutableStateOf(...)` (not StateFlow), with `viewModelScope.launch` for async work.
  - Screens get them with `val viewModel: XViewModel = viewModel()` (lifecycle-viewmodel-compose; no factories or arguments).
  - Loading happens in `LaunchedEffect(Unit) { viewModel.loadData() }`.
  - `loadData()` reads the local DB synchronously, then `refreshFromServer()` checks `NetworkMonitor.isConnected.value`, wraps the call in try/catch, and silently falls back to the cache.
  - Screen IDs are passed as composable parameters, not through SavedStateHandle.
- **API layer:** Retrofit plus Gson.
  - Date format is `yyyy-MM-dd'T'HH:mm:ss'Z'`.
  - One interface per domain in `network/api/*Api.kt`, with `suspend` functions and `@GET/@POST/@PUT/@DELETE/@Multipart` annotations; paths are relative and have no leading slash.
  - `ApiClient` exposes `val xApi by lazy { retrofit(ApiService.CORE|NUTRITION|NOTIFICATION).create(...) }`.
  - Base URLs come from `ApiConfiguration.baseUrl(service)` (`enum ApiService`, `enum ApiEnvironment`). A Billing API would need a new `ApiService.BILLING` and base URL.
  - DTOs live in `network/dto/SyncDtos.kt` (a catch-all) and `model/api/*` (`FLUser`, etc.). Paged results use `PaginatedResponse<T>`. Errors are in `network/ApiError.kt`.
  - Auth comes from an OkHttp interceptor that calls `AuthManager.getIdToken()` inside `runBlocking`. Logging uses `HttpLoggingInterceptor` at BODY level.
- **Persistence:** raw SQLite through `SQLiteOpenHelper` in `persistence/DatabaseManager.kt` (`fitnesslink.db`, hand-written `CREATE TABLE` statements, versioned `migrateToVN` functions, about v10).
  - **No Room.** Access uses `rawQuery`/`execSQL`/`ContentValues` helper functions such as `user()`, `saveUserFromApi`, `insertNotification`, `enqueueSyncEntry(entityType, entityId, action, payload)`, `pendingSyncEntries` and `markSyncEntrySynced`.
  - JSON columns are stored with Gson (`phasesJson`, `optionsJson`).
  - The only test is `src/androidTest/.../DatabaseIntegrationTests.kt`.
- **Navigation:** Navigation-Compose with string routes.
  - `ui/navigation/AppRoute.kt` is a sealed class with route strings; parameterized routes have `companion ROUTE` and `createRoute(id)`. It is used inconsistently — most `navigate()` calls build strings inline (`"workoutDetail/$id"`).
  - `AppNavigation.kt` holds a top-level state switch (auth, then personalization, then main) and an `AuthNavigation` NavHost (welcome/login/signUp).
  - `MainTabNavigation` is a Scaffold with a Material3 `NavigationBar`: 5 tabs (home, catalog, nutrition, calendar, profile) with drawable selected/unselected icons, using `popUpTo(start){saveState}`, `launchSingleTop` and `restoreState`.
  - There is one NavHost for every sub-screen. Screens take `onBack = { navController.popBackStack() }` and `onNavigate: (String) -> Unit` callbacks, and arguments use `navArgument(... NavType.StringType)`.
  - There is no global router or pending-route mechanism; iOS uses `AppRouter.shared.open(route)`.
  - Tab order differs from iOS, which puts Calendar before Nutrition.
- **UI and theme** (`ui/theme/`):
  - `Color.kt` constants: `FLPrimary #23AF8D`, `FLPrimaryDark #1A8A6E`, `BackgroundColor #F0F4F5`, `TextPrimaryColor #1A1A1A`, `TextSecondaryColor #6B7280`, `OrangeTheme #F69833`, `PurpleTheme #8B5CF6`, `BlueTheme #3B82F6`, `MediumGray`, `BlendGray`, `DisabledButton #B0B0B0`, `HighlightState #E8F5F1`, `White`, `Black`.
  - `Theme.kt` is a single `lightColorScheme` (primary FLPrimary, secondary Orange, tertiary Purple); there is no dark theme.
  - Screens mostly hardcode `fontSize = X.sp` instead of using typography. Typical layout: a `Column(Modifier.fillMaxSize().background(BackgroundColor))` with `HeaderView()` or `HeaderBackView(onBack)` (`ui/components/HeaderView.kt`), and white `RoundedCornerShape(12.dp)` cards.
  - Shared components: `ActionButtonView(text, isEnabled, isLoading, onClick)` and `SocialSignInButton` (`ui/components/Buttons.kt`), `SeparatorView`, `FLImageView(ref)`, `StepProgressView`, `InfoViews`.
  - Auth screens use the `R.drawable.welcome` background with a black gradient, the `R.drawable.logo`, and pill text fields (`RoundedCornerShape(100.dp)`, white container, transparent border).
  - Developer settings is the exception: it uses Material3 `Scaffold`/`TopAppBar`.
- **Libraries present:** Firebase Auth and Messaging (BOM), WorkManager, lifecycle-process, Retrofit/OkHttp/Gson, Coil, Media3 ExoPlayer and UI (no HLS module), CameraX, ML Kit barcode.
  - **Absent and needed for parity:** Play Billing, Credential Manager / googleid (Google sign-in), Media3 HLS, and a `FirebaseMessagingService` plus manifest entries and the `POST_NOTIFICATIONS` permission.
