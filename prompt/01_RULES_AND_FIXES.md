# 01 — HARD RULES, KNOWN PROBLEMS OF THE OLD BUILD, AND WHAT TO CHANGE

Read this fully. It explains **why the previous build looked/worked wrong** and exactly what to fix.

## A. Problems found in the previous build (and the fix for each)

| # | Problem in old build | Required fix |
|---|---|---|
| 1 | **Demo mode / `DemoAudioGenerator` / `isDemoFile`** created fake duplicate tracks, which violates the "no dummy data" rule and hides real bugs. | **Delete** `DemoAudioGenerator.kt`, the demo toggle in Settings, the `isDemoFile` column, and every demo branch in `AudioScannerRepository`. |
| 2 | Content hash was a **"partial header + data hash"** → two different files can be reported as exact duplicates → **data-loss risk**. | Use a 3-stage pipeline: size → quick hash (first/last 64 KB) → **full SHA-256**. Only a full-SHA-256 match may be called "Exact duplicate". See file 04. |
| 3 | Recycle Bin stored files in `cacheDir`. Android can wipe cache any time → **trashed files lost**. | Use `filesDir/recycle_bin/` (never cache). See file 05. |
| 4 | `fallbackToDestructiveMigration` → every schema change **wipes the user's data**. | Remove it. Provide real `Migration`s, or (during development only) `fallbackToDestructiveMigrationFrom(1,2)` for the two existing versions, then never again. |
| 5 | Two players: Android `MediaPlayer` **and** Media3. | Use **only Media3 ExoPlayer**. Delete `AudioPlayerController` MediaPlayer code. |
| 6 | Retrofit, OkHttp, Moshi, Firebase AI, Gemini SDK, Accompanist were present. They add build weight and break compilation. | **Remove all of them.** Use `rememberLauncherForActivityResult` for permissions. |
| 7 | **AI Music Generation Studio** needed network/Firebase and produced synthetic WAVs. It is off-topic for a cleaner app and a source of build failures. | **Delete** `AiMusicStudioScreen`, `AudioSynthesizer`, `GeminiMusicService`. (Do not replace with anything.) |
| 8 | **ID3 tag editor** only edited "in memory/Room cache" — it did not really write tags. A fake feature. | **Delete** `MetadataEditorDialog`. Replace with a **read-only "File details" sheet** (real data from `MediaMetadataRetriever`). |
| 9 | Single giant `MainViewModel` (scan + playback + recycle bin + settings). Slow recomposition, hard to debug. | Split into one ViewModel per screen (file 02). Use Hilt `@HiltViewModel`. |
| 10 | `minSdk 24` conflicted with spec (26). | `minSdk = 26`, `targetSdk` and `compileSdk` = latest stable already in the project. |
| 11 | Side-by-side comparison was a **Dialog** (cramped, janky). | Make it a **full screen route** with shared player, A/B switch (file 08). |
| 12 | Whitelist keyed by MediaStore id (changes when file moves). | Key by **content hash pair** (`fileHashA`, `fileHashB`, sorted). |
| 13 | Layout switch at 720dp by manual width checks. | Use `WindowSizeClass` (Compact / Medium / Expanded). See file 07. |
| 14 | Dashboard only knew audio. | Dashboard covers all categories (file 08). |
| 15 | Cards had borders but weak hierarchy/contrast, flat look. | New design system (file 07): glass cards, gradient accents, consistent 16/20/24dp rhythm, motion. |
| 16 | `READ_MEDIA_AUDIO` only → cannot see photos, videos, documents. | New permission flow (file 02 + 04): All-files access path + MediaStore fallback. |
| 17 | Scan ran as a plain coroutine tied to UI; killed when app closed. | Run in a **foreground service/WorkManager `CoroutineWorker`** with progress notification (file 04). |

## B. Absolute rules

1. **No fake/demo/sample/random data anywhere**, including `@Preview`. (No `@Preview` functions at all — skip them.)
2. **No network permission, no network code.**
3. **Nothing is deleted irreversibly without an explicit confirm dialog.** Default action = move to Recycle Bin.
4. **Never delete the last remaining copy.** In every duplicate group at least one file must stay "Keep". The UI and the use-case must both enforce this.
5. **Never touch system/app-private folders:** `Android/data`, `Android/obb` (of other apps), `/system`, and the app's own `recycle_bin` during scans. Skip hidden folders that contain a `.nomedia`-only *system* purpose only if user excluded; otherwise scan normally.
6. **Honesty in Junk cleaner:** Android does not let a normal app clear *other apps'* caches. Do **not** show "Clean RAM", "Boost", "Cool down CPU", "Clear all app cache" — these cannot work. Only clean files the app can really delete (listed in file 05) and show the real bytes freed measured with `StatFs` before/after.
7. **Every number shown** (counts, sizes, percentages, times) is computed from real data.
8. **Accessibility:** min touch target 48dp; all icons have contentDescription; text sizes in `sp`; contrast ≥ 4.5:1.
9. **Respect "Remove animations"** (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`): skip decorative motion.
10. **Language:** English UI; all text in `strings.xml`; use `<plurals>` for counts.

## C. Reply format required from the AI in every phase

```
1. List of files created/modified/deleted (paths)
2. Full code of each file (complete, compilable)
3. Build result (you must run the build; paste the final success line)
4. Phase self-check table (requirement → ✅/❌)
```
