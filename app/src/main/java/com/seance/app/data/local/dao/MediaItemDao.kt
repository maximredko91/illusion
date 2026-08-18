package com.seance.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.domain.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Query("SELECT * FROM media_items WHERE stableId = :stableId")
    suspend fun getById(stableId: String): MediaItemEntity?

    // actors/director are stored as a JSON string array (Converters.fromStringList) - matching
    // against a quoted name (`"Name"`) avoids a false positive on a name that's merely a substring
    // of another (e.g. "Anna" inside "Anna-Maria"). Filters at the DB level instead of pulling
    // every row into memory to filter in Kotlin (previously LibraryRepository.getFilmography did
    // a full getAll() scan, redundant with the one DetailsViewModel already does for the same item
    // just to compute which names are worth linking).
    @Query(
        """
        SELECT * FROM media_items
        WHERE actors LIKE '%"' || :name || '"%' OR director LIKE '%"' || :name || '"%'
        """
    )
    suspend fun getFilmography(name: String): List<MediaItemEntity>

    @Query(
        """
        SELECT * FROM media_items WHERE category = :category
        ORDER BY
            CASE WHEN :sort = 'YEAR' THEN year END DESC,
            CASE WHEN :sort = 'RATING' THEN rating END DESC,
            CASE WHEN :sort = 'DATE_ADDED' THEN dateAdded END DESC,
            CASE WHEN :sort = 'TITLE' THEN title END ASC
        """
    )
    fun observeByCategory(category: Category, sort: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY dateAdded DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int = 20): Flow<List<MediaItemEntity>>

    // Title/originalTitle matches are ranked first (rankMatch = 0), everything else (plot, actors,
    // seriesStableId - kept broad on purpose so a search still finds a title by a remembered plot
    // detail or actor name) sorts after, alphabetically within each group. Without this, a query
    // that happens to appear mid-word in some unrelated item's plot (e.g. "престиж" inside
    // "престижные казино") ranked identically to an actual title match.
    @Query(
        """
        SELECT *, CASE WHEN title LIKE '%' || :query || '%' OR originalTitle LIKE '%' || :query || '%' THEN 0 ELSE 1 END AS rankMatch
        FROM media_items
        WHERE title LIKE '%' || :query || '%'
           OR originalTitle LIKE '%' || :query || '%'
           OR plot LIKE '%' || :query || '%'
           OR actors LIKE '%' || :query || '%'
           OR seriesStableId LIKE '%' || :query || '%'
        ORDER BY rankMatch, title
        """
    )
    fun search(query: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE seriesStableId = :seriesStableId ORDER BY seasonNumber, episodeNumber")
    fun observeEpisodes(seriesStableId: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE collectionName = :collectionName")
    fun observeByCollection(collectionName: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE category = :category")
    suspend fun getByCategory(category: Category): List<MediaItemEntity>

    @Query("SELECT * FROM media_items")
    suspend fun getAll(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE sourceId = :sourceId")
    suspend fun getBySource(sourceId: Long): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE stableId NOT IN (SELECT mediaItemStableId FROM thumbnail_sprites)")
    suspend fun getItemsWithoutThumbnails(): List<MediaItemEntity>

    @Query("UPDATE media_items SET introStartMs = :startMs, introEndMs = :endMs WHERE stableId = :stableId")
    suspend fun setIntroMarkers(stableId: String, startMs: Long, endMs: Long)

    @Query("UPDATE media_items SET introStartMs = :startMs, introEndMs = :endMs WHERE seriesStableId = :seriesStableId AND seasonNumber = :seasonNumber")
    suspend fun setIntroMarkersForSeason(seriesStableId: String, seasonNumber: Int, startMs: Long, endMs: Long)

    @Query("UPDATE media_items SET introStartMs = NULL, introEndMs = NULL WHERE stableId = :stableId")
    suspend fun clearIntroMarkers(stableId: String)

    @Query("UPDATE media_items SET introStartMs = NULL, introEndMs = NULL WHERE seriesStableId = :seriesStableId AND seasonNumber = :seasonNumber")
    suspend fun clearIntroMarkersForSeason(seriesStableId: String, seasonNumber: Int)
}
