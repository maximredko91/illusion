package com.illusion.app.domain.model

import com.illusion.app.data.local.entity.MediaItemEntity

/** Standard resolution-tier label (like "4K"/"1080p") derived from the taller of width/height - taller rather than a fixed axis since a portrait-shot video would otherwise read as SD off its (small) width alone. Null when [MediaItemEntity.videoHeight]/[MediaItemEntity.videoWidth] haven't been read yet (see LibraryScanner.extractVideoFormat's own KDoc for when that happens). */
val MediaItemEntity.videoQualityLabel: String?
    get() {
        val width = videoWidth ?: return null
        val height = videoHeight ?: return null
        val longSide = maxOf(width, height)
        return when {
            longSide >= 3840 -> "4K"
            longSide >= 1920 -> "1080p"
            longSide >= 1280 -> "720p"
            longSide >= 720 -> "SD"
            else -> null
        }
    }
