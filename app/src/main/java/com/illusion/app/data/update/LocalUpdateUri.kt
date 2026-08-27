package com.illusion.app.data.update

import android.net.Uri

/**
 * Encodes (sourceId, path) for a local-update APK into a pseudo-URL string, so [UpdateInfo] and
 * everything downstream of it (UpdateDownloadWorker's own `KEY_URL`, WorkManager's persisted
 * input data) can carry either a real GitHub https:// asset URL or a local SMB one through the
 * exact same String field, distinguished only by scheme - no separate "which kind of update is
 * this" field needed anywhere else.
 */
object LocalUpdateUri {
    private const val SCHEME = "smb-update"

    fun build(sourceId: Long, path: String): String = "$SCHEME://$sourceId/${Uri.encode(path)}"

    fun isLocal(url: String): Boolean = url.startsWith("$SCHEME://")

    fun parse(url: String): Pair<Long, String> {
        val uri = Uri.parse(url)
        val sourceId = requireNotNull(uri.authority?.toLongOrNull()) { "Invalid local update uri, missing source id: $url" }
        val path = requireNotNull(uri.path?.removePrefix("/")) { "Invalid local update uri, missing path: $url" }
        return sourceId to Uri.decode(path)
    }
}
