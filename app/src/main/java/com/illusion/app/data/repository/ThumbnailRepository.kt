package com.illusion.app.data.repository

import com.illusion.app.data.local.dao.ThumbnailSpriteDao
import com.illusion.app.data.local.entity.ThumbnailSpriteEntity

class ThumbnailRepository(
    private val spriteDao: ThumbnailSpriteDao
) {
    suspend fun save(sprite: ThumbnailSpriteEntity) = spriteDao.upsert(sprite)

    suspend fun getForItem(stableId: String): ThumbnailSpriteEntity? = spriteDao.getById(stableId)

    suspend fun clearAll() = spriteDao.deleteAll()
}
