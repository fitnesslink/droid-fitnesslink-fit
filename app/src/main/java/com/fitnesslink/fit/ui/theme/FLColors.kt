package com.fitnesslink.fit.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * FTNSSLNK-540. Semantic, theme-aware colours. Screens read these through
 * [FLTheme.colors] instead of the light-only constants in `Color.kt`, so a
 * screen goes dark just by using them.
 *
 * Light values are today's Android constants exactly (light mode must not
 * change); dark values come from the iOS named colours in
 * `Assets.xcassets/Colors` (the `dark` luminosity variants) so both platforms
 * look the same. Brand / accent colours (`FLPrimary`, `OrangeTheme`,
 * `PurpleTheme`, `BlueTheme`) are shared and stay in `Color.kt`.
 *
 * Mapping from the legacy constants lives in `ui/theme/README.md`.
 */
@Immutable
data class FLColors(
    /** Screen background (`BackgroundColor`). iOS `BackgroundColor`. */
    val background: Color,
    /** Cards, list groups, rows (`White` used as a fill). iOS `Surface`. */
    val surface: Color,
    /** Sheets, dialogs, menus raised above a [surface]. */
    val surfaceElevated: Color,
    /** Subtle fill inside a card: settings groups, input wells (`MenuColor`). */
    val surfaceMuted: Color,
    /** Body and title text (`TextPrimaryColor`, `Black` used as text). */
    val textPrimary: Color,
    /** Secondary text, captions (`TextSecondaryColor`, `InfoColor`). */
    val textSecondary: Color,
    /** Hints, placeholders, inactive icons (`MediumGray`). */
    val textTertiary: Color,
    /** 1dp separators (`BlendGray` / `MediumGray` used as a line). */
    val divider: Color,
    /** Outlines around inputs, chips, unselected cards. */
    val border: Color,
    /** Calendar grid lines (`CalendarLine`). */
    val calendarLine: Color,
    /** Empty track behind progress bars and rings (`ProgressBG`). */
    val progressTrack: Color,
    /** Image / video placeholder fill (`ImagePlaceholder`). */
    val imagePlaceholder: Color,
    /** Selected row / selected day / brand-tinted fill (`HighlightState`). */
    val highlight: Color,
    /** Disabled button fill (`DisabledButton`). */
    val disabled: Color,
    /** Text and icons drawn on a brand-teal or accent fill (`White` on [FLPrimary]). */
    val onBrand: Color,
    /** Dimming behind overlays, and gradient scrims over photos (stays dark in both modes). */
    val scrim: Color,
    /** Destructive actions (Logout, Delete). */
    val danger: Color,
    val isDark: Boolean,
)

@Suppress("DEPRECATION") // the legacy constants are the light values by definition
val LightFLColors = FLColors(
    background = BackgroundColor,
    surface = White,
    surfaceElevated = White,
    surfaceMuted = MenuColor,
    textPrimary = TextPrimaryColor,
    textSecondary = TextSecondaryColor,
    textTertiary = MediumGray,
    divider = BlendGray,
    border = CalendarLine,
    calendarLine = CalendarLine,
    progressTrack = ProgressBG,
    imagePlaceholder = ImagePlaceholder,
    highlight = HighlightState,
    disabled = DisabledButton,
    onBrand = White,
    scrim = Black.copy(alpha = 0.5f),
    danger = Color(0xFFFF0000),
    isDark = false,
)

@Suppress("DEPRECATION") // White / Black here are mode-independent (onBrand, scrim)
val DarkFLColors = FLColors(
    background = Color(0xFF1C1C1E),       // iOS BackgroundColor (dark)
    surface = Color(0xFF2C2C2E),          // iOS Surface (dark)
    surfaceElevated = Color(0xFF3A3A3C),
    surfaceMuted = Color(0xFF242426),
    textPrimary = Color(0xFFF2F2F7),      // iOS TextPrimaryColor (dark)
    textSecondary = Color(0xFFAEAEB2),    // iOS MenuColor (dark)
    textTertiary = Color(0xFF8E8E93),
    divider = Color(0xFF38383A),          // iOS CalendarLine (dark)
    border = Color(0xFF48484A),
    calendarLine = Color(0xFF38383A),     // iOS CalendarLine (dark)
    progressTrack = Color(0xFF3A3A3C),    // iOS ProgressBG (dark)
    imagePlaceholder = Color(0xFF2C2C2E), // iOS ImagePlaceholder (dark)
    highlight = Color(0xFF1E3A33),        // iOS HighlightState (dark)
    disabled = Color(0xFF48484A),         // iOS DisabledButton (dark)
    onBrand = White,
    scrim = Black.copy(alpha = 0.6f),
    danger = Color(0xFFFF453A),
    isDark = true,
)

val LocalFLColors = staticCompositionLocalOf { LightFLColors }

/** Entry point for theme-aware colours: `FLTheme.colors.surface`. */
object FLTheme {
    val colors: FLColors
        @Composable @ReadOnlyComposable
        get() = LocalFLColors.current
}
