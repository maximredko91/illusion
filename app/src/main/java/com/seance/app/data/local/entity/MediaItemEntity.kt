package com.seance.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.seance.app.domain.model.Category

// Indices match MediaItemDao's actual WHERE clauses (observeByCategory, observeSeriesGroupedByCategory,
// observeByCollection, connectionInfoById-adjacent sourceId lookups) - added after the schema had been
// running full-table scans on a 3000+ row table since the first commit (see CLAUDE.md polish backlog).
@Entity(
    tableName = "media_items",
    indices = [
        Index("category"),
        Index("seriesStableId"),
        Index("collectionName"),
        Index("sourceId")
    ]
)
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
    val episodeThumbPath: String? = null,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val seriesStableId: String?,
    val dateAdded: Long,
    val sizeBytes: Long,
    val subtitlePaths: List<String>,
    /** SMB path of a sibling trailer file ("<video>-trailer.ext" or bare "trailer.ext" in the same folder), if one was found during scanning. */
    val trailerPath: String? = null,
    val trailerSizeBytes: Long? = null,
    /** The video file's own SMB last-write-time, as of the scan that produced this row - lets a later rescan detect "this exact file, unchanged" and skip re-hashing/re-parsing it. */
    val lastModified: Long = 0L,
    /** Same idea as [lastModified] but for the sidecar .nfo (null if none exists) - an nfo edited without touching the video must still trigger a re-parse. */
    val nfoLastModified: Long? = null,
    val introStartMs: Long? = null,
    val introEndMs: Long? = null,
    val mpaa: String? = null,
    val tagline: String? = null,
    val studio: String? = null,
    val premiered: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null
)

/** True if any sidecar subtitle file follows the "forced" naming convention (e.g. "Movie.forced.srt") - the file itself isn't parsed, just its name. */
val MediaItemEntity.hasForcedSubtitles: Boolean
    get() = subtitlePaths.any { it.substringAfterLast('\\').contains("forced", ignoreCase = true) }
