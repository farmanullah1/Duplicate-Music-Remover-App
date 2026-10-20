package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioFile
import com.example.model.DuplicateGroup
import com.example.model.MatchType
import com.example.ui.components.DuplicateGroupCard
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
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<MatchType?>(null) }
    var selectedSort by remember { mutableStateOf(GroupSortOption.SAVINGS) }

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

    val totalReclaimableFiltered = remember(filteredGroups) {
        filteredGroups.sumOf { group: DuplicateGroup -> group.potentialSavingsBytes }
    }
    val formattedReclaimable = remember(totalReclaimableFiltered) {
        val mb = totalReclaimableFiltered / (1024.0 * 1024.0)
        if (mb >= 1000) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
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
            Column {
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
                Button(
                    onClick = onAutoCleanAll,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldKeep),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("auto_recommend_all_button")
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

        // Filter & Sort Chips Bar
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("All Matches (${duplicateGroups.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryIndigo,
                        selectedLabelColor = Color.White
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == MatchType.FILENAME_VARIATION,
                    onClick = { selectedFilter = MatchType.FILENAME_VARIATION },
                    label = { Text("Filename Variations") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryIndigo,
                        selectedLabelColor = Color.White
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == MatchType.EXACT_HASH,
                    onClick = { selectedFilter = MatchType.EXACT_HASH },
                    label = { Text("Exact Hash Matches") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryCyan,
                        selectedLabelColor = Color.White
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == MatchType.METADATA_MATCH,
                    onClick = { selectedFilter = MatchType.METADATA_MATCH },
                    label = { Text("ID3 Tag Matches") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                        selectedLabelColor = Color.White
                    )
                )
            }
            // Sort Chips
            GroupSortOption.values().forEach { sortOpt ->
                item {
                    FilterChip(
                        selected = selectedSort == sortOpt,
                        onClick = { selectedSort = sortOpt },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = { Text(sortOpt.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldKeep,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
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
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredGroups, key = { it.groupId }) { group ->
                    DuplicateGroupCard(
                        group = group,
                        onCompare = { onCompareGroup(group) },
                        onQuickDeleteDuplicate = { audioFile, permanent -> onQuickDelete(group, audioFile, permanent) }
                    )
                }
            }
        }
    }
}
