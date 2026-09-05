package com.illusion.app.work

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** Consumes exactly the existing remote prefix, verifying every byte before any write. */
internal fun verifyUploadPrefix(
    input: InputStream,
    length: Long,
    readRemote: (ByteArray, Long, Int) -> Int
) {
    require(length >= 0)
    val remote = ByteArray(512 * 1024)
    val local = ByteArray(remote.size)
    var position = 0L
    while (position < length) {
        val count = readRemote(remote, position, minOf(remote.size.toLong(), length - position).toInt())
        if (count <= 0) throw EOFException("Файл на NAS изменился во время проверки")
        var filled = 0
        while (filled < count) {
            val read = input.read(local, filled, count - filled)
            if (read < 0) throw EOFException("Файл на NAS длиннее выбранного видео")
            if (read == 0) {
                val byte = input.read()
                if (byte < 0) throw EOFException("Файл на NAS длиннее выбранного видео")
                local[filled++] = byte.toByte()
            } else filled += read
        }
        if ((0 until count).any { remote[it] != local[it] }) {
            throw IOException("На NAS уже есть другой файл с этим именем. Выберите другое имя видео.")
        }
        position += count
    }
}
