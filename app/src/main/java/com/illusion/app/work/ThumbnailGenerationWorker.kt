package com.illusion.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.illusion.app.data.repository.ThumbnailRepository
import com.illusion.app.data.scan.ThumbnailGenerator

class ThumbnailGenerationWorker(
    context: Context,
    params: WorkerParameters,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val thumbnailRepository: ThumbnailRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val items = thumbnailRepository.getItemsMissingThumbnails()
        items.forEachIndexed { index, item ->
            setProgress(workDataOf(KEY_PROCESSED to index, KEY_TOTAL to items.size))
            runCatching { thumbnailGenerator.generate(item) }
                .getOrNull()
                ?.let { thumbnailRepository.save(it) }
        }
        return Result.success()
    }

    companion object {
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
    }
}
