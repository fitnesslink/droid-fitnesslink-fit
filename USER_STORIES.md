# FitnessLink Android — User Stories

Project: **FA — FitnessLink Android**
Board: https://intrisia-team.atlassian.net/jira/software/c/projects/FA/boards/343
Generated: 2026-04-18

This document captures user stories organized by epic for the FitnessLink Android app. Stories reflect features present in the codebase (Kotlin / Jetpack Compose / Retrofit / Firebase / Room) and are retroactively ticketed in JIRA.

**Totals:** 8 epics · 61 stories · 187 story points

---

## Epic FA-1 — User Authentication & Onboarding
Secure login/registration, Google/Facebook sign-in, personalization flow, password recovery, FCM device token.

| Key | Story | Points |
|---|---|---|
| [FA-9](https://intrisia-team.atlassian.net/browse/FA-9) | Log in with email and password | 3 |
| [FA-10](https://intrisia-team.atlassian.net/browse/FA-10) | Sign up with email and password | 5 |
| [FA-11](https://intrisia-team.atlassian.net/browse/FA-11) | Sign in with Google or Facebook | 5 |
| [FA-12](https://intrisia-team.atlassian.net/browse/FA-12) | Post-login personalization questionnaire | 5 |
| [FA-13](https://intrisia-team.atlassian.net/browse/FA-13) | Password reset and account recovery | 2 |
| [FA-14](https://intrisia-team.atlassian.net/browse/FA-14) | Register device token for push notifications | 2 |

---

## Epic FA-2 — Home Dashboard & Overview
Daily dashboard, calendar navigation, habit badges, goal cards, pull-to-refresh.

| Key | Story | Points |
|---|---|---|
| [FA-15](https://intrisia-team.atlassian.net/browse/FA-15) | View daily dashboard | 5 |
| [FA-16](https://intrisia-team.atlassian.net/browse/FA-16) | Calendar navigation by month | 3 |
| [FA-17](https://intrisia-team.atlassian.net/browse/FA-17) | Habit completion badges on home screen | 2 |
| [FA-18](https://intrisia-team.atlassian.net/browse/FA-18) | Goal progress summary cards on home screen | 3 |
| [FA-19](https://intrisia-team.atlassian.net/browse/FA-19) | Pull-to-refresh data sync | 2 |

---

## Epic FA-3 — Workout Management & Library
Browse catalog, view details, create/edit custom, favorite, saved workouts.

| Key | Story | Points |
|---|---|---|
| [FA-20](https://intrisia-team.atlassian.net/browse/FA-20) | Browse workout catalog | 5 |
| [FA-21](https://intrisia-team.atlassian.net/browse/FA-21) | View workout details and exercise list | 3 |
| [FA-22](https://intrisia-team.atlassian.net/browse/FA-22) | Create custom workout | 8 |
| [FA-23](https://intrisia-team.atlassian.net/browse/FA-23) | Edit an existing workout | 5 |
| [FA-24](https://intrisia-team.atlassian.net/browse/FA-24) | Favorite and save workouts | 2 |
| [FA-25](https://intrisia-team.atlassian.net/browse/FA-25) | View my saved workouts | 2 |

---

## Epic FA-4 — Workout Execution & Tracking
Session UI, videos, timers, set logging, RPE, post-workout summary.

| Key | Story | Points |
|---|---|---|
| [FA-26](https://intrisia-team.atlassian.net/browse/FA-26) | Start interactive workout session | 5 |
| [FA-27](https://intrisia-team.atlassian.net/browse/FA-27) | Navigate between exercises during session | 3 |
| [FA-28](https://intrisia-team.atlassian.net/browse/FA-28) | Exercise video playback with pause/play | 3 |
| [FA-29](https://intrisia-team.atlassian.net/browse/FA-29) | Automatic rest period timer with notifications | 3 |
| [FA-30](https://intrisia-team.atlassian.net/browse/FA-30) | Total workout duration timer | 2 |
| [FA-31](https://intrisia-team.atlassian.net/browse/FA-31) | Log reps and weight per set | 3 |
| [FA-32](https://intrisia-team.atlassian.net/browse/FA-32) | Complete workout and rate RPE | 3 |
| [FA-33](https://intrisia-team.atlassian.net/browse/FA-33) | View post-workout summary | 2 |

---

## Epic FA-5 — Nutrition Tracking & Meal Planning
Food logging, barcode scanning, nutrition totals, meal plans, grocery lists.

| Key | Story | Points |
|---|---|---|
| [FA-34](https://intrisia-team.atlassian.net/browse/FA-34) | Log food entry manually | 3 |
| [FA-35](https://intrisia-team.atlassian.net/browse/FA-35) | Barcode scanning for food logging | 5 |
| [FA-36](https://intrisia-team.atlassian.net/browse/FA-36) | Search and add from recent foods | 2 |
| [FA-37](https://intrisia-team.atlassian.net/browse/FA-37) | View daily nutrition summary | 3 |
| [FA-38](https://intrisia-team.atlassian.net/browse/FA-38) | Set and edit nutrition goals | 2 |
| [FA-39](https://intrisia-team.atlassian.net/browse/FA-39) | View meal-by-meal nutrition breakdown | 2 |
| [FA-40](https://intrisia-team.atlassian.net/browse/FA-40) | Create and view weekly meal plans | 8 |
| [FA-41](https://intrisia-team.atlassian.net/browse/FA-41) | Generate AI grocery list from meal plan | 5 |
| [FA-42](https://intrisia-team.atlassian.net/browse/FA-42) | Manage grocery checklist | 2 |
| [FA-43](https://intrisia-team.atlassian.net/browse/FA-43) | Edit or delete food entries | 1 |

---

## Epic FA-6 — Progress Tracking (Weight, Measurements, Photos)
Weight chart, measurements with deltas, CameraX progress photos, side-by-side comparison.

| Key | Story | Points |
|---|---|---|
| [FA-44](https://intrisia-team.atlassian.net/browse/FA-44) | Log weight entry | 2 |
| [FA-45](https://intrisia-team.atlassian.net/browse/FA-45) | View weight chart and trends | 3 |
| [FA-46](https://intrisia-team.atlassian.net/browse/FA-46) | Log body measurements | 3 |
| [FA-47](https://intrisia-team.atlassian.net/browse/FA-47) | View body measurement changes vs. previous | 2 |
| [FA-48](https://intrisia-team.atlassian.net/browse/FA-48) | Capture progress photos | 3 |
| [FA-49](https://intrisia-team.atlassian.net/browse/FA-49) | Side-by-side progress photo comparison | 3 |
| [FA-50](https://intrisia-team.atlassian.net/browse/FA-50) | Filter progress data by date | 2 |
| [FA-51](https://intrisia-team.atlassian.net/browse/FA-51) | Delete progress entries | 1 |

---

## Epic FA-7 — Goals, Habits & Achievements
Goal framework, habits, streaks, milestones, badges, pause/archive.

| Key | Story | Points |
|---|---|---|
| [FA-52](https://intrisia-team.atlassian.net/browse/FA-52) | Create a fitness goal | 5 |
| [FA-53](https://intrisia-team.atlassian.net/browse/FA-53) | View active goals and progress | 3 |
| [FA-54](https://intrisia-team.atlassian.net/browse/FA-54) | Create daily or weekly habits | 5 |
| [FA-55](https://intrisia-team.atlassian.net/browse/FA-55) | Log habit completion | 2 |
| [FA-56](https://intrisia-team.atlassian.net/browse/FA-56) | View habit streak counter | 2 |
| [FA-57](https://intrisia-team.atlassian.net/browse/FA-57) | Track goal milestones | 3 |
| [FA-58](https://intrisia-team.atlassian.net/browse/FA-58) | Unlock achievements and badges | 5 |
| [FA-59](https://intrisia-team.atlassian.net/browse/FA-59) | View my achievements | 2 |
| [FA-60](https://intrisia-team.atlassian.net/browse/FA-60) | Pause or archive goals | 2 |

---

## Epic FA-8 — Notifications, Settings & Profile
Notification center, FCM push, profile, units, privacy, developer debug.

| Key | Story | Points |
|---|---|---|
| [FA-61](https://intrisia-team.atlassian.net/browse/FA-61) | View notification center | 3 |
| [FA-62](https://intrisia-team.atlassian.net/browse/FA-62) | Configure notification preferences | 3 |
| [FA-63](https://intrisia-team.atlassian.net/browse/FA-63) | Receive push notifications | 3 |
| [FA-64](https://intrisia-team.atlassian.net/browse/FA-64) | Mark notifications as read | 1 |
| [FA-65](https://intrisia-team.atlassian.net/browse/FA-65) | View and edit user profile | 2 |
| [FA-66](https://intrisia-team.atlassian.net/browse/FA-66) | Update user preferences (units, coaching tone) | 2 |
| [FA-67](https://intrisia-team.atlassian.net/browse/FA-67) | Set training experience level | 1 |
| [FA-68](https://intrisia-team.atlassian.net/browse/FA-68) | Account and privacy settings | 3 |
| [FA-69](https://intrisia-team.atlassian.net/browse/FA-69) | Developer/QA debug menus with mock data | 2 |
