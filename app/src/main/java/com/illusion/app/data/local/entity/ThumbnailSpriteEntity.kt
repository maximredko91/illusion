package com.illusion.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A grid of scrubbing-preview frames for one video, sampled at [intervalMs] and stored as one JPEG in the app cache. */
@Entity(tableName = "thumbnail_sprites")
data class ThumbnailSpriteEntity(
    @PrimaryKey val mediaItemStableId: String,
    val filePath: String,
    val intervalMs: Long,
    val columns: Int,
    val rows: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val frameCount: Int
)
