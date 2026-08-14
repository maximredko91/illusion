package com.seance.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.seance.app.data.image.fanartModel
import com.seance.app.data.image.posterModel
import com.seance.app.data.repository.LibraryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Warms Coil's disk cache for every poster/fanart in the library, so the grid renders instantly instead of loading images one by one over SMB. */
class PosterPreloadWorker(
    context: Context,
    params: WorkerParameters,
    private val libraryRepository: LibraryRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val items = libraryRepository.getAll()
        val imageLoader = SingletonImageLoader.get(applicationContext)
        val models = items.flatMap { listOfNotNull(it.posterModel, it.fanartModel) }.distinct()
        var done = 0
        setProgress(workDataOf(KEY_PROCESSED to 0, KEY_TOTAL to models.size))

        coroutineScope {
            models.chunked(CONCURRENCY).forEach { chunk ->
                chunk.map { model ->
                    async {
                        runCatching {
                            imageLoader.execute(ImageRequest.Builder(applicationContext).data(model).build())
                        }
                    }
                }.awaitAll()
                done += chunk.size
                setProgress(workDataOf(KEY_PROCESSED to done, KEY_TOTAL to models.size))
            }
        }

        return Result.success()
    }

    companion object {
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        private const val CONCURRENCY = 6
    }
}
