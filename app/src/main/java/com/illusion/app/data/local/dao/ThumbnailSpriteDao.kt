package com.illusion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.illusion.app.data.local.entity.ThumbnailSpriteEntity

@Dao
interface ThumbnailSpriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sprite: ThumbnailSpriteEntity)

    @Query("SELECT * FROM thumbnail_sprites WHERE mediaItemStableId = :stableId")
    suspend fun getById(stableId: String): ThumbnailSpriteEntity?

    @Query("DELETE FROM thumbnail_sprites")
    suspend fun deleteAll()
}
