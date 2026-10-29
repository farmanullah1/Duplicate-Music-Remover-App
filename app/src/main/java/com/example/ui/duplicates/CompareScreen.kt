package com.example.ui.duplicates

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.core.Formatters
import com.example.di.LocalAppContainer
import com.example.di.viewModelFactory
import com.example.domain.model.DuplicateGroupUi
import com.example.domain.model.MatchType
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    groupId: Long,
    onNavigateBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompareViewModel = run {
        val c = LocalAppContainer.current
        viewModel(key = "compare_$groupId", factory = viewModelFactory {
            CompareViewModel(
                context = c.context,
                fileRepository = c.fileRepository,
                trashUseCase = c.trashUseCase,
                targetGroupId = groupId
            )
        })
    }
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val group = uiState.currentGroup

    val coordinator = viewModel.playerCoordinator
    val playerState by coordinator.state.collectAsStateWithLifecycle()

    var fileForTagEdit by remember { mutableStateOf<MediaFile?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            coordinator.pause()
        }
    }

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val count = uiState.groups.size
                    val current = if (count > 0) uiState.currentIndex + 1 else 0
                    Text(
                        text = "Group $current of $count",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.groups.size > 1) {
                        IconButton(
                            onClick = { viewModel.selectGroupIndex(uiState.currentIndex - 1) },
                            enabled = uiState.currentIndex > 0
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous")
                        }
                        IconButton(
                            onClick = { viewModel.selectGroupIndex(uiState.currentIndex + 1) },
                            enabled = uiState.currentIndex < uiState.groups.size - 1
                        ) {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (group == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No duplicate group to compare",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = Spacing.md)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Match Header Card
                SafaiCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            CategoryBadge(group.category)
                            Column {
                                Text(
                                    text = "Match Type: " + when (group.matchType) {
                                        MatchType.EXACT_CONTENT -> "Identical content (SHA-256)"
                                        MatchType.SIMILAR_IMAGE -> "Similar photo ${(group.score * 100).toInt()}%"
                                        MatchType.SAME_AUDIO_TAGS -> "Same song tags"
                                        MatchType.NAME_VARIATION -> "Name variation"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${group.files.size} duplicate copies · ${Formatters.formatBytes(group.reclaimableBytes)} can be freed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        ConfidenceBadge(group.score)
                    }
                }

                // Waveform Variance Badge (for Audio)
                if (group.category == MediaCategory.AUDIO && group.files.size >= 2) {
                    val fileA = group.files[0].file
                    val fileB = group.files[1].file
                    WaveformDifferenceBadge(fileA = fileA, fileB = fileB)
                }

                // Category-Specific Preview
                when (group.category) {
                    MediaCategory.AUDIO -> {
                        AudioComparePreview(
                            files = group.files.map { it.file },
                            playerState = playerState,
                            onSelectIndex = { coordinator.select(it, keepPosition = true) },
                            onTogglePlay = { coordinator.toggle() },
                            onSeek = { coordinator.seek(it) },
                            onEditTags = { fileForTagEdit = it }
                        )
                    }
                    MediaCategory.IMAGE -> {
                        ImageComparePreview(
                            files = group.files.map { it.file },
                            onOpenFile = { file -> openExternalFile(context, file) }
                        )
                    }
                    MediaCategory.VIDEO -> {
                        VideoComparePreview(
                            files = group.files.map { it.file },
                            onOpenFile = { file -> openExternalFile(context, file) }
                        )
                    }
                    MediaCategory.DOCUMENT, MediaCategory.ARCHIVE, MediaCategory.APK, MediaCategory.OTHER -> {
                        DocComparePreview(
                            files = group.files.map { it.file },
                            onOpenFile = { file -> openExternalFile(context, file) }
                        )
                    }
                }

                // Comparison Matrix
                ComparisonMatrixCard(
                    group = group,
                    onCopyPath = { path ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("File Path", path))
                        onShowSnackbar("Path copied to clipboard")
                    },
                    onEditTags = { file -> fileForTagEdit = file }
                )

                // Actions Card
                SafaiCard(tonal = true) {
                    Text(
                        text = "Decide Keep or Remove",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(Spacing.sm))

                    group.files.forEach { member ->
                        val isKeep = uiState.fileKeepOverrides[member.file.path] ?: member.recommendedKeep
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = member.file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            KeepRemoveToggle(
                                isKeep = isKeep,
                                onToggle = {
                                    val ok = viewModel.toggleFileKeep(member.file.path)
                                    if (!ok) onShowSnackbar("At least one file must be kept")
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.markNotDuplicate {
                                    onShowSnackbar("Marked as not duplicate")
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Not a duplicate")
                        }

                        DangerButton(
                            text = "Clean REMOVE",
                            onClick = {
                                viewModel.cleanGroup { batch ->
                                    onShowSnackbar("Moved ${batch.succeeded.size} files to Recycle Bin")
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }

    // Tag Editor Dialog
    if (fileForTagEdit != null) {
        MetadataEditorDialog(
            mediaFile = fileForTagEdit!!,
            onSave = { path, title, artist, album ->
                viewModel.updateMetadata(path, title, artist, album) {
                    onShowSnackbar("Updated tags for ${fileForTagEdit!!.name}")
                }
                fileForTagEdit = null
            },
            onDismiss = { fileForTagEdit = null }
        )
    }
}

private fun openExternalFile(context: Context, file: MediaFile) {
    try {
        val f = File(file.path)
        val uri = if (file.uri != null) Uri.parse(file.uri)
        else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mime ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
    }
}

@Composable
private fun AudioComparePreview(
    files: List<MediaFile>,
    playerState: PlayerCoordinator.UiState,
    onSelectIndex: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onEditTags: (MediaFile) -> Unit
) {
    val activeFile = files.getOrNull(playerState.activeIndex) ?: files.firstOrNull()

    SafaiCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Synchronized Audio A/B Playback", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (activeFile != null) {
                TextButton(
                    onClick = { onEditTags(activeFile) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit Tags", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))

        // A / B Selector Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            files.forEachIndexed { index, file ->
                val isSelected = playerState.activeIndex == index
                val label = ('A' + index).toString()
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectIndex(index) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Track $label (${file.extension.uppercase()})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${file.bitrateKbps ?: 0} kbps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // Active Track Waveform
        if (activeFile != null) {
            val otherFile = files.find { it.path != activeFile.path }
            AudioWaveformVisualizer(
                file = activeFile,
                isPlaying = playerState.isPlaying,
                progressMs = playerState.positionMs,
                durationMs = playerState.durationMs.coerceAtLeast(activeFile.durationMs ?: 1000L),
                onSeek = onSeek,
                comparisonBaseFile = otherFile,
                height = 36.dp,
                barCount = 36
            )
        }

        Spacer(Modifier.height(Spacing.xs))

        // Playhead Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    tint = Color.Black
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = if (playerState.durationMs > 0) playerState.positionMs.toFloat() / playerState.durationMs else 0f,
                    onValueChange = { frac ->
                        onSeek((frac * playerState.durationMs).toLong())
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(Formatters.formatDuration(playerState.positionMs), style = MaterialTheme.typography.labelSmall)
                    Text(Formatters.formatDuration(playerState.durationMs), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ImageComparePreview(
    files: List<MediaFile>,
    onOpenFile: (MediaFile) -> Unit
) {
    SafaiCard {
        Text("Photos Side-by-Side (Pinch to zoom)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.sm))

        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            offset += offsetChange
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .transformable(state = transformState),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            files.forEach { file ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .clickable { onOpenFile(file) }
                ) {
                    AsyncImage(
                        model = file.uri ?: file.path,
                        contentDescription = file.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoComparePreview(
    files: List<MediaFile>,
    onOpenFile: (MediaFile) -> Unit
) {
    SafaiCard {
        Text("Video Comparison", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            files.forEach { file ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ThumbnailImage(file = file, size = 80.dp)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val res = if ((file.width ?: 0) > 0 && (file.height ?: 0) > 0) "${file.width}×${file.height} · " else ""
                    val dur = file.durationMs?.let { Formatters.formatDuration(it) } ?: ""
                    Text(
                        text = if (res.isNotEmpty() || dur.isNotEmpty()) "$res$dur" else Formatters.formatBytes(file.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Button(
                        onClick = { onOpenFile(file) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Open Video", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DocComparePreview(
    files: List<MediaFile>,
    onOpenFile: (MediaFile) -> Unit
) {
    SafaiCard {
        Text("Document Comparison", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            files.forEach { file ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ThumbnailImage(file = file, size = 64.dp)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = Formatters.formatBytes(file.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Button(
                        onClick = { onOpenFile(file) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Open", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonMatrixCard(
    group: DuplicateGroupUi,
    onCopyPath: (String) -> Unit,
    onEditTags: (MediaFile) -> Unit
) {
    val files = group.files.map { it.file }
    SafaiCard {
        Text(
            text = "Detailed Comparison Matrix",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.sm))

        // Attribute rows
        MatrixRow("Name", files.map { it.name })
        MatrixRow("Folder", files.map { it.folder }, onCopy = onCopyPath)
        MatrixRow("Size", files.map { Formatters.formatBytes(it.sizeBytes) }, bestIndex = files.indices.maxByOrNull { files[it].sizeBytes })
        MatrixRow("Modified", files.map { Formatters.formatDate(it.modifiedMs) })

        if (group.category == MediaCategory.AUDIO) {
            MatrixRow("Bitrate", files.map { "${it.bitrateKbps ?: 0} kbps" }, bestIndex = files.indices.maxByOrNull { files[it].bitrateKbps ?: 0 })
            MatrixRow("Duration", files.map { Formatters.formatDuration(it.durationMs ?: 0L) })
            MatrixRow("Sample Rate", files.map { it.sampleRateHz?.let { "$it Hz" } ?: "—" })
            MatrixRow("Title", files.map { it.title ?: "—" })
            MatrixRow("Artist", files.map { it.artist ?: "—" })
            MatrixRow("Album", files.map { it.album ?: "—" })
            MatrixRow("Format", files.map { it.extension.uppercase() })
        }

        if (group.category == MediaCategory.IMAGE) {
            MatrixRow("Resolution", files.map { if ((it.width ?: 0) > 0) "${it.width}×${it.height}" else "—" })
            MatrixRow("Megapixels", files.map {
                val mp = ((it.width ?: 0) * (it.height ?: 0)) / 1_000_000f
                if (mp > 0) String.format("%.1f MP", mp) else "—"
            }, bestIndex = files.indices.maxByOrNull { (files[it].width ?: 0) * (files[it].height ?: 0) })
        }

        if (group.category == MediaCategory.VIDEO) {
            MatrixRow("Resolution", files.map { if ((it.width ?: 0) > 0) "${it.width}×${it.height}" else "—" })
            MatrixRow("Duration", files.map { Formatters.formatDuration(it.durationMs ?: 0L) })
        }

        MatrixRow("SHA-256", files.map { it.fullHash?.take(10)?.plus("…") ?: "—" })
    }
}

@Composable
private fun MatrixRow(
    label: String,
    values: List<String>,
    bestIndex: Int? = null,
    onCopy: ((String) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            values.forEachIndexed { index, value ->
                val isBest = bestIndex == index
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (onCopy != null) Modifier.clickable { onCopy(value) } else Modifier),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isBest) Success.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, if (isBest) Success.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                        color = if (isBest) Success else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
