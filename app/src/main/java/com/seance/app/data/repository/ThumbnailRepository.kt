package com.seance.app.data.repository

import com.seance.app.data.local.dao.MediaItemDao
import com.seance.app.data.local.dao.ThumbnailSpriteDao
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.local.entity.ThumbnailSpriteEntity

class ThumbnailRepository(
    private val spriteDao: ThumbnailSpriteDao,
    private val mediaItemDao: MediaItemDao
) {
    suspend fun getItemsMissingThumbnails(): List<MediaItemEntity> = mediaItemDao.getItemsWithoutThumbnails()

    suspend fun save(sprite: ThumbnailSpriteEntity) = spriteDao.upsert(sprite)

    suspend fun getForItem(stableId: String): ThumbnailSpriteEntity? = spriteDao.getById(stableId)

    suspend fun clearAll() = spriteDao.deleteAll()
}
