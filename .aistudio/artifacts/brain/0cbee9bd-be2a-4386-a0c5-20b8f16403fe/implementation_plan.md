# Comprehensive Audio Cleaner & Studio Upgrade

Transform Duplicate Music Remover into a premier audio optimization and studio utility featuring precision acoustic waveform comparison, interactive storage analytics visualization, built-in ID3 tag & metadata inspection, scheduled automated cleanup rules, and AI music generation powered by Gemini & Lyria audio preview models.

## User Review & Critical Decisions

> [!IMPORTANT]
> The user requested an all-encompassing upgrade across audio comparison, storage analytics, metadata editing, visual design, and AI audio capabilities. Below are the confirmed architectural decisions and recommended implementation sequencing.

- **Confirmed Scope**: Comprehensive multi-pillar improvement covering:
  1. **Smart Audio Comparison & Review**: Dual interactive waveform visualizers, synchronized A/B snippet crossfade listening, and similarity metric indicators (bitrate, sample rate, duration, acoustic fingerprint match).
  2. **Storage Analytics & Breakdown**: Material 3 visual storage allocation breakdown (pie/donut and bar distribution by audio format, duplicate waste, bitrate brackets, and folder hubs).
  3. **ID3 Tag & Metadata Editor**: On-device audio metadata inspector and editor (Title, Artist, Album, Year, Genre, Track number) allowing users to fix tags before or after deduplication.
  4. **Batch Operations & Automation**: Quick selection filters (Keep Highest Bitrate, Keep Oldest, Keep Newest, Keep Most Complete Metadata), swipe-to-keep/swipe-to-trash gestures, and scheduled scan notification reminders.
  5. **AI Music Generation Studio**: A dedicated generation tab utilizing Gemini / Lyria preview models (`models/gemini-3.8-flash` or Lyria audio generation endpoints) to generate royalty-free audio tracks, stems, and sound clips directly in the app.
  6. **Visual & Theme Polish**: AMOLED Pure Black mode, dynamic Material You color tonal pallets, fluid spring transitions, and unified edge-to-edge layout design.

- **Execution Phasing Recommendation**:
  - **Phase 1 (Core Dedup & Visual Overhaul)**: Storage breakdown analytics dashboard, interactive waveform A/B player, and smart batch selection presets.
  - **Phase 2 (Metadata & Management)**: ID3 tag editor dialog, swipe triage gesture cards, and custom directory scanner exclusions.
  - **Phase 3 (AI Audio Generation Studio)**: Integrated Lyria/Gemini music generation studio with clip preview, prompt presets, and export to device storage.

---

## 1. Overview & Core Concept

### What It Does
An all-in-one music library optimizer that not only detects exact (SHA-256) and similar (fuzzy tag + audio duration) duplicates, but empowers users to:
1. Visually and acoustically compare suspected duplicates side-by-side using real-time waveforms and synchronized A/B playback.
2. Analyze music storage usage with interactive Material 3 graphical charts.
3. Clean audio file clutter using 1-tap smart heuristics (e.g. "Keep 320kbps MP3s and purge 128kbps copies").
4. Edit missing or broken ID3 track tags directly on device.
5. Create new original music tracks and sound clips using state-of-the-art AI generative audio.

### Target Audience & Persona
Music collectors, audiophiles, podcast archivists, and creators with large local media libraries who want pristine metadata, zero wasted storage, and built-in creative audio tools.

### Key Value
Converts a passive duplicate scanner into an indispensable, gorgeous daily audio studio that frees up gigabytes of precious phone storage while protecting high-resolution audio files.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Storage Audit & Rapid Clean Flow**:
   - User opens the app into a refreshed **Overview Dashboard** displaying high-level storage stats (Total Audio Size, Reclaimable Duplicate Space, Total Tracks).
   - An interactive visual storage ring chart details format breakdowns (.mp3, .flac, .wav, .m4a) and storage savings potential.
   - User taps **"Start Smart Scan"**, seeing a real-time linear progress indicator tracking files analyzed per second.
   - Upon completion, user is presented with categorized duplicate clusters and can tap **"Auto-Select Lower Quality"** to queue duplicates in 1 click.

2. **Precision A/B Audio Comparison Flow**:
   - User taps any duplicate group to open the **Side-by-Side Compare Sheet**.
   - Dual interactive waveform visualizers render audio peaks for Track A and Track B.
   - User taps **"A/B Play"** to crossfade effortlessly between the two tracks at the exact same playhead timestamp to hear quality differences.
   - Detailed audio specs (Bitrate, Sample Rate, Channels, Codec, File Size, Path) are highlighted with green badges on the superior file.

3. **Metadata Inspector & Editor Flow**:
   - Long-press or tap **"Edit Tags"** on any file card to open a bottom sheet.
   - Edit track title, artist, album, genre, and release year.
   - 1-tap **"Smart Auto-Fill from Filename"** parses common patterns (e.g., `Artist - Title.mp3`) automatically.

4. **AI Music Studio Flow**:
   - User switches to the **"AI Studio"** tab.
   - Enters a musical prompt (e.g., *"Lo-fi chill hop beat with gentle piano and vinyl crackle"* or *"Upbeat electronic synthwave, 120 bpm"*).
   - Generates a 30s preview clip via Gemini/Lyria audio generation.
   - Previews playback with waveform visualization and can save/export directly to their local Music folder.

### Visual Identity & Theme
- **Aesthetic Direction**: Modern Audiophile / Studio Grade. Clean, dark, high-contrast typography, precision data visualizations, and expressive audio waveforms.
- **Color Palette**:
  - *Primary*: Deep Violet / Electric Indigo (`#6366F1`)
  - *Secondary / Accents*: Emerald Green (`#10B981`) for keepers and superior audio specs; Sunset Rose (`#F43F5E`) for duplicates queued for deletion.
  - *Surfaces*: Pure AMOLED Black (`#000000`) and Deep Charcoal Slate (`#121826`) with subtle elevated card tonal borders (`#1F293D`).
- **Typography**: Clean monospace accents for audio bitrates and file sizes (`fontFamily = FontFamily.Monospace`), paired with prominent bold display headers and readable sans-serif body copy.
- **Interactive Feedback**: Haptic ticks on selection toggles, spring-based bar fill animations on storage charts, and smooth animated waveform scrubs.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Dual Waveform A/B Crossfade vs. Simple Sequential Player**
  - *Chosen Approach*: Synchronized timestamp scrubbing with instant crossfade toggle between file A and file B.
  - *Why*: Audiophiles and regular users alike struggle to distinguish identical-sounding tracks without instant seamless switching at the same musical chorus or beat.
  - *Alternatives Considered*: Standard sequential playback required users to manually restart and guess timestamps.

- **Decision 2: Rule-Based Smart Selection Presets**
  - *Chosen Approach*: Offer 4 explicit 1-tap selection strategies:
    1. *Highest Audio Fidelity* (preserves highest bitrate / lossless format).
    2. *Complete Metadata* (preserves file with existing ID3 tags, artist, album art).
    3. *Oldest Creation Date* (preserves original file).
    4. *Newest Modification Date*.
  - *Why*: Removes decision fatigue when dealing with dozens or hundreds of duplicates across large libraries.

- **Decision 3: On-Device Tag Editing vs. External File Manager**
  - *Chosen Approach*: Built-in tag reader/writer using Android MediaStore updates and standard audio file header inspection.
  - *Why*: Users often discover that duplicate files have mismatched tags; allowing on-the-spot tag cleanup ensures their curated library is clean before deleting redundant copies.

- **Decision 4: AI Audio Generation Integration**
  - *Chosen Approach*: REST-based Gemini API audio generation endpoint using `models/gemini-3.8-flash` or Lyria audio preview with secure `BuildConfig.GEMINI_API_KEY` handling.
  - *Why*: Enables seamless creation of custom audio tracks while adhering strictly to background threading and secret management guidelines.

---

## 4. Technical Architecture & Data Strategy

### System Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Jetpack Compose UI                            │
├──────────────────┬──────────────────┬─────────────────┬────────────────┤
│ Storage Overview │ Duplicate Triage │ Side-by-Side    │ AI Music       │
│ & Analytics Bar  │ & Group Cards    │ A/B Waveform    │ Studio Screen  │
└─────────┬────────┴────────┬─────────┴────────┬────────┴───────┬────────┘
          │                 │                  │                │
          ▼                 ▼                  ▼                ▼
┌────────────────────────────────────────────────────────────────────────┐
│                             MainViewModel                              │
│   (UI State, Selection State, Playback Coordinator, Scan Flows)        │
└─────────┬─────────────────┬──────────────────┬────────────────┬────────┘
          │                 │                  │                │
          ▼                 ▼                  ▼                ▼
┌──────────────────┐ ┌──────────────┐ ┌───────────────┐ ┌────────────────┐
│  AudioScanner    │ │ MediaStore   │ │ AudioPlayer   │ │ Gemini / Lyria │
│  Repository      │ │ DeleteHelper │ │ Controller    │ │ Music Service  │
│ (Hash & Metadata)│ │ & RecycleBin │ │ (A/B Crossfade│ │ (REST API)     │
└─────────┬────────┘ └──────┬───────┘ └───────┬───────┘ └───────┬────────┘
          │                 │                 │                 │
          ▼                 ▼                 ▼                 ▼
┌──────────────────┐ ┌──────────────┐ ┌───────────────┐ ┌────────────────┐
│ Android          │ │ Local Room   │ │ MediaPlayer / │ │ Generative AI  │
│ MediaStore / FS  │ │ AppDatabase  │ │ ExoPlayer     │ │ Cloud Endpoint │
└──────────────────┘ └──────────────┘ └───────────────┘ └────────────────┘
```

### State Management & Entities
- **`StorageStats`**: Real-time aggregated storage volume, audio format breakdown counts, and duplicate savings potential.
- **`DuplicateGroup`**: Enhanced with `qualityComparisonSummary`, highest-bitrate pointer, and tag-completeness score.
- **`ABPlaybackState`**: Active track ID, crossfade balance (A vs B), synchronized position milliseconds, playing state, and waveform amplitudes.
- **`AiMusicGenerationState`**: Prompt text, selected style/genre, generating status, resulting audio URI/buffer, and playback progress.

### Proposed File Modifications & Additions
- **`ui/screens/ScanDashboardScreen.kt`**: Add storage distribution breakdown chart, format breakdown chips, and quick action tiles.
- **`ui/components/SideBySideCompareDialog.kt`**: Dual interactive waveform canvas with synchronized scrubber and instant A/B toggle button.
- **`ui/components/StorageBreakdownChart.kt`**: Custom Compose canvas rendering segmented storage utilization and format rings.
- **`ui/screens/AiMusicStudioScreen.kt`**: AI music generation prompt input, style presets, audio player, and save-to-library flow.
- **`ui/components/MetadataEditorDialog.kt`**: ID3 tag editor bottom sheet for quick title, artist, album, and track number edits.
- **`audio/AudioPlayerController.kt`**: Enhanced with dual-source synchronized playback and smooth volume crossfading.
- **`ui/MainViewModel.kt`**: Expose reactive state flows for storage analytics, A/B playback, smart selection presets, and AI audio generation.
