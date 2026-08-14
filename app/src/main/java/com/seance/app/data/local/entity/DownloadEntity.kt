package com.seance.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED }

/** [remotePath] is kept alongside the local [uri] so the player can still derive a subtitle's language/label the same way it does for live SMB playback. */
@Serializable
data class DownloadedSubtitle(val uri: String, val remotePath: String)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val stableId: String,
    /** content:// Uri of the downloaded video, under either a user-picked SAF folder or the default public Downloads/Seans. */
    val contentUri: String,
    val subtitles: List<DownloadedSubtitle> = emptyList(),
    val status: DownloadStatus,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val updatedAt: Long,
    val errorMessage: String? = null
)
