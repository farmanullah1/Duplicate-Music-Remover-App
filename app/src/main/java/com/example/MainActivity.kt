package com.example

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import com.example.ui.screens.AiMusicStudioScreen
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.screens.DuplicateResultsScreen
import com.example.ui.screens.RecycleBinScreen
import com.example.ui.screens.ScanDashboardScreen
import com.example.ui.components.SideBySideCompareDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Alignment
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DuplicateMusicTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DuplicateMusicTheme {
                val permissionsToRequest = remember {
                    val list = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        list.add(Manifest.permission.READ_MEDIA_AUDIO)
                        list.add(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    list
                }

                val permissionsState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

                LaunchedEffect(Unit) {
                    if (!permissionsState.allPermissionsGranted) {
                        permissionsState.launchMultiplePermissionRequest()
                    }
                }

                DuplicateMusicApp(
                    viewModel = viewModel,
                    onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateMusicApp(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    val scanConfig by viewModel.scanConfig.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val duplicateGroups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val recycleBinItems by viewModel.recycleBinItems.collectAsStateWithLifecycle()
    val allScannedFiles by viewModel.allScannedFiles.collectAsStateWithLifecycle()
    val selectedGroupForCompare by viewModel.selectedGroupForCompare.collectAsStateWithLifecycle()
    val playerState by viewModel.audioPlayerController.playerState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val pendingIntentSender by viewModel.pendingIntentSender.collectAsStateWithLifecycle()

    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.clearPendingIntentSender()
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeleteIntentSenderSuccess()
        } else {
            viewModel.onDeleteIntentSenderCancelled()
        }
    }

    LaunchedEffect(pendingIntentSender) {
        pendingIntentSender?.let { request ->
            deleteRequestLauncher.launch(request)
        }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight(),
                    header = {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                ) {
                    NavigationRailItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Dashboard,
                                contentDescription = "Dashboard"
                            )
                        },
                        label = { Text("Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_scan_dashboard")
                    )

                    NavigationRailItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (duplicateGroups.isNotEmpty()) {
                                        Badge { Text("${duplicateGroups.size}") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CopyAll,
                                    contentDescription = "Duplicates"
                                )
                            }
                        },
                        label = { Text("Duplicates", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_duplicates")
                    )

                    NavigationRailItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Studio"
                            )
                        },
                        label = { Text("AI Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_ai_studio")
                    )

                    NavigationRailItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (recycleBinItems.isNotEmpty()) {
                                        Badge { Text("${recycleBinItems.size}") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Recycle Bin"
                                )
                            }
                        },
                        label = { Text("Recycle Bin", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_recycle_bin")
                    )

                    NavigationRailItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_settings")
                    )
                }

                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1100.dp)
                    ) {
                        AppScreenContent(
                            selectedTab = selectedTab,
                            viewModel = viewModel,
                            scanConfig = scanConfig,
                            scanProgress = scanProgress,
                            duplicateGroups = duplicateGroups,
                            recycleBinItems = recycleBinItems,
                            allScannedFiles = allScannedFiles,
                            onRequestPermissions = onRequestPermissions
                        )

                        // Snackbar Host for Wide Layout
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        )
                    }
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.border(
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        )
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Dashboard,
                                    contentDescription = "Dashboard"
                                )
                            },
                            label = { Text("Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("tab_scan_dashboard")
                        )

                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (duplicateGroups.isNotEmpty()) {
                                            Badge { Text("${duplicateGroups.size}") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CopyAll,
                                        contentDescription = "Duplicates"
                                    )
                                }
                            },
                            label = { Text("Duplicates", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("tab_duplicates")
                        )

                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Studio"
                                )
                            },
                            label = { Text("AI Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("tab_ai_studio")
                        )

                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (recycleBinItems.isNotEmpty()) {
                                            Badge { Text("${recycleBinItems.size}") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Recycle Bin"
                                    )
                                }
                            },
                            label = { Text("Recycle Bin", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("tab_recycle_bin")
                        )

                        NavigationBarItem(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("tab_settings")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AppScreenContent(
                        selectedTab = selectedTab,
                        viewModel = viewModel,
                        scanConfig = scanConfig,
                        scanProgress = scanProgress,
                        duplicateGroups = duplicateGroups,
                        recycleBinItems = recycleBinItems,
                        allScannedFiles = allScannedFiles,
                        onRequestPermissions = onRequestPermissions
                    )
                }
            }
        }

        // Side-by-Side Dual Pane Modal (Accessible in both layouts)
        selectedGroupForCompare?.let { group ->
            SideBySideCompareDialog(
                group = group,
                playerState = playerState,
                onTogglePlay = { viewModel.audioPlayerController.togglePlayPause(it) },
                onPlayCompareDual = { fileA, fileB, target ->
                    viewModel.audioPlayerController.playCompareDual(fileA, fileB, target)
                },
                onPlayGroupSynchronized = { files, target ->
                    viewModel.audioPlayerController.playGroupSynchronized(files, target)
                },
                onSeek = { viewModel.audioPlayerController.seekTo(it) },
                onDeleteFile = { audioFile, permanent -> viewModel.deleteFileFromGroup(group, audioFile, permanent) },
                onDeleteBatchFiles = { audioFiles, permanent -> viewModel.deleteBatchFromGroup(group, audioFiles, permanent) },
                onKeepBoth = { viewModel.markKeepBoth(group) },
                onEditMetadata = viewModel::updateAudioFileMetadata,
                onDismiss = viewModel::closeCompareDialog
            )
        }
    }
}

@Composable
private fun AppScreenContent(
    selectedTab: Int,
    viewModel: MainViewModel,
    scanConfig: com.example.model.ScanConfig,
    scanProgress: com.example.model.ScanProgress,
    duplicateGroups: List<com.example.model.DuplicateGroup>,
    recycleBinItems: List<com.example.data.local.RecycleBinEntity>,
    allScannedFiles: List<com.example.model.AudioFile>,
    onRequestPermissions: () -> Unit
) {
    when (selectedTab) {
        0 -> ScanDashboardScreen(
            scanConfig = scanConfig,
            scanProgress = scanProgress,
            duplicateGroups = duplicateGroups,
            recycleBinItems = recycleBinItems,
            allScannedFiles = allScannedFiles,
            onUpdateConfig = viewModel::updateScanConfig,
            onStartScan = viewModel::startScan,
            onAutoCleanAll = viewModel::autoSelectAllRecommendedDuplicates,
            onCompareGroup = viewModel::openCompareDialog,
            onQuickDelete = viewModel::deleteFileFromGroup
        )

        1 -> DuplicateResultsScreen(
            duplicateGroups = duplicateGroups,
            onCompareGroup = viewModel::openCompareDialog,
            onQuickDelete = viewModel::deleteFileFromGroup,
            onAutoCleanAll = viewModel::autoSelectAllRecommendedDuplicates,
            scanProgress = scanProgress,
            onDeleteSelected = viewModel::deleteSelectedDuplicates,
            onUpdateMetadata = viewModel::updateAudioFileMetadata
        )

        2 -> AiMusicStudioScreen(
            onPlayTrack = { viewModel.audioPlayerController.play(it) },
            onTrackExportedToScanner = viewModel::onAiTrackExported
        )

        3 -> RecycleBinScreen(
            trashedItems = recycleBinItems,
            onRestore = viewModel::restoreFromTrash,
            onRestoreBatch = viewModel::restoreBatchFromTrash,
            onPermanentlyDelete = viewModel::permanentlyDeleteFromTrash,
            onPermanentlyDeleteBatch = viewModel::permanentlyDeleteBatchFromTrash,
            onEmptyRecycleBin = viewModel::emptyRecycleBin
        )

        4 -> SettingsScreen(
            scanConfig = scanConfig,
            onUpdateConfig = viewModel::updateScanConfig,
            onRequestPermissions = onRequestPermissions
        )
    }
}
