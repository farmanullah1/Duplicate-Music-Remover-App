package com.example.ui.duplicates

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.Formatters
import com.example.domain.model.DuplicateGroupUi
import com.example.domain.model.MatchType
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import com.example.domain.model.MemberUi
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DuplicateGroupCard(
    group: DuplicateGroupUi,
    fileKeepOverrides: Map<String, Boolean>,
    onToggleKeep: (String) -> Unit,
    onCompare: () -> Unit,
    onNotDuplicate: () -> Unit,
    onCleanGroup: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectStrategy: ((KeepStrategy) -> Unit)? = null,
    onEditTags: ((MediaFile) -> Unit)? = null,
    currentlyPlayingPath: String? = null,
    onTogglePlayAudio: ((MediaFile) -> Unit)? = null,
    playingProgressMs: Long = 0L
) {
    val keeperMember = group.files.find { it.recommendedKeep } ?: group.files.first()

    SafaiCard(
        modifier = modifier.fillMaxWidth(),
        tonal = true
    ) {
        // Group Header
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
                CategoryBadge(group.category)
                Column {
                    Text(
                        text = keeperMember.file.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        ConfidenceBadge(group.score)
                        Text(
                            text = when (group.matchType) {
                                MatchType.EXACT_CONTENT -> "Identical content (SHA-256)"
                                MatchType.SIMILAR_IMAGE -> "Similar photo ${(group.score * 100).toInt()}%"
                                MatchType.SAME_AUDIO_TAGS -> "Same song tags"
                                MatchType.NAME_VARIATION -> "Name variation"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Formatters.formatBytes(group.reclaimableBytes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (onSelectStrategy != null) {
                    var showStrategyMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showStrategyMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Selection helpers")
                        }
                        DropdownMenu(
                            expanded = showStrategyMenu,
                            onDismissRequest = { showStrategyMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Keep newest") },
                                onClick = {
                                    showStrategyMenu = false
                                    onSelectStrategy(KeepStrategy.NEWEST)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Keep oldest") },
                                onClick = {
                                    showStrategyMenu = false
                                    onSelectStrategy(KeepStrategy.OLDEST)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Keep largest") },
                                onClick = {
                                    showStrategyMenu = false
                                    onSelectStrategy(KeepStrategy.LARGEST)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Keep in priority folder") },
                                onClick = {
                                    showStrategyMenu = false
                                    onSelectStrategy(KeepStrategy.PRIORITY_FOLDER)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Audio comparison badge if comparing multiple audio tracks
        if (group.category == MediaCategory.AUDIO && group.files.size >= 2) {
            Spacer(Modifier.height(Spacing.xs))
            val fileA = group.files[0].file
            val fileB = group.files[1].file
            WaveformDifferenceBadge(fileA = fileA, fileB = fileB)
        }

        Spacer(Modifier.height(Spacing.md))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Spacing.xs))

        // File Rows
        group.files.forEach { member ->
            val isKeep = fileKeepOverrides[member.file.path] ?: member.recommendedKeep
            val isAudioPlaying = currentlyPlayingPath == member.file.path

            DuplicateMemberRow(
                member = member,
                isKeep = isKeep,
                onToggle = { onToggleKeep(member.file.path) },
                onEditTags = onEditTags,
                isPlayingAudio = isAudioPlaying,
                onTogglePlayAudio = { onTogglePlayAudio?.invoke(member.file) },
                playingProgressMs = if (isAudioPlaying) playingProgressMs else 0L
            )
            Spacer(Modifier.height(Spacing.xs))
        }

        Spacer(Modifier.height(Spacing.xs))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Spacing.sm))

        // Footer Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedButton(
                    onClick = onCompare,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Compare,
                        contentDescription = null,
                        modifier = Modifier.size(Sizes.iconSm)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.dup_compare), style = MaterialTheme.typography.labelSmall)
                }

                TextButton(
                    onClick = onNotDuplicate,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.dup_not_duplicate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = onCleanGroup,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.dup_clean_group), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DuplicateMemberRow(
    member: MemberUi,
    isKeep: Boolean,
    onToggle: () -> Unit,
    onEditTags: ((MediaFile) -> Unit)? = null,
    isPlayingAudio: Boolean = false,
    onTogglePlayAudio: (() -> Unit)? = null,
    playingProgressMs: Long = 0L
) {
    val file = member.file
    val context = androidx.compose.ui.platform.LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isKeep) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (isKeep) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onToggle,
                onLongClick = { showContextMenu = true }
            )
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                ThumbnailImage(file = file, size = Sizes.thumb)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = file.folder,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val metaDetail = when (file.category) {
                        MediaCategory.AUDIO -> {
                            val br = file.bitrateKbps?.let { "$it kbps · " } ?: ""
                            val dur = file.durationMs?.let { Formatters.formatDuration(it) + " · " } ?: ""
                            "$br$dur${Formatters.formatBytes(file.sizeBytes)}"
                        }
                        MediaCategory.IMAGE -> {
                            val res = if ((file.width ?: 0) > 0) "${file.width}×${file.height} · " else ""
                            "$res${Formatters.formatBytes(file.sizeBytes)}"
                        }
                        MediaCategory.VIDEO -> {
                            val res = if ((file.width ?: 0) > 0) "${file.width}×${file.height} · " else ""
                            val dur = file.durationMs?.let { Formatters.formatDuration(it) + " · " } ?: ""
                            "$res$dur${Formatters.formatBytes(file.sizeBytes)}"
                        }
                        else -> {
                            "${Formatters.formatDate(file.modifiedMs)} · ${Formatters.formatBytes(file.sizeBytes)}"
                        }
                    }
                    Text(
                        text = metaDetail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Audio quick play audition button
                if (file.category == MediaCategory.AUDIO && onTogglePlayAudio != null) {
                    IconButton(
                        onClick = onTogglePlayAudio,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isPlayingAudio) Teal400 else MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Icon(
                            imageVector = if (isPlayingAudio) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlayingAudio) "Pause" else "Audition track",
                            tint = if (isPlayingAudio) Color.Black else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // KEEP / REMOVE Toggle Button
                KeepRemoveToggle(
                    isKeep = isKeep,
                    onToggle = onToggle
                )
            }

            // Audio Waveform Visualizer
            if (file.category == MediaCategory.AUDIO) {
                Spacer(Modifier.height(4.dp))
                AudioWaveformVisualizer(
                    file = file,
                    isPlaying = isPlayingAudio,
                    progressMs = playingProgressMs,
                    durationMs = file.durationMs ?: 1000L,
                    height = 24.dp,
                    barCount = 30
                )
            }

            if (isKeep && !member.reason.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Gold400,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Recommended: ${member.reason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gold400,
                        fontSize = 10.sp
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Open") },
                leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                onClick = {
                    showContextMenu = false
                    try {
                        val f = java.io.File(file.path)
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                        val mime = file.mime ?: "*/*"
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Open with"))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Cannot open file", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )

            if (file.category == MediaCategory.AUDIO && onEditTags != null) {
                DropdownMenuItem(
                    text = { Text("Edit audio tags") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        showContextMenu = false
                        onEditTags(file)
                    }
                )
            }

            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                onClick = {
                    showContextMenu = false
                    try {
                        val f = java.io.File(file.path)
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                        val mime = file.mime ?: "*/*"
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share file"))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Cannot share file", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )

            DropdownMenuItem(
                text = { Text("Copy path") },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                onClick = {
                    showContextMenu = false
                    val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("File path", file.path))
                    android.widget.Toast.makeText(context, "Path copied", android.widget.Toast.LENGTH_SHORT).show()
                }
            )

            DropdownMenuItem(
                text = { Text("File details") },
                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                onClick = {
                    showContextMenu = false
                    showDetailsDialog = true
                }
            )
        }
    }

    if (showDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = {
                Text("File Details", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text("Name: ${file.name}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text("Folder: ${file.folder}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Full Path: ${file.path}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Size: ${Formatters.formatBytes(file.sizeBytes)} (${file.sizeBytes} bytes)", style = MaterialTheme.typography.bodySmall)
                    Text("Modified: ${Formatters.formatDate(file.modifiedMs)}", style = MaterialTheme.typography.bodySmall)
                    Text("Type: ${file.mime ?: "Unknown"} (${file.category.name})", style = MaterialTheme.typography.bodySmall)
                    if (!file.title.isNullOrBlank()) {
                        Text("Title: ${file.title}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!file.artist.isNullOrBlank()) {
                        Text("Artist: ${file.artist}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!file.album.isNullOrBlank()) {
                        Text("Album: ${file.album}", style = MaterialTheme.typography.bodySmall)
                    }
                    if ((file.durationMs ?: 0L) > 0L) {
                        Text("Duration: ${Formatters.formatDuration(file.durationMs!!)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if ((file.bitrateKbps ?: 0) > 0) {
                        Text("Bitrate: ${file.bitrateKbps} kbps", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun KeepRemoveToggle(
    isKeep: Boolean,
    onToggle: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isKeep) Teal600.copy(alpha = 0.2f) else Danger.copy(alpha = 0.2f),
        label = "toggle_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isKeep) Teal400 else Danger,
        label = "toggle_text"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isKeep) Teal400.copy(alpha = 0.5f) else Danger.copy(alpha = 0.5f),
        label = "toggle_border"
    )

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isKeep) Icons.Rounded.CheckCircle else Icons.Rounded.RemoveCircle,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isKeep) "KEEP" else "REMOVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}
