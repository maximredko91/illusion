package com.illusion.app.work

import android.content.Context
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.illusion.app.data.repository.DownloadRepository
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.scan.LibraryScanner
import com.illusion.app.data.settings.SettingsRepository
import com.illusion.app.data.smb.SmbClient
import com.illusion.app.data.update.LocalUpdateChecker
import com.illusion.app.data.update.UpdateChecker

class IllusionWorkerFactory(
    private val libraryScanner: LibraryScanner,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val smbSourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient,
    private val downloadRepository: DownloadRepository,
    private val updateChecker: UpdateChecker,
    private val localUpdateChecker: LocalUpdateChecker
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ) = when (workerClassName) {
        LibraryScanWorker::class.java.name ->
            LibraryScanWorker(appContext, workerParameters, libraryScanner, settingsRepository)
        PosterPreloadWorker::class.java.name ->
            PosterPreloadWorker(appContext, workerParameters, libraryRepository)
        DownloadWorker::class.java.name ->
            DownloadWorker(appContext, workerParameters, libraryRepository, smbSourceRepository, smbClient, downloadRepository, settingsRepository)
        UploadWorker::class.java.name ->
            UploadWorker(appContext, workerParameters, smbSourceRepository, smbClient)
        UpdateDownloadWorker::class.java.name ->
            UpdateDownloadWorker(appContext, workerParameters, smbSourceRepository, smbClient)
        IntroDetectWorker::class.java.name ->
            IntroDetectWorker(appContext, workerParameters, libraryRepository, smbSourceRepository, smbClient)
        UpdateCheckWorker::class.java.name ->
            UpdateCheckWorker(appContext, workerParameters, updateChecker, localUpdateChecker, settingsRepository)
        else -> null
    }
}
