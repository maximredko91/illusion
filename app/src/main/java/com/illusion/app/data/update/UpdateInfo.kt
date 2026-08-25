package com.illusion.app.data.update

/**
 * A GitHub release newer than the running build. [versionCode] is parsed from the release's
 * git tag (digits only, so both "v70" and "70" work) and compared against BuildConfig.VERSION_CODE -
 * versionName isn't used for the comparison since this app's alphaN naming isn't reliably
 * orderable as a string ("alpha9" vs "alpha10").
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long?,
    val releasePageUrl: String
)
