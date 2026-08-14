package com.seance.app.data.image

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem

/** Loads a poster/fanart image straight off an SMB share for a `smb-image://` request model. */
class SmbImageFetcher(
    private val uri: Uri,
    private val pool: SmbImageConnectionPool
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val parsed = SmbImageUri.parse(uri.authority, uri.path)
        val bytes = pool.withConnection(parsed.sourceId) { connection ->
            connection.openInputStream(parsed.path).use { it.readBytes() }
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
            return SmbImageFetcher(data, pool)
        }
    }
}
