package com.seance.app.work

import android.content.Context
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.repository.ThumbnailRepository
import com.seance.app.data.scan.LibraryScanner
import com.seance.app.data.scan.ThumbnailGenerator
import com.seance.app.data.settings.SettingsRepository
import com.seance.app.data.smb.SmbClient

class SeanceWorkerFactory(
    private val libraryScanner: LibraryScanner,
    private val settingsRepository: SettingsRepository,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val thumbnailRepository: ThumbnailRepository,
    private val libraryRepository: LibraryRepository,
    private val smbSourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient,
    private val downloadRepository: DownloadRepository
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ) = when (workerClassName) {
        LibraryScanWorker::class.java.name ->
            LibraryScanWorker(appContext, workerParameters, libraryScanner, settingsRepository)
        ThumbnailGenerationWorker::class.java.name ->
            ThumbnailGenerationWorker(appContext, workerParameters, thumbnailGenerator, thumbnailRepository)
        PosterPreloadWorker::class.java.name ->
            PosterPreloadWorker(appContext, workerParameters, libraryRepository)
        DownloadWorker::class.java.name ->
            DownloadWorker(appContext, workerParameters, libraryRepository, smbSourceRepository, smbClient, downloadRepository, settingsRepository)
        else -> null
    }
}
