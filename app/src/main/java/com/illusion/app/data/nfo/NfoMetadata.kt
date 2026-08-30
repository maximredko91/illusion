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
    /** tvshow.nfo's own <status> (e.g. "Continuing"/"Ended"/"Canceled") - see [com.illusion.app.domain.model.statusLabel] for the Russian display mapping. */
    val status: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    /** Kodi/tinyMediaManager's <edition> - a fixed set of English codes (e.g. "DIRECTORS_CUT", "EXTENDED_EDITION"), not freeform text. See [com.illusion.app.domain.model.editionLabel] for the Russian display mapping. */
    val edition: String? = null,
    /** From <fileinfo><streamdetails><video> - tinyMediaManager (and other scrapers) writes this from a real one-time probe of the file at scan-in time, so reading it here is free (same nfo fetch already happening for every other field) versus this app re-probing every video's container header itself over SMB, which an on-device timing test showed to be dramatically slower for a library of thousands of files. */
    val videoWidth: Int? = null,
    val videoHeight: Int? = null
)
