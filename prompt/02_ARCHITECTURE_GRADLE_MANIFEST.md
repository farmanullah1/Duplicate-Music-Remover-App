# 02 — ARCHITECTURE, GRADLE, MANIFEST, DI

## 1. Package layout (under `app/src/main/java/com/example/`)

```
SafaiApp.kt                      // @HiltAndroidApp Application
MainActivity.kt                  // @AndroidEntryPoint, edge-to-edge, hosts NavHost
core/
  Formatters.kt                  // formatBytes(), formatDuration(), formatDate()
  Result.kt                      // sealed class Outcome<T>
  Dispatchers.kt                 // @IoDispatcher, @DefaultDispatcher qualifiers
  StorageAccess.kt               // permission state helpers
data/
  db/ AppDatabase.kt, Dao files, Entity files   (file 03)
  settings/ SettingsRepository.kt (DataStore)   (file 03)
  scan/ FileScanner.kt, MediaStoreScanner.kt, Hasher.kt, MetadataReader.kt, PerceptualHash.kt (file 04)
  trash/ RecycleBinManager.kt, FileOps.kt        (file 05)
  junk/ JunkScanner.kt                           (file 05)
  storage/ StorageAnalyzer.kt, ImageQuality.kt                          (file 06)
domain/
  model/ MediaFile.kt, DuplicateGroup.kt, MediaCategory.kt, MatchType.kt, JunkItem.kt
  usecase/ ScanUseCase.kt, DetectDuplicatesUseCase.kt, RecommendKeeperUseCase.kt, TrashUseCase.kt ...
di/ AppModule.kt                 // Hilt providers
service/ ScanService.kt (foreground), ScanWorker.kt
ui/
  theme/ Color.kt, Theme.kt, Type.kt, Shape.kt, Motion.kt
  components/ (file 07 + 08)
  nav/ Routes.kt, SafaiNavHost.kt, AdaptiveScaffold.kt
  home/ HomeScreen + HomeViewModel
  duplicates/ DuplicatesScreen, CompareScreen, + ViewModels
  junk/ JunkScreen + ViewModel
  storage/ StorageScreen + ViewModel
  recyclebin/ RecycleBinScreen + ViewModel
  settings/ SettingsScreen + ViewModel
  onboarding/ OnboardingScreen
widget/ (file 09)
```

Delete these old files: `util/DemoAudioGenerator.kt`, `util/AudioSynthesizer.kt`, `util/GeminiMusicService.kt`, `audio/AudioPlayerController.kt` (rewrite with Media3), `ui/components/MetadataEditorDialog.kt`, `ui/screens/AiMusicStudioScreen.kt`, `ui/MainViewModel.kt` (split).

## 2. Gradle (version catalog) — keep the project's existing versions

`libs.versions.toml` — ensure these exist (use the project's version if it already has one equal or newer; the values below are **minimum known-good**):

```toml
[versions]
agp = "8.7.3"                # keep project's
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
composeBom = "2024.12.01"
coreKtx = "1.15.0"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
navigationCompose = "2.8.5"
hilt = "2.52"
hiltNavCompose = "1.2.0"
room = "2.6.1"
datastore = "1.1.1"
workManager = "2.10.0"
media3 = "1.5.0"
coil = "2.7.0"
glance = "1.1.1"
windowSizeClass = "1.3.1"      # androidx.compose.material3:material3-window-size-class
coroutines = "1.9.0"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material3-wsc = { module = "androidx.compose.material3:material3-window-size-class" }
compose-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
compose-animation = { module = "androidx.compose.animation:animation" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavCompose" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "workManager" }
hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "hiltNavCompose" }
hilt-work-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "hiltNavCompose" }
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-common = { module = "androidx.media3:media3-common", version.ref = "media3" }
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
coil-video = { module = "io.coil-kt:coil-video", version.ref = "coil" }
glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
glance-material3 = { module = "androidx.glance:glance-material3", version.ref = "glance" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
```

`app/build.gradle.kts` essentials:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)      // Kotlin 2.0+ compose compiler plugin
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "com.example"
    defaultConfig {
        applicationId = "com.aistudio.duplicatemusic.remover"
        minSdk = 26
        // keep compileSdk/targetSdk already in the project
        versionCode = 2
        versionName = "2.0"
    }
    buildTypes {
        release { isMinifyEnabled = true; isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui); implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3); implementation(libs.compose.material3.wsc)
    implementation(libs.compose.icons.extended); implementation(libs.compose.animation)
    implementation(libs.androidx.core.ktx); implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose); implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android); ksp(libs.hilt.compiler); implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work); ksp(libs.hilt.work.compiler)
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.media3.exoplayer); implementation(libs.media3.common)
    implementation(libs.coil.compose); implementation(libs.coil.video)
    implementation(libs.glance.appwidget); implementation(libs.glance.material3)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
}
```

**Remove** from dependencies: retrofit, okhttp, moshi, firebase-*, accompanist-*, roborazzi, robolectric (keep only plain JUnit), media3-ui (not needed).
Note: do NOT add `kotlin-parcelize`/`kotlinx-serialization` unless a phase asks for it.

## 3. AndroidManifest.xml (complete)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Storage -->
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />
    <!-- Needed to scan/clean documents, junk, and all folders (user grants in system settings) -->
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />

    <!-- Notifications + foreground scan -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:name=".SafaiApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:requestLegacyExternalStorage="true"
        android:theme="@style/Theme.Safai">

        <activity android:name=".MainActivity" android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode"
            android:theme="@style/Theme.Safai.Splash">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />
        </activity>

        <service android:name=".service.ScanService" android:exported="false"
            android:foregroundServiceType="dataSync" />

        <!-- Widgets (file 09) -->
        <receiver android:name=".widget.StorageWidgetReceiver" android:exported="true">
            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/storage_widget_info" />
        </receiver>
        <receiver android:name=".widget.QuickActionsWidgetReceiver" android:exported="true">
            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/quick_actions_widget_info" />
        </receiver>
        <receiver android:name=".widget.CleanedWidgetReceiver" android:exported="true">
            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/cleaned_widget_info" />
        </receiver>
        <provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider"
            android:exported="false" android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
```

Notes: no INTERNET permission. `MANAGE_EXTERNAL_STORAGE` is intended for personal/sideloaded use (Google Play may restrict it — mention this in a code comment only).

## 4. Application + DI

```kotlin
@HiltAndroidApp
class SafaiApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration get() =
        Configuration.Builder().setWorkerFactory(workerFactory).build()
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)   // channels: scan_progress (low), cleanup (default)
    }
}
```

Disable default WorkManager initializer in the manifest when using `Configuration.Provider`:
```xml
<provider android:name="androidx.startup.InitializationProvider" android:authorities="${applicationId}.androidx-startup" android:exported="false" tools:node="merge">
    <meta-data android:name="androidx.work.WorkManagerInitializer" android:value="androidx.startup" tools:node="remove" />
</provider>
```

`AppModule` (`@Module @InstallIn(SingletonComponent::class)`) provides: `AppDatabase` (Room.databaseBuilder, name `"safai_db"`, **no destructive fallback**, `addMigrations(...)`), all DAOs, `SettingsRepository`, `FileOps`, `RecycleBinManager`, `Hasher`, `FileScanner`, `JunkScanner`, `@IoDispatcher CoroutineDispatcher = Dispatchers.IO`, `@DefaultDispatcher = Dispatchers.Default`.

`NotificationChannels.create`: two channels — `scan_progress` (IMPORTANCE_LOW) and `cleanup` (IMPORTANCE_DEFAULT). Channel names/descriptions from strings.xml.

## 5. Permission flow (implement in `core/StorageAccess.kt`)

```kotlin
object StorageAccess {
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else false

    fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun hasMediaPermissions(ctx: Context) = mediaPermissions().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    fun allFilesIntent(ctx: Context) = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${ctx.packageName}"))

    enum class Mode { FULL, MEDIA_ONLY, NONE }
    fun mode(ctx: Context): Mode = when {
        hasAllFilesAccess() || Build.VERSION.SDK_INT < 30 && hasMediaPermissions(ctx) -> Mode.FULL
        hasMediaPermissions(ctx) -> Mode.MEDIA_ONLY
        else -> Mode.NONE
    }
}
```

- **FULL** = real `java.io.File` scanning/deleting of everything (documents, junk, all folders). On API < 30 with READ/WRITE_EXTERNAL_STORAGE granted, `File` access also works.
- **MEDIA_ONLY** = only audio/images/video via MediaStore; deletion uses `MediaStore.createDeleteRequest` (API 30+). Documents and Junk screens show a clear card: "Allow 'All files access' to scan documents and junk" with a button that launches `allFilesIntent`.
- **NONE** = onboarding permission screen.
- Re-check mode in `onResume` (user returns from Settings).
- `POST_NOTIFICATIONS` (API 33+) is requested only when the user starts a background scan or turns on the cleanup reminder (with a rationale dialog first).
