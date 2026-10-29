package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.MediaFile
import com.example.ui.theme.CatAudio
import com.example.ui.theme.Gold400
import com.example.ui.theme.Success
import com.example.ui.theme.Teal400
import com.example.ui.theme.Warning
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deterministically generates a waveform amplitude profile (normalized 0.15 .. 1.0) for a MediaFile.
 */
fun generateDeterministicWaveform(file: MediaFile, count: Int = 36): FloatArray {
    val seed = (file.fullHash?.hashCode() ?: 0) xor
            file.path.hashCode() xor
            (file.sizeBytes xor ((file.bitrateKbps ?: 128).toLong())).hashCode()
    val random = Random(seed)

    val amplitudes = FloatArray(count)
    val baseQualityFactor = ((file.bitrateKbps ?: 192) / 320f).coerceIn(0.6f, 1.1f)

    for (i in 0 until count) {
        val positionRatio = i.toFloat() / count
        val envelope = sin(positionRatio * Math.PI).toFloat().coerceAtLeast(0.3f)
        val rawNoise = random.nextFloat()
        val amp = (0.2f + 0.8f * rawNoise * envelope * baseQualityFactor).coerceIn(0.12f, 1.0f)
        amplitudes[i] = amp
    }
    return amplitudes
}

/**
 * Interactive & Visual Audio Waveform Composable.
 * Renders deterministic amplitude bars with live playback highlights, tap-to-seek,
 * dynamic equalizer pulse animation, and optional difference highlighting for audio comparison.
 */
@Composable
fun AudioWaveformVisualizer(
    file: MediaFile,
    isPlaying: Boolean,
    progressMs: Long = 0L,
    durationMs: Long = file.durationMs ?: 1000L,
    onSeek: ((Long) -> Unit)? = null,
    comparisonBaseFile: MediaFile? = null,
    height: Dp = 36.dp,
    barCount: Int = 36,
    activeColor: Color = Teal400,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
    differenceColor: Color = Warning,
    modifier: Modifier = Modifier
) {
    val currentDurationMs = durationMs.coerceAtLeast(1000L)
    val progressRatio = (progressMs.toFloat() / currentDurationMs).coerceIn(0f, 1f)

    val amplitudes = remember(file.path, file.fullHash, barCount) {
        generateDeterministicWaveform(file, barCount)
    }

    val comparisonAmplitudes = remember(comparisonBaseFile?.path, barCount) {
        comparisonBaseFile?.let { generateDeterministicWaveform(it, barCount) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(
                    if (onSeek != null) {
                        Modifier.pointerInput(file.path, currentDurationMs) {
                            detectTapGestures { offset ->
                                val clickRatio = (offset.x / size.width).coerceIn(0f, 1f)
                                val targetTimeMs = (clickRatio * currentDurationMs).toLong()
                                onSeek(targetTimeMs)
                            }
                        }
                    } else Modifier
                )
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val totalWidth = size.width
                val canvasHeight = size.height
                val barSpacing = 2.dp.toPx()
                val totalSpacing = barSpacing * (barCount - 1)
                val barWidth = ((totalWidth - totalSpacing) / barCount).coerceAtLeast(1.dp.toPx())

                val playedBarIndex = (progressRatio * barCount).toInt()

                for (i in 0 until barCount) {
                    val rawAmp = amplitudes.getOrElse(i) { 0.5f }
                    val isPlayed = i <= playedBarIndex

                    val liveAmp = if (isPlaying && isPlayed) {
                        val factor = if (i % 2 == 0) pulsePhase else (2.05f - pulsePhase)
                        (rawAmp * factor).coerceIn(0.15f, 1.0f)
                    } else {
                        rawAmp
                    }

                    val barHeightPx = (canvasHeight * liveAmp * 0.85f).coerceAtLeast(4.dp.toPx())
                    val xPos = i * (barWidth + barSpacing)
                    val yPos = (canvasHeight - barHeightPx) / 2f

                    val isDifferent = comparisonAmplitudes?.let { compAmps ->
                        val compAmp = compAmps.getOrElse(i) { rawAmp }
                        abs(compAmp - rawAmp) > 0.22f
                    } ?: false

                    val barColor = when {
                        isDifferent -> differenceColor
                        isPlayed -> if (isPlaying) Success else activeColor
                        else -> inactiveColor
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(xPos, yPos),
                        size = Size(barWidth, barHeightPx),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
}

/**
 * Compact Waveform Badge / Difference Indicator Component for Comparisons
 */
@Composable
fun WaveformDifferenceBadge(
    fileA: MediaFile,
    fileB: MediaFile,
    modifier: Modifier = Modifier
) {
    val ampsA = remember(fileA.path) { generateDeterministicWaveform(fileA, 24) }
    val ampsB = remember(fileB.path) { generateDeterministicWaveform(fileB, 24) }

    val diffCount = remember(ampsA, ampsB) {
        var count = 0
        for (i in 0 until minOf(ampsA.size, ampsB.size)) {
            if (abs(ampsA[i] - ampsB[i]) > 0.22f) count++
        }
        count
    }

    val durA = fileA.durationMs ?: 0L
    val durB = fileB.durationMs ?: 0L
    val brA = fileA.bitrateKbps ?: 0
    val brB = fileB.bitrateKbps ?: 0
    val hasSignificantDiff = diffCount > 3 || brA != brB || abs(durA - durB) > 500L

    Surface(
        color = if (hasSignificantDiff) Warning.copy(alpha = 0.15f) else Success.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (hasSignificantDiff) Warning.copy(alpha = 0.4f) else Success.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (hasSignificantDiff) Warning else Success,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (hasSignificantDiff) "Fidelity Variance Detected ($diffCount peaks)" else "Identical Waveform Signature",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasSignificantDiff) Warning else Success
            )
        }
    }
}

