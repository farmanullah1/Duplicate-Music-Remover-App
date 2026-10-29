package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.settings.AppSettings
import com.example.data.settings.ThemeMode

@Composable
fun SafaiTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> {
            val bg = if (settings.amoledBlack) DarkBg else Color(0xFF0B0F14)
            darkColorScheme(
                primary = Teal400,
                onPrimary = Color(0xFF00201C),
                primaryContainer = Teal900,
                onPrimaryContainer = Color(0xFFB9F5EC),
                secondary = Violet400,
                onSecondary = Color(0xFF1E0E62),
                secondaryContainer = Color(0xFF37277E),
                onSecondaryContainer = Color(0xFFE4DFFF),
                tertiary = Gold400,
                error = Danger,
                background = bg,
                surface = DarkSurface,
                surfaceContainer = DarkSurface2,
                surfaceContainerHigh = DarkSurface3,
                outline = DarkOutline,
                outlineVariant = DarkOutline.copy(alpha = 0.6f),
                onSurface = DarkText,
                onSurfaceVariant = DarkTextDim
            )
        }
        else -> {
            lightColorScheme(
                primary = Teal600,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFB9F5EC),
                onPrimaryContainer = Teal900,
                secondary = Violet600,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE4DFFF),
                onSecondaryContainer = Color(0xFF1E0E62),
                tertiary = Gold700,
                error = Danger,
                background = LightBg,
                surface = LightSurface,
                surfaceContainer = LightSurface2,
                surfaceContainerHigh = Color(0xFFE2E8F0),
                outline = LightOutline,
                outlineVariant = LightOutline.copy(alpha = 0.6f),
                onSurface = LightText,
                onSurfaceVariant = LightTextDim
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val reduce = settings.reduceMotion || rememberAnimatorScaleZero()

    CompositionLocalProvider(
        LocalReduceMotion provides reduce,
        LocalSpacing provides Spacing
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SafaiTypography,
            shapes = SafaiShapes,
            content = content
        )
    }
}
