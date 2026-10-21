# Theme colours (FTNSSLNK-540)

Screens get dark mode for free by reading **`FLTheme.colors`** (or `MaterialTheme.colorScheme`)
instead of the light-only constants in `Color.kt`. `FitnessLinkTheme` swaps
`LightFLColors` ↔ `DarkFLColors` when the user changes Preferences → Appearance, or when the
device switches while Appearance = System. No restart is needed.

- Light values are the old Android constants, so light mode is unchanged.
- Dark values come from the iOS named colours (`iOS-fitnesslink-fit/FitnessLink/Assets.xcassets/Colors`, dark variants).
- Brand and accents (`FLPrimary`, `FLPrimaryDark`, `OrangeTheme`, `PurpleTheme`, `BlueTheme`,
  `ProgressHighlight`) are the same in both modes. Use them directly.

## Migrating a screen

| Legacy constant | Replace with | Notes |
|---|---|---|
| `BackgroundColor` | `FLTheme.colors.background` | screen background |
| `White` (fill: card, row, sheet) | `FLTheme.colors.surface` | dialogs / popovers above a card: `surfaceElevated` |
| `White` (text/icon on teal or an accent) | `FLTheme.colors.onBrand` | stays white in dark |
| `White` (text over a photo / video) | keep `Color.White` with `// theme-exempt: over media` | allowed only in exempt files, see below |
| `TextPrimaryColor`, `Black` (text) | `FLTheme.colors.textPrimary` | |
| `TextSecondaryColor`, `InfoColor` | `FLTheme.colors.textSecondary` | |
| `MediumGray` (text/icon) | `FLTheme.colors.textTertiary` | |
| `MediumGray` / `BlendGray` (1dp line) | `FLTheme.colors.divider` | |
| `BlendGray` / `ProgressBG` (track) | `FLTheme.colors.progressTrack` | |
| `CalendarLine` | `FLTheme.colors.calendarLine` | input/chip outlines: `border` |
| `ImagePlaceholder` | `FLTheme.colors.imagePlaceholder` | |
| `MenuColor` | `FLTheme.colors.surfaceMuted` | |
| `HighlightState` | `FLTheme.colors.highlight` | selected row/day, teal-tinted fill |
| `DisabledButton` | `FLTheme.colors.disabled` | |
| `Black.copy(alpha = …)` (dimming, photo gradients) | `FLTheme.colors.scrim` | |
| `Color.Red` (destructive) | `FLTheme.colors.danger` | |
| raw `Color(0xFF…)` | the closest token, or a new token in `FLColors` (light + dark) | never inline a new neutral |

Material components (Card, TextField, Switch, DropdownMenu, AlertDialog, ModalBottomSheet) pick up
`MaterialTheme.colorScheme`. In dark mode every surface/outline slot is mapped from `DarkFLColors`,
so prefer their defaults over hardcoded container colours. The light scheme intentionally keeps
its pre-540 slots so existing light screens don't shift.

## Guard

`./gradlew check` runs `checkHardcodedColors`. It fails if any file under `ui/` (except
`ui/theme/`) uses a legacy neutral, `Color.White`/`Color.Black` or a `Color(0x…)` literal and is
**not** listed in `config/hardcoded-colors-allowlist.txt`.

When you migrate a screen, delete its line from the allowlist; the task prints files that are
now clean. Only remove lines. To regenerate after a big migration, run
`./gradlew checkHardcodedColors -PupdateColorAllowlist` and check that the diff only removes lines.
Media-overlay files that must keep white text (players, camera) stay allowlisted with a comment.

## Previews

Annotate previews with `@PreviewLightDark` (androidx.compose.ui.tooling) and wrap them in
`FitnessLinkTheme { … }`. Appearance defaults to System, so the preview's night mode drives the
theme.

## Platform surfaces

- `res/values{,-night}/themes.xml` is a DayNight pair. The window background and bar icon
  contrast match the theme, so there's no white flash on a dark launch.
- On API 31+ `FitnessLinkTheme` calls `UiModeManager.setApplicationNightMode`, so view-based
  pickers, system dialogs and camera chrome follow the in-app choice. MainActivity has
  `configChanges="uiMode"`, so switching re-themes in place without recreating the activity.
  Below API 31 those surfaces follow the device setting.
