package com.seance.app.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.seance.app.data.download.DownloadStorage
import com.seance.app.data.local.entity.DownloadEntity
import com.seance.app.data.local.entity.DownloadStatus
import com.seance.app.data.local.entity.DownloadedSubtitle
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.settings.SettingsRepository
import com.seance.app.data.smb.SmbClient
import com.seance.app.data.smb.SmbConnectionInfo
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

    override suspend fun doWork(): Result {
        val stableId = inputData.getString(KEY_STABLE_ID) ?: return Result.failure()
        val item = libraryRepository.getById(stableId) ?: return Result.failure()
        val info = sourceRepository.connectionInfoById(item.sourceId)
            ?: return failWith(stableId, null, item.sizeBytes, "Источник SMB недоступен")

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
            downloadRepository.upsert(
                DownloadEntity(
                    stableId = stableId,
                    contentUri = videoUri.toString(),
                    subtitles = subtitles,
                    status = DownloadStatus.COMPLETED,
                    totalBytes = item.sizeBytes,
                    downloadedBytes = item.sizeBytes,
                    updatedAt = System.currentTimeMillis()
                )
            )
            Result.success()
        } catch (e: IOException) {
            // Keep the partial file and its last-reported downloadedBytes - a later retry resumes
            // from here instead of re-downloading everything.
            failWith(stableId, videoUri, item.sizeBytes, e.message ?: "Ошибка загрузки")
        } catch (e: CancellationException) {
            DownloadStorage.delete(applicationContext, videoUri)
            downloadRepository.remove(stableId)
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
