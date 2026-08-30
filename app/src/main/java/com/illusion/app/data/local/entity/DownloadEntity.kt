package com.illusion.app.data.local.entity

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
    /** content:// Uri of the downloaded video, under either a user-picked SAF folder or the default public Downloads/Illusion. */
    val contentUri: String,
    val subtitles: List<DownloadedSubtitle> = emptyList(),
    val status: DownloadStatus,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val updatedAt: Long,
    val errorMessage: String? = null,
    /** Local content:// Uri of a poster/fanart copied alongside the video, if the source had one - see [com.illusion.app.work.DownloadWorker]'s own KDoc. Lets a recovered orphaned download (data wipe/reinstall) still show a poster/fanart with no SMB source to fetch one from. Null for downloads made before this existed. */
    val posterUri: String? = null,
    val fanartUri: String? = null
)
