package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object Sizes {
    val touch = 48.dp
    val iconSm = 20.dp
    val icon = 24.dp
    val iconLg = 32.dp
    val iconHero = 56.dp
    val thumb = 56.dp
    val thumbLg = 72.dp
    val bottomBar = 68.dp
    val railWidth = 88.dp
    val drawerWidth = 280.dp
    val maxContent = 1040.dp
    val maxContentNarrow = 720.dp
}

// Brand
val Teal400 = Color(0xFF2DD4BF)
val Teal600 = Color(0xFF0F9D8E)
val Teal900 = Color(0xFF0B3B36)
val Violet400 = Color(0xFF8B7CF6)
val Violet600 = Color(0xFF6D5BE0)
val Gold400 = Color(0xFFF5C451)
val Gold700 = Color(0xFFB7791F)
val Success = Color(0xFF34D399)
val Warning = Color(0xFFFBBF24)
val Danger = Color(0xFFFF5D73)

// Dark (AMOLED)
val DarkBg = Color(0xFF000000)
val DarkSurface = Color(0xFF0B0F14)
val DarkSurface2 = Color(0xFF111820)
val DarkSurface3 = Color(0xFF18212B)
val DarkOutline = Color(0xFF263241)
val DarkText = Color(0xFFE8EEF5)
val DarkTextDim = Color(0xFF9AA9BA)

// Light
val LightBg = Color(0xFFF4F7FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurface2 = Color(0xFFEDF2F7)
val LightOutline = Color(0xFFD5DEE8)
val LightText = Color(0xFF0E1621)
val LightTextDim = Color(0xFF5B6B7C)

// Category colors
val CatAudio = Color(0xFF8B7CF6)
val CatImage = Color(0xFF38BDF8)
val CatVideo = Color(0xFFFB7185)
val CatDocument = Color(0xFFFBBF24)
val CatArchive = Color(0xFFF97316)
val CatApk = Color(0xFF34D399)
val CatOther = Color(0xFF94A3B8)

// Gradients
val BrandGradient = Brush.linearGradient(listOf(Teal400, Violet400))
val HeroGradient = Brush.linearGradient(listOf(Color(0xFF0B3B36), Color(0xFF0E2A47), Color(0xFF1B1646)))
val DangerGradient = Brush.linearGradient(listOf(Danger, Color(0xFFB4233A)))
