# SAFAI — Master Instructions (READ FIRST)

> You are building **Safai**: an Android app (Kotlin + Jetpack Compose + Material 3) that
> **cleans** (duplicate music/photos/videos/documents, junk files, large files),
> **protects** (a safe Recycle Bin with real restore) and **explains** (a folder-by-folder
> Storage Analyzer, Smart Clean, home-screen widgets).
>
> This spec is split into numbered files. They are written to be given to you **one at a time**.
> Each file is a "phase". Do not start a phase until the previous one **compiles and runs**.

## 0. Files in this pack (give them to AI Studio in this order)

| # | File | What it builds |
|---|------|----------------|
| 00 | `00_README_START_HERE.md` | This file: how to work, global rules |
| 01 | `01_RULES_AND_FIXES.md` | Hard rules, what was wrong in the old build, what to delete |
| 02 | `02_ARCHITECTURE_GRADLE_MANIFEST.md` | Packages, Gradle, Manifest, Application class, DI |
| 03 | `03_DATA_LAYER.md` | Room entities/DAOs, DataStore settings, repositories |
| 04 | `04_SCANNING_AND_DUPLICATES.md` | File scanner, hashing, duplicate engine for ALL media types |
| 05 | `05_CLEANER_RECYCLEBIN_JUNK.md` | Deletion, Recycle Bin, Junk cleaner, Large files, Storage stats |
| 06 | `06_STORAGE_ANALYZER_AND_SMART_CLEAN.md` | Storage Analyzer, blurry/dark photo finder, Smart Clean, priority folders, reminders, shortcuts |
| 07 | `07_DESIGN_SYSTEM_AND_ANIMATION.md` | Colors, type, shapes, adaptive layout, animations, icon |
| 08 | `08_SCREENS_AND_NAVIGATION.md` | Every screen, exact layout and behaviour |
| 09 | `09_WIDGETS.md` | Home-screen widgets (Glance) |
| 10 | `10_QA_CHECKLIST_AND_PERFORMANCE.md` | Test list, performance rules, final self-audit |

## 1. How the human should use this (not for the AI)

1. Open the existing AI Studio project (keep it; we are **upgrading** it).
2. Paste `00` + `01` in the first message. Say: "Acknowledge the rules, then wait."
3. Then paste each file **alone** with: "Implement this phase completely. Output every changed file in full. Then run a build and fix every compile error before replying."
4. If a phase fails to compile, paste the compiler error and say "fix only this".
5. Never paste two phases in one message — the weaker model starts skipping things.

## 2. Global rules for the AI (apply to every phase)

1. **Output complete files.** Never write `// ... rest of code`, `// TODO`, `// same as before`, or "implement later". Every file you touch is output in full.
2. **It must compile.** Add every import. Do not use APIs you are not sure exist. Prefer the stable APIs shown in this spec. If you add an experimental Compose API, add the `@OptIn(...)` annotation.
3. **No dummy data. Ever.** No demo files, no fake groups, no `delay()`-simulated progress, no random numbers, no hard-coded sample song names/sizes. Empty screens show a proper empty state.
4. **Every button must do something real.** If a feature cannot truly work on Android, do not add the button. Do not leave "coming soon" buttons.
5. **Never block the main thread.** All file/DB/hash work runs on `Dispatchers.IO` / `Dispatchers.Default`.
6. **No network.** The app has no INTERNET permission. No Firebase, no Retrofit, no OkHttp, no Gemini, no analytics. Everything runs locally on the device.
7. **Keep the existing identifiers:** `applicationId = "com.aistudio.duplicatemusic.remover"`, `namespace = "com.example"`. Only the visible app name changes to **Safai** (`strings.xml`).
8. **Use the Gradle versions already in the project** (`libs.versions.toml`). Only add a library if this spec lists it. If a listed version is older than what the project has, keep the project's version.
9. All UI text lives in `strings.xml` (English). All icons have `contentDescription` (or `null` if purely decorative).
10. After each phase, finish your reply with a **"Phase self-check"** table: each requirement in the phase file → ✅ done / ❌ not done (and fix the ❌ before replying).

## 3. Product summary (so you understand the goal)

- **Primary user:** one person on an Infinix phone (6.78" AMOLED, 1080×2436, 20:9, punch-hole camera, rounded corners). Dark mode is primary (pure black saves battery on AMOLED). Soon also a tablet.
- **Tabs (bottom bar on phone / rail on tablet):** Home · Duplicates · Junk · Storage · Settings.
- **Cleaning scope:** Audio, Photos, Videos, Documents (PDF, Word, Excel, PowerPoint, TXT, ZIP/RAR, APK), Junk (temp/log/empty/thumbnail/APK leftovers/empty folders), Large files.
- **Safety first:** nothing is ever deleted without a confirm and goes to the Recycle Bin (30 days) unless the user chooses "Delete permanently".
- **Smart tools:** Storage Analyzer (folder-by-folder usage), Smart Clean (one safe tap), blurry/dark photo finder, priority folders, launcher shortcuts, 3 home-screen widgets.
- **Look and feel:** calm, premium, AMOLED black, teal + violet accents, glassy cards, smooth spring animations, haptics — all rendered locally by Compose.

## 4. Definition of done (whole project)

- Fresh install → onboarding → grant permission → Scan → real results from the phone's real files.
- Duplicate groups can be reviewed, compared, kept/removed, trashed and restored.
- Junk scan shows real sizes and cleaning really frees space (verify with `StatFs`).
- Storage tab folder sizes match the system Files app within a small margin.
- Smart Clean only ever touches verified exact duplicates and pre-selected junk.
- Widgets add to the home screen and update.
- Works at 411dp phone width and 800dp+ tablet width, portrait and landscape, light and dark.
