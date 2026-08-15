package com.seance.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached result of probing a video's container for its audio tracks (language + free-form
 * label, e.g. a dub studio name) - kept in its own table, not on [MediaItemEntity], so a library
 * rescan's whole-row REPLACE upsert never wipes it out (same reasoning as [WatchProgressEntity]/
 * [FavoriteEntity] living separately). [stableId] not present here just means "not probed yet",
 * not "no audio tracks".
 */
@Entity(tableName = "audio_tracks")
data class AudioTrackEntity(
    @PrimaryKey val stableId: String,
    val tracks: List<String>,
    val probedAt: Long
)
