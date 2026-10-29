# 09 — HOME-SCREEN WIDGETS (Jetpack Glance, fully local)

Three widgets, all reading real data from Room / DataStore / StatFs. No network, no fake values. Dependencies already in file 02 (`glance-appwidget`, `glance-material3`).

| Widget | Size | Shows | Tap |
|---|---|---|---|
| **Storage** | 2×2 (resizable to 4×2) | % used, free space, progress bar, duplicate groups + reclaimable size | opens Home |
| **Quick Actions** | 4×1 | 4 buttons: Scan · Duplicates · Junk · Storage | opens the matching screen |
| **Cleaned** | 2×1 (resizable to 4×1) | total space freed so far, last scan "2 days ago", **Scan** button | Scan → starts scan flow |

## 1. Widget data (outside Hilt via an EntryPoint)

```kotlin
@EntryPoint @InstallIn(SingletonComponent::class)
interface WidgetEntryPoint { fun stats(): StatsRepository; fun settings(): SettingsRepository }
// StatsRepository must expose: suspend fun snapshot(): HomeStats   (one-shot version of the Home flow; see file 03)

object WidgetData {
    suspend fun stats(ctx: Context): HomeStats =
        EntryPointAccessors.fromApplication(ctx, WidgetEntryPoint::class.java).stats().snapshot()
    fun relative(ctx: Context, ms: Long?): String { // "Never scanned" / "Today" / "Yesterday" / "5 days ago"
        if (ms == null) return ctx.getString(R.string.widget_never_scanned)
        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - ms).toInt()
        return when (days) { 0 -> ctx.getString(R.string.widget_today); 1 -> ctx.getString(R.string.widget_yesterday)
            else -> ctx.getString(R.string.widget_days_ago, days) } }
}
```
Format sizes with the same `Formatters.formatBytes` used in the app.

## 2. Shared resources

`res/drawable/widget_bg_dark.xml`
```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="315" android:startColor="#0B3B36" android:centerColor="#0E2A47" android:endColor="#1B1646"/>
    <corners android:radius="24dp"/>
</shape>
```
`res/drawable/widget_bg_surface.xml`: same shape with `<solid android:color="#0B0F14"/>`, `<stroke android:width="1dp" android:color="#263241"/>`, radius 24 dp. `res/drawable/widget_chip.xml`: rounded rect radius 16 dp, `<solid android:color="#1F2A37"/>`.
Vector icons (24×24, fill white, tinted at runtime): `ic_w_scan` (`M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z`), `ic_w_copy` (`M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z`), `ic_w_clean` (`M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z`), `ic_w_storage` (`M2,20h20v-4H2v4zM4,17h2v2H4v-2zM2,4v4h20V4H2zM6,7H4V5h2v2zM2,14h20v-4H2v4zM4,11h2v2H4v-2z`). Each: `<vector android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFF" android:pathData="…"/></vector>`.

```kotlin
fun openApp(ctx: Context, route: String) = actionStartActivity(
    Intent(ctx, MainActivity::class.java).apply {
        putExtra("route", route); flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP })
```
`MainActivity` reads `route` in `onCreate` **and** `onNewIntent`: `home`, `duplicates`, `junk`, `storage`, `recycle_bin`, `scan` (navigates Home then calls `HomeViewModel.requestScan()`).
Glance colors: `import androidx.glance.unit.ColorProvider`; white `ColorProvider(Color.White)`, dim `ColorProvider(Color(0xFFB7C4D1))`, teal `ColorProvider(Color(0xFF2DD4BF))`, gold `ColorProvider(Color(0xFFF5C451))`, track `ColorProvider(Color(0xFF263241))`.

## 3. Storage widget (complete code)

```kotlin
class StorageWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetData.stats(context)
        provideContent { Content(context, s) }
    }

    @Composable private fun Content(ctx: Context, s: HomeStats) {
        val usedFraction = if (s.totalBytes > 0) 1f - s.freeBytes / s.totalBytes.toFloat() else 0f
        val wide = LocalSize.current.width >= 250.dp
        Column(
            modifier = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_dark))
                .padding(14.dp).clickable(openApp(ctx, "home")),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(ctx.getString(R.string.widget_storage_title), style = TextStyle(color = ColorProvider(Color(0xFFB7C4D1)), fontSize = 11.sp, fontWeight = FontWeight.Medium))
            Text("${(usedFraction * 100).roundToInt()}% " + ctx.getString(R.string.widget_used),
                style = TextStyle(color = ColorProvider(Color.White), fontSize = if (wide) 26.sp else 22.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(progress = usedFraction, modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = ColorProvider(Color(0xFF2DD4BF)), backgroundColor = ColorProvider(Color(0xFF263241)))
            Spacer(GlanceModifier.height(6.dp))
            Text(ctx.getString(R.string.widget_free, Formatters.formatBytes(s.freeBytes)),
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp))
            Text(if (s.lastScanAt == null) ctx.getString(R.string.widget_tap_to_scan)
                 else ctx.getString(R.string.widget_duplicates_line, s.openGroups, Formatters.formatBytes(s.reclaimableBytes)),
                style = TextStyle(color = ColorProvider(Color(0xFFF5C451)), fontSize = 11.sp), maxLines = 1)
        }
    }
}
class StorageWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StorageWidget() }
```
`res/xml/storage_widget_info.xml`:
```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp" android:minHeight="110dp" android:targetCellWidth="2" android:targetCellHeight="2"
    android:minResizeWidth="110dp" android:minResizeHeight="110dp" android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000" android:widgetCategory="home_screen"
    android:initialLayout="@layout/glance_default_loading_layout" android:description="@string/widget_storage_desc" />
```
Strings: `widget_storage_title`="Storage", `widget_used`="used", `widget_free`="%1$s free", `widget_tap_to_scan`="Tap to scan", `widget_duplicates_line`="%1$d duplicate groups · %2$s", `widget_never_scanned`="Never scanned", `widget_today`="Today", `widget_yesterday`="Yesterday", `widget_days_ago`="%1$d days ago", `widget_storage_desc`="Storage usage and reclaimable space".

## 4. Quick Actions widget (4×1)
`SizeMode.Single`. Layout: `Row` (surface background `widget_bg_surface`, padding 8 dp) with four cells `GlanceModifier.defaultWeight()`, each `Column(horizontalAlignment = CenterHorizontally)`: a 40 dp `Box` (`widget_chip` background, centered) holding `Image(ImageProvider(R.drawable.ic_w_…), contentDescription, colorFilter = ColorFilter.tint(ColorProvider(teal)), modifier = size(22.dp))`, and a 10 sp label below. Actions: Scan (`ic_w_scan`, `openApp(ctx,"scan")`), Duplicates (`ic_w_copy`, `"duplicates"`), Junk (`ic_w_clean`, `"junk"`), Storage (`ic_w_storage`, `"storage"`). Receiver `QuickActionsWidgetReceiver`; XML `quick_actions_widget_info.xml` (`targetCellWidth=4 targetCellHeight=1 minWidth=250dp minHeight=56dp`, resizeMode horizontal).

## 5. "Cleaned" widget (2×1 → 4×1)
`SizeMode.Responsive(setOf(DpSize(110.dp, 56.dp), DpSize(250.dp, 56.dp)))`. Left: tiny label "Freed so far", big `Formatters.formatBytes(s.totalFreedBytes)` (gold, bold; when 0 show "0 B"), below `WidgetData.relative(ctx, s.lastScanAt)` ("Last scan: 2 days ago"). Right (wide only): a teal rounded **Scan** button (`Box` with `widget_chip`-style background + `ic_w_scan`) → `openApp(ctx,"scan")`. Whole widget background `widget_bg_dark`; tap on the left part → `"home"`. Receiver `CleanedWidgetReceiver`; XML `cleaned_widget_info.xml` (`targetCellWidth=2 targetCellHeight=1`).

## 6. Keeping widgets fresh (all must exist)
```kotlin
object WidgetUpdater {
    suspend fun updateAll(ctx: Context) { StorageWidget().updateAll(ctx); QuickActionsWidget().updateAll(ctx); CleanedWidget().updateAll(ctx) }
}
```
Call `WidgetUpdater.updateAll` at: end of every scan, after every trash/delete/restore/clean, after Smart Clean, and from `WidgetRefreshWorker` (`PeriodicWorkRequest`, 30 minutes, unique name `widget_refresh`, enqueued in `SafaiApp.onCreate`) so free space stays current. `updatePeriodMillis` is only a backup.

## 7. Checks
- All three appear in the launcher's widget picker (titles: "Storage", "Quick actions", "Cleaned").
- Numbers equal the Home screen's numbers.
- Tapping each widget (or button) opens the right screen even when the app is closed.
- Readable on black and colorful wallpapers (dark gradient background, text contrast ≥ 4.5:1).
