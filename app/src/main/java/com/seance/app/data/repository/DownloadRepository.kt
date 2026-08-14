package com.seance.app.data.repository

import android.content.Context
import android.net.Uri
import com.seance.app.data.download.DownloadStorage
import com.seance.app.data.local.dao.DownloadDao
import com.seance.app.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val context: Context, private val dao: DownloadDao) {
    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    fun observeForItem(stableId: String): Flow<DownloadEntity?> = dao.observeForItem(stableId)

    suspend fun getForItem(stableId: String): DownloadEntity? = dao.getForItem(stableId)

    suspend fun upsert(download: DownloadEntity) = dao.upsert(download)

    /** Deletes the DB row and the downloaded video + subtitle files themselves - the two must never drift apart or a stale row would point at nothing. */
    suspend fun remove(stableId: String) {
        dao.getForItem(stableId)?.let { entity ->
            runCatching { DownloadStorage.delete(context, Uri.parse(entity.contentUri)) }
            entity.subtitles.forEach { sub -> runCatching { DownloadStorage.delete(context, Uri.parse(sub.uri)) } }
        }
        dao.delete(stableId)
    }

    /** Total on-disk size of every completed/in-progress download, for the Settings cache summary. */
    suspend fun totalSizeBytes(): Long = dao.getAllOnce().sumOf { it.downloadedBytes }

    suspend fun removeAll() {
        dao.getAllOnce().forEach { remove(it.stableId) }
    }
}
