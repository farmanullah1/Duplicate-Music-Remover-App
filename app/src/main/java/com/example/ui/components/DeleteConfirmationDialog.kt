package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioFile
import com.example.ui.theme.CrimsonDelete
import com.example.ui.theme.EmeraldKeep

@Composable
fun BatchDeleteConfirmationDialog(
    audioFiles: List<AudioFile>,
    onMoveToRecycleBin: () -> Unit,
    onDeletePermanently: () -> Unit,
    onDismiss: () -> Unit
) {
    if (audioFiles.isEmpty()) return
    if (audioFiles.size == 1) {
        DeleteConfirmationDialog(
            audioFile = audioFiles.first(),
            onMoveToRecycleBin = onMoveToRecycleBin,
            onDeletePermanently = onDeletePermanently,
            onDismiss = onDismiss
        )
        return
    }

    val totalBytes = audioFiles.sumOf { it.sizeBytes }
    val formattedTotalSize = formatBytes(totalBytes)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Files",
                    tint = CrimsonDelete,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Remove ${audioFiles.size} Duplicate Files",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Selected for Removal (${audioFiles.size} files, $formattedTotalSize):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        audioFiles.take(4).forEach { file ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${file.displayName} (${file.formattedSize})",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (audioFiles.size > 4) {
                            Text(
                                text = "+ ${audioFiles.size - 4} more files",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Choose how you would like to remove these ${audioFiles.size} duplicate files from your device:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: Move to Recycle Bin
                Surface(
                    onClick = {
                        onMoveToRecycleBin()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldKeep.copy(alpha = 0.1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_move_to_recycle_bin_option")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Recycle Bin",
                            tint = EmeraldKeep,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Move All to Recycle Bin (Recommended)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = EmeraldKeep
                            )
                            Text(
                                text = "Safely stages ${audioFiles.size} files in trash. Can be restored anytime.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 2: Delete Permanently
                Surface(
                    onClick = {
                        onDeletePermanently()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = CrimsonDelete.copy(alpha = 0.1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_delete_permanently_option")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Permanent Delete",
                            tint = CrimsonDelete,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Delete All Permanently from Device",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = CrimsonDelete
                            )
                            Text(
                                text = "Directly deletes physical files & frees $formattedTotalSize immediately.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_batch_delete_button")
            ) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag("batch_delete_confirmation_dialog")
    )
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.0f KB", kb)
}

@Composable
fun DeleteConfirmationDialog(
    audioFile: AudioFile,
    onMoveToRecycleBin: () -> Unit,
    onDeletePermanently: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete File",
                    tint = CrimsonDelete,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Remove Audio File",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Audio",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = audioFile.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${audioFile.format} • ${audioFile.bitrateKbps} kbps • ${audioFile.formattedSize}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = audioFile.absolutePath,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Text(
                    text = "Choose how you would like to remove this file from your device:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: Move to Recycle Bin
                Surface(
                    onClick = {
                        onMoveToRecycleBin()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldKeep.copy(alpha = 0.1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("move_to_recycle_bin_option")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Recycle Bin",
                            tint = EmeraldKeep,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Move to Recycle Bin (Recommended)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = EmeraldKeep
                            )
                            Text(
                                text = "Safely stages file in trash. Can be restored anytime.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 2: Delete Permanently
                Surface(
                    onClick = {
                        onDeletePermanently()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = CrimsonDelete.copy(alpha = 0.1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_permanently_option")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Permanent Delete",
                            tint = CrimsonDelete,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Delete Permanently from Device",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = CrimsonDelete
                            )
                            Text(
                                text = "Directly deletes physical file & frees storage immediately.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_button")
            ) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag("delete_confirmation_dialog")
    )
}
