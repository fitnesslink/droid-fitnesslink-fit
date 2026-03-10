package com.fitnesslink.fit.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = FLPrimary,
    onPrimary = White,
    primaryContainer = FLPrimary,
    background = BackgroundColor,
    surface = White,
    onBackground = TextPrimaryColor,
    onSurface = TextPrimaryColor,
    secondary = OrangeTheme,
    tertiary = PurpleTheme
)

@Composable
fun FitnessLinkTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
