package com.seance.app.data.repository

import com.seance.app.data.local.dao.AudioTrackDao
import com.seance.app.data.local.entity.AudioTrackEntity

class AudioTrackRepository(private val dao: AudioTrackDao) {
    suspend fun getForItem(stableId: String): AudioTrackEntity? = dao.getForItem(stableId)

    suspend fun save(stableId: String, tracks: List<String>, now: Long) {
        dao.upsert(AudioTrackEntity(stableId, tracks, now))
    }
}
