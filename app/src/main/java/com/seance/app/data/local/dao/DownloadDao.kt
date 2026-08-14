package com.seance.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.seance.app.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE stableId = :stableId")
    suspend fun getForItem(stableId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE stableId = :stableId")
    fun observeForItem(stableId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads")
    suspend fun getAllOnce(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE stableId = :stableId")
    suspend fun delete(stableId: String)
}
