package com.illusion.app.domain.model

/**
 * Which video/audio decoder the player prefers when the device offers more than one for a codec -
 * chosen in Settings, applied when the player is (re)created.
 *
 * Only ever *reorders* decoders, never removes one, so a format with a single decoder still plays in
 * every mode: the platform's own list via [com.illusion.app.data.player.decoderSelector], plus the
 * app's FFmpeg video renderer, which goes first in Авто/Программный and last in Аппаратный (see
 * [com.illusion.app.data.player.IllusionRenderersFactory]). In Авто FFmpeg only takes formats whose
 * platform decoder is broken or missing (DivX/XviD, DivX 3, VC-1).
 */
enum class DecoderMode {
    /** Platform order - hardware first for anything the SoC can decode. */
    AUTO,

    /** Hardware-accelerated decoders first, software ones only as a fallback. */
    HARDWARE,

    /**
     * Software decoders first. Worth trying on streams a hardware decoder renders with artifacts
     * or green frames - it tolerates non-conforming bitstreams that the SoC's fixed-function
     * decoder rejects, at the cost of CPU (and so heat/battery) on high-resolution video.
     */
    SOFTWARE
}
