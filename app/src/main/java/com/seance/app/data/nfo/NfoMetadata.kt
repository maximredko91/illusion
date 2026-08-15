package com.seance.app.data.nfo

data class NfoMetadata(
    val title: String? = null,
    val originalTitle: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val plot: String? = null,
    val director: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    /** Parallel to [actors] by index; empty string where the nfo has no role for that actor. */
    val actorRoles: List<String> = emptyList(),
    val country: String? = null,
    val runtimeMinutes: Int? = null,
    val collectionName: String? = null,
    val posterUrl: String? = null,
    val fanartUrl: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val mpaa: String? = null,
    val tagline: String? = null,
    val studio: String? = null,
    val premiered: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null
)
