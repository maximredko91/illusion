package com.illusion.app.data.repository

import com.illusion.app.data.local.dao.FavoriteDao
import com.illusion.app.data.local.dao.WatchProgressDao
import com.illusion.app.data.local.entity.FavoriteEntity
import com.illusion.app.data.local.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

class WatchProgressRepository(
    private val progressDao: WatchProgressDao,
    private val favoriteDao: FavoriteDao
) {
    fun observeContinueWatching(limit: Int = 20): Flow<List<WatchProgressEntity>> =
        progressDao.observeContinueWatching(limit)

    fun observeHistory(): Flow<List<WatchProgressEntity>> = progressDao.observeHistory()

    suspend fun getProgress(stableId: String): WatchProgressEntity? = progressDao.getForItem(stableId)

    fun observeProgress(stableId: String): Flow<WatchProgressEntity?> = progressDao.observeForItem(stableId)

    suspend fun deleteHistoryEntry(stableId: String) = progressDao.deleteForItem(stableId)

    suspend fun clearHistory() = progressDao.deleteAll()

    suspend fun updateProgress(stableId: String, positionMs: Long, durationMs: Long, watched: Boolean, now: Long) {
        progressDao.upsert(
            WatchProgressEntity(
                mediaItemStableId = stableId,
                positionMs = positionMs,
                durationMs = durationMs,
                watched = watched,
                updatedAt = now
            )
        )
    }

    fun observeFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.observeAll()

    fun observeIsFavorite(stableId: String): Flow<Boolean> = favoriteDao.observeIsFavorite(stableId)

    suspend fun setFavorite(stableId: String, favorite: Boolean, now: Long) {
        if (favorite) {
            favoriteDao.add(FavoriteEntity(stableId, now))
        } else {
            favoriteDao.remove(FavoriteEntity(stableId, now))
        }
    }

    suspend fun restoreFavorites(entries: List<FavoriteEntity>) = entries.forEach { favoriteDao.add(it) }

    suspend fun clearFavorites() = favoriteDao.deleteAll()

    suspend fun restoreProgress(entries: List<WatchProgressEntity>) = entries.forEach { progressDao.upsert(it) }
}
