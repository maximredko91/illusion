package com.illusion.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads the .apk for a GitHub release UpdateChecker already confirmed is newer than the
 * running build - a plain WorkManager job (not a suspend fun the caller awaits directly) so the
 * download survives the update dialog being dismissed/the screen being backgrounded, same as this
 * app's other background downloads (see DownloadWorker).
 */
class UpdateDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val versionCode = inputData.getInt(KEY_VERSION_CODE, -1)
        if (versionCode < 0) return@withContext Result.failure()

        val outFile = File(apkDir(applicationContext), "illusion-$versionCode.apk")
        val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure()
                val body = response.body ?: return@withContext Result.failure()
                val total = body.contentLength().takeIf { it > 0 } ?: -1L
                var downloaded = 0L
                var lastReportedPercent = -1
                body.byteStream().use { input ->
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
                }
            }
        } catch (e: Exception) {
            runCatching { outFile.delete() }
            return@withContext Result.failure()
        }
        Result.success(workDataOf(KEY_FILE_PATH to outFile.absolutePath))
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_VERSION_CODE = "versionCode"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_FILE_PATH = "filePath"

        fun apkDir(context: Context): File = File(context.cacheDir, "updates").apply { mkdirs() }
    }
}
