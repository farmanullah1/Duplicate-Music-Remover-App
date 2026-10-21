package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.PlayerState
import com.example.model.AudioFile
import com.example.model.DuplicateGroup
import com.example.ui.theme.CrimsonDelete
import com.example.ui.theme.EmeraldKeep
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.PrimaryIndigo

@Composable
fun SideBySideCompareDialog(
    group: DuplicateGroup,
    playerState: PlayerState,
    onTogglePlay: (AudioFile) -> Unit,
    onPlayCompareDual: ((AudioFile, AudioFile, AudioFile) -> Unit)? = null,
    onPlayGroupSynchronized: ((List<AudioFile>, AudioFile) -> Unit)? = null,
    onSeek: (Long) -> Unit,
    onDeleteFile: (AudioFile, Boolean) -> Unit,
    onDeleteBatchFiles: ((List<AudioFile>, Boolean) -> Unit)? = null,
    onKeepBoth: () -> Unit,
    onEditMetadata: ((AudioFile) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val files = group.files
    if (files.isEmpty()) return

    val autoRecommendedKeepId = group.autoRecommendedKeepId ?: files.first().id
    var keepFileId by remember(group.groupId, files.map { it.id }) { mutableStateOf(autoRecommendedKeepId) }
    var pendingDeleteFiles by remember { mutableStateOf<List<AudioFile>?>(null) }
    var pendingSingleDeleteFile by remember { mutableStateOf<AudioFile?>(null) }
    var fileForEdit by remember { mutableStateOf<AudioFile?>(null) }

    fileForEdit?.let { file ->
        MetadataEditorDialog(
            audioFile = file,
            onSave = { updated ->
                onEditMetadata?.invoke(updated)
                fileForEdit = null
            },
            onDismiss = { fileForEdit = null }
        )
    }

    val filesForRemoval = remember(keepFileId, files) {
        files.filter { it.id != keepFileId }
    }

    val activePlayingFileId = playerState.playingFileId
    val isPlaying = playerState.isPlaying

    pendingSingleDeleteFile?.let { targetFile ->
        DeleteConfirmationDialog(
            audioFile = targetFile,
            onMoveToRecycleBin = {
                onDeleteFile(targetFile, false)
                pendingSingleDeleteFile = null
            },
            onDeletePermanently = {
                onDeleteFile(targetFile, true)
                pendingSingleDeleteFile = null
            },
            onDismiss = { pendingSingleDeleteFile = null }
        )
    }

    pendingDeleteFiles?.let { targetFiles ->
        BatchDeleteConfirmationDialog(
            audioFiles = targetFiles,
            onMoveToRecycleBin = {
                if (onDeleteBatchFiles != null) {
                    onDeleteBatchFiles(targetFiles, false)
                } else {
                    targetFiles.forEach { onDeleteFile(it, false) }
                }
                pendingDeleteFiles = null
            },
            onDeletePermanently = {
                if (onDeleteBatchFiles != null) {
                    onDeleteBatchFiles(targetFiles, true)
                } else {
                    targetFiles.forEach { onDeleteFile(it, true) }
                }
                pendingDeleteFiles = null
            },
            onDismiss = { pendingDeleteFiles = null }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 940.dp)
                .fillMaxHeight(0.93f)
                .clip(RoundedCornerShape(24.dp))
                .border(androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(24.dp))
                .testTag("side_by_side_dialog"),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Side-by-Side Comparison",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = PrimaryIndigo.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${group.similarityScorePercentage}% Match • ${files.size} Files",
                                    color = PrimaryIndigo,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = group.groupTitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_compare_dialog")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Scrollable Content Pane
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Match Type Banner & Waveform Difference Badge
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Match Reason",
                                    tint = PrimaryCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = group.matchType.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = group.matchType.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (files.size >= 2) {
                                Spacer(modifier = Modifier.height(8.dp))
                                WaveformDifferenceBadge(fileA = files[0], fileB = files[1])
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Synchronized Audio Preview Player Center
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("synchronized_audio_player_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            val activePlayingFile = files.find { it.id == activePlayingFileId }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Synchronized Audio",
                                        tint = PrimaryIndigo,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Synchronized Multi-Track Comparison",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                val channelText = when {
                                    !isPlaying -> "Paused"
                                    activePlayingFile != null -> "Playing: ${activePlayingFile.displayName.take(16)}..."
                                    else -> "Ready"
                                }

                                Surface(
                                    color = if (isPlaying) EmeraldKeep.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = channelText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPlaying) EmeraldKeep else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Multi-Channel Selector Buttons (Scrollable Row for N files)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                files.forEachIndexed { index, file ->
                                    val isThisFilePlaying = isPlaying && activePlayingFileId == file.id
                                    val fileLabel = "File ${index + 1}"

                                    Button(
                                        onClick = {
                                            if (onPlayGroupSynchronized != null) {
                                                onPlayGroupSynchronized(files, file)
                                            } else if (onPlayCompareDual != null && files.size >= 2) {
                                                val fileB = files.getOrNull(if (index == 0) 1 else 0) ?: file
                                                onPlayCompareDual(file, fileB, file)
                                            } else {
                                                onTogglePlay(file)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isThisFilePlaying) EmeraldKeep else PrimaryIndigo
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("switch_channel_${index}_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isThisFilePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "$fileLabel Channel",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isThisFilePlaying) "Playing $fileLabel" else "Listen $fileLabel (${file.bitrateKbps}k)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Active Track Waveform Visualizer & Timeline Scrubber Slider
                            if ((isPlaying || playerState.progressMs > 0) && activePlayingFile != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Column {
                                    AudioWaveformVisualizer(
                                        audioFile = activePlayingFile,
                                        isPlaying = isPlaying,
                                        progressMs = playerState.progressMs,
                                        durationMs = playerState.durationMs,
                                        onSeek = onSeek,
                                        height = 30.dp,
                                        barCount = 38
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = formatMs(playerState.progressMs),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatMs(playerState.durationMs),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Slider(
                                        value = playerState.progressMs.toFloat(),
                                        onValueChange = { onSeek(it.toLong()) },
                                        valueRange = 0f..(playerState.durationMs.coerceAtLeast(1000L).toFloat()),
                                        colors = SliderDefaults.colors(
                                            thumbColor = PrimaryIndigo,
                                            activeTrackColor = PrimaryIndigo
                                        ),
                                        modifier = Modifier.height(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Side-by-Side File Cards Layout (Adaptive / Horizontally Scrollable for N files)
                    if (files.size == 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            files.forEachIndexed { index, file ->
                                val otherFile = files.getOrNull(if (index == 0) 1 else 0)
                                FilePaneCard(
                                    audioFile = file,
                                    paneLabel = "FILE ${index + 1}",
                                    isKeep = file.id == keepFileId,
                                    playerState = playerState,
                                    comparisonBaseFile = otherFile,
                                    onSeek = onSeek,
                                    onSelectKeep = { keepFileId = file.id },
                                    onEditTags = { fileForEdit = file },
                                    onTogglePlay = {
                                        if (onPlayGroupSynchronized != null) {
                                            onPlayGroupSynchronized(files, file)
                                        } else if (onPlayCompareDual != null) {
                                            val fileB = files.find { it.id != file.id } ?: file
                                            onPlayCompareDual(file, fileB, file)
                                        } else {
                                            onTogglePlay(file)
                                        }
                                    },
                                    onDelete = { pendingSingleDeleteFile = file },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        // Multi-file horizontal scroll layout (3 up to 10 files)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            files.forEachIndexed { index, file ->
                                val otherFile = files.find { it.id != file.id }
                                FilePaneCard(
                                    audioFile = file,
                                    paneLabel = "FILE ${index + 1}",
                                    isKeep = file.id == keepFileId,
                                    playerState = playerState,
                                    comparisonBaseFile = otherFile,
                                    onSeek = onSeek,
                                    onSelectKeep = { keepFileId = file.id },
                                    onEditTags = { fileForEdit = file },
                                    onTogglePlay = {
                                        if (onPlayGroupSynchronized != null) {
                                            onPlayGroupSynchronized(files, file)
                                        } else {
                                            onTogglePlay(file)
                                        }
                                    },
                                    onDelete = { pendingSingleDeleteFile = file },
                                    modifier = Modifier.width(260.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Technical Parameter Comparison Matrix Across All Files
                    Text(
                        text = "Technical Parameter Matrix (${files.size} Options)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    MultiFileComparisonMatrix(files = files)
                }

                // 1-Tap Pick Best Audio Fidelity Preset Button
                val bestAudioFile = remember(files) { files.maxByOrNull { it.bitrateKbps } }
                if (bestAudioFile != null && files.size >= 2) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { keepFileId = bestAudioFile.id },
                        color = PrimaryCyan.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = null,
                                    tint = PrimaryCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Auto-Pick Highest Audio Quality",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = PrimaryCyan
                                    )
                                    Text(
                                        text = "Selects '${bestAudioFile.displayName}' (${bestAudioFile.bitrateKbps} kbps • ${bestAudioFile.format})",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (keepFileId == bestAudioFile.id) {
                                Surface(
                                    color = EmeraldKeep,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "SELECTED",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Bottom Action Buttons: Keep All / Delete Selected Duplicates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onKeepBoth,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryIndigo),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("keep_both_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Keep All",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (files.size > 2) "Keep All (${files.size} Files)" else "Keep Both (False Duplicate)",
                            fontSize = 11.sp,
                            color = PrimaryIndigo,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = { pendingDeleteFiles = filesForRemoval },
                        enabled = filesForRemoval.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonDelete,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("auto_delete_duplicate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Duplicates",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Delete ${filesForRemoval.size} Duplicate${if (filesForRemoval.size > 1) "s" else ""}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilePaneCard(
    audioFile: AudioFile,
    paneLabel: String,
    isKeep: Boolean,
    playerState: PlayerState,
    comparisonBaseFile: AudioFile? = null,
    onSeek: ((Long) -> Unit)? = null,
    onSelectKeep: () -> Unit,
    onEditTags: (() -> Unit)? = null,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying = playerState.playingFileId == audioFile.id && playerState.isPlaying

    Card(
        modifier = modifier.testTag("file_pane_${audioFile.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isKeep) {
                EmeraldKeep.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isKeep) 2.dp else 1.dp,
            color = if (isKeep) EmeraldKeep else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Badge & Pane Label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = paneLabel,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onEditTags != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onEditTags,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("edit_tags_pane_${audioFile.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Tags",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (isKeep) {
                    Surface(
                        color = EmeraldKeep,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "KEEP",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                } else {
                    Surface(
                        color = CrimsonDelete.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "REMOVE",
                            color = CrimsonDelete,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Keep Selection Radio / Toggle Button
            Surface(
                onClick = onSelectKeep,
                shape = RoundedCornerShape(10.dp),
                color = if (isKeep) EmeraldKeep.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isKeep) EmeraldKeep else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isKeep) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Select to keep",
                        tint = if (isKeep) EmeraldKeep else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isKeep) "Selected to Keep" else "Keep This File",
                        fontSize = 11.sp,
                        fontWeight = if (isKeep) FontWeight.Bold else FontWeight.Medium,
                        color = if (isKeep) EmeraldKeep else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = audioFile.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${audioFile.format} • ${audioFile.bitrateKbps} kbps • ${audioFile.formattedSize}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryIndigo,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            Text(
                text = audioFile.absolutePath,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Visual Waveform Profile with Comparison Variance Highlighting
            Text(
                text = "Audio Waveform Signature:",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            AudioWaveformVisualizer(
                audioFile = audioFile,
                isPlaying = isPlaying,
                progressMs = if (isPlaying) playerState.progressMs else 0L,
                durationMs = audioFile.durationMs,
                onSeek = onSeek,
                comparisonBaseFile = comparisonBaseFile,
                height = 28.dp,
                barCount = 28
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Embedded Audio Player Button
            Button(
                onClick = onTogglePlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) EmeraldKeep else PrimaryIndigo
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Preview Track",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isPlaying) "Playing..." else "Preview",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Delete Individual File
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonDelete),
                border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonDelete.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete File",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete $paneLabel", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MultiFileComparisonMatrix(files: List<AudioFile>) {
    val maxBitrate = files.maxOfOrNull { it.bitrateKbps } ?: 0
    val maxSizeBytes = files.maxOfOrNull { it.sizeBytes } ?: 0L
    val maxSampleRate = files.maxOfOrNull { it.sampleRateHz } ?: 0
    val maxDuration = files.maxOfOrNull { it.durationMs } ?: 0L

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp)
    ) {
        // Table Header: File 1, File 2, ... File N
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Metric",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(80.dp)
            )

            files.forEachIndexed { index, _ ->
                Text(
                    text = "File ${index + 1}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(90.dp)
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Bitrate Row
        MatrixMetricRow(
            label = "Bitrate",
            files = files,
            getValue = { "${it.bitrateKbps} kbps" },
            isBest = { it.bitrateKbps == maxBitrate && maxBitrate > 0 },
            scrollState = scrollState
        )

        // File Size Row
        MatrixMetricRow(
            label = "File Size",
            files = files,
            getValue = { it.formattedSize },
            isBest = { it.sizeBytes == maxSizeBytes && maxSizeBytes > 0L },
            scrollState = scrollState
        )

        // Sample Rate Row
        MatrixMetricRow(
            label = "Sample Rate",
            files = files,
            getValue = { "${it.sampleRateHz / 1000} kHz" },
            isBest = { it.sampleRateHz == maxSampleRate && maxSampleRate > 0 },
            scrollState = scrollState
        )

        // Duration Row
        MatrixMetricRow(
            label = "Duration",
            files = files,
            getValue = { it.formattedDuration },
            isBest = { kotlin.math.abs(it.durationMs - maxDuration) < 1000L },
            scrollState = scrollState
        )

        // Format Row
        MatrixMetricRow(
            label = "Format",
            files = files,
            getValue = { it.format },
            isBest = { it.format == "FLAC" || it.format == "WAV" },
            scrollState = scrollState
        )
    }
}

@Composable
private fun MatrixMetricRow(
    label: String,
    files: List<AudioFile>,
    getValue: (AudioFile) -> String,
    isBest: (AudioFile) -> Boolean,
    scrollState: androidx.compose.foundation.ScrollState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )

        files.forEach { file ->
            val best = isBest(file)
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (best) EmeraldKeep.copy(alpha = 0.15f) else Color.Transparent)
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getValue(file),
                    fontWeight = if (best) FontWeight.Bold else FontWeight.Normal,
                    color = if (best) EmeraldKeep else MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
