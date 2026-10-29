package com.example.ui.storage

import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.Formatters
import com.example.core.StorageAccess
import com.example.data.storage.StorageNode
import com.example.data.storage.StorageSort
import com.example.data.storage.listing
import com.example.di.LocalAppContainer
import com.example.di.viewModelFactory
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StorageScreen(
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StorageViewModel = run {
        val c = LocalAppContainer.current
        viewModel(factory = viewModelFactory {
            StorageViewModel(
                storageAnalyzer = c.storageAnalyzer,
                recycleBinManager = c.recycleBinManager
            )
        })
    }
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }

    val hasFullAccess = StorageAccess.mode(context) == StorageAccess.Mode.FULL

    BackHandler(enabled = uiState.pathStack.size > 1) {
        viewModel.navigateUp()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.storage_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Rounded.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Size (Largest first)") },
                                onClick = {
                                    viewModel.setSort(StorageSort.SIZE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (A-Z)") },
                                onClick = {
                                    viewModel.setSort(StorageSort.NAME)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Date (Newest first)") },
                                onClick = {
                                    viewModel.setSort(StorageSort.DATE)
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.analyze() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                }
            }

            if (!hasFullAccess) {
                PermissionCard(
                    title = stringResource(R.string.perm_all_files_title),
                    text = "Storage Analyzer needs All Files Access to calculate folder sizes and reveal what is occupying disk space.",
                    buttonLabel = stringResource(R.string.perm_all_files_button),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.startActivity(StorageAccess.allFilesIntent(context))
                        }
                    },
                    modifier = Modifier.padding(Spacing.md)
                )
            } else if (uiState.isAnalyzing) {
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
                        text = "Analyzing storage directory sizes…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "${uiState.analyzedFilesCount} files indexed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = uiState.currentAnalyzingDir,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (uiState.index == null) {
                EmptyState(
                    icon = Icons.Rounded.PieChart,
                    title = "Analyze Your Storage",
                    message = "Inspect folder-by-folder usage like a disk-usage explorer to find space hogs.",
                    actionLabel = "Analyze Storage",
                    onAction = { viewModel.analyze() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val index = uiState.index!!
                val statFs = StatFs(Environment.getExternalStorageDirectory().path)
                val totalBytes = statFs.totalBytes
                val freeBytes = statFs.availableBytes

                // Summary Card
                SafaiCard(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    tonal = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Internal Storage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${Formatters.formatBytes(totalBytes - freeBytes)} used of ${Formatters.formatBytes(totalBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${Formatters.formatBytes(freeBytes)} free",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Breadcrumb
                if (uiState.pathStack.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(uiState.pathStack) { path ->
                            val label = if (path in index.roots) "Internal" else path.substringAfterLast('/')
                            val isCurrent = path == uiState.currentPath
                            AssistChip(
                                onClick = { viewModel.navigateToBreadcrumb(path) },
                                label = { Text(label, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(12.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                        }
                    }
                }

                // Node Listing
                val nodes = index.listing(uiState.currentPath, uiState.sort)

                if (nodes.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("This folder is empty.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        contentPadding = PaddingValues(bottom = if (uiState.isSelectionMode) 90.dp else 16.dp)
                    ) {
                        items(nodes, key = { it.path }) { node ->
                            val isSelected = node.path in uiState.selectedNodes
                            StorageNodeRow(
                                node = node,
                                isSelected = isSelected,
                                onSelect = { viewModel.toggleNodeSelection(node.path) },
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleNodeSelection(node.path)
                                    } else if (node.isDir) {
                                        viewModel.navigateTo(node.path)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleNodeSelection(node.path)
                                }
                            )
                        }

                        item {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = stringResource(R.string.storage_folder_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.xs)
                            )
                            Spacer(Modifier.height(Spacing.xl))
                        }
                    }
                }
            }
        }

        // Selection Bottom Bar
        if (uiState.isSelectionMode) {
            val selectedNodes = uiState.index?.listing(uiState.currentPath, StorageSort.SIZE)?.filter { it.path in uiState.selectedNodes } ?: emptyList()
            val totalBytes = selectedNodes.sumOf { it.bytes }
            SelectionBottomBar(
                count = uiState.selectedNodes.size,
                size = totalBytes,
                onClear = { viewModel.clearSelection() },
                onDelete = {
                    viewModel.moveSelectedToTrash { count ->
                        onShowSnackbar("Moved $count items to Recycle Bin")
                    }
                },
                label = "To Trash",
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorageNodeRow(
    node: StorageNode,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val barWidthFraction by animateFloatAsState(
        targetValue = node.fractionOfParent.coerceIn(0f, 1f),
        label = "storage_bar"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                IconBadge(
                    icon = if (node.isDir) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                    tint = if (node.isDir) CatArchive else CatDocument,
                    size = 40.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (node.isDir) "${Formatters.formatBytes(node.bytes)} · ${node.fileCount} files"
                        else "${Formatters.formatBytes(node.bytes)} · ${Formatters.formatDate(node.modifiedMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${(node.fractionOfParent * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(Spacing.xs))

            // Proportional Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barWidthFraction)
                        .fillMaxHeight()
                        .background(Teal400)
                )
            }
        }
    }
}
