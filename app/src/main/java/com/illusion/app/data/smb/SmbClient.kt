package com.illusion.app.data.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import android.util.Log
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.EnumSet
import java.util.concurrent.TimeUnit

data class SmbDirectoryListing(
    val files: List<SmbFileRef>,
    val directoryPaths: List<String>
)

data class SmbConnectionInfo(
    val host: String,
    val share: String,
    val rootPath: String,
    val domain: String,
    val username: String,
    val password: String
)

class SmbConnection internal constructor(
    private val client: SMBClient,
    private val diskShare: DiskShare
) : java.io.Closeable {

    /**
     * False once the underlying share/session has been torn down - either explicitly via [close]
     * or because smbj itself detected the connection is dead (e.g. the NAS closed an idle SMB
     * session server-side). A cheap, local, non-blocking check - callers reusing a cached
     * [SmbConnection] must check this before reuse, since attempting an operation on an already
     * dead share throws immediately rather than transparently reconnecting.
     */
    val isConnected: Boolean
        get() = diskShare.isConnected

    /**
     * Lists exactly one directory level under [path] (videos, subtitles, .nfo, sub-folders,
     * everything) - one SMB round trip. Callers that need the full tree recurse themselves so
     * they can fan sibling directories out across multiple connections instead of one round trip
     * at a time, which is what made a large share slow to scan.
     */
    fun listDirectory(path: String): SmbDirectoryListing {
        val files = mutableListOf<SmbFileRef>()
        val directoryPaths = mutableListOf<String>()
        for (entry in diskShare.list(path)) {
            val name = entry.fileName
            if (name == "." || name == "..") continue
            val childPath = if (path.isEmpty()) name else "$path\\$name"
            val isDirectory = FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value and entry.fileAttributes != 0L
            if (isDirectory) {
                directoryPaths.add(childPath)
            } else {
                files.add(
                    SmbFileRef(
                        path = childPath,
                        name = name,
                        sizeBytes = entry.endOfFile,
                        lastModified = entry.lastWriteTime.toEpochMillis()
                    )
                )
            }
        }
        return SmbDirectoryListing(files, directoryPaths)
    }

    fun openInputStream(path: String): InputStream {
        val file = diskShare.openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        return file.inputStream
    }

    /** Opens [path] for positional reads, used by the Media3 data source so seeking doesn't require reopening the stream. */
    fun openRandomAccessFile(path: String): SmbRandomAccessFile {
        val file = diskShare.openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        return SmbRandomAccessFile(file)
    }

    /** Actual free space on the share's volume, in bytes - used by the developer-only media-add flow to warn before a large upload. */
    fun freeSpaceBytes(): Long = diskShare.shareInformation.freeSpace

    fun fileExists(path: String): Boolean = diskShare.fileExists(path)

    fun folderExists(path: String): Boolean = diskShare.folderExists(path)

    /** Creates every missing segment of [path] in order - used by the developer-only media-add flow, never by scanning/playback. */
    fun mkdirs(path: String) {
        var current = ""
        for (segment in path.split('\\').filter { it.isNotBlank() }) {
            current = if (current.isEmpty()) segment else "$current\\$segment"
            if (!diskShare.folderExists(current)) {
                diskShare.mkdir(current)
            }
        }
    }

    /**
     * Opens [path] for writing (creating or overwriting) - small files only (.nfo, poster/fanart
     * images), no resume support needed at that size. Parent folder must already exist, see
     * [mkdirs]. Write-only, used by the developer-only media-add flow - the read paths above never
     * call this.
     */
    fun openOutputStream(path: String): OutputStream {
        val file = diskShare.openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        )
        return file.outputStream
    }

    /**
     * Opens [path] for positional writes - the write-side counterpart to [openRandomAccessFile],
     * used for the one big/slow copy in the developer-only media-add flow (the video file itself)
     * so a dropped connection mid-upload can reconnect and resume from the last written offset
     * instead of restarting, the same way [readEdgeSamples]'s caller (the download path) does for
     * reads. [createNew] truncates/creates the file - only the very first open of a given upload
     * attempt should pass true; a reconnect mid-copy must pass false to keep what's already there.
     */
    fun openRandomAccessFileForWrite(path: String, createNew: Boolean): SmbRandomAccessWriteFile {
        val file = diskShare.openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            if (createNew) SMB2CreateDisposition.FILE_OVERWRITE_IF else SMB2CreateDisposition.FILE_OPEN,
            null
        )
        return SmbRandomAccessWriteFile(file)
    }

    /** Best-effort cleanup of a partially-written file after a failed/cancelled upload - never called from the read/scan paths. */
    fun deleteFile(path: String) {
        if (diskShare.fileExists(path)) diskShare.rm(path)
    }

    /**
     * Reads up to [sampleSize] bytes from the start and end of [path], for [StableIdGenerator] -
     * a content sample survives the file being renamed or moved, unlike hashing the name/path.
     * A single read for files no larger than [sampleSize] (head and tail would overlap anyway).
     */
    fun readEdgeSamples(path: String, sizeBytes: Long, sampleSize: Int): Pair<ByteArray, ByteArray> {
        val effectiveSample = minOf(sampleSize.toLong(), sizeBytes).toInt().coerceAtLeast(0)
        if (effectiveSample == 0) return ByteArray(0) to ByteArray(0)
        openRandomAccessFile(path).use { raf ->
            val head = ByteArray(effectiveSample)
            val headRead = raf.read(head, 0L, 0, effectiveSample).coerceAtLeast(0)
            val headBytes = if (headRead == effectiveSample) head else head.copyOf(headRead)

            if (sizeBytes <= sampleSize) {
                // Whole file already covered by the head read - no need for a second round trip.
                return headBytes to headBytes
            }

            val tailStart = sizeBytes - effectiveSample
            val tail = ByteArray(effectiveSample)
            val tailRead = raf.read(tail, tailStart, 0, effectiveSample).coerceAtLeast(0)
            val tailBytes = if (tailRead == effectiveSample) tail else tail.copyOf(tailRead)

            return headBytes to tailBytes
        }
    }

    // runCatching, not a plain call: if the connection already died (network drop mid-session),
    // smbj throws SMBRuntimeException trying to send the close request over an already-closed
    // DiskShare - there's nothing left to clean up at that point, so there's no reason to let that
    // propagate. Left unguarded, this crashed the whole process on-device (SIGABRT, "JNI DETECTED
    // ERROR ... CallBooleanMethodV called with pending exception") specifically because
    // SmbMediaDataSource.close() (below) is invoked by MediaMetadataRetriever's own native code
    // via JNI - Android's CheckJNI aborts hard on an uncaught Java exception crossing that
    // boundary, unlike a normal uncaught-exception crash the app's own crash handler could log.
    override fun close() {
        runCatching { diskShare.close() }
        runCatching { client.close() }
    }
}

class SmbRandomAccessFile internal constructor(
    private val file: com.hierynomus.smbj.share.File
) : java.io.Closeable {
    /**
     * Reads up to [length] bytes into [buffer] at [offset], starting at [filePosition] in the
     * remote file; returns bytes read, or <= 0 at end of file. Callers MUST pass [length] rather
     * than rely on the 2-arg `File.read(buffer, fileOffset)` convenience overload - that one reads
     * up to `buffer.size` regardless of how much the caller actually wants, which overflows a
     * caller-supplied destination when it's reused (e.g. a growing scratch buffer) for a smaller
     * request than it was last sized for.
     */
    fun read(buffer: ByteArray, filePosition: Long, offset: Int = 0, length: Int = buffer.size): Int =
        file.read(buffer, filePosition, offset, length)

    // See SmbConnection.close()'s comment - same reasoning, a dead connection makes close() itself
    // throw SMBRuntimeException, which must not propagate uncaught here.
    override fun close() { runCatching { file.close() } }
}

class SmbRandomAccessWriteFile internal constructor(
    private val file: com.hierynomus.smbj.share.File
) : java.io.Closeable {
    /** Writes [length] bytes from [buffer] (starting at [offset]) to [filePosition] in the remote file - the write-side counterpart to [SmbRandomAccessFile.read]. */
    fun write(buffer: ByteArray, filePosition: Long, offset: Int = 0, length: Int = buffer.size): Long =
        file.write(buffer, filePosition, offset, length)

    // See SmbConnection.close()'s comment - same reasoning.
    override fun close() { runCatching { file.close() } }
}

class SmbClient {
    // smbj's default socket timeout is 0 (= wait forever), so an unreachable host or a
    // firewall that silently drops packets hangs connect() indefinitely - an explicit
    // timeout here plus the withTimeout() backstop below are both needed to fail fast.
    private val config: SmbConfig = SmbConfig.builder()
        .withSoTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .withTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun connect(info: SmbConnectionInfo): SmbConnection = withContext(Dispatchers.IO) {
        withTimeout(CONNECTION_TIMEOUT_SECONDS * 1000) {
            Log.d(TAG, "connect: host=${info.host} share=${info.share} guest=${info.username.isBlank()}")
            val client = SMBClient(config)
            val connection = client.connect(info.host)
            Log.d(TAG, "connect: TCP connected, authenticating")
            // An empty username isn't the same as a real guest/anonymous login in NTLM terms -
            // some SMB servers reject it outright, so route it through smbj's dedicated guest auth.
            val auth = if (info.username.isBlank()) {
                AuthenticationContext.guest()
            } else {
                AuthenticationContext(info.username, info.password.toCharArray(), info.domain)
            }
            val session = connection.authenticate(auth)
            Log.d(TAG, "connect: authenticated, connecting share")
            val share = session.connectShare(info.share) as DiskShare
            Log.d(TAG, "connect: share connected")
            SmbConnection(client, share)
        }
    }

    suspend fun testConnection(info: SmbConnectionInfo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            connect(info).use { it.fileExists(info.rootPath) }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "testConnection failed", e)
            Result.failure(mapConnectionError(e))
        }
    }

    private fun mapConnectionError(e: Throwable): Exception = Exception(classifySmbError(e))

    companion object {
        private const val TAG = "SmbClient"
        private const val CONNECTION_TIMEOUT_SECONDS = 10L
    }
}

/** Shared by [SmbClient.testConnection]'s error dialog and [com.illusion.app.data.scan.LibraryScanner]'s per-source scan-failure reporting - one place classifying the same handful of SMB failure shapes into a Russian message the user can act on. */
fun classifySmbError(e: Throwable): String = when (e) {
    is TimeoutCancellationException, is SocketTimeoutException ->
        "Не удалось подключиться: сервер не отвечает (проверьте адрес и что телефон в той же сети)"
    is java.net.UnknownHostException ->
        "Не удалось найти хост \"${e.message}\" - проверьте адрес сервера"
    is java.net.ConnectException ->
        "Соединение отклонено - проверьте адрес и порт SMB (445)"
    else -> {
        val message = e.message ?: e::class.simpleName ?: "неизвестная ошибка"
        when {
            // These two used to be collapsed into one "wrong password" message - a genuinely
            // different failure (STATUS_ACCESS_DENIED, the current credentials authenticate fine
            // but this specific file/folder is off-limits per the NAS's own per-folder ACL) read
            // exactly like a credential problem, sending the user to keep re-entering the same
            // already-correct password. Confirmed on-device twice this session: once opening a
            // single file whose folder had incomplete per-user permissions, once during a full
            // library scan where the same class of error on some OTHER folder in the tree - not
            // the credentials at all - produced this same misleading message.
            message.contains("STATUS_LOGON_FAILURE", ignoreCase = true) -> "Неверный логин или пароль"
            message.contains("STATUS_ACCESS_DENIED", ignoreCase = true) ->
                // Keeps the original exception message (smbj includes the exact remote path it
                // was denied on, e.g. "Create failed for \\host\share\Movies\...") rather than
                // just a generic "check your permissions" - that path is exactly what's needed to
                // find the one folder with the wrong ACL on the router, not just that one exists
                // somewhere in the tree.
                "Доступ запрещён (логин и пароль верны, дело не в них - проверьте права этого пользователя на роутере): $message"
            message.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) -> "Шара с таким именем не найдена на сервере"
            else -> "Не удалось подключиться: $message"
        }
    }
}
