package com.seance.app.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.smb.SmbClient
import com.seance.app.data.smb.SmbConnectionInfo
import java.io.InputStream
import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay

/**
 * The one slow/large step of the developer-only "add media" scraper (see [AddMediaViewModel] for
 * the rest of the flow, which already wrote the .nfo/poster/fanart before enqueueing this):
 * copies the picked local video's bytes to [KEY_DESTINATION_PATH] on the NAS. Mirrors
 * [DownloadWorker]'s reconnect-and-resume approach but in the opposite direction - the NAS write
 * side is what can drop mid-transfer, not the (local, reliable) read side, so only the SMB
 * connection ever needs reopening; the local InputStream just keeps reading from where it was.
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters,
    private val sourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong(KEY_SOURCE_ID, -1L).takeIf { it >= 0 } ?: return Result.failure()
        val videoUriString = inputData.getString(KEY_VIDEO_URI) ?: return Result.failure()
        val destinationPath = inputData.getString(KEY_DESTINATION_PATH) ?: return Result.failure()
        val totalBytes = inputData.getLong(KEY_TOTAL_BYTES, -1L)

        val info = sourceRepository.connectionInfoById(sourceId)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Источник SMB недоступен"))

        return try {
            val opened = applicationContext.contentResolver.openInputStream(Uri.parse(videoUriString))
                ?: return Result.failure(workDataOf(KEY_ERROR to "Не удалось открыть выбранный файл"))
            opened.use { input -> copyWithReconnect(info, destinationPath, input, totalBytes) }
            Result.success(workDataOf(KEY_UPLOADED to totalBytes, KEY_TOTAL to totalBytes))
        } catch (e: CancellationException) {
            runCatching { smbClient.connect(info).use { it.deleteFile(destinationPath) } }
            Result.failure()
        } catch (e: Exception) {
            // Covers IOException and smbj's SMBRuntimeException alike (see copyWithReconnect) -
            // both mean the upload is unrecoverable after exhausting retries.
            runCatching { smbClient.connect(info).use { it.deleteFile(destinationPath) } }
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e::class.simpleName ?: "Ошибка загрузки")))
        }
    }

    private suspend fun copyWithReconnect(
        info: SmbConnectionInfo,
        destinationPath: String,
        localInput: InputStream,
        totalBytes: Long
    ) {
        var position = 0L
        var connection = smbClient.connect(info)
        var writer = connection.openRandomAccessFileForWrite(destinationPath, createNew = true)
        var consecutiveFailures = 0
        val buffer = ByteArray(CHUNK_SIZE)
        var lastReportAt = 0L
        try {
            while (true) {
                if (isStopped) throw CancellationException("Upload stopped")
                val read = localInput.read(buffer)
                if (read <= 0) break

                var written = false
                while (!written) {
                    try {
                        // Reconnecting itself can time out too (the NAS/network hiccup that broke
                        // the write in the first place often hasn't cleared yet) - kept inside this
                        // same catch so a failed reconnect also counts as one attempt and loops back
                        // for another, instead of escaping the retry loop and killing the upload.
                        writer.write(buffer, position, 0, read)
                        written = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // smbj wraps some failures (e.g. writing through a connection that just
                        // died) as SMBRuntimeException, a RuntimeException rather than an
                        // IOException - caught broadly here so those still retry instead of
                        // crashing the worker outright.
                        consecutiveFailures++
                        if (consecutiveFailures > MAX_CONSECUTIVE_FAILURES) throw e
                        runCatching { writer.close() }
                        runCatching { connection.close() }
                        delay(RECONNECT_BACKOFF_MS)
                        runCatching {
                            connection = smbClient.connect(info)
                            writer = connection.openRandomAccessFileForWrite(destinationPath, createNew = false)
                        }
                    }
                }
                consecutiveFailures = 0
                position += read

                val now = System.currentTimeMillis()
                if (now - lastReportAt > PROGRESS_INTERVAL_MS) {
                    lastReportAt = now
                    setProgress(workDataOf(KEY_UPLOADED to position, KEY_TOTAL to totalBytes))
                }
            }
            setProgress(workDataOf(KEY_UPLOADED to position, KEY_TOTAL to totalBytes))
        } finally {
            runCatching { writer.close() }
            runCatching { connection.close() }
        }
    }

    companion object {
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_VIDEO_URI = "video_uri"
        const val KEY_DESTINATION_PATH = "destination_path"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_UPLOADED = "uploaded"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val CHUNK_SIZE = 512 * 1024
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private const val RECONNECT_BACKOFF_MS = 2000L
    }
}
