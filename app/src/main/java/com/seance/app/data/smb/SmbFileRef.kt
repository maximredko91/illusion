package com.seance.app.data.smb

data class SmbFileRef(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)

val VIDEO_EXTENSIONS = setOf(
    "mkv", "mp4", "avi", "mov", "m4v", "ts", "wmv"
)

val SUBTITLE_EXTENSIONS = setOf("srt", "ass")

val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

val SmbFileRef.extension: String
    get() = name.substringAfterLast('.', "").lowercase()

val SmbFileRef.isVideo: Boolean
    get() = extension in VIDEO_EXTENSIONS

val SmbFileRef.isSubtitle: Boolean
    get() = extension in SUBTITLE_EXTENSIONS

val SmbFileRef.isImage: Boolean
    get() = extension in IMAGE_EXTENSIONS

/** Base file name without its extension, e.g. "poster" for "poster.jpg". */
val SmbFileRef.baseName: String
    get() = name.substringBeforeLast('.')
