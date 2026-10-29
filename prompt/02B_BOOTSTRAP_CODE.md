# 02B — BOOTSTRAP CODE (give this right after file 02, BEFORE file 03)

Goal of this phase: the app **compiles, launches, shows the real adaptive navigation shell, and persists settings**. Later phases fill the screens. Everything below is complete code — copy it, fix imports, build.

> Phase-0 rule: a destination whose phase isn't built yet shows a real `EmptyState` titled with the screen name and the text "Not built yet". Remove those as each phase lands. No fake data.

## 1. `core/` helpers

```kotlin
// core/Dispatchers.kt
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class DefaultDispatcher

// core/Models.kt
enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class ScanSummary(val groups: Int, val reclaimableBytes: Long, val filesScanned: Int, val unreadable: Int)

// core/Formatters.kt  — SI units (1 GB = 1000 MB) so numbers match the phone's own Settings/Files app
object Formatters {
    fun formatBytes(b: Long): String {
        if (b < 1000) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = b.toDouble(); var i = -1
        do { v /= 1000.0; i++ } while (v >= 1000.0 && i < units.lastIndex)
        val pattern = if (v >= 100) "%.0f %s" else if (v >= 10) "%.1f %s" else "%.2f %s"
        return String.format(Locale.US, pattern, v, units[i])
    }
    fun formatDuration(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }
    fun formatDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
    /** "Today", "Yesterday", "5 days ago" — strings come from strings.xml via the caller when localised. */
    fun daysAgo(ms: Long): Int = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - ms).toInt()
}
```
(`java.text.DateFormat`, `java.util.*`, `java.util.concurrent.TimeUnit`.)

## 2. Notification channels

```kotlin
object NotificationChannels {
    const val SCAN = "scan_progress"; const val CLEANUP = "cleanup"
    fun create(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(SCAN, ctx.getString(R.string.channel_scan), NotificationManager.IMPORTANCE_LOW)
            .apply { description = ctx.getString(R.string.channel_scan_desc); setShowBadge(false) })
        nm.createNotificationChannel(NotificationChannel(CLEANUP, ctx.getString(R.string.channel_cleanup), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = ctx.getString(R.string.channel_cleanup_desc) })
    }
}
```
Notification small icon: `R.drawable.ic_stat_safai` — a 24 dp white vector (use the `ic_w_clean` path from file 09).

## 3. Settings repository (JSON in one DataStore key — simple, atomic, no key-per-field mistakes)

```kotlin
private val Context.settingsStore by preferencesDataStore(name = "safai_settings")   // top-level, once

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val KEY = stringPreferencesKey("settings_json")

    val flow: Flow<AppSettings> = ctx.settingsStore.data
        .map { p -> decode(p[KEY]) }.distinctUntilChanged()

    suspend fun update(block: (AppSettings) -> AppSettings) {
        ctx.settingsStore.edit { p -> p[KEY] = encode(block(decode(p[KEY]))) }     // edit{} is atomic
    }

    private fun decode(s: String?): AppSettings {
        val d = AppSettings()
        if (s == null) return d
        return runCatching {
            val o = JSONObject(s)
            fun set(name: String, default: Set<String>) = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: default
            d.copy(
                scanAudio = o.optBoolean("scanAudio", d.scanAudio),
                minFileSizeKb = o.optInt("minFileSizeKb", d.minFileSizeKb),
                excludedFolders = set("excludedFolders", d.excludedFolders),
                themeMode = runCatching { ThemeMode.valueOf(o.optString("themeMode", d.themeMode.name)) }.getOrDefault(d.themeMode),
                // …EVERY other AppSettings field, same pattern: optBoolean / optInt / optLong / optString / set(...)
            )
        }.getOrDefault(d)
    }
    private fun encode(s: AppSettings): String = JSONObject().apply {
        put("scanAudio", s.scanAudio); put("minFileSizeKb", s.minFileSizeKb)
        put("excludedFolders", JSONArray(s.excludedFolders.toList()))
        put("themeMode", s.themeMode.name)
        // …EVERY other field
    }.toString()
}
```
**Hard rule:** `decode` and `encode` must cover **every** field of `AppSettings` (files 03, 04, 05, 06). After writing them, list all fields of `AppSettings` and tick each one in both functions. A missed field silently never saves.

## 4. Application, Activity, root composable

```kotlin
@HiltViewModel
class AppViewModel @Inject constructor(repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings?> = repo.flow.map<AppSettings, AppSettings?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val route = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        route.value = intent?.getStringExtra("route")
        setContent {
            val vm: AppViewModel = hiltViewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings
            if (s == null) { Box(Modifier.fillMaxSize().background(Color.Black)); return@setContent }   // avoid theme flash
            val wsc = calculateWindowSizeClass(this)
            SafaiTheme(s) {
                CompositionLocalProvider(LocalWindowSize provides wsc) {
                    SafaiRoot(settings = s, pendingRoute = route)
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        route.value = intent.getStringExtra("route")
    }
}

val LocalWindowSize = staticCompositionLocalOf<WindowSizeClass> { error("WindowSizeClass not provided") }
```

```kotlin
// ui/nav/Tabs.kt
enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    HOME("home", R.string.tab_home, Icons.Rounded.Home),
    DUPLICATES("duplicates", R.string.tab_duplicates, Icons.Rounded.ContentCopy),
    JUNK("junk", R.string.tab_junk, Icons.Rounded.CleaningServices),
    STORAGE("storage", R.string.tab_storage, Icons.Rounded.PieChart),
    SETTINGS("settings", R.string.tab_settings, Icons.Rounded.Settings) }
```

```kotlin
// ui/nav/SafaiRoot.kt
@Composable
fun SafaiRoot(settings: AppSettings, pendingRoute: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val start = if (settings.onboardingDone) "home" else "onboarding"

    val target by pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(target, settings.onboardingDone) {
        val r = target ?: return@LaunchedEffect
        if (!settings.onboardingDone) return@LaunchedEffect
        when (r) {
            "scan" -> { nav.navigateTab("home"); /* HomeViewModel.requestScan() is triggered via a shared SharedFlow in AppEvents */ AppEvents.requestScan() }
            "home", "duplicates", "junk", "storage", "settings" -> nav.navigateTab(r)
            "recycle_bin" -> nav.navigate("recycle_bin")
        }
        pendingRoute.value = null
    }

    val isTopLevel = Tab.values().any { it.route == current }
    AdaptiveScaffold(
        current = current, showNav = isTopLevel,
        onSelect = { nav.navigateTab(it.route) }, snackbar = snackbar,
    ) { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(300, easing = Motion.Emphasized)) { it / 12 } },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(300)) }, popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(300)) { it / 12 } }) {
            composable("onboarding") { /* OnboardingScreen(onDone = { nav.navigateTab("home") }) */ PendingScreen("Onboarding") }
            composable("home") { PendingScreen("Home") }
            composable("duplicates") { PendingScreen("Duplicates") }
            composable("junk") { PendingScreen("Junk") }
            composable("storage") { PendingScreen("Storage") }
            composable("settings") { PendingScreen("Settings") }
            composable("recycle_bin") { PendingScreen("Recycle Bin") }
            composable("compare/{groupId}", arguments = listOf(navArgument("groupId") { type = NavType.LongType })) { PendingScreen("Compare") }
        }
    }
}

fun NavController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true; restoreState = true
}

@Composable fun PendingScreen(name: String) =
    EmptyState(icon = Icons.Rounded.Construction, title = name, message = "Not built yet")   // removed when the real screen lands

/** Process-wide one-shot events (widget "Scan" button etc.). */
object AppEvents {
    private val _scan = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scanRequests = _scan.asSharedFlow()
    fun requestScan() { _scan.tryEmit(Unit) }
}
```
`HomeViewModel` collects `AppEvents.scanRequests` in `init` and calls `requestScan()`.
Note: `onboarding` must not show the nav bar (`isTopLevel` is false there). The Compare route also hides it.

## 5. Adaptive scaffold (full code)

```kotlin
@Composable
fun AdaptiveScaffold(
    current: String?, showNav: Boolean, onSelect: (Tab) -> Unit, snackbar: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit
) {
    val wsc = LocalWindowSize.current
    val useRail = wsc.widthSizeClass == WindowWidthSizeClass.Medium ||
        (wsc.widthSizeClass == WindowWidthSizeClass.Compact && wsc.heightSizeClass == WindowHeightSizeClass.Compact)
    val useDrawer = wsc.widthSizeClass == WindowWidthSizeClass.Expanded && wsc.heightSizeClass != WindowHeightSizeClass.Compact
    val bg = MaterialTheme.colorScheme.background

    when {
        !showNav -> Scaffold(containerColor = bg, snackbarHost = { SnackbarHost(snackbar) }) { content(it) }

        useDrawer -> PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(Modifier.width(Sizes.drawerWidth), drawerContainerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.height(Spacing.xl))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = Spacing.xl))
                    Spacer(Modifier.height(Spacing.lg))
                    Tab.values().forEach { t ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(t.label)) },
                            icon = { Icon(t.icon, null) },
                            selected = current == t.route, onClick = { onSelect(t) },
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp))
                    }
                }
            }) { Scaffold(containerColor = bg, snackbarHost = { SnackbarHost(snackbar) }) { content(it) } }

        useRail -> Row(Modifier.fillMaxSize().background(bg)) {
            NavigationRail(Modifier.width(Sizes.railWidth), containerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.weight(1f))
                Tab.values().forEach { t ->
                    NavigationRailItem(selected = current == t.route, onClick = { onSelect(t) },
                        icon = { Icon(t.icon, stringResource(t.label)) }, label = { Text(stringResource(t.label)) },
                        alwaysShowLabel = true)
                }
                Spacer(Modifier.weight(1f))
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Scaffold(Modifier.weight(1f), containerColor = bg, snackbarHost = { SnackbarHost(snackbar) }) { content(it) }
        }

        else -> Scaffold(
            containerColor = bg,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Surface(
                    modifier = Modifier.navigationBarsPadding().padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.sm),
                    shape = RoundedCornerShape(28.dp), tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    NavigationBar(containerColor = Color.Transparent, windowInsets = WindowInsets(0), modifier = Modifier.height(Sizes.bottomBar)) {
                        Tab.values().forEach { t ->
                            NavigationBarItem(selected = current == t.route, onClick = { onSelect(t) },
                                icon = { Icon(t.icon, stringResource(t.label)) }, label = { Text(stringResource(t.label), maxLines = 1) })
                        }
                    }
                }
            }
        ) { content(it) }
    }
}
```
`content(padding)`: every top-level screen must additionally apply `WindowInsets.statusBars` on top (the Scaffold with no topBar does not pad for the status bar/punch-hole) — in a shared helper:
```kotlin
@Composable fun ScreenContainer(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val wsc = LocalWindowSize.current
    val h = when (wsc.widthSizeClass) { WindowWidthSizeClass.Compact -> Spacing.md; WindowWidthSizeClass.Medium -> Spacing.xl; else -> Spacing.xxl }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.TopCenter) {
        Column(modifier.widthIn(max = Sizes.maxContent).fillMaxSize().padding(horizontal = h), content = content)
    }
}
```
Lists inside use `contentPadding = PaddingValues(bottom = 24.dp)`; the Scaffold's `padding` already contains the floating bar height.

## 6. Foreground scan service (complete)

```kotlin
@AndroidEntryPoint
class ScanService : Service() {
    @Inject lateinit var scan: ScanUseCase
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var holder: ScanStateHolder
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> { job?.cancel(); return START_NOT_STICKY }
        }
        if (job?.isActive == true) return START_NOT_STICKY
        val rootOverride = intent?.getStringExtra(EXTRA_ROOT)          // optional "scan this folder only"
        ServiceCompat.startForeground(this, NOTIF_ID, notification(0, 0, getString(R.string.scan_starting)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        job = scope.launch {
            try {
                val s = settingsRepo.flow.first()
                val mode = StorageAccess.mode(applicationContext)
                holder.state.value = ScanState.Running(ScanProgress())
                val r = scan(s, mode, rootOverride) { p ->
                    holder.state.value = ScanState.Running(p)
                    nm().notify(NOTIF_ID, notification(p.processed, p.total, p.currentPath))
                }
                holder.state.value = ScanState.Finished(r.groups, r.reclaimableBytes, r.filesScanned, r.unreadable)
                WidgetUpdater.updateAll(applicationContext)
            } catch (e: CancellationException) {
                holder.state.value = ScanState.Idle; throw e
            } catch (t: Throwable) {
                holder.state.value = ScanState.Failed(t.message ?: getString(R.string.scan_failed))
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun nm() = getSystemService(NotificationManager::class.java)
    private fun notification(done: Int, total: Int, path: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("route", "home"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, 1, Intent(this, ScanService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, NotificationChannels.SCAN)
            .setSmallIcon(R.drawable.ic_stat_safai).setContentTitle(getString(R.string.scan_notification_title))
            .setContentText(if (total > 0) getString(R.string.scan_notification_progress, done, total) else path.substringAfterLast('/'))
            .setProgress(total, done, total == 0).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(0, getString(R.string.cancel), cancel).build()
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object { const val NOTIF_ID = 1001; const val ACTION_CANCEL = "com.example.CANCEL_SCAN"; const val EXTRA_ROOT = "root"
        fun start(ctx: Context, root: String? = null) = ContextCompat.startForegroundService(ctx,
            Intent(ctx, ScanService::class.java).apply { root?.let { putExtra(EXTRA_ROOT, it) } }) }
}
```
Change the `ScanUseCase.invoke` signature (file 04 §10) to `invoke(s, mode, rootOverride: String?, onProgress)`; when `rootOverride != null` only that folder is enumerated (and cache-deletion of unseen rows is skipped so other folders' cache survives).
ScanState is a singleton `StateFlow`, so the Home screen shows live progress even after the app is reopened mid-scan.

## 7. Resource files to create now
- `res/xml/file_paths.xml`: `<paths><root-path name="root" path="." /><files-path name="files" path="." /></paths>`
- `res/values/strings.xml`: every string used so far (`app_name`="Safai", tab labels, channel names/descriptions, scan notification texts, `cancel`, `scan_starting`, `scan_failed`, `scan_notification_title`="Scanning your storage", `scan_notification_progress`="%1$d of %2$d files").
- `res/drawable/ic_stat_safai.xml` (white 24 dp vector), launcher icon files from file 07 §11, `themes.xml` from file 07 §10.
- `proguard-rules.pro`: empty file with a comment is fine (Room/Hilt/Glance ship consumer rules).
- Manifest: set `android:allowBackup="false"` (the Recycle Bin lives in `filesDir`; it must not be backed up/restored).

## 8. Phase-0 acceptance
- `./gradlew assembleDebug` succeeds with **zero** errors.
- App launches to Onboarding placeholder; after setting `onboardingDone` manually it shows the shell with 5 tabs; rotating phone ↔ tablet width switches bottom bar ↔ rail ↔ drawer; nothing draws under the camera punch-hole.
- Changing a setting (temporarily via a debug-free test in a unit/instrumented test, not UI) round-trips through `SettingsRepository`.
