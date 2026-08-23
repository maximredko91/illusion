package com.seance.app

import android.app.Application
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import com.seance.app.data.backup.BackupManager
import com.seance.app.data.crash.CrashReporter
import com.seance.app.data.image.PosterCacheSettings
import com.seance.app.data.image.PosterCachePolicyInterceptor
import com.seance.app.data.image.SmbImageConnectionPool
import com.seance.app.data.image.SmbImageFetcher
import com.seance.app.data.local.AppDatabase
import com.seance.app.data.nfo.NfoParser
import com.seance.app.data.nfo.NfoWriter
import com.seance.app.data.player.AudioTrackProber
import com.seance.app.data.player.SmbDataSourceFactory
import com.seance.app.data.repository.AudioTrackRepository
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.repository.ThumbnailRepository
import com.seance.app.data.repository.WatchProgressRepository
import com.seance.app.data.scan.LibraryScanner
import com.seance.app.data.scan.ThumbnailGenerator
import com.seance.app.data.security.DevAccessStore
import com.seance.app.data.settings.SettingsRepository
import com.seance.app.data.smb.SmbClient
import com.seance.app.data.smb.SmbCredentialStore
import com.seance.app.data.tmdb.TmdbClient
import com.seance.app.work.SeanceWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SeanceApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

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
        ThumbnailRepository(database.thumbnailSpriteDao(), database.mediaItemDao())
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                SeanceWorkerFactory(
                    libraryScanner,
                    settingsRepository,
                    thumbnailGenerator,
                    thumbnailRepository,
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
        CrashReporter.install(this)
        applicationScope.launch {
            settingsRepository.posterCachingEnabled.collect { PosterCacheSettings.cachingEnabled = it }
        }
        // One-time cleanup: downloads used to live in this app-private dir before moving to public
        // Downloads/Seans (content Uris) - those old files are now orphaned dead weight.
        java.io.File(filesDir, "downloads").let { if (it.exists()) it.deleteRecursively() }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
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
                    .directory(context.cacheDir.resolve("coil3_disk_cache").toOkioPath())
                    .maxSizeBytes(limitMb.toLong() * 1024 * 1024)
                    .build()
            }
            // Posters stream in one-by-one over SMB as each fetch completes, popping in with no
            // transition of their own - a crossfade turns that into a soft fade instead of a hard
            // "hlop", so a grid filling in at staggered times reads as intentional, not janky.
            .crossfade(true)
            .build()
}
