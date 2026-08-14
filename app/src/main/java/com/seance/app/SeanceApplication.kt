package com.seance.app

import android.app.Application
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.seance.app.data.backup.BackupManager
import com.seance.app.data.image.PosterCacheSettings
import com.seance.app.data.image.PosterCachePolicyInterceptor
import com.seance.app.data.image.SmbImageConnectionPool
import com.seance.app.data.image.SmbImageFetcher
import com.seance.app.data.local.AppDatabase
import com.seance.app.data.nfo.NfoParser
import com.seance.app.data.player.SmbDataSourceFactory
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.repository.ThumbnailRepository
import com.seance.app.data.repository.WatchProgressRepository
import com.seance.app.data.scan.LibraryScanner
import com.seance.app.data.scan.ThumbnailGenerator
import com.seance.app.data.settings.SettingsRepository
import com.seance.app.data.smb.SmbClient
import com.seance.app.data.smb.SmbCredentialStore
import com.seance.app.work.SeanceWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
            .build()
}
