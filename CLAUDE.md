# Сеанс (Seance)

Android media-center app (Kotlin, Jetpack Compose/Material3, `com.seance.app`, minSdk 26 / targetSdk 37 / compileSdk 37). Streams video directly from an SMB share on a home NAS ("Netcraze Ultra" router) — no download required for playback. Full spec lives in the conversation history / ask the user for the ТЗ if needed; this file tracks durable architecture and status.

## Architecture

MVVM, package-by-feature, **no DI framework** — `SeanceApplication.kt` manually wires all singletons. This is intentional (user prefers minimal abstraction, not an oversight).

- `data/local` — Room: `SmbSourceEntity`, `MediaItemEntity`, `WatchProgressEntity`, `FavoriteEntity`, `ThumbnailSpriteEntity` + DAOs
- `data/smb` — `SmbClient`/`SmbConnection` (wraps `smbj` 0.14.0), `SmbCredentialStore` (EncryptedSharedPreferences, never Room), `StableIdGenerator`
- `data/nfo` — Kodi-style `.nfo` XML parser
- `data/player` — custom Media3 `SmbDataSource`/`SmbDataSourceFactory`, positional reads via `SmbRandomAccessFile` (no local download)
- `data/scan` — `LibraryScanner`, `ThumbnailGenerator`, `SmbMediaDataSource`
- `work` — WorkManager: `LibraryScanWorker`, `ThumbnailGenerationWorker`, `WorkScheduler`
- `ui/*` — per-feature screens; `ui/player/*` is the most fully-built subsystem (gestures, gradient overlay, scrub thumbnails, PiP, GPU sharpen shader via Media3 Effects)

## Key decisions (don't relitigate without reason)

- Custom Media3 `DataSource` over SMB, not download-then-play — needed for streaming/seeking.
- FFmpeg decoder extension (DTS/AC3/TrueHD) not bundled — no prebuilt AAR exists upstream. `scripts/build_ffmpeg_extension.sh` documents the NDK build; must be run manually outside Claude, then pointed to via `local.properties` key `seance.ffmpegExtension.aarPath`. Code already picks it up via `EXTENSION_RENDERER_MODE_ON`.
- Chromecast/DLNA deliberately not implemented — a Chromecast can't read SMB directly, would need a local HTTP bridge server (bigger subsystem, out of scope for now). Cast button in the player UI is a visible disabled stub, not forgotten.
- Adaptive buffering is one-shot at player creation (bandwidth estimate), not continuous — swapping `LoadControl` on a live `ExoPlayer` risks `Allocator` desync.
- GPU sharpen shader implemented against real decompiled Media3 1.11.0 bytecode (`javap` on Gradle-cached `.class` files), not guessed from docs — do this for other fast-moving Media3 Effects / newer Android platform APIs too.

## Known bug — SMB connection timeout (root-caused, fix not yet applied)

App fails to connect to a confirmed-reachable SMB host (another app on the same phone connects fine; `adb shell nc` confirms ports 445 and 139 open; all standard permissions granted). **Root cause**: `targetSdk = 37` triggers Android's new mandatory `ACCESS_LOCAL_NETWORK` runtime permission (https://developer.android.com/privacy-and-security/local-network-permission) — any raw-socket local-network connection (which is exactly what `smbj` does) is silently blocked by the OS until this permission is declared *and* granted at runtime. The app currently does neither. Manifests as a plain TCP timeout, not an explicit denial — easy to misdiagnose as a network/router problem.

**Fix** (not yet done):
1. `AndroidManifest.xml`: add `<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />`
2. Request it at runtime (`ActivityResultContracts.RequestPermission()`) before any SMB `connect()` attempt — onboarding form, settings SMB add/edit form, and the scan/playback paths all need to check/request it first.
3. Handle denial gracefully (clear message, since a bare timeout is what happens if you skip this and the user won't know why).

## Implementation status vs spec (audited 2026-08-12 — re-verify before trusting specific claims, code moves fast)

**Working**: SMB multi-source scanning + Room index, `.nfo` parsing, 4-category model, onboarding + test-connection + scan-progress flow, bottom nav, player core (gestures, gradient bars, scrub thumbnails, resume, external subs, audio-track switching, PiP, immersive mode, autoplay-next, wake lock, error/retry), Material You + day/night, edge-to-edge, WorkManager charging-only constraint (for thumbnail gen).

**Biggest gaps** (see full audit in session transcript for exhaustive per-requirement checklist; headline items):
1. Details/card screen is a near-empty stub — no fanart/poster/metadata/cast-filmography/similar/collections. **Highest-impact gap.**
2. Coil is a dependency but **never actually used** — no poster images render anywhere (Home, Library, Details all text-only).
3. Search screen is a non-functional stub (no results, not wired to `MediaItemDao.search()`).
4. Library sort/filter backend exists, no UI control to use it.
5. Favorites/History have data layers, zero UI.
6. Backup export/import — dead menu item in Settings.
7. `WorkScheduler.schedulePeriodicScan()` defined, never called — periodic rescan is dead code.
8. `StableIdGenerator` hashes filename directly — survives file *move* but not *rename*, defeating its stated purpose.
9. Dolby Vision RPU/EL explicit handling missing (spec explicitly calls this out as the one real DV risk).
10. Cast/DLNA not implemented (deliberate, see above).
11. Audio-fingerprint auto-skip-intro: Room fields exist, nothing populates them — dead feature by construction.
12. Offline downloads: entirely absent.
13. Settings: no cache-management UI, no rescan-interval UI, no SMB source *edit* (add/delete only).
14. No `WindowSizeClass` adaptive layout / D-pad focus handling anywhere (TV support unaddressed).
15. No empty states, shimmer/skeleton loading, shared-element transitions, or haptics.

## Working conventions

- **Communicate in Russian** on this project (user's explicit preference).
- **No abstractions beyond what's needed** — match the existing no-DI, minimal-layering style.
- When listing deferred/unimplemented items, expect the user's answer to be "do it now" — don't just leave gaps noted, offer to implement.
- **Verify on the real device** (Xiaomi 17 via `adb`) rather than asserting something "should work," especially for player/HDR/DV/PiP behavior that can't be confirmed from code alone.
- For fast-moving/lightly-documented APIs (newer Media3 Effects, newer Android platform permissions), check real signatures/official docs before writing code — don't rely on training-data recall, since Android has moved past the knowledge cutoff.
