package com.seance.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.seance.app.data.local.entity.AudioTrackEntity

@Dao
interface AudioTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AudioTrackEntity)

    @Query("SELECT * FROM audio_tracks WHERE stableId = :stableId")
    suspend fun getForItem(stableId: String): AudioTrackEntity?
}
