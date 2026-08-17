package com.seance.app.data.tmdb

import com.seance.app.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Minimal TMDB v3 REST client for the developer-only "add media" scraper (`ui/addmedia`) - the
 * only place this app ever calls out to the internet for metadata, and only ever at the moment
 * new media is being added, not on every scan/view (this is otherwise an offline-first app). No
 * retrofit/DI: three-ish endpoints, plain OkHttp + kotlinx.serialization is enough.
 *
 * [apiKeyProvider] is read fresh on every call rather than cached once - lets the key be entered
 * in-app (`DevAccessStore.tmdbApiKey`, editable from `AddMediaScreen`) and take effect immediately
 * without restarting the app, instead of only ever coming from a local.properties rebuild.
 */
class TmdbClient(private val apiKeyProvider: () -> String = { BuildConfig.TMDB_API_KEY }) {
    private val apiKey: String get() = apiKeyProvider()

    /** False if no key has been entered in-app or set via `seance.tmdb.apiKey` in local.properties - callers should disable the add-media entry point rather than let every request fail. */
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchMovies(query: String, language: String = "ru-RU"): List<TmdbSearchResult> =
        json.decodeFromString<TmdbSearchResponse>(
            get("search/movie") {
                addQueryParameter("query", query)
                addQueryParameter("language", language)
            }
        ).results

    suspend fun searchTvShows(query: String, language: String = "ru-RU"): List<TmdbSearchResult> =
        json.decodeFromString<TmdbSearchResponse>(
            get("search/tv") {
                addQueryParameter("query", query)
                addQueryParameter("language", language)
            }
        ).results

    suspend fun getMovieDetails(id: Int, language: String = "ru-RU"): TmdbMovieDetails =
        json.decodeFromString(
            get("movie/$id") {
                addQueryParameter("language", language)
                addQueryParameter("append_to_response", "credits,external_ids,release_dates")
            }
        )

    suspend fun getTvDetails(id: Int, language: String = "ru-RU"): TmdbTvDetails =
        json.decodeFromString(
            get("tv/$id") {
                addQueryParameter("language", language)
                addQueryParameter("append_to_response", "credits,external_ids,content_ratings")
            }
        )

    suspend fun getSeasonDetails(tvId: Int, seasonNumber: Int, language: String = "ru-RU"): TmdbSeasonDetails =
        json.decodeFromString(
            get("tv/$tvId/season/$seasonNumber") {
                addQueryParameter("language", language)
            }
        )

    /** Downloads a poster/backdrop/still image's raw bytes for writing to the NAS as a local file - see [NfoWriter]'s KDoc for why nothing here is ever embedded as a remote URL instead. */
    suspend fun downloadImage(path: String, size: String = "w780"): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$IMAGE_BASE_URL$size$path").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("TMDB image fetch failed: ${response.code}")
            response.body?.bytes() ?: throw IOException("TMDB image fetch: empty body")
        }
    }

    /** For showing a thumbnail in the search-results picker before anything is downloaded/written. */
    fun previewUrl(path: String, size: String = "w185"): String = "$IMAGE_BASE_URL$size$path"

    private suspend fun get(path: String, params: HttpUrl.Builder.() -> Unit): String = withContext(Dispatchers.IO) {
        check(isConfigured) { "TMDB API key not configured - set seance.tmdb.apiKey in local.properties" }
        val url = "$API_BASE_URL$path".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .apply(params)
            .build()
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            val bodyText = response.body?.string()
            if (!response.isSuccessful) throw IOException("TMDB request failed (${response.code}): $path")
            bodyText ?: throw IOException("TMDB request: empty body")
        }
    }

    companion object {
        private const val API_BASE_URL = "https://api.themoviedb.org/3/"
        private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
    }
}
