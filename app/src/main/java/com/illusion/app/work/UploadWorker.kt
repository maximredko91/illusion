package com.illusion.app.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.smb.MissingSmbCredentialException
import com.illusion.app.data.smb.SmbClient
import com.illusion.app.data.smb.SmbConnectionInfo
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

        val info = try {
            sourceRepository.connectionInfoById(sourceId)
        } catch (e: MissingSmbCredentialException) {
            return Result.failure(workDataOf(KEY_ERROR to e.message))
        } ?: return Result.failure(workDataOf(KEY_ERROR to "Источник SMB недоступен"))

        return try {
            val uploaded = applicationContext.contentResolver.openInputStream(Uri.parse(videoUriString))
                ?.use { input ->
                    smbClient.connect(info).use { connection ->
                        val existingSize = connection.fileSizeOrNull(destinationPath)
                        val start = existingSize ?: 0L
                        require(totalBytes < 0 || start <= totalBytes) {
                            "Файл на NAS длиннее выбранного видео. Выберите другое имя."
                        }
                        if (start > 0) {
                            setProgress(workDataOf(KEY_UPLOADED to start, KEY_TOTAL to totalBytes, KEY_VERIFYING to true))
                        }
                        if (start > 0) connection.openRandomAccessFile(destinationPath).use { remote ->
                            verifyUploadPrefix(input, start) { buffer, offset, count ->
                                if (isStopped) throw CancellationException("Upload stopped")
                                remote.read(buffer, offset, 0, count)
                            }
                        }
                        setProgress(workDataOf(KEY_UPLOADED to start, KEY_TOTAL to totalBytes))
                        copyWithReconnect(info, destinationPath, input, totalBytes, start, existingSize == null)
                    }
                } ?: return Result.failure(workDataOf(KEY_ERROR to "Не удалось открыть выбранный файл"))
            Result.success(workDataOf(KEY_UPLOADED to uploaded, KEY_TOTAL to uploaded))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Covers IOException and smbj's SMBRuntimeException alike (see copyWithReconnect) -
            // both mean this attempt is done, but NOT that the upload itself is unrecoverable -
            // leaves the partial file for the next attempt's startPosition to pick up from.
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e::class.simpleName ?: "Ошибка загрузки")))
        }
    }

    private suspend fun copyWithReconnect(
        info: SmbConnectionInfo,
        destinationPath: String,
        localInput: InputStream,
        totalBytes: Long,
        startPosition: Long,
        createNew: Boolean
    ): Long {
        var position = startPosition
        var connection = smbClient.connect(info)
        var writer = try {
            connection.openRandomAccessFileForWrite(destinationPath, createNew = createNew)
        } catch (e: Exception) {
            connection.close()
            throw e
        }
        var consecutiveFailures = 0
        val buffer = ByteArray(CHUNK_SIZE)
        var lastReportAt = 0L
        try {
            while (true) {
                if (isStopped) throw CancellationException("Upload stopped")
                val read = localInput.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                check(totalBytes < 0 || position + read <= totalBytes) { "Размер выбранного файла изменился" }

                var written = false
                while (!written) {
                    try {
                        // Reconnecting itself can time out too (the NAS/network hiccup that broke
                        // the write in the first place often hasn't cleared yet) - kept inside this
                        // same catch so a failed reconnect also counts as one attempt and loops back
                        // for another, instead of escaping the retry loop and killing the upload.
                        var offset = 0
                        while (offset < read) {
                            val count = writer.write(buffer, position + offset, offset, read - offset).toInt()
                            check(count in 1..(read - offset)) { "Не удалось записать видео на NAS" }
                            offset += count
                        }
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
            check(totalBytes < 0 || position == totalBytes) { "Выбранный файл закончился раньше ожидаемого размера" }
            setProgress(workDataOf(KEY_UPLOADED to position, KEY_TOTAL to position))
            return position
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
        const val KEY_VERIFYING = "verifying"
        const val KEY_UPLOADED = "uploaded"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val CHUNK_SIZE = 512 * 1024
        private const val PROGRESS_INTERVAL_MS = 500L
        // Was 5 attempts * 2s = ~10s total resilience, mirroring DownloadWorker's own budget -
        // too short for this specific NAS, which is documented elsewhere (SmbDataSourceFactory's
        // own KDoc) to drop idle SMB sessions aggressively and sometimes take longer than that to
        // accept a fresh connection during a real hiccup. DownloadWorker's short budget makes sense
        // for a foreground, user-visible download where a stuck spinner for 30s+ reads as "broken" -
        // this is a rare, developer-only, one-shot background upload with nothing else competing
        // for attention, so trading a longer worst-case wait for actually surviving a real blip
        // (reported on-device: progress climbs to some point, resets, and the file never lands on
        // the NAS at all - consistent with exhausting a too-small retry budget on every attempt)
        // is the right tradeoff here specifically.
        private const val MAX_CONSECUTIVE_FAILURES = 12
        private const val RECONNECT_BACKOFF_MS = 3000L
    }
}
