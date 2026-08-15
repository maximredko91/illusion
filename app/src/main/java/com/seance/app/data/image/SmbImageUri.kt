package com.seance.app.data.image

import android.net.Uri
import com.seance.app.data.local.entity.MediaItemEntity

/**
 * Encodes an SMB source id + in-share image path as a Uri Coil can hold as a request model, so
 * [SmbImageFetcher] can resolve it back to a share/file without a DB lookup on the UI thread.
 * Mirrors [com.seance.app.data.player.SmbMediaUri] but kept separate since images and video use
 * different fetch/decode pipelines (Coil vs Media3).
 */
object SmbImageUri {
    const val SCHEME = "smb-image"

    fun build(sourceId: Long, path: String): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(sourceId.toString())
        .path(path)
        .build()

    data class Parsed(val sourceId: Long, val path: String)

    /** Reads straight off a [coil3.Uri]'s already-decoded authority/path - no re-parsing needed. */
    fun parse(authority: String?, path: String?): Parsed {
        val sourceId = requireNotNull(authority?.toLongOrNull()) { "Invalid smb-image uri, missing source id" }
        val filePath = requireNotNull(path) { "Invalid smb-image uri, missing path" }.removePrefix("/")
        return Parsed(sourceId, filePath)
    }

    /** [posterOrFanartPath] as stored on [com.seance.app.data.local.entity.MediaItemEntity] - a remote URL is passed straight through, a share-relative path is wrapped as a Coil model. */
    fun resolve(sourceId: Long, posterOrFanartPath: String?): Any? = when {
        posterOrFanartPath.isNullOrBlank() -> null
        posterOrFanartPath.startsWith("http://") || posterOrFanartPath.startsWith("https://") -> posterOrFanartPath
        else -> build(sourceId, posterOrFanartPath)
    }
}

/** Coil request model for this item's poster, or null if it has none. */
val MediaItemEntity.posterModel: Any?
    get() = SmbImageUri.resolve(sourceId, posterPath)

/** Coil request model for this item's fanart/backdrop, or null if it has none. */
val MediaItemEntity.fanartModel: Any?
    get() = SmbImageUri.resolve(sourceId, fanartPath)

/** Coil request model for this episode's own screenshot, or null if it has none. */
val MediaItemEntity.episodeThumbModel: Any?
    get() = SmbImageUri.resolve(sourceId, episodeThumbPath)
