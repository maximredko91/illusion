package com.seance.app.data.image

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem

/**
 * Loads a poster/fanart image straight off an SMB share for a `smb-image://` request model.
 *
 * Coil's [coil3.intercept.EngineInterceptor] always calls [Fetcher.fetch] - unlike its HTTP
 * fetchers, a plain [SourceFetchResult]-returning fetcher does NOT get a disk-cache read for
 * free, so without this the app would hit the NAS over SMB on every load, even for a poster
 * already on disk - making cached posters vanish the moment the phone isn't on the NAS's
 * network (offline/no-wifi), instead of falling back to what's already cached. Read/write the
 * disk cache here explicitly, the same way an HTTP fetcher would, so a disk hit never touches
 * the network at all.
 */
class SmbImageFetcher(
    private val uri: Uri,
    private val pool: SmbImageConnectionPool,
    private val diskCache: DiskCache?,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val cacheKey = options.diskCacheKey ?: uri.toString()

        if (options.diskCachePolicy.readEnabled) {
            diskCache?.openSnapshot(cacheKey)?.let { snapshot ->
                return SourceFetchResult(
                    source = ImageSource(
                        file = snapshot.data,
                        fileSystem = diskCache.fileSystem,
                        diskCacheKey = cacheKey,
                        closeable = snapshot
                    ),
                    mimeType = null,
                    dataSource = DataSource.DISK
                )
            }
        }

        val parsed = SmbImageUri.parse(uri.authority, uri.path)
        val bytes = pool.withConnection(parsed.sourceId) { connection ->
            connection.openInputStream(parsed.path).use { it.readBytes() }
        }

        if (options.diskCachePolicy.writeEnabled) {
            diskCache?.openEditor(cacheKey)?.let { editor ->
                try {
                    diskCache.fileSystem.write(editor.data) { write(bytes) }
                    editor.commit()
                } catch (t: Throwable) {
                    editor.abort()
                    throw t
                }
            }
        }

        val buffer = Buffer().apply { write(bytes) }
        return SourceFetchResult(
            source = ImageSource(buffer, FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.NETWORK
        )
    }

    class Factory(private val pool: SmbImageConnectionPool) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != SmbImageUri.SCHEME) return null
            return SmbImageFetcher(data, pool, imageLoader.diskCache, options)
        }
    }
}
