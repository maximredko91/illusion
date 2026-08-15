package com.seance.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.seance.app.data.local.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WatchProgressEntity)

    @Query("SELECT * FROM watch_progress WHERE mediaItemStableId = :stableId")
    suspend fun getForItem(stableId: String): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress WHERE mediaItemStableId = :stableId")
    fun observeForItem(stableId: String): Flow<WatchProgressEntity?>

    @Query(
        """
        SELECT * FROM watch_progress
        WHERE watched = 0 AND positionMs > 0
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    fun observeContinueWatching(limit: Int = 20): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC")
    fun observeHistory(): Flow<List<WatchProgressEntity>>

    @Query("DELETE FROM watch_progress WHERE mediaItemStableId = :stableId")
    suspend fun deleteForItem(stableId: String)

    @Query("DELETE FROM watch_progress")
    suspend fun deleteAll()
}
