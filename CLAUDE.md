# Сеанс (Seance)

Android media-center app (Kotlin, Jetpack Compose/Material3, `com.seance.app`, minSdk 26 / targetSdk 37 / compileSdk 37). Streams video directly from an SMB share on a home NAS ("Netcraze Ultra" router) — no download required for playback. Full spec lives in the conversation history / ask the user for the ТЗ if needed; this file tracks durable architecture and status.

## Architecture

MVVM, package-by-feature, **no DI framework** — `SeanceApplication.kt` manually wires all singletons. This is intentional (user prefers minimal abstraction, not an oversight).

- `data/local` — Room: `SmbSourceEntity`, `MediaItemEntity`, `WatchProgressEntity`, `FavoriteEntity`, `ThumbnailSpriteEntity` + DAOs
- `data/smb` — `SmbClient`/`SmbConnection` (wraps `smbj` 0.14.0), `SmbCredentialStore` (EncryptedSharedPreferences, never Room), `StableIdGenerator`. Read-only for scanning/playback; `SmbConnection.mkdirs`/`openOutputStream`/`openRandomAccessFileForWrite`/`deleteFile` exist solely for the developer-only add-media flow below.
- `data/nfo` — Kodi-style `.nfo` XML parser (`NfoParser`) + writer (`NfoWriter`, developer-only add-media flow only)
- `data/tmdb` — `TmdbClient` (developer-only add-media flow only - the only place this app calls the internet for metadata)
- `data/security` — `DevAccessStore`, the password gate for the add-media flow
- `data/player` — custom Media3 `SmbDataSource`/`SmbDataSourceFactory`, positional reads via `SmbRandomAccessFile` (no local download)
- `data/scan` — `LibraryScanner`, `ThumbnailGenerator`, `SmbMediaDataSource`
- `work` — WorkManager: `LibraryScanWorker`, `ThumbnailGenerationWorker`, `WorkScheduler`, `UploadWorker` (developer-only add-media flow)
- `ui/*` — per-feature screens; `ui/player/*` is the most fully-built subsystem (gestures, gradient overlay, scrub thumbnails, PiP, GPU sharpen shader via Media3 Effects); `ui/addmedia/*` is developer-only, see below

## Key decisions (don't relitigate without reason)

- Custom Media3 `DataSource` over SMB, not download-then-play — needed for streaming/seeking.
- FFmpeg decoder extension (DTS/AC3/TrueHD) not bundled — no prebuilt AAR exists upstream. `scripts/build_ffmpeg_extension.sh` documents the NDK build; must be run manually outside Claude, then pointed to via `local.properties` key `seance.ffmpegExtension.aarPath`. Code already picks it up via `EXTENSION_RENDERER_MODE_ON`.
- Chromecast/DLNA deliberately not implemented — a Chromecast can't read SMB directly, would need a local HTTP bridge server (bigger subsystem, out of scope for now). Cast button in the player UI is a visible disabled stub, not forgotten.
- Adaptive buffering is one-shot at player creation (bandwidth estimate), not continuous — swapping `LoadControl` on a live `ExoPlayer` risks `Allocator` desync.
- GPU sharpen shader implemented against real decompiled Media3 1.11.0 bytecode (`javap` on Gradle-cached `.class` files), not guessed from docs — do this for other fast-moving Media3 Effects / newer Android platform APIs too.
- Dolby Vision Profile 7 (FEL, dual-layer) enhancement-layer detail can never be composited by this app, on the phone *or* the Xiaomi TV Box target — verified by decompiling media3-container 1.11.0's `DolbyVisionConfig.java` (only parses `dvcC`/`dvvC` for a codec string, never reads `el_present_flag` or an EL track) and cross-checking AOSP's HDR docs (BL+EL concatenation needs a vendor `MediaExtractor`, not available through ExoPlayer's Java extractors, which is all this app's `SmbDataSource` pipeline uses). Not a decoder-capability gap on any specific device — it's architectural. Player now surfaces the DV profile number in its diagnostic overlay and a plain-language warning when Profile 7 is detected, rather than attempting compositing.

## Fixed bug — SMB connection timeout via `ACCESS_LOCAL_NETWORK` (resolved)

`targetSdk = 37` triggers Android's mandatory `ACCESS_LOCAL_NETWORK` runtime permission (https://developer.android.com/privacy-and-security/local-network-permission) — any raw-socket local-network connection (which is exactly what `smbj` does) is silently blocked by the OS until this permission is declared *and* granted at runtime, manifesting as a plain TCP timeout rather than an explicit denial. **Fixed**: `AndroidManifest.xml` declares the permission, and `data/smb/LocalNetworkPermission.kt` + `ui/smbsource/LocalNetworkPermissionGate.kt` request/gate it at runtime, wired into onboarding, SMB add/edit forms, and scan/playback paths.

## Implementation status vs spec (audited 2026-08-16 — re-verify before trusting specific claims, code moves fast)

**Working**: SMB multi-source scanning + Room index, `.nfo` parsing, 4-category model, onboarding + test-connection + scan-progress flow, player core (gestures, gradient bars, scrub thumbnails, resume, external subs, audio-track switching, PiP, immersive mode, autoplay-next, wake lock, error/retry), Material You + day/night, edge-to-edge, WorkManager charging-only constraint (for thumbnail gen), Details screen (fanart/poster/metadata/cast-filmography/similar/collections, continue-watching, zoomable poster/fanart), Coil `AsyncImage` sized loading across all screens (grids/carousels/Details no longer decode at full source resolution), functional search, library sort/filter UI, Favorites/History UI, backup export/import, periodic rescan (`WorkScheduler.schedulePeriodicScan`), rename-proof `StableIdGenerator` (hashes source+size+byte samples, not filename), offline downloads (`DownloadsScreen`/`DownloadWorker`/`DownloadEntity`), Settings cache management + rescan interval + SMB source edit, empty states/shimmer/shared-element transitions/haptics, MP4 audio-track title read (`Mp4AudioTrackTitleReader`, `udta/name` box) for "озвучка" display, SMB source form UX (imePadding keyboard fix, password visibility toggle, host/share autofill suggestions), trailer playback (Kodi/TMM `<video>-trailer.ext` or bare `trailer.ext` sibling file, detected during scan and excluded from being indexed as its own item — see `SmbFileRef.isTrailer`/`LibraryScanner`'s `matchesTrailer`, `PlayerViewModel.loadTrailer`, `MediaItemEntity.trailerPath`).

**TV support**: manual Phone/TV mode toggle (Settings + onboarding), left-edge `NavigationRail` in TV mode (bottom `NavigationBar` was a confirmed D-pad dead end), D-pad focus (`ui/common/TvFocus.kt` `focusHighlight()`/`focusGroup()`) wired and on-device-verified across every screen (Home, Library, Search, Favorites, Details, Settings, Downloads, History, Player, onboarding/SMB forms, scan progress, cache screen). Poster grids/carousels size cards via `posterCardMinWidth()` (`ui/common/AdaptiveSizing.kt`), `LocalUiMode`-aware (176dp in TV mode vs 120dp on phone) rather than a fixed `GridCells.Adaptive` minSize — `GridCells.Adaptive` already recomputes column *count* for a wider screen on its own, the fix was card *size* for couch-distance viewing, not adding runtime `WindowSizeClass` detection (deliberately not used — see the manual-toggle-only note on `UiMode.kt`).

**Intro skipping**: manual, not audio-fingerprint. Player's settings dialog (gear icon, series episodes only) has "mark end of intro" — tapping it at the current position writes `introStartMs=0`/`introEndMs=<position>` to every episode in that season at once (`LibraryRepository.markIntroEnd`), and a "reset" action once marked. True audio-fingerprint cross-episode auto-detection (decode + correlate, Jellyfin/Plex-style) is real DSP work, deliberately deferred - this is the interim stand-in, not a bug.

**Developer-only "add media" scraper** (2026-08-17): lets the developer (only) pick a local video file, search TMDB, and write it straight onto the NAS in the same Kodi-style layout `LibraryScanner`/`NfoParser` already read — a manual rescan afterwards is what actually adds it to the library, this flow never touches Room directly. Deliberately hidden and gated, not exposed as a normal feature:
- **Access**: 7 taps on the version string at the bottom of Settings. First-ever unlock generates a random password, shows it once, stores only its SHA-256 hash (`DevAccessStore`, mirrors `SmbCredentialStore`'s EncryptedSharedPreferences pattern) — every unlock after that requires typing it. Not real security (a decompiled APK could bypass the check) - just enough to keep it out of reach of anyone else using this install, per explicit design.
- **TMDB**: only place this app ever calls the internet for metadata, and only at this one deliberate moment - never on scan/browse, matching the app's offline-first architecture. Needs a free API key from themoviedb.org in `local.properties` as `seance.tmdb.apiKey` (same pattern as `seance.ffmpegExtension.aarPath` - not committed, exposed via `BuildConfig.TMDB_API_KEY`). Without a key, `AddMediaScreen` shows a "not configured" message instead of the flow.
- **Write path**: `SmbConnection` gained `mkdirs`/`openOutputStream`/`openRandomAccessFileForWrite`/`deleteFile` (verified against smbj's actual `DiskShare`/`File` class bytecode + GitHub source before writing, not guessed - `write(byte[], long fileOffset, int offset, int length)` mirrors the existing read-side positional API exactly). `NfoWriter` writes `<movie>`/`<tvshow>`/`<episodedetails>` XML with exactly the tags `NfoParser` reads back; poster/fanart are downloaded and written as real local `poster.jpg`/`fanart.jpg` files (never embedded as remote `<thumb>` URLs) so nothing needs a live fetch again after this one-time write.
- **Upload**: `AddMediaViewModel` writes the (small) .nfo/images inline, then hands the video's own (potentially large/slow) byte copy to `UploadWorker` - mirrors `DownloadWorker`'s reconnect-and-resume approach but reversed (local read is reliable, the NAS write side is what can drop), observed by the UI directly via `WorkManager`'s own progress `Data`, no dedicated Room table (unlike `DownloadsScreen`'s queue).
- **Not yet built / known limits**: no cross-app-restart resume for the upload (a killed app loses progress, would need to be re-added from scratch); destination folder path is manually typed/edited by the developer, not auto-inferred per source's actual category-folder convention (varies per NAS layout, out of scope); TV mode/D-pad focus not wired into `ui/addmedia/*` (phone-first, developer's own use).

**Remaining real gaps**:
1. Cast/DLNA not implemented (deliberate, see above — not a gap to close).
2. Dolby Vision Profile 7 (FEL) enhancement-layer detail — resolved as "won't fix, diagnosed instead": see the key-decisions note above. Not on the gaps list anymore; the player now tells the user honestly rather than silently doing nothing.
3. Audio-fingerprint auto-skip-intro detection — deliberately deferred (manual marking ships instead, see above), pick up if/when wanted.

**Not a gap (previously mis-tracked as one, corrected 2026-08-16)**: offline downloads DO include subtitles — `DownloadWorker.downloadSubtitles()` fetches every `item.subtitlePaths` entry alongside the video into the same per-title folder, stores them on `DownloadEntity.subtitles`, and `PlayerViewModel.load()` uses those local `content://` Uris (falling back to live SMB only when a completed download has no subtitles). Present since the initial commit — a stale audit note had called this "still streams over SMB," which was wrong; don't re-flag without re-checking `DownloadWorker.kt`/`PlayerViewModel.kt:201-207` first.

## Polish backlog (from a 3-way parallel audit, 2026-08-17 — check off/update as items land)

Performance:
1. [x] `HomeScreen.kt` continue-watching/recently-added `LazyRow` `items()` had no `key` — fixed, now `key = { it.stableId }` like every other list/grid.
2. [ ] No `@Index` anywhere in the Room schema despite `MediaItemDao` filtering on `category`/`seriesStableId`/`collectionName`/`sourceId` — full-table scans on a 3000+ row table. Needs a Room migration.
3. [ ] Details→Person does two independent full-table `getAll()` scans back to back (`DetailsViewModel` then `LibraryRepository.getFilmography`) — should be one SQL-level query.
4. [ ] `LibraryRepository.getSimilar()` pulls the whole category into memory to filter/sort in Kotlin — lower priority, genres being a `List<String>` column makes a SQL-level fix costlier than the payoff right now.

Animation:
5. [x] Player controls show/hide (`PlayerScreen.kt`) and the skip-intro banner were a hard `if` — fixed, both now `AnimatedVisibility` (fade for controls; fade+slide for the banner).
6. [x] List/grid item removal had no exit animation in Favorites/History/Downloads/Library — fixed, all four now use `Modifier.animateItem()`.
7. [x] Details season expand/collapse was an instant snap — fixed, now `AnimatedVisibility` with `fadeIn()+expandVertically()`/`fadeOut()+shrinkVertically()`.
8. [x] Details favorite-toggle icon swapped instantly — fixed, now `Crossfade`.

Polish/consistency:
9. [x] `PersonScreen.kt`'s filmography `LazyVerticalGrid` was missing `.focusGroup()` — fixed, now consistent with every other grid.
10. [ ] `MediaItemEntity.hasNfo` is written during scan but never read anywhere — mirror image of the intro-skip dead-reader bug fixed this session. Needs a decision: wire up (e.g. a "no metadata" badge) or delete the field.
11. [ ] Scan failure message is generic ("scan failed") — `LibraryScanWorker` doesn't distinguish network-unreachable / auth-failed / single-bad-file.
12. [ ] Settings has no "reset to defaults"/re-run-onboarding action — low priority for a single-user app, noted only because it's the one settings-completeness gap found.

## Working conventions

- **Communicate in Russian** on this project (user's explicit preference).
- **No abstractions beyond what's needed** — match the existing no-DI, minimal-layering style.
- When listing deferred/unimplemented items, expect the user's answer to be "do it now" — don't just leave gaps noted, offer to implement.
- **Verify on the real device** (Xiaomi 17 via `adb`) rather than asserting something "should work," especially for player/HDR/DV/PiP behavior that can't be confirmed from code alone.
- For fast-moving/lightly-documented APIs (newer Media3 Effects, newer Android platform permissions), check real signatures/official docs before writing code — don't rely on training-data recall, since Android has moved past the knowledge cutoff.
