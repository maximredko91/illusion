package com.illusion.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import com.illusion.app.data.backup.BackupManager
import com.illusion.app.data.crash.CrashReporter
import com.illusion.app.data.image.PosterCacheSettings
import com.illusion.app.data.image.PosterCachePolicyInterceptor
import com.illusion.app.data.image.SmbImageConnectionPool
import com.illusion.app.data.image.SmbImageFetcher
import com.illusion.app.data.local.AppDatabase
import com.illusion.app.data.nfo.NfoParser
import com.illusion.app.data.nfo.NfoWriter
import com.illusion.app.data.player.AudioTrackProber
import com.illusion.app.data.player.SmbDataSourceFactory
import com.illusion.app.data.repository.AudioTrackRepository
import com.illusion.app.data.repository.DownloadRepository
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.repository.ThumbnailRepository
import com.illusion.app.data.repository.WatchProgressRepository
import com.illusion.app.data.scan.LibraryScanner
import com.illusion.app.data.scan.ThumbnailGenerator
import com.illusion.app.data.security.DevAccessStore
import com.illusion.app.data.settings.SettingsRepository
import com.illusion.app.data.smb.SmbClient
import com.illusion.app.data.smb.SmbCredentialStore
import com.illusion.app.data.tmdb.TmdbClient
import com.illusion.app.work.IllusionWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class IllusionApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val credentialStore: SmbCredentialStore by lazy { SmbCredentialStore(this) }
    val smbClient: SmbClient by lazy { SmbClient() }
    val nfoParser: NfoParser by lazy { NfoParser() }

    val smbSourceRepository: SmbSourceRepository by lazy {
        SmbSourceRepository(database.smbSourceDao(), credentialStore, smbClient)
    }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(database.mediaItemDao()) }
    val watchProgressRepository: WatchProgressRepository by lazy {
        WatchProgressRepository(database.watchProgressDao(), database.favoriteDao())
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val libraryScanner: LibraryScanner by lazy {
        LibraryScanner(smbSourceRepository, libraryRepository, smbClient, nfoParser)
    }

    val smbDataSourceFactory: SmbDataSourceFactory by lazy {
        SmbDataSourceFactory(smbSourceRepository, smbClient)
    }

    val thumbnailRepository: ThumbnailRepository by lazy {
        ThumbnailRepository(database.thumbnailSpriteDao())
    }
    val thumbnailGenerator: ThumbnailGenerator by lazy {
        ThumbnailGenerator(smbSourceRepository, smbClient, this)
    }
    val downloadRepository: DownloadRepository by lazy { DownloadRepository(this, database.downloadDao()) }
    val backupManager: BackupManager by lazy { BackupManager(smbSourceRepository, watchProgressRepository) }
    val audioTrackRepository: AudioTrackRepository by lazy { AudioTrackRepository(database.audioTrackDao()) }
    val audioTrackProber: AudioTrackProber by lazy { AudioTrackProber(smbDataSourceFactory) }

    // Developer-only "add media" scraper (see data/security/DevAccessStore's KDoc for the access
    // gate, ui/addmedia for the flow) - the only place this app calls out to the internet for
    // metadata, and the only place it ever writes to an SMB share instead of reading from one.
    val devAccessStore: DevAccessStore by lazy { DevAccessStore(this, BuildConfig.DEV_ACCESS_PASSWORD) }
    val tmdbClient: TmdbClient by lazy {
        TmdbClient { devAccessStore.tmdbApiKey ?: BuildConfig.TMDB_API_KEY }
    }
    val nfoWriter: NfoWriter by lazy { NfoWriter() }
    val updateChecker: com.illusion.app.data.update.UpdateChecker by lazy { com.illusion.app.data.update.UpdateChecker() }
    val localUpdateChecker: com.illusion.app.data.update.LocalUpdateChecker by lazy {
        com.illusion.app.data.update.LocalUpdateChecker(smbSourceRepository, smbClient)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                IllusionWorkerFactory(
                    libraryScanner,
                    settingsRepository,
                    libraryRepository,
                    smbSourceRepository,
                    smbClient,
                    downloadRepository
                )
            )
            .build()

    private val smbImagePool: SmbImageConnectionPool by lazy { SmbImageConnectionPool(smbSourceRepository, smbClient) }

    override fun onCreate() {
        super.onCreate()
        // Должно случиться раньше создания MainActivity (Application.onCreate() всегда
        // завершается до Activity.onCreate() системой) - системный SplashScreen резолвит
        // values/values-night для @color/splash_bg по актуальной Configuration.uiMode
        // процесса. Без этого он следует только реальной теме ОС и не знает о выборе
        // пользователя внутри приложения (тот применяется later, на уровне Compose/IllusionTheme).
        applyPersistedThemeMode()
        CrashReporter.install(this)
        applicationScope.launch {
            settingsRepository.posterCachingEnabled.collect { PosterCacheSettings.cachingEnabled = it }
        }
        // One-time cleanup: downloads used to live in this app-private dir before moving to public
        // Downloads/Illusion (content Uris) - those old files are now orphaned dead weight.
        java.io.File(filesDir, "downloads").let { if (it.exists()) it.deleteRecursively() }
        // StreamingService (the external-player HTTP bridge) is a plain Service, which can't take
        // constructor args - it only ever needs this one process-wide factory, the same one
        // internal playback already shares.
        com.illusion.app.data.player.StreamingService.dataSourceFactory = smbDataSourceFactory
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        buildImageLoader(context, POSTER_CACHE_DIR_NAME)

    // Separate ImageLoader + disk cache directory from the poster one above, purely so Settings >
    // Cache can offer an independent "clear fanart cache" action - fanarts are the bigger files
    // (a fullscreen backdrop vs a small poster thumbnail) and per user feedback someone tight on
    // storage may want to drop those first without also losing every poster (which is what makes
    // grids/carousels render instantly). Coil3's DiskCache has no bulk "clear entries matching X"
    // API, only a full clear() - two disk caches was the only way to make the two independently
    // clearable. Same size limit setting applies to both (a per-cache ceiling, not a shared pool).
    val fanartImageLoader: ImageLoader by lazy { buildImageLoader(this, FANART_CACHE_DIR_NAME) }

    /**
     * Called by [com.illusion.app.ui.player.PlayerViewModel] when an item is opened without a
     * cached scrub-preview sprite - generates it lazily instead of the old whole-library
     * background scan. Runs on [applicationScope], not the ViewModel's own scope, since the
     * ViewModel (and its scope) is torn down the moment the player screen closes, while
     * [com.illusion.app.data.scan.ThumbnailGenerator] deliberately no-ops until real playback
     * finishes (see its own KDoc on decoder contention) - this has to keep running after that.
     */
    fun generateThumbnailIfMissing(item: com.illusion.app.data.local.entity.MediaItemEntity) {
        applicationScope.launch {
            if (thumbnailRepository.getForItem(item.stableId) != null) return@launch
            runCatching { thumbnailGenerator.generate(item) }.getOrNull()?.let { thumbnailRepository.save(it) }
        }
    }

    // Синхронное чтение (runBlocking), тот же паттерн, что и imageCacheLimitMb ниже - должно
    // отработать до создания первой Activity, а DataStore тут работает с Flow, не с
    // блокирующим API.
    private fun applyPersistedThemeMode() {
        val mode = kotlinx.coroutines.runBlocking { settingsRepository.themeMode.first() }
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                com.illusion.app.domain.model.ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                com.illusion.app.domain.model.ThemeMode.DARK,
                com.illusion.app.domain.model.ThemeMode.BLACK -> AppCompatDelegate.MODE_NIGHT_YES
                com.illusion.app.domain.model.ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun buildImageLoader(context: PlatformContext, cacheDirName: String): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(SmbImageFetcher.Factory(smbImagePool))
                add(PosterCachePolicyInterceptor())
            }
            // Coil's own default disk cache caps at 250MB regardless of how much free space the
            // device actually has - confirmed on-device this library's posters+fanarts alone
            // filled that cap (251MB, 1169 files) while the device had 300+GB free, silently
            // evicting older/larger entries (fanarts especially, being the bigger files) and
            // making them "vanish offline" even though poster caching was on. User-configurable
            // (Settings > Cache) for anyone on constrained device storage who'd rather cap this
            // low than let it grow toward the 1GB default - same directory name Coil3 uses by
            // default so existing cached entries carry over rather than being orphaned.
            //
            // newImageLoader() is a synchronous Coil callback, not suspend, so this reads the
            // setting's current value once via runBlocking (consistent with this class's existing
            // pattern of wiring singletons eagerly in onCreate) - a later change to the setting
            // only takes effect after the app restarts and Coil rebuilds this ImageLoader fresh.
            .diskCache {
                val limitMb = kotlinx.coroutines.runBlocking { settingsRepository.imageCacheLimitMb.first() }
                coil3.disk.DiskCache.Builder()
                    .directory(context.cacheDir.resolve(cacheDirName).toOkioPath())
                    .maxSizeBytes(limitMb.toLong() * 1024 * 1024)
                    .build()
            }
            // Posters stream in one-by-one over SMB as each fetch completes, popping in with no
            // transition of their own - a crossfade turns that into a soft fade instead of a hard
            // "hlop", so a grid filling in at staggered times reads as intentional, not janky.
            .crossfade(true)
            .build()

    companion object {
        /** Coil3's own default disk cache directory name - kept as-is (not renamed) so existing cached entries from before the poster/fanart split carry over rather than being orphaned. */
        const val POSTER_CACHE_DIR_NAME = "coil3_disk_cache"
        /** Shared with SettingsViewModel, which needs the directory name to measure/clear this cache from Settings > Cache without holding a reference to the ImageLoader itself. */
        const val FANART_CACHE_DIR_NAME = "coil3_disk_cache_fanart"
    }
}
