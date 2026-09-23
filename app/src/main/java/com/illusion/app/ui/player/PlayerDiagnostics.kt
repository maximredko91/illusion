package com.illusion.app.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.illusion.app.data.local.entity.MediaItemEntity

/** Text for the player's diagnostic overlay (file, codecs, decoders, HDR/DV, tracks). */
private fun decoderKindLabel(decoderName: String): String =
    if (com.illusion.app.data.player.isHardwareDecoder(decoderName)) "аппаратный" else "программный"

@OptIn(UnstableApi::class)
internal fun buildVideoFormatSummary(
    player: ExoPlayer,
    item: MediaItemEntity?,
    videoDecoderName: String?,
    audioDecoderName: String?,
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>
): String {
    val format = player.videoFormat
    val audio = player.audioFormat

    return buildString {
        item?.let {
            appendLine("Файл: ${it.filePath.substringAfterLast('\\')}")
            appendLine("Размер: ${formatFileSize(it.sizeBytes)}")
        }
        if (player.duration > 0) appendLine("Длительность: ${formatTime(player.duration)}")

        if (format == null) {
            appendLine()
            appendLine("Видеодорожка ещё не определена")
            // When the renderer never gets a video format, the interesting question is whether
            // the container even exposed a video track and whether this device can decode it -
            // "no video track at all" and "track present but unsupported codec" look identical
            // on screen (an endless buffering spinner) but mean completely different things.
            val videoGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            if (videoGroups.isEmpty()) {
                appendLine("В контейнере не найдено ни одной видеодорожки")
            } else {
                for (group in videoGroups) {
                    for (i in 0 until group.length) {
                        val trackFormat = group.getTrackFormat(i)
                        val supported = if (group.isTrackSupported(i)) "поддерживается" else "НЕ поддерживается"
                        appendLine(
                            "Дорожка: ${trackFormat.sampleMimeType ?: "—"} " +
                                "${trackFormat.width}x${trackFormat.height} - $supported" +
                                if (group.isTrackSelected(i)) ", выбрана" else ""
                        )
                    }
                }
            }
        } else {
            val color = format.colorInfo
            val dvProfile = format.codecs
                ?.takeIf { it.startsWith("dvhe") || it.startsWith("dvh1") || it.startsWith("dva1") || it.startsWith("dvav") }
                ?.split(".")
                ?.getOrNull(1)
                ?.toIntOrNull()
            val dynamicRange = when {
                dvProfile != null -> "Dolby Vision (Profile $dvProfile)"
                color?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10/HDR10+"
                color?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
                else -> "SDR"
            }
            appendLine()
            appendLine("Видео")
            appendLine("Кодек: ${format.sampleMimeType ?: "—"} (${format.codecs ?: "—"})")
            videoDecoderName?.let { appendLine("Декодер: $it (${decoderKindLabel(it)})") }
            appendLine("Разрешение: ${format.width}x${format.height}")
            if (format.frameRate > 0) appendLine("Частота кадров: ${"%.2f".format(format.frameRate)} fps")
            if (format.bitrate > 0) appendLine("Битрейт: ${format.bitrate / 1000} кбит/с")
            appendLine("Динамический диапазон: $dynamicRange")
            appendLine("Цвет: пространство=${color?.colorSpace ?: "—"}, transfer=${color?.colorTransfer ?: "—"}, range=${color?.colorRange ?: "—"}")
            if (dvProfile == 7) {
                appendLine(
                    "⚠ Profile 7 хранит доп. детализацию в отдельном enhancement-layer потоке. " +
                        "Плеер показывает только базовый слой — картинка корректна, но без этой детализации."
                )
            }
        }

        if (audio != null) {
            appendLine()
            appendLine("Аудио")
            appendLine("Кодек: ${audio.sampleMimeType ?: "—"} (${audio.codecs ?: "—"})")
            audioDecoderName?.let { appendLine("Декодер: $it (${decoderKindLabel(it)})") }
            if (audio.channelCount != androidx.media3.common.Format.NO_VALUE) appendLine("Каналы: ${audio.channelCount}")
            if (audio.sampleRate != androidx.media3.common.Format.NO_VALUE) appendLine("Частота дискретизации: ${audio.sampleRate} Гц")
            if (audio.bitrate > 0) appendLine("Битрейт: ${audio.bitrate / 1000} кбит/с")
        }

        if (audioTracks.size > 1) {
            appendLine()
            appendLine("Все аудиодорожки: ${audioTracks.joinToString(", ") { it.label }}")
        }
        if (subtitleTracks.isNotEmpty()) {
            appendLine("Субтитры: ${subtitleTracks.joinToString(", ") { it.label }}")
        } else {
            appendLine()
            appendLine("Субтитры: нет")
        }
    }.trim()
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}
