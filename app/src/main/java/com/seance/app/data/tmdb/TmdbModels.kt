package com.seance.app.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbSearchResponse(val results: List<TmdbSearchResult> = emptyList())

@Serializable
data class TmdbSearchResult(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val overview: String? = null
) {
    val displayTitle: String get() = title ?: name ?: ""
    val displayYear: String? get() = (releaseDate ?: firstAirDate)?.take(4)?.takeIf { it.length == 4 }
}

@Serializable
data class TmdbGenre(val name: String)

@Serializable
data class TmdbCastMember(val name: String, val order: Int = Int.MAX_VALUE)

@Serializable
data class TmdbCrewMember(val name: String, val job: String)

@Serializable
data class TmdbCredits(
    val cast: List<TmdbCastMember> = emptyList(),
    val crew: List<TmdbCrewMember> = emptyList()
)

@Serializable
data class TmdbExternalIds(@SerialName("imdb_id") val imdbId: String? = null)

@Serializable
data class TmdbProductionCompany(val name: String)

@Serializable
data class TmdbNetwork(val name: String)

@Serializable
data class TmdbCreator(val name: String)

@Serializable
data class TmdbMovieDetails(
    val id: Int,
    val title: String,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("production_companies") val productionCompanies: List<TmdbProductionCompany> = emptyList(),
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val credits: TmdbCredits? = null,
    @SerialName("external_ids") val externalIds: TmdbExternalIds? = null
)

@Serializable
data class TmdbTvDetails(
    val id: Int,
    val name: String,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val genres: List<TmdbGenre> = emptyList(),
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val networks: List<TmdbNetwork> = emptyList(),
    @SerialName("created_by") val createdBy: List<TmdbCreator> = emptyList(),
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val credits: TmdbCredits? = null,
    @SerialName("external_ids") val externalIds: TmdbExternalIds? = null
)

@Serializable
data class TmdbSeasonDetails(val episodes: List<TmdbEpisode> = emptyList())

@Serializable
data class TmdbEpisode(
    @SerialName("episode_number") val episodeNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null
)
