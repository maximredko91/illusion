package com.illusion.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.smb.SmbClient
import com.illusion.app.data.update.LocalUpdateUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads the .apk for a build UpdateChecker/LocalUpdateChecker already confirmed is newer than
 * the running one - a plain WorkManager job (not a suspend fun the caller awaits directly) so the
 * download survives the update dialog being dismissed/the screen being backgrounded, same as this
 * app's other background downloads (see DownloadWorker). [KEY_URL] carries either a real GitHub
 * https:// asset URL or a [LocalUpdateUri]-encoded local SMB one - [LocalUpdateUri.isLocal]
 * decides which of the two nearly-identical byte-copy loops below actually runs; everything else
 * (progress reporting, output file, success/failure contract) is shared.
 */
class UpdateDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val smbSourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val versionCode = inputData.getInt(KEY_VERSION_CODE, -1)
        if (versionCode < 0) return@withContext Result.failure()

        val outFile = apkFile(applicationContext, versionCode)
        // Download into a .part file and rename only once the copy finished: the bytes used to go
        // straight into the final name, so a cancelled or killed download left a truncated APK
        // that the next attempt (UpdateViewModel.startDownload, which takes any non-empty file as
        // ready) happily handed to the package installer as a finished update.
        val partFile = File(outFile.parentFile, outFile.name + PART_SUFFIX)
        runCatching { partFile.delete() }
        runCatching { outFile.delete() }
        try {
            if (LocalUpdateUri.isLocal(url)) downloadFromSmb(url, partFile) else downloadFromHttp(url, partFile)
            if (!partFile.renameTo(outFile)) throw IOException("Не удалось сохранить файл обновления")
        } catch (e: java.util.concurrent.CancellationException) {
            runCatching { partFile.delete() }
            throw e
        } catch (e: Exception) {
            runCatching { partFile.delete() }
            runCatching { outFile.delete() }
            return@withContext Result.failure(workDataOf(KEY_ERROR to (e.message ?: e::class.simpleName)))
        }
        Result.success(workDataOf(KEY_FILE_PATH to outFile.absolutePath))
    }

    private suspend fun downloadFromHttp(url: String, outFile: File) {
        val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // Was 60s - confirmed on-device (real report: progress stalls near 1%, then the whole
            // "what's new" dialog silently reappears and the user re-taps "Обновить" only to hit
            // the same wall again) that this was tripping readTimeout on a connection with real
            // multi-second-to-tens-of-second stalls between chunks (satellite/high-jitter link),
            // not an actually-dead connection - each individual stall was well under a sane
            // "this transfer is dead" threshold, just over the old 60s one. 5 minutes tolerates a
            // real stall without waiting forever on a truly dead connection.
            .readTimeout(5, TimeUnit.MINUTES)
            // callTimeout bounds the WHOLE request in addition to readTimeout's per-gap bound, so
            // a connection that keeps trickling data forever (never triggering readTimeout) still
            // eventually fails instead of hanging indefinitely - surfaces as a normal error the
            // user can act on (see the Cancel button added to DownloadProgressDialog) rather than
            // an unbounded hang.
            .callTimeout(15, TimeUnit.MINUTES)
            .build()
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val expected = body.contentLength().takeIf { it > 0 } ?: -1L
            val written = copyToFile(body.byteStream(), outFile, expected)
            // A connection cut mid-transfer ends the stream without an exception, so without this
            // a half-downloaded APK counted as a successful download.
            if (expected > 0 && written != expected) {
                throw IOException("Файл обновления скачан не полностью ($written из $expected)")
            }
        }
    }

    private suspend fun downloadFromSmb(url: String, outFile: File) {
        val (sourceId, path) = LocalUpdateUri.parse(url)
        val info = smbSourceRepository.connectionInfoById(sourceId) ?: throw IOException("Источник обновлений не найден")
        val connection = smbClient.connect(info)
        try {
            connection.openInputStream(path).use { input ->
                copyToFile(input, outFile, -1L)
            }
        } finally {
            connection.close()
        }
    }

    /** Returns how many bytes were written. */
    private suspend fun copyToFile(input: InputStream, outFile: File, total: Long): Long {
        var downloaded = 0L
        var lastReportedPercent = -1
        FileOutputStream(outFile).use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (isStopped) throw IOException("cancelled")
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                downloaded += read
                // Only actually pushes a new WorkInfo when the percent changes -
                // setProgress() on every 64KB chunk would be hundreds of updates/sec
                // for a fast connection, way more than the UI needs to redraw a bar.
                if (total > 0) {
                    val percent = (downloaded * 100 / total).toInt()
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        setProgress(workDataOf(KEY_DOWNLOADED to downloaded, KEY_TOTAL to total))
                    }
                }
            }
        }
        return downloaded
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_VERSION_CODE = "versionCode"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_FILE_PATH = "filePath"
        const val KEY_ERROR = "error"

        /** Suffix of the in-progress download - see [doWork]. */
        const val PART_SUFFIX = ".part"

        fun apkDir(context: Context): File = File(context.cacheDir, "updates").apply { mkdirs() }

        /** Where the finished APK for [versionCode] lands; only ever exists complete. */
        fun apkFile(context: Context, versionCode: Int): File =
            File(apkDir(context), "illusion-$versionCode.apk")
    }
}
