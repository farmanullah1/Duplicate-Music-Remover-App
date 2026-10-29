package com.example.ui.recyclebin

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.Formatters
import com.example.data.db.RecycleBinEntity
import com.example.di.LocalAppContainer
import com.example.di.viewModelFactory
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecycleBinScreen(
    onNavigateBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecycleBinViewModel = run {
        val c = LocalAppContainer.current
        viewModel(factory = viewModelFactory {
            RecycleBinViewModel(
                recycleBinDao = c.database.recycleBinDao(),
                recycleBinManager = c.recycleBinManager,
                settingsRepository = c.settingsRepository
            )
        })
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showEmptyConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.recycle_bin_title),
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
                    if (uiState.items.isNotEmpty()) {
                        TextButton(onClick = { showEmptyConfirm = true }) {
                            Text("Empty Bin", color = Danger, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.DeleteOutline,
                    title = stringResource(R.string.recycle_bin_empty_title),
                    message = stringResource(R.string.recycle_bin_empty_desc),
                    actionLabel = "Back to Home",
                    onAction = onNavigateBack,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Header Card
                    SafaiCard(tonal = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "${uiState.totalCount} files · ${Formatters.formatBytes(uiState.totalBytes)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Auto-deleted after ${uiState.retentionDays} days",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconBadge(
                                icon = Icons.Rounded.DeleteOutline,
                                tint = Danger,
                                size = 48.dp
                            )
                        }
                    }

                    // Selection Bar / Action Hint
                    if (uiState.isSelectionMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${uiState.selectedIds.size} selected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Button(
                                    onClick = {
                                        viewModel.restore(uiState.selectedIds.toList()) { res ->
                                            onShowSnackbar("Restored ${res.restored} files (${res.failed} failed)")
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(Sizes.iconSm))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Restore")
                                }

                                DangerButton(
                                    text = "Delete Forever",
                                    onClick = {
                                        viewModel.deletePermanent(uiState.selectedIds.toList()) {
                                            onShowSnackbar("Deleted selected files permanently")
                                        }
                                    },
                                    modifier = Modifier.height(40.dp)
                                )
                            }
                        }
                    }

                    // Items List
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            val isSelected = item.id in uiState.selectedIds
                            RecycleBinItemRow(
                                item = item,
                                retentionDays = uiState.retentionDays,
                                isSelected = isSelected,
                                isSelectionMode = uiState.isSelectionMode,
                                onSelect = { viewModel.toggleSelection(item.id) },
                                onRestore = {
                                    viewModel.restore(listOf(item.id)) { res ->
                                        onShowSnackbar("Restored ${item.name}")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = Danger) },
            title = { Text("Empty Recycle Bin?") },
            text = {
                Text(
                    "Permanently delete all ${uiState.totalCount} files (${Formatters.formatBytes(uiState.totalBytes)})? This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyConfirm = false
                        viewModel.emptyBin {
                            onShowSnackbar("Recycle Bin emptied")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Empty Bin Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecycleBinItemRow(
    item: RecycleBinEntity,
    retentionDays: Int,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelect: () -> Unit,
    onRestore: () -> Unit
) {
    val daysAgo = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - item.deletedAt).toInt()
    val daysLeft = (retentionDays - daysAgo).coerceAtLeast(0)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { if (isSelectionMode) onSelect() },
                onLongClick = onSelect
            )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onSelect() })
            }

            val cat = runCatching { MediaCategory.valueOf(item.category) }.getOrDefault(MediaCategory.OTHER)
            val mockMediaFile = MediaFile(
                path = item.trashPath,
                name = item.name,
                sizeBytes = item.sizeBytes,
                modifiedMs = item.deletedAt,
                category = cat,
                mime = item.mime,
                extension = item.name.substringAfterLast('.', "")
            )

            ThumbnailImage(file = mockMediaFile, size = 52.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.originalPath.substringBeforeLast('/'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Formatters.formatBytes(item.sizeBytes)} · $daysLeft days left",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (daysLeft <= 3) Warning else MaterialTheme.colorScheme.primary
                )
            }

            if (!isSelectionMode) {
                IconButton(onClick = onRestore) {
                    Icon(
                        imageVector = Icons.Rounded.RestoreFromTrash,
                        contentDescription = "Restore",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
