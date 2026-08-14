package com.seance.app.data.download

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.OutputStream

/**
 * Resolves where downloaded video/subtitle files live and how to create/write/delete them.
 *
 * Two modes, chosen by whether the user has picked a folder in Settings:
 * - No folder picked (default): public `Downloads/Seans` via [MediaStore.Downloads] (API 29+) or a
 *   direct file under the legacy public Downloads dir (API 26-28) - shows up in the system Downloads
 *   app with no permission prompt.
 * - A folder picked via the system folder picker (SAF): create/write there instead, using the
 *   persisted tree permission.
 *
 * Both paths return a stable, playable `content://` Uri - [DownloadWorker] resumes into the exact
 * same Uri it stored last time rather than re-resolving by filename.
 */
object DownloadStorage {
    const val RELATIVE_DIR = "Seans"
    const val DEFAULT_LOCATION_LABEL = "Downloads/$RELATIVE_DIR"

    fun folderDisplayName(context: Context, treeUri: String?): String {
        if (treeUri == null) return DEFAULT_LOCATION_LABEL
        return runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.name }
            .getOrNull() ?: DEFAULT_LOCATION_LABEL
    }

    /** Intent to launch the system folder picker, hinting at the public Downloads folder as a starting point. */
    fun pickerInitialUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Download"
            )
        } else {
            null
        }

    /** Best-effort "show me that folder" - not every OEM file manager honors ACTION_VIEW on a tree/document Uri, so this always falls back to the system Downloads listing. */
    fun openFolderIntent(context: Context, treeUri: String?): Intent {
        if (treeUri != null) {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(treeUri), DocumentsContract.Document.MIME_TYPE_DIR)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) return intent
        }
        return Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Creates a new empty file named [fileName] under the configured folder and returns its Uri, or null on failure. */
    fun create(context: Context, treeUri: String?, fileName: String): Uri? {
        val mimeType = mimeTypeFor(fileName)
        return if (treeUri != null) {
            createUnderTree(context, treeUri, fileName, mimeType)
        } else {
            createInDownloads(context, fileName, mimeType)
        }
    }

    fun length(context: Context, uri: Uri): Long =
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L }.getOrDefault(0L)

    fun exists(context: Context, uri: Uri): Boolean =
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)

    fun openOutput(context: Context, uri: Uri, append: Boolean): OutputStream? =
        runCatching { context.contentResolver.openOutputStream(uri, if (append) "wa" else "w") }.getOrNull()

    fun delete(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private fun createUnderTree(context: Context, treeUri: String, fileName: String, mimeType: String): Uri? {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return null
        return root.createFile(mimeType, fileName)?.uri
    }

    private fun createInDownloads(context: Context, fileName: String, mimeType: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/$RELATIVE_DIR/")
            }
            return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }
        // API 26-28: no MediaStore.Downloads - write directly into the legacy public Downloads dir.
        // Requires WRITE_EXTERNAL_STORAGE (declared maxSdkVersion=28) granted at runtime.
        val dir = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            RELATIVE_DIR
        )
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = java.io.File(dir, fileName)
        // A stale file with the same sanitized name (leftover from a deleted DB row, or a
        // reinstall) must not be silently reused - callers of create() always expect a fresh,
        // empty file, same guarantee MediaStore.insert() gives on API 29+. Reusing it as-is would
        // make DownloadWorker treat its stray bytes as already-downloaded progress and append
        // real data after them, corrupting the file.
        if (file.exists() && !file.delete()) return null
        return runCatching { if (file.createNewFile()) Uri.fromFile(file) else null }.getOrNull()
    }

    private fun mimeTypeFor(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
