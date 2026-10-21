package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioFile
import com.example.ui.theme.EmeraldKeep
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.PrimaryIndigo
import com.example.util.GeminiMusicService
import com.example.util.GeneratedMusicTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private val GENRE_PRESETS = listOf(
    "Lo-Fi Chill",
    "Cyberpunk Synthwave",
    "Acoustic Sunrise",
    "Deep Ambient",
    "8-Bit Chiptune",
    "Jazz Cafe"
)

private val PROMPT_SUGGESTIONS = listOf(
    "Mellow rain beats with vintage electric piano",
    "Neon highway drive with analog synth bass",
    "Morning acoustic guitar with warm fingerpicking",
    "Space ambient drone with celestial harmonics",
    "Retro arcade boss stage with 8-bit pulse",
    "Midnight coffee shop jazz with soft upright bass"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiMusicStudioScreen(
    onPlayTrack: (AudioFile) -> Unit,
    onTrackExportedToScanner: ((AudioFile) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicService = remember { GeminiMusicService(context) }

    var promptText by remember { mutableStateOf("Mellow rain beats with vintage electric piano") }
    var selectedGenre by remember { mutableStateOf("Lo-Fi Chill") }
    var selectedBpm by remember { mutableIntStateOf(90) }
    var durationSeconds by remember { mutableIntStateOf(15) }

    var isGenerating by remember { mutableStateOf(false) }
    var currentTrack by remember { mutableStateOf<GeneratedMusicTrack?>(null) }
    var generationError by remember { mutableStateOf<String?>(null) }
    var isSavedToLibrary by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var playProgressFraction by remember { mutableFloatStateOf(0f) }

    // Simulated playback progress
    LaunchedEffect(isPlaying, currentTrack) {
        if (isPlaying && currentTrack != null) {
            val totalMs = currentTrack!!.durationSeconds * 1000L
            val stepMs = 100L
            while (isPlaying && playProgressFraction < 1f) {
                delay(stepMs)
                playProgressFraction += stepMs.toFloat() / totalMs
            }
            if (playProgressFraction >= 1f) {
                isPlaying = false
                playProgressFraction = 0f
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1000.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Studio Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_studio_header_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.3f))
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                PrimaryIndigo.copy(alpha = 0.25f),
                                PrimaryCyan.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PrimaryIndigo),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Studio",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "AI Music Generation Studio",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Compose original audio tracks powered by Gemini AI",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Prompt Input & Suggestion Chips
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_music_prompt_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Prompt & Musical Vision",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = { Text("Describe the mood, instruments, rhythm or vibe...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_music_prompt_input"),
                    shape = RoundedCornerShape(14.dp),
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Quick Inspiration",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PROMPT_SUGGESTIONS.forEach { suggestion ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    promptText = suggestion
                                    if (suggestion.contains("cyberpunk", true)) selectedGenre = "Cyberpunk Synthwave"
                                    if (suggestion.contains("acoustic", true)) selectedGenre = "Acoustic Sunrise"
                                    if (suggestion.contains("ambient", true)) selectedGenre = "Deep Ambient"
                                    if (suggestion.contains("8-bit", true)) selectedGenre = "8-Bit Chiptune"
                                    if (suggestion.contains("jazz", true)) selectedGenre = "Jazz Cafe"
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = suggestion,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Genre Chips
                Text(
                    text = "Musical Style / Genre",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GENRE_PRESETS.forEach { genre ->
                        val isSelected = selectedGenre == genre
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedGenre = genre },
                            label = { Text(genre, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryIndigo.copy(alpha = 0.2f),
                                selectedLabelColor = PrimaryIndigo
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) PrimaryIndigo else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tempo (BPM) & Duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "BPM",
                                tint = PrimaryCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tempo: $selectedBpm BPM",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Slider(
                            value = selectedBpm.toFloat(),
                            onValueChange = { selectedBpm = it.toInt() },
                            valueRange = 70f..150f,
                            steps = 7,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryCyan,
                                activeTrackColor = PrimaryCyan
                            )
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Duration",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15, 30).forEach { dur ->
                                val isSelected = durationSeconds == dur
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { durationSeconds = dur },
                                    color = if (isSelected) PrimaryIndigo else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, if (isSelected) PrimaryIndigo else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = "${dur}s",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Generate Button
                Button(
                    onClick = {
                        isGenerating = true
                        generationError = null
                        isSavedToLibrary = false
                        isPlaying = false
                        playProgressFraction = 0f

                        scope.launch {
                            val result = musicService.generateTrack(
                                prompt = promptText,
                                genre = selectedGenre,
                                bpm = selectedBpm,
                                durationSeconds = durationSeconds
                            )

                            isGenerating = false
                            result.onSuccess { track ->
                                currentTrack = track
                                // Automatically play generated audio via AudioPlayerController
                                val audioFile = AudioFile(
                                    id = "ai_track_${System.currentTimeMillis()}",
                                    displayName = "${track.title}.wav",
                                    absolutePath = track.wavFile.absolutePath,
                                    uri = track.uri,
                                    sizeBytes = track.wavFile.length(),
                                    durationMs = track.durationSeconds * 1000L,
                                    title = track.title,
                                    artist = track.artist,
                                    album = "AI Studio Creations",
                                    genre = track.genre,
                                    bitrateKbps = 320,
                                    sampleRateHz = 44100,
                                    format = "WAV",
                                    dateModifiedMs = System.currentTimeMillis(),
                                    contentHash = "ai_hash_${UUID.randomUUID()}",
                                    isDemoFile = false
                                )
                                onPlayTrack(audioFile)
                                isPlaying = true
                            }.onFailure { err ->
                                generationError = err.message ?: "Failed to generate track"
                            }
                        }
                    },
                    enabled = !isGenerating && promptText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("ai_generate_music_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo
                    )
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Synthesizing audio track...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Generate",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate AI Audio Track", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        // Generated Audio Preview Card
        currentTrack?.let { track ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generated_music_player_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Track Title and Specs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = track.artist,
                                fontSize = 12.sp,
                                color = PrimaryCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Surface(
                            color = PrimaryIndigo.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "${track.bpm} BPM • ${track.musicalKey}",
                                color = PrimaryIndigo,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = track.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Animated Waveform Canvas Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        AiTrackWaveformVisualizer(
                            isPlaying = isPlaying,
                            progressFraction = playProgressFraction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Playback Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                isPlaying = !isPlaying
                                val audioFile = AudioFile(
                                    id = "ai_track_toggle",
                                    displayName = "${track.title}.wav",
                                    absolutePath = track.wavFile.absolutePath,
                                    uri = track.uri,
                                    sizeBytes = track.wavFile.length(),
                                    durationMs = track.durationSeconds * 1000L,
                                    title = track.title,
                                    artist = track.artist,
                                    album = "AI Studio Creations",
                                    genre = track.genre,
                                    bitrateKbps = 320,
                                    sampleRateHz = 44100,
                                    format = "WAV",
                                    dateModifiedMs = System.currentTimeMillis(),
                                    contentHash = "ai_hash_toggle",
                                    isDemoFile = false
                                )
                                onPlayTrack(audioFile)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PrimaryCyan)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black
                            )
                        }

                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            val currentSec = (playProgressFraction * track.durationSeconds).toInt()
                            Text(
                                text = String.format("%d:%02d / %d:%02d", currentSec / 60, currentSec % 60, track.durationSeconds / 60, track.durationSeconds % 60),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = playProgressFraction,
                                onValueChange = { playProgressFraction = it },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = PrimaryCyan,
                                    activeTrackColor = PrimaryCyan
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Actions Row: Export to Library & Duplicate Scanner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val savedUri = musicService.saveTrackToLibrary(track)
                                    if (savedUri != null) {
                                        isSavedToLibrary = true
                                        val newAudioFile = AudioFile(
                                            id = "exported_${System.currentTimeMillis()}",
                                            displayName = "${track.title}.wav",
                                            absolutePath = track.wavFile.absolutePath,
                                            uri = savedUri,
                                            sizeBytes = track.wavFile.length(),
                                            durationMs = track.durationSeconds * 1000L,
                                            title = track.title,
                                            artist = track.artist,
                                            album = "AI Studio Creations",
                                            genre = track.genre,
                                            bitrateKbps = 320,
                                            sampleRateHz = 44100,
                                            format = "WAV",
                                            dateModifiedMs = System.currentTimeMillis(),
                                            contentHash = "exported_ai_hash",
                                            isDemoFile = false
                                        )
                                        onTrackExportedToScanner?.invoke(newAudioFile)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSavedToLibrary) EmeraldKeep else PrimaryIndigo
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_ai_music_button")
                        ) {
                            Icon(
                                imageVector = if (isSavedToLibrary) Icons.Default.Check else Icons.Default.Download,
                                contentDescription = "Save",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSavedToLibrary) "Saved to Music" else "Save to Library",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun AiTrackWaveformVisualizer(
    isPlaying: Boolean,
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 6.28f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val barCount = 42
        val barWidth = size.width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f

        for (i in 0 until barCount) {
            val x = i * (barWidth + spacing)
            val relPos = i.toFloat() / barCount
            val baseAmp = Math.sin(relPos * Math.PI).toFloat().coerceAtLeast(0.2f)
            val dynamicAmp = if (isPlaying) {
                (baseAmp + Math.sin(relPos * 8.0 + phase).toFloat() * 0.25f).coerceIn(0.15f, 1.0f)
            } else {
                baseAmp
            }

            val barHeight = size.height * dynamicAmp
            val y = (size.height - barHeight) / 2f
            val isPassed = relPos <= progressFraction

            drawRoundRect(
                color = if (isPassed) PrimaryCyan else PrimaryIndigo.copy(alpha = 0.35f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
