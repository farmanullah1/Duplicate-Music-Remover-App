package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButton
import com.example.model.AudioFile
import com.example.model.DuplicateGroup
import com.example.model.MatchType
import com.example.ui.theme.CrimsonDelete
import com.example.ui.theme.EmeraldKeep
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.PrimaryIndigo

@Composable
fun DuplicateGroupCard(
    group: DuplicateGroup,
    onCompare: () -> Unit,
    onQuickDeleteDuplicate: (AudioFile, Boolean) -> Unit,
    selectedFileIds: Set<String> = emptySet(),
    onToggleSelectFile: ((AudioFile) -> Unit)? = null,
    onToggleGroupSelection: (() -> Unit)? = null,
    onEditMetadata: ((AudioFile) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var pendingDeleteFile by remember { mutableStateOf<AudioFile?>(null) }

    pendingDeleteFile?.let { targetFile ->
        DeleteConfirmationDialog(
            audioFile = targetFile,
            onMoveToRecycleBin = {
                onQuickDeleteDuplicate(targetFile, false)
                pendingDeleteFile = null
            },
            onDeletePermanently = {
                onQuickDeleteDuplicate(targetFile, true)
                pendingDeleteFile = null
            },
            onDismiss = { pendingDeleteFile = null }
        )
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("duplicate_group_${group.groupId}"),
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
            // Header: Title & Match Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.groupTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Surface(
                            color = when (group.matchType) {
                                MatchType.EXACT_HASH -> PrimaryCyan.copy(alpha = 0.15f)
                                MatchType.FILENAME_VARIATION -> PrimaryIndigo.copy(alpha = 0.15f)
                                MatchType.METADATA_MATCH -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = group.matchType.label,
                                color = when (group.matchType) {
                                    MatchType.EXACT_HASH -> PrimaryCyan
                                    MatchType.FILENAME_VARIATION -> PrimaryIndigo
                                    MatchType.METADATA_MATCH -> MaterialTheme.colorScheme.tertiary
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${group.files.size} Versions Found",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Potential Storage Savings Pill
                val savingsKb = group.potentialSavingsBytes / 1024.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Potential Storage Savings Pill
                    val savingsKb = group.potentialSavingsBytes / 1024.0
                    val savingsMb = savingsKb / 1024.0
                    val savingsFormatted = if (savingsMb >= 1.0) String.format("%.1f MB", savingsMb) else String.format("%.0f KB", savingsKb)

                    Surface(
                        color = EmeraldKeep.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = "Savings",
                                tint = EmeraldKeep,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Save $savingsFormatted",
                                color = EmeraldKeep,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (onToggleGroupSelection != null) {
                        val duplicateCandidates = group.files.filter { it.id != group.autoRecommendedKeepId }
                        val allInGroupSelected = duplicateCandidates.isNotEmpty() && duplicateCandidates.all { it.id in selectedFileIds }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onToggleGroupSelection,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("select_group_${group.groupId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Toggle Group Selection",
                                tint = if (allInGroupSelected) CrimsonDelete else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // File Summaries List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.files.forEach { audioFile ->
                    val isRecommended = audioFile.id == group.autoRecommendedKeepId
                    val isSelected = audioFile.id in selectedFileIds

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isSelected -> CrimsonDelete.copy(alpha = 0.12f)
                                    isRecommended -> EmeraldKeep.copy(alpha = 0.08f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    when {
                                        isSelected -> CrimsonDelete.copy(alpha = 0.6f)
                                        isRecommended -> EmeraldKeep.copy(alpha = 0.35f)
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    }
                                ),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = onToggleSelectFile != null && !isRecommended) {
                                onToggleSelectFile?.invoke(audioFile)
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRecommended) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = EmeraldKeep.copy(alpha = 0.18f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "Original Keep",
                                        tint = EmeraldKeep,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelectFile?.invoke(audioFile) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = CrimsonDelete,
                                    uncheckedColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("checkbox_duplicate_${audioFile.id}")
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = audioFile.displayName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isRecommended) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = EmeraldKeep.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "KEEP ORIGINAL",
                                            color = EmeraldKeep,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = CrimsonDelete.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "TO DELETE",
                                            color = CrimsonDelete,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${audioFile.format} • ${audioFile.bitrateKbps} kbps • ${audioFile.formattedSize}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (onEditMetadata != null) {
                            IconButton(
                                onClick = { onEditMetadata(audioFile) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("edit_tags_${audioFile.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Tags",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }


            Spacer(modifier = Modifier.height(12.dp))

            // Group CTAs: Side-by-Side Compare + Quick Auto Clean
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCompare,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("compare_group_${group.groupId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Compare,
                        contentDescription = "Side-by-Side",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Side-by-Side Compare", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Quick delete recommended duplicate
                val duplicateToDelete = group.files.find { it.id != group.autoRecommendedKeepId } ?: group.files.last()
                Button(
                    onClick = { pendingDeleteFile = duplicateToDelete },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonDelete.copy(alpha = 0.15f),
                        contentColor = CrimsonDelete
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CrimsonDelete.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("quick_delete_${group.groupId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Quick Clean",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clean Duplicate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
