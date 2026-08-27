package com.illusion.app.data.update

import kotlinx.serialization.Serializable

/**
 * `IllusionUpdates/manifest.json`, published by hand to the same SMB source alongside its listed
 * [assets] (also under `IllusionUpdates/`) - see [LocalUpdateChecker]. A minimal hand-authored
 * counterpart to what UpdateChecker already gets for free from the GitHub Releases API.
 */
@Serializable
data class LocalUpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String = "",
    val assets: List<LocalUpdateManifestAsset>
)

/** [abi] matches one of [android.os.Build.SUPPORTED_ABIS] (e.g. "arm64-v8a") - [fileName] is
 * just the file's own name under `IllusionUpdates/`, not a full path. */
@Serializable
data class LocalUpdateManifestAsset(
    val abi: String,
    val fileName: String,
    val sizeBytes: Long
)
