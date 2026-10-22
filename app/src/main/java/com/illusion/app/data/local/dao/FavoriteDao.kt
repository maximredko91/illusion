package com.illusion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.illusion.app.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Delete
    suspend fun remove(favorite: FavoriteEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaItemStableId = :stableId)")
    fun observeIsFavorite(stableId: String): Flow<Boolean>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    /** Всё избранное разом - для объединения источников (см. [com.illusion.app.data.repository.favoriteTransfers]). */
    @Query("SELECT * FROM favorites")
    suspend fun getAll(): List<FavoriteEntity>

    @Query("DELETE FROM favorites")
    suspend fun deleteAll()
}
