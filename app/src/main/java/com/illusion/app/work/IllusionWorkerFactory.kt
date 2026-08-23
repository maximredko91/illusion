package com.illusion.app.work

import android.content.Context
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.illusion.app.data.repository.DownloadRepository
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.repository.ThumbnailRepository
import com.illusion.app.data.scan.LibraryScanner
import com.illusion.app.data.scan.ThumbnailGenerator
import com.illusion.app.data.settings.SettingsRepository
import com.illusion.app.data.smb.SmbClient

class IllusionWorkerFactory(
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
        UploadWorker::class.java.name ->
            UploadWorker(appContext, workerParameters, smbSourceRepository, smbClient)
        else -> null
    }
}
