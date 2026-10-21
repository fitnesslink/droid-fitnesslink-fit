package com.fitnesslink.fit.ui.theme

import androidx.compose.ui.graphics.Color

// Brand + accent colours: identical in light and dark, safe to use directly.
val FLPrimary = Color(0xFF23AF8D)
val FLPrimaryDark = Color(0xFF1A8A6E)
val OrangeTheme = Color(0xFFF69833)
val PurpleTheme = Color(0xFF8B5CF6)
val BlueTheme = Color(0xFF3B82F6)
val ProgressHighlight = Color(0xFF23AF8D)

// Legacy light-only neutrals (FTNSSLNK-540). They render light in Dark
// appearance; new code uses FLTheme.colors. Mapping: ui/theme/README.md.
// The values stay so unmigrated screens are unchanged in light mode, and they
// are the light half of LightFLColors.

@Deprecated("Light-only. Use FLTheme.colors.background", ReplaceWith("FLTheme.colors.background"))
val BackgroundColor = Color(0xFFF0F4F5)
@Deprecated("Light-only. Use FLTheme.colors.textPrimary", ReplaceWith("FLTheme.colors.textPrimary"))
val TextPrimaryColor = Color(0xFF1A1A1A)
@Deprecated("Light-only. Use FLTheme.colors.textSecondary", ReplaceWith("FLTheme.colors.textSecondary"))
val TextSecondaryColor = Color(0xFF6B7280)
@Deprecated("Light-only. Use FLTheme.colors.textTertiary (text/icons) or FLTheme.colors.divider (lines)", ReplaceWith("FLTheme.colors.textTertiary"))
val MediumGray = Color(0xFF9CA3AF)
@Deprecated("Light-only. Use FLTheme.colors.divider (lines) or FLTheme.colors.progressTrack (tracks)", ReplaceWith("FLTheme.colors.divider"))
val BlendGray = Color(0xFFE5E7EB)
@Deprecated("Light-only. Use FLTheme.colors.progressTrack", ReplaceWith("FLTheme.colors.progressTrack"))
val ProgressBG = Color(0xFFE5E7EB)
@Deprecated("Light-only. Use FLTheme.colors.disabled", ReplaceWith("FLTheme.colors.disabled"))
val DisabledButton = Color(0xFFB0B0B0)
@Deprecated("Light-only. Use FLTheme.colors.calendarLine (grid) or FLTheme.colors.border (outlines)", ReplaceWith("FLTheme.colors.calendarLine"))
val CalendarLine = Color(0xFFD1D5DB)
@Deprecated("Light-only. Use FLTheme.colors.imagePlaceholder", ReplaceWith("FLTheme.colors.imagePlaceholder"))
val ImagePlaceholder = Color(0xFFE5E7EB)
@Deprecated("Light-only. Use FLTheme.colors.surfaceMuted", ReplaceWith("FLTheme.colors.surfaceMuted"))
val MenuColor = Color(0xFFF9FAFB)
@Deprecated("Light-only. Use FLTheme.colors.textSecondary", ReplaceWith("FLTheme.colors.textSecondary"))
val InfoColor = Color(0xFF6B7280)
@Deprecated("Light-only. Use FLTheme.colors.highlight", ReplaceWith("FLTheme.colors.highlight"))
val HighlightState = Color(0xFFE8F5F1)
@Deprecated("Light-only. Use FLTheme.colors.surface (fills) or FLTheme.colors.onBrand (text/icons on teal)", ReplaceWith("FLTheme.colors.surface"))
val White = Color(0xFFFFFFFF)
@Deprecated("Light-only. Use FLTheme.colors.textPrimary (text) or FLTheme.colors.scrim (overlays)", ReplaceWith("FLTheme.colors.textPrimary"))
val Black = Color(0xFF000000)
