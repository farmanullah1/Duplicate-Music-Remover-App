# 10 — QA CHECKLIST, PERFORMANCE RULES, FINAL SELF-AUDIT

Run this after phase 09. Fix every failure before finishing. Paste the completed table in your final reply.

## A. Performance rules (must be true in code)
1. **Zero blocking work on the main thread.** Enumeration, hashing, decoding, DB, `File.copy/delete` → `Dispatchers.IO`; comparisons/grouping → `Dispatchers.Default`.
2. **Hashing runs on `limitedParallelism(2)`**, metadata reads on `limitedParallelism(3)` — protects battery and avoids heat on a phone.
3. **Hash only when needed** (size → quick → full). Reuse cached hashes/metadata when `size + modified` are unchanged.
4. **Throttle UI updates:** scan progress ≤ 4 emissions/second; no per-second timers anywhere in the app.
5. **Compose:** `@Immutable` UI models; `key` + `contentType` on every `LazyColumn/LazyGrid` item; `remember`/`derivedStateOf` for computed totals; `collectAsStateWithLifecycle()`; no lambdas allocated per item where avoidable; no `Modifier.blur` (slow); max one infinite animation per screen; no animation inside list items except `animateItem()`.
6. **Images:** Coil with fixed thumbnail size (≈168 px), memory cache on, `crossfade(true)`; never load full-size images in lists.
7. **Memory:** stream files in 64 KB chunks; bitmaps decoded with `inSampleSize` and recycled (dHash); `MediaMetadataRetriever` always `release()`d in `finally`; PdfRenderer closed.
8. **Release build:** `isMinifyEnabled = true`, `isShrinkResources = true`; keep rules only for Room/Hilt if required. Add a **Baseline Profile is optional** (skip if it complicates the build).
9. **Startup:** `Application.onCreate` only creates notification channels and enqueues two unique periodic works. No scans, no DB reads on the main thread.
10. **Room:** indices as defined; use `@Upsert` batches of ≤ 500 rows; `@Transaction` for group replacement.

## B. Functional test matrix (tick each)

| # | Test | Expected |
|---|---|---|
| 1 | Fresh install, no permissions | Onboarding; app does not crash; Home shows permission card when scanning |
| 2 | Grant All files access | Mode FULL; Scan lists real counts; Junk tab enabled |
| 3 | Grant media only | Mode MEDIA_ONLY; documents/junk show "Allow all-files access" card; duplicate deletion shows the system dialog |
| 4 | Scan with 0 duplicates | Animated "No duplicates found", no fake groups |
| 5 | Copy a file twice in a folder (`a.jpg`, `a (1).jpg`) | One EXACT group, keeper = `a.jpg`, reclaimable = file size |
| 6 | Two different songs, same word in title | **Not** grouped |
| 7 | Same photo resized/re-compressed | Grouped as "Similar photo", not "Identical" |
| 8 | Toggle REMOVE on every file of a group | Blocked; snackbar "At least one file must be kept" |
| 9 | Remove 3 files → Recycle Bin → Undo within 10 s | Files back at original path, visible in gallery |
| 10 | Restore a file whose folder was deleted | Created in original folder or `Restored/` fallback; name collision handled |
| 11 | Not enough space for trash | Clear error; original file untouched; offer permanent delete |
| 12 | Kill the app mid-scan, reopen | Cached rows reused; re-scan is much faster |
| 13 | Cancel scan from notification | Scan stops within ~1 s; state Idle |
| 14 | Junk scan → clean → check `StatFs` | Freed ≈ reported; empty folders deleted deepest-first; protected folders untouched |
| 15 | Mark "Not a duplicate", rescan | Pair no longer appears |
| 16 | Storage tab: Analyze, drill into DCIM | Child sizes ≤ parent; totals match the Files app roughly |
| 17 | Storage: select a folder → Recycle Bin | Moved file by file with progress; protected folders cannot be selected |
| 18 | Smart Clean with similar-photo and tag-match groups present | Plan contains only EXACT_CONTENT non-keepers + pre-selected junk |
| 19 | Blurry photo slider | Count changes instantly, no re-decode |
| 20 | Priority folder set, group with files in/out of it | Keeper = file inside priority folder, reason shown |
| 21 | Long-press app icon | Scan / Duplicates / Junk shortcuts open correct screens |
| 22 | Rotate phone / resize window / open on tablet | Layout switches Compact → Medium → Expanded without state loss |
| 23 | Light/dark/AMOLED toggles | Instant, no flicker; contrast OK |
| 24 | Reduce motion ON or system animations OFF | No decorative animation; app still usable |
| 25 | TalkBack | Every icon button announces its purpose |
| 26 | Add all 3 widgets | They render, tap through to the right screens |
| 27 | Export CSV | File contains real rows from the current results |
| 28 | Large library (10k+ files) | No ANR; scrolling smooth; memory stable |

## C. Compile-safety audit (the model must grep its own output)
- [ ] No `TODO(`, `// ...`, `pass`, "implement later", "placeholder", "sample", "mock", "demo", "dummy", `Random(` anywhere in `app/src`.
- [ ] No imports/usages of `retrofit`, `okhttp`, `moshi`, `firebase`, `accompanist`, `MediaPlayer`.
- [ ] `AndroidManifest` has no `INTERNET` permission; all declared receivers/services/providers exist as classes; all XML resources referenced exist.
- [ ] Every `R.string.*`, `R.drawable.*`, `R.xml.*` referenced exists.
- [ ] Room: all entities listed in `@Database`, all DAOs have an abstract getter, schema export dir set.
- [ ] Hilt: every injected class is `@Inject constructor` or provided in a module; `@HiltViewModel` on VMs; `@AndroidEntryPoint` on Activity and receivers that inject.
- [ ] No usage of deprecated `Accompanist`; `rememberLauncherForActivityResult` used for permissions.
- [ ] Every `Icon(...)` has `contentDescription` (or `null` with decorative intent).
- [ ] Unit tests listed in files 04 and 06 exist and pass (`./gradlew :app:testDebugUnitTest`).

## D. Final reply format
1. Short summary of what was built.
2. The completed test matrix B with ✅/❌ and notes (be honest — mark ❌ what you could not verify).
3. Known limitations (must include): Android does not allow cleaning other apps' private caches; documents/junk need "All files access"; blurry/dark photo detection is an estimate; widgets refresh every ~30 min and after scans/cleans.
