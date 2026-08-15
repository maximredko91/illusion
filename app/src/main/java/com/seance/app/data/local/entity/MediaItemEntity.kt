package com.seance.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.seance.app.domain.model.Category

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val stableId: String,
    val sourceId: Long,
    val filePath: String,
    val category: Category,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val genres: List<String>,
    val rating: Double?,
    val country: String?,
    val runtimeMinutes: Int?,
    val plot: String?,
    val director: List<String>,
    val actors: List<String>,
    val actorRoles: List<String> = emptyList(),
    val collectionName: String?,
    val posterPath: String?,
    val fanartPath: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val seriesStableId: String?,
    val hasNfo: Boolean,
    val dateAdded: Long,
    val sizeBytes: Long,
    val subtitlePaths: List<String>,
    val introStartMs: Long? = null,
    val introEndMs: Long? = null,
    val mpaa: String? = null,
    val tagline: String? = null,
    val studio: String? = null,
    val premiered: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null
)
