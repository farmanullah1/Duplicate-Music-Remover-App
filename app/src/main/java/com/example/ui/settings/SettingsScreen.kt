package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.Formatters
import com.example.core.StorageAccess
import com.example.data.settings.ThemeMode
import com.example.di.LocalAppContainer
import com.example.di.viewModelFactory
import com.example.ui.components.*
import com.example.ui.theme.*
import java.io.File

@Composable
fun SettingsScreen(
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = run {
        val c = LocalAppContainer.current
        viewModel(factory = viewModelFactory {
            SettingsViewModel(
                context = c.context,
                settingsRepository = c.settingsRepository,
                scanSessionDao = c.database.scanSessionDao(),
                scannedFileDao = c.database.scannedFileDao(),
                fileRepository = c.fileRepository,
                exclusionDao = c.database.exclusionDao()
            )
        })
    }
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val exclusions by viewModel.exclusions.collectAsStateWithLifecycle()
    val exclusionCount by viewModel.exclusionCount.collectAsStateWithLifecycle()

    var showFolderDialog by remember { mutableStateOf<String?>(null) } // "excluded" or "priority"
    var showExclusionsDialog by remember { mutableStateOf(false) }
    var hasCrashLog by remember { mutableStateOf(viewModel.hasCrashLog()) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportCsv(uri) { success ->
                onShowSnackbar(if (success) "Report exported successfully" else "Failed to export report")
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item { Spacer(Modifier.height(Spacing.xs)) }

        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 1. Permissions Card
        item {
            val mode = StorageAccess.mode(context)
            SafaiCard(tonal = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Storage Permission Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (mode) {
                                StorageAccess.Mode.FULL -> "All Files Access (deep disk & junk cleaning enabled)"
                                StorageAccess.Mode.MEDIA_ONLY -> "Media Only Access (system dialog required for deletes)"
                                StorageAccess.Mode.NONE -> "No storage permissions granted"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (mode != StorageAccess.Mode.FULL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Button(
                            onClick = { context.startActivity(StorageAccess.allFilesIntent(context)) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Grant All")
                        }
                    }
                }
            }
        }

        // 2. Scanning Preferences Card
        item {
            SafaiCard(tonal = true) {
                Text(
                    text = stringResource(R.string.settings_section_scanning),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(Spacing.sm))

                SettingSwitchRow(stringResource(R.string.settings_scan_audio), settings.scanAudio) {
                    viewModel.updateSettings { s -> s.copy(scanAudio = it) }
                }
                SettingSwitchRow(stringResource(R.string.settings_scan_images), settings.scanImages) {
                    viewModel.updateSettings { s -> s.copy(scanImages = it) }
                }
                SettingSwitchRow(stringResource(R.string.settings_scan_videos), settings.scanVideos) {
                    viewModel.updateSettings { s -> s.copy(scanVideos = it) }
                }
                SettingSwitchRow(stringResource(R.string.settings_scan_docs), settings.scanDocuments) {
                    viewModel.updateSettings { s -> s.copy(scanDocuments = it) }
                }
                SettingSwitchRow(stringResource(R.string.settings_scan_archives), settings.scanArchivesApks) {
                    viewModel.updateSettings { s -> s.copy(scanArchivesApks = it) }
                }
                SettingSwitchRow(stringResource(R.string.settings_skip_hidden), settings.skipHiddenFolders) {
                    viewModel.updateSettings { s -> s.copy(skipHiddenFolders = it) }
                }

                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Minimum file size: ${settings.minFileSizeKb} KB",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = settings.minFileSizeKb.toFloat(),
                    onValueChange = { viewModel.updateSettings { s -> s.copy(minFileSizeKb = it.toInt()) } },
                    valueRange = 0f..1024f,
                    steps = 8
                )

                Spacer(Modifier.height(Spacing.xs))
                SettingActionRow(
                    title = "Scan keeps stopping?",
                    subtitle = "Allow Safai to run in the background (ignore battery optimization)",
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                )

                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Excluded Folders (${settings.excludedFolders.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                settings.excludedFolders.forEach { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(folder, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeExcludedFolder(folder) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = Danger, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Button(
                    onClick = { showFolderDialog = "excluded" },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Add Excluded Folder")
                }
            }
        }

        // 3. Cleaning Card
        item {
            SafaiCard(tonal = true) {
                Text(
                    text = stringResource(R.string.settings_section_cleaning),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(Spacing.sm))

                Text("Recycle Bin Retention Period", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    listOf(7, 14, 30, 60).forEach { days ->
                        FilterChip(
                            selected = settings.trashRetentionDays == days,
                            onClick = { viewModel.updateSettings { it.copy(trashRetentionDays = days) } },
                            label = { Text("$days days") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.xs))
                SettingSwitchRow("Confirm batch deletions", settings.confirmBatchDeletes) {
                    viewModel.updateSettings { s -> s.copy(confirmBatchDeletes = it) }
                }
                SettingSwitchRow("Weekly auto-scan (idle & charging)", settings.autoScanWeekly) {
                    viewModel.updateSettings { s -> s.copy(autoScanWeekly = it) }
                }
                SettingSwitchRow("Monthly cleanup reminder", settings.cleanupReminder) {
                    viewModel.updateSettings { s -> s.copy(cleanupReminder = it) }
                }

                Spacer(Modifier.height(Spacing.xs))
                SettingActionRow(
                    title = "Ignored duplicate pairs",
                    subtitle = if (exclusionCount == 0) "No pairs currently ignored" else "$exclusionCount pairs ignored from scanning",
                    onClick = { showExclusionsDialog = true }
                )

                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Priority Keep Folders (${settings.priorityFolders.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Files in priority folders are always chosen as the keeper copy in duplicate groups.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                settings.priorityFolders.forEach { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(folder, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removePriorityFolder(folder) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = Danger, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Button(
                    onClick = { showFolderDialog = "priority" },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Add Priority Folder")
                }
            }
        }

        // 4. Appearance Card
        item {
            SafaiCard(tonal = true) {
                Text(
                    text = stringResource(R.string.settings_section_appearance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(Spacing.sm))

                Text("Theme Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.updateSettings { it.copy(themeMode = mode) } },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                SettingSwitchRow("AMOLED pure black background", settings.amoledBlack) {
                    viewModel.updateSettings { s -> s.copy(amoledBlack = it) }
                }
                SettingSwitchRow("Material You dynamic color", settings.dynamicColor) {
                    viewModel.updateSettings { s -> s.copy(dynamicColor = it) }
                }
                SettingSwitchRow("Haptic feedback", settings.haptics) {
                    viewModel.updateSettings { s -> s.copy(haptics = it) }
                }
                SettingSwitchRow("Reduce motion (instant animations)", settings.reduceMotion) {
                    viewModel.updateSettings { s -> s.copy(reduceMotion = it) }
                }
            }
        }

        // 5. Cleaning History & Export Card
        item {
            SafaiCard(tonal = true) {
                Text(
                    text = stringResource(R.string.settings_section_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(Spacing.sm))

                if (history.isEmpty()) {
                    Text(
                        text = "No scan sessions recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Recent Space Freed (Bytes per session)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.xs))

                    val maxFreed = history.maxOfOrNull { it.freedBytes }.takeIf { it != null && it > 0 } ?: 1L
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        val barWidth = (size.width / history.size.coerceAtLeast(1).coerceAtMost(10)) * 0.6f
                        val gap = (size.width / history.size.coerceAtLeast(1).coerceAtMost(10)) * 0.4f

                        history.take(10).reversed().forEachIndexed { index, session ->
                            val barHeight = (session.freedBytes.toFloat() / maxFreed.toFloat()) * size.height
                            val left = index * (barWidth + gap)
                            val top = size.height - barHeight.coerceAtLeast(4f)
                            drawRoundRect(
                                color = Teal400,
                                topLeft = Offset(left, top),
                                size = Size(barWidth, barHeight.coerceAtLeast(4f)),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                PrimaryButton(
                    text = stringResource(R.string.settings_export_csv),
                    onClick = { csvLauncher.launch("safai_duplicates_report_${System.currentTimeMillis()}.csv") },
                    icon = Icons.Rounded.Download,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 6. How Matching Works Card (Plain-text breakdown)
        item {
            SafaiCard(tonal = true) {
                Text(
                    text = "How Matching Works",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Safai uses strict on-device algorithms to detect duplicates without sending any data online:\n\n" +
                        "• Exact duplicates: Byte-identical streaming SHA-256 hash match.\n" +
                        "• Similar photos: 64-bit perceptual visual hash (dHash) comparing structural layout.\n" +
                        "• Same song: Matching ID3 artist + title tags and duration.\n" +
                        "• Name variations: Normalized filenames with copy suffixes like (1), - Copy, or _copy_2 stripped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // How matching works (file 11 §5)
        item {
            SafaiCard(tonal = true) {
                Text(
                    text = "How Matching Works",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.xs))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = "• Exact duplicates: Verified byte-for-byte using cryptographic SHA-256 binary hashing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Similar photos: Visual perception hashing (dHash) robust to resizing, compression, and slight crops.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Audio tracks: Matched when embedded ID3 tags (track title + artist) and duration closely agree across bitrates or formats.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Copy names: Normalized filename comparison detecting copy suffixes like ' (1)', ' - Copy', and bracketed variations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 7. About Card & Crash Logging
        item {
            SafaiCard(tonal = true) {
                Text(
                    text = stringResource(R.string.settings_section_about),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Safai v2.0",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "100% offline, verified SHA-256 duplicate detection, safe 30-day Recycle Bin, and complete user privacy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (hasCrashLog) {
                    Spacer(Modifier.height(Spacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.shareCrashLogIntent()?.let {
                                    context.startActivity(Intent.createChooser(it, "Share crash log"))
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share Crash Log")
                        }
                        TextButton(
                            onClick = {
                                viewModel.clearCrashLog()
                                hasCrashLog = false
                                onShowSnackbar("Crash log cleared")
                            }
                        ) {
                            Text("Clear", color = Danger)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(Spacing.xl)) }
    }

    // Folder selection dialog
    if (showFolderDialog != null) {
        val root = Environment.getExternalStorageDirectory()
        var currentDir by remember { mutableStateOf(root) }
        val subDirs: List<File> = currentDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList()

        AlertDialog(
            onDismissRequest = { showFolderDialog = null },
            title = { Text("Select Folder") },
            text = {
                Column(modifier = Modifier.heightIn(max = 300.dp)) {
                    Text(currentDir.absolutePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(Spacing.xs))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (currentDir.parentFile != null && currentDir != root) {
                            item {
                                TextButton(onClick = { currentDir = currentDir.parentFile!! }) {
                                    Text(".. (Up one level)")
                                }
                            }
                        }
                        items(subDirs) { d ->
                            TextButton(
                                onClick = { currentDir = d },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = CatArchive)
                                    Text(d.name, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val path = currentDir.absolutePath
                        if (showFolderDialog == "excluded") viewModel.addExcludedFolder(path)
                        else if (showFolderDialog == "priority") viewModel.addPriorityFolder(path)
                        showFolderDialog = null
                    }
                ) {
                    Text("Select This Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Ignored Pairs Dialog
    if (showExclusionsDialog) {
        AlertDialog(
            onDismissRequest = { showExclusionsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ignored Pairs (${exclusions.size})")
                    if (exclusions.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.clearAllExclusions()
                                onShowSnackbar("All ignored pairs cleared")
                            }
                        ) {
                            Text("Clear all", color = Danger)
                        }
                    }
                }
            },
            text = {
                if (exclusions.isEmpty()) {
                    Text("You haven't ignored any pairs.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(exclusions) { exclusion ->
                            var nameA by remember { mutableStateOf<String?>(null) }
                            var nameB by remember { mutableStateOf<String?>(null) }
                            LaunchedEffect(exclusion) {
                                nameA = viewModel.getFileNameForHash(exclusion.hashA) ?: "(file removed)"
                                nameB = viewModel.getFileNameForHash(exclusion.hashB) ?: "(file removed)"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(nameA ?: "Loading…", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("↔ " + (nameB ?: "Loading…"), style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.removeExclusion(exclusion) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = Danger, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExclusionsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
