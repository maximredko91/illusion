package com.illusion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.illusion.app.data.local.entity.TagTranslationEntity

@Dao
interface TagTranslationDao {
    @Query("SELECT * FROM tag_translations")
    suspend fun getAll(): List<TagTranslationEntity>

    @Query("SELECT * FROM tag_translations WHERE tag = :tag")
    suspend fun getOne(tag: String): TagTranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TagTranslationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TagTranslationEntity>)
}
