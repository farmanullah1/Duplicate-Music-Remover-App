package com.example.ui.duplicates

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.R
import com.example.core.Formatters
import com.example.di.LocalAppContainer
import com.example.di.viewModelFactory
import com.example.domain.model.DuplicateGroupUi
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    initialCategory: String?,
    onNavigateToCompare: (Long) -> Unit,
    onNavigateToScan: () -> Unit,
    onShowSnackbarWithAction: (String, String, () -> Unit) -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DuplicatesViewModel = run {
        val c = LocalAppContainer.current
        viewModel(factory = viewModelFactory {
            DuplicatesViewModel(
                fileRepository = c.fileRepository,
                trashUseCase = c.trashUseCase
            )
        })
    }
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scanState by com.example.data.scan.ScanStateHolder.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var showSortMenu by remember { mutableStateOf(false) }
    var fileToEditMetadata by remember { mutableStateOf<MediaFile?>(null) }
    var groupToConfirmClean by remember { mutableStateOf<DuplicateGroupUi?>(null) }
    var showConfirmDeleteForeverDialog by remember { mutableStateOf(false) }

    // Quick audition ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    var currentlyPlayingPath by remember { mutableStateOf<String?>(null) }
    var auditionProgressMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentlyPlayingPath = null
                    auditionProgressMs = 0L
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(currentlyPlayingPath) {
        while (isActive && currentlyPlayingPath != null) {
            auditionProgressMs = exoPlayer.currentPosition
            delay(150)
        }
    }

    LaunchedEffect(initialCategory) {
        if (!initialCategory.isNullOrBlank()) {
            val cat = runCatching { MediaCategory.valueOf(initialCategory) }.getOrNull()
            viewModel.setCategory(cat)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sleek Top App Bar Area (Fixed compact header)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.tab_duplicates),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Rounded.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Most space reclaimable") },
                            onClick = {
                                viewModel.setSort(DupSort.MOST_SPACE)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Most duplicate files") },
                            onClick = {
                                viewModel.setSort(DupSort.MOST_FILES)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("File name (A-Z)") },
                            onClick = {
                                viewModel.setSort(DupSort.NAME)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            // Scrollable Content
            if (uiState.filteredGroups.isEmpty() && uiState.searchQuery.isEmpty() && uiState.selectedCategory == null) {
                if (uiState.allGroups.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.ContentCopy,
                        title = stringResource(R.string.dup_empty_noscan_title),
                        message = stringResource(R.string.dup_empty_noscan_message),
                        actionLabel = "Scan Now",
                        onAction = onNavigateToScan,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.dup_empty_title),
                        message = "No duplicate files match your filter or search criteria.",
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                var renderLimit by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(500) }
                val visibleGroups = remember(uiState.filteredGroups, renderLimit) {
                    uiState.filteredGroups.take(renderLimit)
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(
                        bottom = if (uiState.totalSelectedCount > 0) 80.dp else 16.dp
                    )
                ) {
                    // 1. Partial media access banner
                    if (com.example.core.StorageAccess.isPartialMediaAccess(context)) {
                        item {
                            PermissionCard(
                                title = "Partial Photo Access (Selected Only)",
                                text = "Safai can only see the photos you selected. Allow all photos to find every duplicate.",
                                buttonLabel = "App Settings",
                                onClick = {
                                    context.startActivity(com.example.core.StorageAccess.appDetailsIntent(context))
                                }
                            )
                        }
                    }

                    // 2. Active scan indicator
                    if (scanState is com.example.data.scan.ScanState.Running) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "Storage scan in progress… Please wait before cleaning.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // 3. Search Bar
                    item {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.dup_search_hint)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    }

                    // 4. Batch Selection Action Presets Row
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            item {
                                AssistChip(
                                    onClick = { viewModel.selectBestBitrate() },
                                    label = { Text("Keep Best Bitrate") },
                                    leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null, tint = Gold400, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = { viewModel.selectPurgeCopiesOldest() },
                                    label = { Text("Purge Copies (Keep Oldest)") },
                                    leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, tint = Teal400, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = { viewModel.selectKeepCompleteTags() },
                                    label = { Text("Keep Complete Tags") },
                                    leadingIcon = { Icon(Icons.Rounded.Label, contentDescription = null, tint = Violet400, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = { viewModel.selectKeepNewest() },
                                    label = { Text("Keep Newest") },
                                    leadingIcon = { Icon(Icons.Rounded.NewReleases, contentDescription = null, tint = Teal400, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = { viewModel.selectAllDuplicates() },
                                    label = { Text("Select All Duplicates") },
                                    leadingIcon = { Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = { viewModel.deselectAllDuplicates() },
                                    label = { Text("Deselect All") },
                                    leadingIcon = { Icon(Icons.Rounded.Deselect, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = { viewModel.invertSelection() },
                                    label = { Text("Invert Selection") },
                                    leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // 5. Category Filter Chips
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            item {
                                FilterChip(
                                    selected = uiState.selectedCategory == null,
                                    onClick = { viewModel.setCategory(null) },
                                    label = { Text("All (${uiState.allGroups.size})") },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            MediaCategory.entries.filter { it != MediaCategory.OTHER }.forEach { cat ->
                                val count = uiState.allGroups.count { it.category == cat }
                                item {
                                    FilterChip(
                                        selected = uiState.selectedCategory == cat,
                                        onClick = { viewModel.setCategory(cat) },
                                        enabled = count > 0,
                                        label = { Text("${cat.name.lowercase().replaceFirstChar { it.uppercase() }} ($count)") },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 6. Confidence Filter Chips
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            listOf("Certain" to Success, "Likely" to Warning, "Review" to Danger).forEach { (conf, color) ->
                                val selected = conf in uiState.confidenceFilter
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.toggleConfidence(conf) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = conf,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 7. Summary Card
                    if (uiState.filteredGroups.isNotEmpty()) {
                        item {
                            SafaiCard(tonal = true) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = "${uiState.filteredGroups.size} Duplicate Sets Found",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Selected: ${uiState.totalSelectedCount} files (${Formatters.formatBytes(uiState.totalSelectedBytes)})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        TextButton(onClick = { viewModel.selectRecommendedForCertain() }) {
                                            Text("Auto Pick", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Button(
                                            onClick = { viewModel.setReviewSheetOpen(true) },
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = uiState.totalSelectedCount > 0,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Review", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 8. If viewing Audio duplicates, display StorageBreakdownChart
                    if ((uiState.selectedCategory == null || uiState.selectedCategory == MediaCategory.AUDIO) && uiState.allAudioFiles.isNotEmpty()) {
                        item {
                            StorageBreakdownChart(
                                allFiles = uiState.allAudioFiles,
                                duplicateGroups = uiState.allGroups,
                                onSelectFormatFilter = { fmt -> viewModel.setFormatFilter(fmt) }
                            )
                        }
                    }

                    // 9. Duplicate Group Cards
                    items(
                        items = visibleGroups,
                        key = { it.id }
                    ) { group ->
                        DuplicateGroupCard(
                            group = group,
                            fileKeepOverrides = uiState.fileKeepOverrides,
                            onToggleKeep = { path ->
                                val success = viewModel.toggleFileKeep(group, path)
                                if (!success) {
                                    onShowSnackbar("At least one file must be kept")
                                }
                            },
                            onCompare = { onNavigateToCompare(group.id) },
                            onNotDuplicate = {
                                viewModel.markNotDuplicate(group) { undoFn ->
                                    onShowSnackbarWithAction("Won't show these together again", "Undo") {
                                        coroutineScope.launch { undoFn() }
                                    }
                                }
                            },
                            onCleanGroup = {
                                if (scanState is com.example.data.scan.ScanState.Running) {
                                    onShowSnackbar("Wait for the scan to finish")
                                } else {
                                    groupToConfirmClean = group
                                }
                            },
                            onSelectStrategy = { strategy ->
                                viewModel.applyGroupStrategy(group, strategy)
                            },
                            onEditTags = { file ->
                                fileToEditMetadata = file
                            },
                            currentlyPlayingPath = currentlyPlayingPath,
                            onTogglePlayAudio = { audioFile ->
                                if (currentlyPlayingPath == audioFile.path) {
                                    exoPlayer.stop()
                                    currentlyPlayingPath = null
                                } else {
                                    currentlyPlayingPath = audioFile.path
                                    val uri = if (audioFile.uri != null) Uri.parse(audioFile.uri) else Uri.fromFile(java.io.File(audioFile.path))
                                    exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                                    exoPlayer.prepare()
                                    exoPlayer.playWhenReady = true
                                }
                            },
                            playingProgressMs = auditionProgressMs
                        )
                    }

                    // 10. Show more footer
                    if (uiState.filteredGroups.size > visibleGroups.size) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { renderLimit += 500 },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Box(
                                    modifier = Modifier.padding(Spacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Show more (${uiState.filteredGroups.size - visibleGroups.size} remaining)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Persistent Bottom Selection Bar (Floats above bottom navigation bar without overlapping)
        if (uiState.totalSelectedCount > 0) {
            SelectionBottomBar(
                count = uiState.totalSelectedCount,
                size = uiState.totalSelectedBytes,
                onClear = { viewModel.deselectAllDuplicates() },
                onDelete = {
                    if (scanState is com.example.data.scan.ScanState.Running) {
                        onShowSnackbar("Wait for the scan to finish")
                    } else {
                        viewModel.setReviewSheetOpen(true)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                label = "Review & Clean"
            )
        }
    }

    // Confirmation Dialog before cleaning a single group
    if (groupToConfirmClean != null) {
        val group = groupToConfirmClean!!
        val overrides = uiState.fileKeepOverrides
        val filesToRemove = group.files.filter { !(overrides[it.file.path] ?: it.recommendedKeep) }
        val toRemoveBytes = filesToRemove.sumOf { it.file.sizeBytes }

        AlertDialog(
            onDismissRequest = { groupToConfirmClean = null },
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Danger) },
            title = { Text("Clean Duplicate Group?") },
            text = {
                Text(
                    "You are about to remove ${filesToRemove.size} duplicate copies (${Formatters.formatBytes(toRemoveBytes)}) from \"${group.files.firstOrNull()?.file?.name}\".\n\n" +
                    "At least one original file will be safely kept. Redundant copies will be moved to your Recycle Bin."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = groupToConfirmClean
                        groupToConfirmClean = null
                        if (target != null) {
                            if (currentlyPlayingPath != null) {
                                exoPlayer.stop()
                                currentlyPlayingPath = null
                            }
                            viewModel.cleanGroup(target) { batch ->
                                if (batch.failed.isNotEmpty() && batch.succeeded.isEmpty()) {
                                    onShowSnackbar("Could not clean: ${batch.failed.first()}")
                                } else {
                                    onShowSnackbarWithAction(
                                        "Moved ${batch.succeeded.size} files to Recycle Bin",
                                        "Undo"
                                    ) {
                                        viewModel.undoTrash(batch.succeeded.map { it.id })
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clean Group", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToConfirmClean = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showConfirmDeleteForeverDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteForeverDialog = false },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = Danger) },
            title = { Text("Delete Files Permanently?") },
            text = {
                Text(
                    "You are about to permanently delete ${uiState.totalSelectedCount} file(s) (${Formatters.formatBytes(uiState.totalSelectedBytes)}).

" +
                    "These files will not be moved to the Recycle Bin and cannot be recovered. Are you sure you want to proceed?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteForeverDialog = false
                        viewModel.setReviewSheetOpen(false)
                        if (currentlyPlayingPath != null) {
                            exoPlayer.stop()
                            currentlyPlayingPath = null
                        }
                        viewModel.cleanAllSelected(permanent = true) { batch ->
                            onShowSnackbar("Permanently deleted ${Formatters.formatBytes(batch.bytes)}")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteForeverDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Metadata Tag Editor Dialog
    if (fileToEditMetadata != null) {
        MetadataEditorDialog(
            mediaFile = fileToEditMetadata!!,
            onSave = { path, title, artist, album ->
                viewModel.updateMetadata(path, title, artist, album) {
                    onShowSnackbar("Updated tags for ${fileToEditMetadata!!.name}")
                }
                fileToEditMetadata = null
            },
            onDismiss = { fileToEditMetadata = null }
        )
    }

    // Review Modal Bottom Sheet
    if (uiState.reviewSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setReviewSheetOpen(false) },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "Review Clean Selection",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "You are about to remove ${uiState.totalSelectedCount} duplicate copies (${Formatters.formatBytes(uiState.totalSelectedBytes)}). Every duplicate group will keep at least one original copy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = "Safe Deletion Options",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Move to Recycle Bin: safe for 30 days with instant 1-tap restore.\n• Delete permanently: removes files immediately to free hardware storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    PrimaryButton(
                        text = "To Recycle Bin",
                        onClick = {
                            if (currentlyPlayingPath != null) {
                                exoPlayer.stop()
                                currentlyPlayingPath = null
                            }
                            viewModel.cleanAllSelected(permanent = false) { batch ->
                                onShowSnackbarWithAction(
                                    "Moved ${batch.succeeded.size} files to Recycle Bin",
                                    "Undo"
                                ) {
                                    viewModel.undoTrash(batch.succeeded.map { it.id })
                                }
                            }
                        },
                        icon = Icons.Rounded.DeleteOutline,
                        modifier = Modifier.weight(1f)
                    )

                    DangerButton(
                        text = "Delete Forever",
                        onClick = {
                            showConfirmDeleteForeverDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
