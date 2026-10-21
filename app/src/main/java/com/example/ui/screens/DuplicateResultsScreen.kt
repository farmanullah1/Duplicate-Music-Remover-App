package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import com.example.ui.components.MetadataEditorDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.model.AudioFile
import com.example.model.DuplicateGroup
import com.example.model.MatchType
import com.example.model.ScanPhase
import com.example.model.ScanProgress
import com.example.ui.components.BatchDeleteConfirmationDialog
import com.example.ui.components.DuplicateGroupCard
import com.example.ui.theme.CrimsonDelete
import com.example.ui.theme.EmeraldKeep
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.PrimaryIndigo

enum class GroupSortOption(val label: String) {
    SAVINGS("Max Storage Savings"),
    FILE_COUNT("Most Duplicates"),
    TITLE("Group Title")
}

@Composable
fun DuplicateResultsScreen(
    duplicateGroups: List<DuplicateGroup>,
    onCompareGroup: (DuplicateGroup) -> Unit,
    onQuickDelete: (DuplicateGroup, AudioFile, Boolean) -> Unit,
    onAutoCleanAll: () -> Unit,
    scanProgress: ScanProgress = ScanProgress(),
    onDeleteSelected: ((List<AudioFile>, Boolean) -> Unit)? = null,
    onUpdateMetadata: ((AudioFile) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<MatchType?>(null) }
    var selectedSort by remember { mutableStateOf(GroupSortOption.SAVINGS) }
    var selectedFileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var fileForMetadataEdit by remember { mutableStateOf<AudioFile?>(null) }

    val filteredGroups = remember(duplicateGroups, searchQuery, selectedFilter, selectedSort) {
        duplicateGroups
            .filter { group ->
                if (selectedFilter != null && group.matchType != selectedFilter) return@filter false
                if (searchQuery.isBlank()) return@filter true
                val query = searchQuery.trim().lowercase()
                group.groupTitle.lowercase().contains(query) ||
                        group.files.any {
                            it.displayName.lowercase().contains(query) ||
                                    it.artist.lowercase().contains(query) ||
                                    it.title.lowercase().contains(query)
                        }
            }
            .sortedWith { g1, g2 ->
                when (selectedSort) {
                    GroupSortOption.SAVINGS -> g2.potentialSavingsBytes.compareTo(g1.potentialSavingsBytes)
                    GroupSortOption.FILE_COUNT -> g2.files.size.compareTo(g1.files.size)
                    GroupSortOption.TITLE -> g1.groupTitle.compareTo(g2.groupTitle, ignoreCase = true)
                }
            }
    }

    // All confirmed duplicates across filtered groups (all candidate files except the recommended keep version)
    val allConfirmedDuplicates = remember(filteredGroups) {
        filteredGroups.flatMap { g ->
            val keepId = g.autoRecommendedKeepId ?: g.files.firstOrNull()?.id
            g.files.filter { it.id != keepId }
        }
    }

    val isAllSelected = remember(allConfirmedDuplicates, selectedFileIds) {
        allConfirmedDuplicates.isNotEmpty() && allConfirmedDuplicates.all { it.id in selectedFileIds }
    }

    // Resolve AudioFile instances for selected files
    val selectedFiles = remember(selectedFileIds, duplicateGroups) {
        duplicateGroups.flatMap { it.files }
            .filter { it.id in selectedFileIds }
            .distinctBy { it.id }
    }

    val totalSelectedSavings = remember(selectedFiles) {
        selectedFiles.sumOf { it.sizeBytes }
    }
    val formattedSelectedSavings = remember(totalSelectedSavings) {
        val mb = totalSelectedSavings / (1024.0 * 1024.0)
        if (mb >= 1000) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }

    val totalReclaimableFiltered = remember(filteredGroups) {
        filteredGroups.sumOf { group: DuplicateGroup -> group.potentialSavingsBytes }
    }
    val formattedReclaimable = remember(totalReclaimableFiltered) {
        val mb = totalReclaimableFiltered / (1024.0 * 1024.0)
        if (mb >= 1000) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }

    val isScanning = scanProgress.phase != ScanPhase.IDLE && scanProgress.phase != ScanPhase.COMPLETED

    // Batch Delete Dialog
    if (showBatchDeleteDialog && selectedFiles.isNotEmpty()) {
        BatchDeleteConfirmationDialog(
            audioFiles = selectedFiles,
            onMoveToRecycleBin = {
                onDeleteSelected?.invoke(selectedFiles, false)
                selectedFileIds = emptySet()
                showBatchDeleteDialog = false
            },
            onDeletePermanently = {
                onDeleteSelected?.invoke(selectedFiles, true)
                selectedFileIds = emptySet()
                showBatchDeleteDialog = false
            },
            onDismiss = { showBatchDeleteDialog = false }
        )
    }

    // Smart preset selectors
    val selectLowerBitrate = {
        val newSet = mutableSetOf<String>()
        filteredGroups.forEach { group ->
            val bestFile = group.files.maxByOrNull { it.bitrateKbps }
            group.files.filter { it.id != bestFile?.id }.forEach { newSet.add(it.id) }
        }
        selectedFileIds = newSet
    }

    val selectPurgeCopiesOldest = {
        val newSet = mutableSetOf<String>()
        filteredGroups.forEach { group ->
            val oldest = group.files.minByOrNull { it.dateModifiedMs }
            group.files.filter { it.id != oldest?.id }.forEach { newSet.add(it.id) }
        }
        selectedFileIds = newSet
    }

    val selectKeepNewest = {
        val newSet = mutableSetOf<String>()
        filteredGroups.forEach { group ->
            val newest = group.files.maxByOrNull { it.dateModifiedMs }
            group.files.filter { it.id != newest?.id }.forEach { newSet.add(it.id) }
        }
        selectedFileIds = newSet
    }

    val selectCompleteTags = {
        val newSet = mutableSetOf<String>()
        filteredGroups.forEach { group ->
            val mostComplete = group.files.maxByOrNull {
                (if (it.title.isNotBlank()) 1 else 0) +
                (if (it.artist.isNotBlank() && it.artist != "Unknown Artist") 1 else 0) +
                (if (it.album.isNotBlank() && it.album != "Unknown Album") 1 else 0)
            }
            group.files.filter { it.id != mostComplete?.id }.forEach { newSet.add(it.id) }
        }
        selectedFileIds = newSet
    }

    // Metadata Editor Dialog
    fileForMetadataEdit?.let { file ->
        MetadataEditorDialog(
            audioFile = file,
            onSave = { updated ->
                onUpdateMetadata?.invoke(updated)
                fileForMetadataEdit = null
            },
            onDismiss = { fileForMetadataEdit = null }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1000.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Top Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Duplicates Overview",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "${filteredGroups.size} Duplicate Sets ($formattedReclaimable Reclaimable)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (duplicateGroups.isNotEmpty()) {
                    if (selectedFileIds.isNotEmpty()) {
                        Button(
                            onClick = { showBatchDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonDelete),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CrimsonDelete.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("delete_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Delete Selected (${selectedFileIds.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = onAutoCleanAll,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldKeep),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, EmeraldKeep.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("auto_recommend_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "Auto Clean",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto Clean All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Visual Linear Scan Progress Indicator (Active during duplicate detection)
            if (isScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_progress_indicator_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = PrimaryIndigo.copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = PrimaryCyan,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (scanProgress.phase) {
                                        ScanPhase.SCANNING_STORAGE -> "Scanning storage for music files..."
                                        ScanPhase.ANALYZING_METADATA -> "Analyzing audio tags & bitrates..."
                                        ScanPhase.COMPUTING_HASHES -> "Computing acoustic & content hashes..."
                                        ScanPhase.GROUPING_DUPLICATES -> "Grouping duplicate tracks..."
                                        else -> "Detecting duplicate tracks..."
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            val pct = (scanProgress.progressFraction * 100).toInt()
                            Text(
                                text = "$pct%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = PrimaryCyan
                            )
                        }

                        if (scanProgress.totalCount > 0) {
                            Text(
                                text = "${scanProgress.processedCount} of ${scanProgress.totalCount} audio files scanned",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        if (scanProgress.currentFile.isNotEmpty()) {
                            Text(
                                text = "Scanning: ${scanProgress.currentFile}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Prominent Visual Linear Progress Bar
                        LinearProgressIndicator(
                            progress = { scanProgress.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .testTag("duplicates_linear_progress_bar"),
                            color = PrimaryCyan,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search duplicate songs by name, artist, format...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = PrimaryCyan,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryCyan,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_duplicates_input")
            )

            // Select All & Selection Controls Bar
            if (allConfirmedDuplicates.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedFileIds = if (isAllSelected) {
                                emptySet()
                            } else {
                                allConfirmedDuplicates.map { it.id }.toSet()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isAllSelected) PrimaryCyan else MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isAllSelected) PrimaryCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("select_all_button")
                    ) {
                        Icon(
                            imageVector = if (isAllSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (isAllSelected) "Deselect All" else "Select All",
                            tint = if (isAllSelected) PrimaryCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isAllSelected) "Deselect All" else "Select All Duplicates (${allConfirmedDuplicates.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (selectedFileIds.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CrimsonDelete.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${selectedFileIds.size} Selected • $formattedSelectedSavings",
                                color = CrimsonDelete,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Smart Batch Preset Actions
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 4.dp)
                ) {
                    item {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectLowerBitrate() },
                            color = PrimaryCyan.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.HighQuality, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Keep Best Bitrate", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                            }
                        }
                    }
                    item {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectPurgeCopiesOldest() },
                            color = PrimaryIndigo.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Purge Copies (Keep Oldest)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryIndigo)
                            }
                        }
                    }
                    item {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectCompleteTags() },
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Keep Complete Tags", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                    item {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectKeepNewest() },
                            color = EmeraldKeep.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, EmeraldKeep.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = EmeraldKeep, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Keep Newest", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldKeep)
                            }
                        }
                    }
                    if (selectedFileIds.isNotEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val candidateIds = allConfirmedDuplicates.map { it.id }.toSet()
                                        selectedFileIds = candidateIds - selectedFileIds
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Invert Selection", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Filter & Sort Chips Bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    val isSelected = selectedFilter == null
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = null },
                        label = { Text("All Matches (${duplicateGroups.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryIndigo,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                }
                item {
                    val isSelected = selectedFilter == MatchType.FILENAME_VARIATION
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = MatchType.FILENAME_VARIATION },
                        label = { Text("Filename Variations") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryIndigo,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                }
                item {
                    val isSelected = selectedFilter == MatchType.EXACT_HASH
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = MatchType.EXACT_HASH },
                        label = { Text("Exact Hash Matches") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryCyan,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                }
                item {
                    val isSelected = selectedFilter == MatchType.METADATA_MATCH
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = MatchType.METADATA_MATCH },
                        label = { Text("ID3 Tag Matches") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                }
                // Sort Chips
                GroupSortOption.values().forEach { sortOpt ->
                    item {
                        val isSelected = selectedSort == sortOpt
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSort = sortOpt },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = { Text(sortOpt.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldKeep,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicOff,
                            contentDescription = "No Duplicates",
                            tint = EmeraldKeep,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No Matching Duplicates" else "No Duplicates Found!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty())
                                "No duplicate audio groups match '$searchQuery'. Try clearing your search query."
                            else
                                "Your music library is clean and optimized. Check back after adding new music files.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = if (selectedFileIds.isNotEmpty()) 80.dp else 24.dp)
                ) {
                    items(filteredGroups, key = { it.groupId }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            selectedFileIds = selectedFileIds,
                            onToggleSelectFile = { file ->
                                selectedFileIds = if (file.id in selectedFileIds) {
                                    selectedFileIds - file.id
                                } else {
                                    selectedFileIds + file.id
                                }
                            },
                            onToggleGroupSelection = {
                                val groupDuplicateIds = group.files
                                    .filter { it.id != group.autoRecommendedKeepId }
                                    .map { it.id }
                                val allInGroupSelected = groupDuplicateIds.isNotEmpty() &&
                                        groupDuplicateIds.all { it in selectedFileIds }
                                selectedFileIds = if (allInGroupSelected) {
                                    selectedFileIds - groupDuplicateIds.toSet()
                                } else {
                                    selectedFileIds + groupDuplicateIds.toSet()
                                }
                            },
                            onCompare = { onCompareGroup(group) },
                            onQuickDeleteDuplicate = { audioFile, permanent -> onQuickDelete(group, audioFile, permanent) },
                            onEditMetadata = { fileForMetadataEdit = it }
                        )
                    }
                }
            }
        }

        // Floating Bottom Multi-Delete Banner
        AnimatedVisibility(
            visible = selectedFileIds.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                border = BorderStroke(1.dp, CrimsonDelete.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 700.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedFileIds.size} Duplicate Tracks Selected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Free up $formattedSelectedSavings of device space",
                            color = EmeraldKeep,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selectedFileIds = emptySet() }) {
                            Text("Clear", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = { showBatchDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonDelete),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("floating_delete_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Selected", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
