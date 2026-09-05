package com.illusion.app.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.illusion.app.R
import com.illusion.app.data.download.DownloadStorage
import com.illusion.app.data.local.entity.DownloadEntity
import com.illusion.app.data.local.entity.DownloadStatus
import com.illusion.app.data.local.entity.DownloadedSubtitle
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.repository.DownloadRepository
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.settings.SettingsRepository
import com.illusion.app.data.smb.MissingSmbCredentialException
import com.illusion.app.data.smb.SmbClient
import com.illusion.app.data.smb.SmbConnectionInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.CancellationException

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val libraryRepository: LibraryRepository,
    private val sourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient,
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    // Set as soon as the item is known, read by getForegroundInfo() - which WorkManager can call
    // before doWork() has reached that point (speculatively negotiating foreground eligibility),
    // hence the blank fallback rather than a lateinit that would crash on that early call.
    private var currentTitle: String = ""

    override suspend fun getForegroundInfo(): ForegroundInfo =
        DownloadNotifications.progressForegroundInfo(
            applicationContext,
            currentTitle.ifBlank { applicationContext.getString(R.string.download_notification_channel_name) },
            percent = null
        )

    override suspend fun doWork(): Result {
        val stableId = inputData.getString(KEY_STABLE_ID) ?: return Result.failure()
        val item = libraryRepository.getById(stableId) ?: return Result.failure()
        currentTitle = item.title
        // Runs as a real foreground service for the whole download, not just a plain background
        // CoroutineWorker - see DownloadNotifications' own KDoc for why a large file over SMB
        // needs this (Android's background-execution time limit otherwise silently kills the
        // worker partway through, which read as "download speed drops to 0 and never resumes").
        setForeground(getForegroundInfo())
        val info = try {
            sourceRepository.connectionInfoById(item.sourceId)
        } catch (e: MissingSmbCredentialException) {
            return failWith(stableId, null, item.sizeBytes, e.message!!)
        } ?: return failWith(stableId, null, item.sizeBytes, "Источник SMB недоступен")

        val treeUri = settingsRepository.downloadsFolderUri.first()
        val existing = downloadRepository.getForItem(stableId)
        val videoUri = resolveVideoUri(existing, treeUri, item)
            ?: return failWith(stableId, null, item.sizeBytes, "Не удалось создать файл в папке загрузок")

        val resumeFrom = if (existing?.status != DownloadStatus.COMPLETED) {
            DownloadStorage.length(applicationContext, videoUri)
        } else {
            0L
        }

        downloadRepository.upsert(
            (existing ?: DownloadEntity(
                stableId = stableId,
                contentUri = videoUri.toString(),
                subtitles = emptyList(),
                status = DownloadStatus.DOWNLOADING,
                totalBytes = item.sizeBytes,
                downloadedBytes = 0,
                updatedAt = 0
            )).copy(
                    contentUri = videoUri.toString(),
                    status = DownloadStatus.DOWNLOADING,
                    totalBytes = item.sizeBytes,
                    downloadedBytes = resumeFrom,
                    updatedAt = System.currentTimeMillis(),
                    errorMessage = null
                )
        )

        return try {
            copyWithReconnect(info, item.filePath, videoUri, item.sizeBytes, resumeFrom, stableId)
            val subtitles = downloadSubtitles(info, item, treeUri)
            val (posterUri, fanartUri) = downloadImages(info, item, treeUri)
            downloadRepository.upsert(
                DownloadEntity(
                    stableId = stableId,
                    contentUri = videoUri.toString(),
                    subtitles = subtitles,
                    status = DownloadStatus.COMPLETED,
                    totalBytes = item.sizeBytes,
                    downloadedBytes = item.sizeBytes,
                    updatedAt = System.currentTimeMillis(),
                    posterUri = posterUri,
                    fanartUri = fanartUri
                )
            )
            Result.success()
        } catch (e: IOException) {
            // Keep the partial file and its last-reported downloadedBytes - a later retry resumes
            // from here instead of re-downloading everything.
            failWith(stableId, videoUri, item.sizeBytes, e.message ?: "Ошибка загрузки")
        } catch (e: CancellationException) {
            // Leave the partial file and its DB row alone - an explicit user cancel already deletes
            // both via DownloadRepository.remove() (wired to the cancel button), and any other stop
            // reason (lost network constraint, Doze/battery restrictions, a stray touch) should resume
            // from here on the next attempt rather than restart from zero.
            Result.failure()
        }
    }

    /** Reuses the exact Uri stored from a previous attempt so resume writes append to the same file - only falls back to creating a new one if that Uri no longer resolves (user deleted it externally, or this is the first attempt). */
    private fun resolveVideoUri(existing: DownloadEntity?, treeUri: String?, item: MediaItemEntity): Uri? {
        val previous = existing?.takeIf { it.status != DownloadStatus.COMPLETED }?.contentUri?.let { Uri.parse(it) }
        if (previous != null && DownloadStorage.exists(applicationContext, previous)) return previous
        return DownloadStorage.create(applicationContext, treeUri, folderSegments(item), videoFileName(item))
    }

    /**
     * Reads [remotePath] from [startPosition] onward, appending to [outputUri]. A dropped SMB
     * session (NAS/router idle timeout, same root cause as the player's "source error") triggers a
     * reconnect-and-resume rather than failing the whole download - only [MAX_CONSECUTIVE_FAILURES]
     * reconnects in a row without a single successful read gives up for good.
     */
    private suspend fun copyWithReconnect(
        info: SmbConnectionInfo,
        remotePath: String,
        outputUri: Uri,
        totalBytes: Long,
        startPosition: Long,
        stableId: String
    ) {
        var position = startPosition
        var connection = smbClient.connect(info)
        var raf = connection.openRandomAccessFile(remotePath)
        var consecutiveFailures = 0
        val buffer = ByteArray(CHUNK_SIZE)
        var lastReportAt = 0L
        val out = DownloadStorage.openOutput(applicationContext, outputUri, append = startPosition > 0)
            ?: throw IOException("Не удалось открыть файл для записи")
        try {
            out.use {
                while (totalBytes < 0 || position < totalBytes) {
                    if (isStopped) throw CancellationException("Download stopped")
                    val maxRead = if (totalBytes >= 0) minOf(CHUNK_SIZE.toLong(), totalBytes - position).toInt() else CHUNK_SIZE
                    val read = try {
                        raf.read(buffer, position, 0, maxRead)
                    } catch (e: IOException) {
                        consecutiveFailures++
                        if (consecutiveFailures > MAX_CONSECUTIVE_FAILURES) throw e
                        runCatching { raf.close() }
                        runCatching { connection.close() }
                        delay(RECONNECT_BACKOFF_MS)
                        connection = smbClient.connect(info)
                        raf = connection.openRandomAccessFile(remotePath)
                        continue
                    }
                    consecutiveFailures = 0
                    if (read <= 0) break
                    it.write(buffer, 0, read)
                    position += read
                    val now = System.currentTimeMillis()
                    if (now - lastReportAt > PROGRESS_INTERVAL_MS) {
                        lastReportAt = now
                        setProgress(workDataOf(KEY_DOWNLOADED to position, KEY_TOTAL to totalBytes))
                        downloadRepository.getForItem(stableId)?.let { entity ->
                            downloadRepository.upsert(entity.copy(downloadedBytes = position, updatedAt = now))
                        }
                        if (totalBytes > 0) {
                            val percent = (position * 100 / totalBytes).toInt().coerceIn(0, 100)
                            setForeground(DownloadNotifications.progressForegroundInfo(applicationContext, currentTitle, percent))
                        }
                    }
                }
            }
        } finally {
            runCatching { raf.close() }
            runCatching { connection.close() }
        }
    }

    /** Subtitles are small - one straightforward read+write per file, with a single retry-via-reconnect on failure rather than the video's full chunked resume machinery. */
    private suspend fun downloadSubtitles(
        info: SmbConnectionInfo,
        item: MediaItemEntity,
        treeUri: String?
    ): List<DownloadedSubtitle> {
        if (item.subtitlePaths.isEmpty()) return emptyList()
        val connection = smbClient.connect(info)
        return try {
            val segments = folderSegments(item)
            item.subtitlePaths.mapNotNull { remotePath ->
                val fileName = subtitleFileName(remotePath)
                val uri = DownloadStorage.create(applicationContext, treeUri, segments, fileName) ?: return@mapNotNull null
                val out = DownloadStorage.openOutput(applicationContext, uri, append = false) ?: return@mapNotNull null
                runCatching {
                    connection.openInputStream(remotePath).use { input -> out.use { input.copyTo(it) } }
                }.onFailure { DownloadStorage.delete(applicationContext, uri) }
                    .map { DownloadedSubtitle(uri.toString(), remotePath) }
                    .getOrNull()
            }
        } finally {
            runCatching { connection.close() }
        }
    }

    /**
     * Copies poster.jpg/fanart.jpg into the same per-title folder as the video and subtitles -
     * small files, one read+write each, same reconnect-free approach as [downloadSubtitles]. Not
     * for making the Downloads/Details screens work offline right now (they still fetch live via
     * [com.illusion.app.data.image.SmbImageUri] as long as the SMB source is reachable, unchanged) -
     * this is purely so [DownloadRepository.recoverOrphanedDownloads] has a local copy to fall back
     * on if the app's data (and with it, the SMB source's saved credentials) is ever wiped or the
     * app reinstalled while the downloaded files themselves survive on the SAF-picked folder. A
     * remote http(s) poster/fanart URL is skipped - Coil already fetches those directly with no
     * SMB source involved, so there's nothing this needs to preserve locally for that case.
     */
    private suspend fun downloadImages(info: SmbConnectionInfo, item: MediaItemEntity, treeUri: String?): Pair<String?, String?> {
        val toFetch = listOfNotNull(
            item.posterPath?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { POSTER_FILE_NAME to it },
            item.fanartPath?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { FANART_FILE_NAME to it }
        )
        if (toFetch.isEmpty()) return null to null
        val segments = folderSegments(item)
        val connection = smbClient.connect(info)
        return try {
            val results = toFetch.associate { (fileName, remotePath) ->
                fileName to runCatching {
                    val uri = DownloadStorage.create(applicationContext, treeUri, segments, fileName) ?: return@runCatching null
                    val out = DownloadStorage.openOutput(applicationContext, uri, append = false) ?: return@runCatching null
                    connection.openInputStream(remotePath).use { input -> out.use { input.copyTo(it) } }
                    uri.toString()
                }.getOrNull()
            }
            results[POSTER_FILE_NAME] to results[FANART_FILE_NAME]
        } finally {
            runCatching { connection.close() }
        }
    }

    private suspend fun failWith(stableId: String, videoUri: Uri?, totalBytes: Long, message: String): Result {
        val existing = downloadRepository.getForItem(stableId)
        downloadRepository.upsert(
            (existing ?: DownloadEntity(
                stableId = stableId,
                contentUri = videoUri?.toString() ?: "",
                subtitles = emptyList(),
                status = DownloadStatus.FAILED,
                totalBytes = totalBytes,
                downloadedBytes = 0,
                updatedAt = 0
            )).copy(status = DownloadStatus.FAILED, errorMessage = message, updatedAt = System.currentTimeMillis())
        )
        return Result.failure()
    }

    companion object {
        const val KEY_STABLE_ID = "stable_id"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        private const val CHUNK_SIZE = 512 * 1024
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private const val RECONNECT_BACKOFF_MS = 2000L
        /** Shared with [com.illusion.app.data.repository.DownloadRepository.recoverOrphanedDownloads], which looks for these exact names as siblings of a recovered video file. */
        const val POSTER_FILE_NAME = "poster.jpg"
        const val FANART_FILE_NAME = "fanart.jpg"

        private fun sanitizeFileName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "video" }

        // One subfolder per title (movie) or per show/season (episode) instead of dropping every
        // download flat into the same folder - mirrors how the source library on the NAS is
        // itself organized, so downloads stay just as browsable.
        fun folderSegments(item: MediaItemEntity): List<String> {
            val segments = if (item.seriesStableId != null) {
                listOfNotNull(
                    item.seriesStableId.substringAfterLast('\\'),
                    item.seasonNumber?.let { "Сезон $it" }
                )
            } else {
                listOf(item.year?.let { "${item.title} ($it)" } ?: item.title)
            }
            return segments.map { sanitizeFileName(it) }
        }

        // Keep the exact filename from the SMB source rather than synthesizing one from metadata -
        // the user wants the downloaded file to look the same on-device as it does on the NAS, and
        // a raw title-only name also throws away useful info (release quality tags, show name for
        // episodes whose own `title` is just the episode name) the original filename already had.
        fun videoFileName(item: MediaItemEntity): String =
            sanitizeFileName(item.filePath.substringAfterLast('\\'))

        private fun subtitleFileName(remotePath: String): String =
            sanitizeFileName(remotePath.substringAfterLast('\\'))
    }
}
