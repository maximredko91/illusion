package com.seance.app.ui.player

import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Small cross-cutting signal between [com.seance.app.MainActivity] (which owns the actual
 * enterPictureInPictureMode call) and the player screen (the only place that knows whether
 * PiP makes sense right now and what aspect ratio the video is).
 */
object PipController {
    var isPlayerActive by mutableStateOf(false)
    var isInPipMode by mutableStateOf(false)
    var aspectRatio by mutableStateOf(Rational(16, 9))
}
