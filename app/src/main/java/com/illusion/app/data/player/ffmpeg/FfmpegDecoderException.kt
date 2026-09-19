package com.illusion.app.data.player.ffmpeg

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderException

@OptIn(UnstableApi::class)
class FfmpegDecoderException : DecoderException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
