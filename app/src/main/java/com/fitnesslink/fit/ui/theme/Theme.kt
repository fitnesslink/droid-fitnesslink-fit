package com.fitnesslink.fit.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.fitnesslink.fit.settings.Appearance
import com.fitnesslink.fit.settings.UserSettings

private val LightColorScheme = lightColorScheme(
    primary = FLPrimary,
    onPrimary = White,
    primaryContainer = FLPrimary,
    background = BackgroundColor,
    surface = White,
    onBackground = TextPrimaryColor,
    onSurface = TextPrimaryColor,
    onSurfaceVariant = TextSecondaryColor,
    secondary = OrangeTheme,
    tertiary = PurpleTheme
)

/**
 * Dark scheme (iOS `AppTheme.dark`). Brand teal stays the accent; surfaces
 * drop to the dark neutrals so Material components (cards, switches,
 * dialogs, text fields) follow the user's Appearance choice. Screens that
 * still hardcode `BackgroundColor` / `White` render light until they are
 * migrated to `MaterialTheme.colorScheme` — see the parity status log.
 */
private val DarkColorScheme = darkColorScheme(
    primary = FLPrimary,
    onPrimary = White,
    primaryContainer = FLPrimaryDark,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    secondary = OrangeTheme,
    tertiary = PurpleTheme
)

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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
