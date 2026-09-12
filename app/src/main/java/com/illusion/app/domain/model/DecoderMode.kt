package com.illusion.app.domain.model

/**
 * Which video/audio decoder the player prefers when the device offers more than one for a codec -
 * chosen in Settings, applied when the player is (re)created.
 *
 * Only ever *reorders* the platform's own decoder list (see
 * [com.illusion.app.data.player.decoderSelector]); nothing is ever removed, so a format with only
 * one decoder still plays in every mode. That matters for MPEG-4 ASP (DivX/XviD), where
 * `c2.android.mpeg4.decoder` is the only decoder on this device either way.
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
