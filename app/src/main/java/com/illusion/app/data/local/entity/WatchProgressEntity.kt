package com.illusion.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val mediaItemStableId: String,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long
)
