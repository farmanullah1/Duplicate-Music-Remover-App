package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioFile
import com.example.ui.theme.EmeraldKeep
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.PrimaryIndigo
import kotlin.random.Random

/**
 * Deterministically generates a waveform amplitude profile (normalized 0.15 .. 1.0) for an AudioFile.
 */
fun generateDeterministicWaveform(audioFile: AudioFile, count: Int = 36): FloatArray {
    val seed = (audioFile.contentHash.hashCode() xor audioFile.id.hashCode() xor (audioFile.sizeBytes xor audioFile.bitrateKbps.toLong()).hashCode())
    val random = Random(seed)

    val amplitudes = FloatArray(count)
    val baseQualityFactor = (audioFile.bitrateKbps / 320f).coerceIn(0.6f, 1.1f)

    for (i in 0 until count) {
        // Build smooth envelope curve (fade in, peak dynamics, fade out)
        val positionRatio = i.toFloat() / count
        val envelope = Math.sin(positionRatio * Math.PI).toFloat().coerceAtLeast(0.3f)

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
    audioFile: AudioFile,
    isPlaying: Boolean,
    progressMs: Long = 0L,
    durationMs: Long = audioFile.durationMs,
    onSeek: ((Long) -> Unit)? = null,
    comparisonBaseFile: AudioFile? = null,
    height: Dp = 36.dp,
    barCount: Int = 36,
    activeColor: Color = PrimaryCyan,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
    differenceColor: Color = Color(0xFFFF9800), // Amber highlight for audio profile variance
    modifier: Modifier = Modifier
) {
    val currentDurationMs = durationMs.coerceAtLeast(1000L)
    val progressRatio = (progressMs.toFloat() / currentDurationMs).coerceIn(0f, 1f)

    val amplitudes = remember(audioFile.id, audioFile.contentHash, barCount) {
        generateDeterministicWaveform(audioFile, barCount)
    }

    val comparisonAmplitudes = remember(comparisonBaseFile?.id, barCount) {
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("waveform_visualizer_${audioFile.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(
                    if (onSeek != null) {
                        Modifier.pointerInput(audioFile.id, currentDurationMs) {
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

                    // Apply animated pulse to played/active bars when audio is actively playing
                    val liveAmp = if (isPlaying && isPlayed) {
                        val factor = if (i % 2 == 0) pulsePhase else (2.05f - pulsePhase)
                        (rawAmp * factor).coerceIn(0.15f, 1.0f)
                    } else {
                        rawAmp
                    }

                    // Height calculation centered vertically
                    val barHeightPx = (canvasHeight * liveAmp * 0.85f).coerceAtLeast(4.dp.toPx())
                    val xPos = i * (barWidth + barSpacing)
                    val yPos = (canvasHeight - barHeightPx) / 2f

                    // Check if difference with comparison file
                    val isDifferent = comparisonAmplitudes?.let { compAmps ->
                        val compAmp = compAmps.getOrElse(i) { rawAmp }
                        kotlin.math.abs(compAmp - rawAmp) > 0.22f
                    } ?: false

                    val barColor = when {
                        isDifferent -> differenceColor
                        isPlayed -> if (isPlaying) EmeraldKeep else activeColor
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
    fileA: AudioFile,
    fileB: AudioFile,
    modifier: Modifier = Modifier
) {
    val ampsA = remember(fileA.id) { generateDeterministicWaveform(fileA, 24) }
    val ampsB = remember(fileB.id) { generateDeterministicWaveform(fileB, 24) }

    val diffCount = remember(ampsA, ampsB) {
        var count = 0
        for (i in 0 until minOf(ampsA.size, ampsB.size)) {
            if (kotlin.math.abs(ampsA[i] - ampsB[i]) > 0.22f) count++
        }
        count
    }

    val hasSignificantDiff = diffCount > 3 || fileA.bitrateKbps != fileB.bitrateKbps || kotlin.math.abs(fileA.durationMs - fileB.durationMs) > 500L

    Surface(
        color = if (hasSignificantDiff) Color(0xFFFF9800).copy(alpha = 0.15f) else EmeraldKeep.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
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
                        color = if (hasSignificantDiff) Color(0xFFFF9800) else EmeraldKeep,
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (hasSignificantDiff) "Waveform Variance Detected ($diffCount peaks)" else "Identical Waveform Signature",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasSignificantDiff) Color(0xFFFF9800) else EmeraldKeep
            )
        }
    }
}
