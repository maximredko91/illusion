package com.illusion.app.data.player

import android.media.MediaCodecList
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.illusion.app.domain.model.DecoderMode

/**
 * Builds the [MediaCodecSelector] for a [DecoderMode]. Reorders the platform's decoder list for a
 * MIME type without ever shortening it, so the mode is a *preference*, not a filter - a format
 * with a single available decoder keeps playing in every mode instead of failing to initialize.
 */
@OptIn(UnstableApi::class)
fun decoderSelector(mode: DecoderMode): MediaCodecSelector = when (mode) {
    DecoderMode.AUTO -> MediaCodecSelector.DEFAULT
    else -> MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        // partition keeps the platform's own relative order inside each half, so within "hardware"
        // (or "software") the device's preferred decoder still wins - only the two groups swap.
        val (preferred, rest) = infos.partition {
            if (mode == DecoderMode.SOFTWARE) it.softwareOnly else it.hardwareAccelerated
        }
        preferred + rest
    }
}

/**
 * Whether the decoder MediaCodec actually picked is hardware-backed - for the player's diagnostic
 * overlay, which otherwise only shows the *stream's* codec and can't answer "did this file end up
 * on the SoC or on the CPU?". `MediaCodecInfo.isHardwareAccelerated` only exists from API 29;
 * below that fall back to the AOSP naming convention (`c2.android.*` / `OMX.google.*` are the
 * platform's own software codecs).
 */
fun isHardwareDecoder(decoderName: String): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val info = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { it.name == decoderName }
        }.getOrNull()
        if (info != null) return info.isHardwareAccelerated
    }
    return !decoderName.startsWith("c2.android.") && !decoderName.startsWith("OMX.google.")
}
