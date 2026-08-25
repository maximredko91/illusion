package com.illusion.app.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    /** The release's own title (e.g. "v75 (0.1.0-beta2)") - distinct from [tagName] (e.g. "v75"), which only carries the versionCode digits UpdateChecker compares against. */
    val name: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L
)
