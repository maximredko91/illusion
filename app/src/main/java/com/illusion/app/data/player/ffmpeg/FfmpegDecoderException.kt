package com.illusion.app.data.player.ffmpeg

import androidx.media3.decoder.DecoderException

class FfmpegDecoderException : DecoderException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
