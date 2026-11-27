# EMOM PRD v2.0 — implementation spec (iOS + Android)

Source: "EMOM PRD .pdf" (FitnessLink Master PRD, EMOM — Every Minute on the Minute, v2.0, target 2027-01-15).
iOS is built first (`iOS-fitnesslink-fit`); Android ports the same rules. This file is the shared contract —
both apps MUST produce identical numbers, strings and cue decisions for the same workout.

## 1. Terminology

| Term | Meaning |
|---|---|
| Interval | programmed seconds allotted to ONE exercise (default 60) |
| Round | one exercise interval |
| Cycle | one rotation through all exercises |
| Total Rounds | exercises × cycles |
| Total Duration | total rounds × interval |
| Work | time the user performs the exercise |
| Rest | remaining interval time after work |

Round r (1-based) runs member `(r − 1) % n`; its cycle is `(r − 1) / n + 1`.

## 2. Data mapping — NO new numeric columns

EMOM lives on the interval group's LEAD task (first member), same as today:

| Concept | Stored as |
|---|---|
| mode | `intervalMode = "emom"` |
| Total Duration (s) | `intervalWindowSeconds` (= totalRounds × interval) |
| Total Rounds | `rounds` / `totalRounds` on the lead (+ row.totalRounds, mirrored onto members like today) |
| Interval (s) | DERIVED: `intervalWindowSeconds / rounds`. Also written to `intervalWorkSeconds` for other clients, but never read back for EMOM |
| Cycles | DERIVED: totalRounds / exerciseCount |
| Sets / between-sets rest / work-per-min / rest-per-min / active recovery | removed for EMOM. New/edited EMOMs write `intervalSets = 1`, `intervalRestSeconds = null`, `intervalSetRestSeconds = null`, `restType = passive`, no rest movement |
| Exercise target | each member's existing `usesDuration` + `reps` / `durationSeconds` (wire: `reps` / `intervalSeconds`) |
| Exercise Notes | each member's `instructions` |
| Coach Notes (block) | NEW optional `coachNotes` string on the lead task (wire `coachNotes`). Backend column is being added separately; until it deploys the server drops it — clients must tolerate it missing. EMOM only (Tabata/HIIT/AMRAP keep using the lead's `instructions` as before) |

### 2.1 Resolving a stored lead into an EMOM plan (`emomPlan`)

```
n          = number of movement members (lead + advanced), min 1
sets       = max(1, lead.intervalSets ?? 1)          // legacy only
storedR    = positive(lead.rounds) ?? positive(lead.totalRounds) ?? positive(row.totalRounds)
window     = positive(lead.intervalWindowSeconds)
if storedR && window && window % storedR == 0 && window / storedR >= 5:
    interval = window / storedR ; perSet = storedR
elif window:   interval = 60 ; perSet = max(1, window / 60)      // legacy minutes model
elif storedR:  interval = 60 ; perSet = storedR
else:          interval = 60 ; perSet = n                         // 1 cycle
totalRounds   = perSet * sets          // legacy multi-set EMOMs are flattened (no between-sets rest)
totalSeconds  = totalRounds * interval
cycles        = ceil(totalRounds / n)  // display; complete when totalRounds % n == 0
```
Legacy EMOMs (window = minutes×60, rounds = minutes) therefore resolve to a 60 s interval with rounds = minutes —
exactly how they ran before. The legacy "work per minute" value is ignored.

`IntervalPlan` for EMOM: `workSeconds = interval`, `restSeconds = 0`, `roundsPerSet = totalRounds`, `sets = 1`,
`betweenSetsRestSeconds = 0`, `windowSeconds = totalSeconds`. `blockSeconds = totalSeconds`.

### 2.2 Session requirements

`requiredIterations` for an EMOM member = number of rounds that member occurs in
(`count of r in 1...totalRounds where (r-1) % n == memberIndex`) — i.e. cycles for complete-cycle EMOMs —
NOT the lead's `rounds` (which is total rounds). Iteration k of member m = its k-th occurrence = cycle k.

## 3. Builder (Workout Builder)

### 3.1 Creation defaults (EMOM chosen in the existing interval flow)
interval 60, cycles 1 → rounds = n, window = n × 60. E.g. 2 exercises → 2 rounds, 2:00.

### 3.2 Membership changes after timing is configured
Adding / removing / ungrouping members of an EMOM keeps **interval and cycles** fixed and recomputes
`rounds = cycles × newN`, `window = rounds × interval`. (Capture cycles from the OLD n before mutating.)
Reorder / swap change nothing.

### 3.3 Builder card (approved mock "Edit Workout")
Header "INTERVAL · EMOM" (existing). Summary card: title line
`"{20-min} EMOM · {60s} Interval · {20:00}"` (minutes part = `"{m}-min"` when totalSeconds % 60 == 0, else `"{m:ss}"`),
"Edit ›", then a tile row: Duration `"20 min"` (or `"7:30"`), Interval `"60 sec"`, Rounds `"20"`, Cycles `"10"`
(Cycles tile omitted when n == 1). Tapping opens Edit EMOM.

Member rows: `WORK` pill, name, target, chevron, existing ⋯ menu. Target text: `"20 reps"`, `"30 sec"`
(durations ≥ 60 as `"1:30"`), or `"Reps – Time"` when no target is set (reps == 0 and not duration-mode, or
duration-mode with no duration). Do not show the interval on member rows.

Expanded member (existing expandable card): Reps / Time selector, the existing +/- target control
(reps stepper, or duration chips/stepper), Exercise Notes field. No sets, no per-round weights for EMOM.

### 3.4 Edit EMOM sheet (approved mock 1)
Title **"Edit EMOM"** (never "Edit Interval"). Cancel (left, reverts every change made in the sheet) / Done (right, disabled while invalid).
Sections, in order:
- **Summary**: "EMOM" label, big `"20:00"` + "Total Time", line `"20 rounds · 10 cycles"` (single exercise: `"10 rounds"`).
- **TIMING**: *Total Duration* — numeric minutes field ("Enter the total time you want to program."), accepts decimals (7.5); *Interval Duration* — dropdown ("The length of each round.") with options 15,20,30,40,45,60,75,90,120,150,180,240,300 sec, labelled `"60 sec"` / `"2 min"`.
- **ROUNDS & CYCLES**: *Cycles* numeric field ("One cycle includes both exercises." / "…all 3 exercises." / single exercise: label **Rounds**, caption "Number of times to repeat the exercise."); *Total Rounds* read-only, accent colored, caption "Calculated automatically · 2 exercises selected" (hidden for single exercise).
- **PREVIEW**: one row per member in order: chip `R1`, name, target (or "Reps – Time"), trailing `"60s"`; footer `"Repeat sequence 10 times · 20 total rounds"` (single: `"Repeat 10 times · 10 total rounds"`).
- **COACH NOTES**: multiline field "Coaching cue (optional)" → lead.coachNotes.
- Keep the existing "Change interval style…" action at the bottom.
Removed for EMOM: Sets, Work/min, Rest/min, Active Recovery.

### 3.5 Dual-input sync (§4.3)
Track the most recently edited primary value (`duration` or `cycles`).
- Cycles edited → rounds = cycles×n, duration = rounds×interval. Always valid.
- Duration edited → rounds = duration / interval, cycles = rounds / n. Valid only if both divide exactly
  (and cycles ≥ 1). Otherwise show the validation and DO NOT write the model.
- Interval edited → if last primary was duration: re-run the duration rule with the new interval (may become invalid);
  else keep cycles and recompute duration.
Validation messages (exact strings):
- partial cycle: `"{duration} doesn't divide into complete cycles. Each cycle is {cycleTime} ({n} exercises × {interval}s). Try {lower} or {upper}."`
  where lower/upper are the nearest valid durations (multiples of n×interval) formatted m:ss / "N min".
- time target too long: `"{Exercise} is set to {target}, longer than the {interval}s interval. Shorten the target or lengthen the interval."`
- duration < one cycle: same partial-cycle message.
Done is disabled while any message shows. The workout's Save also refuses while any EMOM member's time target
exceeds its interval (show the same message in the existing save-error surface) — that check is the only one that
can be reached outside the sheet.

## 4. Instructional text (§7, EMOM-023) — generated, never stored

Number words: 1–9 → "one"…"nine", ≥ 10 → digits. Interval phrase: `"{s} seconds"`; when s ≥ 120 and s % 60 == 0 → `"{m} minutes"`.
- n == 1: `"Complete {target} {name} every {interval} for {rounds} rounds."` — target `"20"` for reps (→ "Complete 20 bodyweight squats…"),
  `"30 seconds of"` for time (→ "Complete 30 seconds of plank every…"), omitted when unset. Name lowercased. rounds as digits. (1 round → "for 1 round.")
- n ≥ 2: `"Complete one exercise every {interval}. Repeat {both|all three|all four|all 11} exercises for {cycles} {cycle|cycles}."`
  (cycles via number words: "for four cycles", "for 10 cycles", "for one cycle").
Heading above it: **"How this EMOM works"**. Shown on Workout Overview and in Playlist Mode. The Edit EMOM preview
communicates sequence + repeat count via its footer instead.

## 5. Workout Overview (detail, mock 3)
EMOM block card: badge "INTERVAL · EMOM" + "2 exercises". Tiles:
- n ≥ 2: 2×2 grid — Duration `"20:00"`, Interval `"60s"`, Rounds `"20"`, Cycles `"10"`.
- n == 1: one row of 3 equal tiles — Duration, Interval, Rounds.
Then "How this EMOM works" + text, then the existing exercise cards (thumbnail, name, target).

## 6. Playlist Mode (mock 4)
Same header as §5 (EMOM label, Duration, Interval, Rounds, Cycles when n ≥ 2) + "How this EMOM works" text.
Members: existing playlist rows (thumbnail, name, target, previous performance, logging circles, checkbox).
- Checkbox = exercise-level: completes every occurrence of that member (`markExerciseCompleted` over its
  occurrence count from §2.2). Other members untouched.
- Logging circles: the existing controls, but at most ONE per member for EMOM (do not render a circle per round);
  i.e. treat an EMOM member like a single-set exercise for the circle row.
- No timers, no auto-advance, no countdown audio in Playlist.
- Records: no durations from checkboxes; the session's mode (lastMode) says Playlist.

## 7. Interactive Mode (mock 5)

### 7.1 Preparation
The existing 10 s preparation screen precedes the block. For EMOM, the final FIVE seconds are spoken
"5, 4, 3, 2, 1" (one number per second) and the countdown ends with "Let's work!" (instead of "Go").
If the block is entered without the prep screen (runner's own lead-in), same 5-second spoken countdown when the
lead-in is ≥ 5 s, else what fits.

### 7.2 Runner state machine (per round r, member m = (r−1)%n)
- Round starts → WORK.
  - reps-based (member not duration-mode): WORK lasts the full interval. "Round Done" (visible while WORK and
    > 1 s remain) → outcome `.confirmed` (completion time = elapsed work seconds), phase flips to REST with the
    SAME end instant (interval boundary). If the interval expires in WORK → outcome `.unconfirmed`, next round.
  - time-based: WORK lasts `min(target, interval)` (target default = interval when unset). When it hits 0 →
    outcome `.autoCompleted` (completion time = work length) → REST for `interval − work` if > 0, else straight to
    the next round. No Round Done button.
- REST ends at the interval boundary → next round's WORK, or block complete after the last round.
- Rounds advance every interval; cycles every n rounds. Block total = totalRounds × interval (the progress bar and
  "remaining" use this; Round Done never changes the schedule).
- **Pause** freezes the interval timer and transitions (existing). **Resume** continues from the paused position.
- **Previous**: restart the previous round's FULL interval (any phase). In round 1 → leave the block (existing behaviour).
  >3 s into the current round's WORK → restart the CURRENT round's interval instead (existing pattern).
  Restarting a round clears that round's recorded outcome.
- **Next**: the next round at its full interval. Current round, if not confirmed/auto-completed, → `.skipped`.
  On the last round → leave the block (existing block-exit flow).
- **Skip** (existing control): same as Next for EMOM (never marks the round complete).
- No bottom "Skip Block" button; leaving the whole block stays in the ⋯ / existing navigation.
- Outcomes per round: `.confirmed`, `.autoCompleted`, `.unconfirmed`, `.skipped` (+ completion seconds).
  On block exit they are written to session progress: confirmed/auto → completed (durationSeconds = completion
  seconds, completionMethod "manual"/"auto"), unconfirmed → incomplete, skipped or never reached → skipped.
  Iteration = the member's cycle number.

### 7.3 Overlay layout
Row 1: `EMOM` chip + phase badge (`WORK` orange / `REST`). Row 2: `"EMOM BLOCK · 16:58 remaining"` (remaining only)
+ block progress bar (time based). Row 3:
- WORK: left = exercise name (uppercase, bold) + target ("20 reps" / "30 sec"); right = big countdown + `"ROUND 2 / 20"`.
  Below: large centered white **Round Done** button (reps-based only). No cycle on WORK.
- REST: left = big "REST"; right = countdown + `"CYCLE 1 / 10"` (accent "CYCLE", hidden when n == 1) + `"ROUND 2 / 20"`.
  Below: UP NEXT card — thumbnail, `"Push-ups · 10 reps"`, `"Round 3 / 20"`, chevron (tap = Next).
  Final round's REST: no Up Next card (or the existing next-item card).
Forward slashes, not "of". Existing top bar (Playlist switch, workout timer, mute, ⋯), phase label, transport bar stay.

## 8. Voice coaching (§9.10)

Every line below is the exact phrase. Rotations cycle in order and never repeat the previous pick.

| Moment | Phrase |
|---|---|
| prep last 5 s | "5", "4", "3", "2", "1", then "Let's work!" |
| round start | `"Round {r}. {Name}. {target}. {starter}"` — target "20 reps" / "30 seconds" (omitted if unset); starter rotates "Let's go!", "You've got this!", "Time to work!", "Let's get after it!" |
| new cycle (n ≥ 2, r > 1, (r−1)%n == 0) | prefix `"Cycle {c}. "` before "Round …" |
| final round (totalRounds > 1) | `"Last round! Give it all you've got! {Name}. {target}!"` (replaces the normal round-start line; no starter) |
| 10 s warning | "Ten seconds remaining!" — reps: when the unconfirmed interval has 10 s left; time: when work has 10 s left. Only when that phase is ≥ 15 s long. |
| final countdown | "5","4","3","2","1" in the last 5 s of the same timer (reps unconfirmed → interval end; time → work end) |
| Round Done (reps) | rest ≥ 20 s: rotate "Nice work! Take your rest.", "Well done! Catch your breath.", "Strong work! You've earned that rest."; 8–19 s: rotate "Great job! Recover.", "Nice work! Recover."; < 8 s: no speech |
| time-based complete | rest ≥ 20 s: rotate "Time! Nice work.", "Great job! Take your rest.", "Work complete. Recover."; 8–19 s: "Time! Recover."; rest < 8 s (incl. 0): no speech |
| unconfirmed expiry / skip / next / previous | NO completion praise; just the next round-start line |
| block complete | "Block complete. Way to work!" |

Priority (low number wins): 1 round transitions / block complete, 2 round-start exercise + target, 3 countdown
warnings ("Ten seconds remaining!", numbers), 4 completion / rest, 5 motivation. A cue whose level is LOWER priority
than the phrase in flight is dropped (never queued); an equal-or-higher one interrupts. Pause, Previous, Next, Skip
and leaving the block stop speech immediately; resume never replays missed cues; muting suppresses speech only
(timers/visuals continue), unmuting plays only future cues. Beeps/haptics for the last 3 s stay as today.

## 9. Workout Complete
Unchanged screen. EMOM block exit feeds the existing workout flow (never ends the workout when blocks remain).

## 10. Files (iOS reference implementation)
- Math/plan/metrics/instruction text: `FitnessLink/Models/WorkoutCreationModels.swift` (`IntervalMath.emom…`, `EmomMath`, `EmomText`)
- Runner: `FitnessLink/ViewModels/IntervalRunner.swift`
- Voice: `FitnessLink/Services/Session/EmomCoach.swift` + `AudioCueService.speakCoach`
- Overlay: `FitnessLink/Views/WorkoutSession/Interactive/IntervalOverlayView.swift`
- Edit sheet: `FitnessLink/Views/Workouts/EmomEditSheet.swift`; builder card/rows: `GroupEditorView.swift`
- Overview/playlist header: `IntervalTimelinePreview.swift` (`EmomMetricsHeader`)
- Tests: `FitnessLinkTests/EmomTests.swift`
