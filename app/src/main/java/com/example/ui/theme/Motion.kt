package com.example.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

object Motion {
    const val Short = 150
    const val Medium = 300
    const val Long = 500
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun <T> spring() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
}

val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalSpacing = staticCompositionLocalOf { Spacing }

@Composable
fun rememberAnimatorScaleZero(): Boolean {
    val c = LocalContext.current
    return remember {
        Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Press-down scale feedback (0.97) + ripple with double-tap protection. */
fun Modifier.pressScaleClickable(onClick: () -> Unit): Modifier = pressScaleClickable(debounceMs = 300L, onClick = onClick)

fun Modifier.pressScaleClickable(debounceMs: Long = 300L, onClick: () -> Unit): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val s by animateFloatAsState(
        targetValue = if (pressed && !reduce) 0.97f else 1f,
        animationSpec = spring(0.6f, 800f),
        label = "press"
    )
    this.graphicsLayer {
        scaleX = s
        scaleY = s
    }.clickable(
        interactionSource = src,
        indication = ripple(),
        onClick = {
            val now = System.currentTimeMillis()
            if (now - lastClickTime >= debounceMs) {
                lastClickTime = now
                onClick()
            }
        }
    )
}

fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "x"
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val hi = MaterialTheme.colorScheme.outlineVariant
    drawWithCache {
        val w = size.width
        val brush = Brush.linearGradient(
            listOf(base, hi, base),
            Offset(w * x, 0f),
            Offset(w * (x + 0.6f), size.height)
        )
        onDrawBehind { drawRect(brush) }
    }
}
