package com.illusion.app.domain.model

/** Which player plays a movie/episode when the user taps play - chosen once in Settings rather than per-playback in the player itself. */
enum class PlayerMode {
    INTERNAL,
    EXTERNAL,
    ASK
}
