package com.seance.app.data.local

import androidx.room.TypeConverter
import com.seance.app.data.local.entity.DownloadStatus
import com.seance.app.data.local.entity.DownloadedSubtitle
import com.seance.app.domain.model.Category
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = Json.decodeFromString(value)

    @TypeConverter
    fun fromCategory(value: Category): String = value.name

    @TypeConverter
    fun toCategory(value: String): Category = Category.valueOf(value)

    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus): String = value.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)

    @TypeConverter
    fun fromDownloadedSubtitles(value: List<DownloadedSubtitle>): String = Json.encodeToString(value)

    @TypeConverter
    fun toDownloadedSubtitles(value: String): List<DownloadedSubtitle> = Json.decodeFromString(value)
}
