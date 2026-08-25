package com.illusion.app.data.update

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Checks the app's own GitHub Releases feed for a build newer than the one running. The only
 * network call this app makes outside the developer-only TMDB scraper (see TmdbClient's own KDoc)
 * - a deliberate, narrow exception to the offline-first design, since checking for an app update
 * is expected even of an otherwise-offline app and nothing here touches the media library.
 *
 * No GitHub auth token - the repo is public and this is a single unauthenticated GET, well under
 * GitHub's 60-requests/hour-per-IP anonymous rate limit for how often this app calls it.
 *
 * Release convention this depends on: tag the GitHub release so it contains the build's
 * versionCode as a run of digits (e.g. "v70" or "70") - [versionCodeFromTag] strips everything
 * else, so either form works. The release must also have exactly one .apk asset attached.
 */
class UpdateChecker(
    private val owner: String = "maximredko91",
    private val repo: String = "illusion"
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * [UpdateCheckResult.Failed] (no internet, GitHub unreachable, malformed response, ...) is
     * deliberately distinct from [UpdateCheckResult.UpToDate] - collapsing both into a single
     * null used to make "нет интернета" look identical to "у вас последняя версия" on the manual
     * check button, which is actively misleading (the user has no way to tell those two states
     * apart, and might reasonably conclude they're on the latest build when the check never
     * actually completed).
     */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult = withContext(Dispatchers.IO) {
        val release = runCatching { fetchLatestRelease() }.getOrElse {
            return@withContext UpdateCheckResult.Failed(it.message ?: "Неизвестная ошибка")
        }
        if (release.draft || release.prerelease) return@withContext UpdateCheckResult.UpToDate
        val versionCode = versionCodeFromTag(release.tagName) ?: return@withContext UpdateCheckResult.UpToDate
        if (versionCode <= currentVersionCode) return@withContext UpdateCheckResult.UpToDate
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return@withContext UpdateCheckResult.UpToDate
        UpdateCheckResult.Available(
            UpdateInfo(
                versionCode = versionCode,
                versionName = release.tagName,
                releaseNotes = release.body.orEmpty(),
                apkDownloadUrl = apk.browserDownloadUrl,
                apkSizeBytes = apk.size.takeIf { it > 0 },
                releasePageUrl = release.htmlUrl
            )
        )
    }

    private fun fetchLatestRelease(): GitHubRelease {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub API ${response.code}")
            val bodyText = response.body?.string() ?: throw IOException("Empty response")
            return json.decodeFromString(GitHubRelease.serializer(), bodyText)
        }
    }

    private fun versionCodeFromTag(tag: String): Int? =
        tag.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()
}
