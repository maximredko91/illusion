package com.illusion.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.illusion.app.domain.model.Category

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
    val tmdbId: String? = null,
    /** Kodi's separate freeform <tag> field - distinct from [genres], often left in whatever language the scraper that wrote them used. */
    val tags: List<String> = emptyList(),
    /**
     * True only for a synthetic row created by [com.illusion.app.data.repository.DownloadRepository.recoverOrphanedDownloads] -
     * a video file found sitting in the default downloads folder with no matching library row
     * (app data cleared, or reinstalled, while the downloaded file itself survived). Has no real
     * NAS source (sourceId is a sentinel, filePath is the local content:// Uri) and only enough
     * metadata (title/year guessed from the folder name) to show up in Downloads and be playable -
     * excluded from every ordinary library browse query (see MediaItemDao) so it never shows up
     * in Home/Library/Search looking like a real, fully-scanned title.
     */
    val isOrphanedDownload: Boolean = false,
    /** Real pixel dimensions read from the video container's own header during scanning (LibraryScanner.extractVideoFormat) - null if that read failed (corrupt/unsupported header, dropped connection) or hasn't run yet. Drives the resolution badge on Details ([com.illusion.app.domain.model.videoQualityLabel]). */
    val videoWidth: Int? = null,
    val videoHeight: Int? = null
)

/** True if any sidecar subtitle file follows the "forced" naming convention (e.g. "Movie.forced.srt") - the file itself isn't parsed, just its name. */
val MediaItemEntity.hasForcedSubtitles: Boolean
    get() = subtitlePaths.any { it.substringAfterLast('\\').contains("forced", ignoreCase = true) }
