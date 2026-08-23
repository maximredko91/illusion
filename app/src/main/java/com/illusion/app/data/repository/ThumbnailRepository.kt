package com.illusion.app.data.repository

import com.illusion.app.data.local.dao.MediaItemDao
import com.illusion.app.data.local.dao.ThumbnailSpriteDao
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.local.entity.ThumbnailSpriteEntity

class ThumbnailRepository(
    private val spriteDao: ThumbnailSpriteDao,
    private val mediaItemDao: MediaItemDao
) {
    suspend fun getItemsMissingThumbnails(): List<MediaItemEntity> = mediaItemDao.getItemsWithoutThumbnails()

    suspend fun save(sprite: ThumbnailSpriteEntity) = spriteDao.upsert(sprite)

    suspend fun getForItem(stableId: String): ThumbnailSpriteEntity? = spriteDao.getById(stableId)

    suspend fun clearAll() = spriteDao.deleteAll()
}
