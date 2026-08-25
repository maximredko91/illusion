package com.illusion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.illusion.app.data.local.entity.SmbSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmbSourceDao {
    @Query("SELECT * FROM smb_sources ORDER BY id")
    fun observeAll(): Flow<List<SmbSourceEntity>>

    @Query("SELECT * FROM smb_sources WHERE enabled = 1")
    suspend fun getEnabled(): List<SmbSourceEntity>

    @Query("SELECT * FROM smb_sources WHERE id = :id")
    suspend fun getById(id: Long): SmbSourceEntity?

    @Query("UPDATE smb_sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: SmbSourceEntity): Long

    @Update
    suspend fun update(source: SmbSourceEntity)

    @Delete
    suspend fun delete(source: SmbSourceEntity)
}
