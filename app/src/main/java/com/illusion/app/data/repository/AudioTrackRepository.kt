package com.illusion.app.data.repository

import com.illusion.app.data.local.dao.AudioTrackDao
import com.illusion.app.data.local.entity.AudioTrackEntity

class AudioTrackRepository(private val dao: AudioTrackDao) {
    suspend fun getForItem(stableId: String): AudioTrackEntity? = dao.getForItem(stableId)

    suspend fun save(stableId: String, tracks: List<String>, now: Long) {
        dao.upsert(AudioTrackEntity(stableId, tracks, now))
    }
}
