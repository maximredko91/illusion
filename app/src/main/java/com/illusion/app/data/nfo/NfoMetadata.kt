package com.illusion.app.data.nfo

data class NfoMetadata(
    val title: String? = null,
    val originalTitle: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    /** Kodi's separate freeform <tag> field - keywords/labels distinct from <genre>, often left in whatever language the scraper that wrote them used (frequently English even when genre itself is Russian). */
    val tags: List<String> = emptyList(),
    val rating: Double? = null,
    val plot: String? = null,
    val director: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
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
