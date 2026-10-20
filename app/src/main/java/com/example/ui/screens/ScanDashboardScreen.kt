package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RecycleBinEntity
import com.example.model.AudioFile
import com.example.model.DuplicateGroup
import com.example.model.ScanConfig
import com.example.model.ScanProgress
import com.example.ui.components.DuplicateGroupCard
import com.example.ui.components.ScanProgressHeader
import com.example.ui.components.StoragePermissionCard
import com.example.util.PermissionUtils
import androidx.compose.ui.platform.LocalContext
import com.example.ui.theme.EmeraldKeep
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.PrimaryIndigo

@Composable
fun ScanDashboardScreen(
    scanConfig: ScanConfig,
    scanProgress: ScanProgress,
    duplicateGroups: List<DuplicateGroup>,
    recycleBinItems: List<RecycleBinEntity>,
    onUpdateConfig: (ScanConfig) -> Unit,
    onStartScan: () -> Unit,
    onAutoCleanAll: () -> Unit,
    onCompareGroup: (DuplicateGroup) -> Unit,
    onQuickDelete: (DuplicateGroup, AudioFile, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasStoragePermission = PermissionUtils.hasStoragePermission(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!hasStoragePermission && !scanConfig.includeDemoTracks) {
            StoragePermissionCard(
                onPermissionGranted = onStartScan,
                onEnableDemoMode = {
                    onUpdateConfig(scanConfig.copy(includeDemoTracks = true))
                    onStartScan()
                }
            )
        }

        // Top Real-Time Scanner Header
        ScanProgressHeader(
            progress = scanProgress,
            onStartScan = onStartScan,
            onAutoCleanAll = onAutoCleanAll
        )

        // Storage Space Recovered Dashboard Component
        StorageRecoveredDashboardCard(
            recycleBinItems = recycleBinItems,
            scanProgress = scanProgress
        )

        // Scanner Filter Options Component (Folder, Min Size, Audio Formats)
        ScannerFiltersCard(
            scanConfig = scanConfig,
            onUpdateConfig = onUpdateConfig,
            onStartScan = onStartScan
        )

        // Duplicate Detection Strategies Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("detection_strategies_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Strategies",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Detection Strategy Rules",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // Strategy 1: Filename Matching
                StrategyToggleItem(
                    title = "Filename & Numbered Suffixes",
                    subtitle = "Matches 'vardan.mp3' with 'vardan(1).mp3', 'song (copy)', or numbered variations.",
                    icon = Icons.Default.Folder,
                    checked = scanConfig.enableFilenameMatching,
                    onCheckedChange = {
                        onUpdateConfig(scanConfig.copy(enableFilenameMatching = it))
                        onStartScan()
                    },
                    testTag = "toggle_filename_matching"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Strategy 2: ID3 Metadata Matching
                StrategyToggleItem(
                    title = "Embedded ID3 Metadata",
                    subtitle = "Compares Title, Artist, Album, and Duration embedded tags.",
                    icon = Icons.Default.Sell,
                    checked = scanConfig.enableMetadataMatching,
                    onCheckedChange = {
                        onUpdateConfig(scanConfig.copy(enableMetadataMatching = it))
                        onStartScan()
                    },
                    testTag = "toggle_metadata_matching"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Strategy 3: SHA-256 Audio Content Hashing
                StrategyToggleItem(
                    title = "Content Hash Fingerprinting",
                    subtitle = "Catches exact bit-for-bit duplicate audio data even if names/tags differ.",
                    icon = Icons.Default.Fingerprint,
                    checked = scanConfig.enableContentHash,
                    onCheckedChange = {
                        onUpdateConfig(scanConfig.copy(enableContentHash = it))
                        onStartScan()
                    },
                    testTag = "toggle_content_hash"
                )
            }
        }

        // Preview of Top Duplicate Groups
        if (duplicateGroups.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Detected Duplicate Groups",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "${duplicateGroups.size} Groups",
                    fontSize = 12.sp,
                    color = PrimaryCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }

            duplicateGroups.take(5).forEach { group ->
                DuplicateGroupCard(
                    group = group,
                    onCompare = { onCompareGroup(group) },
                    onQuickDeleteDuplicate = { audioFile, permanent -> onQuickDelete(group, audioFile, permanent) }
                )
            }
        }
    }
}

@Composable
fun StorageRecoveredDashboardCard(
    recycleBinItems: List<RecycleBinEntity>,
    scanProgress: ScanProgress,
    modifier: Modifier = Modifier
) {
    val totalRecoveredBytes = recycleBinItems.sumOf { it.sizeBytes }
    val formattedRecovered = formatBytes(totalRecoveredBytes)
    val formattedReclaimable = formatBytes(scanProgress.reclaimableSizeBytes)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("storage_recovered_dashboard_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EmeraldKeep.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = "Recovered Storage",
                            tint = EmeraldKeep,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Storage Space Recovered",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${recycleBinItems.size} duplicate audio files cleaned",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Space Recovered Big Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EmeraldKeep.copy(alpha = 0.08f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Total Storage Reclaimed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedRecovered,
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp,
                        color = EmeraldKeep,
                        modifier = Modifier.testTag("recovered_storage_value_text")
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Pending Duplicates",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedReclaimable,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryCyan
                    )
                }
            }

            // Audio Formats Recovered Breakdown
            if (recycleBinItems.isNotEmpty()) {
                val formatCounts = recycleBinItems.groupBy { it.format.uppercase() }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    formatCounts.forEach { (format, items) ->
                        val sizeSum = formatBytes(items.sumOf { it.sizeBytes })
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$format: ${items.size} ($sizeSum)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerFiltersCard(
    scanConfig: ScanConfig,
    onUpdateConfig: (ScanConfig) -> Unit,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scanner_filters_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter Scanner",
                    tint = PrimaryCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scanner Filter Options",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            // 1. Folder Filter
            Text(
                text = "Target Storage Folder",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            val folders = listOf(
                "ALL" to "All Folders",
                "Music" to "Music",
                "Download" to "Downloads",
                "WhatsApp Audio" to "WhatsApp",
                "Podcasts" to "Podcasts"
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                folders.forEach { (folderKey, label) ->
                    val isSelected = scanConfig.folderFilter.equals(folderKey, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onUpdateConfig(scanConfig.copy(folderFilter = folderKey))
                            onStartScan()
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = label,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryIndigo,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_folder_$folderKey")
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 2. Minimum File Size Filter
            Text(
                text = "Minimum Audio File Size",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )
            val minSizes = listOf(
                0L to "Any Size",
                1_048_576L to "> 1 MB",
                5_242_880L to "> 5 MB",
                10_485_760L to "> 10 MB"
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                minSizes.forEach { (sizeBytes, label) ->
                    val isSelected = scanConfig.minSizeBytes == sizeBytes
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onUpdateConfig(scanConfig.copy(minSizeBytes = sizeBytes))
                            onStartScan()
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Straighten,
                                contentDescription = label,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryIndigo,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_size_$sizeBytes")
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 3. Audio Format Filter (FLAC, WAV, MP3, M4A)
            Text(
                text = "Specific Audio Formats",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )
            val formats = listOf(
                "ALL" to "All Formats",
                "FLAC" to "FLAC (Hi-Res)",
                "WAV" to "WAV (Uncompressed)",
                "MP3" to "MP3",
                "M4A" to "M4A/AAC"
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                formats.forEach { (formatKey, label) ->
                    val isSelected = scanConfig.formatFilter.equals(formatKey, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onUpdateConfig(scanConfig.copy(formatFilter = formatKey))
                            onStartScan()
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.HighQuality,
                                contentDescription = label,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryIndigo,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_format_$formatKey")
                    )
                }
            }
        }
    }
}

@Composable
private fun StrategyToggleItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = PrimaryCyan,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryIndigo,
                checkedTrackColor = PrimaryIndigo.copy(alpha = 0.3f)
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1000) {
        String.format("%.2f GB", mb / 1024.0)
    } else {
        String.format("%.1f MB", mb)
    }
}
