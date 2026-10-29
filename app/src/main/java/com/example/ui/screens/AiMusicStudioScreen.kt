package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import kotlin.math.sin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.core.Formatters
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SafaiCard
import com.example.ui.theme.CatAudio
import com.example.ui.theme.Gold400
import com.example.ui.theme.HeroGradient
import com.example.ui.theme.Sizes
import com.example.ui.theme.Spacing
import com.example.ui.theme.Success
import com.example.ui.theme.Teal400
import com.example.ui.theme.Violet400
import com.example.util.AudioSynthesizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class SynthesizedTrack(
    val title: String,
    val artist: String,
    val genre: String,
    val description: String,
    val bpm: Int,
    val musicalKey: String,
    val wavFile: File,
    val uri: Uri,
    val durationSeconds: Int,
    var isSavedToDevice: Boolean = false
)

private val GENRE_OPTIONS = listOf(
    "Lo-Fi Chill",
    "Cyberpunk Synthwave",
    "Acoustic Sunrise",
    "Deep Ambient",
    "8-Bit Chiptune",
    "Jazz Cafe"
)

private val PROMPT_SUGGESTIONS = listOf(
    "Calm rainy night lo-fi beat with mellow piano and vinyl crackle",
    "Energetic retro synthwave with pulsing bass and driving drums",
    "Peaceful acoustic sunrise guitar chords with warm ambient atmosphere",
    "Deep atmospheric cosmic meditation with lush synthesizer pads",
    "Playful nostalgic 8-bit arcade chiptune melody with square waves",
    "Smooth late-night jazz cafe with walking bass and electric piano"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiMusicStudioScreen(
    onNavigateBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var promptText by remember { mutableStateOf(PROMPT_SUGGESTIONS[0]) }
    var selectedGenre by remember { mutableStateOf(GENRE_OPTIONS[0]) }
    var bpm by remember { mutableIntStateOf(100) }
    var durationSeconds by remember { mutableIntStateOf(20) }

    var isGenerating by remember { mutableStateOf(false) }
    var generatedTrack by remember { mutableStateOf<SynthesizedTrack?>(null) }

    // Media3 ExoPlayer for playback
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    isPlaying = false
                    playbackPositionMs = 0L
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Playback progress ticker
    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying) {
            playbackPositionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(150)
        }
    }

    fun startGeneration() {
        if (isGenerating) return
        isGenerating = true
        exoPlayer.stop()
        isPlaying = false

        coroutineScope.launch {
            try {
                val adjectives = listOf("Midnight", "Cosmic", "Neon", "Velvet", "Golden", "Subtle", "Electric", "Atmospheric")
                val nouns = listOf("Echoes", "Pulse", "Drift", "Horizon", "Symphony", "Groove", "Waves", "Serenade")
                val trackTitle = "${adjectives.random()} ${nouns.random()}"
                val key = when (selectedGenre) {
                    "Cyberpunk Synthwave" -> "A Minor"
                    "Acoustic Sunrise" -> "G Major"
                    "8-Bit Chiptune" -> "C Major"
                    "Jazz Cafe" -> "D Minor"
                    else -> "C Major"
                }

                val wavFile = AudioSynthesizer.generateMusicWav(
                    context = context,
                    fileName = "${trackTitle.lowercase().replace(" ", "_")}_${System.currentTimeMillis() % 10000}",
                    durationSeconds = durationSeconds,
                    bpm = bpm,
                    genre = selectedGenre,
                    musicalKey = key
                )

                val track = SynthesizedTrack(
                    title = trackTitle,
                    artist = "Safai Studio",
                    genre = selectedGenre,
                    description = "Procedurally composed $selectedGenre track with harmonic chords, analog bass, and rhythm drums ($bpm BPM in $key).",
                    bpm = bpm,
                    musicalKey = key,
                    wavFile = wavFile,
                    uri = Uri.fromFile(wavFile),
                    durationSeconds = durationSeconds
                )

                generatedTrack = track

                // Load track into ExoPlayer
                exoPlayer.setMediaItem(MediaItem.fromUri(track.uri))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true

                onShowSnackbar("Track synthesized: $trackTitle ($selectedGenre)")
            } catch (e: Exception) {
                onShowSnackbar("Failed to synthesize: ${e.message}")
            } finally {
                isGenerating = false
            }
        }
    }

    fun saveToDeviceLibrary(track: SynthesizedTrack) {
        coroutineScope.launch {
            try {
                val savedUri = AudioSynthesizer.exportToDeviceMusicFolder(
                    context = context,
                    wavFile = track.wavFile,
                    title = track.title,
                    artist = track.artist
                )
                if (savedUri != null) {
                    track.isSavedToDevice = true
                    onShowSnackbar("Saved '${track.title}.wav' to device Music folder!")
                } else {
                    onShowSnackbar("Could not export to Music folder")
                }
            } catch (e: Exception) {
                onShowSnackbar("Error saving: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = CatAudio
                        )
                        Text(
                            text = "AI Music Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Hero Studio Header Card
            item {
                SafaiCard(brush = HeroGradient) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(CatAudio.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = CatAudio,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Synthesize Original Music",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "100% on-device mathematical audio synthesis. Create, preview, and export lossless WAV tracks directly to your library.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Prompt Input Card
            item {
                SafaiCard(tonal = true) {
                    Text(
                        text = "Musical Vision & Theme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 84.dp),
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("Describe mood, instruments, rhythm, or atmospheric vibe...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )

                    Spacer(Modifier.height(Spacing.sm))

                    Text(
                        text = "Quick Inspiration Presets",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.xs))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        PROMPT_SUGGESTIONS.forEachIndexed { index, prompt ->
                            val genre = GENRE_OPTIONS.getOrElse(index) { "Lo-Fi" }
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        promptText = prompt
                                        selectedGenre = genre
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Style & Audio Parameters Card
            item {
                SafaiCard(tonal = true) {
                    Text(
                        text = "Style & Synthesizer Controls",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(Spacing.sm))

                    // Genre Selector Chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        GENRE_OPTIONS.forEach { genre ->
                            val isSelected = selectedGenre == genre
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedGenre = genre },
                                label = { Text(genre) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.md))

                    // BPM Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, tint = CatAudio, modifier = Modifier.size(18.dp))
                            Text(text = "Tempo: $bpm BPM", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = when {
                                bpm < 85 -> "Slow / Laid-back"
                                bpm < 115 -> "Moderate Groove"
                                else -> "High Energy"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Slider(
                        value = bpm.toFloat(),
                        onValueChange = { bpm = it.toInt() },
                        valueRange = 60f..160f,
                        steps = 20,
                        colors = SliderDefaults.colors(
                            thumbColor = CatAudio,
                            activeTrackColor = CatAudio
                        )
                    )

                    Spacer(Modifier.height(Spacing.xs))

                    // Duration Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Duration", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            listOf(15, 20, 30, 45).forEach { dur ->
                                val selected = durationSeconds == dur
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { durationSeconds = dur },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) CatAudio.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, if (selected) CatAudio else MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = "${dur}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) CatAudio else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.md))

                    // Synthesize Action Button
                    PrimaryButton(
                        text = if (isGenerating) "Synthesizing Audio..." else "Compose & Synthesize Audio",
                        onClick = { startGeneration() },
                        enabled = !isGenerating,
                        icon = Icons.Rounded.AutoAwesome,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Generated Track Preview Card
            if (generatedTrack != null) {
                val track = generatedTrack!!
                item {
                    SafaiCard(
                        modifier = Modifier.fillMaxWidth(),
                        tonal = true
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Teal400.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = Teal400,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${track.genre} · ${track.bpm} BPM · Key of ${track.musicalKey}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (track.isSavedToDevice) {
                                Surface(
                                    color = Success.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(12.dp))
                                        Text("Saved", style = MaterialTheme.typography.labelSmall, color = Success, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(Spacing.sm))

                        Text(
                            text = track.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(Spacing.md))

                        // Waveform Visualizer
                        InteractiveSynthWaveform(
                            isPlaying = isPlaying,
                            progressRatio = if (durationMs > 0) (playbackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                            onSeek = { frac ->
                                val target = (frac * durationMs).toLong()
                                exoPlayer.seekTo(target)
                            }
                        )

                        Spacer(Modifier.height(Spacing.sm))

                        // Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPlaying) exoPlayer.pause()
                                    else exoPlayer.play()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Teal400)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.Black
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                val posStr = Formatters.formatDuration(playbackPositionMs)
                                val durStr = Formatters.formatDuration(durationMs.coerceAtLeast(track.durationSeconds * 1000L))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(posStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(durStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            OutlinedButton(
                                onClick = { saveToDeviceLibrary(track) },
                                shape = RoundedCornerShape(12.dp),
                                enabled = !track.isSavedToDevice
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save to Library", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

@Composable
private fun InteractiveSynthWaveform(
    isPlaying: Boolean,
    progressRatio: Float,
    onSeek: (Float) -> Unit,
    barCount: Int = 36,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "synth_wave")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "synth_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable { /* tap to seek can be enhanced with gestures */ }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val totalWidth = size.width
            val canvasHeight = size.height
            val spacing = 2.dp.toPx()
            val barWidth = ((totalWidth - (spacing * (barCount - 1))) / barCount).coerceAtLeast(1.dp.toPx())
            val playedIndex = (progressRatio * barCount).toInt()

            for (i in 0 until barCount) {
                val ratio = i.toFloat() / barCount
                val baseAmp = (0.25f + 0.75f * sin(ratio * Math.PI)).toFloat()
                val liveAmp = if (isPlaying && i <= playedIndex) {
                    val factor = if (i % 2 == 0) pulse else (2.1f - pulse)
                    (baseAmp * factor).coerceIn(0.15f, 1f)
                } else baseAmp

                val h = (canvasHeight * liveAmp * 0.85f).coerceAtLeast(4.dp.toPx())
                val x = i * (barWidth + spacing)
                val y = (canvasHeight - h) / 2f
                val color = if (i <= playedIndex) Teal400 else Color(0xFF263241)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}

