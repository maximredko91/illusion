package com.illusion.app.domain.model

/**
 * How much video the player pre-buffers before/during playback - chosen once in Settings, applied
 * when the player is (re)created. Larger buffers rebuffer less often on a slow/congested SMB link
 * (e.g. a high-bitrate 4K remux) at the cost of a slower start and more held memory.
 */
enum class PlayerBufferSize {
    /** Scales with the estimated network bandwidth at player-creation time - see AdaptiveLoadControl.kt. */
    AUTO,
    INCREASED,
    MAXIMUM
}
