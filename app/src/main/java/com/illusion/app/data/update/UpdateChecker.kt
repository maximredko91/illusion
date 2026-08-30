package com.illusion.app.data.update

import android.os.Build
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
 * else, so either form works. Since the build produces one .apk per ABI (see app/build.gradle.kts'
 * own `splits.abi` block), each release attaches one .apk asset per ABI, its filename containing
 * that ABI's own name (e.g. "illusion-81-arm64-v8a.apk") - [selectApkForDevice] picks the one
 * matching the running device. A release with only a single unmarked .apk (the old one-file
 * convention) still works too, falling straight through as the only candidate.
 */
class UpdateChecker(
    private val owner: String = "maximredko91",
    private val repo: String = "illusion"
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // Bounds the WHOLE request, not just gaps between reads - see UpdateDownloadWorker's own
        // comment on the same setting for why readTimeout alone isn't enough on a connection that
        // trickles data in just under that window.
        .callTimeout(30, TimeUnit.SECONDS)
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
        if (release.draft || release.prerelease) return@withContext UpdateCheckResult.UpToDate()
        val versionCode = versionCodeFromTag(release.tagName) ?: return@withContext UpdateCheckResult.UpToDate()
        if (versionCode <= currentVersionCode) return@withContext UpdateCheckResult.UpToDate()
        val apk = selectApkForDevice(release.assets) ?: return@withContext UpdateCheckResult.UpToDate()
        val (mandatory, notes) = parseMandatory(release.body.orEmpty())
        UpdateCheckResult.Available(
            UpdateInfo(
                versionCode = versionCode,
                versionName = versionNameFromRelease(release),
                releaseNotes = notes,
                apkDownloadUrl = apk.browserDownloadUrl,
                apkSizeBytes = apk.size.takeIf { it > 0 },
                releasePageUrl = release.htmlUrl,
                mandatory = mandatory
            )
        )
    }

    /**
     * A release is marked mandatory by starting its body with a `[MANDATORY]` or `[ОБЯЗАТЕЛЬНОЕ]`
     * marker line (case-insensitive) - see UpdateInfo.mandatory's own KDoc for what that actually
     * does in the UI. The marker itself is stripped out before the text is shown as release notes,
     * so it never leaks into the "what's new" dialog as visible clutter.
     */
    private fun parseMandatory(body: String): Pair<Boolean, String> {
        val firstLine = body.lineSequence().firstOrNull()?.trim().orEmpty()
        val isMandatory = firstLine.equals("[MANDATORY]", ignoreCase = true) ||
            firstLine.equals("[ОБЯЗАТЕЛЬНОЕ]", ignoreCase = true)
        val notes = if (isMandatory) body.substringAfter('\n').trimStart('\n', '\r') else body
        return isMandatory to notes
    }

    /**
     * Was `release.tagName` directly - the tag only ever carries the versionCode digits (e.g.
     * "v75"), so the "What's new" dialog showed "Доступно обновление v75" instead of the app's
     * actual semantic version. The release title is published as "vNN (versionName)" (see
     * PowerShell's `gh release create --title`) - pulls the parenthesized part out of that, and
     * falls back to the tag if the title is missing/doesn't follow the convention rather than
     * failing the whole check over a cosmetic string.
     */
    private fun versionNameFromRelease(release: GitHubRelease): String =
        release.name?.let { title -> Regex("\\(([^)]+)\\)").find(title)?.groupValues?.get(1) }
            ?: release.tagName

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

    /**
     * A release attaches one .apk per ABI (build.gradle.kts' `splits.abi`), so more than one
     * .apk asset is the normal case, not an error. [Build.SUPPORTED_ABIS] lists every
     * ABI this exact device can run, most-preferred first (e.g. a 64-bit device that can also run
     * 32-bit code lists both, arm64 first) - matching against it in order means a device capable
     * of more than one of this app's ABIs always gets its best one, not whichever happened to
     * sort first among the release's assets. Falls back to the release's only .apk (or simply the
     * first one, if for some reason none of the names match any supported ABI) rather than
     * failing the whole check - an old single-file release, or an unmarked asset, should still be
     * offered as an update instead of silently reporting up to date.
     */
    private fun selectApkForDevice(assets: List<GitHubReleaseAsset>): GitHubReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.size <= 1) return apks.firstOrNull()
        return Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
            apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }
        } ?: apks.first()
    }
}
