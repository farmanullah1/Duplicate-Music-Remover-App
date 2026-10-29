package com.example.ui.home

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.Formatters
import com.example.core.StorageAccess
import com.example.data.scan.ScanPhase
import com.example.data.scan.ScanState
import com.example.di.LocalAppContainer
import com.example.di.viewModelFactory
import com.example.domain.model.MediaCategory
import com.example.ui.components.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDuplicates: (String?) -> Unit,
    onNavigateToJunk: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToRecycleBin: () -> Unit,
    onNavigateToStudio: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = run {
        val c = LocalAppContainer.current
        viewModel(factory = viewModelFactory {
            HomeViewModel(
                context = c.context,
                statsRepository = c.statsRepository,
                settingsRepository = c.settingsRepository,
                smartCleanUseCase = c.smartCleanUseCase
            )
        })
    }
) {
    val context = LocalContext.current
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val smartPlan by viewModel.smartPlan.collectAsStateWithLifecycle()
    val isSmartCleaning by viewModel.isSmartCleaning.collectAsStateWithLifecycle()
    val scannedFolder by viewModel.scannedFolder.collectAsStateWithLifecycle()
    val hasExternalChanges by viewModel.hasExternalChanges.collectAsStateWithLifecycle()

    var showPermissionCard by remember { mutableStateOf(StorageAccess.mode(context) == StorageAccess.Mode.NONE) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showScanMenu by remember { mutableStateOf(false) }
    var showUnreadableDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, stats?.lastScanAt) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkExternalChanges(stats?.lastScanAt)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.requestScan()
    }

    var crashInfo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.refreshStats()
        val (crashed, msg) = com.example.SafaiApp.checkAndClearCrashMarker(context)
        if (crashed) {
            crashInfo = msg ?: "Unexpected termination recovered"
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

        // 1. Header row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onNavigateToRecycleBin,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    val count = stats?.trashCount ?: 0
                    if (count > 0) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text(count.toString())
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Recycle Bin",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Recycle Bin",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Crash Recovery Banner
        if (crashInfo != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(Icons.Rounded.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Safai Recovered from an Error",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Crash detected: ${crashInfo}. Diagnostics were recorded safely to crash.log.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { crashInfo = null }) {
                                Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    crashInfo = null
                                    try {
                                        val f = java.io.File(context.filesDir, "crash.log")
                                        if (f.exists() && f.length() > 0) {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Safai Crash Log")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(android.content.Intent.createChooser(intent, "Share crash log"))
                                        }
                                    } catch (e: Exception) {
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Share Crash Log", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Low storage banner
        val freeBytes = stats?.freeBytes ?: Long.MAX_VALUE
        if (freeBytes < 1024L * 1024L * 1024L) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Warning.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Warning.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = Warning)
                            Text(
                                text = stringResource(R.string.home_low_storage_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextButton(onClick = onNavigateToJunk) {
                            Text("Clean now", color = Warning, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Permission Card if needed
        if (showPermissionCard) {
            item {
                PermissionCard(
                    title = stringResource(R.string.perm_all_files_title),
                    text = stringResource(R.string.perm_all_files_desc),
                    buttonLabel = stringResource(R.string.perm_all_files_button),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.startActivity(StorageAccess.allFilesIntent(context))
                        }
                    }
                )
            }
        }

        // Partial media access banner if on Android 14+ with only selected photos
        if (StorageAccess.isPartialMediaAccess(context)) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().clickable {
                        context.startActivity(StorageAccess.appDetailsIntent(context))
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Partial Photo Access (Selected Only)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Safai can only see photos you selected. Tap to allow all photos in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // External changes rescan chip
        if (hasExternalChanges) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.requestScan() }
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Files changed on storage",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "New or modified files detected since last scan. Tap to rescan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Smart Clean Card
        item {
            SafaiCard(
                brush = BrandGradient,
                onClick = { viewModel.prepareSmartPlan() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Text(
                                text = stringResource(R.string.home_smart_clean_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(
                            text = stringResource(R.string.home_smart_clean_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Button(
                        onClick = { viewModel.prepareSmartPlan() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Smart Clean",
                            color = Teal900,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Storage Card with ProgressRing
        item {
            SafaiCard {
                Text(
                    text = stringResource(R.string.home_storage_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.md))

                val total = stats?.totalBytes ?: 1L
                val free = stats?.freeBytes ?: 0L
                val used = (total - free).coerceAtLeast(0L)
                val usedFraction = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                val segments = mutableListOf<Pair<Float, Color>>()
                stats?.categoryTotals?.forEach { (cat, catTotal) ->
                    val frac = (catTotal.bytes.toFloat() / total.toFloat()).coerceAtLeast(0f)
                    val color = when (cat) {
                        MediaCategory.AUDIO -> CatAudio
                        MediaCategory.IMAGE -> CatImage
                        MediaCategory.VIDEO -> CatVideo
                        MediaCategory.DOCUMENT -> CatDocument
                        MediaCategory.ARCHIVE -> CatArchive
                        MediaCategory.APK -> CatApk
                        else -> CatOther
                    }
                    if (frac > 0.005f) {
                        segments.add(frac to color)
                    }
                }
                // Apps & system segment
                val scannedSum = stats?.categoryTotals?.values?.sumOf { it.bytes } ?: 0L
                val systemBytes = (used - scannedSum).coerceAtLeast(0L)
                val systemFrac = (systemBytes.toFloat() / total.toFloat()).coerceAtLeast(0f)
                if (systemFrac > 0.01f) {
                    segments.add(systemFrac to Color(0xFF64748B))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ProgressRing(
                        progress = usedFraction,
                        segments = segments,
                        modifier = Modifier.size(140.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(usedFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "used",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = Formatters.formatBytes(free) + " free",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "of " + Formatters.formatBytes(total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Button(
                            onClick = onNavigateToStorage,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Explore Folders", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 4. Scan Hero Card (Morphs between Idle, Running, Finished)
        item {
            SafaiCard(
                brush = HeroGradient
            ) {
                AnimatedContent(
                    targetState = scanState,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "scan_hero_morph"
                ) { targetState ->
                    when (targetState) {
                        is ScanState.Idle -> {
                            Column {
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
                                        IconBadge(
                                            icon = Icons.Rounded.Radar,
                                            tint = Teal400,
                                            size = 48.dp
                                        )
                                        Column {
                                            Text(
                                                text = stringResource(R.string.home_scan_hero_title),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            val lastScan = stats?.lastScanAt
                                            Text(
                                                text = if (lastScan == null) stringResource(R.string.home_scan_hero_never)
                                                else "Last scan: " + Formatters.formatDate(lastScan),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    if (StorageAccess.mode(context) == StorageAccess.Mode.FULL) {
                                        Box {
                                            IconButton(onClick = { showScanMenu = true }) {
                                                Icon(
                                                    Icons.Rounded.MoreVert,
                                                    contentDescription = "Scan options",
                                                    tint = Color.White
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showScanMenu,
                                                onDismissRequest = { showScanMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Scan a folder…") },
                                                    leadingIcon = {
                                                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                                                    },
                                                    onClick = {
                                                        showScanMenu = false
                                                        showFolderDialog = true
                                                    }
                                                )
                                                if (scannedFolder != null) {
                                                    DropdownMenuItem(
                                                        text = { Text("Scan entire storage") },
                                                        leadingIcon = {
                                                            Icon(Icons.Rounded.Storage, contentDescription = null)
                                                        },
                                                        onClick = {
                                                            showScanMenu = false
                                                            viewModel.requestScan()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (scannedFolder != null) {
                                    Spacer(Modifier.height(Spacing.xs))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.15f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Target: $scannedFolder",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { viewModel.requestScan() },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Close,
                                                    contentDescription = "Clear folder",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(Spacing.md))
                                PrimaryButton(
                                    text = if (scannedFolder != null) "Scan This Folder" else stringResource(R.string.home_scan_button),
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            if (scannedFolder != null) {
                                                viewModel.scanFolder(scannedFolder!!)
                                            } else {
                                                viewModel.requestScan()
                                            }
                                        }
                                    },
                                    icon = Icons.Rounded.Radar,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (stats?.lastScanAt == null) {
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text(
                                        text = "The first scan can take several minutes on large libraries. You can leave the app.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.75f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        is ScanState.Running -> {
                            val p = targetState.progress
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 3.dp,
                                            color = Teal400
                                        )
                                        Text(
                                            text = when (p.phase) {
                                                ScanPhase.ENUMERATING -> "Discovering files…"
                                                ScanPhase.READING_INFO -> "Reading file info…"
                                                ScanPhase.HASHING -> "Checking exact hashes…"
                                                ScanPhase.COMPARING -> "Grouping duplicates…"
                                                ScanPhase.SAVING -> "Saving results…"
                                                ScanPhase.DONE -> "Finishing up…"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    TextButton(onClick = { viewModel.cancelScan() }) {
                                        Text(stringResource(R.string.home_cancel), color = Warning)
                                    }
                                }
                                Spacer(Modifier.height(Spacing.sm))

                                if (p.total > 0) {
                                    val progressFraction = (p.processed.toFloat() / p.total.toFloat()).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progressFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Teal400,
                                        trackColor = Color(0xFF263241)
                                    )
                                    Spacer(Modifier.height(Spacing.xs))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${p.processed} of ${p.total} files",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = if (p.groupsFound > 0) "${p.groupsFound} groups found" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Gold400
                                        )
                                    }
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Teal400,
                                        trackColor = Color(0xFF263241)
                                    )
                                }

                                if (p.currentPath.isNotBlank()) {
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text(
                                        text = p.currentPath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        is ScanState.Finished -> {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Success)
                                    Text(
                                        text = "Scan Completed",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(Modifier.height(Spacing.md))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    StatPill(
                                        icon = Icons.Rounded.Description,
                                        value = targetState.filesScanned.toString(),
                                        label = "Scanned"
                                    )
                                    StatPill(
                                        icon = Icons.Rounded.ContentCopy,
                                        value = targetState.groups.toString(),
                                        label = "Groups",
                                        tint = Gold400
                                    )
                                    StatPill(
                                        icon = Icons.Rounded.DeleteSweep,
                                        value = Formatters.formatBytes(targetState.reclaimableBytes),
                                        label = "Can free",
                                        tint = Teal400
                                    )
                                }
                                if (targetState.unreadable > 0) {
                                    Spacer(Modifier.height(Spacing.xs))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.clickable { showUnreadableDialog = true }
                                    ) {
                                        Icon(
                                            Icons.Rounded.Warning,
                                            contentDescription = null,
                                            tint = Warning,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${targetState.unreadable} files could not be read (tap to view)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Warning,
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                        )
                                    }
                                }
                                Spacer(Modifier.height(Spacing.md))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    PrimaryButton(
                                        text = stringResource(R.string.home_review_duplicates),
                                        onClick = { onNavigateToDuplicates(null) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = { viewModel.requestScan() },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Text(stringResource(R.string.home_scan_again))
                                    }
                                }
                            }
                        }
                        is ScanState.Failed -> {
                            Column {
                                Text(
                                    text = "Scan Failed: ${targetState.message}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Danger
                                )
                                Spacer(Modifier.height(Spacing.sm))
                                PrimaryButton(
                                    text = "Retry Scan",
                                    onClick = { viewModel.requestScan() },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Quick tiles
        item {
            SectionHeader(title = "Quick Tools")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Duplicates Tile
                SafaiCard(
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToDuplicates(null) },
                    tonal = true
                ) {
                    IconBadge(Icons.Rounded.ContentCopy, CatAudio)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Duplicates", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${stats?.openGroups ?: 0} groups",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Junk Tile
                SafaiCard(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToJunk,
                    tonal = true
                ) {
                    IconBadge(Icons.Rounded.CleaningServices, CatApk)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Junk Clean", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Clean temp files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Storage Analyzer Tile
                SafaiCard(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToStorage,
                    tonal = true
                ) {
                    IconBadge(Icons.Rounded.PieChart, CatImage)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Storage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Folder explorer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Recycle Bin Tile
                SafaiCard(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToRecycleBin,
                    tonal = true
                ) {
                    IconBadge(Icons.Rounded.DeleteOutline, Danger)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Recycle Bin", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${stats?.trashCount ?: 0} files",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

                // AI Music Studio Feature Card
        item {
            SafaiCard(
                brush = Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF4338CA))),
                onClick = onNavigateToStudio
            ) {
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
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CatAudio.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = CatAudio,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "AI Music Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Synthesize original Lo-Fi, Synthwave and Ambient music offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }

        // 6. Library tiles
        item {
            SectionHeader(title = "Categories")
            val categories = listOf(
                MediaCategory.AUDIO to ("Audio" to CatAudio),
                MediaCategory.IMAGE to ("Photos" to CatImage),
                MediaCategory.VIDEO to ("Videos" to CatVideo),
                MediaCategory.DOCUMENT to ("Documents" to CatDocument),
                MediaCategory.ARCHIVE to ("Archives" to CatArchive),
                MediaCategory.APK to ("APKs" to CatApk)
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                categories.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        row.forEach { (cat, info) ->
                            val catData = stats?.categoryTotals?.get(cat)
                            SafaiCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(96.dp),
                                onClick = { onNavigateToDuplicates(cat.name) },
                                tonal = true
                            ) {
                                Text(
                                    text = info.first,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = info.second
                                )
                                Spacer(Modifier.height(Spacing.xxs))
                                Text(
                                    text = Formatters.formatBytes(catData?.bytes ?: 0L),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${catData?.count ?: 0} files",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. Impact Card
        item {
            SafaiCard(
                tonal = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.home_total_freed),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AnimatedCounterText(
                            value = stats?.totalFreedBytes ?: 0L,
                            color = Gold400
                        )
                    }
                    IconBadge(
                        icon = Icons.Rounded.Celebration,
                        tint = Gold400,
                        size = 56.dp
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Spacing.xl)) }
    }

    // Smart Clean Bottom Sheet
    if (smartPlan != null) {
        val plan = smartPlan!!
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSmartPlan() },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    IconBadge(Icons.Rounded.AutoAwesome, Teal400)
                    Column {
                        Text(
                            text = "Smart Clean Plan",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Safe to clean ${Formatters.formatBytes(plan.totalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "Includes only verified exact duplicate copies (full SHA-256) and temporary junk files. Keeps at least one original copy of every file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Exact duplicate copies:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${plan.exactDuplicateFiles.size} files (${Formatters.formatBytes(plan.exactDuplicateFiles.sumOf { it.sizeBytes })})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pre-selected junk files:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${plan.junk.sumOf { it.items.size }} items (${Formatters.formatBytes(plan.junk.sumOf { it.totalBytes })})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                PrimaryButton(
                    text = "Clean ${Formatters.formatBytes(plan.totalBytes)}",
                    onClick = {
                        viewModel.executeSmartClean { batch ->
                            onShowSnackbar("Cleaned ${Formatters.formatBytes(batch.bytes)} successfully")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showFolderDialog) {
        var customPath by remember { mutableStateOf("") }
        val commonFolders = remember {
            listOf(
                "Downloads" to "/storage/emulated/0/Download",
                "Camera & Photos (DCIM)" to "/storage/emulated/0/DCIM",
                "Pictures" to "/storage/emulated/0/Pictures",
                "Music" to "/storage/emulated/0/Music",
                "Documents" to "/storage/emulated/0/Documents",
                "Movies & Videos" to "/storage/emulated/0/Movies"
            )
        }

        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = {
                Text("Select Folder to Scan", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        "Scan only a specific directory instead of the whole storage:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customPath,
                        onValueChange = { customPath = it },
                        label = { Text("Custom Folder Path") },
                        placeholder = { Text("/storage/emulated/0/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (customPath.isNotBlank()) {
                                IconButton(onClick = { customPath = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                    Text(
                        "Or choose a common folder:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(commonFolders.size) { idx ->
                            val (name, path) = commonFolders[idx]
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (customPath == path) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { customPath = path }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = customPath.trim()
                        if (target.isNotBlank()) {
                            showFolderDialog = false
                            viewModel.scanFolder(target)
                        }
                    },
                    enabled = customPath.isNotBlank()
                ) {
                    Text("Scan Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUnreadableDialog) {
        val issues = remember { viewModel.readScanIssues() }
        AlertDialog(
            onDismissRequest = { showUnreadableDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = Warning)
                    Text("Unreadable Files (${issues.size})", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        "These files could not be opened or hashed due to Android permission restrictions, broken symlinks, or file system errors:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (issues.isEmpty()) {
                        Text("No detailed issues logged.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(issues.size) { idx ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = issues[idx],
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(Spacing.sm)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUnreadableDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
