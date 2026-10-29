package com.example.ui.junk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.Formatters
import com.example.core.StorageAccess
import com.example.di.LocalAppContainer
import com.example.di.viewModelFactory
import com.example.domain.model.JunkCategoryResult
import com.example.domain.model.JunkItem
import com.example.domain.model.JunkKind
import com.example.ui.components.*
import com.example.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JunkScreen(
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JunkViewModel = run {
        val c = LocalAppContainer.current
        viewModel(factory = viewModelFactory {
            JunkViewModel(
                junkScanner = c.junkScanner,
                fileOps = c.fileOps,
                trashUseCase = c.trashUseCase,
                recycleBinManager = c.recycleBinManager,
                scanSessionDao = c.database.scanSessionDao(),
                settingsRepository = c.settingsRepository
            )
        })
    }
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanningDir by viewModel.currentScanningDir.collectAsStateWithLifecycle()
    val categories by viewModel.junkCategories.collectAsStateWithLifecycle()
    val selectedKinds by viewModel.selectedKinds.collectAsStateWithLifecycle()
    val largeFiles by viewModel.largeFiles.collectAsStateWithLifecycle()
    val selectedLargeFiles by viewModel.selectedLargeFiles.collectAsStateWithLifecycle()
    val cleanResult by viewModel.cleanResult.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var largeThresholdMb by remember { mutableIntStateOf(settings.largeFileThresholdMb) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val hasFullAccess = StorageAccess.mode(context) == StorageAccess.Mode.FULL

    LaunchedEffect(selectedTab, largeThresholdMb) {
        if (selectedTab == 1 && largeFiles.isEmpty()) {
            viewModel.loadLargeFiles(largeThresholdMb)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Text(
                text = stringResource(R.string.tab_junk),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )

            // Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.junk_tab_cleanup)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.loadLargeFiles(largeThresholdMb)
                    },
                    text = { Text(stringResource(R.string.junk_tab_large)) }
                )
            }

            Spacer(Modifier.height(Spacing.xs))

            if (selectedTab == 0) {
                // CLEANUP TAB
                if (!hasFullAccess) {
                    PermissionCard(
                        title = stringResource(R.string.perm_all_files_title),
                        text = "To find and clean temporary files, cache, and empty folders, All Files Access is required.",
                        buttonLabel = stringResource(R.string.perm_all_files_button),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                context.startActivity(StorageAccess.allFilesIntent(context))
                            }
                        },
                        modifier = Modifier.padding(Spacing.md)
                    )
                } else if (categories.isEmpty() && !isScanning) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        IconBadge(
                            icon = Icons.Rounded.CleaningServices,
                            tint = Teal400,
                            size = 72.dp
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = stringResource(R.string.junk_scan_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = stringResource(R.string.junk_scan_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(Spacing.xl))
                        PrimaryButton(
                            text = stringResource(R.string.junk_scan_button),
                            onClick = { viewModel.scanJunk() },
                            icon = Icons.Rounded.CleaningServices
                        )
                    }
                } else if (isScanning) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Teal400, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(Spacing.lg))
                        Text(
                            text = "Analyzing storage for junk files…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (scanningDir.isNotBlank()) {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = scanningDir,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    // Category List
                    val selectedBytes = categories.filter { it.kind in selectedKinds }.sumOf { it.totalBytes }
                    val totalFoundBytes = categories.sumOf { it.totalBytes }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        contentPadding = PaddingValues(bottom = if (selectedBytes > 0) 90.dp else 16.dp)
                    ) {
                        item {
                            SafaiCard(tonal = true) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Junk Files Detected",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        AnimatedCounterText(
                                            value = totalFoundBytes,
                                            style = MaterialTheme.typography.headlineLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.scanJunk() },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(Sizes.iconSm))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Rescan")
                                    }
                                }
                            }
                        }

                        items(categories) { cat ->
                            JunkCategoryCard(
                                category = cat,
                                isSelected = cat.kind in selectedKinds,
                                onToggle = { viewModel.toggleCategory(cat.kind) }
                            )
                        }

                        item {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = stringResource(R.string.junk_honest_footer),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.xs)
                            )
                            Spacer(Modifier.height(Spacing.xl))
                        }
                    }
                }
            } else {
                // LARGE FILES TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.md)
                ) {
                    // Threshold Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        listOf(50 to "50 MB", 100 to "100 MB", 500 to "500 MB", 1024 to "1 GB").forEach { (threshold, label) ->
                            FilterChip(
                                selected = largeThresholdMb == threshold,
                                onClick = {
                                    largeThresholdMb = threshold
                                    viewModel.loadLargeFiles(threshold)
                                },
                                label = { Text(label) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    if (largeFiles.isEmpty()) {
                        EmptyState(
                            icon = Icons.Rounded.Storage,
                            title = "No large files found",
                            message = "No files larger than $largeThresholdMb MB found on storage.",
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            contentPadding = PaddingValues(bottom = if (selectedLargeFiles.isNotEmpty()) 90.dp else 16.dp)
                        ) {
                            items(largeFiles, key = { it.path }) { item ->
                                val isSelected = item.path in selectedLargeFiles
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleLargeFileSelection(item.path) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(Spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { viewModel.toggleLargeFileSelection(item.path) }
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = File(item.path).name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.path.substringBeforeLast('/'),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${Formatters.formatBytes(item.sizeBytes)} · ${Formatters.formatDate(item.modifiedMs)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(onClick = {
                                            try {
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.path))
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, "*/*")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                            }
                                        }) {
                                            Icon(Icons.Rounded.OpenInNew, contentDescription = "Open")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Selection Bar for Cleanup
        if (selectedTab == 0 && categories.isNotEmpty()) {
            val selectedBytes = categories.filter { it.kind in selectedKinds }.sumOf { it.totalBytes }
            val selectedCount = categories.filter { it.kind in selectedKinds }.sumOf { it.items.size }
            if (selectedBytes > 0 || selectedCount > 0) {
                SelectionBottomBar(
                    count = selectedCount,
                    size = selectedBytes,
                    onClear = { /* deselect */ },
                    onDelete = { showConfirmDialog = true },
                    label = "Clean",
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        } else if (selectedTab == 1 && selectedLargeFiles.isNotEmpty()) {
            val selectedBytes = largeFiles.filter { it.path in selectedLargeFiles }.sumOf { it.sizeBytes }
            SelectionBottomBar(
                count = selectedLargeFiles.size,
                size = selectedBytes,
                onClear = { /* deselect */ },
                onDelete = {
                    viewModel.trashLargeFiles(selectedLargeFiles) {
                        onShowSnackbar("Moved large files to Recycle Bin")
                    }
                },
                label = "To Trash",
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Confirm Dialog for Junk Clean
    if (showConfirmDialog) {
        val selectedBytes = categories.filter { it.kind in selectedKinds }.sumOf { it.totalBytes }
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = { Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = Teal400) },
            title = { Text("Clean Selected Junk?") },
            text = {
                Text(
                    "You are about to free approximately ${Formatters.formatBytes(selectedBytes)}.\n\n" +
                    "Temporary and empty files will be deleted permanently. Any APK files or messaging media will be moved safely to your Recycle Bin."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.cleanSelectedJunk { res ->
                            onShowSnackbar("Cleaned ${Formatters.formatBytes(res.freedBytes)} successfully")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Clean Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Result Bottom Sheet
    if (cleanResult != null) {
        val res = cleanResult!!
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissCleanResult() },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                IconBadge(Icons.Rounded.CheckCircle, Success, size = 64.dp)
                Text(
                    text = "Freed ${Formatters.formatBytes(res.freedBytes)}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Measured with system storage. Your device has more breathing room!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                PrimaryButton(
                    text = "Done",
                    onClick = { viewModel.dismissCleanResult() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun JunkCategoryCard(
    category: JunkCategoryResult,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val (title, icon, color) = when (category.kind) {
        JunkKind.TEMP_LOG -> Triple("Temporary & Log Files", Icons.Rounded.Description, CatDocument)
        JunkKind.THUMBNAILS -> Triple("Cached Thumbnails", Icons.Rounded.Image, CatImage)
        JunkKind.EMPTY_FILE -> Triple("Empty Files", Icons.Rounded.InsertDriveFile, CatOther)
        JunkKind.EMPTY_FOLDER -> Triple("Empty Folders", Icons.Rounded.Folder, CatArchive)
        JunkKind.INCOMPLETE_DOWNLOAD -> Triple("Incomplete Downloads", Icons.Rounded.Download, CatAudio)
        JunkKind.APP_CACHE -> Triple("App Cache", Icons.Rounded.Cached, Teal400)
        JunkKind.APK_INSTALLER -> Triple("Old APK Installers", Icons.Rounded.Android, CatApk)
        JunkKind.MESSAGING_MEDIA -> Triple("Old WhatsApp Media (>30d)", Icons.Rounded.Message, CatVideo)
        else -> Triple(category.kind.name, Icons.Rounded.CleaningServices, CatOther)
    }

    SafaiCard(tonal = true) {
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
                Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
                IconBadge(icon = icon, tint = color, size = 40.dp)
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${category.items.size} items · ${Formatters.formatBytes(category.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }

        if (expanded && category.items.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.xs))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                category.items.take(20).forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = File(item.path).name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = Formatters.formatBytes(item.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (category.items.size > 20) {
                    Text(
                        text = "…and ${category.items.size - 20} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
