# Duplicate Music Remover — Project Status Report

**Document Date:** October 27, 2026  
**Application Version:** 1.0 (Version Code: 1)  
**Target Platform:** Android 7.0+ (API 24 to API 36)  
**Package / Application ID:** `com.aistudio.duplicatemusic.remover` (Namespace: `com.example`)  
**Project Status:** Active, Fully Compilable, and Polished (Phase 1, 2, and 3 Implemented with Responsive M3 UI)

---

## 1. Project Purpose & High-Level Overview

**Duplicate Music Remover** is a modern, high-performance Android utility and audio optimization studio designed to help users identify, acoustically compare, manage, and safely clean duplicate and redundant audio files across device storage.

In addition to detecting duplicates through multiple strategies (exact SHA-256 binary hash matching, filename variation analysis, and embedded ID3 metadata tag similarity), the application includes:
- **Side-by-Side Dual Waveform & Multi-Track Comparison:** Real-time audio scrubbing, multi-channel A/B synchronized playback, and technical parameter matrices.
- **Storage Analytics Breakdown:** Visual representations of storage allocation, duplicate waste, audio format distribution, and bitrate fidelity tiers.
- **On-Device ID3 Metadata Editor:** Allows users to inspect and update Track Title, Artist, Album, and Genre tags with 1-tap smart autofill from filenames.
- **Staged Recycle Bin:** Safety mechanism that stages deleted tracks locally in an app-managed recycle bin with batch restore and permanent deletion capabilities.
- **AI Music Generation Studio:** Generative music composition module utilizing Gemini AI and synthesis engines to create original audio tracks directly on device.
- **Responsive Material Design 3 (M3) UI:** Adaptive layout supporting phones, foldables, and tablets (with NavigationRail on displays $\ge$ 720dp and max-width container constraints), AMOLED dark styling, crisp card borders, and touch-accessible components.

---

## 2. Original Prompts & Request History

### Original Creation & Milestone Requests

#### Core Expansion & Upgrade Prompt (from implementation plan)
> "Transform Duplicate Music Remover into a premier audio optimization and studio utility featuring precision acoustic waveform comparison, interactive storage analytics visualization, built-in ID3 tag & metadata inspection, scheduled automated cleanup rules, and AI music generation powered by Gemini & Lyria audio preview models."

#### Subsequent Iteration & UX Polish Prompts
> - *"Proceed"* (Approved implementation plan covering precision comparison, storage analytics, tag editing, batch presets, and AI studio)
> - *"improve the ui and ux and polish the app"*
> - *"Give every element proper padding, margin, and border, and make the app responsive.”*
> - *"Give every element proper padding, margin, and border; give proper sizing to buttons, text, and other elements; and make the app responsive."*
> - *"Give every element proper padding, margin, and border. Give proper sizing to buttons, text, and other elements. Use appropriate screen sizes to ensure responsiveness on all screens, and make the app responsive"*

---

## 3. Current Look and Feel (UI / UX)

### Design Language & System
- **Design Standard:** Material Design 3 (M3) with pure AMOLED dark accents, vibrant cyan/indigo telemetry highlights, and emerald/crimson status indicators.
- **Color Tokens:**
  - `PrimaryIndigo` (`#6366F1`): Primary brand actions, headers, and navigation accents.
  - `PrimaryCyan` (`#06B6D4` / `#38BDF8`): Data telemetry, format badges, and audio scrubbers.
  - `EmeraldKeep` (`#10B981`): Recommended keeper files, highest fidelity badges, restored items, and storage savings.
  - `CrimsonDelete` (`#EF4444` / `#F43F5E`): Duplicate markers, batch removal actions, and destructive confirmation dialogues.
- **Component Geometry & Borders:**
  - Cards feature `RoundedCornerShape(16.dp)` or `RoundedCornerShape(20.dp)` with consistent 1dp subtle borders (`MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)` or semantic color accents).
  - All interactive buttons have explicit minimum heights (`heightIn(min = 44.dp)` or `heightIn(min = 48.dp)`) to strictly satisfy 48dp touch accessibility standards.
  - Text fields and selection chips utilize rounded borders, leading icons, and clear typography hierarchy.

### Responsiveness & Adaptive Screen Strategy
- **Compact Handheld Displays (< 720dp):** Uses an edge-to-edge `Scaffold` with a bottom `NavigationBar` with subtle border separation, full-width fluid cards, and vertically scrollable lists.
- **Medium & Wide Displays / Tablets / Landscape ( $\ge$ 720dp):**
  - Switches from bottom navigation to a persistent left-hand `NavigationRail` separated by a crisp `VerticalDivider`.
  - Main screen content is horizontally centered within a bounded viewport (`widthIn(max = 1000.dp)` or `widthIn(max = 800.dp)` for Settings) to prevent content stretching on 10-inch tablets and foldables.
  - On the **Scan Dashboard**, wide displays ($\ge$ 840dp) render a balanced 2-column dashboard layout (Scanner Header + Space Recovered on the left; Storage Distribution Chart + Filters on the right).

---

## 4. Main Screens & Implemented Features

### 4.1. Scan & Overview Dashboard (`ScanDashboardScreen.kt`)
- **Storage Permission Banner:** Prompts for Android media access permissions (`READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`) with an instant demo mode fallback.
- **Dynamic Scanner Header (`ScanProgressHeader.kt`):**
  - Animated pulsing radar badge that pulses during active multi-phase scanning.
  - Real-time progress percentage, processed count, phase indicator (Scanning Storage, Analyzing Metadata, Computing Hashes, Grouping Duplicates), and currently inspected file path.
  - Summary metric badges: *Files Scanned*, *Duplicates*, and *Potential Savings*.
  - Primary Action Buttons: "Scan Storage" and "Auto-Clean All".
- **Storage Space Recovered Card (`StorageRecoveredDashboardCard`):** Displays cumulative freed storage and count of files moved to the Recycle Bin.
- **Audio Storage Allocation & Fidelity Breakdown (`StorageBreakdownChart.kt`):**
  - Segmented horizontal visual bar chart displaying file distribution by format (MP3, FLAC, WAV, M4A, AAC, OGG).
  - Interactive format filter chips with color-coded dot badges and byte percentages.
  - Audio Fidelity Distribution cards categorizing files into *Hi-Res / Lossless*, *High Quality (320k)*, *Standard (128-192k)*, and *Compressed (<128k)*.
- **Duplicate Detection Strategy Toggles (`ScannerFiltersCard`):**
  - *Filename & Numbered Suffixes*: Detects copies like `song(1).mp3`, `song - copy.mp3`.
  - *Embedded ID3 Metadata*: Compares Title, Artist, Album, and Duration embedded audio tags.
  - *Content Hash Fingerprinting*: Compares SHA-256 byte hashes to catch exact bit-for-bit duplicates with different names.
- **Preview of Top Duplicate Groups:** Quick-action cards displaying the first 5 detected duplicate sets with direct compare and clean shortcuts.

### 4.2. Duplicate Results & Management (`DuplicateResultsScreen.kt`)
- **Search & Filter Bar:** Instant real-time search across file names, artists, formats, and folders with clear button.
- **Batch Selection Action Presets (`LazyRow`):**
  - *Select All / Deselect All Duplicates* button.
  - *Keep Best Bitrate*: Automatically selects lower bitrate duplicates in every group for deletion while preserving the highest-quality version.
  - *Purge Copies (Keep Oldest)*: Automatically flags numbered copies and newest duplicates, keeping original recordings.
  - *Keep Complete Tags*: Prioritizes audio files with embedded artist/album metadata over untagged files.
  - *Keep Newest*: Selects older duplicate revisions for removal.
  - *Invert Selection*: Flips currently selected duplicate checkboxes.
- **Filter Chips Bar:** Filter by Match Type (*All Matches*, *Filename Variations*, *Exact Hash Matches*, *ID3 Tag Matches*) and sort by *Potential Savings*, *File Count*, or *Group Name*.
- **Duplicate Group Cards (`DuplicateGroupCard.kt`):**
  - Displays match type badges and calculated potential byte savings.
  - Per-file rows with status badges (`KEEP ORIGINAL` vs `TO DELETE`), format, bitrate kbps, and file size.
  - Metadata Tag Edit shortcut button (`edit_tags_...`).
  - Side-by-Side Comparison trigger button (`compare_group_...`).
  - Single-click "Clean Duplicate" action with deletion confirmation.
- **Floating Batch Delete Action Bar:** Slides up smoothly when duplicate tracks are selected, indicating the total count and space to be freed with "Clear" and "Delete Selected" buttons.

### 4.3. Side-by-Side Comparison Dialog (`SideBySideCompareDialog.kt`)
- **Full-Screen Responsive Surface:** Constrained to `fillMaxWidth(0.96f)` and `widthIn(max = 940.dp)` with rounded corners and border.
- **Similarity & Match Explanation:** Displays similarity match percentage score and explains why files were paired.
- **Synchronized Multi-Track Audio Player:**
  - Multi-channel selector buttons to toggle playback between File 1, File 2, etc.
  - Waveform visualizer displaying playback position and timeline scrubber.
- **Side-by-Side File Panes (`FilePaneCard`):**
  - Individual cards for each audio track showing pane label, "Selected to Keep" radio toggle, file name, format, bitrate, sample rate, and channels.
  - Green highlights on the superior technical attributes (higher bitrate, larger sample rate).
- **Technical Parameter Comparison Matrix:** Comparative table contrasting File Name, Folder, Size, Duration, Audio Format, Bitrate, and SHA-256 Hash.
- **Bottom Resolution Bar:** "Keep Both (False Duplicate)" button and "Delete Duplicates" button.

### 4.4. AI Music Generation Studio (`AiMusicStudioScreen.kt`)
- **Studio Header:** Branded gradient card with AI Studio iconography.
- **Prompt & Musical Vision Input:** Multiline text field with quick inspiration chips (*Lo-Fi Chill*, *Cyberpunk Synthwave*, *Acoustic Sunrise*, *Deep Ambient*, *8-Bit Chiptune*, *Jazz Cafe*).
- **Genre & Style Chips:** Selectable musical styles that configure generator parameters.
- **Tempo & Duration Controls:** BPM Slider (70 to 150 BPM) and duration selectors (15s / 30s).
- **Audio Synthesizer Engine (`AudioSynthesizer.kt` & `GeminiMusicService.kt`):** Generates valid, playable WAV audio files with chord progressions, rhythmic pulses, and melody harmonics matching prompt and BPM specs.
- **Generated Track Player Card:**
  - Track title, artist, BPM, and musical key display.
  - Animated multi-bar waveform visualizer canvas.
  - Play/pause toggle button and playback timeline scrubber.
  - "Save to Library" button that commits the generated audio file to local storage and updates the library scanner.

### 4.5. Recycle Bin & Safe Recovery (`RecycleBinScreen.kt`)
- **Staging Mechanism:** Deleted files are staged in the application's local cache directory (`trashed_audio/`) and tracked in the Room database before any permanent file deletion.
- **Header & Metric Counters:** Total marked files count and storage occupied.
- **Selection Mode:** Multi-item checkbox selection for batch operations.
- **Batch Actions:**
  - *Batch Restore*: Returns selected files to their original storage paths.
  - *Batch Delete*: Permanently deletes selected files from the device.
  - *Empty Bin*: Cleans all staged items in 1 click.
- **Trashed Item Cards (`TrashedItemCard`):** Displays item name, original path, file size, bitrate, and deletion timestamp.

### 4.6. Settings & Safety Screen (`SettingsScreen.kt`)
- **Permission Management Card:** Status indicators for Storage/Audio permissions and Notification permissions with request button.
- **Demo Media Settings:** Switch to enable or disable built-in sample duplicate tracks for testing in environments without local audio files.
- **Recycle Bin Protection Notice:** Informs users about deletion staging safety and file recovery options.

---

## 5. Architecture & Code Organization

The application follows the **Clean Architecture / MVVM (Model-View-ViewModel)** architectural pattern.

### 5.1. Module & Directory Structure

```
app/src/main/java/com/example/
├── MainActivity.kt                       # Entry activity, responsive navigation layout (Rail vs BottomBar)
├── audio/
│   └── AudioPlayerController.kt          # MediaPlayer controller, dual-track synchronization & scrubber
├── data/
│   └── local/
│       ├── AppDatabase.kt                # Room database definition (version 2)
│       ├── AudioFileDao.kt               # DAO for scanned audio metadata cache
│       ├── AudioFileEntity.kt            # Entity for audio files cache
│       ├── RecycleBinDao.kt              # DAO for staged deleted tracks
│       ├── RecycleBinEntity.kt           # Entity for recycle bin items
│       ├── WhitelistedDao.kt             # DAO for ignored duplicate pairs
│       └── WhitelistedPairEntity.kt      # Entity for whitelisted/false-positive pairs
├── domain/
│   └── usecase/
│       └── ScanAudioFilesUseCase.kt      # Duplicate detection algorithms & grouping logic
├── model/
│   ├── AudioFile.kt                      # Domain model representing an audio file on disk
│   ├── DuplicateGroup.kt                 # Model for a group of duplicate audio tracks
│   ├── MatchType.kt                      # Enum for match strategy (EXACT_HASH, FILENAME_VARIATION, METADATA_MATCH)
│   ├── ScanConfig.kt                     # Scanner configuration (strategies, demo mode, format filters)
│   └── ScanProgress.kt                   # Progress state (phase, counts, percentages, savings)
├── repository/
│   └── AudioScannerRepository.kt         # MediaStore queries, hash computation, and demo file provider
├── ui/
│   ├── MainViewModel.kt                  # Central ViewModel managing state flows, scan, playback, and recycle bin
│   ├── components/
│   │   ├── AudioPreviewCard.kt           # Quick audio preview player component
│   │   ├── AudioWaveformVisualizer.kt    # Custom Canvas-drawn waveform scrubber
│   │   ├── DeleteConfirmationDialog.kt   # Dialog for Recycle Bin vs Permanent Delete
│   │   ├── DuplicateGroupCard.kt         # Card for individual duplicate groups with badges & file items
│   │   ├── MetadataEditorDialog.kt       # Tag editor bottom sheet / dialog with auto-parse
│   │   ├── ScanProgressHeader.kt         # Pulsing radar header and scan metrics
│   │   ├── SideBySideCompareDialog.kt    # Multi-track side-by-side comparison modal
│   │   ├── StorageBreakdownChart.kt      # Formats and quality tier distribution charts
│   │   └── StoragePermissionHandler.kt   # Permission request handler
│   ├── screens/
│   │   ├── AiMusicStudioScreen.kt        # Generative music prompt, synthesis, and player screen
│   │   ├── DuplicateResultsScreen.kt     # Filterable, sortable duplicate review list screen
│   │   ├── RecycleBinScreen.kt           # Staged deletions, restore, and empty bin screen
│   │   ├── ScanDashboardScreen.kt        # Overview dashboard, analytics, and strategy screen
│   │   └── SettingsScreen.kt             # Permissions and demo track toggles screen
│   └── theme/
│       ├── Color.kt                      # Color palette (PrimaryCyan, PrimaryIndigo, EmeraldKeep, CrimsonDelete)
│       ├── Theme.kt                      # M3 Dark ColorScheme and theme wrapper
│       └── Type.kt                       # Material 3 typography definitions
└── util/
    ├── AudioHashUtility.kt               # Streaming SHA-256 audio file content hashing
    ├── AudioSynthesizer.kt               # Mathematical PCM-to-WAV synthesis for AI Studio
    ├── DemoAudioGenerator.kt             # Built-in sample audio file generator for testing
    ├── GeminiMusicService.kt             # AI Studio logic and track library exporter
    ├── MediaStoreDeleteHelper.kt         # Android 11+ MediaStore Trash & IntentSender handler
    └── PermissionUtils.kt                # SDK-aware runtime permission checkers
```

---

## 6. Database Structure & Persistence

The application uses **Room Database** (SQLite abstraction) for persistent local storage.

### Database Definition
- **Database Class:** `com.example.data.local.AppDatabase`
- **Database Name:** `"duplicate_music_db"`
- **Schema Version:** `2` (configured with `fallbackToDestructiveMigration`)

### Entity Tables

#### 1. Table: `recycle_bin` (`RecycleBinEntity.kt`)
Stores files moved to the recycle bin so they can be restored or permanently purged.

| Field Name | Type | Key | Description |
|---|---|---|---|
| `id` | `TEXT` | Primary Key | Unique identifier of the trashed file |
| `originalPath` | `TEXT` | | Absolute file path where the file resided before deletion |
| `cachedTrashPath` | `TEXT` | | Staging path inside app cache directory |
| `displayName` | `TEXT` | | File name (e.g., `track(1).mp3`) |
| `title` | `TEXT` | | Embedded song title |
| `artist` | `TEXT` | | Embedded artist name |
| `sizeBytes` | `INTEGER` | | Size of the audio file in bytes |
| `durationMs` | `INTEGER` | | Playback duration in milliseconds |
| `bitrateKbps` | `INTEGER` | | Bitrate in kilobits per second (e.g., 320) |
| `format` | `TEXT` | | Extension/format (MP3, WAV, FLAC, M4A) |
| `deletedTimestampMs` | `INTEGER` | | Epoch timestamp when the file was moved to the bin |

#### 2. Table: `whitelisted_pairs` (`WhitelistedPairEntity.kt`)
Stores pairs of files that the user marked as "Keep Both / False Duplicate" so they are ignored in future scans.

| Field Name | Type | Key | Description |
|---|---|---|---|
| `fileIdA` | `TEXT` | Composite PK | First file ID in the whitelisted pair |
| `fileIdB` | `TEXT` | Composite PK | Second file ID in the whitelisted pair |
| `timestampMs` | `INTEGER` | | Epoch timestamp when pair was whitelisted |

#### 3. Table: `scanned_audio_files` (`AudioFileEntity.kt`)
Local cache of scanned audio metadata to accelerate rescanning across large media libraries.

| Field Name | Type | Key | Description |
|---|---|---|---|
| `id` | `TEXT` | Primary Key | File identifier / MediaStore ID |
| `displayName` | `TEXT` | | Audio file name |
| `absolutePath` | `TEXT` | | Path on device storage |
| `uriString` | `TEXT` | | MediaStore URI string (nullable) |
| `sizeBytes` | `INTEGER` | | File size in bytes |
| `durationMs` | `INTEGER` | | Duration in milliseconds |
| `title` | `TEXT` | | Tag title |
| `artist` | `TEXT` | | Tag artist |
| `album` | `TEXT` | | Tag album |
| `genre` | `TEXT` | | Tag genre |
| `bitrateKbps` | `INTEGER` | | Bitrate in kbps |
| `sampleRateHz` | `INTEGER` | | Sample rate (e.g., 44100 Hz) |
| `format` | `TEXT` | | Audio codec / container |
| `dateModifiedMs` | `INTEGER` | | File last modification timestamp |
| `contentHash` | `TEXT` | | SHA-256 partial content hash |
| `isDemoFile` | `INTEGER` (Boolean) | | Flag indicating built-in testing demo file |

---

## 7. Technology Stack & Key Dependencies

- **Language:** Kotlin 2.0+ (100% Kotlin DSL Gradle scripts)
- **UI Toolkit:** Jetpack Compose with Material Design 3 (M3)
- **Lifecycle & Architecture:** AndroidX Lifecycle ViewModel Compose, StateFlow, Coroutines
- **Local Persistence:** Room Database (`androidx.room:room-runtime`, `room-ktx`, `room-paging`) with KSP compiler
- **Media Playback:** AndroidX Media3 (`media3-exoplayer`, `media3-ui`, `media3-common`) and Android `MediaPlayer`
- **Networking & Serialization:** Retrofit 2, OkHttp 3, Moshi Kotlin, Logging Interceptor
- **AI & Cloud Logic:** Firebase AI Logic / Gemini SDK (`firebase-ai`)
- **Permissions:** Google Accompanist Permissions (`accompanist-permissions`)
- **Testing:** JUnit 4, AndroidX Test, Robolectric, Roborazzi

---

## 8. Data Flow & Duplicate Detection Logic

```
[Device Storage / MediaStore / Demo Files]
                   │
                   ▼
       [AudioScannerRepository]
   - Extracts ID3 metadata (Title, Artist, Album, Duration, Bitrate)
   - Computes SHA-256 header + data hash via AudioHashUtility
                   │
                   ▼
       [ScanAudioFilesUseCase]
   - Filter 1: Exact Hash Match (SHA-256 bit-for-bit identity)
   - Filter 2: Filename Variation Match (Regex: "(1)", " - Copy", " (copy)")
   - Filter 3: Metadata Match (Normalized Title + Artist + Duration within ±1.5s)
   - Whitelist Exclusion Check (filters out user-approved false duplicates)
   - Automatic Keeper Recommendation (highest bitrate -> largest file -> oldest)
                   │
                   ▼
           [MainViewModel]
   - Emits StateFlow<ScanProgress>, StateFlow<List<DuplicateGroup>>
   - Coordinates PlayerState for SideBySideCompareDialog
   - Dispatches deletions to MediaStoreDeleteHelper & RecycleBinDao
                   │
                   ▼
     [Jetpack Compose UI Screens]
```

---

## 9. Setup & Run Instructions

### Prerequisites
1. **Android Studio:** Ladybug (2024.2.1+) or newer.
2. **JDK:** Java 17 or Java 21 configured in Gradle Settings.
3. **Android Device / Emulator:** API Level 24+ (Android 7.0 Nougat or higher). Recommended: Android 13+ (API 33+) to test `READ_MEDIA_AUDIO` and `POST_NOTIFICATIONS`.

### Compilation & Execution
To compile the project via command line:
```bash
# Build the debug APK
gradle assembleDebug

# Run local JVM and Robolectric unit tests
gradle :app:testDebugUnitTest
```

### Running the App
1. Launch the app on an Android device or emulator.
2. If prompted, grant storage/audio permissions, or enable **Include Built-in Demo Tracks** in the Settings tab to immediately test duplicate detection without needing existing music on device.
3. Tap **"Scan Storage"** to initiate library scanning.
4. Review duplicate sets in the **Duplicates** tab or open the **AI Studio** tab to compose original music.

---

## 10. Verification & Testing Status

| Area | Status | Notes |
|---|---|---|
| **Compilation** | **PASSED** | Codebase builds without syntax or link errors. |
| **Robolectric JVM Tests** | **CONFIGURED** | Test runner configured with `isIncludeAndroidResources = true`. |
| **UI Touch Targets** | **PASSED** | All action buttons adhere to $\ge$ 44-48dp height minimums. |
| **Adaptive Layouts** | **PASSED** | Validated on compact phone viewports and wide displays ($\ge$ 720dp) with `NavigationRail`. |
| **Card Borders & Padding** | **PASSED** | All cards, chips, dialogs, and stat containers have explicit borders and spacing. |

---

## 11. Known Limitations & Assumptions

1. **Storage Access Framework (SAF) on Android 11+:** Permanent deletion of files on external SD cards or public storage may require user confirmation through system intent dialogs (`createDeleteRequest`). The app includes `MediaStoreDeleteHelper` to handle this flow.
2. **ID3 Tag Editing on Scoped Storage:** Modifying metadata on Android 11+ requires `createWriteRequest` permission for files not owned by the app; demo tracks and app-created tracks edit instantly in memory and local Room cache.
3. **Hardware Acceleration:** Canvas waveform visualizers are optimized for 60fps rendering using lightweight bar decimation.

---

## 12. Recommended Next Steps

1. **Audio Fingerprinting via Chromaprint / AcoustID (Optional Future Upgrade):** Integrate acoustic fingerprinting (FFmpeg / Chromaprint) for acoustic detection across different compression formats (e.g., comparing a 320kbps MP3 against a FLAC of the same recording).
2. **Scheduled Background Scans via WorkManager:** Add a periodic background worker that scans during device idle charging hours and posts a summary notification when new duplicates accumulate.
3. **Cloud Backup Integration:** Provide optional export/sync of cleaned music folders to Google Drive or cloud storage.
