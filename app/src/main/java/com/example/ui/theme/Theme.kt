package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = SlateDark700,
    onPrimaryContainer = PrimaryContainerBlue,
    secondary = SecondarySlate,
    onSecondary = Color.White,
    tertiary = SecondaryTeal,
    background = SlateDark900,
    onBackground = Color.White,
    surface = SlateDark800,
    onSurface = Color.White,
    surfaceVariant = SlateDark700,
    onSurfaceVariant = SlateLight200
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerBlue,
    onPrimaryContainer = OnPrimaryContainerBlue,
    secondary = SecondarySlate,
    onSecondary = Color.White,
    tertiary = SecondaryTeal,
    background = SlateLight50,
    onBackground = SlateDark900,
    surface = Color.White,
    onSurface = SlateDark900,
    surfaceVariant = SlateLight100,
    onSurfaceVariant = SlateDark700
)

@Composable
fun DuplicateMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to maintain crisp brand palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    DuplicateMusicTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
