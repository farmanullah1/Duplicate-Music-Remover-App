# 07 — DESIGN SYSTEM, ADAPTIVE LAYOUT, ANIMATION, ICONS

Goal: a calm, premium, **AMOLED-black** app with teal + violet accents, glassy cards, generous spacing and smooth **local** motion (Jetpack Compose animation only — no Lottie, no remote assets, no web fonts).

## 1. Target devices
- **Primary: Infinix phone, 6.78" AMOLED, 1080×2436 px, 20:9, ≈ 411 × 928 dp**, punch-hole camera centered at the top, rounded screen corners, near-zero bezels.
- Also test at 360×800 dp (small phone), 600×1024 dp (small tablet), 800×1280 dp & 1280×800 dp (tablet, both orientations), and foldable inner screen (~ 840 dp wide).
- Always `enableEdgeToEdge()` in `MainActivity.onCreate`. Handle insets:
  - **Top (punch-hole + status bar):** large screen titles start **below** `WindowInsets.statusBars` (use `Modifier.windowInsetsPadding(WindowInsets.statusBars)` or `Scaffold` insets) — never draw text/buttons in the camera area.
  - **Corners:** keep a minimum **16 dp** horizontal margin so nothing is clipped by rounded corners; the floating bottom bar sits 12 dp above the gesture bar.
  - **Bottom:** list `contentPadding.bottom = navigationBarsInset + bottomBarHeight + 16dp`.
- Pure black (`#000000`) page background in dark mode (saves AMOLED power); light mode uses a soft cool white.

## 2. Spacing & size tokens (use ONLY these)

```kotlin
object Spacing { val xxs = 4.dp; val xs = 8.dp; val sm = 12.dp; val md = 16.dp; val lg = 20.dp; val xl = 24.dp; val xxl = 32.dp; val xxxl = 48.dp }
object Sizes { val touch = 48.dp; val iconSm = 20.dp; val icon = 24.dp; val iconLg = 32.dp; val iconHero = 56.dp
               val thumb = 56.dp; val thumbLg = 72.dp; val bottomBar = 68.dp; val railWidth = 88.dp; val drawerWidth = 280.dp
               val maxContent = 1040.dp; val maxContentNarrow = 720.dp }
```
Page horizontal padding: Compact **16 dp**, Medium **24 dp**, Expanded **32 dp**. Vertical gap between cards **12 dp**; inside a card **16 dp**; between a title and its content **8 dp**; between sections **24 dp**. Every clickable element ≥ 48×48 dp. Every text/icon sits inside padded containers (no element touches the card edge).

## 3. Color — `Color.kt`

```kotlin
// Brand
val Teal400 = Color(0xFF2DD4BF); val Teal600 = Color(0xFF0F9D8E); val Teal900 = Color(0xFF0B3B36)
val Violet400 = Color(0xFF8B7CF6); val Violet600 = Color(0xFF6D5BE0)
val Gold400 = Color(0xFFF5C451); val Gold700 = Color(0xFFB7791F)
val Success = Color(0xFF34D399); val Warning = Color(0xFFFBBF24); val Danger = Color(0xFFFF5D73)
// Dark (AMOLED)
val DarkBg = Color(0xFF000000); val DarkSurface = Color(0xFF0B0F14); val DarkSurface2 = Color(0xFF111820)
val DarkSurface3 = Color(0xFF18212B); val DarkOutline = Color(0xFF263241)
val DarkText = Color(0xFFE8EEF5); val DarkTextDim = Color(0xFF9AA9BA)
// Light
val LightBg = Color(0xFFF4F7FA); val LightSurface = Color(0xFFFFFFFF); val LightSurface2 = Color(0xFFEDF2F7)
val LightOutline = Color(0xFFD5DEE8); val LightText = Color(0xFF0E1621); val LightTextDim = Color(0xFF5B6B7C)
// Category colors (used for icons, chart segments, badges — keep in this order)
val CatAudio = Color(0xFF8B7CF6); val CatImage = Color(0xFF38BDF8); val CatVideo = Color(0xFFFB7185)
val CatDocument = Color(0xFFFBBF24); val CatArchive = Color(0xFFF97316); val CatApk = Color(0xFF34D399); val CatOther = Color(0xFF94A3B8)
```
Semantics: **Teal = primary actions/keep/safe**, **Violet = secondary/info**, **Gold = achievements (freed-space numbers, success highlights)**, **Danger = delete/destructive only**. Never use red for decoration.

Gradients (`Brush`): `BrandGradient = linearGradient(Teal400→Violet400)`, `HeroGradient = linearGradient(Color(0xFF0B3B36), Color(0xFF0E2A47), Color(0xFF1B1646))` (used by the Home scan hero card) with a soft teal radial glow (`radialGradient(Teal400 @ 18% alpha → transparent)`) top-right, `DangerGradient = Danger→Color(0xFFB4233A)`.

`ColorScheme` mapping (dark): background=DarkBg, surface=DarkSurface, surfaceContainer=DarkSurface2, surfaceContainerHigh=DarkSurface3, primary=Teal400, onPrimary=Color(0xFF00201C), primaryContainer=Teal900, onPrimaryContainer=Color(0xFFB9F5EC), secondary=Violet400, tertiary=Gold400, error=Danger, outline=DarkOutline, outlineVariant=DarkOutline.copy(alpha=.6f), onSurface=DarkText, onSurfaceVariant=DarkTextDim. Light mapping: primary=Teal600/onPrimary white, etc. If `amoledBlack=false` in dark mode use `Color(0xFF0B0F14)` as background. If `dynamicColor=true` and API ≥ 31 use `dynamicDarkColorScheme/dynamicLightColorScheme` for the M3 roles but keep category colors.

## 4. Typography — `Type.kt` (system font; numbers use tabular figures)

| Style | Size / line | Weight | Use |
|---|---|---|---|
| displayLarge | 44 / 52 | Bold | Big countdown, freed space |
| headlineLarge | 30 / 38 | Bold | Screen titles |
| headlineMedium | 24 / 32 | SemiBold | Section hero numbers |
| titleLarge | 20 / 28 | SemiBold | Card titles |
| titleMedium | 16 / 24 | SemiBold | List titles |
| bodyLarge | 16 / 24 | Normal | Body |
| bodyMedium | 14 / 20 | Normal | Secondary text |
| labelLarge | 14 / 20 | Medium | Buttons |
| labelSmall | 11 / 16 | Medium | Badges, captions (letterSpacing 0.4sp) |
Add `fontFeatureSettings = "tnum"` to number-heavy styles so digits don't jitter while counting.

## 5. Shapes — `Shape.kt`
`extraSmall 8`, `small 12`, `medium 16`, `large 24`, `extraLarge 32` dp. Cards = large (24) on phone, 20 on tablet; chips = 12 (rounded rectangles, not pills, except status badges = full); bottom bar = 28; dialogs = 28; bottom sheets top corners = 28.

## 6. Reusable components — build ALL of these in `ui/components/`

```kotlin
@Composable fun SafaiCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, tonal: Boolean = false,
                          brush: Brush? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    val base = Modifier.fillMaxWidth().clip(shape)
        .then(if (brush != null) Modifier.background(brush) else Modifier.background(
            if (tonal) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)      // subtle 1dp border = "glass" edge
        .then(if (onClick != null) Modifier.pressScaleClickable(onClick) else Modifier)
    Column(modifier.then(base).padding(Spacing.md), content = content)
}
```
Also: `SectionHeader(title, action?)`, `StatPill(icon, value, label)`, `PrimaryButton` (gradient Teal→TealDark, 52 dp high, 16 dp radius, icon + label, `pressScale`), `SecondaryButton` (outlined), `DangerButton`, `IconBadge(icon, tint, size)` (rounded 14 dp square with tint@16% background), `CategoryBadge(category)`, `ConfidenceBadge(score)` (Certain=Success, Likely=Warning, Review=Danger text on 16%-alpha background), `EmptyState(icon, title, message, actionLabel?, onAction?)` (large tinted icon, 2 lines, one button), `ErrorState`, `PermissionCard(title, text, buttonLabel, onClick)`, `ProgressRing(progress, segments: List<Pair<Float, Color>>, centerContent)` (Canvas, rounded caps, animated sweep), `AnimatedCounterText(value: Long, format: (Long)->String)`, `ShimmerBox`, `SelectionBottomBar` (count + size + Clear + Delete), `ThumbnailImage(file)` (Coil `AsyncImage` for IMAGE; Coil video-frame decoder for VIDEO; for AUDIO the embedded album art decoded with `MediaMetadataRetriever.embeddedPicture` in a `produceState` on IO, else `IconBadge(MusicNote)`; for DOCUMENT the colored extension badge, e.g. "PDF").
Every list thumbnail uses a fixed size (56dp) so list scroll never jumps; Coil `size(Size(168,168))`, `crossfade(true)`, `memoryCachePolicy` default.

## 7. Icons (Material Icons Extended **Rounded** set) 

| Place | Icon |
|---|---|
| Home tab | `Icons.Rounded.Home` |
| Duplicates tab | `Icons.Rounded.ContentCopy` |
| Junk tab | `Icons.Rounded.CleaningServices` |
| Storage tab | `Icons.Rounded.PieChart` (fallback `Storage`) |
| Settings tab | `Icons.Rounded.Settings` |
| Scan / refresh | `Icons.Rounded.Radar` (if unavailable use `Search`) |
| Audio / Photo / Video / Document / Archive / APK | `MusicNote`, `Image`, `Videocam`, `Description`, `FolderZip`, `Android` |
| Recycle Bin | `DeleteOutline` / restore = `RestoreFromTrash` |
| Keep / Remove | `CheckCircle` / `RemoveCircle` |
| Compare | `Compare` |
| Large files | `Storage` |
| Smart Clean | `AutoAwesome` |
| Blurry / dark photos | `BlurOn` / `Brightness2` |
| Folder | `Folder` |
If any icon name does not exist in the project's icons version, pick the closest existing one — **never** leave a build error.

## 8. Adaptive layout — `ui/nav/AdaptiveScaffold.kt`

Use `calculateWindowSizeClass(activity)` (`@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)`) in `MainActivity`; provide via `CompositionLocalProvider(LocalWindowSize provides wsc)`.

| Width class | Navigation | Content |
|---|---|---|
| **Compact** (< 600 dp — the Infinix phone) | **Floating bottom bar**: height 68 dp, 16 dp side margin, 12 dp bottom margin, `RoundedCornerShape(28)`, surfaceContainerHigh + 1 dp border; selected item shows an animated pill behind icon + label; | single column, page padding 16 dp |
| **Medium** (600–839 dp — small tablet, foldable) | `NavigationRail` 88 dp with app logo on top, items centered | content centered, `widthIn(max = 840dp)`, padding 24 dp; Duplicates screen uses **list + detail two-pane** |
| **Expanded** (≥ 840 dp — your tablet) | `PermanentNavigationDrawer` 280 dp (logo, 5 destinations, Recycle Bin shortcut with count badge, storage mini-bar at bottom) | content `widthIn(max = 1040dp)`, 32 dp padding, Home becomes 2–3 column grid, Duplicates = master/detail (list left 380 dp, compare right), Storage = folder list left, selection details right |

Landscape phones fall into Medium or Compact-height: when `heightSizeClass == Compact` use the rail instead of the bottom bar.
Use `LazyVerticalGrid(GridCells.Adaptive(minSize = 160.dp))` for category tiles so the count of columns adapts automatically.

## 9. Motion — `Motion.kt` (all local, all optional via reduce-motion)

```kotlin
object Motion {
    const val Short = 150; const val Medium = 300; const val Long = 500
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun <T> spring() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
}
val LocalReduceMotion = staticCompositionLocalOf { false }   // provided in SafaiTheme: settings.reduceMotion || animatorScale == 0f

@Composable fun rememberAnimatorScaleZero(): Boolean { val c = LocalContext.current
    return remember { Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } }

/** Press-down scale feedback (0.97) + ripple. Use this single helper for EVERY tappable card/row/tile. */
fun Modifier.pressScaleClickable(onClick: () -> Unit): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && !reduce) 0.97f else 1f, spring(0.6f, 800f), label = "press")
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = ripple(), onClick = onClick)   // androidx.compose.material3.ripple
}
```

```kotlin
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val hi = MaterialTheme.colorScheme.outlineVariant
    drawWithCache { val w = size.width
        val brush = Brush.linearGradient(listOf(base, hi, base), Offset(w * x, 0f), Offset(w * (x + 0.6f), size.height))
        onDrawBehind { drawRect(brush) } }
}
```

### Where motion is used (implement all)
1. **Navigation:** `NavHost` `enterTransition = fadeIn(tween(300)) + slideInHorizontally(tween(300, easing = Emphasized)) { it / 12 }`, exit mirrors; tab switches use `fadeThrough` (fadeOut 90 ms → fadeIn 210 ms + scale 0.96→1).
2. **Bottom bar selection:** animated pill (`animateDpAsState` for indicator offset/width, `animateColorAsState` for icon tint).
3. **Home:** staggered entrance for cards (index × 40 ms, fade + 16 dp slide up, only on first composition); storage ring sweeps 0 → value in 900 ms (`Animatable`, `Emphasized`); numbers count up via `AnimatedCounterText` (`animateFloatAsState` / `Animatable`, 700 ms).
4. **Scan:** while scanning a radar badge pulses (`infiniteTransition`: 2 expanding rings, scale 1→1.8, alpha .5→0, 1600 ms, stagger); progress bar uses `animateFloatAsState`; the current-path line cross-fades (`Crossfade`) at ≤ 4 Hz.
5. **Lists:** `Modifier.animateItem()` on every `LazyColumn` item (reorder/removal slides); removing a duplicate row = `AnimatedVisibility(shrinkVertically + fadeOut)`.
6. **Selection:** checkbox morph (`animateColorAsState` + scale bounce 1→1.15→1), the `SelectionBottomBar` enters with `slideInVertically { it } + fadeIn`, count uses `AnimatedContent` (slide up/down on change).
7. **Compare screen:** A/B switch cross-fades the "now playing" highlight; waveform playhead moves smoothly (`animateFloatAsState` linear 100 ms); winner attribute rows tint with `animateColorAsState`.
8. **Clean success:** result sheet shows a checkmark drawn with `Path` + `PathMeasure` (`animateFloatAsState(0→1, 600 ms)`), then the freed-space number counts up.
9. **Smart Clean / Storage:** the Smart Clean button shimmers once per app launch; Storage folder bars grow from 0 to their fraction (`animateFloatAsState`, 500 ms, staggered 20 ms) when a folder opens.
10. **Dialogs/sheets:** default M3 motion (no artificial delays on buttons).
11. **Haptics:** `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove)` on selection toggles and tab change, `LongPress` on destructive confirm, only if `settings.haptics`.
12. **Reduce motion:** when `LocalReduceMotion.current` is true, use `snap()` for all of the above and don't start infinite animations.
Performance guard: animations only run while the screen is `RESUMED` (`LocalLifecycleOwner` + `repeatOnLifecycle`); never animate inside `LazyColumn` items using infinite transitions (only the single hero element per screen).

## 10. Theme wrapper & XML theme

```kotlin
@Composable fun SafaiTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; else -> isSystemInDarkTheme() }
    val scheme = /* choose per section 3 */
    val reduce = settings.reduceMotion || rememberAnimatorScaleZero()
    CompositionLocalProvider(LocalReduceMotion provides reduce, LocalSpacing provides Spacing) {
        MaterialTheme(colorScheme = scheme, typography = SafaiTypography, shapes = SafaiShapes, content = content)
    }
    // + SideEffect to set status/navigation bar icon colors: WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
}
```
`res/values/themes.xml`:
```xml
<resources>
  <style name="Theme.Safai" parent="android:Theme.Material.NoActionBar">
    <item name="android:windowBackground">#000000</item>
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
    <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
  </style>
  <style name="Theme.Safai.Splash" parent="Theme.Safai" />
</resources>
```
(`windowLayoutInDisplayCutoutMode=shortEdges` lets the black background extend around the punch-hole; Compose insets keep content below it.) In `res/values-night` keep the same. In `res/values-v31/themes.xml` add to `Theme.Safai.Splash`: `<item name="android:windowSplashScreenBackground">#000000</item>`.

## 11. App icon (adaptive, themed, local vector)

`res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` (identical):
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```
`res/drawable/ic_launcher_background.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
  <path android:pathData="M0,0h108v108h-108z">
    <aapt:attr name="android:fillColor">
      <gradient android:startX="0" android:startY="0" android:endX="108" android:endY="108"
          android:startColor="#0F766E" android:endColor="#5B4BD1" android:type="linear"/>
    </aapt:attr>
  </path>
</vector>
```
`res/drawable/ic_launcher_foreground.xml` (two stacked "cards" + check = duplicates cleaned):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
  <path android:fillColor="#66FFFFFF"
      android:pathData="M38,30h26a6,6 0 0 1 6,6v26a6,6 0 0 1 -6,6h-26a6,6 0 0 1 -6,-6v-26a6,6 0 0 1 6,-6z"/>
  <path android:fillColor="#FFFFFF"
      android:pathData="M44,38h26a6,6 0 0 1 6,6v26a6,6 0 0 1 -6,6h-26a6,6 0 0 1 -6,-6v-26a6,6 0 0 1 6,-6z"/>
  <path android:strokeColor="#0F766E" android:strokeWidth="5" android:strokeLineCap="round" android:strokeLineJoin="round"
      android:pathData="M48,57l7,7l13,-14"/>
</vector>
```
App name in `strings.xml`: `<string name="app_name">Safai</string>`.

## 12. Visual quality checklist
- No element closer than 16 dp to the screen edge; no text overlapping the punch-hole.
- Consistent 12 dp card gaps; icons aligned to a 24 dp grid; one primary button per screen section.
- Contrast ≥ 4.5:1 for body text in both themes.
- Skeleton shimmers (not spinners) while Room flows load for the first time.
- Press, selection, navigation and result animations all present; nothing janky at 60/120 Hz (the Infinix panel may be 120 Hz; do not hard-code frame timing).
