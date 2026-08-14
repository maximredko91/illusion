package com.seance.app.data.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.local.entity.ThumbnailSpriteEntity
import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.smb.SmbClient
import com.seance.app.data.smb.SmbConnection
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Samples frames from a video on the SMB share and packs them into one JPEG sprite sheet,
 * used by the player's seek bar to show a preview while scrubbing. Slow by nature (many
 * remote reads + frame decodes per video) - always run from a background worker.
 */
class ThumbnailGenerator(
    private val sourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient,
    private val context: Context
) {
    suspend fun generate(item: MediaItemEntity): ThumbnailSpriteEntity? = withContext(Dispatchers.IO) {
        if (item.sizeBytes <= 0) return@withContext null
        val info = sourceRepository.connectionInfoById(item.sourceId) ?: return@withContext null
        val connection = smbClient.connect(info)
        connection.use { extractSprite(item, it) }
    }

    private fun extractSprite(item: MediaItemEntity, connection: SmbConnection): ThumbnailSpriteEntity? {
        val randomAccessFile = connection.openRandomAccessFile(item.filePath)
        val dataSource = SmbMediaDataSource(randomAccessFile, item.sizeBytes)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(dataSource)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return null
            if (durationMs <= 0) return null

            val frameCount = (durationMs / MIN_INTERVAL_MS).toInt().coerceIn(1, MAX_FRAMES)
            val intervalMs = durationMs / frameCount

            val frames = mutableListOf<Bitmap>()
            for (index in 0 until frameCount) {
                val timeUs = index * intervalMs * 1000
                val original = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val scaled = Bitmap.createScaledBitmap(original, FRAME_WIDTH, FRAME_HEIGHT, true)
                if (scaled !== original) original.recycle()
                frames += scaled
            }
            if (frames.isEmpty()) return null

            val columns = ceil(sqrt(frames.size.toDouble())).toInt().coerceAtLeast(1)
            val rows = ceil(frames.size.toDouble() / columns).toInt()
            val sprite = Bitmap.createBitmap(columns * FRAME_WIDTH, rows * FRAME_HEIGHT, Bitmap.Config.RGB_565)
            val canvas = Canvas(sprite)
            frames.forEachIndexed { index, frame ->
                val col = index % columns
                val row = index / columns
                canvas.drawBitmap(frame, (col * FRAME_WIDTH).toFloat(), (row * FRAME_HEIGHT).toFloat(), null)
                frame.recycle()
            }

            val outFile = File(spriteDir(context), "${item.stableId}.jpg")
            FileOutputStream(outFile).use { out -> sprite.compress(Bitmap.CompressFormat.JPEG, 80, out) }
            sprite.recycle()

            return ThumbnailSpriteEntity(
                mediaItemStableId = item.stableId,
                filePath = outFile.absolutePath,
                intervalMs = intervalMs,
                columns = columns,
                rows = rows,
                frameWidth = FRAME_WIDTH,
                frameHeight = FRAME_HEIGHT,
                frameCount = frames.size
            )
        } catch (e: Exception) {
            return null
        } finally {
            retriever.release()
            runCatching { dataSource.close() }
        }
    }

    companion object {
        private const val FRAME_WIDTH = 160
        private const val FRAME_HEIGHT = 90
        private const val MIN_INTERVAL_MS = 10_000L
        private const val MAX_FRAMES = 100

        fun spriteDir(context: Context): File = File(context.cacheDir, "thumbnails").apply { mkdirs() }
    }
}
