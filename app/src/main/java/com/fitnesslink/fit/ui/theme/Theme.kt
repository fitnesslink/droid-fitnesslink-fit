package com.fitnesslink.fit.ui.theme

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.fitnesslink.fit.settings.Appearance
import com.fitnesslink.fit.settings.UserSettings

/**
 * Light scheme: exactly the slots it had before FTNSSLNK-540, so light mode
 * stays pixel-identical (stock Cards / TextFields keep their current default
 * container and outline colours).
 */
private val LightColorScheme = lightColorScheme(
    primary = FLPrimary,
    onPrimary = LightFLColors.onBrand,
    primaryContainer = FLPrimary,
    background = LightFLColors.background,
    surface = LightFLColors.surface,
    onBackground = LightFLColors.textPrimary,
    onSurface = LightFLColors.textPrimary,
    onSurfaceVariant = LightFLColors.textSecondary,
    secondary = OrangeTheme,
    tertiary = PurpleTheme
)

/**
 * Dark scheme derived from [DarkFLColors] so stock components (cards,
 * dialogs, sheets, text fields, switches, menus) match the hand-built
 * screens. Every surface/outline slot is filled: Material's defaults are
 * purple-tinted and would leak into containers otherwise.
 */
private fun darkMaterialScheme(c: FLColors): ColorScheme {
    return darkColorScheme().copy(
        primary = FLPrimary,
        onPrimary = c.onBrand,
        primaryContainer = FLPrimaryDark,
        onPrimaryContainer = c.onBrand,
        inversePrimary = FLPrimary,
        secondary = OrangeTheme,
        onSecondary = c.onBrand,
        secondaryContainer = c.highlight,
        onSecondaryContainer = c.textPrimary,
        tertiary = PurpleTheme,
        onTertiary = c.onBrand,
        background = c.background,
        onBackground = c.textPrimary,
        surface = c.surface,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceMuted,
        onSurfaceVariant = c.textSecondary,
        surfaceTint = FLPrimary,
        inverseSurface = c.textPrimary,
        inverseOnSurface = c.background,
        error = c.danger,
        outline = c.border,
        outlineVariant = c.divider,
        scrim = c.scrim,
        surfaceBright = c.surfaceElevated,
        surfaceDim = c.background,
        surfaceContainerLowest = c.surface,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceElevated,
        surfaceContainerHighest = c.surfaceElevated,
    )
}

private val DarkColorScheme = darkMaterialScheme(DarkFLColors)

/** Resolve the Appearance preference against the device setting. */
@Composable
fun isAppInDarkTheme(): Boolean {
    val appearance by UserSettings.appearanceFlow.collectAsState()
    return when (appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
}

@Composable
fun FitnessLinkTheme(
    darkTheme: Boolean = isAppInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
            // configChanges="uiMode" keeps the activity alive on an Appearance
            // switch, so the XML windowBackground isn't re-read; set it here so
            // transitions and keyboard gaps match the theme.
            val background = if (darkTheme) DarkFLColors.background else LightFLColors.background
            window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
        }
        ApplyAppNightMode()
    }

    CompositionLocalProvider(LocalFLColors provides if (darkTheme) DarkFLColors else LightFLColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Push the in-app Appearance down to the platform (API 31+) so non-Compose
 * UI follows it too: view-based date/time pickers, system dialogs, camera
 * chrome, and the window background between screens. MainActivity declares
 * `configChanges="uiMode"`, so this re-themes in place without recreating the
 * activity. Below API 31 those surfaces follow the device setting.
 */
@Composable
private fun ApplyAppNightMode() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = LocalContext.current
    val appearance by UserSettings.appearanceFlow.collectAsState()
    LaunchedEffect(appearance) {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager ?: return@LaunchedEffect
        uiModeManager.setApplicationNightMode(
            when (appearance) {
                Appearance.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                Appearance.LIGHT -> UiModeManager.MODE_NIGHT_NO
                Appearance.DARK -> UiModeManager.MODE_NIGHT_YES
            }
        )
    }
}
