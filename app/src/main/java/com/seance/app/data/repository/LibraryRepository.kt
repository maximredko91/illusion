package com.seance.app.data.repository

import com.seance.app.data.local.dao.MediaItemDao
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.domain.model.Category
import com.seance.app.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(private val dao: MediaItemDao) {

    suspend fun getAll(): List<MediaItemEntity> = dao.getAll()
    suspend fun getBySource(sourceId: Long): List<MediaItemEntity> = dao.getBySource(sourceId)
    fun observeByCategory(category: Category, sort: SortOrder): Flow<List<MediaItemEntity>> =
        dao.observeByCategory(category, sort.name)

    /**
     * Same as [observeByCategory] but collapses every episode of a series down to a single
     * representative row (its earliest season/episode) - for a TV/cartoon-series library grid,
     * so browsing shows one card per show instead of one per episode. Only this representative's
     * own copy gets its title swapped for the show's name (derived from its folder) - elsewhere
     * (recently-added, filmography, similar) an episode should still show its own episode title.
     */
    fun observeSeriesGroupedByCategory(category: Category, sort: SortOrder): Flow<List<MediaItemEntity>> =
        observeByCategory(category, sort).map { items ->
            val (episodes, standalone) = items.partition { it.seriesStableId != null }
            val representatives = episodes
                .groupBy { it.seriesStableId }
                .values
                .map { group ->
                    val representative = group.minWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }))
                    val seriesTitle = representative.seriesStableId?.substringAfterLast('\\')
                    if (seriesTitle != null) representative.copy(title = seriesTitle) else representative
                }
            standalone + representatives
        }

    fun observeRecentlyAdded(limit: Int = 20): Flow<List<MediaItemEntity>> =
        dao.observeRecentlyAdded(limit)

    fun observeEpisodes(seriesStableId: String): Flow<List<MediaItemEntity>> =
        dao.observeEpisodes(seriesStableId)

    fun observeByCollection(collectionName: String): Flow<List<MediaItemEntity>> =
        dao.observeByCollection(collectionName)

    fun search(query: String): Flow<List<MediaItemEntity>> = dao.search(query)

    suspend fun getById(stableId: String): MediaItemEntity? = dao.getById(stableId)

    suspend fun upsertAll(items: List<MediaItemEntity>) = dao.upsertAll(items)

    /** Other items in the same category sharing at least one genre with [item], for the "Похожие" block. */
    suspend fun getSimilar(item: MediaItemEntity, limit: Int = 12): List<MediaItemEntity> {
        if (item.genres.isEmpty()) return emptyList()
        val minShared = minOf(2, item.genres.size)
        return dao.getByCategory(item.category)
            .asSequence()
            .filter { it.stableId != item.stableId }
            .filter { item.seriesStableId == null || it.seriesStableId != item.seriesStableId }
            // A candidate sharing the same imdb/tmdb id is the same title under a different file
            // (a re-encode, a copy in another SMB source) - not a recommendation, so it doesn't
            // belong in "Похожие" even if genres overlap.
            .filter { candidate ->
                (item.imdbId == null || candidate.imdbId != item.imdbId) &&
                    (item.tmdbId == null || candidate.tmdbId != item.tmdbId)
            }
            .distinctBy { it.seriesStableId ?: it.stableId }
            .map { candidate -> candidate to candidate.genres.count { genre -> genre in item.genres } }
            .filter { (_, shared) -> shared >= minShared }
            .sortedWith(compareByDescending<Pair<MediaItemEntity, Int>> { it.second }.thenByDescending { it.first.rating ?: 0.0 })
            .map { (candidate, _) -> candidate }
            .take(limit)
            .toList()
    }

    /** Everything a person appears in as an actor or director, for the filmography screen. */
    suspend fun getFilmography(personName: String): List<MediaItemEntity> =
        dao.getAll().filter { personName in it.actors || personName in it.director }

    /**
     * Manual stand-in for the audio-fingerprint auto-detection that isn't built yet: the user
     * taps "mark end of intro" once, at the position where this episode's intro ends, and it's
     * assumed to start at 0 and be identical across the season (true for the overwhelming
     * majority of shows), so it's written to every episode in [item]'s season at once rather than
     * just this one. Re-marking from a later episode simply overwrites the season's marker.
     */
    suspend fun markIntroEnd(item: MediaItemEntity, endMs: Long) {
        val seriesId = item.seriesStableId
        val season = item.seasonNumber
        if (seriesId != null && season != null) {
            dao.setIntroMarkersForSeason(seriesId, season, 0L, endMs)
        } else {
            dao.setIntroMarkers(item.stableId, 0L, endMs)
        }
    }

    /** Undoes [markIntroEnd] - same single-item-vs-whole-season scoping. */
    suspend fun clearIntroMarkers(item: MediaItemEntity) {
        val seriesId = item.seriesStableId
        val season = item.seasonNumber
        if (seriesId != null && season != null) {
            dao.clearIntroMarkersForSeason(seriesId, season)
        } else {
            dao.clearIntroMarkers(item.stableId)
        }
    }
}
